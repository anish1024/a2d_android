package com.a2d.app.domain.model

data class ActionItem(
    val id: Long,
    val description: String,
    override val achieved: Boolean = false,
    val syncId: String = "",
    val updatedAt: Long = 0L,
    val dirty: Boolean = false,
    val taskSyncId: String = ""
) : ActivityAchieved
