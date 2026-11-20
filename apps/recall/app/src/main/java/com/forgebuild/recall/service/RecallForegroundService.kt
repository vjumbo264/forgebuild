package com.forgebuild.recall.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.forgebuild.recall.data.ClockSyncEngine
import com.forgebuild.recall.data.RecallRepository
import com.forgebuild.recall.data.RecallStatus
import com.forgebuild.recall.notify.RecallNotificationHub
import com.forgebuild.recall.util.RecallDiagnostics

/**
 * Event-driven foreground service preserving background alarm reliability.
 *
 * Ported from TaskFlow's DueForegroundService (`specialUse` type, API 34+
 * ServiceInfo guard, ForegroundServiceStartNotAllowedException catch).
 * Unlike TaskFlow's 1-second polling loop, this service does ZERO background
 * polling and updates strictly when RECALL state or scheduled boundaries change.
 */
class RecallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = RecallRepository.get(this).currentState
        if (state.status == RecallStatus.STOPPED) {
            startForegroundSafely()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundSafely()
        return START_STICKY
    }

    private fun startForegroundSafely() {
        try {
            val repo = RecallRepository.get(this)
            val state = repo.currentState
            val next = if (state.status == RecallStatus.ACTIVE) {
                val effectiveAfter = maxOf(System.currentTimeMillis(), state.lastFiredBoundaryMs)
                ClockSyncEngine.nextGlobalEvent(state.intervals, effectiveAfter)
            } else {
                null
            }
            val notif = RecallNotificationHub.buildForegroundNotification(this, state, next)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    RecallNotificationHub.FOREGROUND_NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(RecallNotificationHub.FOREGROUND_NOTIFICATION_ID, notif)
            }
        } catch (t: Throwable) {
            RecallDiagnostics.logError(this, "RecallService", "startForeground failed: ${t.message}", t)
            stopSelf()
        }
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, RecallForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { t ->
                RecallDiagnostics.logError(context, "RecallService", "startService failed: ${t.message}", t)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, RecallForegroundService::class.java))
            }
        }
    }
}
