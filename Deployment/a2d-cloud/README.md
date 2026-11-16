# A2D cloud deployment runbook
# Run from: sudo ./deploy.sh (first install) or sudo ./deploy.sh (updates).
# The only public entry point is the Cloudflare Tunnel hostname. No inbound
# ports are published; the host does not need a TLS certificate, reverse proxy,
# or dynamic DNS.
# =============================================================================

## 1. Prerequisites

- Debian/Ubuntu host (this repo verified on trixie). Also should work on
  any distro with apt-get supervisor python3 python3-venv curl.
- Cloudflare account with a Zero Trust tunnel already configured. See step 4.
- 2 GB RAM minimum, a few GB of disk for tenant data, backups, and logs.
- Outbound HTTPS/443 allowed. Inbound 80/443 is not required and should stay closed.
- curl, supervisor, and cloudflared installed by deploy.sh.

The deployment runs **without Docker**. The FastAPI/uvicorn app and the
cloudflared tunnel are managed as native processes by supervisord.

## 2. First install

```bash
cd Deployment/a2d-cloud
cp .env.example .env
chmod 600 .env
${EDITOR:-vi} .env          # fill A2D_JWT_SECRET, A2D_PUBLIC_BASE_URL, CLOUDFLARE_TUNNEL_TOKEN
sudo ./deploy.sh
```

`deploy.sh` is idempotent:
1. validates .env, checks the backend source,
2. creates a system user `a2dcloud` (uid/gid 10001 if you want it),
3. creates a Python venv and installs the a2d-cloud package in it,
4. prepares the data directory `$A2D_APP_HOME/data` (owned by a2dcloud),
5. runs tenant migrations as the service user,
6. writes a supervisord drop-in at `/etc/supervisor/conf.d/a2d-cloud.conf`
   that runs two programs: `a2d-app` (uvicorn, binds to
   A2D_BIND_HOST:A2D_BIND_PORT) and `a2d-cloudflared` (tunnel out to Cloudflare),
7. reloads supervisord and waits for the app health check before reporting success.

The app **listens on 127.0.0.1:8000 only** — cloudflared connects to this
address from the outside through Cloudflare. No ports are published on the host.

Verify through the tunnel:

```bash
curl -fsS https://a2d.example.com/healthz
curl -fsS https://a2d.example.com/readyz
```

- `/healthz` is process liveness.
- `/readyz` checks the control DB, `/data` writability, and tenant-root readability.
- Neither exposes tenant content.

## 3. Cloudflare Tunnel setup (one time, in the existing account)

1. Add the domain to Cloudflare and create a tunnel under **Zero Trust → Networks → Tunnels**.
2. Add a public hostname:
   - Hostname: `a2d.example.com`
   - Service: `http://127.0.0.1:8000`
3. Copy the tunnel token into `.env` as `CLOUDFLARE_TUNNEL_TOKEN`.
4. Optional: add a Cloudflare Access application for the **web UI paths only**:
   `/`, `/tasks*`, `/import`, `/export`, `/archives*`, `/settings*`, `/login*`.
   Leave `/v1/*` outside Access. The Android client sends
   `Authorization: Bearer`; an Access HTML redirect would break sync.
5. Add WAF/rate-limit rules for `/v1/auth/login` and keep caching bypassed
   for `/v1/*` and HTML.
6. Keep the origin firewall closed to inbound traffic. TLS terminates at the
   Cloudflare edge. No host certificate, reverse proxy, or dynamic DNS is needed.

## 4. Invite a user

Registration is disabled by default (`A2D_ALLOW_REGISTRATION=false`). Create accounts explicitly:

```bash
sudo ./invite.sh user@example.com
```

The backend CLI creates the control rows and `$A2D_APP_HOME/data/users/<userId>/tasks.db`
at the current tenant schema. Ask the user to open the Android app, enter the
public server URL, and sign in.

## 5. Updates

```bash
cd Deployment/a2d-cloud
sudo ./deploy.sh
```

The script is idempotent — it validates, rebuilds the venv, applies migrations,
writes the supervisord drop-in, and restarts the service. Tenant migrations are
forward-only and never edited after release.

Useful manual commands:

```bash
# Tail logs (structured JSON; never task text).
sudo supervisorctl tail a2d-app stderr  # some versions
sudo tail -f /var/log/a2d-cloud/app.log

# Show app state and health.
sudo supervisorctl status a2d-app a2d-cloudflared

# Apply tenant migrations only.
sudo ./as_run_user -m app.cli migrate-users   # this helper is in lib.sh

# Manually archive a tenant.
sudo ./as_run_user -m app.cli archive
```

## 6. Backups

```bash
sudo ./backup.sh                    # keep A2D_BACKUP_KEEP (default 14)
sudo ./backup.sh --keep 30          # one-off retention override
```

The backend CLI invokes `app.cli ops-backup`, which does the heavy lifting:
- consistent `VACUUM INTO` snapshots of the control DB and every tenant `tasks.db`;
- copies of tenant archives and `meta.json`;
- a `manifest.json` with sizes and SHA-256 hashes;
- retention via `A2D_BACKUP_KEEP`.

Backups are safe to run while the app is live. Owned by the service user and
may need `sudo` to inspect:

```bash
sudo ls -la /var/lib/a2d-cloud/data/backups
sudo cat /var/lib/a2d-cloud/data/backups/<backupId>/manifest.json
```

