package com.a2d.app.domain.engine

import com.a2d.app.domain.model.PulseResult
import com.a2d.app.domain.model.Task
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskListPipelineProcessor @Inject constructor(
    private val gateProcessor: SerialAndGateProcessor
) {
    /**
     * Tier 2 Gate Integration:
     * Evaluates task list completion as an AND gate of arity tasks.size.
     */
    fun evaluateTaskList(tasks: List<Task>): PulseResult {
        if (tasks.isEmpty()) {
            return PulseResult("0", "LOW", emptyList(), 1, reset = false)
        }

        val arityPin = tasks.size.toString()
        gateProcessor.processPulse(arityPin, "LOW") // Reset register

        var lastResult = PulseResult(arityPin, "LOW", emptyList(), 0, reset = true)
        for (task in tasks) {
            val pulse = if (task.achieved) "HIGH" else "LOW"
            lastResult = gateProcessor.processPulse(arityPin, pulse)
            if (lastResult.reset) break
        }
        return lastResult
    }
}
