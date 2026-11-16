# ADR 0002 — Cloud Sync Architecture and Ingress

- Status: Accepted
- Date: 2026-11-16
- Owner: project maintainer
- Supersedes: none
- Superseded by: none

## Context

A2D is an offline-first Android app whose Room database (`tasks`, `action_items`) is the only copy of
the user's data. The product requirement is a private cloud replica that:

- uploads the Android dataset's changes on login and keeps server-side, downloadable archives;
- lets the user browse tasks in a browser and import/export tasks as JSON/CSV;
- pushes tasks authored or imported in the cloud back to the app, and Android edits up to the cloud;
- preserves full offline use of the app;
- gives every registered account its own directory containing its authoritative task database and
  related metadata, so one account's data is structurally separate from another's.

Constraints from the current code (`8d3b7f4`): `Long` primary keys allocated as `id*10+n` for
activities, device-local `"yyyy-MM-dd HH:mm:ss"` timestamps, no `updatedAt`, no deletion tombstones,
`exportSchema = false`, no authentication, no `INTERNET` permission, and no network stack. The
original blueprint (`docs/a2d_graph.yaml`) already described a Python/FastAPI task service, but with
flat-file storage (`task.json`, `users.json`), which is not a viable replica.

The full behavioural specification is in
[FEAT-0002](../features/FEAT-0002-cloud-sync.md).

## Decision

1. **The cloud stores a logical replica, not the SQLite file.** Import/export and archives use one
   versioned canonical JSON document (`a2d.tasks`, `formatVersion = 1`, published as JSON Schema);
   sync copies the same rows in a flat, per-table frame. Uploading the raw `.db` is not supported.
   Archives are generated from the replica and restored offline through the CLI.
2. **External identity is a client-generated UUID (`syncId`)** on tasks and activities; the Room
   `Long id` remains a local-only primary key. Sync, import, and export all key on `syncId`.
3. **Cloud sync is two watermark-driven table-copy halves: backup and download.** Backup copies
   dirty rows from each Room table (`tasks` first, then `action_items`) and merges per row by
   last-write-wins on `(updatedAt, deviceId)` with a five-minute future-clock clamp; accepted rows are
   compare-and-cleared and rejections return the authoritative server row. Clamping is authoritative:
   the accepted entry returns the stored version and the client adopts it, so a future-dated row is
   neither re-sent forever nor able to beat the user's later edits. Download copies rows from
   each table whose monotonic per-user `server_updated_at` is after the opaque `lastDownloadAt` token
   (a keyset of `server_updated_at` + table rank + row `syncId`, ordered so a parent task precedes its
   activities); `full=true` requests a full scan. UUID `syncId` is preserved; local numeric IDs are
   never transmitted. Deletes are tombstones retained server-side for 90 days; the client purges
   confirmed tombstones after 30. An expired token returns `410` and forces a full download, which
   reconciles absence as deletion. Exactly two checkpoints exist (`lastBackupAt`, `lastDownloadAt`);
   `dirty` is per-row state and `server_updated_at` is a per-row column, not extra cursors. There is no
   `change_log`, sequence cursor, or full-snapshot request.
4. **Backend: FastAPI + SQLAlchemy 2.x.** A small **control database** (SQLite WAL by default,
   Postgres optional via `A2D_DATABASE_URL`) holds only identity, devices, refresh tokens, archive
   metadata, storage quota, and a schema-version registry (the tenant's `user_state` remains
   authoritative). Passwords are Argon2id; sessions use short-lived
   access JWTs plus rotating, hashed refresh tokens per device. Accounts are invite-only
   (`app.cli create-user`). OIDC is not used in v1.
5. **Ingress is Cloudflare Tunnel on the existing Cloudflare account.** A `cloudflared` process
   makes outbound-only connections; Cloudflare terminates TLS and applies edge WAF/rate limiting.
   No origin web server (Caddy/nginx) and no inbound ports are used. Cloudflare Access is optional
   and, if enabled, is scoped to web UI paths only; `/v1/*` stays outside Access so the Android
   client receives JSON errors rather than an HTML login redirect.
6. **Web UI: FastAPI-served Jinja2 + HTMX**, same origin, secure session cookie + CSRF. Version 1 is
   **read-only for tasks** and **ships cloud JSON/CSV import and export** as the only web write
   paths. Direct web editing reuses the existing CRUD endpoints in 1.1.
