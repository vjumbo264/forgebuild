package com.forgebuild.recall.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.forgebuild.recall.audio.RecallAudioEngine
import com.forgebuild.recall.data.ClockSyncEngine
import com.forgebuild.recall.data.RecallRepository
import com.forgebuild.recall.notify.RecallNotificationHub
import com.forgebuild.recall.scheduler.RecallScheduler
import com.forgebuild.recall.util.RecallDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Exact-alarm BroadcastReceiver for clock-boundary chimes.
 *
 * Acquires a bounded partial WakeLock across audio playback and next-alarm
 * scheduling so chimes reliably complete when the screen is off or locked.
 * Guarantees missed chimes are suppressed (never replayed as a backlog).
 */
class RecallAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHIME_BOUNDARY) return
        val appCtx = context.applicationContext
        val targetBoundaryMs = intent.getLongExtra(RecallScheduler.EXTRA_TARGET_BOUNDARY_MS, -1L)

        val pm = appCtx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Recall:ChimeWakeLock").apply {
            setReferenceCounted(false)
            acquire(15_000L)
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = RecallRepository.get(appCtx)
                val state = repo.currentState
                val nowMs = System.currentTimeMillis()

                when (
                    val decision = ClockSyncEngine.evaluateAlarmTrigger(
                        targetBoundaryMs = targetBoundaryMs,
                        nowMs = nowMs,
                        lastFiredBoundaryMs = state.lastFiredBoundaryMs,
                        status = state.status,
                    )
                ) {
                    ClockSyncEngine.TriggerDecision.NotActive -> {
                        RecallScheduler.cancelScheduledAlarm(appCtx)
                        RecallDiagnostics.logEvent(appCtx, "AlarmReceiver", "Ignored trigger while status=${state.status}")
                    }

                    is ClockSyncEngine.TriggerDecision.TooEarly -> {
                        RecallDiagnostics.logEvent(appCtx, "AlarmReceiver", "Early trigger ignored; rescheduling from now")
                        RecallScheduler.scheduleNext(appCtx, decision.rescheduleFromMs)
                    }

                    is ClockSyncEngine.TriggerDecision.Duplicate -> {
                        RecallDiagnostics.logEvent(appCtx, "AlarmReceiver", "Duplicate boundary=$targetBoundaryMs suppressed")
                        RecallScheduler.scheduleNext(appCtx, decision.rescheduleFromMs)
                    }

                    is ClockSyncEngine.TriggerDecision.MissedSuppressed -> {
                        RecallDiagnostics.logEvent(
                            appCtx,
                            "AlarmReceiver",
                            "Missed boundary=$targetBoundaryMs suppressed (late by ${decision.lateByMs}ms); no backlog played",
                        )
                        RecallScheduler.scheduleNext(appCtx, decision.rescheduleFromMs)
                    }

                    is ClockSyncEngine.TriggerDecision.Fire -> {
                        val resolved = ClockSyncEngine.resolveAtBoundary(state.intervals, decision.boundaryMs)
                        if (resolved != null) {
                            repo.recordFiredBoundary(decision.boundaryMs)
                            val winner = resolved.winningInterval
                            val overridden = resolved.coincidingIntervals.drop(1).joinToString(", ") { it.label }
                            RecallDiagnostics.logEvent(
                                appCtx,
                                "AlarmReceiver",
                                buildString {
                                    append("Playing ${winner.label} (${winner.sound.displayName}, vol=${(winner.volume * 100).toInt()}%)")
                                    if (overridden.isNotEmpty()) append(" [overrode: $overridden]")
                                },
                            )
                            RecallAudioEngine.playChimeBlocking(appCtx, winner.sound, winner.volume)
                            if (state.headsUpNotificationsEnabled) {
                                RecallNotificationHub.postHeadsUpChime(appCtx, decision.boundaryMs, winner)
                            }
                        } else {
                            RecallDiagnostics.logEvent(appCtx, "AlarmReceiver", "No enabled interval matched boundary=${decision.boundaryMs}")
                        }
                        RecallScheduler.scheduleNext(appCtx, maxOf(System.currentTimeMillis(), decision.boundaryMs))
                    }
                }
            } catch (t: Throwable) {
                RecallDiagnostics.logError(appCtx, "AlarmReceiver", "Alarm execution error: ${t.message}", t)
                runCatching { RecallScheduler.scheduleNext(appCtx, System.currentTimeMillis()) }
            } finally {
                runCatching { if (wakeLock.isHeld) wakeLock.release() }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHIME_BOUNDARY = "com.forgebuild.recall.action.CHIME_BOUNDARY"
    }
}
