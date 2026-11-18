#!/usr/bin/env bash
# =============================================================================
# A2D cloud - install or update the service on this host.
#
# Idempotent sequence (same whether deploying from source or from a tarball):
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
#
# Options:
#   --tarball <path>           Path to backend source tarball.
#                              Local path, or scp://user@host/path,
#                              or user@host:/path/to/file.tgz.
#   --target <path>            Installation root for the extracted source.
#                              Default: /opt/a2d-cloud
#   --from-source              Install from the source tree at ../../a2d-cloud
#                              (relative to deploy.sh location). Implies no --tarball.
# =============================================================================

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
    cat <<'EOF'
Usage: sudo ./deploy.sh [--tarball <path>] [--from-source] [--target <path>]

  --tarball <path>           Path to backend source tarball.
                            Local path, or scp://user@host/path,
                            or user@host:/path/to/file.tgz.

  --from-source              Install from the source tree at ../../a2d-cloud
                             (relative to deploy.sh location). This is the default
                             behaviour when no --tarball is supplied.

  --target <path>            Installation root for the extracted source.
                             Default: /opt/a2d-cloud
EOF
}

TARBALL=""
FROM_SOURCE=""
TARGET=""

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --tarball)
            TARBALL="${2:-}"
            shift 2
            ;;
        --from-source)
            FROM_SOURCE=1
            shift
            ;;
        --target)
            TARGET="${2:-}"
            shift 2
            ;;
        --)
            shift
            break
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
validate_env

# --- Determine A2D_APP_HOME ---
if [ -n "$TARBALL" ]; then
    # Deploy from tarball
    DEPLOY_FROM_TARBALL=1
    TARGET="${TARGET:-/opt/a2d-cloud}"
    STAGE="$(mktemp -d /tmp/a2d-deploy.XXXXXX)"
    TARBALL_LOCAL="$STAGE/tarball"
    fetch_tarballed "$TARBALL" "$TARBALL_LOCAL"
    tar -xzf "$TARBALL_LOCAL" -C "$STAGE"
    A2D_APP_HOME="$TARGET"
    A2D_VENV_DIR="$A2D_APP_HOME/.venv"
    A2D_DATA_DIR="$A2D_APP_HOME/data"
    mkdir -p "$A2D_APP_HOME"
    # Strip the optional top-level a2d-cloud/ wrapper so pyproject.toml lands
    # directly at $A2D_APP_HOME/pyproject.toml regardless of how the tarball was built.
    if [ -d "$STAGE/a2d-cloud" ]; then
        cp -a "$STAGE/a2d-cloud"/. "$A2D_APP_HOME/"
    else
        cp -a "$STAGE"/. "$A2D_APP_HOME/"
    fi
    rm -rf "$STAGE"
    log "installed backend source from tarball: $TARBALL"
elif [ "$FROM_SOURCE" = "1" ]; then
    # Deploy from source tree - copy to target if specified
    SOURCE_DIR="$(realpath -m "$SCRIPT_DIR/../../a2d-cloud")"
    A2D_APP_HOME="${A2D_APP_HOME:-$TARGET}"
    mkdir -p "$A2D_APP_HOME"
    # Strip the optional top-level a2d-cloud/ wrapper so pyproject.toml lands
    # directly at $A2D_APP_HOME/pyproject.toml regardless of how the source tree was structured.
    if [ -d "$SOURCE_DIR/a2d-cloud" ]; then
        cp -a "$SOURCE_DIR/a2d-cloud"/. "$A2D_APP_HOME/"
    else
        cp -a "$SOURCE_DIR"/. "$A2D_APP_HOME/"
    fi
    A2D_VENV_DIR="$A2D_APP_HOME/.venv"
    A2D_DATA_DIR="$A2D_APP_HOME/data"
    log "installed backend source from source tree: $SOURCE_DIR -> $A2D_APP_HOME"
else
    # Default: deploy from source tree (use the default location)
    A2D_APP_HOME="${A2D_APP_HOME:-$(realpath -m "$SCRIPT_DIR/../../a2d-cloud")}"
fi

require_backend

mkdir -p "$A2D_LOG_DIR"

ensure_system_user

if [ "${DEPLOY_FROM_TARBALL:-0}" = "1" ]; then
    log "changing ownership of $A2D_APP_HOME to $A2D_RUN_USER:$A2D_RUN_GROUP"
    chown -R "$A2D_RUN_USER:$A2D_RUN_GROUP" "$A2D_APP_HOME"
fi

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