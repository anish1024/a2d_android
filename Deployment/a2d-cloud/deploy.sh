#!/usr/bin/env bash
# =============================================================================
# A2D cloud - install or update the service on this host.
#
# Idempotent sequence:
#   1. validate .env and the backend source
#   2. create the system user and virtualenv
#   3. install backend dependencies into the venv
#   4. prepare the data directory (owner: service user)
#   5. apply pending tenant migrations as the service user
#   6. write the supervisord drop-in (app + cloudflared)
#   7. reload supervisord and wait for health
#
# Run as root (sudo). The app binds to 127.0.0.1 only; Cloudflare Tunnel is the
# only entry point.
# =============================================================================

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
    cat <<'EOF'
Usage: sudo ./deploy.sh

First install and every update use the same command. Requires .env and the
backend source at ../../a2d-cloud/.
EOF
}

case "${1:-}" in
    -h|--help) usage; exit 0 ;;
    "") ;;
    *) usage >&2; die "unknown argument: $1" ;;
esac

require_root
need_cmd curl
need_cmd supervisorctl
require_env_file
validate_env
require_backend
mkdir -p "$A2D_LOG_DIR"

ensure_system_user

install_python_deps

prepare_data_dir

install_env_file

log "applying tenant migrations"
as_run_user -m app.cli migrate-users

write_supervisor_conf

log "reloading supervisord"
supervisorctl reread
supervisorctl update

wait_for_health

base_url="$(env_get A2D_PUBLIC_BASE_URL)"
log "deployed successfully"
log "public URL: $base_url"
log "health:     $base_url/healthz"
log "logs:       tail -f $A2D_LOG_DIR/app.log"
log "status:     supervisorctl status a2d-app a2d-cloudflared"