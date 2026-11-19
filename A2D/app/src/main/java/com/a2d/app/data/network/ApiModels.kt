package com.a2d.app.data.network

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /v1/sync/backup.
 *
 * Matches the a2d-cloud Pydantic models in app/api/sync.py:
 *   tables: Dict[str, List[SyncRow]]  — keys: "tasks" | "action_items"
 *   lastBackupAt: Optional[int]       — client-side ms timestamp cutoff
 */
data class SyncBackupRequest(
    val tables: Map<String, List<SyncRow>> = emptyMap(),
    val lastBackupAt: Long? = null
)

/**
 * A single sync row in the flat per-table frame.
 *
 * Matches a2d-cloud's SyncRow (app/api/sync.py) with `extra = "allow"` for forward compat.
 */
data class SyncRow(
    val syncId: String? = null,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
    // Task fields
    val taskName: String? = null,
    val description: String? = null,
    val priority: Int? = null,
    val context: String? = null,
    val achieved: Boolean? = null,
    val creationTime: String? = null,
    // Action item fields
    val taskSyncId: String? = null,
    val origin_device: String? = null
)

/**
 * Response body for POST /v1/sync/backup.
 */
data class SyncBackupResponse(
    val accepted: List<Map<String, Any?>> = emptyList(),
    val rejected: List<Map<String, Any?>> = emptyList(),
    val nextDownloadToken: String? = null,
    val nextDownloadAt: Long? = null,
    val nextBatchCount: Int = 0
)

/**
 * Response body for GET /v1/sync/updates.
 */
data class SyncUpdatesResponse(
    val rows: List<Map<String, Any?>> = emptyList(),
    val nextDownloadToken: String? = null,
    val nextDownloadAt: Long? = null,
    val nextBatchCount: Int = 0
)

/**
 * Convenience result type for sync operations.
 */
sealed class SyncResult {
    data class Success(val uploaded: Int, val downloaded: Int) : SyncResult()
    data class Failure(val message: String, val uploaded: Int = 0, val downloaded: Int = 0) : SyncResult()
    data object Offline : SyncResult()
}