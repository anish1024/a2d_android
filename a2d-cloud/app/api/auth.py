"""Authentication API routes."""
import re
import time
import secrets

from fastapi import APIRouter, Form, HTTPException
from sqlalchemy import text

from app.control_db import get_control_session
from app.security import verify_password, generate_token
from app.config import get_settings


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
        user_id, device_id="", ttl_seconds=settings.access_token_ttl_seconds
    )
    refresh_token = generate_token(
        user_id, device_id="", ttl_seconds=settings.refresh_token_ttl_seconds
    )

    return {
        "access_token": access_token,
        "refresh_token": refresh_token,
        "token_type": "bearer",
        "expires_in": settings.access_token_ttl_seconds,
        "user_id": user_id,
        "email": user_email,
    }
