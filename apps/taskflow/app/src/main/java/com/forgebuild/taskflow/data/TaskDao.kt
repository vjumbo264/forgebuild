package com.forgebuild.taskflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<Task?>

    /** Active tasks at one level: due-now pinned first, then priority rank (highest first). */
    @Query("SELECT * FROM tasks WHERE parentId IS :parentId AND completed = 0 AND missed = 0 ORDER BY dueNow DESC, rank DESC")
    fun observeActiveChildren(parentId: Long?): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE parentId IS :parentId AND completed = 0 AND missed = 0 ORDER BY dueNow DESC, rank DESC")
    suspend fun activeChildren(parentId: Long?): List<Task>

    /** All tasks at one level regardless of completion (for reordering / nesting). */
    @Query("SELECT * FROM tasks WHERE parentId IS :parentId ORDER BY dueNow DESC, rank DESC")
    fun observeChildren(parentId: Long?): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE parentId IS :parentId ORDER BY dueNow DESC, rank DESC")
    suspend fun children(parentId: Long?): List<Task>

    /** Completed tasks area: sorted newest completion first. */
    @Query("SELECT * FROM tasks WHERE completed = 1 ORDER BY completedAt DESC, updatedAt DESC")
    fun observeCompleted(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE completed = 1 ORDER BY completedAt DESC, updatedAt DESC")
    suspend fun completedTasks(): List<Task>

    /** Auto-delete completed tasks older than retention cutoff. */
    @Query("DELETE FROM tasks WHERE completed = 1 AND completedAt IS NOT NULL AND completedAt < :cutoffMillis")
    suspend fun deleteCompletedBefore(cutoffMillis: Long): Int

    @Query("DELETE FROM tasks WHERE completed = 1")
    suspend fun clearCompleted(): Int

    @Query("SELECT * FROM tasks WHERE completed = 0 AND missed = 0")
    fun observeAllActive(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE completed = 0 AND missed = 0")
    suspend fun allActive(): List<Task>

    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<Task>

    @Query("SELECT * FROM tasks WHERE isRecurringTemplate = 1")
    suspend fun recurringTemplates(): List<Task>

    @Query("SELECT * FROM tasks WHERE fixedTime IS NOT NULL AND completed = 0 AND dueNow = 0 AND missed = 0")
    suspend fun pendingTimedTasks(): List<Task>

    /** Unfinished view: fixed-time tasks whose time passed without completion, newest first. */
    @Query("SELECT * FROM tasks WHERE missed = 1 ORDER BY missedAt DESC")
    fun observeMissed(): Flow<List<Task>>

    @Query("DELETE FROM tasks WHERE missed = 1")
    suspend fun clearMissed(): Int

    /** Auto-delete missed (Unfinished-view) tasks older than retention cutoff — same retention rule as Completed. */
    @Query("DELETE FROM tasks WHERE missed = 1 AND missedAt IS NOT NULL AND missedAt < :cutoffMillis")
    suspend fun deleteMissedBefore(cutoffMillis: Long): Int

    /** Uncompleted, non-template tasks whose fixed time has already passed (candidates for the Unfinished view). */
    @Query("SELECT * FROM tasks WHERE completed = 0 AND missed = 0 AND isRecurringTemplate = 0 AND fixedTime IS NOT NULL AND fixedTime < :nowMillis")
    suspend fun expiredFixedTime(nowMillis: Long): List<Task>

    /** Fixed-time tasks still carrying the loud due-now pin whose full span has already elapsed. */
    @Query("SELECT * FROM tasks WHERE dueNow = 1 AND completed = 0 AND missed = 0 AND fixedTime IS NOT NULL")
    suspend fun dueNowPinned(): List<Task>

    @Query("SELECT COUNT(*) FROM tasks WHERE parentId IS :parentId AND completed = 0")
    suspend fun activeChildCount(parentId: Long?): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE parentId IS :parentId")
    suspend fun childCount(parentId: Long?): Int
}
