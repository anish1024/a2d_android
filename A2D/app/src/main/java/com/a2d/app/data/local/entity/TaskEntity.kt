package com.a2d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: Long,
    val taskName: String,
    val description: String,
    val priority: Int,
    val context: String,
    val achieved: Boolean,
    val creationTime: String,
    @ColumnInfo(name = "sync_id") val syncId: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
    @ColumnInfo(name = "dirty") val dirty: Boolean = false,
)
