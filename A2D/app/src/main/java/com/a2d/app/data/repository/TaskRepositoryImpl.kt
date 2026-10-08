package com.a2d.app.data.repository

import com.a2d.app.data.local.dao.TaskDao
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.local.entity.TaskEntity
import com.a2d.app.domain.model.ActionItem
import com.a2d.app.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao
) {
    fun getAllTasks(): Flow<List<Task>> {
        return taskDao.getAllTasks().map { list ->
            list.map { withItems ->
                Task(
                    id = withItems.task.id,
                    taskName = withItems.task.taskName,
                    description = withItems.task.description,
                    priority = withItems.task.priority,
                    achieved = withItems.task.achieved,
                    context = withItems.task.context,
                    creationTime = withItems.task.creationTime,
                    activities = withItems.actionItems.map {
                        ActionItem(it.id, it.description, it.achieved)
                    }
                )
            }
        }
    }

    suspend fun getTaskSnapshot(): List<Task> {
        return taskDao.getTaskSnapshot().map { withItems ->
            Task(
                id = withItems.task.id,
                taskName = withItems.task.taskName,
                description = withItems.task.description,
                priority = withItems.task.priority,
                achieved = withItems.task.achieved,
                context = withItems.task.context,
                creationTime = withItems.task.creationTime,
                activities = withItems.actionItems.map {
                    ActionItem(it.id, it.description, it.achieved)
                }
            )
        }
    }

    suspend fun getTask(id: Long): Task? {
        val record = taskDao.getTaskById(id) ?: return null
        return Task(
            id = record.task.id,
            taskName = record.task.taskName,
            description = record.task.description,
            priority = record.task.priority,
            achieved = record.task.achieved,
            context = record.task.context,
            creationTime = record.task.creationTime,
            activities = record.actionItems.map { ActionItem(it.id, it.description, it.achieved) }
        )
    }

    suspend fun saveTask(task: Task) {
        val timestamp = task.creationTime ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val taskEntity = TaskEntity(
            id = task.id,
            taskName = task.taskName,
            description = task.description,
            priority = task.priority,
            context = task.context,
            achieved = task.achieved,
            creationTime = timestamp
        )
        val actionEntities = task.activities.map {
            ActionItemEntity(id = it.id, taskId = task.id, description = it.description, achieved = it.achieved)
        }
        taskDao.insertOrUpdateTask(taskEntity, actionEntities)
    }

    suspend fun deleteTask(id: Long): Boolean {
        return taskDao.deleteTask(id) > 0
    }
}
