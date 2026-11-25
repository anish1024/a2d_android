package com.a2d.app.data.sync

import android.content.Context
import com.a2d.app.data.local.dao.SyncDao
import com.a2d.app.data.local.dao.TaskDao
import com.a2d.app.data.local.entity.SyncStateEntity
import com.a2d.app.data.local.entity.TaskEntity
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.network.ApiClient
import com.a2d.app.data.network.SyncBackupRequest
import com.a2d.app.data.network.SyncBackupResponse
import com.a2d.app.data.network.SyncResult
import com.a2d.app.data.network.SyncRow
import com.a2d.app.data.network.SyncUpdatesResponse
import com.a2d.app.data.network.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import retrofit2.Response

@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val syncDao: SyncDao,
    private val apiClient: ApiClient,
    private val tokenStore: TokenStore
) {

    private val MAX_BATCH_ROWS = 500
    private val _status = MutableStateFlow<SyncState>(SyncState.Idle)
    val status: StateFlow<SyncState> = _status.asStateFlow()

    suspend fun sync(): SyncResult {
        if (!tokenStore.isAuthenticated()) {
            _status.value = SyncState.Error("Not authenticated")
            return SyncResult.Failure("Not authenticated")
        }

        _status.value = SyncState.Syncing
        return try {
            val result = withContext(Dispatchers.IO) {
                performSyncRoundTrip()
            }
            if (result is SyncResult.Success) {
                _status.value = SyncState.Success(uploaded = result.uploaded, downloaded = result.downloaded)
            }
            result
        } catch (e: Exception) {
            _status.value = SyncState.Error(e.message ?: "Sync failed")
            SyncResult.Failure(e.message ?: "Sync failed")
        }
    }

    private suspend fun performSyncRoundTrip(): SyncResult {
        val uploaded = uploadDirtyRows()
        val downloaded = downloadUpdates()
        return SyncResult.Success(uploaded = uploaded, downloaded = downloaded)
    }

    private suspend fun uploadDirtyRows(): Int {
        val dirtyTasks = syncDao.getDirtyTasks(MAX_BATCH_ROWS)
        val dirtyItems = syncDao.getDirtyActionItems(MAX_BATCH_ROWS)

        val taskRows: List<SyncRow> = dirtyTasks.map { entity ->
            SyncRow(
                syncId = entity.syncId.ifBlank { UUID.randomUUID().toString() },
                updatedAt = entity.updatedAt,
                deleted = entity.deleted,
                taskName = entity.taskName,
                description = entity.description,
                priority = entity.priority,
                context = entity.context,
                achieved = entity.achieved,
                creationTime = entity.creationTime
            )
        }
        val taskSyncIdById = dirtyTasks.associate { it.id to it.syncId }

        val itemRows: List<SyncRow> = dirtyItems.map { entity ->
            val taskSyncId = taskSyncIdById[entity.taskId] ?: entity.syncId
            SyncRow(
                syncId = entity.syncId.ifBlank { UUID.randomUUID().toString() },
                updatedAt = entity.updatedAt,
                deleted = entity.deleted,
                description = entity.description,
                achieved = entity.achieved,
                taskSyncId = taskSyncId
            )
        }

        val request = SyncBackupRequest(
            tables = mapOf(
                "tasks" to taskRows,
                "action_items" to itemRows
            ),
            lastBackupAt = syncDao.getSyncState()?.lastBackupAt ?: 0L
        )
        android.util.Log.d("A2D_Sync", "REQUEST BODY: ${request.tables}")

        val idempotencyKey = UUID.randomUUID().toString()
        val response: Response<SyncBackupResponse> = apiClient.api.syncBackup(
            idempotencyKey = idempotencyKey,
            request = request
        ).execute()

        if (!response.isSuccessful) {
            val errBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
            android.util.Log.e("A2D_Sync", "Upload failed ${response.code()}: $errBody")
            throw IllegalStateException("Upload failed: ${response.code()} $errBody")
        }

        val body = response.body() ?: throw IllegalStateException("Empty response body")

        if (dirtyTasks.isNotEmpty()) syncDao.markTasksClean(dirtyTasks.map { it.id })
        if (dirtyItems.isNotEmpty()) syncDao.markActionItemsClean(dirtyItems.map { it.id })

        val newTs = body.nextDownloadAt ?: System.currentTimeMillis()
        val cursor = body.nextDownloadToken ?: ""
        syncDao.updateWatermarks(newTs, cursor)

        return taskRows.size + itemRows.size
    }

    private suspend fun downloadUpdates(): Int {
        var cursor = syncDao.getSyncState()?.lastDownloadAt ?: ""
        var total = 0
        var cursorValid = true

        while (cursorValid) {
            val response: Response<SyncUpdatesResponse> = apiClient.api.syncUpdates(
                lastDownloadAt = cursor,
                limit = MAX_BATCH_ROWS
            ).execute()

            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: ${response.code()} ${response.message()}")
            }

            val body = response.body() ?: throw IllegalStateException("Empty response body")
            val rows = body.rows ?: emptyList()
            total += rows.size

            for (row in rows) {
                val rowMap = row as? Map<*, *> ?: continue
                val tableType = if (rowMap.containsKey("taskName")) "tasks" else "action_items"

                if (tableType == "tasks") {
                    val taskMap = rowMap as Map<String, Any>
                    val syncId = taskMap["syncId"]?.toString() ?: ""
                    // Find existing entity by syncId to preserve local id
                    val existing = syncDao.findTaskBySyncId(syncId)
                    val newId = existing?.id ?: syncDao.nextTaskId()
                    val entity = TaskEntity(
                        id = newId,
                        taskName = taskMap["taskName"]?.toString() ?: "",
                        description = taskMap["description"]?.toString() ?: "",
                        priority = (taskMap["priority"] as? Number)?.toInt() ?: 1,
                        context = taskMap["context"]?.toString() ?: "No Context",
                        achieved = taskMap["achieved"] as? Boolean ?: false,
                        creationTime = taskMap["creationTime"]?.toString() ?: "",
                        syncId = syncId,
                        updatedAt = (taskMap["updatedAt"] as? Number)?.toLong() ?: 0L,
                        deleted = taskMap["deleted"] as? Boolean ?: false,
                        dirty = false
                    )
                    syncDao.upsertTask(entity)
                } else if (tableType == "action_items") {
                    val itemMap = rowMap as Map<String, Any>
                    val syncId = itemMap["syncId"]?.toString() ?: ""
                    val taskSyncId = itemMap["taskSyncId"]?.toString()
                    val existing = syncDao.findActionItemBySyncId(syncId)
                    val taskLocalId = syncDao.getTaskIdBySyncId(taskSyncId ?: "")
                    val newId = existing?.id ?: syncDao.nextActionItemId()
                    val entity = ActionItemEntity(
                        id = newId,
                        taskId = taskLocalId ?: 0L,
                        description = itemMap["description"]?.toString() ?: "",
                        achieved = itemMap["achieved"] as? Boolean ?: false,
                        syncId = syncId,
                        updatedAt = (itemMap["updatedAt"] as? Number)?.toLong() ?: 0L,
                        deleted = itemMap["deleted"] as? Boolean ?: false,
                        dirty = false
                    )
                    syncDao.upsertActionItem(entity)
                }
            }

            if (body.nextBatchCount == 0 || body.nextDownloadToken.isNullOrBlank()) {
                cursorValid = false
            } else {
                cursor = body.nextDownloadToken ?: ""
            }
        }

        syncDao.updateLastDownloadToken(cursor)
        syncDao.updateWatermarks(System.currentTimeMillis(), cursor)

        return total
    }
}

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val uploaded: Int, val downloaded: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}
