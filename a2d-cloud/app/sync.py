"""Sync service: LWW merge, keyset pagination, idempotency.

All sync operations work against a single tenant (user_id) determined by
the access token. Task tables carry no user_id column - the tenant store
opens exactly one tenant database derived from the token's user_id.
"""
import base64
import json
import time
from typing import Any, Dict, List, Optional, Tuple

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.control_db import get_control_session
from app.tenant import get_tenant_store


# Table rank determines keyset order between tables.
# Lower rank sorts first; tasks before action_items.
TABLE_RANKS = {
    "tasks": 1,
    "action_items": 2,
}

SYNCABLE_TABLES = {"tasks", "action_items"}

# Max rows per sync batch (matches FEAT-0002 spec)
MAX_BATCH_ROWS = 500

# Idempotency key retention window (seconds)
IDEMPOTENCY_RETENTION_SECONDS = 24 * 60 * 60  # 24 hours


def next_server_stamp(session: Session, user_id: str) -> int:
    """Return and advance the tenant's monotonic server_updated_at counter.

    Called within an active tenant session transaction.
    Returns fresh millisecond stamp.
    """
    now = int(time.time() * 1000)

    row = session.execute(
        text("SELECT last_server_stamp FROM user_state WHERE id = 1")
    ).fetchone()
    current_stamp = row[0] if row else 0

    new_stamp = max(current_stamp, now) + 1
    session.execute(
        text(
            "UPDATE user_state SET last_server_stamp = :stamp, updated_at = :ts WHERE id = 1"
        ),
        {"stamp": new_stamp, "ts": now},
    )

    return new_stamp


def decode_token(last_download_at: str) -> Optional[Tuple[int, int, str]]:
    """Decode lastDownloadAt opaque token.

    Token format: base64url({"t": server_updated_at_ms, "r": table_rank, "id": syncId})
    Returns (t, r, id) tuple or None if invalid.
    """
    try:
        padding = 4 - (len(last_download_at) % 4)
        if padding != 4:
            last_download_at += "=" * padding
        decoded = base64.urlsafe_b64decode(last_download_at).decode()
        data = json.loads(decoded)
        t = int(data["t"])
        r = int(data["r"])
        id_val = str(data["id"])
        return t, r, id_val
    except (ValueError, KeyError, TypeError, json.JSONDecodeError):
        return None


def encode_token(t: int, r: int, id_val: str) -> str:
    """Encode opaque lastDownloadAt token."""
    payload = json.dumps({"t": t, "r": r, "id": id_val}, separators=(",", ":"))
    return base64.urlsafe_b64encode(payload.encode()).rstrip(b"=").decode()


def _row_to_dict(table_name: str, row: Any) -> Dict[str, Any]:
    """Convert a row tuple to a sync row dict."""
    if table_name == "tasks":
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
            "server_updated_at": row[9],
            "origin_device": row[10],
        }
    else:  # action_items
        return {
            "syncId": row[0],
            "taskSyncId": row[1],
            "description": row[2],
            "achieved": bool(row[3]),
            "updatedAt": row[4],
            "deleted": row[5] is not None,
            "server_updated_at": row[6],
            "origin_device": row[7],
        }


