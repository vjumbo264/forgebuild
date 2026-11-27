package com.forgebuild.taskflow.data

import android.content.Context
import com.forgebuild.taskflow.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Single source of truth for the task graph.
 * Handles priority rank generation, recursive deletion, recurrence spawning,
 * due-now pinning, Eisenhower Matrix category management, and daily duration allocation tracking.
 */
class TaskRepository(private val dao: TaskDao, private val appContext: Context? = null) {

    /** Hook wired to ReminderScheduler.rescheduleAll. */
    var onTimedTasksChanged: (suspend () -> Unit)? = null
    /** Low-priority ping hook when a recurring instance is generated. */
    var onRecurringInstance: (suspend (Task) -> Unit)? = null
    /** Invoked at day rollover for tasks that were due but never completed. */
    var onOverdue: (suspend (Task) -> Unit)? = null

    fun activeChildren(parentId: Long?): Flow<List<Task>> = dao.observeActiveChildren(parentId)
    fun observeByCategory(parentId: Long?, category: TaskCategory): Flow<List<Task>> =
        dao.observeActiveChildrenByCategory(parentId, category)
    fun children(parentId: Long?): Flow<List<Task>> = dao.observeChildren(parentId)
    fun completedTasks(): Flow<List<Task>> = dao.observeCompleted()
    fun byId(id: Long): Flow<Task?> = dao.observeById(id)
    fun all(): Flow<List<Task>> = dao.observeAll()
    fun allActive(): Flow<List<Task>> = dao.observeAllActive()

    suspend fun get(id: Long): Task? = dao.getById(id)
    /** One-shot snapshot of all active tasks (used by the foreground service timer watcher). */
    suspend fun allActiveNow(): List<Task> = withContext(Dispatchers.IO) { dao.allActive() }
    suspend fun allTasksIncludingCompleted(): List<Task> = withContext(Dispatchers.IO) { dao.all() }
    suspend fun childCount(parentId: Long?): Int = dao.childCount(parentId)
    suspend fun activeChildCount(parentId: Long?): Int = dao.activeChildCount(parentId)
    suspend fun siblingsOf(parentId: Long?): List<Task> = withContext(Dispatchers.IO) { dao.children(parentId) }
    suspend fun activeSiblingsOf(parentId: Long?): List<Task> = withContext(Dispatchers.IO) { dao.activeChildren(parentId) }

    suspend fun tasksByCategory(category: TaskCategory, parentId: Long? = null): List<Task> = withContext(Dispatchers.IO) {
        dao.activeChildrenByCategory(parentId, category)
    }

    suspend fun countByCategory(parentId: Long? = null): Map<TaskCategory, Int> = withContext(Dispatchers.IO) {
        TaskCategoryHelper.countByCategory(dao.activeChildren(parentId))
    }

    suspend fun moveToCategory(id: Long, category: TaskCategory, includeSubtasks: Boolean = false): Task? =
        withContext(Dispatchers.IO) {
            val t = dao.getById(id) ?: return@withContext null
            val now = System.currentTimeMillis()
            val updated = t.copy(category = category, updatedAt = now)
            dao.update(updated)
            if (includeSubtasks) {
                moveSubtasksCategory(id, category, now)
            }
            updated
        }

    private suspend fun moveSubtasksCategory(parentId: Long, category: TaskCategory, now: Long) {
        dao.children(parentId).forEach { child ->
            dao.update(child.copy(category = category, updatedAt = now))
            moveSubtasksCategory(child.id, category, now)
        }
    }