7. **Archives are server-generated gzip canonical JSON** written under the owning account's folder
   (`/data/users/<userId>/archives/…`), with `kind` (`daily`/`manual`/`pre_restore`), SHA-256, size,
   and registry metadata in the control database. The daily job is skipped per account when that
   account's dataset hash is unchanged. Retention: 30 daily + 12 monthly + all manual. Restore is
   CLI-only, takes the tenant lock, and requires the app to be stopped. The app never uploads a
   snapshot.
8. **Android:** one Room v2 migration that recreates `tasks`/`action_items` to add `syncId`,
   `updatedAt`, `deletedAt`, `dirty` (schema exported), a transactional `SyncEngine` that runs backup
   then download, Retrofit/OkHttp, tokens in Keystore-backed storage, and WorkManager triggers on
   login, app start, every 12 hours, and manual "Sync now". Gate state is never serialised; it is
   recomputed on-device after applying changes.
9. **Tenant storage is one directory and one database file per account.** Runtime state is rooted at
   `A2D_DATA_DIR` — the host path (e.g. `$A2D_APP_HOME/data` or `/var/lib/a2d-cloud/data`) — and is
   never committed. Registration creates `/data/users/<userId>/` (mode `0700`, server-generated UUID name)
   containing the authoritative `tasks.db`, its WAL files, a `.lock`, `meta.json`, and `archives/`.
   The tenant file is the isolation boundary: task tables carry no `user_id`, and a request opens
   exactly one tenant from the token. State that affects convergence — `user_state.last_server_stamp`,
   `schema_version`, and idempotency responses — lives in that database. `meta.json` is derived ops
   metadata, never read for decisions. The control database remains the only place that resolves
   accounts.
