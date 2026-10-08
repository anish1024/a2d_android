package com.a2d.app

import com.a2d.app.domain.model.ActionItem
import com.a2d.app.domain.model.Task
import org.junit.Assert.*
import org.junit.Test

class TaskDomainTest {

    @Test
    fun testProcessActivityWithoutCallbackSetsAchievedTrue() {
        val task = Task(
            id = 1L,
            taskName = "Default Test",
            description = "Desc",
            priority = 1
        )
        assertFalse(task.achieved)
        val res = task.processActivity()
        assertTrue(res)
        assertTrue(task.achieved)
    }

    @Test
    fun testProcessActivityWithCallbackEvaluatesActivities() {
        val item1 = ActionItem(1L, "Step 1", achieved = true)
        val item2 = ActionItem(2L, "Step 2", achieved = false)
        val task = Task(
            id = 2L,
            taskName = "Callback Test",
            description = "Desc",
            priority = 1,
            activities = listOf(item1, item2)
        )

        val completed = task.processActivity { acts -> acts.all { it.achieved } }
        assertFalse(completed)
        assertFalse(task.achieved)
    }
}
