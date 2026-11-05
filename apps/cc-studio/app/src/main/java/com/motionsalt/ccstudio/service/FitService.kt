package com.motionsalt.ccstudio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.motionsalt.ccstudio.MainActivity
import com.motionsalt.ccstudio.core.LutSolver
import com.motionsalt.ccstudio.core.PixelPair
import com.motionsalt.ccstudio.core.SolverProgress
import com.motionsalt.ccstudio.core.SolverResult
import kotlinx.coroutines.*

class FitService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var fitJob: Job? = null

    var currentProgress: SolverProgress? = null
        private set
    var latestResult: SolverResult? = null
        private set
    var isRunning: Boolean = false
        private set

    private var progressListener: ((SolverProgress) -> Unit)? = null
    private var completionListener: ((SolverResult?) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): FitService = this@FitService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelFit()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    fun startFit(
        pairs: List<PixelPair>,
        gridSize: Int = 17,
        onProgress: (SolverProgress) -> Unit,
        onComplete: (SolverResult?) -> Unit
    ) {
        if (isRunning) return
        isRunning = true
        progressListener = onProgress
        completionListener = onComplete

        val notification = createNotification("Starting look solver...", 0)
        startForeground(NOTIFICATION_ID, notification)

        fitJob = serviceScope.launch {
            try {
                val result = LutSolver.fit(pairs, gridSize) { prog ->
                    currentProgress = prog
                    updateNotification("Step ${prog.step}/${prog.totalSteps}: ${prog.stepName}", (prog.progressFraction * 100).toInt())
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        progressListener?.invoke(prog)
                    }
                }
                latestResult = result
                isRunning = false
                withContext(Dispatchers.Main) {
                    completionListener?.invoke(result)
                }
            } catch (e: CancellationException) {
                isRunning = false
                withContext(Dispatchers.Main) {
                    completionListener?.invoke(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isRunning = false
                withContext(Dispatchers.Main) {
                    completionListener?.invoke(null)
                }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    fun cancelFit() {
        fitJob?.cancel()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Look Fitting Solver",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress during 3D LUT look fitting"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String, progress: Int): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, FitService::class.java).apply {
            action = ACTION_CANCEL
        }
        val pCancel = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fitting MOTIONSALT Look")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pCancel)
            .build()
    }

    private fun updateNotification(content: String, progress: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(content, progress))
    }

    companion object {
        const val CHANNEL_ID = "cc_studio_solver_channel"
        const val NOTIFICATION_ID = 2024
        const val ACTION_CANCEL = "com.motionsalt.ccstudio.ACTION_CANCEL"
    }
}
