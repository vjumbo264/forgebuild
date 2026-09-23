package com.forgebuild.taskflow.reminder

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.forgebuild.taskflow.data.TaskRepository
import com.forgebuild.taskflow.data.TimerEngine
import com.forgebuild.taskflow.notify.NotificationHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Persistent low-priority foreground service keeping reminder reliability honest:
 * Android does NOT permit unrestricted always-on background execution, so the app
 * uses exact alarms + this persistent foreground indicator + a user-granted battery
 * exemption to ensure reminder delivery.
 *
 * Revision Pass 7: while any task timer is RUNNING, this service also posts a
 * persistent live-countdown notification (updated once per second, silent). When the
 * running timer is a Pomodoro one it additionally shows progress toward the next
 * transition (toward the upcoming break in a work interval, toward resuming work in a
 * break) — the Android system renders that bar with the Material You wavy-progress
 * appearance on modern releases.
 */
class DueForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        runCatching {
            NotificationHub.ensureChannels(this)
            startForeground(1001, NotificationHub.foregroundNotification(this))
        }
        watchTimers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            NotificationHub.ensureChannels(this)
            startForeground(1001, NotificationHub.foregroundNotification(this))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        NotificationHub.cancelTimerNotification(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun watchTimers() {
        val repo = TaskRepository.get(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        serviceScope.launch {
            // The Room flow re-emits on any task change; the 1s ticker drives the live
            // countdown text between emissions. Posting the same id updates in place.
            while (isActive) {
                val running = repo.allActiveNow().firstOrNull {
                    TimerEngine.stateOf(it) == TimerEngine.TimerState.RUNNING
                }
                if (running == null) {
                    NotificationHub.cancelTimerNotification(this@DueForegroundService)
                } else {
                    val now = System.currentTimeMillis()
                    val remainingMs = TimerEngine.remainingMs(running, now)
                    val seg = TimerEngine.currentSegment(running, now)
                    val phaseLabel = seg?.let { if (it.isWork) "until break" else "until work resumes" }
                    val progress = seg?.progress ?: -1f
                    runCatching {
                        nm.notify(
                            NotificationHub.TIMER_NOTIFICATION_ID,
                            NotificationHub.timerNotification(
                                this@DueForegroundService,
                                running.title,
                                fmtCountdown(remainingMs),
                                phaseLabel,
                                progress
                            )
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    private fun fmtCountdown(ms: Long): String {
        val totalSec = (ms + 999L) / 1000L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
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
