package com.a2d.app

import com.a2d.app.domain.engine.SerialAndGateProcessor
import com.a2d.app.domain.engine.TaskListPipelineProcessor
import com.a2d.app.domain.model.ActionItem
import com.a2d.app.domain.model.Task
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IntegratedGateTaskFlowTest {

    private lateinit var gateProcessor: SerialAndGateProcessor
    private lateinit var pipelineProcessor: TaskListPipelineProcessor

    @Before
    fun setup() {
        gateProcessor = SerialAndGateProcessor()
        pipelineProcessor = TaskListPipelineProcessor(gateProcessor)
    }

    @Test
    fun testTierOneGateEvaluatesSubActions() {
        val task = Task(
            id = 100L,
            taskName = "Morning Checklist",
            description = "Start day",
            priority = 1,
            activities = listOf(
                ActionItem(1L, "Water", achieved = true),
                ActionItem(2L, "Meds", achieved = false)
            )
        )

        task.processActivityWithGate(gateProcessor)
        assertFalse(task.achieved)

        task.activities = task.activities.map { it.copy(achieved = true) }
        task.processActivityWithGate(gateProcessor)
        assertTrue(task.achieved)
    }

    @Test
    fun testTierTwoPipelineEvaluatesTaskList() {
        val task1 = Task(id = 1L, taskName = "T1", description = "", priority = 1, achieved = true)
        val task2 = Task(id = 2L, taskName = "T2", description = "", priority = 2, achieved = false)

        val result1 = pipelineProcessor.evaluateTaskList(listOf(task1, task2))
        assertEquals(0, result1.output)
        assertTrue(result1.reset)

        val result2 = pipelineProcessor.evaluateTaskList(listOf(task1, task2.copy(achieved = true)))
        assertEquals(1, result2.output)
        assertFalse(result2.reset)
    }
}
