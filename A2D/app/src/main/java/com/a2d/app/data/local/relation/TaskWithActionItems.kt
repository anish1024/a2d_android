package com.a2d.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.local.entity.TaskEntity

data class TaskWithActionItems(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val actionItems: List<ActionItemEntity>
)
