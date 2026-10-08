package com.a2d.app.domain.model

data class ActionItem(
    val id: Long,
    val description: String,
    override val achieved: Boolean = false
) : ActivityAchieved
