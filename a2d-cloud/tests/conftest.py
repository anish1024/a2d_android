"""Pytest configuration and fixtures."""
import os
import tempfile
import pytest
from sqlalchemy import text

# Force file-based test DB before any app modules are imported
os.environ["A2D_DATABASE_URL"] = "sqlite:///test.db"


@pytest.fixture(scope="session")
def test_engine():
    """Create a file-based control DB engine and tables once per session."""
    import pathlib
    # Clean any stale test DB from a previous run
    pathlib.Path("test.db").unlink(missing_ok=True)
    from app.control_db import reset_control_db
    reset_control_db("sqlite:///test.db")

    from app.control_db import get_engine, get_control_session
    engine = get_engine()

    # Create a test user
    from app.security import hash_password
    import time, secrets
    user_id = secrets.token_urlsafe(16)
    now = int(time.time() * 1000)
    pw_hash = hash_password("test123")
    with get_control_session() as s:
        s.execute(text(
            "INSERT INTO users (id, email, password_hash, is_active, created_at) "
            "VALUES (:id, :email, :pw, 1, :ts)"
        ), {"id": user_id, "email": "test@example.com", "pw": pw_hash, "ts": now})
        s.execute(text(
            "INSERT INTO user_storage (user_id, schema_version, bytes_used, folder_created_at) "
            "VALUES (:id, 1, 0, :ts)"
        ), {"id": user_id, "ts": now})

    yield engine


@pytest.fixture
def test_user_id(test_engine):
    """The user id used by the shared test user (test@example.com)."""
    with test_engine.connect() as conn:
        row = conn.execute(
            text("SELECT id FROM users WHERE email = 'test@example.com'")
        ).fetchone()
    return row[0]


@pytest.fixture
def client(test_engine, test_user_id):
    """FastAPI TestClient with the test control DB and a fresh tenant dir."""
    # Reload main to pick up the reset control DB
    import importlib
    import app.main
    importlib.reload(app.main)

    # Fresh tenant data dir per test for isolation
    data_dir = tempfile.mkdtemp(prefix="a2d-tenant-")
    from app.tenant import init_tenant_store, TenantStore
    init_tenant_store(data_dir)
    # Provision the tenant so sync calls have a DB
    TenantStore(data_dir).provision_tenant(test_user_id)

    from fastapi.testclient import TestClient
    return TestClient(app.main.app)


@pytest.fixture
def auth_headers(client, test_user_id):
    """Bearer headers for the shared test user."""
    r = client.post(
        "/v1/auth/login",
        data={"email": "test@example.com", "password": "test123"},
    )
    assert r.status_code == 200
    token = r.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}
