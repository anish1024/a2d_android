"""TenantStore: per-account database provisioning, locking, and access.

Each registered account gets its own directory and SQLite file at:
    $A2D_DATA_DIR/users/<userId>/tasks.db

The tenant file is the isolation boundary. Task tables carry no user_id column;
a request opens exactly one tenant derived from the access token's userId.
"""
import os
import uuid
import pathlib
import time
from contextlib import contextmanager
from typing import Generator, Optional

from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session

from app.config import get_settings


_settings = get_settings()


class TenantStore:
    def __init__(self, data_dir: str):
        self.data_dir = pathlib.Path(data_dir)
        self.users_dir = self.data_dir / "users"
        self.users_dir.mkdir(parents=True, exist_ok=True)
        self.users_dir.chmod(0o700)

    def tenant_dir(self, user_id: str) -> pathlib.Path:
        """Return the tenant directory path for a user. Creates if needed."""
        tenant_path = self.users_dir / user_id
        tenant_path.mkdir(parents=True, exist_ok=True)
        tenant_path.chmod(0o700)
        return tenant_path

    def tenant_db_path(self, user_id: str) -> str:
        """Return the path to the tenant's tasks.db"""
        return str(self.tenant_dir(user_id) / "tasks.db")

    def tenant_lock_path(self, user_id: str) -> str:
        """Return the path to the tenant's .lock file"""
        return str(self.tenant_dir(user_id) / ".lock")

    def provision_tenant(self, user_id: str) -> str:
        """Create a tenant folder and initialize tasks.db with the base schema.
        Returns the tenant db path. Idempotent: safe to call again."""
        tenant_path = self.tenant_dir(user_id)
        db_path = self.tenant_db_path(user_id)
        
        if pathlib.Path(db_path).exists():
            # Already provisioned
            return db_path
        
        # Create tenant database
        conn = create_engine(f"sqlite:///{db_path}", connect_args={"check_same_thread": False})
        with conn.connect() as db_conn:
            db_conn.execute(text("""
                CREATE TABLE IF NOT EXISTS user_state (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    schema_version INTEGER NOT NULL,
                    last_server_stamp INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """))
            
            # tasks table (schema matches sync contract - syncId UUID PK)
            db_conn.execute(text("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,                    -- syncId (UUID)
                    task_name TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    priority INTEGER NOT NULL DEFAULT 1,
                    context TEXT NOT NULL DEFAULT 'No Context',
                    achieved INTEGER NOT NULL DEFAULT 0,
                    creation_time TEXT,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER,
                    server_updated_at INTEGER NOT NULL,
                    origin_device TEXT NOT NULL DEFAULT 'server'
                )
            """))
            
            db_conn.execute(text("""
                CREATE INDEX IF NOT EXISTS ix_tasks_server ON tasks(server_updated_at, id)
            """))
            db_conn.execute(text("""
                CREATE INDEX IF NOT EXISTS ix_tasks_deleted ON tasks(deleted_at)
            """))
            
            # action_items table
            db_conn.execute(text("""
                CREATE TABLE IF NOT EXISTS action_items (
                    id TEXT PRIMARY KEY,                    -- syncId (UUID)
                    task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                    description TEXT NOT NULL,
                    achieved INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER,
                    server_updated_at INTEGER NOT NULL,
                    origin_device TEXT NOT NULL DEFAULT 'server'
                )
            """))
            
            db_conn.execute(text("""
                CREATE INDEX IF NOT EXISTS ix_action_items_server ON action_items(server_updated_at, id)
            """))
            db_conn.execute(text("""
                CREATE INDEX IF NOT EXISTS ix_action_items_deleted ON action_items(deleted_at)
            """))
            db_conn.execute(text("""
                CREATE INDEX IF NOT EXISTS ix_action_items_task ON action_items(task_id)
            """))
            
            # idempotency_keys
            db_conn.execute(text("""
                CREATE TABLE IF NOT EXISTS idempotency_keys (
                    key TEXT PRIMARY KEY,
                    response_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """))
            
            # Initialize user_state single row
            db_conn.execute(text("""
                INSERT OR IGNORE INTO user_state (id, schema_version, last_server_stamp, created_at, updated_at)
                VALUES (1, 1, 0, :ts, :ts)
            """), {"ts": int(time.time() * 1000)})
            
            db_conn.commit()
        
        # Initialize user_storage in control DB
        # (This will be done by the control DB session in the calling context)
        
        return db_path

    @contextmanager
    def tenant_session(self, user_id: str) -> Generator[Session, None, None]:
        """Get a SQLAlchemy session for a tenant database."""
        db_path = self.tenant_db_path(user_id)
        engine = create_engine(
            f"sqlite:///{db_path}",
            connect_args={"check_same_thread": False},
        )
        from sqlalchemy.orm import Session
        session = Session(engine)
        try:
            yield session
            session.commit()
        except Exception:
            session.rollback()
            raise
        finally:
            session.close()
            engine.dispose()

    def get_tenant_stats(self, user_id: str) -> dict:
        """Return tenant stats: file size, row counts."""
        db_path = self.tenant_db_path(user_id)
        p = pathlib.Path(db_path)
        
        stats = {
            "db_exists": p.exists(),
            "db_size_bytes": p.stat().st_size if p.exists() else 0,
        }
        
        if p.exists():
            engine = create_engine(f"sqlite:///{db_path}", connect_args={"check_same_thread": False})
            with engine.connect() as conn:
                tasks_result = conn.execute(text("SELECT COUNT(*) FROM tasks")).scalar()
                activities_result = conn.execute(text("SELECT COUNT(*) FROM action_items")).scalar()
                stats["tasks_count"] = tasks_result
                stats["action_items_count"] = activities_result
            engine.dispose()
        
        return stats

    def tenant_exists(self, user_id: str) -> bool:
        """Check if a tenant directory exists."""
        return self.tenant_dir(user_id).exists()

    def remove_tenant(self, user_id: str) -> bool:
        """Remove a tenant folder and all its contents. Returns True if removed."""
        tenant_path = self.tenant_dir(user_id)
        if not tenant_path.exists():
            return False
        
        import shutil
        shutil.rmtree(tenant_path)
        return True


# Global tenant store instance (initialized by main app)
_tenant_store: Optional[TenantStore] = None


def get_tenant_store() -> TenantStore:
    """Get the global tenant store instance."""
    if _tenant_store is None:
        raise RuntimeError("TenantStore not initialized. Call init_tenant_store(data_dir) first.")
    return _tenant_store


def init_tenant_store(data_dir: str) -> TenantStore:
    """Initialize and return the global tenant store."""
    global _tenant_store
    _tenant_store = TenantStore(data_dir)
    return _tenant_store