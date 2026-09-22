package com.forgebuild.taskflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Task::class], version = 1, exportSchema = false)
abstract class TaskFlowDb : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var instance: TaskFlowDb? = null
        fun get(context: Context): TaskFlowDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, TaskFlowDb::class.java, "taskflow.db")
                    .build().also { instance = it }
            }
    }
}
