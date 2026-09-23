package com.forgebuild.taskflow.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.forgebuild.taskflow.data.TaskRepository
import com.forgebuild.taskflow.notify.NotificationHub
import com.forgebuild.taskflow.settings.AppSettings
import kotlinx.coroutines.flow.first
import java.util.Calendar

/** Schedules exact alarms for fixed-time tasks, the daily agenda, and day rollover. */
class ReminderScheduler(private val context: Context) {
    private val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pi(taskId: Long, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, (action + taskId).hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(action).putExtra("task_id", taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun scheduleTask(taskId: Long, atMillis: Long) {
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi(taskId, ReminderReceiver.ACTION_DUE))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi(taskId, ReminderReceiver.ACTION_DUE))
        }
    }

    fun scheduleDeadlineLead(taskId: Long, dueMillis: Long, leadMinutes: Int) {
        val at = dueMillis - leadMinutes * 60_000L
        if (at <= System.currentTimeMillis()) return
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(taskId, ReminderReceiver.ACTION_LEAD))
        }
    }

    fun cancelTask(taskId: Long) {
        am.cancel(pi(taskId, ReminderReceiver.ACTION_DUE))
        am.cancel(pi(taskId, ReminderReceiver.ACTION_LEAD))
    }

    /** Reschedule all pending timed tasks + the daily agenda + next rollover. */
    suspend fun rescheduleAll() {
        val repo = TaskRepository.get(context)
        val settings = AppSettings.get(context)
        val leadEnabled = settings.notifEnabled(com.forgebuild.taskflow.settings.NotifType.APPROACHING_DEADLINE).first()
        val lead = settings.deadlineLeadMinutes.first()
        val now = System.currentTimeMillis()
        repo.pendingTimed().filter { (it.fixedTime ?: 0L) > now }.forEach { t ->
            scheduleTask(t.id, t.fixedTime!!)
            if (leadEnabled) scheduleDeadlineLead(t.id, t.fixedTime!!, lead)
        }
        scheduleAgenda(settings.agendaHour.first())
        scheduleRollover()
    }

    private fun scheduleAgenda(hour: Int) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val p = PendingIntent.getBroadcast(context, "agenda".hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_AGENDA),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, p)
    }

    private fun scheduleRollover() {
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val p = PendingIntent.getBroadcast(context, "rollover".hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_ROLLOVER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, p)
    }

    /** Exact alarm for when a due-now task's full span elapses (start + duration). */
    fun scheduleDueEnd(taskId: Long, atMillis: Long) {
        if (atMillis <= System.currentTimeMillis()) return
        val p = pi(taskId, ReminderReceiver.ACTION_DUE_END)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p)
    }

    /** Exact alarm for a running countdown finishing while the app is closed. */
    fun scheduleTimerEnd(taskId: Long, atMillis: Long) {
        if (atMillis <= System.currentTimeMillis()) return
        val p = pi(taskId, ReminderReceiver.ACTION_TIMER_END)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p)
    }
}