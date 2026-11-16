#!/usr/bin/env bash
# =============================================================================
# A2D cloud - restore from an archive or a full recovery set.
#
# Both paths stop the app first, run the CLI restore under the tenant lock,
# and restart it. A full backup restore is a disaster-recovery action: the CLI
# leaves the previous state under $A2D_DATA_DIR/pre-restore-* and re-stamps the
# restored rows so every device converges on the next download.
# =============================================================================

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
    cat <<'EOF'
Usage:
  sudo ./restore.sh archive <archiveId> [--yes]
  sudo ./restore.sh backup  <backupId>  [--yes]

  archive  Restore one server-side gzip canonical-JSON tenant archive.
  backup   Restore a full recovery set from backup.sh. Replaces the control DB
           and every tenant folder; the previous state is kept under
           $A2D_DATA_DIR/pre-restore-*.

--yes skips the confirmation prompt. The app is stopped for the duration.
EOF
}

[ "$#" -ge 1 ] || { usage >&2; exit 2; }

command="$1"
shift

case "$command" in
    -h|--help) usage; exit 0 ;;
    archive|backup) ;;
    *) usage >&2; die "unknown restore command: $command" ;;
esac

id="${1:-}"
[ -n "$id" ] || { usage >&2; die "missing restore id"; }
shift

ASSUME_YES=0
while [ "$#" -gt 0 ]; do
    case "$1" in
        --yes|-y) ASSUME_YES=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; die "unknown argument: $1" ;;
    esac
done
export A2D_ASSUME_YES="$ASSUME_YES"

case "$command" in
    backup)
        [[ "$id" =~ ^[0-9]{8}-[0-9]{6}$ ]] || die "invalid backup id: $id (expected YYYYmmdd-HHMMSS)"
        ;;
    archive)
        [[ "$id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die "invalid archive id: $id"
        ;;
esac

require_root
need_cmd curl
need_cmd supervisorctl
require_env_file
validate_env
require_backend

if [ "$command" = "backup" ]; then
    confirm "Restore backup $id? This replaces the control DB and all tenant data." || die "aborted"
    cli_args=(-m app.cli restore-backup --backup-id "$id" --confirm)
else
    confirm "Restore archive $id? This rewrites that tenant's live replica." || die "aborted"
    cli_args=(-m app.cli restore --archive "$id" --confirm)
fi

log "stopping app"
supervisorctl stop a2d-app || true

log "restoring $command $id"
if ! as_run_user "${cli_args[@]}"; then
    warn "restore failed; restarting the app without further changes"
    supervisorctl start a2d-app || true
    die "restore failed; inspect the logs before retrying"
fi

log "starting app"
supervisorctl start a2d-app || true
wait_for_health

log "restore complete: $command $id"
log "verify counts and device sync before removing any pre-restore copy"