def sync_backup(
    user_id: str,
    idempotency_key: Optional[str],
    table_data: Dict[str, List[Dict[str, Any]]],
) -> Dict[str, Any]:
    """Apply dirty rows from each Room table using LWW merge.

    Args:
        user_id: The authenticated user id (from access token).
        idempotency_key: Optional idempotency key for retry safety.
        table_data: Dict mapping table name ("tasks" | "action_items")
            to list of row dicts (JSON representation of the sync frame).

    Returns dict with "accepted", "rejected", "nextDownloadToken", etc.
    """
    accepted: List[Dict[str, Any]] = []
    rejected: List[Dict[str, Any]] = []

    # Track last accepted row for nextDownloadToken
    last_accepted_stamp = 0
    last_accepted_rank = 0
    last_accepted_id = ""

    # Idempotency check - if key already processed, return cached response
    if idempotency_key:
        with get_control_session() as session:
            row = session.execute(
                text("SELECT response_json FROM idempotency_keys WHERE key = :key"),
                {"key": idempotency_key},
            ).fetchone()
            if row is not None:
                return json.loads(row[0])

    now = int(time.time() * 1000)

    for table_name in ("tasks", "action_items"):
        rows = table_data.get(table_name, [])
        if not rows:
            continue

        with get_tenant_store().tenant_session(user_id) as session:
            for row_data in rows:
                sync_id = row_data.get("syncId")
                if not sync_id:
                    rejected.append({**row_data, "_reason": "missing syncId"})
                    continue

                if table_name == "tasks":
                    required = ["taskName"]
                else:
                    required = ["description", "taskSyncId"]
                missing = [f for f in required if row_data.get(f) in (None, "")]
                if missing:
                    rejected.append({**row_data, "_reason": f"missing fields: {', '.join(missing)}"})
                    continue

                existing = session.execute(
                    text(f"SELECT server_updated_at FROM {table_name} WHERE id = :id"),
                    {"id": sync_id},
                ).fetchone()

                client_updated_at = int(row_data.get("updatedAt", 0))
                deleted = bool(row_data.get("deleted", False))

                fresh_stamp = next_server_stamp(session, user_id)

                if existing is None:
                    # New row - insert
                    if table_name == "tasks":
                        session.execute(
                            text(
                                f"INSERT INTO {table_name} "
                                "(id, task_name, description, priority, context, achieved, "
                                "creation_time, updated_at, deleted_at, server_updated_at, origin_device) "
                                "VALUES (:id, :task_name, :description, :priority, :context, :achieved, "
                                ":creation_time, :updated_at, :deleted_at, :server_updated_at, :origin_device)"
                            ),
                            {
                                "id": sync_id,
                                "task_name": row_data.get("taskName", ""),
                                "description": row_data.get("description") or "",
                                "priority": row_data.get("priority", 1),
                                "context": row_data.get("context") or "No Context",
                                "achieved": 1 if row_data.get("achieved", False) else 0,
                                "creation_time": row_data.get("creationTime", ""),
                                "updated_at": client_updated_at,
                                "deleted_at": now if deleted else None,
                                "server_updated_at": fresh_stamp,
                                "origin_device": row_data.get("origin_device") or "client",
                            }
                        )
                    else:  # action_items
                        session.execute(
                            text(
                                f"INSERT INTO {table_name} "
                                "(id, task_id, description, achieved, updated_at, deleted_at, "
                                "server_updated_at, origin_device) "
                                "VALUES (:id, :task_id, :description, :achieved, "
                                ":updated_at, :deleted_at, :server_updated_at, :origin_device)"
                            ),
                            {
                                "id": sync_id,
                                "task_id": row_data.get("taskSyncId"),
                                "description": row_data.get("description") or "",
                                "achieved": 1 if row_data.get("achieved", False) else 0,
                                "updated_at": client_updated_at,
                                "deleted_at": now if deleted else None,
                                "server_updated_at": fresh_stamp,
                                "origin_device": row_data.get("origin_device") or "client",
                            }
                        )
                    accepted.append({**row_data, "_synced": True})
                    last_accepted_stamp = fresh_stamp
                    last_accepted_rank = TABLE_RANKS.get(table_name, 0)
                    last_accepted_id = str(sync_id)

                else:
                    existing_server_updated_at = existing[0]

                    # If server version is newer (server stamp > client updated_at), keep server version
                    if existing_server_updated_at > client_updated_at:
                        rejected.append({**row_data, "_reason": "server version newer (LWW)"})
                        continue

                    # Client wins LWW - update
                    if table_name == "tasks":
                        session.execute(
                            text(
                                f"UPDATE {table_name} SET "
                                "task_name = :task_name, description = :description, "
                                "priority = :priority, context = :context, achieved = :achieved, "
                                "creation_time = :creation_time, updated_at = :updated_at, "
                                "deleted_at = :deleted_at, server_updated_at = :server_updated_at, "
                                "origin_device = :origin_device "
                                "WHERE id = :id"
                            ),
                            {
                                "id": sync_id,
                                "task_name": row_data.get("taskName", ""),
                                "description": row_data.get("description") or "",
                                "priority": row_data.get("priority", 1),
                                "context": row_data.get("context") or "No Context",
                                "achieved": 1 if row_data.get("achieved", False) else 0,
                                "creation_time": row_data.get("creationTime", ""),
                                "updated_at": client_updated_at,
                                "deleted_at": now if deleted else None,
                                "server_updated_at": fresh_stamp,
                                "origin_device": row_data.get("origin_device") or "client",
                            }
                        )
                    else:  # action_items
                        session.execute(
                            text(
                                f"UPDATE {table_name} SET "
                                "task_id = :task_id, description = :description, "
                                "achieved = :achieved, updated_at = :updated_at, "
                                "deleted_at = :deleted_at, server_updated_at = :server_updated_at, "
                                "origin_device = :origin_device WHERE id = :id"
                            ),
                            {
                                "id": sync_id,
                                "task_id": row_data.get("taskSyncId"),
                                "description": row_data.get("description") or "",
                                "achieved": 1 if row_data.get("achieved", False) else 0,
                                "updated_at": client_updated_at,
                                "deleted_at": now if deleted else None,
                                "server_updated_at": fresh_stamp,
                                "origin_device": row_data.get("origin_device") or "client",
                            }
                        )
                    accepted.append({**row_data, "_synced": True})
                    last_accepted_stamp = fresh_stamp
                    last_accepted_rank = TABLE_RANKS.get(table_name, 0)
                    last_accepted_id = str(sync_id)

    response = {
        "accepted": accepted,
        "rejected": rejected,
        "nextDownloadToken": encode_token(last_accepted_stamp, last_accepted_rank, last_accepted_id) if last_accepted_stamp > 0 else None,
        "nextDownloadAt": last_accepted_stamp if last_accepted_stamp > 0 else now,
        "nextBatchCount": len(accepted),
    }

    if idempotency_key:
        with get_control_session() as session:
            session.execute(
                text(
                    "INSERT OR REPLACE INTO idempotency_keys (key, response_json, created_at) VALUES (:key, :resp, :ts)"
                ),
                {"key": idempotency_key, "resp": json.dumps(response), "ts": now},
            )

    return response


