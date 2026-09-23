package com.forgebuild.taskflow.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.forgebuild.taskflow.data.TaskRepository
import com.forgebuild.taskflow.notify.NotificationHub
import com.forgebuild.taskflow.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Handles exact-alarm fires: due-now, deadline lead, daily agenda, day rollover. */
class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DUE = "com.forgebuild.taskflow.action.DUE"
        const val ACTION_DUE_END = "com.forgebuild.taskflow.action.DUE_END"
        const val ACTION_TIMER_END = "com.forgebuild.taskflow.action.TIMER_END"
        const val ACTION_LEAD = "com.forgebuild.taskflow.action.LEAD"
        const val ACTION_AGENDA = "com.forgebuild.taskflow.action.AGENDA"
        const val ACTION_ROLLOVER = "com.forgebuild.taskflow.action.ROLLOVER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = TaskRepository.get(context)
                when (intent.action) {
                    ACTION_DUE -> {
                        val id = intent.getLongExtra("task_id", -1L)
                        if (id > 0) repo.markDue(id)?.let { t ->
                            if (!t.completed) NotificationHub.dueNow(context, t.id, t.title)
                            // Due-now persists for the FULL span: schedule its end-of-due alarm.
                            t.fixedTime?.let { ft ->
                                ReminderScheduler(context).scheduleDueEnd(
                                    id, ft + t.durationMinutes.coerceAtLeast(1L) * 60_000L)
                            }
                        }
                        ReminderScheduler(context).rescheduleAll()
                    }
                    ACTION_DUE_END -> {
                        // Span elapsed: degrade the loud due-now pin to the subtle overdue state.
                        repo.transitionDueNow()
                        ReminderScheduler(context).rescheduleAll()
                    }
                    ACTION_TIMER_END -> {
                        // Countdown elapsed while app was closed: ding + final-state broadcast.
                        val id = intent.getLongExtra("task_id", -1L)
                        if (id > 0) repo.get(id)?.let { t ->
                            // Stale-alarm guard: only ding when the countdown genuinely just elapsed.
                            if (!t.completed && !t.timerFinished &&
                                com.forgebuild.taskflow.data.TimerEngine.stateOf(t) ==
                                    com.forgebuild.taskflow.data.TimerEngine.TimerState.FINISHED) {
                                com.forgebuild.taskflow.notify.TimerSounds.playCompletion()
                                repo.update(t.copy(timerFinished = true, timerEndsAt = null))
                            }
                        }
                    }
                    ACTION_LEAD -> {
                        val id = intent.getLongExtra("task_id", -1L)
                        if (id > 0) repo.get(id)?.let { t ->
                            if (!t.completed) {
                                val lead = AppSettings.get(context).deadlineLeadMinutes.first()
                                NotificationHub.approachingDeadline(context, t.id, t.title, lead)
                            }
                        }
                    }
                    ACTION_AGENDA -> { postAgenda(context, repo); ReminderScheduler(context).rescheduleAll() }
                    ACTION_ROLLOVER -> { repo.rollover(); ReminderScheduler(context).rescheduleAll() }
                }
            } finally { pending.finish() }
        }
    }

    private suspend fun postAgenda(context: Context, repo: TaskRepository) {
        val todayStart = repo.dayStart(System.currentTimeMillis())
        val tomorrowStart = todayStart + 86_400_000L
        val all = repo.pendingTimed().filter { it.fixedTime != null && it.fixedTime!! < tomorrowStart }
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timed = all.sortedBy { it.fixedTime }
        val top = repo.siblingsOf(null).filter { !it.completed }.take(3)
        val sb = StringBuilder()
        if (timed.isNotEmpty()) {
            sb.append("Timed: ")
            sb.append(timed.joinToString(", ") { "${fmt.format(Date(it.fixedTime!!))} ${it.title}" })
        }
        if (top.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Top priority: ").append(top.joinToString(", ") { it.title })
        }
        if (sb.isNotEmpty()) NotificationHub.dailyAgenda(context, sb.toString())
    }
}