    /** Find an active task by full or partial title match, optionally scoped to [category]. */
    suspend fun findByTitle(query: String, category: TaskCategory? = null): Task? = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext null
        val all = dao.allActive()
        val scoped = if (category != null) all.filter { it.category == category } else all
        scoped.firstOrNull { it.title.equals(q, ignoreCase = true) }
            ?: scoped.firstOrNull { it.title.contains(q, ignoreCase = true) }
            ?: if (category != null) {
                all.firstOrNull { it.title.equals(q, ignoreCase = true) }
                    ?: all.firstOrNull { it.title.contains(q, ignoreCase = true) }
            } else null
    }

    suspend fun create(
        title: String,
        parentId: Long? = null,
        rank: Double? = null,
        durationMinutes: Long = 30L,
        fixedTime: Long? = null,
        scheduledDate: Long? = null,
        recurrence: Recurrence = Recurrence.NONE,
        weekdaysMask: Int = 0,
        info: String = "",
        recurrenceEndDate: Long? = null,
        category: TaskCategory = TaskCategory.DEFAULT
    ): Task = withContext(Dispatchers.IO) {
        val siblings = dao.children(parentId)
        val r = rank ?: PriorityRank.between(null, siblings.lastOrNull()?.rank)
        val template = recurrence != Recurrence.NONE
        val dur = durationMinutes.coerceAtLeast(1L)
        val t = Task(
            title = title,
            rank = r,
            durationMinutes = dur,
            fixedTime = fixedTime,
            scheduledDate = scheduledDate,
            recurrence = recurrence,
            weekdaysMask = weekdaysMask,
            info = info,
            category = category,
            parentId = parentId,
            isRecurringTemplate = template,
            recurrenceEndDate = if (template) recurrenceEndDate else null
        )
        val id = dao.insert(t)
        val saved = t.copy(id = id, seriesId = if (template) id else null)
        if (template) dao.update(saved)
        reschedule()
        saved
    }

    suspend fun update(task: Task): Task = withContext(Dispatchers.IO) {
        val saved = task.copy(
            durationMinutes = task.durationMinutes.coerceAtLeast(1L),
            updatedAt = System.currentTimeMillis()
        )
        dao.update(saved)
        reschedule()
        saved
    }

    suspend fun deleteTree(id: Long) = withContext(Dispatchers.IO) {
        deleteRecursive(id)
        reschedule()
    }

    private suspend fun deleteRecursive(id: Long) {
        dao.children(id).forEach { deleteRecursive(it.id) }
        dao.deleteById(id)
    }

    suspend fun setCompleted(id: Long, done: Boolean) = withContext(Dispatchers.IO) {
        val t = dao.getById(id) ?: return@withContext
        val now = System.currentTimeMillis()
        if (done && t.isRecurringTemplate) {
            val history = Task(
                title = t.title,
                rank = t.rank,
                durationMinutes = t.durationMinutes,
                fixedTime = t.fixedTime,
                recurrence = Recurrence.NONE,
                weekdaysMask = t.weekdaysMask,
                info = t.info,
                category = t.category,
                parentId = t.parentId,
                completed = true,
                completedAt = now,
                updatedAt = now,
                seriesId = t.seriesId ?: t.id
            )
            dao.insert(history)
            val next = RecurrenceEngine.nextAfter(t, now)
            if (next != null && (t.recurrenceEndDate == null || next <= t.recurrenceEndDate)) {
                dao.update(t.copy(fixedTime = next, dueNow = false, missed = false, missedAt = null, updatedAt = now))
            } else {
                dao.update(t.copy(completed = true, completedAt = now, dueNow = false, updatedAt = now))
            }
            reschedule()
            return@withContext
        }
        dao.update(TimerEngine.clear(t.copy(completed = done, completedAt = if (done) now else null, dueNow = false, updatedAt = now)))
        if (done && t.recurrence != Recurrence.NONE) {
            dao.getById(t.id)?.let { cur ->
                val next = RecurrenceEngine.nextAfter(cur, now)
                if (next != null && (cur.recurrenceEndDate == null || next <= cur.recurrenceEndDate)) {
                    dao.update(cur.copy(completed = false, completedAt = null, fixedTime = next,
                        dueNow = false, missed = false, missedAt = null, updatedAt = now))
                }
            }
        }
        reschedule()
    }

    suspend fun childDurationError(parentId: Long?, durationMinutes: Long, excludeChildId: Long? = null): String? =
        withContext(Dispatchers.IO) {
            if (parentId == null) return@withContext null
            val parent = dao.getById(parentId) ?: return@withContext null
            val used = dao.children(parentId)
                .filter { it.id != excludeChildId }
                .sumOf { it.durationMinutes.coerceAtLeast(1L) }
            val total = used + durationMinutes.coerceAtLeast(1L)
            val cap = parent.durationMinutes.coerceAtLeast(1L)
            if (total > cap)
                "Sub-task durations would total ${total}m, exceeding parent \"${parent.title}\" duration (${cap}m)."
            else null
        }

    suspend fun parentShrinkError(taskId: Long, newDurationMinutes: Long): String? =
        withContext(Dispatchers.IO) {
            val kids = dao.children(taskId)
            if (kids.isEmpty()) return@withContext null
            val used = kids.sumOf { it.durationMinutes.coerceAtLeast(1L) }
            val cap = newDurationMinutes.coerceAtLeast(1L)
            if (used > cap)
                "Duration (${cap}m) is below the ${used}m already allocated to this task's sub-tasks."
            else null
        }

    suspend fun clearCompleted() = withContext(Dispatchers.IO) {
        dao.clearCompleted()
    }

    /** Missed fixed-time tasks feed for the Unfinished view. */
    fun unfinishedTasks(): Flow<List<Task>> = dao.observeMissed()

    suspend fun sweepMissed() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val todayStart = dayStart(now)
        val expired = dao.expiredFixedTime(todayStart).filter { t ->
            val spanEnd = (t.fixedTime ?: 0L) + t.durationMinutes.coerceAtLeast(1L) * 60_000L
            spanEnd <= todayStart
        }
        expired.forEach { t ->
            dao.update(t.copy(missed = true, missedAt = now, dueNow = false, updatedAt = now))
            onOverdue?.invoke(t)
        }
        if (expired.isNotEmpty()) reschedule()
    }

    suspend fun purgeExpiredMissed(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        if (retentionDays <= 0) return@withContext 0
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 86_400_000L)
        dao.deleteMissedBefore(cutoff)
    }

    suspend fun restoreMissed(id: Long) = withContext(Dispatchers.IO) {
        val t = dao.getById(id) ?: return@withContext
        dao.update(t.copy(missed = false, missedAt = null, fixedTime = null, dueNow = false,
            updatedAt = System.currentTimeMillis()))
        reschedule()
    }

    suspend fun clearMissed() = withContext(Dispatchers.IO) {
        dao.clearMissed()
    }

    suspend fun purgeExpiredCompleted(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        if (retentionDays <= 0) return@withContext 0
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 86_400_000L)
        dao.deleteCompletedBefore(cutoff)
    }

    suspend fun move(id: Long, newParentId: Long?) = withContext(Dispatchers.IO) {
        val t = dao.getById(id) ?: return@withContext
        if (newParentId != null && wouldCycle(id, newParentId)) return@withContext
        val siblings = dao.children(newParentId).filter { it.id != id }
        dao.update(t.copy(parentId = newParentId, rank = PriorityRank.between(null, siblings.lastOrNull()?.rank),
            updatedAt = System.currentTimeMillis()))
        reschedule()
    }

    private suspend fun wouldCycle(id: Long, newParentId: Long): Boolean {
        var cur: Long? = newParentId
        var guard = 0
        while (cur != null && guard++ < 1000) {
            if (cur == id) return true
            cur = dao.getById(cur!!)?.parentId
        }
        return false
    }

    /** Insert [id] between [aboveId] and [belowId] at its current level (AI + drag). */
    suspend fun insertBetween(id: Long, aboveId: Long?, belowId: Long?) = withContext(Dispatchers.IO) {
        val t = dao.getById(id) ?: return@withContext
        val above = aboveId?.let { dao.getById(it) }
        val below = belowId?.let { dao.getById(it) }
        dao.update(t.copy(rank = PriorityRank.between(above?.rank, below?.rank), updatedAt = System.currentTimeMillis()))
        maybeRebalance(t.parentId)
    }

    /** Move by positional delta within its level (drag-and-drop reorder). */
    suspend fun moveRelative(id: Long, delta: Int) = withContext(Dispatchers.IO) {
        if (delta == 0) return@withContext
        val t = dao.getById(id) ?: return@withContext
        val sibs = dao.children(t.parentId).toMutableList()
        val idx = sibs.indexOfFirst { it.id == id }
        if (idx < 0) return@withContext
        val target = (idx + delta).coerceIn(0, sibs.size - 1)
        if (target == idx) return@withContext
        sibs.add(target, sibs.removeAt(idx))
        val above = if (target == 0) null else sibs[target - 1]
        val below = if (target == sibs.size - 1) null else sibs[target + 1]
        dao.update(t.copy(rank = PriorityRank.between(above?.rank, below?.rank), updatedAt = System.currentTimeMillis()))
        maybeRebalance(t.parentId)
    }

    /** Background rebalance when fractional ranks fragment below the minimum gap. */
    suspend fun maybeRebalance(parentId: Long?) = withContext(Dispatchers.IO) {
        val sibs = dao.children(parentId)
        if (sibs.size < 2) return@withContext
        var fragmented = false
        for (i in 0 until sibs.size - 1) {
            if (sibs[i].rank - sibs[i + 1].rank < PriorityRank.MIN_GAP) { fragmented = true; break }
        }
        if (!fragmented) return@withContext
        var r = PriorityRank.START
        sibs.forEach { dao.update(it.copy(rank = r)); r += PriorityRank.STEP }
    }

    /** Fixed-time task's moment arrived -> pin at top with due-now styling. */
    suspend fun markDue(id: Long): Task? = withContext(Dispatchers.IO) {
        val t = dao.getById(id) ?: return@withContext null
        val saved = t.copy(dueNow = true, dueNowDay = dayStart(System.currentTimeMillis()),
            updatedAt = System.currentTimeMillis())
        dao.update(saved)
        saved
    }

    /** Day rollover: overdue notifications, clear stale due-now pins, regen recurring instances. */
    suspend fun rollover() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val today = dayStart(now)
        val stale = dao.all().filter { t ->
            if (!t.dueNow || t.dueNowDay == null || t.dueNowDay!! >= today) return@filter false
            val spanEnd = (t.fixedTime ?: 0L) + t.durationMinutes.coerceAtLeast(1L) * 60_000L
            t.fixedTime == null || spanEnd <= now
        }
        stale.forEach { dao.update(it.copy(dueNow = false)) }
        sweepMissed()
        dao.recurringTemplates().forEach { tpl ->
            val anchor = tpl.fixedTime
            if (anchor != null && anchor < today && !tpl.completed && !tpl.missed) {
                RecurrenceEngine.nextAfter(tpl, now)
                    ?.takeIf { tpl.recurrenceEndDate == null || it <= tpl.recurrenceEndDate }
                    ?.let { next ->
                        dao.update(tpl.copy(fixedTime = next, dueNow = false, updatedAt = now))
                        onRecurringInstance?.invoke(tpl.copy(fixedTime = next))
                    }
            }
        }
        reschedule()
    }

    /** Degrade the loud due-now pin once a fixed-time task's full span (start + duration) has elapsed. */
    suspend fun transitionDueNow(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val expired = dao.dueNowPinned().filter { t ->
            (t.fixedTime ?: 0L) + t.durationMinutes.coerceAtLeast(1L) * 60_000L <= now
        }
        expired.forEach { dao.update(it.copy(dueNow = false, updatedAt = now)) }
        expired.size
    }

    // ---- Per-task countdown timer (TimerEngine state machine, persisted on Task) ----
    suspend fun timerStart(id: Long) = withContext(Dispatchers.IO) {
        var workMs: Long? = null
        var breakMs: Long? = null
        appContext?.let { ctx ->
            val s = AppSettings.get(ctx)
            if (s.pomodoroEnabled.first()) {
                workMs = s.pomodoroWorkMinutes.first() * 60_000L
                breakMs = s.pomodoroBreakMinutes.first() * 60_000L
            }
        }
        dao.getById(id)?.let { dao.update(TimerEngine.startOrResume(it, pomodoroWorkMs = workMs, pomodoroBreakMs = breakMs)) }
    }

    suspend fun timerPause(id: Long) = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { dao.update(TimerEngine.pause(it)) }
    }

    suspend fun timerExtend(id: Long, extraMinutes: Long) = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { dao.update(TimerEngine.extend(it, extraMinutes.coerceAtLeast(1L) * 60_000L)) }
    }

    suspend fun timerClear(id: Long) = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { dao.update(TimerEngine.clear(it)) }
    }

    suspend fun pendingTimed(): List<Task> = withContext(Dispatchers.IO) { dao.pendingTimedTasks() }

    suspend fun getAllocatedMinutesToday(excludeTaskId: Long? = null): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        DayAccounting.allocatedMinutesForDay(dao.allActive(), now, now, excludeTaskId)
    }

    suspend fun getRemainingMinutesToday(excludeTaskId: Long? = null): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val minutesUntilMidnight = ((DayAccounting.nextDayStart(now) - now) / 60000L).coerceAtLeast(0L)
        val allocated = getAllocatedMinutesToday(excludeTaskId)
        (minutesUntilMidnight - allocated).coerceAtLeast(0L)
    }

    suspend fun getAllocatedMinutesForDay(dayMillis: Long, excludeTaskId: Long? = null): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        DayAccounting.allocatedMinutesForDay(dao.allActive(), dayMillis, now, excludeTaskId)
    }

    suspend fun getRemainingMinutesForDay(dayMillis: Long, excludeTaskId: Long? = null): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val targetDayStart = DayAccounting.dayStart(dayMillis)
        val todayStart = DayAccounting.dayStart(now)
        if (targetDayStart == todayStart) {
            getRemainingMinutesToday(excludeTaskId)
        } else if (targetDayStart < todayStart) {
            0L
        } else {
            val allocated = getAllocatedMinutesForDay(dayMillis, excludeTaskId)
            (1440L - allocated).coerceAtLeast(0L)
        }
    }

    fun dayStart(millis: Long): Long = DayAccounting.dayStart(millis)
    fun dayEnd(millis: Long): Long = DayAccounting.nextDayStart(millis) - 1L
    private suspend fun reschedule() { onTimedTasksChanged?.invoke() }

    companion object {
        @Volatile private var instance: TaskRepository? = null
        fun get(context: Context): TaskRepository =
            instance ?: synchronized(this) {
                instance ?: TaskRepository(TaskFlowDb.get(context).taskDao(), context.applicationContext).also { instance = it }
            }
    }
}
