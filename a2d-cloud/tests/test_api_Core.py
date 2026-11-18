"""API integration tests converted from the bash script."""
import os
import tempfile
import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text


@pytest.fixture(scope="session")
def api_client():
    """Create a persistent app client with a provisioned tenant + test user."""
    os.environ["A2D_DATABASE_URL"] = "sqlite:///test_api.db"
    db_path = os.path.abspath("test_api.db")
    if os.path.exists(db_path):
        os.remove(db_path)

    import importlib
    from app.control_db import reset_control_db
    from app.security import hash_password
    from app.tenant import init_tenant_store, TenantStore

    reset_control_db("sqlite:///test_api.db")

    import app.main
    importlib.reload(app.main)

    test_user_id = str(uuid.uuid4())
    now = int(__import__("time").time() * 1000)
    with __import__("app.control_db").control_db.get_control_session() as session:
        session.execute(
            text(
                "INSERT OR IGNORE INTO users (id, email, password_hash, is_active, created_at) "
                "VALUES (:id, :email, :pw_hash, 1, :ts)"
            ),
            {
                "id": test_user_id,
                "email": "test@example.com",
                "pw_hash": hash_password("test123"),
                "ts": now,
            },
        )
        session.execute(
            text(
                "INSERT OR IGNORE INTO user_storage (user_id, schema_version, bytes_used, folder_created_at) "
                "VALUES (:id, 1, 0, :ts)"
            ),
            {"id": test_user_id, "ts": now},
        )

    data_dir = tempfile.mkdtemp(prefix="a2d-tenant-")
    init_tenant_store(data_dir)
    TenantStore(data_dir).provision_tenant(test_user_id)

    with TestClient(app.main.app) as client:
        yield client


@pytest.fixture(scope="session")
def login_headers(api_client):
    """Bearer headers for the shared test user."""
    response = api_client.post(
        "/v1/auth/login",
        data={"email": "test@example.com", "password": "test123"},
    )
    assert response.status_code == 200, response.text
    token = response.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


def test_login(api_client):
    """Test user login endpoint."""
    response = api_client.post(
        "/v1/auth/login",
        data={"email": "test@example.com", "password": "test123"},
    )

    assert response.status_code == 200
    payload = response.json()
    assert "access_token" in payload
    assert payload["access_token"] is not None


def test_backup_task(api_client, login_headers):
    """Test backup task endpoint with a task."""
    sync_id = str(uuid.uuid4())
    payload = {
        "tables": {
            "tasks": [{
                "syncId": sync_id,
                "taskName": "Task A",
                "description": "do A",
                "priority": 1,
                "context": "ctx",
                "achieved": False,
                "creationTime": "2026-11-15T08:00:00Z",
                "updatedAt": 1000,
                "deleted": False,
            }]
        }
    }

    response = api_client.post(
        "/v1/sync/backup",
        json=payload,
        headers=login_headers,
    )

    assert response.status_code == 200, response.text
    data = response.json()
    assert len(data["accepted"]) == 1
    assert data["accepted"][0]["taskName"] == "Task A"


def test_download_rows(api_client, login_headers):
    """Test download rows (sync updates) endpoint."""
    response = api_client.get(
        "/v1/sync/updates",
        params={"lastDownloadAt": "", "limit": 500},
        headers=login_headers,
    )

    assert response.status_code == 200, response.text
    data = response.json()
    assert "rows" in data
    assert "nextBatchCount" in data


def test_refresh_token(api_client, login_headers):
    """Test token refresh endpoint."""
    response = api_client.get(
        "/v1/auth/refresh",
        headers=login_headers,
    )

    assert response.status_code == 200, response.text
    data = response.json()
    assert "access_token" in data
    assert "refresh_token" in data


def test_idempotency(api_client, login_headers):
    """Test idempotency key prevents duplicate backups."""
    sync_id = str(uuid.uuid4())
    idempotency_key = f"my-key-{uuid.uuid4()}"
    payload = {
        "tables": {
            "tasks": [{
                "syncId": sync_id,
                "taskName": "Task A",
                "description": "do A",
                "priority": 1,
                "context": "ctx",
                "achieved": False,
                "creationTime": "2026-11-15T08:00:00Z",
                "updatedAt": 1000,
                "deleted": False,
            }]
        }
    }

    response1 = api_client.post(
        "/v1/sync/backup",
        json=payload,
        headers={**login_headers, "Idempotency-Key": idempotency_key},
    )
    assert response1.status_code == 200, response1.text
    data1 = response1.json()

    response2 = api_client.post(
        "/v1/sync/backup",
        json=payload,
        headers={**login_headers, "Idempotency-Key": idempotency_key},
    )
    assert response2.status_code == 200, response2.text
    data2 = response2.json()

    assert data1 == data2


def test_healthz(api_client):
    """Test health check endpoint."""
    response = api_client.get("/healthz")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"


def test_readyz(api_client):
    """Test readiness check endpoint."""
    response = api_client.get("/readyz")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ready"


def test_root(api_client):
    """Test root endpoint."""
    response = api_client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["service"] == "a2d-cloud"
    assert data["version"] == "0.1.0"