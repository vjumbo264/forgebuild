package com.forgebuild.taskflow.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgebuild.taskflow.data.PriorityRank
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.RecurrenceEngine
import com.forgebuild.taskflow.data.Task
import com.forgebuild.taskflow.data.TaskRepository
import com.forgebuild.taskflow.reminder.DueForegroundService
import com.forgebuild.taskflow.reminder.ReminderScheduler
import com.forgebuild.taskflow.settings.AppSettings
import com.forgebuild.taskflow.settings.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModel(app: Application) : AndroidViewModel(app) {
    val repo = TaskRepository.get(app)
    val settings = AppSettings.get(app)
    private val scheduler = ReminderScheduler(app)

    /** Navigation stack of parent ids; empty = top level. */
    private val _path = MutableStateFlow<List<Long>>(emptyList())
    val path: StateFlow<List<Long>> = _path.asStateFlow()
    val currentParentId: StateFlow<Long?> = _path.map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tasks: StateFlow<List<Task>> = currentParentId.flatMapLatest { repo.children(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Breadcrumb titles along the current path. */
    val breadcrumbs: StateFlow<List<Pair<Long, String>>> = _path.map { ids ->
        ids.mapNotNull { id -> repo.get(id)?.let { id to it.title } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    val onboardingDone: StateFlow<Boolean> = settings.onboardingDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        // wire repository hooks once
        repo.onTimedTasksChanged = { scheduler.rescheduleAll() }
        repo.onRecurringInstance = { t ->
            com.forgebuild.taskflow.notify.NotificationHub.recurringInstance(getApplication(), t.title)
        }
        repo.onOverdue = { t ->
            com.forgebuild.taskflow.notify.NotificationHub.overdue(getApplication(), t.id, t.title)
        }
        viewModelScope.launch { scheduler.rescheduleAll() }
    }

    fun openTask(taskId: Long) { _path.value = _path.value + taskId }
    fun navigateToBreadcrumb(index: Int) { _path.value = _path.value.take(index + 1) }
    fun navigateUp(): Boolean {
        if (_path.value.isEmpty()) return false
        _path.value = _path.value.dropLast(1)
        return true
    }

    fun addTask(title: String, fixedTime: Long?, recurrence: Recurrence, weekdaysMask: Int, info: String) =
        viewModelScope.launch {
            if (title.isNotBlank()) {
                repo.create(title.trim(), _path.value.lastOrNull(), null, fixedTime, recurrence, weekdaysMask, info.trim())
            }
        }

    fun toggleComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, !task.completed) }
    fun delete(task: Task) = viewModelScope.launch { repo.deleteTree(task.id) }
    fun saveEdit(task: Task) = viewModelScope.launch { repo.update(task) }
    fun moveRelative(task: Task, delta: Int) = viewModelScope.launch { repo.moveRelative(task.id, delta) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone() }

    fun setForegroundService(active: Boolean) = viewModelScope.launch {
        settings.setForegroundActive(active)
        if (active) DueForegroundService.start(getApplication()) else DueForegroundService.stop(getApplication())
    }
}
