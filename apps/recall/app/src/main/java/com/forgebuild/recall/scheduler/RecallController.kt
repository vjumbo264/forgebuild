package com.forgebuild.recall.scheduler

import android.content.Context
import com.forgebuild.recall.audio.RecallAudioEngine
import com.forgebuild.recall.data.RecallRepository
import com.forgebuild.recall.data.RecallStatus
import com.forgebuild.recall.data.ScheduledChime
import com.forgebuild.recall.notify.RecallNotificationHub
import com.forgebuild.recall.service.RecallForegroundService
import com.forgebuild.recall.util.RecallDiagnostics

/**
 * Coordinates RECALL lifecycle transitions across repository state,
 * [RecallScheduler], [RecallForegroundService], and [RecallAudioEngine].
 */
object RecallController {

    fun startOrResume(context: Context): ScheduledChime? {
        val appCtx = context.applicationContext
        val repo = RecallRepository.get(appCtx)
        val wasPaused = repo.currentState.status == RecallStatus.PAUSED
        repo.setStatus(RecallStatus.ACTIVE)
        val next = RecallScheduler.scheduleNext(appCtx, System.currentTimeMillis())
        RecallForegroundService.start(appCtx)
        RecallDiagnostics.logEvent(
            appCtx,
            "Controller",
            if (wasPaused) "Resumed RECALL; synced with clock" else "Started RECALL; synced with clock",
        )
        return next
    }

    fun pause(context: Context) {
        val appCtx = context.applicationContext
        val repo = RecallRepository.get(appCtx)
        repo.setStatus(RecallStatus.PAUSED)
        RecallScheduler.cancelScheduledAlarm(appCtx)
        RecallAudioEngine.stopCurrent()
        RecallForegroundService.start(appCtx)
        RecallNotificationHub.updateForegroundNotification(appCtx, repo.currentState, null)
        RecallDiagnostics.logEvent(appCtx, "Controller", "Paused RECALL; alarm cancelled")
    }

    fun stop(context: Context) {
        val appCtx = context.applicationContext
        val repo = RecallRepository.get(appCtx)
        repo.setStatus(RecallStatus.STOPPED)
        repo.resetLastFiredBoundary()
        RecallScheduler.cancelScheduledAlarm(appCtx)
        RecallAudioEngine.stopCurrent()
        RecallNotificationHub.cancelChimeAlert(appCtx)
        RecallForegroundService.stop(appCtx)
        RecallDiagnostics.logEvent(appCtx, "Controller", "Stopped RECALL")
    }

    fun onIntervalsOrSettingsChanged(context: Context) {
        val appCtx = context.applicationContext
        val state = RecallRepository.get(appCtx).currentState
        if (state.status == RecallStatus.ACTIVE) {
            RecallScheduler.scheduleNext(appCtx, System.currentTimeMillis())
            RecallForegroundService.start(appCtx)
        } else if (state.status == RecallStatus.PAUSED) {
            RecallNotificationHub.updateForegroundNotification(appCtx, state, null)
        }
    }

    fun onSystemRecovery(context: Context, reason: String, resetDedup: Boolean) {
        val appCtx = context.applicationContext
        val repo = RecallRepository.get(appCtx)
        if (resetDedup) {
            repo.resetLastFiredBoundary()
        }
        val state = repo.currentState
        RecallDiagnostics.logEvent(appCtx, "Recovery", "System recovery ($reason), status=${state.status}")
        when (state.status) {
            RecallStatus.ACTIVE -> {
                RecallScheduler.scheduleNext(appCtx, System.currentTimeMillis())
                RecallForegroundService.start(appCtx)
            }
            RecallStatus.PAUSED -> {
                RecallScheduler.cancelScheduledAlarm(appCtx)
                RecallForegroundService.start(appCtx)
            }
            RecallStatus.STOPPED -> {
                RecallScheduler.cancelScheduledAlarm(appCtx)
            }
        }
    }
}