def sync_updates(
    user_id: str,
    last_download_at: str,
    limit: int,
) -> Dict[str, Any]:
    """Download rows changed since last download using keyset pagination.

    Returns dict with "rows", "nextDownloadToken", "nextDownloadAt", "nextBatchCount".
    """
    all_rows: List[Dict[str, Any]] = []
    last_t = 0
    last_r = 0
    last_id = ""

    cursor = decode_token(last_download_at)
    if cursor is None:
        t, r, id_val = 0, 0, ""
    else:
        t, r, id_val = cursor
    last_t = t
    last_r = r
    last_id = id_val

    with get_tenant_store().tenant_session(user_id) as session:
        for table_name in ("tasks", "action_items"):
            table_rank = TABLE_RANKS[table_name]

            if table_name == "tasks":
                cols = "id, task_name, description, priority, context, achieved, creation_time, updated_at, deleted_at, server_updated_at, origin_device"
            else:
                cols = "id, task_id, description, achieved, updated_at, deleted_at, server_updated_at, origin_device"

            if last_t == 0 and last_r == 0:
                sql = (
                    f"SELECT {cols} FROM {table_name} ORDER BY server_updated_at ASC, id ASC LIMIT :limit"
                )
                params: Dict[str, Any] = {"limit": limit}
            elif table_rank > last_r:
                sql = (
                    f"SELECT {cols} FROM {table_name} ORDER BY server_updated_at ASC, id ASC LIMIT :limit"
                )
                params = {"limit": limit}
            elif table_rank == last_r:
                sql = (
                    f"SELECT {cols} FROM {table_name} "
                    f"WHERE (server_updated_at > :t OR (server_updated_at = :t AND id > :id)) "
                    f"ORDER BY server_updated_at ASC, id ASC LIMIT :limit"
                )
                params = {"t": last_t, "id": last_id, "limit": limit}
            else:
                continue

            rows = session.execute(text(sql), params).fetchall()
            for row in rows:
                if table_name == "tasks":
                    last_t = row[9]
                    last_r = table_rank
                    last_id = row[0]
                else:
                    last_t = row[6]
                    last_r = table_rank
                    last_id = row[0]
                all_rows.append(_row_to_dict(table_name, row))

    next_token = None
    if last_t > 0:
        next_token = encode_token(last_t, last_r, last_id)

    return {
        "rows": all_rows,
        "nextDownloadToken": next_token,
        "nextDownloadAt": last_t if last_t > 0 else int(time.time() * 1000),
        "nextBatchCount": len(all_rows),
    }
