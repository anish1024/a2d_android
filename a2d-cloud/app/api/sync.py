"""Sync API routes: backup, updates, tasks, import and export endpoints.

POST /v1/sync/backup  - Upload dirty rows from Room tables (LWW merge)
GET  /v1/sync/updates - Download rows changed since last download (keyset pagination)
GET  /v1/sync/tasks   - List tasks with nested activities (web dashboard)
GET  /v1/sync/export  - Export canonical JSON / CSV
POST /v1/sync/import  - Import canonical JSON (dry-run or apply)

All endpoints require Bearer token auth. Backup accepts Idempotency-Key header
for safe retry. Updates is naturally idempotent (read-only).
"""
import io
import csv
import json
import time
import uuid
from typing import Any, Dict, List, Optional
from fastapi import APIRouter, Depends, File, Header, HTTPException, Query, Request, status, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field, validator
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.api.dependencies import get_current_user
from app.tenant import get_tenant_store
from app.sync import (
    MAX_BATCH_ROWS,
    sync_backup,
    sync_updates,
    next_server_stamp,
)


router = APIRouter(prefix="/v1/sync", tags=["sync"])


# ---------- Shared helpers and models ----------

class ImportReport(BaseModel):
    valid: bool
    wouldCreate: int = 0
    wouldUpdate: int = 0
    wouldSkip: int = 0
    duplicates: int = 0
    errors: List[str] = []
    touchedSyncIds: Dict[str, List[str]] = {"tasks": [], "action_items": []}


class TaskReadResponse(BaseModel):
    tasks: List[Dict[str, Any]]
    total: int


def _row_to_task_dict(row: Any) -> Dict[str, Any]:
    return {
        "syncId": row[0],
        "taskName": row[1],
        "description": row[2],
        "priority": row[3],
        "context": row[4],
        "achieved": bool(row[5]),
        "creationTime": row[6],
        "updatedAt": row[7],
        "deleted": row[8] is not None,
    }


def _row_to_activity_dict(row: Any) -> Dict[str, Any]:
    return {
        "syncId": row[0],
        "taskSyncId": row[1],
        "description": row[2],
        "achieved": bool(row[3]),
        "updatedAt": row[4],
        "deleted": row[5] is not None,
    }


async def _validate_import_json(data: bytes) -> Dict[str, Any]:
    try:
        doc = json.loads(data.decode("utf-8"))
    except json.JSONDecodeError as e:
        raise HTTPException(status_code=422, detail=f"Invalid JSON: {e}")

    if doc.get("format") != "a2d.tasks":
        raise HTTPException(status_code=422, detail="Invalid format field (expected 'a2d.tasks')")
    fv = doc.get("formatVersion")
    if fv != 1:
        raise HTTPException(status_code=422, detail=f"Unsupported formatVersion: {fv} (expected 1)")

    tasks = doc.get("tasks", [])
    if not isinstance(tasks, list):
        raise HTTPException(status_code=422, detail="tasks must be an array")

    return doc


