package com.a2d.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.local.entity.TaskEntity
import com.a2d.app.data.local.relation.TaskWithActionItems
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Transaction
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY priority ASC")
    fun getAllTasks(): Flow<List<TaskWithActionItems>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY priority ASC")
    suspend fun getTaskSnapshot(): List<TaskWithActionItems>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId AND deleted = 0 LIMIT 1")
    suspend fun getTaskById(taskId: Long): TaskWithActionItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskEntity(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionItems(items: List<ActionItemEntity>)

    @androidx.room.Upsert
    suspend fun upsertTask(task: TaskEntity)

    @androidx.room.Upsert
    suspend fun upsertActionItem(item: ActionItemEntity)

    @Query("UPDATE tasks SET deleted = 1, dirty = 1 WHERE id = :taskId")
    suspend fun tombstoneTask(taskId: Long): Int

    @Query("UPDATE action_items SET deleted = 1, dirty = 1 WHERE taskId = :taskId")
    suspend fun tombstoneActionItemsForTask(taskId: Long): Int

    @Query("DELETE FROM action_items WHERE taskId = :taskId")
    suspend fun deleteActionItemsByTaskId(taskId: Long)

    @Transaction
    suspend fun insertOrUpdateTask(task: TaskEntity, items: List<ActionItemEntity>) {
        insertTaskEntity(task)
        deleteActionItemsByTaskId(task.id)
        insertActionItems(items)
    }

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun hardDeleteTask(taskId: Long): Int
}