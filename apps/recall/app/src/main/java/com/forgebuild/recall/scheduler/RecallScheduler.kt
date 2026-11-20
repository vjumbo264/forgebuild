package com.forgebuild.recall.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.forgebuild.recall.data.ClockSyncEngine
import com.forgebuild.recall.data.RecallRepository
import com.forgebuild.recall.data.RecallStatus
import com.forgebuild.recall.data.ScheduledChime
import com.forgebuild.recall.notify.RecallNotificationHub
import com.forgebuild.recall.receiver.RecallAlarmReceiver
import com.forgebuild.recall.util.RecallDiagnostics

/**
 * Exact clock-boundary AlarmManager scheduler for RECALL.
 *
 * Ported & improved from TaskFlow's ReminderScheduler:
 * - Uses a single deterministic PendingIntent ([REQUEST_CODE_BOUNDARY]) and
 *   cancels prior alarms before arming, preventing duplicate triggers.
 * - Never schedules relative to button-press time; always computes the next
 *   real-world wall-clock boundary via [ClockSyncEngine.nextGlobalEvent].
 * - Supports both `setExactAndAllowWhileIdle` and optional `setAlarmClock`
 *   high-precision mode for aggressive OEM battery managers.
 */
object RecallScheduler {

    const val EXTRA_TARGET_BOUNDARY_MS = "extra_target_boundary_ms"
    private const val REQUEST_CODE_BOUNDARY = 9001

    private fun alarmPendingIntent(context: Context, targetBoundaryMs: Long?): PendingIntent {
        val intent = Intent(context, RecallAlarmReceiver::class.java)
            .setAction(RecallAlarmReceiver.ACTION_CHIME_BOUNDARY)
        if (targetBoundaryMs != null) {
            intent.putExtra(EXTRA_TARGET_BOUNDARY_MS, targetBoundaryMs)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BOUNDARY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun cancelScheduledAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmPendingIntent(context, null))
    }

    fun scheduleNext(
        context: Context,
        afterEpochMs: Long = System.currentTimeMillis(),
    ): ScheduledChime? {
        val appCtx = context.applicationContext
        val repo = RecallRepository.get(appCtx)
        val state = repo.currentState

        if (state.status != RecallStatus.ACTIVE) {
            cancelScheduledAlarm(appCtx)
            if (state.status == RecallStatus.PAUSED) {
                RecallNotificationHub.updateForegroundNotification(appCtx, state, null)
            }
            return null
        }

        val effectiveAfter = maxOf(afterEpochMs, state.lastFiredBoundaryMs)
        val next = ClockSyncEngine.nextGlobalEvent(state.intervals, effectiveAfter)
        cancelScheduledAlarm(appCtx)

        if (next == null) {
            RecallDiagnostics.logEvent(appCtx, "Scheduler", "Active but no enabled intervals; alarm cleared.")
            RecallNotificationHub.updateForegroundNotification(appCtx, state, null)
            return null
        }

        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmPendingIntent(appCtx, next.boundaryEpochMs)
        val exactAllowed = canScheduleExactAlarms(appCtx)

        try {
            if (state.highPrecisionAlarmClockMode && exactAllowed) {
                val showPi = RecallNotificationHub.launchPendingIntent(appCtx)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(next.boundaryEpochMs, showPi), pi)
                RecallDiagnostics.logEvent(
                    appCtx,
                    "Scheduler",
                    "Armed setAlarmClock for ${RecallNotificationHub.formatClock(appCtx, next.boundaryEpochMs)} (${next.winningInterval.label})",
                )
            } else if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.boundaryEpochMs, pi)
                RecallDiagnostics.logEvent(
                    appCtx,
                    "Scheduler",
                    "Armed exact alarm for ${RecallNotificationHub.formatClock(appCtx, next.boundaryEpochMs)} (${next.winningInterval.label})",
                )
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.boundaryEpochMs, pi)
                RecallDiagnostics.logEvent(
                    appCtx,
                    "Scheduler",
                    "Exact alarm permission missing; armed inexact fallback for ${RecallNotificationHub.formatClock(appCtx, next.boundaryEpochMs)}",
                )
            }
        } catch (t: Throwable) {
            RecallDiagnostics.logError(appCtx, "Scheduler", "Failed to arm AlarmManager: ${t.message}", t)
        }

        RecallNotificationHub.updateForegroundNotification(appCtx, state, next)
        return next
    }
}
