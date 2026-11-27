package com.forgebuild.taskflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** Recurrence rules; WEEKLY carries [weekdaysMask] (bit 0=Monday … bit 6=Sunday). */
enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Task visual/behavioral type for color-coding and identification:
 * 1. NORMAL: No fixed time, not recurring
 * 2. FIXED_TIME: Has fixed time, not recurring
 * 3. RECURRING_FIXED: Recurring with fixed time
 * 4. RECURRING_NO_TIME: Recurring without fixed time
 */
enum class TaskType {
    NORMAL,
    FIXED_TIME,
    RECURRING_FIXED,
    RECURRING_NO_TIME
}

/**
 * Eisenhower Matrix task categories with canonical IDs, display labels,
 * and urgency/importance attributes.
 */
enum class TaskCategory(
    val id: String,
    val label: String,
    val isUrgent: Boolean,
    val isImportant: Boolean,
    val actionHint: String
) {
    URGENT_IMPORTANT(
        id = "urgent_important",
        label = "Important and urgent",
        isUrgent = true,
        isImportant = true,
        actionHint = "Do first"
    ),
    NOT_URGENT_IMPORTANT(
        id = "not_urgent_important",
        label = "Important but not urgent",
        isUrgent = false,
        isImportant = true,
        actionHint = "Schedule"
    ),
    URGENT_NOT_IMPORTANT(
        id = "urgent_not_important",
        label = "Unimportant and urgent",
        isUrgent = true,
        isImportant = false,
        actionHint = "Delegate"
    ),
    NOT_URGENT_NOT_IMPORTANT(
        id = "not_urgent_not_important",
        label = "Unimportant but not urgent",
        isUrgent = false,
        isImportant = false,
        actionHint = "Later"
    );

    companion object {
        val DEFAULT: TaskCategory = NOT_URGENT_IMPORTANT

        fun fromId(raw: String?): TaskCategory? {
            if (raw.isNullOrBlank()) return null
            val norm = raw.trim().lowercase()
                .replace("-", "_")
                .replace(" ", "_")
            return when (norm) {
                "urgent_important", "important_and_urgent", "important_urgent", "q1", "do", "do_first" ->
                    URGENT_IMPORTANT
                "not_urgent_important", "important_but_not_urgent", "important_not_urgent", "q2", "schedule" ->
                    NOT_URGENT_IMPORTANT
                "urgent_not_important", "unimportant_and_urgent", "not_important_and_urgent", "unimportant_urgent", "q3", "delegate" ->
                    URGENT_NOT_IMPORTANT
                "not_urgent_not_important", "unimportant_but_not_urgent", "unimportant_not_urgent", "not_important_not_urgent", "q4", "eliminate", "later" ->
                    NOT_URGENT_NOT_IMPORTANT
                else -> entries.firstOrNull {
                    it.id.equals(norm, ignoreCase = true) ||
                        it.name.equals(norm, ignoreCase = true) ||
                        it.label.equals(raw.trim(), ignoreCase = true)
                }
            }
        }

        fun fromIdOrDefault(raw: String?): TaskCategory = fromId(raw) ?: DEFAULT
    }
}

class TaskConverters {
    @TypeConverter
    fun fromCategory(category: TaskCategory): String = category.id

    @TypeConverter
    fun toCategory(raw: String?): TaskCategory = TaskCategory.fromIdOrDefault(raw)
}

/** Pure selector and grouping helpers for the four Eisenhower categories. */
object TaskCategoryHelper {
    fun filterByCategory(tasks: List<Task>, category: TaskCategory): List<Task> =
        tasks.filter { it.category == category }

    fun groupByCategory(tasks: List<Task>): Map<TaskCategory, List<Task>> {
        val grouped = tasks.groupBy { it.category }
        return linkedMapOf<TaskCategory, List<Task>>().apply {
            TaskCategory.entries.forEach { cat ->
                put(cat, grouped[cat].orEmpty())
            }
        }
    }

    fun countByCategory(tasks: List<Task>): Map<TaskCategory, Int> {
        val counts = tasks.groupingBy { it.category }.eachCount()
        return linkedMapOf<TaskCategory, Int>().apply {
            TaskCategory.entries.forEach { cat ->
                put(cat, counts[cat] ?: 0)
            }
        }
    }
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Fractional priority rank (lexo-style). Higher = closer to top. Never shown raw to user. */
    val rank: Double,
    /** Mandatory duration in minutes (e.g. 15, 30, 60 min). Required for all tasks. */
    val durationMinutes: Long = 30L,
    /** Optional fixed due time, epoch millis. */
    val fixedTime: Long? = null,
    /** Optional scheduled calendar date (epoch millis at dayStart) for normal tasks without a fixed time. */
    val scheduledDate: Long? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    /** For WEEKLY recurrence: bitmask of weekdays (Calendar.MONDAY -> bit 0). */
    val weekdaysMask: Int = 0,
    /** Free-text notes ("info"). Shown via eye-icon quick-peek, edited on the edit screen. */
    val info: String = "",
    /** Eisenhower Matrix category (defaults to Important but not urgent). */
    val category: TaskCategory = TaskCategory.DEFAULT,
    /** Nullable parent for infinitely nestable sub-tasks. */
    val parentId: Long? = null,
    val completed: Boolean = false,
    /** Timestamp when completed (epoch millis); used for configurable auto-purge retention. */
    val completedAt: Long? = null,
    /** For recurring series: template stays true; generated occurrences are clones. */
    val isRecurringTemplate: Boolean = false,
    /** Link from a generated occurrence back to its template. */
    val seriesId: Long? = null,
    /** True while a fixed-time task's due moment has arrived and it is not yet completed (today). */
    val dueNow: Boolean = false,
    /** Epoch millis of the day this due-now state belongs to (cleared on rollover). */
    val dueNowDay: Long? = null,
    /** Optional recurrence expiration (epoch millis, end of that day). After it, no new instances are generated. Null = repeat indefinitely. */
    val recurrenceEndDate: Long? = null,
    /** True when a fixed-time task's time passed without completion; shown in the Unfinished view, hidden from active lists. */
    val missed: Boolean = false,
    /** When the task was marked missed (epoch millis). */
    val missedAt: Long? = null,
    /** Countdown timer: epoch millis at which the timer ends (set while RUNNING). Null when not running. */
    val timerEndsAt: Long? = null,
    /** Countdown timer: remaining milliseconds held while PAUSED. Null when not paused. */
    val timerRemainingMs: Long? = null,
    /** True after the countdown has fully elapsed, until the user extends or completes the task. */
    val timerFinished: Boolean = false,
    /** Wall-clock start of the current timer run (needed to map wall time onto a Pomodoro plan). */
    val timerStartedAt: Long? = null,
    /** Pomodoro snapshot taken at timer start: work-interval ms. Null = plain continuous countdown. */
    val pomodoroWorkMs: Long? = null,
    /** Pomodoro snapshot taken at timer start: break-interval ms. Null = plain continuous countdown. */
    val pomodoroBreakMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val taskType: TaskType get() = when {
        recurrence != Recurrence.NONE && fixedTime != null -> TaskType.RECURRING_FIXED
        recurrence != Recurrence.NONE -> TaskType.RECURRING_NO_TIME
        fixedTime != null -> TaskType.FIXED_TIME
        else -> TaskType.NORMAL
    }
}
