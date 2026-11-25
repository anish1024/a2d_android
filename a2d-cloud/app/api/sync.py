"""Sync API routes: backup and updates endpoints.

POST /v1/sync/backup  - Upload dirty rows from Room tables (LWW merge)
GET  /v1/sync/updates - Download rows changed since last download (keyset pagination)

Both endpoints require Bearer token auth. Backup accepts Idempotency-Key header
for safe retry. Updates is naturally idempotent (read-only).
"""
import json
import time
import uuid

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, status
from pydantic import BaseModel, Field, validator
from typing import Any, Dict, List, Optional

from app.api.dependencies import get_current_user
from app.sync import (
    MAX_BATCH_ROWS,
    sync_backup,
    sync_updates,
)


router = APIRouter(prefix="/v1/sync", tags=["sync"])


# ---------- Request/Response models ----------

class SyncRow(BaseModel):
    """A single row in the sync frame (flat per-table representation)."""

    syncId: Optional[str] = None
    updatedAt: int = 0
    deleted: bool = False
    # Task fields (present on tasks rows)
    taskName: Optional[str] = None
    description: Optional[str] = None
    priority: Optional[int] = None
    context: Optional[str] = None
    achieved: Optional[bool] = None
    creationTime: Optional[str] = None
    # Action item fields (present on action_items rows)
    taskSyncId: Optional[str] = None
    origin_device: Optional[str] = None

    class Config:
        # Allow extra fields for forward compatibility
        extra = "allow"


class SyncBackupRequest(BaseModel):
    """POST /v1/sync/backup request body."""

    tables: Dict[str, List[SyncRow]] = Field(
        default_factory=dict,
        description="Table name -> list of row dicts. Keys: 'tasks', 'action_items'.",
    )

    lastBackupAt: Optional[int] = Field(
        default=None,
        description="Client timestamp (ms) cutoff for this backup run.",
    )

    @validator("tables")
    def validate_tables(cls, v):
        if not v:
            raise ValueError("at least one table required")
        for table_name in v.keys():
            if table_name not in ("tasks", "action_items"):
                raise ValueError(f"unknown table: {table_name}")
        return v


class SyncBackupResponse(BaseModel):
    """POST /v1/sync/backup response body."""

    accepted: List[Dict[str, Any]] = Field(
        description="Rows successfully merged.",
    )
    rejected: List[Dict[str, Any]] = Field(
        description="Rows rejected with reason.",
    )
    nextDownloadToken: Optional[str] = Field(
        default=None,
        description="Token for next download request (if more data available).",
    )
    nextDownloadAt: Optional[int] = Field(
        default=None,
        description="Server timestamp for next download (ms since epoch).",
    )
    nextBatchCount: int = Field(
        default=0,
        description="Number of rows in next batch (for pagination).",
    )


class SyncUpdatesResponse(BaseModel):
    """GET /v1/sync/updates response body."""

    rows: List[Dict[str, Any]] = Field(
        description="Rows changed since last download.",
    )
    nextDownloadToken: Optional[str] = Field(
        default=None,
        description="Opaque token for next download request.",
    )
    nextDownloadAt: Optional[int] = Field(
        default=None,
        description="Server timestamp for next download (ms since epoch).",
    )
    nextBatchCount: int = Field(
        default=0,
        description="Number of rows in next batch.",
    )


# ---------- Endpoints ----------


@router.post("/backup", response_model=SyncBackupResponse)
async def sync_backup_endpoint(
    request: Request,
    body: SyncBackupRequest,
    user: dict = Depends(get_current_user),
    idempotency_key: Optional[str] = Header(None, alias="Idempotency-Key"),
):
    """Upload dirty rows from Room tables (LWW merge).

    Client sends dirty rows grouped by table (tasks first, then action_items).
    Server merges each row by LWW, returns accepted/rejected rows, and the
    client advances lastBackupAt only after every batch is accepted.
    """
    user_id = user.get("sub")
    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token missing user id",
        )

    # Validate idempotency key format (if provided)
    if idempotency_key:
        if len(idempotency_key) > 128:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Idempotency-Key too long",
            )

    # Convert rows to dicts for sync service
    table_data: Dict[str, List[Dict[str, Any]]] = {}
    for table_name, rows in body.tables.items():
        table_data[table_name] = [row.dict() for row in rows]

    # Enforce batch size limit
    total_rows = sum(len(rows) for rows in table_data.values())
    if total_rows > MAX_BATCH_ROWS:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Batch too large: {total_rows} rows (max {MAX_BATCH_ROWS})",
        )

    # Apply LWW merge
    result = sync_backup(user_id, idempotency_key, table_data)

    return SyncBackupResponse(
        accepted=result["accepted"],
        rejected=result["rejected"],
        nextDownloadToken=result.get("nextDownloadToken"),
        nextDownloadAt=result.get("nextDownloadAt"),
        nextBatchCount=result.get("nextBatchCount", 0),
    )


@router.get("/updates", response_model=SyncUpdatesResponse)
async def sync_updates_endpoint(
    lastDownloadAt: str = Query(..., description="Opaque token from previous download response"),
    limit: int = Query(500, ge=1, le=500, description="Max rows per page"),
    user: dict = Depends(get_current_user),
):
    """Download rows changed since last download (keyset pagination).

    Client sends lastDownloadAt token (opaque base64url JSON) from previous
    response. Server selects next (server_updated_at, table_rank, syncId)
    rows, so equal milliseconds still page correctly. Add full=true for
    a full scan (not implemented in v1).
    """
    user_id = user.get("sub")
    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token missing user id",
        )

    result = sync_updates(user_id, lastDownloadAt, limit)

    return SyncUpdatesResponse(
        rows=result["rows"],
        nextDownloadToken=result["nextDownloadToken"],
        nextDownloadAt=result["nextDownloadAt"],
        nextBatchCount=result["nextBatchCount"],
    )