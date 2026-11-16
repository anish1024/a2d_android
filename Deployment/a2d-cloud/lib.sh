#!/usr/bin/env bash
# =============================================================================
# Shared helpers for the A2D cloud deployment scripts.
#
# Sourced by deploy.sh, invite.sh, backup.sh, and restore.sh. Keep this file
# dependency-free: it only needs bash, coreutils, curl, and supervisorctl.
#
# The service runs as a dedicated system user (A2D_RUN_USER). Scripts that
# touch runtime data must run as root so they can chown and runuser.
# =============================================================================

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"

# Resolve the backend source tree (repo/a2d-cloud).
A2D_APP_HOME="${A2D_APP_HOME:-$(realpath -m "$SCRIPT_DIR/../../a2d-cloud")}"
A2D_VENV_DIR="${A2D_VENV_DIR:-$A2D_APP_HOME/.venv}"
A2D_DATA_DIR="${A2D_DATA_DIR:-$A2D_APP_HOME/data}"

# Runtime identity and bind address. Override in the environment before sourcing
# lib.sh, or let deploy.sh write them into the supervisord drop-in.
A2D_RUN_USER="${A2D_RUN_USER:-a2dcloud}"
A2D_RUN_GROUP="${A2D_RUN_GROUP:-$A2D_RUN_USER}"
A2D_LOG_DIR="${A2D_LOG_DIR:-/var/log/a2d-cloud}"
A2D_BIND_HOST="${A2D_BIND_HOST:-127.0.0.1}"
A2D_BIND_PORT="${A2D_BIND_PORT:-8000}"

# System supervisord drop-in that deploy.sh writes.
A2D_SUPERVISOR_CONF="${A2D_SUPERVISOR_CONF:-/etc/supervisor/conf.d/a2d-cloud.conf}"

export A2D_ENV_FILE="$ENV_FILE"

log()  { printf '[a2d-cloud] %s\n' "$*"; }
warn() { printf '[a2d-cloud] WARNING: %s\n' "$*" >&2; }
die()  { printf '[a2d-cloud] ERROR: %s\n' "$*" >&2; exit 1; }

need_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_root() {
    [ "$(id -u)" = "0" ] || die "this script must run as root (sudo)"
}

venv_python() {
    printf '%s\n' "$A2D_VENV_DIR/bin/python"
}

# Run the CLI as the service user, from the app home, with the venv python.
# Usage: as_run_user -m app.cli invite ...
as_run_user() {
    runuser -u "$A2D_RUN_USER" -- \
        env -C "$A2D_APP_HOME" "$A2D_VENV_DIR/bin/python" "$@"
}

supervisorctl() {
    command supervisorctl "$@"
}

require_env_file() {
    [ -f "$ENV_FILE" ] || die "missing $ENV_FILE; copy .env.example to .env and fill it in"

    local mode
    mode="$(stat -c '%a' "$ENV_FILE" 2>/dev/null || true)"
    if [ -n "$mode" ] && [ "$mode" != "600" ]; then
        warn "$ENV_FILE is mode $mode; tightening to 600"
        chmod 600 "$ENV_FILE" 2>/dev/null || warn "could not chmod 600 $ENV_FILE"
    fi
}

# Read one KEY=value pair from .env without executing the file.
env_get() {
    local key="$1" default="${2-}" line value
    line="$(grep -E "^[[:space:]]*${key}=" "$ENV_FILE" 2>/dev/null | tail -n 1 || true)"
    value="${line#*=}"
    value="${value%$'\r'}"

    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"

    if [ "${value:0:1}" = '"' ] && [ "${value: -1}" = '"' ]; then
        value="${value:1:-1}"
    elif [ "${value:0:1}" = "'" ] && [ "${value: -1}" = "'" ]; then
        value="${value:1:-1}"
    else
        value="${value%%[[:space:]]#*}"
        value="${value%"${value##*[![:space:]]}"}"
    fi

    [ -n "$value" ] || value="$default"
    printf '%s' "$value"
}

validate_env() {
    local secret base token

    secret="$(env_get A2D_JWT_SECRET)"
    [ "${#secret}" -ge 32 ] || die \
        "A2D_JWT_SECRET must be at least 32 characters; generate one with: openssl rand -hex 32"
    case "$secret" in
        change-me*|changeme*|*change-me*) die "A2D_JWT_SECRET still contains a placeholder" ;;
    esac

    base="$(env_get A2D_PUBLIC_BASE_URL)"
    [ -n "$base" ] || die "A2D_PUBLIC_BASE_URL is required"
    case "$base" in
        https://*|http://localhost*|http://127.0.0.1*) ;;
        *) die "A2D_PUBLIC_BASE_URL must be https://... (http is allowed only for localhost)" ;;
    esac
    case "$base" in
        */) die "A2D_PUBLIC_BASE_URL must not end with /" ;;
    esac
    [ "$base" != "https://a2d.example.com" ] || die \
        "A2D_PUBLIC_BASE_URL still uses the example hostname; set your tunnel hostname"

    token="$(env_get CLOUDFLARE_TUNNEL_TOKEN)"
    [ -n "$token" ] || die "CLOUDFLARE_TUNNEL_TOKEN is required"
    case "$token" in
        change-me*|*placeholder*) die "CLOUDFLARE_TUNNEL_TOKEN still contains a placeholder" ;;
    esac
}

