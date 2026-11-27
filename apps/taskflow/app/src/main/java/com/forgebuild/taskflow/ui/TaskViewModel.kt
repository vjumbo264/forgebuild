package com.forgebuild.taskflow.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgebuild.taskflow.data.DayAccounting
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.Task
import com.forgebuild.taskflow.data.TaskCategory
import com.forgebuild.taskflow.data.TaskCategoryHelper
import com.forgebuild.taskflow.data.TaskRepository
import com.forgebuild.taskflow.reminder.DueForegroundService
import com.forgebuild.taskflow.reminder.ReminderScheduler
import com.forgebuild.taskflow.settings.AppSettings
import com.forgebuild.taskflow.settings.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimeRangeView { TODAY, WEEK, MONTH, YEAR }

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModel(app: Application) : AndroidViewModel(app) {
    val repo = TaskRepository.get(app)
    val settings = AppSettings.get(app)
    private val scheduler = ReminderScheduler(app)

    private val _timeRange = MutableStateFlow(TimeRangeView.TODAY)
    val timeRange: StateFlow<TimeRangeView> = _timeRange.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Navigation stack of parent ids; empty = top level. */
    private val _path = MutableStateFlow<List<Long>>(emptyList())
    val path: StateFlow<List<Long>> = _path.asStateFlow()
    val currentParentId: StateFlow<Long?> = _path.map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** All active children at current level. */
    private val rawActiveTasks: StateFlow<List<Task>> = currentParentId
        .flatMapLatest { repo.activeChildren(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Filtered tasks according to the selected TimeRangeView + search query, using shared [DayAccounting] rules. */
    val tasks: StateFlow<List<Task>> = combine(rawActiveTasks, _timeRange, _searchQuery) { list, range, query ->
        val now = System.currentTimeMillis()
        fun daysInRange(): List<Long> {
            val todayStart = DayAccounting.dayStart(now)
            return when (range) {
                TimeRangeView.TODAY -> listOf(todayStart)
                TimeRangeView.WEEK -> {
                    val weekCal = Calendar.getInstance().apply { timeInMillis = now }
                    weekCal.set(Calendar.DAY_OF_WEEK, weekCal.firstDayOfWeek)
                    weekCal.set(Calendar.HOUR_OF_DAY, 0); weekCal.set(Calendar.MINUTE, 0)
                    weekCal.set(Calendar.SECOND, 0); weekCal.set(Calendar.MILLISECOND, 0)
                    (0 until 7).map { weekCal.timeInMillis + it * DayAccounting.DAY_MILLIS }
                }
                TimeRangeView.MONTH -> {
                    val m = Calendar.getInstance().apply { timeInMillis = now }
                    val dim = m.getActualMaximum(Calendar.DAY_OF_MONTH)
                    m.set(Calendar.DAY_OF_MONTH, 1); m.set(Calendar.HOUR_OF_DAY, 0); m.set(Calendar.MINUTE, 0)
                    m.set(Calendar.SECOND, 0); m.set(Calendar.MILLISECOND, 0)
                    (0 until dim).map { m.timeInMillis + it * DayAccounting.DAY_MILLIS }
                }
                TimeRangeView.YEAR -> {
                    val y = Calendar.getInstance().apply { timeInMillis = now }
                    val diy = y.getActualMaximum(Calendar.DAY_OF_YEAR)
                    y.set(Calendar.DAY_OF_YEAR, 1); y.set(Calendar.HOUR_OF_DAY, 0); y.set(Calendar.MINUTE, 0)
                    y.set(Calendar.SECOND, 0); y.set(Calendar.MILLISECOND, 0)
                    (0 until diy).map { y.timeInMillis + it * DayAccounting.DAY_MILLIS }
                }
            }
        }
        val days = daysInRange()
        val q = query.trim()
        list.filter { t ->
            val inRange = if (t.dueNow) {
                true
            } else when {
                t.fixedTime == null && !t.isRecurringTemplate ->
                    days.any { DayAccounting.touchesDay(t, it, now) }
                t.isRecurringTemplate ->
                    days.any { DayAccounting.occursOnDay(t, it) != null }
                else -> days.any { DayAccounting.touchesDay(t, it, now) }
            }
            if (!inRange) return@filter false
            if (q.isNotEmpty()) {
                t.title.contains(q, ignoreCase = true) ||
                    t.info.contains(q, ignoreCase = true) ||
                    t.category.label.contains(q, ignoreCase = true)
            } else {
                true
            }
        }.distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Tasks grouped into all four Eisenhower Matrix categories (always containing all 4 keys). */
    val groupedTasks: StateFlow<Map<TaskCategory, List<Task>>> = tasks
        .map { TaskCategoryHelper.groupByCategory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskCategoryHelper.groupByCategory(emptyList()))

    /** Task counts per Eisenhower Matrix category. */
    val categoryCounts: StateFlow<Map<TaskCategory, Int>> = tasks
        .map { TaskCategoryHelper.countByCategory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskCategoryHelper.countByCategory(emptyList()))

    fun tasksForCategory(category: TaskCategory): List<Task> =
        TaskCategoryHelper.filterByCategory(tasks.value, category)

    /** Completed tasks list. */
    val completedTasks: StateFlow<List<Task>> = repo.completedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Unfinished (missed fixed-time) tasks list. */
    val unfinishedTasks: StateFlow<List<Task>> = repo.unfinishedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Breadcrumb titles along the current path. */
    val breadcrumbs: StateFlow<List<Pair<Long, String>>> = _path.map { ids ->
        ids.mapNotNull { id -> repo.get(id)?.let { id to it.title } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    val onboardingDone: StateFlow<Boolean> = settings.onboardingDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val retentionDays: StateFlow<Int> = settings.retentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 14)

    /** Time tracking for the day — shared [DayAccounting] rules so every task category counts. */
    val allocatedMinutesToday: StateFlow<Long> = repo.allActive().map { all ->
        val now = System.currentTimeMillis()
        DayAccounting.allocatedMinutesForDay(all, now, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val elapsedMinutesToday: StateFlow<Long> = repo.allActive().map {
        val now = System.currentTimeMillis()
        ((now - DayAccounting.dayStart(now)) / 60000L).coerceIn(0L, 1440L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val usedDayMinutes: StateFlow<Long> = combine(allocatedMinutesToday, elapsedMinutesToday) { alloc, elapsed ->
        (elapsed + alloc).coerceIn(0L, 1440L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val pomodoroEnabled: StateFlow<Boolean> = settings.pomodoroEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pomodoroWorkMinutes: StateFlow<Int> = settings.pomodoroWorkMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 25)
    val pomodoroBreakMinutes: StateFlow<Int> = settings.pomodoroBreakMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val remainingMinutesToday: StateFlow<Long> = allocatedMinutesToday.map { allocated ->
        val now = System.currentTimeMillis()
        val minutesUntilMidnight = ((DayAccounting.nextDayStart(now) - now) / 60000L).coerceAtLeast(0L)
        (minutesUntilMidnight - allocated).coerceAtLeast(0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        repo.onTimedTasksChanged = { scheduler.rescheduleAll() }
        repo.onRecurringInstance = { t ->
            com.forgebuild.taskflow.notify.NotificationHub.recurringInstance(getApplication(), t.title)
        }
        repo.onOverdue = { t ->
            com.forgebuild.taskflow.notify.NotificationHub.overdue(getApplication(), t.id, t.title)
        }
        viewModelScope.launch {
            scheduler.rescheduleAll()
            repo.sweepMissed()
            repo.transitionDueNow()
            val days = settings.retentionDays.first()
            repo.purgeExpiredCompleted(days)
            repo.purgeExpiredMissed(days)
        }
    }

    fun setTimeRange(range: TimeRangeView) { _timeRange.value = range }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun openTask(taskId: Long) { _path.value = _path.value + taskId }
    fun navigateToBreadcrumb(index: Int) {
        if (index < 0) _path.value = emptyList()
        else _path.value = _path.value.take(index + 1)
    }
    fun navigateUp(): Boolean {
        if (_path.value.isEmpty()) return false
        _path.value = _path.value.dropLast(1)
        return true
    }

    fun addTask(
        title: String,
        durationMinutes: Long = 30L,
        fixedTime: Long? = null,
        scheduledDate: Long? = null,
        recurrence: Recurrence = Recurrence.NONE,
        weekdaysMask: Int = 0,
        info: String = "",
        recurrenceEndDate: Long? = null,
        parentId: Long? = null,
        category: TaskCategory = TaskCategory.DEFAULT
    ) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        val effectiveParent = parentId ?: _path.value.lastOrNull()
        val dur = durationMinutes.coerceAtLeast(1L)
        val now = System.currentTimeMillis()
        val proposed = Task(
            title = "",
            rank = 0.0,
            durationMinutes = dur,
            fixedTime = fixedTime,
            scheduledDate = scheduledDate,
            category = category
        )
        val todayStart = DayAccounting.dayStart(now)
        val isFutureDay = scheduledDate != null && scheduledDate > todayStart
        val todaySegment = if (isFutureDay) 0L else if (fixedTime == null) dur else DayAccounting.minutesOnDay(proposed, now, now)
        val isToday = !isFutureDay && (fixedTime == null || todaySegment > 0L)
        if (isToday) {
            val remaining = repo.getRemainingMinutesToday()
            if (todaySegment > remaining) {
                _userMessage.emit("Cannot add \"$title\": today's portion (${todaySegment}m) exceeds remaining unallocated time today (${remaining}m left).")
                return@launch
            }
        }
        if (!isFutureDay && fixedTime == null && dur > repo.getRemainingMinutesToday()) {
            _userMessage.emit("Cannot add \"$title\": tasks without a fixed time cannot run overnight — ${dur}m does not fit in today.")
            return@launch
        }
        repo.childDurationError(effectiveParent, dur)?.let {
            _userMessage.emit(it)
            return@launch
        }
        repo.create(
            title = title.trim(),
            parentId = effectiveParent,
            durationMinutes = dur,
            fixedTime = fixedTime,
            scheduledDate = scheduledDate,
            recurrence = recurrence,
            weekdaysMask = weekdaysMask,
            info = info.trim(),
            recurrenceEndDate = if (recurrence != Recurrence.NONE) recurrenceEndDate else null,
            category = category
        )
    }

    fun moveTaskCategory(task: Task, category: TaskCategory) = viewModelScope.launch {
        if (task.category == category) return@launch
        repo.moveToCategory(task.id, category)
        _userMessage.emit("Moved \"${task.title}\" to ${category.label}.")
    }

    fun toggleComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, !task.completed) }

    // ---- Countdown timer controls (play / pause / extend / complete) ----
    fun timerToggle(task: Task) = viewModelScope.launch {
        when (com.forgebuild.taskflow.data.TimerEngine.stateOf(task)) {
            com.forgebuild.taskflow.data.TimerEngine.TimerState.RUNNING -> repo.timerPause(task.id)
            else -> {
                repo.timerStart(task.id)
                repo.get(task.id)?.timerEndsAt?.let { scheduler.scheduleTimerEnd(task.id, it) }
            }
        }
    }

    fun timerExtend(task: Task, minutes: Long) = viewModelScope.launch {
        repo.timerExtend(task.id, minutes)
        repo.get(task.id)?.timerEndsAt?.let { scheduler.scheduleTimerEnd(task.id, it) }
    }

    fun setPomodoroEnabled(enabled: Boolean) = viewModelScope.launch { settings.setPomodoroEnabled(enabled) }
    fun setPomodoroWorkMinutes(m: Int) = viewModelScope.launch { settings.setPomodoroWorkMinutes(m) }
    fun setPomodoroBreakMinutes(m: Int) = viewModelScope.launch { settings.setPomodoroBreakMinutes(m) }
    fun timerComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, true) }
    fun restoreMissed(task: Task) = viewModelScope.launch { repo.restoreMissed(task.id) }
    fun clearMissed() = viewModelScope.launch { repo.clearMissed() }
    fun restoreTask(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, false) }
    fun delete(task: Task) = viewModelScope.launch { repo.deleteTree(task.id) }
    fun clearCompleted() = viewModelScope.launch { repo.clearCompleted() }

    fun saveEdit(task: Task, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val dur = task.durationMinutes.coerceAtLeast(1L)
        val todayStart = DayAccounting.dayStart(now)
        val isFutureDay = task.scheduledDate != null && task.scheduledDate > todayStart
        val todaySegment = if (isFutureDay) 0L else if (task.fixedTime == null) dur else DayAccounting.minutesOnDay(task, now, now)
        val isToday = !isFutureDay && (task.fixedTime == null || todaySegment > 0L)
        if (isToday) {
            val remainingWithOld = repo.getRemainingMinutesToday(excludeTaskId = task.id)
            if (todaySegment > remainingWithOld) {
                onResult(false, "Duration (${dur}m) puts ${todaySegment}m on today, exceeding remaining time left today (${remainingWithOld}m available).")
                return@launch
            }
        }
        if (!isFutureDay && task.fixedTime == null && dur > repo.getRemainingMinutesToday(excludeTaskId = task.id)) {
            onResult(false, "Tasks without a fixed time cannot run overnight — ${dur}m does not fit in today.")
            return@launch
        }
        repo.childDurationError(task.parentId, dur, excludeChildId = task.id)?.let {
            onResult(false, it)
            return@launch
        }
        repo.parentShrinkError(task.id, dur)?.let {
            onResult(false, it)
            return@launch
        }
        repo.update(task)
        onResult(true, null)
    }

    fun moveRelative(task: Task, delta: Int) = viewModelScope.launch { repo.moveRelative(task.id, delta) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setRetentionDays(days: Int) = viewModelScope.launch {
        settings.setRetentionDays(days)
        repo.purgeExpiredCompleted(days)
        repo.purgeExpiredMissed(days)
    }
    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone() }
    fun setForegroundService(active: Boolean) = viewModelScope.launch {
        settings.setForegroundActive(active)
        if (active) DueForegroundService.start(getApplication()) else DueForegroundService.stop(getApplication())
    }
}
