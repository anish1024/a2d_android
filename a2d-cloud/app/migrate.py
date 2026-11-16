import os
import sqlite3
import pathlib
import time
from contextlib import contextmanager

MIGRATIONS_DIR = pathlib.Path(__file__).parent.parent / "migrations"

@contextmanager
def get_db_connection(db_path: str):
    conn = sqlite3.connect(db_path)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA journal_mode=WAL")
    yield conn
    conn.close()

def apply_migration(db_path: str, migration_name: str):
    """Apply a single migration file"""
    migration_path = MIGRATIONS_DIR / migration_name
    if not migration_path.exists():
        raise FileNotFoundError(f"Migration file not found: {migration_path}")
    
    with get_db_connection(db_path) as conn:
        with open(migration_path, 'r') as f:
            sql = f.read()
        conn.executescript(sql)
        conn.commit()
    print(f"✅ Applied migration: {migration_name}")

def apply_all_migrations(db_path: str):
    """Apply all migrations in order"""
    migrations = sorted([
        f for f in os.listdir(MIGRATIONS_DIR) if f.endswith('.sql')
    ])
    
    # Ensure parent directory exists
    pathlib.Path(db_path).parent.mkdir(parents=True, exist_ok=True)
    
    for migration in migrations:
        apply_migration(db_path, migration)

if __name__ == "__main__":
    import sys
    if len(sys.argv) != 2:
        print("Usage: python migrate.py <path_to_control.db>")
        sys.exit(1)
    
    db_path = sys.argv[1]
    apply_all_migrations(db_path)