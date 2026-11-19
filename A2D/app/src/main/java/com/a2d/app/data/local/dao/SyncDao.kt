package com.a2d.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.local.entity.SyncStateEntity
import com.a2d.app.data.local.entity.TaskEntity

/**
 * DAO for sync metadata queries: dirty-row reads, watermark persistence,
 * and per-row sync_id / updated_at / dirty bookkeeping.
 *
 * Column names use the SQL names (snake_case) as declared by @ColumnInfo.
 */
@Dao
interface SyncDao {

    // ---------- dirty rows (upload) ----------

    @Transaction
    @Query("SELECT * FROM tasks WHERE dirty = 1 ORDER BY id ASC LIMIT :limit")
    suspend fun getDirtyTasks(limit: Int): List<TaskEntity>

    @Transaction
    @Query("SELECT * FROM action_items WHERE dirty = 1 ORDER BY id ASC LIMIT :limit")
    suspend fun getDirtyActionItems(limit: Int): List<ActionItemEntity>

    // ---------- watermark ----------

    @Query("SELECT * FROM sync_state WHERE id = 1 LIMIT 1")
    suspend fun getSyncState(): SyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Query("UPDATE sync_state SET last_backup_at = :ts, last_download_at = :token WHERE id = 1")
    suspend fun updateWatermarks(ts: Long, token: String)

    @Query("UPDATE sync_state SET last_download_at = :token WHERE id = 1")
    suspend fun updateLastDownloadToken(token: String)

    // ---------- per-row bookkeeping ----------

    @Update
    suspend fun updateTask(entity: TaskEntity)

    @Update
    suspend fun updateActionItem(entity: ActionItemEntity)

    @Query("UPDATE tasks SET dirty = 0 WHERE id IN (:ids)")
    suspend fun markTasksClean(ids: List<Long>)

    @Query("UPDATE action_items SET dirty = 0 WHERE id IN (:ids)")
    suspend fun markActionItemsClean(ids: List<Long>)

    @Query("UPDATE tasks SET dirty = 1 WHERE id = :id")
    suspend fun markTaskDirty(id: Long)

    @Query("UPDATE action_items SET dirty = 1 WHERE id = :id")
    suspend fun markActionItemDirty(id: Long)

    @Query("UPDATE tasks SET sync_id = :syncId, updated_at = :ts WHERE id = :id")
    suspend fun setTaskSyncMeta(id: Long, syncId: String, ts: Long)

    @Query("UPDATE action_items SET sync_id = :syncId, updated_at = :ts WHERE id = :id")
    suspend fun setActionItemSyncMeta(id: Long, syncId: String, ts: Long)

    @Query("SELECT COALESCE(MAX(updated_at), 0) FROM tasks")
    suspend fun maxTaskUpdatedAt(): Long

    @Query("SELECT COALESCE(MAX(updated_at), 0) FROM action_items")
    suspend fun maxActionItemUpdatedAt(): Long

    // ---------- upsert from download ----------
    @Upsert
    suspend fun upsertTask(entity: TaskEntity)

    @Upsert
    suspend fun upsertActionItem(entity: ActionItemEntity)

    @Query("SELECT id FROM tasks WHERE sync_id = :syncId LIMIT 1")
    suspend fun getTaskIdBySyncId(syncId: String): Long?

    @Query("SELECT * FROM tasks WHERE sync_id = :syncId LIMIT 1")
    suspend fun findTaskBySyncId(syncId: String): TaskEntity?

    @Query("SELECT * FROM action_items WHERE sync_id = :syncId LIMIT 1")
    suspend fun findActionItemBySyncId(syncId: String): ActionItemEntity?

    // ---------- lookups by syncId ----------
    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM tasks")
    suspend fun nextTaskId(): Long

    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM action_items")
    suspend fun nextActionItemId(): Long
}