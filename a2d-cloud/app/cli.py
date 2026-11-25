"""Command-line interface for A2D Cloud operations."""
import argparse
import sys
import time
import secrets

from sqlalchemy import text


def cmd_create_user(args):
    """Create an invited user account."""
    from app.control_db import get_control_session, init_control_db
    from app.security import hash_password
    from app.tenant import init_tenant_store
    
    init_control_db()
    tenant_store = init_tenant_store("./data")
    
    user_id = secrets.token_urlsafe(16)
    now = int(time.time() * 1000)
    password_hash = hash_password(args.password)
    
    with get_control_session() as session:
        session.execute(
            text("INSERT INTO users (id, email, password_hash, is_active, created_at) "
                 "VALUES (:id, :email, :pw, 1, :ts)"),
            {"id": user_id, "email": args.email, "pw": password_hash, "ts": now}
        )
        session.execute(
            text("INSERT INTO user_storage (user_id, schema_version, bytes_used, folder_created_at) "
                 "VALUES (:id, 1, 0, :ts)"),
            {"id": user_id, "ts": now}
        )
    
    # Provision tenant folder
    tenant_store.provision_tenant(user_id)
    
    print(f"✅ Created user {args.email} (id={user_id})")
    print(f"   Tenant folder: ./data/users/{user_id}")
    return 0


def cmd_migrate_users(args):
    """Apply pending tenant migrations to all users."""
    from app.control_db import get_control_session, init_control_db
    from app.tenant import init_tenant_store
    
    init_control_db()
    tenant_store = init_tenant_store("./data")
    
    with get_control_session() as session:
        users = session.execute(text("SELECT id FROM users")).fetchall()
    
    for user in users:
        tenant_store.provision_tenant(user[0])
        print(f"  → migrated tenant {user[0]}")
    
    print(f"✅ Migrated {len(users)} tenants")
    return 0


def cmd_archive(args):
    """Create an archive of a tenant's data."""
    print("Archive command not yet implemented")
    return 0


def cmd_restore(args):
    """Restore a tenant from an archive."""
    print("Restore command not yet implemented")
    return 0


def cmd_restore_backup(args):
    """Restore from a backup."""
    print("Restore backup command not yet implemented")
    return 0


def cmd_ops_backup(args):
    """Run operations backup."""
    print("Ops backup command not yet implemented")
    return 0


def cmd_purge_user(args):
    """Purge a user and their data."""
    print("Purge user command not yet implemented")
    return 0


def cmd_prune(args):
    """Run retention jobs (prune expired tokens, tombstones, etc.)"""
    print("Prune command not yet implemented")
    return 0


def main():
    parser = argparse.ArgumentParser(prog="a2d-cloud", description="A2D Cloud CLI")
    subparsers = parser.add_subparsers(dest="command", help="Command to run")
    
    # create-user
    p_create = subparsers.add_parser("create-user", help="Create an invited user")
    p_create.add_argument("--email", required=True, help="User email")
    p_create.add_argument("--password", required=True, help="Initial password")
    
    # migrate-users
    subparsers.add_parser("migrate-users", help="Apply tenant migrations")
    
    # archive
    p_archive = subparsers.add_parser("archive", help="Create tenant archive")
    p_archive.add_argument("--user-id", help="User ID (optional)")
    p_archive.add_argument("--all", action="store_true", help="Archive all users")
    
    # restore
    p_restore = subparsers.add_parser("restore", help="Restore from archive")
    p_restore.add_argument("--archive-id", required=True, help="Archive ID")
    p_restore.add_argument("--confirm", action="store_true", help="Confirm restore")
    
    # restore-backup
    p_restore_backup = subparsers.add_parser("restore-backup", help="Restore from backup")
    p_restore_backup.add_argument("--backup-id", required=True, help="Backup ID")
    p_restore_backup.add_argument("--confirm", action="store_true", help="Confirm restore")
    
    # ops-backup
    p_ops_backup = subparsers.add_parser("ops-backup", help="Run operations backup")
    p_ops_backup.add_argument("--backup-id", required=True, help="Backup ID")
    p_ops_backup.add_argument("--keep", type=int, default=14, help="Retention count")
    
    # purge-user
    p_purge = subparsers.add_parser("purge-user", help="Purge a user")
    p_purge.add_argument("--id", required=True, help="User ID")
    
    # prune
    subparsers.add_parser("prune", help="Run retention jobs")
    
    args = parser.parse_args()
    
    if args.command == "create-user":
        return cmd_create_user(args)
    elif args.command == "migrate-users":
        return cmd_migrate_users(args)
    elif args.command == "archive":
        return cmd_archive(args)
    elif args.command == "restore":
        return cmd_restore(args)
    elif args.command == "restore-backup":
        return cmd_restore_backup(args)
    elif args.command == "ops-backup":
        return cmd_ops_backup(args)
    elif args.command == "purge-user":
        return cmd_purge_user(args)
    elif args.command == "prune":
        return cmd_prune(args)
    else:
        parser.print_help()
        return 1


if __name__ == "__main__":
    sys.exit(main())