"""Authentication API routes."""
import re
import time
import secrets

from fastapi import APIRouter, Form, HTTPException, Header, Depends
from sqlalchemy import text

from app.control_db import get_control_session
from app.security import verify_password, generate_token
from app.config import get_settings
from app.api.dependencies import get_current_user


router = APIRouter(prefix="/v1/auth", tags=["auth"])

settings = get_settings()

_EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


@router.post("/login")
async def login(email: str = Form(...), password: str = Form(...)):
    """Authenticate a user and return access + refresh tokens."""
    email = email.strip().lower()
    if not _EMAIL_RE.match(email):
        raise HTTPException(status_code=422, detail="Invalid email address")

    with get_control_session() as session:
        row = session.execute(
            text(
                "SELECT id, email, password_hash, is_active, created_at "
                "FROM users WHERE email = :email"
            ),
            {"email": email},
        ).fetchone()

    if row is None:
        raise HTTPException(status_code=401, detail="Invalid credentials")

    user_id, user_email, password_hash, is_active, created_at = row

    if not is_active:
        raise HTTPException(status_code=401, detail="Account disabled")

    if not verify_password(password, password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials")

    now = int(time.time())
    access_token = generate_token(
        user_id, device_id="", ttl_seconds=settings.access_token_ttl_seconds, email=user_email
    )
    refresh_token = generate_token(
        user_id, device_id="", ttl_seconds=settings.refresh_token_ttl_seconds, email=user_email
    )

    return {
        "access_token": access_token,
        "refresh_token": refresh_token,
        "token_type": "bearer",
        "expires_in": settings.access_token_ttl_seconds,
        "user_id": user_id,
        "email": user_email,
    }


@router.post("/logout")
async def logout(current_user: dict = Depends(get_current_user)):
    """Logout the current user by revoking all their refresh tokens."""
    user_id = current_user.get("sub")
    now = int(time.time())
    if not user_id:
        raise HTTPException(status_code=401, detail="User id missing")

    with get_control_session() as session:
        session.execute(
            text("UPDATE refresh_tokens SET revoked_at = :now "
                 "WHERE user_id = :user_id AND revoked_at IS NULL"),
            {"now": now, "user_id": user_id},
        )
    return {"detail": "Successfully logged out"}


@router.get("/refresh")
async def refresh_token(user: dict = Depends(get_current_user)):
    """Issue a fresh access + refresh token for an authenticated user.

    The caller must present a valid Bearer access token. This rotates the
    short-lived access token (and issues a new refresh token) so a worker can
    keep syncing without re-entering credentials.
    """
    user_id = user.get("sub")
    if not user_id:
        raise HTTPException(status_code=401, detail="Token missing user id")

    device_id = user.get("device_id", "")
    email = user.get("email")
    access_token = generate_token(
        user_id, device_id=device_id, ttl_seconds=settings.access_token_ttl_seconds, email=email
    )
    refresh_token = generate_token(
        user_id, device_id=device_id, ttl_seconds=settings.refresh_token_ttl_seconds, email=email
    )

    return {
        "access_token": access_token,
        "refresh_token": refresh_token,
        "token_type": "bearer",
        "expires_in": settings.access_token_ttl_seconds,
        "user_id": user_id,
    }


@router.get("/me")
async def me(current_user: dict = Depends(get_current_user)):
    """Return the email address of the currently authenticated user."""
    return {"email": current_user.get("email", current_user.get("sub", ""))}
