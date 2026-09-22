package com.forgebuild.taskflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Recurrence rules; WEEKLY carries [weekdaysMask] (bit 0=Monday … bit 6=Sunday). */
enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Fractional priority rank (lexo-style). Higher = closer to top. Never shown raw to user. */
    val rank: Double,
    /** Optional fixed due time, epoch millis. */
    val fixedTime: Long? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    /** For WEEKLY recurrence: bitmask of weekdays (Calendar.MONDAY -> bit 0). */
    val weekdaysMask: Int = 0,
    /** Free-text notes ("info"). Shown via eye-icon quick-peek, edited on the edit screen. */
    val info: String = "",
    /** Nullable parent for infinitely nestable sub-tasks. */
    val parentId: Long? = null,
    val completed: Boolean = false,
    /** For recurring series: template stays true; generated occurrences are clones. */
    val isRecurringTemplate: Boolean = false,
    /** Link from a generated occurrence back to its template. */
    val seriesId: Long? = null,
    /** True while a fixed-time task's due moment has arrived and it is not yet completed (today). */
    val dueNow: Boolean = false,
    /** Epoch millis of the day this due-now state belongs to (cleared on rollover). */
    val dueNowDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
