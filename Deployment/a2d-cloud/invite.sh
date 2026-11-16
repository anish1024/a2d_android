#!/usr/bin/env bash
# =============================================================================
# A2D cloud - create an invited account.
#
# Registration is disabled by default. This wraps the backend CLI:
#   python -m app.cli create-user --email <email>
# =============================================================================

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
    cat <<'EOF'
Usage: sudo ./invite.sh <email>

Creates one invited account and its tenant folder. The CLI may prompt for an
initial password; keep the terminal attached.
EOF
}

case "${1:-}" in
    -h|--help) usage; exit 0 ;;
    "") usage >&2; exit 2 ;;
esac

email="$1"
shift
[ "$#" -eq 0 ] || { usage >&2; die "unexpected arguments: $*"; }

[[ "$email" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] \
    || die "invalid email address: $email"

require_root
need_cmd curl
need_cmd supervisorctl
require_env_file
validate_env
require_backend

log "creating invited account: $email"
as_run_user -m app.cli create-user --email "$email"

log "account created; ask the user to sign in from the Android app"