require_backend() {
    [ -f "$A2D_APP_HOME/pyproject.toml" ] || die \
        "backend not found at $A2D_APP_HOME/pyproject.toml; scaffold a2d-cloud/ (P0) before deploying"
    [ -f "$A2D_APP_HOME/app/main.py" ] || warn "backend app/main.py missing at $A2D_APP_HOME"
}

ensure_system_user() {
    if ! id "$A2D_RUN_USER" >/dev/null 2>&1; then
        log "creating system user $A2D_RUN_USER"
        useradd --system --gid "$A2D_RUN_GROUP" \
            --home "$A2D_APP_HOME" --shell /usr/sbin/nologin "$A2D_RUN_USER" 2>/dev/null || \
        useradd --system --gid "$A2D_RUN_GROUP" \
            --home "$A2D_APP_HOME" --shell /usr/sbin/nologin "$A2D_RUN_USER" || \
        die "could not create system user $A2D_RUN_USER"
    fi
}

prepare_data_dir() {
    mkdir -p "$A2D_DATA_DIR/users" "$A2D_DATA_DIR/tmp" "$A2D_DATA_DIR/backups"
    chown -R "$A2D_RUN_USER:$A2D_RUN_GROUP" "$A2D_DATA_DIR"
    chmod 700 "$A2D_DATA_DIR"
    log "data directory: $A2D_DATA_DIR (owner $A2D_RUN_USER)"
}

install_python_deps() {
    need_cmd python3
    if [ ! -x "$A2D_VENV_DIR/bin/python" ]; then
        log "creating virtualenv at $A2D_VENV_DIR"
        python3 -m venv "$A2D_VENV_DIR"
    fi
    log "installing backend into venv"
    "$A2D_VENV_DIR/bin/python" -m pip install --quiet --upgrade pip
    "$A2D_VENV_DIR/bin/python" -m pip install --quiet -e "$A2D_APP_HOME"
    chown -R "$A2D_RUN_USER:$A2D_RUN_GROUP" "$A2D_VENV_DIR"
}

# Write the supervisord drop-in. TUNNEL_TOKEN is inlined because supervisord
# has no env-file support; the file is chmod 0600 so only root can read it.
write_supervisor_conf() {
    local token
    token="$(env_get CLOUDFLARE_TUNNEL_TOKEN)"

    mkdir -p "$(dirname "$A2D_SUPERVISOR_CONF")"
    cat > "$A2D_SUPERVISOR_CONF" <<EOF
[program:a2d-app]
command=$A2D_VENV_DIR/bin/uvicorn app.main:app --host $A2D_BIND_HOST --port $A2D_BIND_PORT --proxy-headers --forwarded-allow-ips=*
directory=$A2D_APP_HOME
user=$A2D_RUN_USER
environment=PYTHONUNBUFFERED="1"
autostart=true
autorestart=true
startsecs=5
stopwaitsecs=30
stopsignal=TERM
stdout_logfile=$A2D_LOG_DIR/app.log
stdout_logfile_maxbytes=10MB
stdout_logfile_backups=5
stderr_logfile=$A2D_LOG_DIR/app.err.log
stderr_logfile_maxbytes=10MB
stderr_logfile_backups=5

[program:a2d-cloudflared]
command=/usr/local/bin/cloudflared tunnel --no-autoupdate run
directory=$A2D_APP_HOME
user=$A2D_RUN_USER
environment=TUNNEL_TOKEN="$token"
autostart=true
autorestart=true
startsecs=5
stopwaitsecs=10
stopsignal=TERM
stdout_logfile=$A2D_LOG_DIR/cloudflared.log
stdout_logfile_maxbytes=10MB
stdout_logfile_backups=5
stderr_logfile=$A2D_LOG_DIR/cloudflared.err.log
stderr_logfile_maxbytes=10MB
stderr_logfile_backups=5
EOF

    chmod 600 "$A2D_SUPERVISOR_CONF"
    log "wrote supervisord drop-in: $A2D_SUPERVISOR_CONF"
}

wait_for_health() {
    local url="http://$A2D_BIND_HOST:$A2D_BIND_PORT/healthz" i
    for i in $(seq 1 60); do
        if curl -fsS "$url" >/dev/null 2>&1; then
            log "app is healthy at $url"
            return 0
        fi
        sleep 2
    done
    die "timed out waiting for $url"
}

install_env_file() {
    local src="$ENV_FILE" dst="$A2D_APP_HOME/.env"
    if [ "$src" != "$dst" ]; then
        install -m 600 "$src" "$dst"
        chown "$A2D_RUN_USER:$A2D_RUN_GROUP" "$dst"
        log "installed env file to $dst"
    fi
}

confirm() {
    local prompt="$1" reply
    if [ "${A2D_ASSUME_YES:-0}" = "1" ]; then
        return 0
    fi
    printf '%s [y/N] ' "$prompt"
    read -r reply || return 1
    case "$reply" in
        y|Y|yes|YES) return 0 ;;
        *) return 1 ;;
    esac
}