package com.a2d.app.data.local.entity

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
    val creationTime: String
)
