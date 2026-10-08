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

    @Test
    fun testIsCompletedFalseWhenNoActivities() {
        val task = Task(
            id = 3L,
            taskName = "Empty",
            description = "Desc",
            priority = 1
        )
        assertFalse(task.isCompleted)
    }

    @Test
    fun testIsCompletedFalseWhenAnyActivityUnchecked() {
        val task = Task(
            id = 4L,
            taskName = "Partial",
            description = "Desc",
            priority = 1,
            activities = listOf(
                ActionItem(1L, "Done", achieved = true),
                ActionItem(2L, "Todo", achieved = false)
            )
        )
        assertFalse(task.isCompleted)
    }

    @Test
    fun testIsCompletedTrueWhenAllActivitiesAchieved() {
        val task = Task(
            id = 5L,
            taskName = "All done",
            description = "Desc",
            priority = 1,
            activities = listOf(
                ActionItem(1L, "A", achieved = true),
                ActionItem(2L, "B", achieved = true)
            )
        )
        assertTrue(task.isCompleted)
    }

    @Test
    fun testProgressPercentZeroWhenNoActivities() {
        val task = Task(
            id = 6L,
            taskName = "Empty progress",
            description = "Desc",
            priority = 1
        )
        assertEquals(0, task.progressPercent)
    }

    @Test
    fun testProgressPercentPartialCompletion() {
        val task = Task(
            id = 7L,
            taskName = "Quarter done",
            description = "Desc",
            priority = 1,
            activities = listOf(
                ActionItem(1L, "A", achieved = true),
                ActionItem(2L, "B", achieved = false),
                ActionItem(3L, "C", achieved = false),
                ActionItem(4L, "D", achieved = false)
            )
        )
        assertEquals(25, task.progressPercent)
    }

    @Test
    fun testProgressPercentTruncatesIntegerDivision() {
        val task = Task(
            id = 8L,
            taskName = "Two thirds",
            description = "Desc",
            priority = 1,
            activities = listOf(
                ActionItem(1L, "A", achieved = true),
                ActionItem(2L, "B", achieved = true),
                ActionItem(3L, "C", achieved = false)
            )
        )
        assertEquals(66, task.progressPercent)
    }

    @Test
    fun testProgressPercentHundredWhenAllAchieved() {
        val task = Task(
            id = 9L,
            taskName = "All done",
            description = "Desc",
            priority = 1,
            activities = listOf(
                ActionItem(1L, "A", achieved = true),
                ActionItem(2L, "B", achieved = true)
            )
        )
        assertEquals(100, task.progressPercent)
    }
}
