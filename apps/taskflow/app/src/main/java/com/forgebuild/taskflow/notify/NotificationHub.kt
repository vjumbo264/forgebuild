package com.forgebuild.taskflow.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.forgebuild.taskflow.settings.AppSettings
import com.forgebuild.taskflow.settings.NotifType
import kotlinx.coroutines.flow.first

object NotificationHub {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_AGENDA = "agenda"
    const val CHANNEL_AGENT = "agent"
    const val CHANNEL_SYSTEM = "system_status"
    const val CHANNEL_FOREGROUND = "foreground_service"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            NotificationChannel(CHANNEL_REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Due-now and overdue alerts for fixed-time tasks" },
            NotificationChannel(CHANNEL_AGENDA, "Daily agenda", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Morning summary of today's fixed-time and top-priority tasks" },
            NotificationChannel(CHANNEL_AGENT, "AI agent", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Confirmations of actions the AI agent performed" },
            NotificationChannel(CHANNEL_SYSTEM, "System status", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Alerts such as all Gemini API keys failing" },
            NotificationChannel(CHANNEL_FOREGROUND, "Reminder service", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "Persistent low-priority indicator that keeps reminders reliable" },
        ).forEach { nm.createNotificationChannel(it) }
    }

    private suspend fun enabled(context: Context, type: NotifType): Boolean =
        AppSettings.get(context).notifEnabled(type).first()

    private fun launchPi(context: Context, taskId: Long?): PendingIntent {
        val i = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra("open_task_id", taskId ?: -1L)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(context, (taskId ?: 0L).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun post(context: Context, id: Int, channel: String, title: String, body: String,
                     taskId: Long?, ongoing: Boolean = false, priority: Int = NotificationCompat.PRIORITY_DEFAULT) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(com.forgebuild.taskflow.R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(launchPi(context, taskId))
            .setAutoCancel(!ongoing).setOngoing(ongoing).setPriority(priority)
            .build()
        runCatching { nm.notify(id, n) }
    }

    suspend fun dueNow(context: Context, taskId: Long, title: String) {
        if (!enabled(context, NotifType.DUE)) return
        post(context, 1000 + taskId.hashCode().mod(10000), CHANNEL_REMINDERS,
            "Due now: $title", "This task's time has arrived. It's pinned at the top of your list.",
            taskId, priority = NotificationCompat.PRIORITY_HIGH)
    }

    suspend fun overdue(context: Context, taskId: Long, title: String) {
        if (!enabled(context, NotifType.OVERDUE)) return
        post(context, 20000 + taskId.hashCode().mod(10000), CHANNEL_REMINDERS,
            "Overdue: $title", "You didn't complete this at its scheduled time.",
            taskId, priority = NotificationCompat.PRIORITY_HIGH)
    }

    suspend fun dailyAgenda(context: Context, body: String) {
        if (!enabled(context, NotifType.DAILY_AGENDA)) return
        post(context, 30001, CHANNEL_AGENDA, "Today's agenda", body, null)
    }

    suspend fun agentConfirmation(context: Context, summary: String) {
        if (!enabled(context, NotifType.AGENT_CONFIRMATION)) return
        post(context, 40001, CHANNEL_AGENT, "TaskFlow AI", summary, null)
    }

    suspend fun apiKeysFailed(context: Context) {
        if (!enabled(context, NotifType.API_KEYS_FAILED)) return
        post(context, 50001, CHANNEL_SYSTEM, "AI unavailable",
            "All Gemini API keys failed. Add a working key in Settings to restore the AI agent.",
            null, priority = NotificationCompat.PRIORITY_HIGH)
    }

    suspend fun approachingDeadline(context: Context, taskId: Long, title: String, minutes: Int) {
        if (!enabled(context, NotifType.APPROACHING_DEADLINE)) return
        post(context, 60000 + taskId.hashCode().mod(10000), CHANNEL_REMINDERS,
            "Coming up: $title", "Due in $minutes minutes.", taskId)
    }

    suspend fun recurringInstance(context: Context, title: String) {
        if (!enabled(context, NotifType.RECURRING_INSTANCE)) return
        post(context, 70000 + title.hashCode().mod(10000), CHANNEL_SYSTEM,
            "Recurring task scheduled", "Next instance of \"$title\" was created.", null,
            priority = NotificationCompat.PRIORITY_LOW)
    }

    fun foregroundNotification(context: Context): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(com.forgebuild.taskflow.R.drawable.ic_launcher_foreground)
            .setContentTitle("TaskFlow reminders active")
            .setContentText("Keeping fixed-time reminders reliable. Android blocks always-on background work; this low-priority indicator is the compliant alternative.")
            .setContentIntent(launchPi(context, null))
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
