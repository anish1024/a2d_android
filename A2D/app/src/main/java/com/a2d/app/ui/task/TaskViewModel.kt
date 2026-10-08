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

    private val _milestoneOutput = MutableStateFlow(0)
    val milestoneOutput: StateFlow<Int> = _milestoneOutput.asStateFlow()

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

    fun toggleActionItem(task: Task, actionId: Long) {
        viewModelScope.launch {
            val updated = task.activities.map {
                if (it.id == actionId) it.copy(achieved = !it.achieved) else it
            }
            task.activities = updated

            // Tier 1: Process Task Gate
            task.processActivityWithGate(gateProcessor)
            repository.saveTask(task)

            // Tier 2: Process Global Pipeline Gate
            recalculatePipeline()
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteTask(id)
            recalculatePipeline()
        }
    }

    private suspend fun recalculatePipeline() {
        val currentTasks = repository.getTaskSnapshot()
        val pipelineResult = pipelineProcessor.evaluateTaskList(currentTasks)
        _milestoneOutput.value = pipelineResult.output
    }
}
