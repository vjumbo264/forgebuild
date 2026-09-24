package com.forgebuild.everbrowse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * EverBrowse ultra-persistent keep-alive engine (anti-LMK).
 *
 * A START_STICKY foreground service holding a PARTIAL_WAKE_LOCK so the
 * browser process keeps CPU residency when the screen turns off or another
 * app takes focus. A low-priority persistent notification keeps the process
 * in the foreground-service bucket, making it a far less attractive victim
 * for the Low Memory Killer — which is what stops WebView pages reloading.
 */
class BrowserKeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if the system does kill us, it restarts the service
        // (and re-runs this) as soon as resources allow.
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
        // Re-start ourselves: LMK-resistant behavior — if torn down, come back.
        runCatching { start(this) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "everbrowse:keepalive").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Browser keep-alive",
                NotificationManager.IMPORTANCE_MIN // low-priority: no sound, no peek
            ).apply {
                description = "Keeps EverBrowse resident so pages don't refresh in the background"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_everbrowse)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Browser active in background")
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "everbrowse_keepalive"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, BrowserKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }
    }
}
