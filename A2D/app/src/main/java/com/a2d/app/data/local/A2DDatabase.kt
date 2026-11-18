package com.a2d.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.a2d.app.data.local.dao.TaskDao
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.local.entity.TaskEntity

@Database(entities = [TaskEntity::class, ActionItemEntity::class], version = 2, exportSchema = true)
abstract class A2DDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: A2DDatabase? = null

        fun getDatabase(context: Context): A2DDatabase = INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                A2DDatabase::class.java,
                "a2d_database.db"
            )
                .addMigrations(RoomMigration.MIGRATION_1_2)
                .build()
            INSTANCE = instance
            instance
        }
    }
}
