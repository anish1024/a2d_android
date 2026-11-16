"""Control-plane SQLAlchemy models.

These are the only tables that hold identity, devices, tokens, archive registry,
and quota metadata. Task content never lives here.
"""
from datetime import datetime, timezone


class User:
    """users(id TEXT PK, email TEXT UNIQUE, password_hash TEXT, is_active INT, created_at INT)"""

    def __init__(self, user_id: str, email: str, password_hash: str, is_active: int, created_at: int):
        self.id = user_id
        self.email = email
        self.password_hash = password_hash
        self.is_active = is_active
        self.created_at = created_at


class Device:
    """devices(id TEXT PK, user_id FK, name, platform, last_seen_at, last_backup_at, revoked_at)"""

    def __init__(self, device_id: str, user_id: str, name: str, platform: str,
                 last_seen_at: int, last_backup_at: int, revoked_at: int):
        self.id = device_id
        self.user_id = user_id
        self.name = name
        self.platform = platform
        self.last_seen_at = last_seen_at
        self.last_backup_at = last_backup_at
        self.revoked_at = revoked_at


class RefreshToken:
    """refresh_tokens(id PK, user_id FK, device_id FK, token_hash, expires_at, revoked_at)"""

    def __init__(self, token_id: str, user_id: str, device_id: str, token_hash: str,
                 expires_at: int, revoked_at: int):
        self.id = token_id
        self.user_id = user_id
        self.device_id = device_id
        self.token_hash = token_hash
        self.expires_at = expires_at
        self.revoked_at = revoked_at


class UserStorage:
    """user_storage(user_id PK FK, schema_version, bytes_used, folder_created_at, last_migrated_at)"""

    def __init__(self, user_id: str, schema_version: int, bytes_used: int,
                 folder_created_at: int, last_migrated_at: int):
        self.user_id = user_id
        self.schema_version = schema_version
        self.bytes_used = bytes_used
        self.folder_created_at = folder_created_at
        self.last_migrated_at = last_migrated_at


class Archive:
    """archives(id PK, user_id FK, created_at, kind, size_bytes, sha256, relpath, note)"""

    def __init__(self, archive_id: str, user_id: str, created_at: int, kind: str,
                 size_bytes: int, sha256: str, relpath: str, note: str):
        self.id = archive_id
        self.user_id = user_id
        self.created_at = created_at
        self.kind = kind
        self.size_bytes = size_bytes
        self.sha256 = sha256
        self.relpath = relpath
        self.note = note