def _apply_import(
    session: Session,
    user_id: str,
    doc: Dict[str, Any],
    dry_run: bool,
    on_conflict: str,
) -> ImportReport:
    report = ImportReport(valid=True)
    tasks = doc.get("tasks", [])
    now = int(time.time() * 1000)

    for task_data in tasks:
        sync_id = task_data.get("syncId")
        activities = task_data.get("activities", [])

        if not sync_id:
            report.errors.append(f"Task missing syncId: {task_data.get('taskName', 'unknown')}")
            report.valid = False
            continue

        existing = session.execute(
            text("SELECT server_updated_at FROM tasks WHERE id = :id"),
            {"id": sync_id},
        ).fetchone()

        if existing:
            if on_conflict == "skip":
                report.wouldSkip += 1
                continue
            elif on_conflict == "duplicate":
                sync_id = str(uuid.uuid4())
                report.wouldCreate += 1
            else:  # replace
                report.wouldUpdate += 1
        else:
            report.wouldCreate += 1

        fresh_stamp = next_server_stamp(session, user_id)

        task_fields = {
            "id": sync_id,
            "task_name": task_data.get("taskName", ""),
            "description": task_data.get("description", ""),
            "priority": task_data.get("priority", 1),
            "context": task_data.get("context", "No Context"),
            "achieved": 1 if task_data.get("achieved", False) else 0,
            "creation_time": task_data.get("creationTime", ""),
            "updated_at": task_data.get("updatedAt", now),
            "deleted_at": now if task_data.get("deleted", False) else None,
            "server_updated_at": fresh_stamp,
            "origin_device": "import",
        }

        if not dry_run:
            session.execute(
                text("""
                    INSERT INTO tasks (id, task_name, description, priority, context, achieved,
                                       creation_time, updated_at, deleted_at, server_updated_at, origin_device)
                    VALUES (:id, :task_name, :description, :priority, :context, :achieved,
                            :creation_time, :updated_at, :deleted_at, :server_updated_at, :origin_device)
                    ON CONFLICT(id) DO UPDATE SET
                        task_name = :task_name,
                        description = :description,
                        priority = :priority,
                        context = :context,
                        achieved = :achieved,
                        creation_time = :creation_time,
                        updated_at = :updated_at,
                        deleted_at = :deleted_at,
                        server_updated_at = :server_updated_at,
                        origin_device = :origin_device
                """),
                task_fields,
            )

        report.touchedSyncIds["tasks"].append(sync_id)

        for act in activities:
            act_sync_id = act.get("syncId")
            if not act_sync_id:
                act_sync_id = str(uuid.uuid4())
            else:
                existing_act = session.execute(
                    text("SELECT server_updated_at FROM action_items WHERE id = :id"),
                    {"id": act_sync_id},
                ).fetchone()
                if existing_act and on_conflict == "skip":
                    report.wouldSkip += 1
                    continue

            fresh_stamp = next_server_stamp(session, user_id)

            act_fields = {
                "id": act_sync_id,
                "task_id": sync_id,
                "description": act.get("description", ""),
                "achieved": 1 if act.get("achieved", False) else 0,
                "updated_at": act.get("updatedAt", now),
                "deleted_at": now if act.get("deleted", False) else None,
                "server_updated_at": fresh_stamp,
                "origin_device": "import",
            }

            if not dry_run:
                session.execute(
                    text("""
                        INSERT INTO action_items (id, task_id, description, achieved,
                                                  updated_at, deleted_at, server_updated_at, origin_device)
                        VALUES (:id, :task_id, :description, :achieved,
                                :updated_at, :deleted_at, :server_updated_at, :origin_device)
                        ON CONFLICT(id) DO UPDATE SET
                            task_id = :task_id,
                            description = :description,
                            achieved = :achieved,
                            updated_at = :updated_at,
                            deleted_at = :deleted_at,
                            server_updated_at = :server_updated_at,
                            origin_device = :origin_device
                    """),
                    act_fields,
                )

            report.touchedSyncIds["action_items"].append(act_sync_id)

    return report


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
async def sync_updates(
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


@router.get("/export")
async def export_data(
    format: str = Query("json", description="Output format (json|csv)"),
    includeDeleted: bool = Query(False, description="Include deleted rows"),
    user: dict = Depends(get_current_user),
):
    """Download canonical document (JSON) or flattened CSV."""
    user_id = user.get("sub")
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token missing user id")

    tenant_store = get_tenant_store()
    with tenant_store.tenant_session(user_id) as session:
        where = "" if includeDeleted else "WHERE deleted_at IS NULL"
        tasks_rows = session.execute(
            text(f"SELECT id, task_name, description, priority, context, achieved, creation_time, updated_at, deleted_at FROM tasks {where} ORDER BY priority ASC, task_name ASC, id ASC")
        ).fetchall()

        activities_rows = session.execute(
            text(f"SELECT id, task_id, description, achieved, updated_at, deleted_at FROM action_items {where} ORDER BY task_id ASC, id ASC")
        ).fetchall()

    activities_by_task: Dict[str, List[Dict[str, Any]]] = {}
    for row in activities_rows:
        task_sync_id = row[1]
        if task_sync_id not in activities_by_task:
            activities_by_task[task_sync_id] = []
        activities_by_task[task_sync_id].append(_row_to_activity_dict(row))

    tasks_list = []
    for row in tasks_rows:
        task = _row_to_task_dict(row)
        task["activities"] = activities_by_task.get(task["syncId"], [])
        tasks_list.append(task)

    exported_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    device_id = user.get("device_id", "")
    document = {
        "format": "a2d.tasks",
        "formatVersion": 1,
        "exportedAt": exported_at,
        "source": {"app": "a2d-cloud", "version": "0.1.0", "deviceId": device_id},
        "tasks": tasks_list,
    }

    if format == "csv":
        output = io.StringIO()
        writer = csv.writer(output)
        writer.writerow([
            "task_sync_id", "task_name", "description", "priority", "context", "task_achieved", "creation_time",
            "activity_sync_id", "activity_description", "activity_achieved"
        ])
        for task in tasks_list:
            activities = task["activities"]
            if activities:
                for act in activities:
                    writer.writerow([
                        task["syncId"], task["taskName"], task["description"], task["priority"], task["context"],
                        1 if task["achieved"] else 0, task["creationTime"],
                        act["syncId"], act["description"], 1 if act["achieved"] else 0
                    ])
            else:
                writer.writerow([
                    task["syncId"], task["taskName"], task["description"], task["priority"], task["context"],
                    1 if task["achieved"] else 0, task["creationTime"], "", "", ""
                ])
        csv_bytes = output.getvalue().encode("utf-8")
        return StreamingResponse(
            io.BytesIO(csv_bytes),
            media_type="text/csv",
            headers={"Content-Disposition": f'attachment; filename="a2d-tasks-{exported_at[:10]}.csv"'},
        )

    json_bytes = json.dumps(document, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    return StreamingResponse(
        io.BytesIO(json_bytes),
        media_type="application/json",
        headers={"Content-Disposition": f'attachment; filename="a2d-tasks-{exported_at[:10]}.json"'},
    )


@router.post("/import", response_model=ImportReport)
async def import_data(
    file: UploadFile = File(..., description="JSON file to import"),
    dry_run: bool = Query(True, description="Validate only, no writes"),
    on_conflict: str = Query("skip", description="Conflict policy: skip|replace|duplicate"),
    user: dict = Depends(get_current_user),
):
    """Import JSON or CSV. Validates against schema, then applies with conflict policy."""
    user_id = user.get("sub")
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token missing user id")

    if file.size and file.size > 10 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="File too large (max 10 MB)")

    content = await file.read()

    if file.filename and file.filename.lower().endswith(".csv"):
        raise HTTPException(status_code=422, detail="CSV import not yet implemented; use JSON")

    doc = await _validate_import_json(content)

    tenant_store = get_tenant_store()
    with tenant_store.tenant_session(user_id) as session:
        report = _apply_import(session, user_id, doc, dry_run, on_conflict)
        if dry_run:
            session.rollback()

    return report


@router.get("/tasks", response_model=TaskReadResponse)
async def list_tasks(
    priority: Optional[int] = Query(None, description="Filter by priority"),
    context: Optional[str] = Query(None, description="Filter by context"),
    q: Optional[str] = Query(None, description="Text search in taskName/description"),
    includeDeleted: bool = Query(False, description="Include deleted rows"),
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
    user: dict = Depends(get_current_user),
):
    """List tasks with nested activities for web dashboard."""
    user_id = user.get("sub")
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token missing user id")

    tenant_store = get_tenant_store()
    with tenant_store.tenant_session(user_id) as session:
        where_parts = []
        params = {"limit": limit, "offset": offset}
        if not includeDeleted:
            where_parts.append("deleted_at IS NULL")
        if priority is not None:
            where_parts.append("priority = :priority")
            params["priority"] = priority
        if context is not None:
            where_parts.append("context = :context")
            params["context"] = context
        if q:
            where_parts.append("(task_name LIKE :q OR description LIKE :q)")
            params["q"] = f"%{q}%"

        where = "WHERE " + " AND ".join(where_parts) if where_parts else ""

        total = session.execute(
            text(f"SELECT COUNT(*) FROM tasks {where}"),
            {k: v for k, v in params.items() if k not in ("limit", "offset")},
        ).scalar()

        # Get tasks
        tasks_rows = session.execute(
            text(f"""
                SELECT id, task_name, description, priority, context, achieved, creation_time, updated_at, deleted_at
                FROM tasks {where}
                ORDER BY priority ASC, task_name ASC, id ASC
                LIMIT :limit OFFSET :offset
            """),
            params,
        ).fetchall()

        task_ids = [row[0] for row in tasks_rows]
        if not task_ids:
            return TaskReadResponse(tasks=[], total=total)

        placeholders = ",".join(f":id{i}" for i in range(len(task_ids)))
        act_params = {f"id{i}": tid for i, tid in enumerate(task_ids)}
        activities_rows = session.execute(
            text(f"SELECT id, task_id, description, achieved, updated_at, deleted_at FROM action_items WHERE task_id IN ({placeholders}) ORDER BY task_id ASC, id ASC"),
            act_params,
        ).fetchall()

    activities_by_task: Dict[str, List[Dict[str, Any]]] = {}
    for row in activities_rows:
        task_sync_id = row[1]
        if task_sync_id not in activities_by_task:
            activities_by_task[task_sync_id] = []
        activities_by_task[task_sync_id].append(_row_to_activity_dict(row))

    tasks_list = []
    for row in tasks_rows:
        task = _row_to_task_dict(row)
        task["activities"] = activities_by_task.get(task["syncId"], [])
        tasks_list.append(task)

    return TaskReadResponse(tasks=tasks_list, total=total)


@router.get("/tasks/{sync_id}")
async def get_task(
    sync_id: str,
    includeDeleted: bool = Query(False, description="Include deleted activities"),
    user: dict = Depends(get_current_user),
):
    """Get a single task with its activities."""
    user_id = user.get("sub")
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token missing user id")

    tenant_store = get_tenant_store()
    with tenant_store.tenant_session(user_id) as session:
        where = "id = :id"
        if not includeDeleted:
            where += " AND deleted_at IS NULL"
        task_row = session.execute(
            text(f"SELECT id, task_name, description, priority, context, achieved, creation_time, updated_at, deleted_at FROM tasks WHERE {where}"),
            {"id": sync_id},
        ).fetchone()

        if not task_row:
            raise HTTPException(status_code=404, detail="Task not found")

        act_where = "task_id = :task_id"
        if not includeDeleted:
            act_where += " AND deleted_at IS NULL"
        activities_rows = session.execute(
            text(f"SELECT id, task_id, description, achieved, updated_at, deleted_at FROM action_items WHERE {act_where} ORDER BY id ASC"),
            {"task_id": sync_id},
        ).fetchall()

    task = _row_to_task_dict(task_row)
    task["activities"] = [_row_to_activity_dict(row) for row in activities_rows]
    return task