10. **Row-count parity is the final sync check.** The last download page reports live and deleted
    counts per syncable table (derived with `COUNT(*)`, not stored, and read in the same snapshot as
    that page's rows). The client compares live counts against Room after a completed run. A mismatch
    with no pending dirty rows fails the run as `RowCountMismatch` and schedules a full download; it
    never deletes data. Tombstones are excluded from parity because client and server retention differ
    (30 vs 90 days).

## Consequences

Positive:

- Archives and the web replica are readable data, not an opaque binary; they survive schema and
  platform changes and are diffable/validatable.
- UUID identity removes cross-device ID collisions and makes import/export idempotent.
- Table-to-table row copy matches the owner's mental model: each table can be reasoned about and
  verified independently, and the final live row count is a cheap end-to-end check.
- Incremental backup and keyset download keep the login path small: a second run with no edits
  transfers nothing, instead of re-uploading the whole dataset or scanning an append-only log.
- Cloudflare Tunnel removes certificate management, port forwarding, and dynamic-DNS work from the
  host, and adds edge TLS/WAF without extra infrastructure.
- The app stays offline-first; a cloud outage only delays sync.
- Per-account folders make tenant isolation structural rather than predicate-based: task tables have
  no `user_id`, and backup, restore, export, quota, and deletion are folder-local operations. One
  corrupt or oversized account cannot block another's queries.

Negative / trade-offs:

- Last-write-wins can discard a genuinely concurrent edit; the losing row is reported, but not merged
  field-by-field.
- Row-level merge can accept a task row and an activity row from different devices; gate recomputation
  after apply keeps `achieved` consistent, and a task tombstone still removes the whole task.
- Row-count parity is a diagnostic, not a transaction boundary: a local edit during a run can make the
  counts diverge until the next run, and a mismatch triggers a full download rather than an automatic
  repair.
- The upload watermark is client time. Correctness therefore relies on the row-level `dirty` flag
  rather than a pure `updatedAt > lastBackupAt` query, and a badly skewed client clock can still win
  an LWW conflict (bounded by the five-minute clamp). Download is immune because it uses server time.
- Without a `change_log`, an expired or restore-invalidated download token forces a full rescan.
  This is cheap at personal-dataset scale and avoids a second table plus its retention job.
- The server operator can read task content (no E2EE in v1). This is acceptable for a private,
  self-hosted deployment but must be documented.
- Cloudflare becomes a hard dependency for remote access, with a ~100 s origin timeout and no
  supported direct LAN path in v1.
- Task CRUD endpoints exist before the UI exposes editing, so they need tests and authorization
  coverage from day one.
- A Room schema change requires a migration test path; the suite is currently JVM-only, so
  Robolectric is added rather than `androidTest`.
- Maintaining the canonical JSON Schema in two places needs a CI hash check.
- Per-tenant files require a migration runner, an LRU engine cache, file-descriptor/disk monitoring,
  and a strict path-validation layer; the default deployment stays single-worker.
- Backups are now a two-part unit (control DB plus every tenant snapshot), and restore/delete tooling
  must be tenant-aware; archives alone cannot restore logins or device sessions.
- Cross-account SQL reporting is not available without iterating tenants; this is acceptable for a
  private, few-account deployment.

## Alternatives considered

| Alternative | Why it was not chosen |
| --- | --- |
| Upload the raw Room `.db` and restore it | WAL/`-shm` sidecars, open handles, Android SQLite build differences, unsafe restore over a live connection, no merge, and a large arbitrary-file parsing surface |
| An append-only `change_log` with a monotonic cursor | Extra table, sequence, pruning, and cursor-expiry paths for the same result; the indexed `(server_updated_at, table_rank, syncId)` keysets already give ordered incremental reads plus tombstones |
| A single combined sync endpoint | Mixes upload and download failure modes and hides which watermark advanced; two calls make the user's "backup + download" model explicit and each half independently retryable |
| Server-authoritative online-only database | Breaks the offline-first guarantee and the existing Room architecture |
| Google Drive / Dropbox app-data backup instead of a server | No browser UI, no web import/export, no cross-device merge semantics under our control |
| Caddy or nginx origin with a public port | Requires inbound ports, certificates, and firewall/DNS work on a home host; redundant with the existing Cloudflare account |
| Cloudflare proxy in front of a public origin | Still needs origin TLS and an exposed port; the tunnel is strictly simpler |
| OIDC (Keycloak/Authentik/Google) in v1 | Adds an IdP, redirect plumbing, and Android AppAuth work for a handful of invite-only users; local accounts are self-contained |
| React/Vue SPA for the web app | Adds a Node build/toolchain and a second deployment artifact for a small CRUD UI |
| Task-aggregate sync | Keeps a task and its activities in one unit, but does not match the requested table-to-table copy, forces child changes to ride on the parent, and makes per-table row counts meaningless |
| Single database with `user_id` on every row | One missing predicate leaks or corrupts another account's data; per-user files make the boundary structural and keep delete/export/restore folder-local. Retained only as a possible future Postgres tenant backend |
| One Postgres database per user | Heavy provisioning and connection overhead for a handful of accounts; schema-per-user is the viable Postgres extension |
| `meta.json` as the authoritative store for sync state | Read-modify-write races between requests, no atomicity with task writes, and a crash can leave the stamp ahead of or behind the rows; tables give transactional updates |
| Keep one shared task DB and only give each user an archive folder | Does not meet the requirement that each registered user has their own database file, and leaves row-level scoping as the isolation mechanism |
| E2EE in v1 | Conflicts with server-rendered browser editing unless key management is redesigned; deferred with the privacy trade-off documented |

## Follow-up work

- Implement FEAT-0002 phases P0–P5 (see the feature record §15).
- Build the tenant provisioning, migration, quota, purge, `ops-backup`, and `restore-backup` CLIs before
  exposing sync endpoints. A full backup restore must re-stamp restored rows and tombstone rows absent
  from the backup so clients with newer download tokens converge.
- Namespace `SerialAndGateProcessor` pins by task `syncId` (current arity-keyed namespace is shared);
  serialise gate recompute during sync apply until then.
- Add a `RowCountMismatch` diagnostic to the sync status and make the next run force `full=true`.
- Add Robolectric and the Room 1→2 migration test before shipping the schema change.
- Publish `a2d-cloud/schema/a2d-tasks-v1.schema.json` and wire the CI hash check with Android fixtures.
- Maintain the deployment assets in `Deployment/a2d-cloud/` (`supervisord.conf.example`, `.env.example`,
  `lib.sh`, the deploy/invite/backup/restore scripts, and the cron example) with the tunnel setup,
  invite, archive/restore, retention, and Access scoping runbook.
- Revisit E2EE and direct web editing in 1.1; evaluate Access service tokens for the app only if the
  API also needs edge authentication.

## References

- Feature record: [FEAT-0002 — Cloud Sync, Web Replica & Task Import/Export](../features/FEAT-0002-cloud-sync.md)
- Current Android stack: [ADR 0001](0001-technology-stack.md), [README §3](../../README.md#3-technology-stack)
- Original server blueprint: [docs/a2d_graph.yaml](../a2d_graph.yaml)
- Code baseline: `8d3b7f4` (Android), documentation HEAD `1390938`
