package com.a2d.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.a2d.app.data.local.dao.TaskDao
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.local.entity.TaskEntity

@Database(entities = [TaskEntity::class, ActionItemEntity::class], version = 1, exportSchema = false)
abstract class A2DDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
