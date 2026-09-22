package com.forgebuild.taskflow.reminder

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.forgebuild.taskflow.notify.NotificationHub

/**
 * Persistent low-priority foreground service keeping reminder reliability honest:
 * Android does NOT permit unrestricted always-on background execution, so the app
 * uses exact alarms + this compliant foreground indicator + a user-granted battery
 * exemption instead of pretending to be unkillable.
 */
class DueForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, NotificationHub.foregroundNotification(this))
        return START_STICKY
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context, Intent(context, DueForegroundService::class.java))
            }
        }
        fun stop(context: Context) = context.stopService(Intent(context, DueForegroundService::class.java))
    }
}
