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

    /** One level of the tree: due-now pinned first, then priority rank (highest first). */
    @Query("SELECT * FROM tasks WHERE parentId IS :parentId ORDER BY dueNow DESC, rank DESC")
    fun observeChildren(parentId: Long?): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE parentId IS :parentId ORDER BY dueNow DESC, rank DESC")
    suspend fun children(parentId: Long?): List<Task>

    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<Task>

    @Query("SELECT * FROM tasks WHERE isRecurringTemplate = 1")
    suspend fun recurringTemplates(): List<Task>

    @Query("SELECT * FROM tasks WHERE fixedTime IS NOT NULL AND completed = 0 AND dueNow = 0")
    suspend fun pendingTimedTasks(): List<Task>

    @Query("SELECT COUNT(*) FROM tasks WHERE parentId IS :parentId")
    suspend fun childCount(parentId: Long?): Int
}
