package com.a2d.app.domain.model

data class PulseResult(
    val inputPin: String,
    val pulse: String,
    val latchedInputs: List<Int>,
    val output: Int,
    val reset: Boolean
)
