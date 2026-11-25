#!/usr/bin/env bash
# =============================================================================
# A2D cloud - write a timestamped recovery set.
#
# The backend CLI performs the SQLite VACUUM INTO snapshots, copies tenant
# archives/meta.json, writes a checksummed manifest, and prunes older sets.
# Output: $A2D_DATA_DIR/backups/<backupId>/
#
# This is safe to run while the app is live. Restore with restore.sh.
# =============================================================================

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
    cat <<'EOF'
Usage: sudo ./backup.sh [--keep N]

Creates one recovery set under $A2D_DATA_DIR/backups. Defaults to A2D_BACKUP_KEEP
from .env (14). Exit status is non-zero if the backup was not verified.
EOF
}

KEEP=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --keep)
            [ "$#" -ge 2 ] || die "--keep requires a number"
            KEEP="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            die "unknown argument: $1"
            ;;
    esac
done

require_root
need_cmd curl
need_cmd supervisorctl
require_env_file
require_backend

if [ -z "$KEEP" ]; then
    KEEP="$(env_get A2D_BACKUP_KEEP 14)"
fi
[[ "$KEEP" =~ ^[1-9][0-9]*$ ]] || die "backup retention must be a positive integer: $KEEP"

BACKUP_ID="$(date -u +%Y%m%d-%H%M%S)"
log "creating recovery set $BACKUP_ID (retain $KEEP)"

as_run_user -m app.cli ops-backup --backup-id "$BACKUP_ID" --keep "$KEEP"

log "backup complete"
log "host path: $A2D_DATA_DIR/backups/$BACKUP_ID"