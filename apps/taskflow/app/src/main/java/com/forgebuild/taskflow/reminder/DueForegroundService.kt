package com.forgebuild.taskflow.reminder

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.forgebuild.taskflow.notify.NotificationHub

/**
 * Persistent low-priority foreground service keeping reminder reliability honest:
 * Android does NOT permit unrestricted always-on background execution, so the app
 * uses exact alarms + this persistent foreground indicator + a user-granted battery
 * exemption to ensure reminder delivery.
 */
class DueForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            NotificationHub.ensureChannels(this)
            startForeground(1001, NotificationHub.foregroundNotification(this))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            NotificationHub.ensureChannels(this)
            startForeground(1001, NotificationHub.foregroundNotification(this))
        }
        return START_STICKY
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, DueForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) = runCatching {
            context.stopService(Intent(context, DueForegroundService::class.java))
        }
    }
}
