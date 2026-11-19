package com.a2d.app.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a2d.app.data.repository.TaskRepositoryImpl
import com.a2d.app.domain.engine.SerialAndGateProcessor
import com.a2d.app.domain.engine.TaskListPipelineProcessor
import com.a2d.app.domain.model.ActionItem
import com.a2d.app.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repository: TaskRepositoryImpl,
    private val gateProcessor: SerialAndGateProcessor,
    private val pipelineProcessor: TaskListPipelineProcessor
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = repository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedTaskId = MutableStateFlow<Long?>(null)

    val currentTask: StateFlow<Task?> = combine(tasks, selectedTaskId) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _milestoneOutput = MutableStateFlow(0)
    val milestoneOutput: StateFlow<Int> = _milestoneOutput.asStateFlow()

    fun selectTask(taskId: Long) {
        selectedTaskId.value = taskId
    }

    fun createSampleTask(id: Long, name: String, priority: Int) {
        viewModelScope.launch {
            val task = Task(
                id = id,
                taskName = name,
                description = "Focus on one action item at a time.",
                priority = priority,
                context = "Daily Routine",
                activities = listOf(
                    ActionItem(id = id * 10 + 1, description = "Prepare materials"),
                    ActionItem(id = id * 10 + 2, description = "Focus 25-minute sprint")
                )
            )
            repository.saveTask(task)
            recalculatePipeline()
        }
    }

    fun toggleActivity(taskId: Long, activityId: Long, achieved: Boolean) {
        viewModelScope.launch {
            val task = repository.getTask(taskId) ?: return@launch
            task.activities = task.activities.map {
                if (it.id == activityId) it.copy(achieved = achieved) else it
            }

            // Tier 1: Process Task Gate
            task.processActivityWithGate(gateProcessor)
            repository.saveTask(task)

            // Tier 2: Process Global Pipeline Gate
            recalculatePipeline()
        }
    }

    fun addActivity(taskId: Long) {
        viewModelScope.launch {
            val task = repository.getTask(taskId) ?: return@launch
            val maxExistingId = tasks.value
                .flatMap { it.activities }
                .maxOfOrNull { it.id } ?: 0L
            val newId = maxOf(maxExistingId, taskId * 10) + 1
            task.activities = task.activities + ActionItem(
                id = newId,
                description = "New activity"
            )

            task.processActivityWithGate(gateProcessor)
            repository.saveTask(task)
            recalculatePipeline()
        }
    }

    fun deleteActivity(taskId: Long, activityId: Long) {
        viewModelScope.launch {
            val task = repository.getTask(taskId) ?: return@launch
            task.activities = task.activities.filterNot { it.id == activityId }

            task.processActivityWithGate(gateProcessor)
            repository.saveTask(task)
            recalculatePipeline()
        }
    }

    /**
     * Update a task's name and description in-place. Trims inputs; a blank name falls back to the
     * current name. Skips the write if nothing changed.
     */
    fun updateTask(taskId: Long, newName: String, newDescription: String) {
        viewModelScope.launch {
            val task = repository.getTask(taskId) ?: return@launch

            val trimmedName = newName.trim()
            val trimmedDescription = newDescription.trim()
            val finalName = trimmedName.ifBlank { task.taskName }

            if (finalName == task.taskName && trimmedDescription == task.description) {
                return@launch // nothing changed — skip the write
            }

            val updated = task.copy(
                taskName = finalName,
                description = trimmedDescription
            )
            repository.saveTask(updated)
        }
    }

    /**
     * Update a single activity's description text. Trims input; a blank description falls back to
     * the current one. Description text does not affect achievement, so no gate/pipeline recalc.
     */
    fun updateActivity(taskId: Long, activityId: Long, newDescription: String) {
        viewModelScope.launch {
            val task = repository.getTask(taskId) ?: return@launch
            val current = task.activities.find { it.id == activityId } ?: return@launch

            val finalDescription = newDescription.trim().ifBlank { current.description }

            if (finalDescription == current.description) {
                return@launch // nothing changed — skip the write
            }

            task.activities = task.activities.map {
                if (it.id == activityId) it.copy(description = finalDescription) else it
            }
            repository.saveTask(task)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            recalculatePipeline()
        }
    }

    /**
     * Move a task up or down in the list.
     * Tasks are ordered by `priority ASC`. Because multiple tasks can share the same priority,
     * a simple swap is not sufficient to guarantee a visible reorder, so we rewrite the whole
     * list in the desired order and re-index all priorities sequentially.
     */
    fun moveTask(taskId: Long, up: Boolean) {
        viewModelScope.launch {
            val sorted = repository.getTaskSnapshot()
                .sortedWith(compareBy<Task> { it.priority }.thenBy { it.id })
            val index = sorted.indexOfFirst { it.id == taskId }
            if (index == -1) return@launch

            val targetIndex = if (up) index - 1 else index + 1
            if (targetIndex !in sorted.indices) return@launch

            val reordered = sorted.toMutableList()
            val moved = reordered.removeAt(index)
            reordered.add(targetIndex, moved)
            reordered.forEachIndexed { i, task ->
                val updated = task.copy(priority = i)
                if (updated.priority != task.priority) {
                    repository.saveTask(updated)
                }
            }
            recalculatePipeline()
        }
    }

    /**
     * Move an activity up or down within its task by swapping description/achieved with the adjacent
     * activity. Activities are displayed in ID order, so swapping content effectively reorders them
     * while preserving stable IDs (for checkbox toggles and deletes).
     */
    fun moveActivity(taskId: Long, activityId: Long, up: Boolean) {
        viewModelScope.launch {
            val task = repository.getTask(taskId) ?: return@launch
            val sorted = task.activities.sortedBy { it.id }
            val index = sorted.indexOfFirst { it.id == activityId }
            if (index == -1) return@launch
            val target = if (up) index - 1 else index + 1
            if (target !in sorted.indices) return@launch

            val current = sorted[index]
            val neighbor = sorted[target]
            val swappedActivities = sorted.mapIndexed { i, act ->
                when (i) {
                    index -> act.copy(
                        description = neighbor.description,
                        achieved = neighbor.achieved
                    )
                    target -> act.copy(
                        description = current.description,
                        achieved = current.achieved
                    )
                    else -> act
                }
            }
            val updated = task.copy(activities = swappedActivities)
            repository.saveTask(updated)
            recalculatePipeline()
        }
    }

    /**
     * Triggers a cloud sync attempt.
     *
     * Returns a user-facing status string. The real `SyncEngine` is not yet wired (P1), so this
     * currently returns a placeholder message explaining that cloud sync is pending backend
     * integration. Once the engine is available, this method will perform the actual backup
     * (`POST /v1/sync/backup`) and updates (`GET /v1/sync/updates`) round-trip and return a
     * success/failure summary.
     */
    fun syncNow(): String {
        // TODO: wire to real SyncEngine once P1 backend is available.
        return "Cloud sync is pending backend integration"
    }

    private suspend fun recalculatePipeline() {
        val currentTasks = repository.getTaskSnapshot()
        val pipelineResult = pipelineProcessor.evaluateTaskList(currentTasks)
        _milestoneOutput.value = pipelineResult.output
    }
}
