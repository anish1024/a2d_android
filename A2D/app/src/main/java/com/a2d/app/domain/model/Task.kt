package com.a2d.app.domain.model

import com.a2d.app.domain.contract.ActivityProcessor
import com.a2d.app.domain.engine.SerialAndGateProcessor

data class Task(
    val id: Long,
    val taskName: String,
    val description: String,
    val priority: Int,
    override var achieved: Boolean = false,
    val context: String = "No Context",
    val creationTime: String? = null,
    override var activities: List<ActionItem> = emptyList()
) : ActivityAchieved, ActivityProcessor {

    /**
     * redesign01 completion rule:
     * A task is completed when it has at least one activity and all activities are achieved.
     * (Distinct from the Tier-1 gate `achieved` flag, which treats an empty task as achieved.)
     */
    val isCompleted: Boolean
        get() = activities.isNotEmpty() && activities.all { it.achieved }

    override fun processActivity(callback: ((List<ActivityAchieved>) -> Boolean)?): Boolean {
        achieved = callback?.invoke(activities) ?: true
        return achieved
    }

    /**
     * Tier 1 Gate Integration:
     * Sets pin arity to activities.size and evaluates ActionItem states sequentially.
     */
    fun processActivityWithGate(processor: SerialAndGateProcessor): Boolean {
        if (activities.isEmpty()) {
            achieved = true
            return true
        }

        val arityPin = activities.size.toString()
        processor.processPulse(arityPin, "LOW") // Reset before serial evaluation

        var gateOutput = 0
        for (item in activities) {
            val pulse = if (item.achieved) "HIGH" else "LOW"
            val result = processor.processPulse(arityPin, pulse)
            gateOutput = result.output
            if (result.reset) break
        }

        achieved = (gateOutput == 1)
        return achieved
    }
}
