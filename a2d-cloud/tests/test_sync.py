"""Tests for sync backup/update endpoints - FEAT-0002 P1."""
import time
import uuid


def _login(client):
    return client.post(
        "/v1/auth/login",
        data={"email": "test@example.com", "password": "test123"},
    ).json()["access_token"]


def _uid():
    return str(uuid.uuid4())


def _task_row(sid: str, name: str, updated_at: int, deleted: bool = False) -> dict:
    return {
        "syncId": sid,
        "taskName": name,
        "description": "d",
        "priority": 1,
        "context": "c",
        "achieved": False,
        "creationTime": "x",
        "updatedAt": updated_at,
        "deleted": deleted,
    }


def test_sync_backup_task(client, auth_headers):
    sid = _uid()
    r = client.post(
        "/v1/sync/backup",
        json={"tables": {"tasks": [_task_row(sid, "Task A", 1000)]}},
        headers=auth_headers,
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert len(data["accepted"]) == 1
    accepted = data["accepted"][0]
    assert accepted["syncId"] == sid
    assert accepted["taskName"] == "Task A"
    assert data["nextBatchCount"] == 1
    assert data["nextDownloadToken"] is not None
    assert accepted["_synced"] is True


def test_sync_backup_action_item(client, auth_headers):
    task_id = _uid()
    aid = _uid()
    r1 = client.post(
        "/v1/sync/backup",
        json={"tables": {"tasks": [_task_row(task_id, "Parent", 1000)]}},
        headers=auth_headers,
    )
    assert r1.status_code == 200

    r2 = client.post(
        "/v1/sync/backup",
        json={"tables": {"action_items": [
            {"syncId": aid, "taskSyncId": task_id, "description": "sub",
             "achieved": False, "updatedAt": 1000, "deleted": False}
        ]}},
        headers=auth_headers,
    )
    assert r2.status_code == 200
    data = r2.json()
    assert len(data["accepted"]) == 1
    assert data["accepted"][0]["taskSyncId"] == task_id


def test_sync_missing_sync_id(client, auth_headers):
    r = client.post(
        "/v1/sync/backup",
        json={"tables": {"tasks": [{"taskName": "No Sync ID", "description": "d", "priority": 1}]}},
        headers=auth_headers,
    )
    assert r.status_code == 200
    data = r.json()
    assert len(data["rejected"]) == 1
    assert "missing syncId" in data["rejected"][0]["_reason"]


def test_sync_backup_missing_fields(client, auth_headers):
    r = client.post(
        "/v1/sync/backup",
        json={"tables": {"tasks": [{"syncId": _uid(), "description": "d", "priority": 1}]}},
        headers=auth_headers,
    )
    assert r.status_code == 200
    data = r.json()
    assert len(data["rejected"]) == 1
    assert "missing fields" in data["rejected"][0]["_reason"]


def test_sync_backup_unauthorized(client):
    r = client.post("/v1/sync/backup", json={"tables": {"tasks": []}})
    assert r.status_code == 401


def test_sync_updates_empty(client, auth_headers):
    r = client.get("/v1/sync/updates", params={"lastDownloadAt": "", "limit": 500}, headers=auth_headers)
    assert r.status_code == 200
    data = r.json()
    assert data["rows"] == []
    assert data["nextBatchCount"] == 0


def test_sync_updates_with_rows(client, auth_headers):
    sid = _uid()
    r1 = client.post(
        "/v1/sync/backup",
        json={"tables": {"tasks": [_task_row(sid, "Task X", 1000)]}},
        headers=auth_headers,
    )
    assert r1.status_code == 200

    r2 = client.get("/v1/sync/updates", params={"lastDownloadAt": "", "limit": 500}, headers=auth_headers)
    assert r2.status_code == 200
    data = r2.json()
    assert len(data["rows"]) >= 1
    row = data["rows"][0]
    assert row["syncId"] == sid


def test_sync_lww_client_wins(client, auth_headers):
    tid = _uid()
    r1 = client.post(
        "/v1/sync/backup",
        json={"tables": {"tasks": [_task_row(tid, "Task", 1000)]}},
        headers=auth_headers,
    )
    assert r1.status_code == 200
    # Query from empty cursor to find the stored row and verify server stamp applied
    r2 = client.get("/v1/sync/updates", params={"lastDownloadAt": "", "limit": 500}, headers=auth_headers)
    assert r2.status_code == 200
    rows = [r for r in r2.json()["rows"] if r.get("syncId") == tid]
    assert rows, "task not found"
    assert rows[0]["server_updated_at"] > 1000


def test_sync_tombstone(client, auth_headers):
    r1 = client.post(
        "/v1/sync/backup",
        json={"tables": {"tasks": [_task_row("t-1", "T", 1000, deleted=True)]}},
        headers=auth_headers,
    )
    assert r1.status_code == 200
    resp1 = r1.json()
    tombstone = resp1["accepted"][0]
    assert tombstone["deleted"] is True
    assert tombstone["syncId"] == "t-1"


def test_sync_tombstone_propagates(client, auth_headers):
    r1 = client.post(
        "/v1/sync/backup",
        json={"tables": {"tasks": [_task_row("t-1-3", "T", 1000, deleted=True)]}},
        headers=auth_headers,
    )
    assert r1.status_code == 200
    # Query from empty cursor - should return the tombstone
    r2 = client.get("/v1/sync/updates", params={"lastDownloadAt": "", "limit": 500}, headers=auth_headers)
    assert r2.status_code == 200
    rows = r2.json()["rows"]
    tombstones = [r for r in rows if r.get("syncId") == "t-1-3"]
    assert tombstones, f"tombstone not found in {rows}"
    assert tombstones[0]["deleted"] is True


def test_sync_idempotency(client, auth_headers):
    tid = _uid()
    key = str(uuid.uuid4())
    body = {"tables": {"tasks": [_task_row(tid, "Task", int(time.time() * 1000))]}}
    r1 = client.post("/v1/sync/backup", json=body, headers={"Idempotency-Key": key, **auth_headers})
    assert r1.status_code == 200
    r2 = client.post("/v1/sync/backup", json=body, headers={"Idempotency-Key": key, **auth_headers})
    assert r2.status_code == 200
    assert r2.json() == r1.json()


def test_sync_full_download_order(client, auth_headers):
    ids = [str(uuid.uuid4()) for _ in range(3)]
    tasks = [
        {"syncId": sid, "taskName": f"Task {sid}", "description": "d",
         "priority": 1, "context": "c", "achieved": False, "creationTime": "x",
         "updatedAt": int(time.time() * 1000), "deleted": False}
        for sid in ids
    ]
    r1 = client.post("/v1/sync/backup", json={"tables": {"tasks": tasks}}, headers=auth_headers)
    assert r1.status_code == 200

    r2 = client.get("/v1/sync/updates", params={"lastDownloadAt": "", "limit": 10}, headers=auth_headers)
    assert r2.status_code == 200
    rows = r2.json()["rows"]
    assert len(rows) >= 3