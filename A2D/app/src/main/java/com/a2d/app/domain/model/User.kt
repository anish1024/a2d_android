package com.a2d.app.domain.model

data class User(
    val name: String,
    val password: String,
    val group: UserGroup
)
