"""Control database engine, session, and helpers.

Uses SQLite WAL by default at $A2D_DATA_DIR/control.db.
A2D_DATABASE_URL can point to Postgres for the control plane only.
Tenant databases remain file-based SQLite in v1.
"""

import os
from pathlib import Path
from contextlib import contextmanager
from typing import Generator, Optional

from sqlalchemy import create_engine, text, event
from sqlalchemy.orm import Session, sessionmaker

from app.config import get_settings


_settings = get_settings()

# Ensure data directory exists
if "sqlite" in _settings.database_url and not _settings.database_url.startswith("sqlite:///:memory:"):
    db_path = _settings.database_url.replace("sqlite:///", "")
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)

engine = create_engine(
    _settings.database_url,
    echo=_settings.debug,
    pool_pre_ping=True,
    connect_args={"check_same_thread": False} if "sqlite" in _settings.database_url else {},
)

SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)


@contextmanager
def get_control_session() -> Generator[Session, None, None]:
    """Get a control database session."""
    session = SessionLocal()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def init_control_db():
    """Create control database tables if they don't exist."""
    with engine.connect() as conn:
        conn.execute(text("""
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                email TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            )
        """))
        conn.execute(text("""
            CREATE TABLE IF NOT EXISTS devices (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                name TEXT,
                platform TEXT,
                last_seen_at INTEGER,
                last_backup_at INTEGER,
                revoked_at INTEGER
            )
        """))
        conn.execute(text("""
            CREATE TABLE IF NOT EXISTS refresh_tokens (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
                token_hash TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                revoked_at INTEGER
            )
        """))
        conn.execute(text("""
            CREATE TABLE IF NOT EXISTS user_storage (
                user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                schema_version INTEGER NOT NULL,
                bytes_used INTEGER NOT NULL DEFAULT 0,
                folder_created_at INTEGER NOT NULL,
                last_migrated_at INTEGER
            )
        """))
        conn.execute(text("""
            CREATE TABLE IF NOT EXISTS archives (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL,
                kind TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                sha256 TEXT NOT NULL,
                relpath TEXT NOT NULL,
                note TEXT
            )
        """))
        conn.execute(text("""
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version INTEGER PRIMARY KEY,
                applied_at INTEGER NOT NULL
            )
        """))
        conn.commit()


def reset_control_db(database_url: str):
    """Reset the control database to a fresh state (for tests).

    Rebuilds the engine and session factory, then creates tables.
    """
    global engine, SessionLocal
    engine = create_engine(
        database_url,
        echo=_settings.debug,
        pool_pre_ping=True,
        connect_args={"check_same_thread": False} if "sqlite" in database_url else {},
    )
    SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)
    init_control_db()


def get_engine():
    return engine