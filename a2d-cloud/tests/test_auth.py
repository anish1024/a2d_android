"""Authentication API tests."""
from fastapi.testclient import TestClient


def test_healthz(client: TestClient):
    r = client.get("/healthz")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}


def test_readyz(client: TestClient):
    r = client.get("/readyz")
    assert r.status_code == 200
    assert r.json() == {"status": "ready"}


def test_root(client: TestClient):
    r = client.get("/")
    assert r.status_code == 200
    data = r.json()
    assert data["service"] == "a2d-cloud"
    assert data["login"] == "/login"


def test_login_success(client: TestClient):
    r = client.post(
        "/v1/auth/login",
        data={"email": "test@example.com", "password": "test123"},
    )
    assert r.status_code == 200
    data = r.json()
    assert data["token_type"] == "bearer"
    assert data["email"] == "test@example.com"
    assert "access_token" in data and data["access_token"]
    assert "refresh_token" in data and data["refresh_token"]
    assert data["expires_in"] > 0


def test_login_wrong_password(client: TestClient):
    r = client.post(
        "/v1/auth/login",
        data={"email": "test@example.com", "password": "wrongpass"},
    )
    assert r.status_code == 401
    assert r.json()["detail"] == "Invalid credentials"


def test_login_invalid_email(client: TestClient):
    r = client.post(
        "/v1/auth/login",
        data={"email": "not-an-email", "password": "test123"},
    )
    assert r.status_code == 422


def test_login_missing_fields(client: TestClient):
    r = client.post("/v1/auth/login", data={"password": "test123"})
    assert r.status_code == 422
