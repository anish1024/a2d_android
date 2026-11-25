package com.a2d.app.data.repository

import com.a2d.app.data.local.dao.SyncDao
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val syncDao: SyncDao
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
                    syncId = withItems.task.syncId,
                    updatedAt = withItems.task.updatedAt,
                    dirty = withItems.task.dirty,
                    activities = withItems.actionItems
                        .sortedBy { it.id }
                        .map { it.toDomain() }
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
                syncId = withItems.task.syncId,
                updatedAt = withItems.task.updatedAt,
                dirty = withItems.task.dirty,
                activities = withItems.actionItems
                    .sortedBy { it.id }
                    .map { it.toDomain() }
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
            syncId = record.task.syncId,
            updatedAt = record.task.updatedAt,
            dirty = record.task.dirty,
            activities = record.actionItems
                .sortedBy { it.id }
                .map { it.toDomain() }
        )
    }

    suspend fun saveTask(task: Task) {
        val timestamp = task.creationTime ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val now = System.currentTimeMillis()
        val taskEntity = TaskEntity(
            id = task.id,
            taskName = task.taskName,
            description = task.description,
            priority = task.priority,
            context = task.context,
            achieved = task.achieved,
            creationTime = timestamp,
            syncId = task.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAt = now,
            deleted = false,
            dirty = true
        )
        val actionEntities = task.activities.map {
            ActionItemEntity(
                id = it.id,
                taskId = task.id,
                description = it.description,
                achieved = it.achieved,
                syncId = it.syncId.ifBlank { UUID.randomUUID().toString() },
                updatedAt = now,
                deleted = false,
                dirty = true
            )
        }
        taskDao.insertOrUpdateTask(taskEntity, actionEntities)
    }

    suspend fun deleteTask(id: Long): Boolean {
        return taskDao.tombstoneTask(id) > 0 && taskDao.tombstoneActionItemsForTask(id) > 0
    }

    // ---------- sync support ----------

    suspend fun getDirtyTasks(limit: Int): List<TaskEntity> = syncDao.getDirtyTasks(limit)
    suspend fun getDirtyActionItems(limit: Int): List<ActionItemEntity> =
        syncDao.getDirtyActionItems(limit)

    suspend fun markTasksClean(ids: List<Long>) = syncDao.markTasksClean(ids)
    suspend fun markActionItemsClean(ids: List<Long>) = syncDao.markActionItemsClean(ids)

    suspend fun upsertTaskFromSync(entity: TaskEntity) = syncDao.updateTask(entity)
    suspend fun upsertActionItemFromSync(entity: ActionItemEntity) = syncDao.updateActionItem(entity)

    suspend fun getSyncState() = syncDao.getSyncState()
    suspend fun upsertSyncState(state: com.a2d.app.data.local.entity.SyncStateEntity) =
        syncDao.upsertSyncState(state)
    suspend fun updateLastDownloadToken(token: String) = syncDao.updateLastDownloadToken(token)
    suspend fun updateWatermarks(ts: Long, token: String) = syncDao.updateWatermarks(ts, token)

    private fun com.a2d.app.data.local.entity.ActionItemEntity.toDomain(): ActionItem =
        ActionItem(
            id = this.id,
            description = this.description,
            achieved = this.achieved,
            syncId = this.syncId,
            updatedAt = this.updatedAt,
            dirty = this.dirty,
            taskSyncId = "" // populated by the SyncEngine when building the sync frame
        )

    private fun com.a2d.app.data.local.entity.TaskEntity.toDomain(): Task =
        Task(
            id = this.id,
            taskName = this.taskName,
            description = this.description,
            priority = this.priority,
            achieved = this.achieved,
            context = this.context,
            creationTime = this.creationTime,
            syncId = this.syncId,
            updatedAt = this.updatedAt,
            dirty = this.dirty
        )
}