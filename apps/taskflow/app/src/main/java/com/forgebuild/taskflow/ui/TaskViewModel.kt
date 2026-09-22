package com.forgebuild.taskflow.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgebuild.taskflow.data.PriorityRank
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.Task
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

    /** Navigation stack of parent ids; empty = top level. */
    private val _path = MutableStateFlow<List<Long>>(emptyList())
    val path: StateFlow<List<Long>> = _path.asStateFlow()

    val currentParentId: StateFlow<Long?> = _path.map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** All active children at current level. */
    private val rawActiveTasks: StateFlow<List<Task>> = currentParentId
        .flatMapLatest { repo.activeChildren(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Filtered tasks according to the selected TimeRangeView. */
    val tasks: StateFlow<List<Task>> = combine(rawActiveTasks, _timeRange) { list, range ->
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val startOfToday = c.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfToday = c.apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        when (range) {
            TimeRangeView.TODAY -> {
                list.filter { t ->
                    if (t.dueNow) return@filter true
                    if (t.fixedTime != null) {
                        t.fixedTime in startOfToday..endOfToday
                    } else {
                        true // un-timed tasks belong to today
                    }
                }
            }
            TimeRangeView.WEEK -> {
                val weekCal = Calendar.getInstance().apply { timeInMillis = now }
                weekCal.set(Calendar.DAY_OF_WEEK, weekCal.firstDayOfWeek)
                weekCal.set(Calendar.HOUR_OF_DAY, 0); weekCal.set(Calendar.MINUTE, 0)
                weekCal.set(Calendar.SECOND, 0); weekCal.set(Calendar.MILLISECOND, 0)
                val weekStart = weekCal.timeInMillis
                weekCal.add(Calendar.DAY_OF_YEAR, 7)
                val weekEnd = weekCal.timeInMillis - 1

                list.filter { t ->
                    if (t.dueNow) return@filter true
                    if (t.recurrence != Recurrence.NONE) return@filter true
                    if (t.fixedTime != null) {
                        t.fixedTime in weekStart..weekEnd
                    } else {
                        true
                    }
                }
            }
            TimeRangeView.MONTH -> {
                val monthCal = Calendar.getInstance().apply { timeInMillis = now }
                monthCal.set(Calendar.DAY_OF_MONTH, 1)
                monthCal.set(Calendar.HOUR_OF_DAY, 0); monthCal.set(Calendar.MINUTE, 0)
                monthCal.set(Calendar.SECOND, 0); monthCal.set(Calendar.MILLISECOND, 0)
                val monthStart = monthCal.timeInMillis
                monthCal.add(Calendar.MONTH, 1)
                val monthEnd = monthCal.timeInMillis - 1

                list.filter { t ->
                    if (t.dueNow) return@filter true
                    if (t.recurrence != Recurrence.NONE) return@filter true
                    if (t.fixedTime != null) {
                        t.fixedTime in monthStart..monthEnd
                    } else {
                        true
                    }
                }
            }
            TimeRangeView.YEAR -> {
                val yearCal = Calendar.getInstance().apply { timeInMillis = now }
                yearCal.set(Calendar.DAY_OF_YEAR, 1)
                yearCal.set(Calendar.HOUR_OF_DAY, 0); yearCal.set(Calendar.MINUTE, 0)
                yearCal.set(Calendar.SECOND, 0); yearCal.set(Calendar.MILLISECOND, 0)
                val yearStart = yearCal.timeInMillis
                yearCal.add(Calendar.YEAR, 1)
                val yearEnd = yearCal.timeInMillis - 1

                list.filter { t ->
                    if (t.dueNow) return@filter true
                    if (t.recurrence != Recurrence.NONE) return@filter true
                    if (t.fixedTime != null) {
                        t.fixedTime in yearStart..yearEnd
                    } else {
                        true
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** Time tracking for the day. */
    val allocatedMinutesToday: StateFlow<Long> = repo.allActive().map { all ->
        val now = System.currentTimeMillis()
        val todayStart = repo.dayStart(now)
        val todayEnd = repo.dayEnd(now)
        all.filter { t ->
            if (t.isRecurringTemplate && t.recurrence != Recurrence.NONE) return@filter false
            if (t.fixedTime != null) t.fixedTime in todayStart..todayEnd
            else true
        }.sumOf { it.durationMinutes.coerceAtLeast(1L) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val remainingMinutesToday: StateFlow<Long> = allocatedMinutesToday.map { allocated ->
        val now = System.currentTimeMillis()
        val todayEnd = repo.dayEnd(now)
        val minutesUntilMidnight = ((todayEnd - now) / 60000L).coerceAtLeast(0L)
        (minutesUntilMidnight - allocated).coerceAtLeast(0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** User feedback message channel (e.g. allocation error snackbar). */
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
            // Day-rollover rules on every open: expired fixed-time tasks -> Unfinished view.
            repo.sweepMissed()
            // Auto-purge expired completed tasks based on retention setting
            val days = settings.retentionDays.first()
            repo.purgeExpiredCompleted(days)
        }
    }

    fun setTimeRange(range: TimeRangeView) { _timeRange.value = range }

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
        recurrence: Recurrence = Recurrence.NONE,
        weekdaysMask: Int = 0,
        info: String = "",
        recurrenceEndDate: Long? = null
    ) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        val dur = durationMinutes.coerceAtLeast(1L)
        val now = System.currentTimeMillis()
        val isToday = fixedTime == null || fixedTime in repo.dayStart(now)..repo.dayEnd(now)

        if (isToday) {
            val remaining = repo.getRemainingMinutesToday()
            if (dur > remaining) {
                _userMessage.emit("Cannot add \"$title\": Duration (${dur}m) exceeds remaining unallocated time today (${remaining}m left).")
                return@launch
            }
        }

        repo.create(
            title = title.trim(),
            parentId = _path.value.lastOrNull(),
            durationMinutes = dur,
            fixedTime = fixedTime,
            recurrence = recurrence,
            weekdaysMask = weekdaysMask,
            info = info.trim(),
            recurrenceEndDate = if (recurrence != Recurrence.NONE) recurrenceEndDate else null
        )
    }

    fun toggleComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, !task.completed) }
    fun restoreMissed(task: Task) = viewModelScope.launch { repo.restoreMissed(task.id) }
    fun clearMissed() = viewModelScope.launch { repo.clearMissed() }
    fun restoreTask(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, false) }
    fun delete(task: Task) = viewModelScope.launch { repo.deleteTree(task.id) }
    fun clearCompleted() = viewModelScope.launch { repo.clearCompleted() }

    fun saveEdit(task: Task, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val isToday = task.fixedTime == null || task.fixedTime in repo.dayStart(now)..repo.dayEnd(now)
        val dur = task.durationMinutes.coerceAtLeast(1L)

        if (isToday) {
            val remainingWithOld = repo.getRemainingMinutesToday(excludeTaskId = task.id)
            if (dur > remainingWithOld) {
                onResult(false, "Duration (${dur}m) exceeds remaining time left today (${remainingWithOld}m available).")
                return@launch
            }
        }

        repo.update(task)
        onResult(true, null)
    }

    fun moveRelative(task: Task, delta: Int) = viewModelScope.launch { repo.moveRelative(task.id, delta) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setRetentionDays(days: Int) = viewModelScope.launch {
        settings.setRetentionDays(days)
        repo.purgeExpiredCompleted(days)
    }
    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone() }
    fun setForegroundService(active: Boolean) = viewModelScope.launch {
        settings.setForegroundActive(active)
        if (active) DueForegroundService.start(getApplication()) else DueForegroundService.stop(getApplication())
    }
}
