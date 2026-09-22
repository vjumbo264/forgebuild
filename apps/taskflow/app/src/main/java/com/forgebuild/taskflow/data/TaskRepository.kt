package com.forgebuild.taskflow.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Single source of truth for the task graph.
 * Handles priority rank generation, recursive deletion, recurrence spawning,
 * due-now pinning, and daily duration allocation tracking.
 */
class TaskRepository(private val dao: TaskDao) {
    /** Hook wired to ReminderScheduler.rescheduleAll. */
    var onTimedTasksChanged: (suspend () -> Unit)? = null
    /** Low-priority ping hook when a recurring instance is generated. */
    var onRecurringInstance: (suspend (Task) -> Unit)? = null
    /** Invoked at day rollover for tasks that were due but never completed. */
    var onOverdue: (suspend (Task) -> Unit)? = null

    fun activeChildren(parentId: Long?): Flow<List<Task>> = dao.observeActiveChildren(parentId)
    fun children(parentId: Long?): Flow<List<Task>> = dao.observeChildren(parentId)
    fun completedTasks(): Flow<List<Task>> = dao.observeCompleted()
    fun byId(id: Long): Flow<Task?> = dao.observeById(id)
    fun all(): Flow<List<Task>> = dao.observeAll()
    fun allActive(): Flow<List<Task>> = dao.observeAllActive()

    suspend fun get(id: Long): Task? = dao.getById(id)
    suspend fun childCount(parentId: Long?): Int = dao.childCount(parentId)
    suspend fun activeChildCount(parentId: Long?): Int = dao.activeChildCount(parentId)
    suspend fun siblingsOf(parentId: Long?): List<Task> = withContext(Dispatchers.IO) { dao.children(parentId) }
    suspend fun activeSiblingsOf(parentId: Long?): List<Task> = withContext(Dispatchers.IO) { dao.activeChildren(parentId) }

    suspend fun create(
        title: String,
        parentId: Long? = null,
        rank: Double? = null,
        durationMinutes: Long = 30L,
        fixedTime: Long? = null,
        recurrence: Recurrence = Recurrence.NONE,
        weekdaysMask: Int = 0,
        info: String = ""
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
            recurrence = recurrence,
            weekdaysMask = weekdaysMask,
            info = info,
            parentId = parentId,
            isRecurringTemplate = template
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
        val completedAt = if (done) System.currentTimeMillis() else null
        dao.update(t.copy(completed = done, completedAt = completedAt, dueNow = false, updatedAt = System.currentTimeMillis()))
        if (done && t.recurrence != Recurrence.NONE) spawnNextInstance(t)
        reschedule()
    }

    suspend fun clearCompleted() = withContext(Dispatchers.IO) {
        dao.clearCompleted()
    }

    suspend fun purgeExpiredCompleted(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        if (retentionDays <= 0) return@withContext 0
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 86_400_000L)
        dao.deleteCompletedBefore(cutoff)
    }

    private suspend fun spawnNextInstance(template: Task) {
        val tpl = dao.getById(template.seriesId ?: template.id) ?: template
        val next = RecurrenceEngine.nextAfter(tpl, System.currentTimeMillis()) ?: return
        val siblings = dao.children(tpl.parentId)
        val inst = Task(
            title = tpl.title,
            rank = PriorityRank.between(null, siblings.lastOrNull()?.rank),
            durationMinutes = tpl.durationMinutes,
            fixedTime = next,
            recurrence = Recurrence.NONE,
            weekdaysMask = tpl.weekdaysMask,
            info = tpl.info,
            parentId = tpl.parentId,
            seriesId = tpl.seriesId ?: tpl.id
        )
        val id = dao.insert(inst)
        onRecurringInstance?.invoke(inst.copy(id = id))
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
        val today = dayStart(System.currentTimeMillis())
        val stale = dao.all().filter { it.dueNow && it.dueNowDay != null && it.dueNowDay!! < today }
        stale.filter { !it.completed }.forEach { onOverdue?.invoke(it) }
        stale.forEach { dao.update(it.copy(dueNow = false)) }
        dao.recurringTemplates().forEach { tpl ->
            val hasPending = dao.children(tpl.parentId).any { it.seriesId == tpl.id && !it.completed }
            if (!hasPending) {
                RecurrenceEngine.nextAfter(tpl, System.currentTimeMillis())?.let { next ->
                    val siblings = dao.children(tpl.parentId)
                    val inst = Task(
                        title = tpl.title,
                        rank = PriorityRank.between(null, siblings.lastOrNull()?.rank),
                        durationMinutes = tpl.durationMinutes,
                        fixedTime = next,
                        recurrence = Recurrence.NONE,
                        weekdaysMask = tpl.weekdaysMask,
                        info = tpl.info,
                        parentId = tpl.parentId,
                        seriesId = tpl.id
                    )
                    val id = dao.insert(inst)
                    onRecurringInstance?.invoke(inst.copy(id = id))
                }
            }
        }
        reschedule()
    }

    suspend fun pendingTimed(): List<Task> = withContext(Dispatchers.IO) { dao.pendingTimedTasks() }

    suspend fun getAllocatedMinutesToday(excludeTaskId: Long? = null): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val todayStart = dayStart(now)
        val todayEnd = dayEnd(now)
        val active = dao.allActive()
        active.filter { t ->
            if (t.id == excludeTaskId) return@filter false
            if (t.isRecurringTemplate && t.recurrence != Recurrence.NONE) return@filter false
            if (t.fixedTime != null) {
                t.fixedTime in todayStart..todayEnd
            } else {
                true
            }
        }.sumOf { it.durationMinutes.coerceAtLeast(1L) }
    }

    suspend fun getRemainingMinutesToday(excludeTaskId: Long? = null): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val todayEnd = dayEnd(now)
        val minutesUntilMidnight = ((todayEnd - now) / 60000L).coerceAtLeast(0L)
        val allocated = getAllocatedMinutesToday(excludeTaskId)
        (minutesUntilMidnight - allocated).coerceAtLeast(0L)
    }

    fun dayStart(millis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun dayEnd(millis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }

    private suspend fun reschedule() { onTimedTasksChanged?.invoke() }

    companion object {
        @Volatile private var instance: TaskRepository? = null

        fun get(context: Context): TaskRepository =
            instance ?: synchronized(this) {
                instance ?: TaskRepository(TaskFlowDb.get(context).taskDao()).also { instance = it }
            }
    }
}