### Schedule

Copy the entries from `a2d-cloud.cron.example` into the deployment user's
crontab, replacing `/srv/a2d_android` with this checkout:

- nightly at 03:15 UTC: `./backup.sh` (with flock guard);
- nightly at 03:45 UTC: `.venv/bin/python -m app.cli archive --all` (hash-gated);
- nightly at 04:15 UTC: `.venv/bin/python -m app.cli prune`.

## 7. Restore

Both restore paths stop the app first, run the CLI under the tenant lock, and
restart it.

### Restore one tenant archive

```bash
sudo ./restore.sh archive <archiveId>
```

The backend rewrites that tenant's replica, advances `server_updated_at` on
every restored row, and writes tombstones for rows that disappeared, so devices
converge on the next download. No other tenant is touched.

### Restore a full recovery set

```bash
sudo ./restore.sh backup <backupId>
```

This is disaster recovery. It:
1. verifies the manifest and every SHA-256;
2. moves the current live state to `$A2D_DATA_DIR/pre-restore-<timestamp>/`;
3. restores the control DB, tenant DBs, archives, and `meta.json`;
4. re-stamps every restored row and tombstones rows that existed before the
   restore but are absent from the backup, so clients re-download the restored
   state;
5. restarts the app and waits for health.

If verification fails, nothing is moved. If the restore fails after mutation,
the script restarts the app and leaves the previous state under
`/data/pre-restore-*` for manual recovery.

## 8. Rollback

Code rollback:

```bash
git checkout <previous-tag-or-commit>
sudo ./deploy.sh
```

Data rollback: `sudo ./restore.sh backup <backupId>`.

## 9. Security notes

- The app listens on 127.0.0.1:8000 only; Cloudflare Tunnel is the sole
  entry point. No inbound ports are published on the host.
- v1 has **no end-to-end encryption**. The operator can read task content;
  state this to users.
- `.env` contains the JWT secret and tunnel token. Keep it mode `0600`; never
  commit it. The deploy script copies it to `$A2D_APP_HOME/.env` (mode 0600).
- Tenant folders are `0700` and named by server-generated UUIDs. The app resolves
  the tenant from the access token, never from a request body or path.
- Cloudflare Access is optional and **web-only**. Do not put `/v1/*` behind
  Access without service tokens.
- The origin should deny all inbound traffic; only outbound tunnel connections
  are needed.
- No third-party analytics or CDN assets are loaded by the web UI.
- Recommended: enable volume-level encryption on the host and restrict SSH
  access.

## 10. Troubleshooting

| Symptom | Check |
| --- | --- |
| `deploy.sh` says backend not found | `$A2D_APP_HOME/pyproject.toml` does not exist yet; scaffold P0 first. |
| supervisord not running | `systemctl status supervisor` or `supervisord -c /etc/supervisor/supervisord.conf` |
| `a2d-app` unhealthy / curl returns 502 | `sudo tail -n 20 /var/log/a2d-cloud/app.log`; check .env and data dir ownership. |
| tunnel not connecting | `sudo tail -n 20 /var/log/a2d-cloud/cloudflared.log`; verify CLOUDFLARE_TUNNEL_TOKEN and that the tunnel exists in the CF dashboard. |
| 524 from Cloudflare | A sync/import call exceeded the ~100 s edge timeout; check batch size and server logs. |
| Disk full | Prune old backups (`A2D_BACKUP_KEEP`), old archives, and check tenant quota usage. |
| Login works, sync gets HTML | Cloudflare Access is protecting `/v1/*`; scope Access to web paths only. |

Health endpoints:

```bash
curl -fsS https://a2d.example.com/healthz
curl -fsS https://a2d.example.com/readyz
```

## 11. Command reference

| Command | Purpose |
| --- | --- |
| `sudo ./deploy.sh` | Install or update; validate, build venv, migrate, start, health-check. |
| `sudo ./invite.sh <email>` | Create one invited account and tenant folder. |
| `sudo ./backup.sh [--keep N]` | Write a checksummed recovery set under `$A2D_APP_HOME/data/backups`. |
| `sudo ./restore.sh archive <id>` | Restore one tenant archive. |
| `sudo ./restore.sh backup <id>` | Full disaster restore from a recovery set. |
| `sudo supervisorctl status a2d-app a2d-cloudflared` | Show program status. |
| `sudo supervisorctl tail a2d-app stderr` | Follow stderr output (some versions). |
| `sudo tail -f /var/log/a2d-cloud/app.log` | Follow structured app logs. |
| `sudo ./as_run_user -m app.cli migrate-users` | Apply tenant migrations manually. |
| `sudo ./as_run_user -m app.cli archive` | Manually archive the current tenant dataset. |
| `sudo ./as_run_user -m app.cli prune` | Run retention jobs manually. |

## 12. Optional: systemd for supervisord

On Debian/Ubuntu, `apt-get install supervisor` also creates a systemd unit
at `/etc/systemd/system/supervisord.service`. Enable and start:

```bash
systemctl enable --now supervisor
```

The drop-in at `/etc/supervisor/conf.d/a2d-cloud.conf` is read automatically.
Disable with `systemctl disable --now supervisor` (restores the supervisord
processes to manual start/stop via `supervisorctl`).