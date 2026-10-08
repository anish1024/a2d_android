package com.a2d.app.domain.engine

import com.a2d.app.domain.model.PulseResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SerialAndGateProcessor @Inject constructor() {

    private data class PinState(
        val inputs: MutableList<Int> = mutableListOf(),
        var output: Int = 0
    )

    private val pinStates = ConcurrentHashMap<String, PinState>()

    @Synchronized
    fun processPulse(inputPin: String, pulse: String): PulseResult {
        val arity = inputPin.toIntOrNull()
            ?: throw IllegalArgumentException("input_pin must parse as an integer: $inputPin")

        val normalized = pulse.trim().uppercase()
        if (normalized != "HIGH" && normalized != "LOW") {
            throw IllegalArgumentException("pulse must be HIGH or LOW after strip+upper, got: $pulse")
        }

        val state = pinStates.getOrPut(inputPin) { PinState() }

        return if (normalized == "LOW") {
            state.inputs.clear()
            state.output = 0
            PulseResult(
                inputPin = inputPin,
                pulse = "LOW",
                latchedInputs = emptyList(),
                output = 0,
                reset = true
            )
        } else {
            state.inputs.add(1)
            state.output = if (state.inputs.size >= arity) 1 else 0
            PulseResult(
                inputPin = inputPin,
                pulse = "HIGH",
                latchedInputs = ArrayList(state.inputs),
                output = state.output,
                reset = false
            )
        }
    }
}
