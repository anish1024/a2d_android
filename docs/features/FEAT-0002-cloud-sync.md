# FEAT-0002 — Cloud Sync, Web Replica & Task Import/Export

- Status: Implementing (design frozen 2026-11-16)
- Owner: project maintainer
- Last updated: 2026-11-16
- Related: [README §4](../../README.md#4-component-mapping--domain-implementation),
  [README §6](../../README.md#6-implementation-status), [ADR 0001](../adr/0001-technology-stack.md),
  [FEAT-0001](FEAT-0001-task-card-redesign.md), [original FastAPI blueprint](../a2d_graph.yaml)
- P2: Android Room v2 migration implemented (syncId UUID, `updatedAt`, `deleted`, `dirty` columns; sync metadata backfilled, `dirty=1`, exported schema at `schemas/2.json`); Robolectric test written (native ARM64 lib unavailable in this env).
- Target version: 1.1.0 (cloud MVP)
- Decision: [ADR 0002 — Cloud sync architecture](../adr/0002-cloud-sync-architecture.md) (Accepted)

> **Approved 2026-11-16.** Defaults accepted with two changes: ingress is an existing Cloudflare
> account (Cloudflare Tunnel, replacing Caddy), and **cloud import/export is required in v1**.
> Implementation has not started; P0 scaffolding is the next step.

## 1. Problem

Today A2D is a single-device, offline Android app. The Room database is the only copy of the user's
tasks; there is no backup, no way to view tasks from a browser, and no way to author or move tasks
between devices. The app has no authentication, no `INTERNET` permission, and no network stack.

The original blueprint (`docs/a2d_graph.yaml`) already specified a Python/FastAPI service with
`/tasks` CRUD and a `users.json` login; this proposal revives that direction as a **private cloud
replica** rather than a second, disconnected task store.

## 2. Goals and success criteria

1. **Backup (upload half of sync)** — on login and on a schedule the app copies changed rows from
   each Room table to the matching cloud table, tracking its `lastBackupAt` watermark. The call is
   incremental; it never re-uploads the whole database and never uploads the raw SQLite file.
2. **Download (pull half of sync)** — the app asks the API for rows changed since its
   `lastDownloadAt` watermark, applies each page transactionally, and repeats until caught up.
3. **Replica** — the server holds a normalised, per-user replica of the Android dataset that can be
   viewed in a browser; in v1 the only web-side writes are validated import/export operations
   (direct web editing is deferred to 1.1).
4. **Two-way sync** — tasks imported (or, from 1.1, edited) on the web reach the Android app on the
   next download, and Android edits reach the server on the next backup. No manual copy/paste.
5. **Import/export** — a versioned JSON document and a flat CSV can be exported from and imported
   into the cloud replica, with validation, dry-run, and conflict policy. **Required in v1.**
6. **Disaster recovery** — the server keeps periodic, downloadable gzip **archives** of the replica
   (server-generated; the app never uploads a snapshot). Restore is an operator action.
7. **Offline-first preserved** — the app remains fully usable with no network; sync failures never
   block task operations.
8. **Per-account isolation** — every registered account gets its own directory and authoritative
   `tasks.db`; the control database holds only identity, device, token, archive, and quota metadata.

Success criteria:

- Two Android devices signed into one account converge to the same task set after sync.
- A reinstall or second device reconstructs the complete dataset from the cloud replica on first sync.
- A task imported in the browser appears on the phone without user action beyond a normal sync.
- Cloud JSON/CSV import and export round-trip without loss on the `a2d.tasks` v1 contract.
- `GET /v1/export?format=json` on the server is accepted by the phone's import path unchanged.
- A restored archive reproduces the exact task/activity set (verified by SHA-256 of canonical JSON).
- Two consecutive syncs with no local edits upload zero rows and download zero rows.
- After a completed sync with no pending local edits, the live row count of every table matches
  between Room and the cloud replica.
- A device whose `lastDownloadAt` is older than the retention window recovers with a full download
  and loses nothing it has locally; local dirty edits are pushed afterwards.
- Every account owns an isolated `/data/users/<userId>/` directory containing its own `tasks.db`;
  no request can read or write another account's file, and deleting an account removes only that
  directory.
- No task content appears in server logs; one user cannot read another user's rows.
- The service is reachable only through the configured Cloudflare hostname; no inbound port is open
  on the origin host.

## 3. Non-goals

- Real-time collaboration, WebSockets, or live cursors.
- Multi-tenant SaaS, billing, public sign-up, or social features.
- End-to-end encryption in v1 (the server operator can read task content; see §13).
- Syncing in-memory gate state (`SerialAndGateProcessor` pins/latches) — gate output is recomputed
  on the device and never persisted server-side.
- Attachments, images, or file uploads per task.
- Replacing Room with a server-authoritative online database.
- Byte-for-byte SQLite file replication (explicitly rejected; see §5 D1).

## 4. Current state and constraints

Facts taken from the code (`8d3b7f4`, docs HEAD `1390938`):

| Area | Current reality | Consequence for this design |
| --- | --- | --- |
| Schema | `tasks(id Long PK, taskName, description, priority, context, achieved, creationTime String)`; `action_items(id Long PK, taskId FK CASCADE, description, achieved)` | No stable cross-device identity, no `updatedAt`, no deletion tombstone |
| IDs | `TaskViewModel` allocates `newId = maxOf(maxExistingId, taskId*10)+1`; activities `id*10+1…` | Imported rows can collide; sync needs a separate external ID |
| Times | `creationTime` is `"yyyy-MM-dd HH:mm:ss"`, device-local, no zone | Contract must normalise to UTC ISO-8601 |
| Room | `version = 1`, `exportSchema = false` | Requires v2 migration + schema export + migration test |
| Auth | `User`/`UserGroup` stubs; Android login UI with email input + "Continue with email" button added; token storage/session deferred to P2 | Android needs login, token storage, and identity |
| Network | No `INTERNET` permission; no HTTP client, no WorkManager | New dependencies and a background worker |
| Domain | `Task.achieved` is gate output; `Task.isCompleted` = `activities.isNotEmpty() && all(achieved)` | Server must not invent a third completion rule |
| Gate | Pins are keyed by arity string on a shared `@Singleton` processor, so tasks with equal arity share a bucket | Sync must treat gate state as device-local and never serialise it |
| Tests | 11 JVM tests; no `androidTest` | Android sync/migration tests should be JVM (Robolectric) to match the suite |

## 5. Key decisions

| # | Decision | Choice | Why |
| --- | --- | --- | --- |
| D1 | What is uploaded by "backup"? | **Dirty rows copied table to table** as logical row data, not the raw `.db` and not a full snapshot | WAL/`-shm` companions, open handles, Android SQLite build differences, no merge, unsafe restore over a live Room connection, arbitrary-file parsing risk. Incremental row copy keeps the login call small and makes retries cheap |
| D2 | External identity | **Adopt separate `syncId` (UUIDv4) on tasks and activities**; Room `Long id` stays local-only PK | User-approved: avoids `id*10+n` collisions across devices and cloud imports; UUID syncs across tables, cloud, and import/export while local numeric IDs remain device-specific |
| D3 | Sync unit | **One row**, grouped by table; `tasks` copied before `action_items` | Table-to-table copy is the mental model the owner asked for; dependency order keeps the FK valid and one transaction keeps a request atomic |
| D4 | Conflict resolution | **Last-write-wins per row**, keyed by `(updatedAt, deviceId)`; losing row reported with the authoritative server row | Simple, explainable, adequate for personal task lists; gate recomputation after apply keeps `achieved` consistent when task and activity rows come from different devices |
| D5 | Deletes | **Soft delete + tombstone** with `deletedAt`; server retains 90 days, client purges confirmed tombstones after 30. A `lastDownloadAt` older than the server window forces a full download, where absence means deleted | Hard deletes cannot propagate to another device; a shorter server retention would let an offline device resurrect a deleted task |
| D6 | Transport | HTTPS + JSON, `Bearer` JWT for the app, secure session cookie for the web | Standard, debuggable, works offline-first |
| D7 | Server storage | **Control plane** SQLite (WAL) by default; each account's task data lives in its **own SQLite file** under `/data/users/<userId>/tasks.db`. `A2D_DATABASE_URL` may point the control plane at Postgres; tenant storage stays file-based in v1 | A small private host does not need a server; per-user files give structural tenant isolation, simple backup/delete, and per-user quotas |
| D8 | Web app | **FastAPI + Jinja2 + HTMX**, one deployable | No Node toolchain; matches "FastAPI serves the webapp"; easy self-hosting |
| D9 | Sync cadence | **Backup + download on login, app start, every 12 h, and manual "Sync now"**; a run with no dirty rows and an up-to-date download token is a no-op | Meets "upload on every login" without unbounded growth or empty round-trips |
| D10 | Import formats | JSON (canonical) + CSV (flattened) with dry-run and conflict policy | Browser-friendly and scriptable |
| D11 | Ingress / TLS | Existing **Cloudflare** account via **Cloudflare Tunnel** (`cloudflared`); **no Caddy, no open inbound ports** | Cloudflare terminates TLS and provides edge WAF/rate limiting; the origin only makes outbound connections, so a home/VPS host needs no certificate or firewall work. Caddy is removed from the design |
| D12 | Web write scope in v1 | Read-only task browsing + **cloud import/export enabled** | Matches the request; import/export is the only v1 write path and runs through validation + dry-run |
| D13 | Sync watermarks | **Only two checkpoints:** `lastBackupAt` (client clock) for upload and `lastDownloadAt` (opaque server-issued feed token) for download | The upload query compares Room's local `updated_at`, so its watermark must be client time; the download query compares server-assigned `server_updated_at`, which the client cannot predict, so the server issues the token. Keeping exactly two positions is the simplest model that survives retries |
| D14 | Where "changed since" comes from | Every syncable row carries `server_updated_at`; any content-changing backup, import, web edit, or restore advances the rows it touched. Download is a union keyset scan on `(server_updated_at, table_rank, syncId)`, so no `change_log` is needed | One indexed column per table replaces an append-only log, a cursor table, and their pruning; per-row stamps make table-to-table copy exact |
| D15 | Multi-user storage layout | **One directory per account**: `/data/users/<userId>/` holds the authoritative `tasks.db`, its WAL files, `meta.json`, a lock file, idempotency responses, and `archives/`. A small control database holds only identity, devices, tokens, quota, and archive metadata | Matches the "each registered user gets a folder" requirement; the tenant file is the isolation boundary, so no query can join or leak two accounts, and backup/export/delete are folder-local operations. Cost: migrations and connection management iterate tenants |
| D16 | End-of-sync verification | **Live row counts per table** (excluding tombstones) must match between Room and the cloud; the final page counts are read in the same snapshot as its rows | A cheap, independent check catches a dropped batch, a bad apply, or a download gap without trusting either side's watermark; tombstones are excluded because client and server retention differ |

## 6. Architecture

```
┌────────────────────┐         HTTPS/JSON         ┌──────────────────────────────┐
│  A2D Android       │  POST /v1/auth/login       │  Cloudflare edge             │
│                    │  POST /v1/sync/backup      │  · TLS termination           │
│  Room (offline)    │  GET  /v1/sync/updates     │  · WAF / rate limits         │
│  SyncEngine        │                            │  · optional Access (web UI)  │
│  WorkManager       │                            └──────────────┬───────────────┘
│  TokenStore        │                                           │ outbound-only tunnel
└────────────────────┘                                           │ (cloudflared, no open ports)
                                                                 ▼
┌────────────────────┐         HTTPS               ┌──────────────────────────────┐
│  Browser           │ ◄─────────────────────────► │  Private host (VPS/home)     │
│  task list (read)  │   via Cloudflare hostname   │                              │
│  import/export     │                             │  FastAPI (uvicorn :8000)     │
└────────────────────┘                             │  ├─ auth (Argon2id + JWT)    │
                                                   │  ├─ backup (LWW merge)       │
                                                   │  ├─ download (keyset)        │
                                                   │  ├─ tasks read API           │
                                                   │  ├─ import/export            │
                                                   │  ├─ archives (gzip JSON)     │
                                                   │  └─ web UI (Jinja2 + HTMX)   │
                                                   │                              │
                                                   │  control.db (auth/registry)  │
                                                   │  users/<id>/tasks.db         │
                                                   │  + archives/ (per user)      │
                                                   └──────────────────────────────┘
```

No process publishes a host port. `cloudflared` dials out to Cloudflare and forwards
`https://<hostname>/…` to the local uvicorn app at `http://127.0.0.1:8000`; Cloudflare terminates
client TLS and the tunnel carries origin traffic. Caddy/nginx are not used; the app is managed by
supervisord.

Data flow on login (cloud sync is these two calls, in this order):

1. `POST /v1/auth/login` → access token (15 min) + refresh token (30 days) + `deviceId`. The token
   carries `userId`; the tenant store opens `/data/users/<userId>/tasks.db` for all task work. The
   client never sends a user identifier.
2. `POST /v1/sync/backup` with the dirty rows from each Room table (`tasks`, then `action_items`).
   The server merges each row by LWW, returns accepted/rejected rows, and the client advances
   `lastBackupAt` to the pre-scan cutoff.
3. `GET /v1/sync/updates?lastDownloadAt=<token>` returns rows changed since the previous download,
   grouped by table. The client applies each page in a Room transaction (tasks before activities) and
   advances `lastDownloadAt` to the server-issued `next` token. On a first sync there is no token: the
   client sends `full=true`, the server returns every row in pages, and the client reconciles by
   absence.
4. The final page includes live row counts. With nothing pending locally, the client compares them per
   table and records `Success` or `RowCountMismatch`.
5. A run with nothing dirty and nothing new downloads no row payloads. Server-side **archives** are
   a separate scheduled/operator job; the app never uploads a snapshot.

The server is a **normalised logical replica**, not a byte copy of the phone database. Each account's
replica lives in its own `tasks.db` file; the control database stores no task content.

## 7. Canonical data contract

One versioned **task document** is shared by export and import. Sync uses a **table-row frame** over
the same rows: `tables.tasks` and `tables.action_items`, with the same `syncId` and field names as the
document's nested objects. Export/import stays nested (a task with its `activities`) because that is
the user-facing file; the sync endpoints stay flat and per-table because that is the copy model. The
server converts between the two views. Both are published from
`a2d-cloud/schema/a2d-tasks-v1.schema.json` (JSON Schema 2020-12) and copied into Android test resources
with a CI hash check so both sides validate the same definitions.

```json
{
  "format": "a2d.tasks",
  "formatVersion": 1,
  "exportedAt": "2026-11-15T08:30:00Z",
  "source": { "app": "a2d-android", "version": "1.1.0", "deviceId": "8f0e…" },
  "tasks": [
    {
      "syncId": "1f0c3d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
      "taskName": "Morning Focus",
      "description": "Start day",
      "priority": 1,
      "context": "Daily Routine",
      "achieved": false,
      "creationTime": "2026-11-15T08:00:00Z",
      "updatedAt": "2026-11-15T08:25:00Z",
      "deleted": false,
      "activities": [
        {
          "syncId": "2a1b…",
          "description": "Drink water",
          "achieved": true,
          "updatedAt": "2026-11-15T08:20:00Z"
        }
      ]
    }
  ]
}
```

Rules:

- `formatVersion` is independent of the HTTP API version; unknown major versions are rejected
  with `422`.
- Timestamps are UTC ISO-8601 with `Z`. Existing local `creationTime` values are parsed as
  device-local time once during the Room migration and converted.
- `context` defaults to `"No Context"`; `priority` is an integer (UI currently writes `1`).
- `achieved` is stored as supplied. When the server mutates activities it recomputes
  `achieved = activities.isNotEmpty() && activities.all(achieved)`, matching `Task.isCompleted`;
  an activity-less task keeps its previous flag (matching the gate's empty-task behaviour).
- Ordering in exports is deterministic: `priority ASC, taskName ASC, syncId ASC`; activities by
  `syncId`. UTF-8, no BOM, `LF`.

The sync frame copies the same data one row at a time:

```json
{
  "tables": {
    "tasks": [
      { "syncId": "1f0c…", "taskName": "Morning Focus", "description": "Start day",
        "priority": 1, "context": "Daily Routine", "achieved": false,
        "creationTime": "2026-11-15T08:00:00Z", "updatedAt": "2026-11-16T09:12:00Z",
        "deleted": false }
    ],
    "action_items": [
      { "syncId": "2a1b…", "taskSyncId": "1f0c…", "description": "Drink water",
        "achieved": true, "updatedAt": "2026-11-16T09:12:00Z", "deleted": false }
    ]
  }
}
```

`deleted: true` rows are tombstones; only `syncId` and `updatedAt` are meaningful on them.
`action_items.taskSyncId` is the parent task's `syncId` (stored as `action_items.task_id` on the
server). The flat frame is not an export format and is never written to disk as a user artifact.

CSV export is one row per activity (task fields repeated), plus header:

```text
task_sync_id,task_name,description,priority,context,task_achieved,creation_time,
activity_sync_id,activity_description,activity_achieved
```

CSV import accepts the same header in any order. Cells beginning with `=`, `+`, `-`, `@`, tab, or CR
are prefixed with `'` on export to prevent spreadsheet formula injection.

## 8. API design

Base path `/v1`. Errors use `application/problem+json` (RFC 9457) with `code`, `detail`, and
`fields`. All responses include `X-Request-Id`. Limits: 10 MB body for import, 2 MB per sync batch,
500 rows per sync batch.

The task-mutation endpoints are versioned and stable, but the v1 web UI only exercises reads plus
import/export (§11); direct web editing reuses the same endpoints in 1.1.

### Auth and devices

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/v1/auth/register` | invite/allowlist | Create account (disabled by default in private deployments) |
| POST | `/v1/auth/login` | — | `{email,password,deviceName}` → tokens + `deviceId` |
| POST | `/v1/auth/refresh` | refresh token | Rotate refresh token, issue access token |
| POST | `/v1/auth/logout` | bearer | Revoke the calling device session |
| GET | `/v1/me` | bearer | Profile, device list, storage usage |
| DELETE | `/v1/me` | bearer | Delete account and all rows/archives |

### Sync: backup and download

Cloud sync is exactly two calls. The app always runs **backup first, then download**, so local edits
reach the server before the client adopts remote state. There is no combined endpoint and no cursor.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/v1/sync/backup` | Upload dirty rows by table; merge each row by LWW; return accepted/rejected |
| GET | `/v1/sync/updates?lastDownloadAt=<token>&limit=500` | Download rows changed since `lastDownloadAt`; add `full=true` for a full scan |
| GET | `/v1/sync/status` | Row counts, oldest retained tombstone, per-device `lastBackupAt`, server time |

`POST /v1/sync/backup` request (`Authorization: Bearer <access>`, `Idempotency-Key: <uuid>`):

```json
{
  "deviceId": "8f0e…",
  "lastBackupAt": "2026-11-16T09:00:00Z",
  "tables": {
    "tasks": [
      { "syncId": "1f0c…", "taskName": "Morning Focus", "description": "Start day",
        "priority": 1, "context": "Daily Routine", "achieved": false,
        "creationTime": "2026-11-15T08:00:00Z", "updatedAt": "2026-11-16T09:12:00Z",
        "deleted": false }
    ],
    "action_items": [
      { "syncId": "2a1b…", "taskSyncId": "1f0c…", "description": "Drink water",
        "achieved": true, "updatedAt": "2026-11-16T09:12:00Z", "deleted": false }
    ]
  }
}
```

Response:

```json
{
  "serverTime": "2026-11-16T09:12:04Z",
  "accepted": [
    { "table": "tasks", "syncId": "1f0c…", "storedUpdatedAt": "2026-11-16T09:12:00Z" },
    { "table": "action_items", "syncId": "2a1b…", "storedUpdatedAt": "2026-11-16T09:12:00Z" }
  ],
  "rejected": [
    { "table": "tasks", "syncId": "9c8d…", "reason": "stale",
      "sentUpdatedAt": "2026-11-16T09:12:00Z",
      "serverUpdatedAt": "2026-11-16T09:10:00Z",
      "row": { "syncId": "9c8d…", "taskName": "Evening Review", "description": "",
               "priority": 2, "context": "Work", "achieved": false,
               "creationTime": "2026-11-14T17:00:00Z",
               "updatedAt": "2026-11-16T09:10:00Z", "deleted": false } }
  ],
  "conflicts": 1
}
```

`GET /v1/sync/updates` response (final page):

```json
{
  "serverTime": "2026-11-16T09:12:04Z",
  "mode": "incremental",
  "next": "eyJ0IjoxNzYzMjg0MzI0MDAwLCJyIjoxLCJpZCI6IjJhMWIuLi4ifQ",
  "hasMore": false,
  "tables": {
    "tasks": [ /* changed task rows and tombstones */ ],
    "action_items": [ /* changed activity rows and tombstones */ ]
  },
  "counts": {
    "tasks": { "live": 42, "deleted": 3 },
    "action_items": { "live": 118, "deleted": 7 }
  }
}
```

Rules:

- **Backup is incremental.** The app sends only rows with `dirty = 1`, i.e. every row created, edited,
  or tombstoned since the last successful backup, grouped by table. It sends 1…N batches and advances
  `lastBackupAt` only after every batch is accepted.
- **Download is incremental.** `lastDownloadAt` is an opaque server token (base64url of
  `{"t": server_updated_at_ms, "r": table_rank, "id": syncId}`; clients store it verbatim and never
  construct it). The server selects the next `(server_updated_at, table_rank, id)` rows, so equal
  timestamps page correctly and a parent task precedes its activities.
  A **full download** is requested with `full=true` (no token on the first page); later pages send
  `full=true` **and** `lastDownloadAt=<next>`, and the client keeps reconciling absence until
  `hasMore` is false. The final `next` is then used for ordinary incremental calls without `full`.
- **No echo loop.** An accepted row gets a fresh `server_updated_at`, so the download feed includes it
  (other devices need it). On the device that uploaded it, the returned row equals local state and
  `dirty` is already cleared, so applying it is a no-op; it is not re-uploaded because server-applied
  rows are stored with `dirty = 0`.
- **Rejected rows carry the winner.** `rejected[].row` is the authoritative server row, so the client
  can converge immediately without waiting for the next download. It is applied only when the local
  row still holds the sent version; a newer mid-run local edit is never overwritten.
- **`next` is mandatory.** When `hasMore` is true, repeat with `lastDownloadAt=<next>`; each page is
  committed locally before the token advances.
- **Final counts are mandatory.** The last page must carry `counts` for both tables. The client
  compares them with local live counts after applying the page; a mismatch with no pending dirty rows
  fails the run and schedules a full download (§9.8).
- **`410 full_download_required`.** The token is older than the server tombstone window (90 days) or
  cannot be parsed. The client clears `lastDownloadAt`, performs a full download, reconciles absence
  as deletion, then pushes any remaining dirty rows.
- **No-op runs.** Nothing dirty and no rows after the token → `200` with empty `tables` arrays. The
  worker still makes the download call, because only the server knows whether another device wrote
  something.
- Limits: 500 rows or 2 MB per request; 10 000 tasks and 50 000 activities, or 20 MB of logical data,
  per user; batch sequentially and stop at the first failure.
- Duplicate `syncId` within one table in one payload → `422`. The same `syncId` may appear in both
  tables because each table has its own key namespace.
- `Idempotency-Key` is accepted on `POST /v1/sync/backup` and `/v1/import`; keys are retained 24 hours
  so a retried worker does not duplicate work. `GET /v1/sync/updates` is naturally idempotent.

Error handling:

| Status | Meaning | Client action |
| --- | --- | --- |
| 400 | Malformed request | Do not retry; record a diagnostic sync run |
| 401 | Access token expired/invalid | Refresh once, retry; on second 401 stop as `AuthRequired` |
| 403 | Device revoked | Clear tokens, stop, keep local data, show "signed out on another device" |
| 410 | `lastDownloadAt` older than retention | Clear the token, full download (§9.10), then re-run backup for remaining dirty rows |
| 413 | Batch too large | Split and retry immediately |
| 422 | Contract/format violation | Do not retry; surface as a client bug |
| 429 | Rate limited | Honour `Retry-After`, then back off |
| 5xx / timeout | Server or tunnel failure | Exponential backoff; app stays offline-first |

### Archives (server-side disaster recovery)

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/v1/archives` | Generate a gzip canonical-JSON archive of the current replica (`kind=manual\|daily`); hash-gated |
| GET | `/v1/archives` | List archives with size, hash, `kind`, `createdAt` |
| GET | `/v1/archives/{id}` | Download the original `.json.gz` |
| POST | `/v1/archives/{id}/verify` | Re-hash and validate against the contract |
| POST | `/v1/archives/{id}/restore` | **Disabled via API by default**; CLI-only restore (§14) |

Archives are generated **by the server**, never uploaded by the app. `POST /v1/archives` is used by
the daily job, the CLI, and the operator; when the canonical dataset hash matches the latest archive it
returns `200` with `{created: false, archive: <latest>}` instead of writing a duplicate. A restore
rewrites the live replica, advances `server_updated_at` to a fresh stamp on every restored row, and
writes tombstones for rows that existed before the restore but are absent from the archive, so the
next download converges every device.

### Task CRUD (web UI and integrations)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/tasks?priority=&context=&q=&includeDeleted=` | List tasks with activities |
| POST | `/v1/tasks` | Create (server mints `syncId` if absent) |
| GET/PUT/DELETE | `/v1/tasks/{syncId}` | Read, replace, soft-delete |
| POST | `/v1/tasks/{syncId}/activities` | Add activity |
| PATCH/DELETE | `/v1/activities/{syncId}` | Update achieved/description, soft-delete |

Every mutation advances the `server_updated_at` of each row it touches to a fresh monotonic stamp, so
the next download sees it (§5 D14). Deleting a task also tombstones its activities.

### Import / export

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/v1/import?dry_run=true&on_conflict=skip\|replace\|duplicate` | Multipart JSON or CSV; returns a validation report |
| GET | `/v1/export?format=json\|csv&includeDeleted=false` | Download canonical document |

Import rules:

- Validate against the JSON Schema (or CSV header contract), size, and row limits before writing.
- Match by `syncId`; if absent, compute `sha256(taskName|priority|sorted(activities))` to detect
  re-imports. `skip` leaves existing rows, `replace` applies the incoming task and its activities row
  by row and tombstones activities absent from the replacement, `duplicate` mints fresh UUIDs.
- `dryRun` returns `{valid, wouldCreate, wouldUpdate, wouldSkip, duplicates, errors[]}` and writes
  nothing.
- All writes for one import run in a single transaction; every touched task and activity row is
  advanced to a fresh `server_updated_at` stamp, so the next download delivers it. The response lists
  `touchedSyncIds` per table and `serverTime`; there is no log cursor to return.

## 9. Database sync protocol (Android Room ⇄ cloud API)

This section is normative. **Cloud sync** means bidirectional convergence of the user's complete
dataset between Room and the cloud replica, and it is exactly two operations:

```
Android Room ── POST /v1/sync/backup ──► cloud replica     copy changed rows by table (upload)
Android Room ◄─ GET  /v1/sync/updates ── cloud replica     copy changed rows by table (download)
```

In the project owner's terms: **cloud sync = cloud backup + download cloud update**. The backup half
copies changed rows from each Android table into the matching cloud table; the download half copies
changed rows back. The two halves are separate calls so each watermark advances independently and
each can be retried on its own.

Only **rows** are copied. Schema, indexes, WAL/`-shm` files, settings, and local numeric IDs are never
transferred. Every row has a stable UUID `syncId`; a row keeps that identity across devices, exports,
archives, and restores. The Room `Long id` is a local implementation detail: it is not sent, and an
incoming row is matched by `syncId` (existing row keeps its local id; a new row gets a fresh one).

The current syncable tables are `tasks` and `action_items`. A table is added to sync by listing it in
the table registry with its key, dependency order, and merge rule; adding or changing one is a
contract-version change. Internal/server-only tables (devices, refresh tokens, archives, idempotency
keys) are never copied to the app.

### 9.1 The two halves of cloud sync

| | Backup (upload) | Download (pull) |
| --- | --- | --- |
| Direction | Room → cloud | cloud → Room |
| Endpoint | `POST /v1/sync/backup` | `GET /v1/sync/updates` |
| Selects | Rows with `dirty = 1` (changed since the last accepted backup) | Rows whose `server_updated_at` is after `lastDownloadAt` |
| Payload | Row arrays grouped by table, with tombstones | Row arrays grouped by table, with tombstones |
| Merge | Per-row last-write-wins | Per-row apply with a dirty guard |
| Watermark | `lastBackupAt` (client clock) | `lastDownloadAt` (opaque server feed token) |
| Frequency | Login, app start, 12 h, manual | Login, app start, 12 h, manual; paged until caught up |
| Failure | Re-send the same dirty rows; idempotent | Re-request from the last committed token; idempotent |
| Final check | Server acknowledged every sent row | Live row counts per table match the server |

A full gzip **archive** is not part of sync. It is a server-generated disaster-recovery artifact
(`/v1/archives`, §8), never uploaded by the app and never used for normal convergence.

### 9.2 Watermarks and what "changed" means

Two watermarks are stored on the device, and they are the **only sync checkpoints**:

| Field | Owner | Value | Advanced when |
| --- | --- | --- | --- |
| `lastBackupAt` | client | Client-clock epoch milliseconds (ISO-8601 on the wire) | The final batch of a backup run has been accepted |
| `lastDownloadAt` | client (token issued by the server) | Opaque token: base64url of `{"t": <server_updated_at_ms>, "r": <table_rank>, "id": "<syncId>"}` | Each downloaded page has been committed locally |

The asymmetry is deliberate:

- The backup query runs on Room and compares the **client's** `updated_at` values. Only the client
  knows that clock, so the upload watermark is client time.
- The download query runs on the **server's** `server_updated_at` values. The client cannot predict
  those, so the server issues an opaque token. Encoding the table rank and `syncId` alongside the
  timestamp makes the scan a strict keyset: rows that share a millisecond still page correctly, and
  parent task rows precede their activities within a stamp.

`dirty` is **not** a third checkpoint. It is a per-row boolean that says "this row still needs to be
uploaded". It exists so the upload is exact; the only cross-run positions are the two watermarks.
Likewise `server_updated_at` is a per-row change marker, not a cursor table.

**What counts as changed.** A row is changed when it is created, when any of its fields change, when
it is deleted (tombstone), or when the server writes it through import, a web mutation, or an archive
restore. Every such write sets that row's `server_updated_at` to a fresh stamp. The task row and its
activity rows have independent stamps; an activity edit advances the activity row and, when the gate
result changes, also the parent task row. There is no `change_log`: the indexed
`(server_updated_at, syncId)` pairs in the two tables are the change feed (§5 D14).

`server_updated_at` is a **monotonic per-user millisecond stamp**, not raw wall time:
`max(server_now_ms, user_state.last_server_stamp + 1)` in the tenant database. It stays
wall-clock-shaped (so the `410` age check is meaningful) but never repeats or goes backwards across
writes. That guarantees a full or incremental scan cannot miss a row that is written while pagination
is in progress. All rows written by one request share one stamp; they are ordered by table rank and
`syncId`, so the order is deterministic. `next_server_stamp` reads and updates the tenant's counter in
one transaction (SQLite `BEGIN IMMEDIATE`, or Postgres `SELECT … FOR UPDATE` in the schema-per-user
variant), so two concurrent requests cannot reuse a stamp.

**Why upload selects `dirty = 1`, not a raw time range.** Every local mutation sets `dirty = 1` and
`updated_at = now` on the row it touches, so "rows changed since the last backup" is exactly the dirty
set. A pure `updated_at > lastBackupAt` query would also pick up rows just applied from the cloud, and
re-uploading them with this device's id would bump `server_updated_at` and echo back to every device
forever. `lastBackupAt` is still the run cutoff and the user-visible watermark: the backup reads
`dirty = 1 AND updated_at <= cutoff` per table, then advances the watermark to `cutoff` only after the
server accepts every batch. A row edited while the upload is in flight keeps `updated_at > cutoff` and
`dirty = 1`, so the next run sends it.

### 9.2.1 Where sync state lives

The two watermarks are **client-owned**; the cloud never treats its own copy as authoritative. The
server-side state that affects convergence is stored in database tables, not in `meta.json`.

| State | Location | Authoritative | Why |
| --- | --- | --- | --- |
| Client `lastBackupAt` | Android DataStore `sync_meta` | yes | the upload scan runs against Room's client clock |
| Client `lastDownloadAt` | Android DataStore `sync_meta` | yes | opaque token issued by the server and replayed by the client |
| `devices.last_backup_at` | control DB | no | client-reported copy for `status` and the dashboard |
| `devices.last_seen_at` | control DB | yes | session and device lifecycle |
| `user_state.last_server_stamp` | tenant DB | yes | must commit atomically with the rows |
| `user_state.schema_version` | tenant DB | yes | decides whether the file may be opened |
| `idempotency_keys` | tenant DB | yes | replay check and merge must share one transaction |
| `archives` registry | control DB | yes | account-wide list, deletion, and retention |
| `meta.json` | tenant folder | no | derived ops snapshot; safe to delete and regenerate |

Rules:

- A value belongs in a **table** if losing it, or reading a stale copy, can change what sync sends or
  accepts. That is true for the server stamp, schema version, idempotency responses, and archive
  index; `meta.json` is never read by request logic.
- `meta.json` holds only derived, human/ops-facing values: `userId`, `schemaVersion`, `createdAt`,
  `lastMigratedAt`, `databaseBytes`, `archiveCount`, `lastArchiveId`, `lastArchiveAt`, `generatedAt`.
  It is written atomically (`temp` + `os.replace`) after the database commit and may lag.
- The server does **not** persist the client's `lastDownloadAt` as a serving cursor. The token is
  self-contained and sent on each call; a stored copy would be a cache. Recording the last token time
  on the device row for diagnostics is optional and never used to select rows.
- `lastBackupAt` from the backup body is stored on the device row for display only. The server never
  filters or merges by it; the client's `dirty = 1` set is the upload selector.
- The final row counts are derived with `COUNT(*)` at read time; they are not stored as sync state.

### 9.3 Invariants

1. Room is the app's source of truth; the UI never blocks on the network.
2. Identity is `syncId` (UUID). Rows are copied as data; the local `Long id` may differ per device and
   is never transmitted. Applying an existing `syncId` keeps that row's local id.
3. Rows are copied table by table. One request applies all tables in dependency order (`tasks`, then
   `action_items`) in a single database transaction; there is no partial cross-table commit.
4. Every local mutation sets `updated_at` and `dirty = 1` on the row it changes. Deleting a task marks
   the task and its child activities as deleted and dirty; an activity change marks the activity and,
   when `achieved` changes, the parent task.
5. `lastBackupAt` advances only after every batch of a backup run is accepted; `lastDownloadAt`
   advances only inside the Room transaction that applies a page.
6. A row applied from the server is stored with `dirty = 0`, so it is never re-uploaded.
7. Sync is idempotent: replaying a batch, a page, or a whole run changes nothing and does not bump
   `server_updated_at`.
8. A local edit made while a run is in flight is never overwritten by that run and is never marked
   backed up; compare-and-clear fails and the row stays dirty.
9. Gate state is derived on-device and is never serialised.
10. Watermarks live in `sync_meta`, never in `tasks`; they are not part of the dataset, export, or
    archive.
11. Server sync state that affects correctness (`last_server_stamp`, `schema_version`, idempotency
    responses) lives in the tenant database; `meta.json` is derived and never read for decisions.
12. **Row-count parity is the final check.** After a successful backup and download with no pending
    dirty rows, the live row count of each table on the device equals the server count. A mismatch is
    reported as a sync failure and schedules a full download; it never triggers automatic deletion.

### 9.4 Local change tracking (Room v2)

Columns are added per §12. Behaviour:

- Every write to a task row sets `updated_at = now` and `dirty = 1` on that task.
- Every write to an activity row sets `updated_at = now` and `dirty = 1` on that activity. If the
  change can alter the task's gate result, the parent task is also marked `dirty = 1` with a new
  `updated_at`, because `tasks.achieved` is part of the copied row.
- `deleteTask()` tombstones the task **and every child activity** (`deleted_at = now, dirty = 1` on
  each row; delete remains soft, never a physical row removal).
- `deleteActivity()` tombstones that activity and marks the parent task dirty if `achieved` changes.
- All read queries add `WHERE deleted_at IS NULL` so tombstones never reach the UI.
- `sync_meta` is a DataStore-backed preferences record (not a task table) holding `last_backup_at`
  (Long, 0 on first install), `last_download_at` (String?, null on first install), `device_id`,
  `server_url`, `last_sync_at`, `force_full_download` (Boolean, set after a count mismatch), and the
  last 20 sync runs. It is not part of the task dataset and is not exported.
- The 1→2 migration marks every existing task and activity `dirty = 1`, assigns a UUID to each row,
  sets `last_backup_at = 0`, and leaves `last_download_at = null`, so the first login uploads the whole
  dataset and then performs a full download.
- Applying a server row writes `dirty = 0` and the server's `updatedAt`; gate recomputation during
  apply writes only `achieved` through a dedicated DAO update that does not touch `updated_at` or
  `dirty`.
- A **local** pipeline recompute that flips `achieved` is a content change: the affected tasks are
  marked `dirty = 1` with a new `updated_at` in the same transaction, so the gate result reaches the
  server. Only the sync-apply recompute (§9.8) skips dirty marking.
- The client may `purgeTombstones()` in both tables for rows with
  `deleted_at < now − 30 days AND dirty = 0` only after a confirmed backup. The server retains its
  tombstones for **90 days**.

### 9.5 Backup wire contract

`POST /v1/sync/backup` — `Authorization: Bearer <access>`, `Idempotency-Key: <uuid>`,
`Content-Type: application/json`. The request and response shapes are in §8; this subsection defines
the semantics.

Request rules:

- `tables.tasks` and `tables.action_items` contain the dirty rows. **All task rows are sent before any
  activity row, across batches**, so a child's parent exists by the time the child is applied. A batch
  may contain rows from one table or both; a task and its children may arrive in the same batch, in
  which case the server applies tasks first. A child whose parent is neither in the batch nor already
  stored returns `422` rather than surfacing a foreign-key error.
- Each row carries its current `updatedAt` and `deleted` flag. A tombstone carries `deleted: true`, its
  `syncId`, and `updatedAt`; no other fields are required.
- `deviceId` is the caller's registered device; a mismatch with the access token's `deviceId` is
  `403`.
- `lastBackupAt` is the watermark before this run. It is advisory for the server (stored per device
  for `status` and diagnostics); the server never uses it to filter or merge.
- Duplicate `syncId` within one table in one payload → `422`; the same `syncId` may appear once in
  `tasks` and once in `action_items` because each table has its own key namespace.
- 1…500 rows or 2 MB per batch. Batches are sent sequentially; each batch has its own idempotency key;
  the next batch is only sent after the previous one succeeds.

Response rules:

- `accepted[]` lists every row the server now holds, including idempotent replays. Each entry has
  `table`, `syncId`, and `storedUpdatedAt`: the version **the server stored**, which equals the sent
  `updatedAt` except when the five-minute future-clock clamp lowered it. The client clears `dirty`
  and adopts that value in one guarded statement:
  `UPDATE <table> SET updated_at = :storedUpdatedAt, dirty = 0 WHERE sync_id = :syncId AND updated_at = :localSentUpdatedAt`.
  The guard uses the value it actually sent, so a local edit during the upload matches 0 rows and the
  row stays dirty. Adopting the clamped value is required: keeping a future-dated local timestamp
  would make the server reject the user's next edit as stale and re-accept the same row on every
  backup.
- `rejected[]` reports a stale row. It carries `table`, `syncId`, `sentUpdatedAt`, `serverUpdatedAt`,
  and the authoritative server row. `sentUpdatedAt` echoes the value the client actually sent
  (before the server's clamp), so the guarded update can match the local row. The client applies the
  row only if it still holds that version (`sync_id = ? AND updated_at = sentUpdatedAt`): if the user
  edited during the upload, the newer local edit wins, stays dirty, and is pushed next run; otherwise
  the server row replaces it, `dirty` clears, and a non-blocking conflict is reported. This avoids
  depending on the next download, whose watermark may already be past that row, without overwriting a
  mid-run edit.
- `serverTime` is the server clock; the client does not adopt it as `lastBackupAt` (that watermark is
  client-clock).
- `conflicts` is the number of rejected rows, shown in Settings.
- The response never contains a download payload. Download is the separate `GET`.
- On success, and only after the final batch, the client sets `lastBackupAt = cutoff` where `cutoff`
  was captured before the first batch. A failed run leaves the old watermark and all unconfirmed rows
  dirty.

### 9.6 Server merge transaction

One database transaction per request. Rows are processed table by table; `tasks` must be applied
before `action_items` because of the foreign key.

```python
SYNC_TABLES = (("tasks", 0), ("action_items", 1))     # dependency order + feed rank

with tenant_db.begin():                              # BEGIN IMMEDIATE; exactly one account
    now_ms = server_now_ms()
    clamp = now_ms + 5 * 60_000                      # 5-minute future-clock clamp
    stamp = None
    deleted_tasks = []
    for table, _rank in SYNC_TABLES:
        for incoming in payload.tables.get(table, []):
            sent_updated_at = incoming.updated_at      # echoed back for the client's guard
            incoming.updated_at = min(incoming.updated_at, clamp)
            if table == "action_items" and parent_is_deleted(incoming.taskSyncId):
                incoming.deleted = True          # never accept a live orphan
            row = get_row(table, incoming.syncId)    # session is bound to this tenant
            incoming_key = (incoming.updated_at, payload.device_id)
            accepted_deleted = False
            if row is None:
                stamp = stamp or next_server_stamp() # user_state.last_server_stamp + 1
                insert_row(table, incoming, origin_device=payload.device_id,
                           server_updated_at=stamp)
                accepted.append((table, incoming.syncId, incoming.updated_at))
                accepted_deleted = incoming.deleted
            elif incoming_key > (row.updated_at, row.origin_device):
                stamp = stamp or next_server_stamp()
                update_row(row, incoming, origin_device=payload.device_id,
                           server_updated_at=stamp)
                accepted.append((table, incoming.syncId, incoming.updated_at))
                accepted_deleted = incoming.deleted
            elif incoming_key == (row.updated_at, row.origin_device):
                accepted.append((table, incoming.syncId, incoming.updated_at))  # replay
            else:
                rejected.append((table, incoming.syncId, sent_updated_at, row))  # authoritative row
            if table == "tasks" and accepted_deleted:
                deleted_tasks.append(incoming.syncId)
    # A deleted task cannot have live children; tombstone any child the client did not send.
    for task_id in deleted_tasks:
        stamp = stamp or next_server_stamp()
        tombstone_live_children(task_id, origin_device="server", server_updated_at=stamp)
# After the tenant commit: diagnostic only, never part of the merge decision.
best_effort_update_device(device_id, last_backup_at=payload.lastBackupAt, last_seen_at=now_ms)
```

- The tenant session is opened from the token's `userId`; there is no `user_id` argument to forget and
  no query in this transaction can reach another account's file.
- `origin_device` is stored on **both** tables so the tie-break `(updatedAt, deviceId)` is
  deterministic on both sides. Rows created directly on the server (import, web CRUD, restore) store
  the sentinel `"server"`, never `NULL`, so tuple comparison is well defined.
- A newer upsert clears an existing `deleted_at` (resurrection); a newer tombstone wins. This lets a
  user undo a delete on a device that was offline.
- A winning tombstone sets `deleted_at = server now` (not the client's timestamp), so the 90-day
  retention window is measured on the server clock and cannot be extended by a skewed device.
- An activity row cannot be live under a deleted task. If it arrives live after the task tombstone has
  won, the server stores it deleted with the task's state; the client gate never sees an orphan. A
  cascade tombstone uses `updated_at = max(child.updated_at, task.updated_at)` and the same server
  stamp as the task tombstone, so it is a deterministic row that every device downloads.
- `server_updated_at` is stamped **only** when a row is inserted, replaced, or tombstoned. An
  idempotent replay does not bump it, which is what prevents accepted rows from bouncing back and
  forth.
- The five-minute clamp is authoritative. `accepted[].storedUpdatedAt` is the stored (possibly clamped)
  version; the client writes it back while clearing `dirty`, so a future-skewed device converges to
  the server's version after one upload instead of resending it forever.
- Import, web CRUD, and archive restore use the same rule: every row they add or change receives a
  fresh stamp; task deletion cascades tombstones to children.
- The idempotency key and its stored response live in the **tenant** database, so the replay check and
  the merge commit in the same transaction. A retried batch returns the stored result without
  re-merging, even if the control DB is briefly unavailable.

### 9.7 Download wire contract

`GET /v1/sync/updates?lastDownloadAt=<token>&limit=500` with the bearer token. Add `full=true` for a
full download; the first full page has no token, later full pages send both `full=true` and the
previous `next`.

The server reads a single ordered feed built from both tables. `tbl_rank` is `0` for `tasks` and `1`
for `action_items`, so a parent task precedes its activities when both share a stamp. SQLite form;
Postgres uses the same ordering:

```sql
WITH feed AS (
    SELECT 0 AS tbl_rank, 'tasks' AS tbl, id, server_updated_at FROM tasks
    UNION ALL
    SELECT 1 AS tbl_rank, 'action_items' AS tbl, id, server_updated_at FROM action_items
)
SELECT tbl_rank, tbl, id, server_updated_at FROM feed
WHERE server_updated_at > :token_time
   OR (server_updated_at = :token_time AND tbl_rank > :token_rank)
   OR (server_updated_at = :token_time AND tbl_rank = :token_rank AND id > :token_id)
ORDER BY server_updated_at, tbl_rank, id
LIMIT :limit;
```

The session is already bound to the caller's `tasks.db`, so there is no `user_id` predicate to
forget. The selected keys are then loaded from their tables and grouped under `tables.tasks` and
`tables.action_items`.

For the first page of a **full** download, the server substitutes `token_time = 0`,
`token_rank = 0`, `token_id = ''` and skips the `410` age check. Every later page, full or
incremental, uses the real token, so the same keyset serves both modes.

- The **row** is the pagination unit. There is no nested aggregate; each table is copied as rows.
- `next` encodes the last row's `(server_updated_at, table_rank, syncId)`. It is opaque to the client
  and is stored verbatim as `lastDownloadAt`.
- `mode` is `"full"` when `full=true` was sent and `"incremental"` otherwise.
- `hasMore` is true when the page was full. The client commits the page, advances
  `lastDownloadAt = next`, and repeats until `hasMore` is false.
- Tombstones are returned as rows with `deleted: true`, their `syncId`, and `updatedAt`. They carry a
  `server_updated_at`, so they page like any other row.
- `410 full_download_required` is returned for an incremental token whose timestamp is older than
  `now − A2D_TOMBSTONE_RETENTION_DAYS` (90), when the token cannot be decoded, or when a restore has
  invalidated old positions. It is never returned for `full=true`. The client clears the token and
  restarts with `full=true`.
- A full download returns every live row plus retained tombstones in feed order. The client treats any
  local, non-dirty row whose `syncId` is absent from the full result as deleted; this is how rows whose
  tombstones have already been pruned disappear. Reconciliation removes child rows before parent
  tasks, or relies on the local `ON DELETE CASCADE` when the parent is removed.
- The **final page** (`hasMore: false`) includes `counts` for each table, captured in the same read
  transaction (one snapshot) as that page:
  `{"tasks": {"live": 42, "deleted": 3}, "action_items": {"live": 118, "deleted": 7}}`.
  `live` counts rows with `deleted_at IS NULL`; `deleted` counts retained tombstones. Only `live` is
  used for parity because client and server tombstone retention differ (30 vs 90 days). Because the
  page and the counts see one snapshot, a row written by another device is either in this page or
  after it, never only in the count. SQLite read transactions give that snapshot; a future
  schema-per-user Postgres tenant backend must use `REPEATABLE READ` for this query.
- The response includes `serverTime` for diagnostics; it is never used as a watermark.

### 9.8 Android apply transaction

One Room transaction per downloaded page (and one per rejected row set applied from a backup
response), in this order:

1. **Apply task rows first.** For each server task row, find by `syncId`.
   - If local `dirty = 1` and `local.updated_at >= server.updatedAt`, keep local and leave it dirty;
     it will be pushed on the next backup.
   - Otherwise replace the task row, set `local.updated_at = server.updatedAt`, and clear `dirty`.
     The row is now server-owned and must not be re-uploaded.
2. **Apply activity rows second.** Use the same per-row dirty guard. The parent task is guaranteed to
   exist because tasks are applied first and the feed orders them before children sharing a stamp.
   A row whose parent is absent from the local database is skipped and reported as a protocol error;
   the next full download repairs it.
3. **Tombstones** are normal rows with `deleted = true`; deleting a task cascades locally to its
   activities. If a child is dirty and newer than a task tombstone, it is still stored under the
   deleted parent but is not visible in the UI; the server tombstones it on the next backup.
4. **Allocate local IDs** for new rows with a global `max(id)+1` per table (never the old `id*10+n`
   scheme). `syncId` is authoritative; existing rows keep their local id.
5. **Apply rejected rows** from the backup response table by table, guarded by `sentUpdatedAt`: the
   server already adjudicated that version, so replace the row, set `updated_at` to the server value,
   clear `dirty`, and record a conflict notice. If the guard matches 0 rows, the user edited during
   the upload; keep the newer local row dirty and do not overwrite it.
6. **Recompute gates** for touched tasks (`processActivityWithGate`) and once for the pipeline. The
   recompute writes only `achieved` through a DAO update that does **not** bump `updated_at` or
   `dirty`, so sync never manufactures new changes.
7. **Advance `lastDownloadAt`** to `next` last, inside the same transaction. A crash before this point
   re-runs the page; steps 1–6 are idempotent.
8. **Full-download reconciliation:** when this was a full download, mark local rows whose `syncId` is
   absent and which are not dirty as deleted (children first, then tasks, or let the task cascade).
   Dirty rows are never removed; they are pushed next.
9. **Purge:** after the worker finishes successfully (outside the page transaction),
   `purgeTombstones()` removes local tombstones older than 30 days whose delete has been confirmed
   (`dirty = 0`) from both tables.
10. **Row-count check:** after the last page, compare `SELECT COUNT(*) WHERE deleted_at IS NULL` for
    each table with the `counts.live` values from the final response. A mismatch with no pending dirty
    rows fails the run as `RowCountMismatch` and schedules `full=true` for the next run. A mismatch
    with pending dirty rows is expected (a local insert/delete landed mid-run) and is deferred to the
    next run.

### 9.9 Concurrency and conflict semantics

- Local writes and sync writes share the Room transaction queue; no lost updates.
- Conflict resolution is **per row**: the higher `(updatedAt, deviceId)` wins in that table. The loser
  receives a `rejected` entry with the authoritative row; it replaces its local copy only if that row
  still holds the version it sent, clears dirty, and gets a non-blocking notice: "1 row changed on
  another device". A newer local edit made during the upload is kept and pushed next run.
- Because tasks and activities are separate rows, a task edit and an activity edit from different
  devices can both be accepted. The apply path recomputes the gate from the resulting activity set, so
  `tasks.achieved` remains consistent with the rows; a task tombstone still removes the whole task.
- `tasks.achieved` is a cached gate result, so an activity-only change can leave the server's task row
  momentarily stale. The web view and every device recompute it from the activity rows, and the next
  task write persists the corrected value; it is never treated as the completion rule.
- Backup and download are serialised under one unique WorkManager job, so a row cannot be uploaded
  while a download for the same row is being applied. If a local edit lands mid-run, the
  compare-and-clear in §9.5 fails and the next run pushes the newer version.
- Rejects and the last 20 sync runs (trigger, pushed/pulled counts, error code, row-count check) are
  visible under Settings → Sync. Task content is never logged.
- Clock skew is tolerated by the 5-minute future clamp on the upload. A badly wrong clock can still win
  a conflict; devices are expected to have automatic time enabled, and `serverTime` is echoed for
  diagnostics. The download side is immune to client clock skew because it uses server time.

### 9.10 First sync, reinstall, and watermark expiry

- **Fresh account with local data:** the migration leaves every task and activity row dirty and
  `last_download_at = null`. Run 1 backs up all dirty rows in batches (advancing `lastBackupAt` after
  the last accepted batch), then performs a full download (`full=true`), applies it, reconciles
  absence, and checks row counts. Run 2 is a no-op.
- **Reinstall or second device:** no local rows and no token. The worker skips backup (nothing dirty)
  and performs a full download; the local database is reconstructed from the cloud replica and the
  row counts are compared.
- **`410 full_download_required`:** clear `lastDownloadAt`, restart with `full=true`, reconcile
  absence while keeping local dirty rows (§9.8 step 8), then run a backup for the remaining dirty
  rows. Nothing is silently dropped.
- **Account switch:** clear `sync_meta` and local task tables only after an explicit confirmation;
  never mix two accounts' data.
- **Archive restore (server side):** every restored row is advanced to a fresh `server_updated_at`
  and rows absent from the archive become tombstones, so devices converge through the normal download;
  no special client path is needed.

### 9.11 Auth refresh and revocation

The OkHttp interceptor attaches the access token and, on a single 401, calls
`POST /v1/auth/refresh` with the device's refresh token, persists the rotated pair atomically, and
replays the request once. Concurrent sync work is serialised under one unique WorkManager name so two
refreshes cannot race. A revoked device gets `403`, the worker stops, tokens are cleared, and local
data is retained.

### 9.12 Batching, retry, idempotency

- Unique work `a2d-sync`; periodic 12 h with `ExistingPeriodicWorkPolicy.KEEP`, immediate runs
  `APPEND_OR_REPLACE`. Constraints: `NetworkType.CONNECTED`.
- **Backup:** 1…500 rows or 2 MB per batch, tasks before activities, sent sequentially, each with its
  own persisted `Idempotency-Key`. One `cutoff` is captured before the first batch and used for both
  tables, so a task or activity created after it is left for the next run instead of orphaning a
  child. Stop at the first failed batch and keep `lastBackupAt` unchanged so the next run re-sends the
  same dirty rows.
- **Download:** pages of 500 rows; commit each page and advance `lastDownloadAt` before requesting the
  next. A failure mid-download leaves the last committed token; the next run resumes from there.
- Retry on 429/5xx/timeout with exponential backoff (30 s → 1 h, max 6 attempts); no retry on
  400/422; one refresh on 401; on 410 clear the token and full-download.
- The server keeps idempotency key → response for 24 h, so a retried batch cannot duplicate writes.
- Limits: 10 000 tasks and 50 000 activities, or 20 MB of logical task data, per user; the tenant file
  plus archives is capped by `A2D_USER_QUOTA_BYTES` (default 100 MB). Exceeding a limit returns `413` with usage.

### 9.13 Triggers and sequence

Triggers: successful login (immediate), app start with a valid session, periodic 12 h, manual
"Sync now", and an optional 30-second debounce after local mutations.

```
Login / app start / 12 h / Sync now
                 │
                 ▼
        TokenStore ──► SyncWorker (unique work, network constraint)
                 │
   1. POST /v1/sync/backup      (dirty rows by table, cutoff = now)
        └─ accepted → compare-and-clear dirty per row; rejected → apply authoritative row
        └─ after last batch: lastBackupAt = cutoff
                 │
   2. GET /v1/sync/updates  (full=true on first run / after 410;
                             otherwise lastDownloadAt=<token>)
        └─ apply page in one Room tx (tasks then action_items) → lastDownloadAt = next
        └─ repeat while hasMore (keep full=true for the whole full run)
                 │
   3. compare live row counts per table with the final page's counts
        └─ match → Success(at); mismatch → RowCountMismatch, next run full=true
                 │
   4. (server-side, independent) daily/manual archive job
```

Archives are intentionally outside the worker: they are generated by the server and never uploaded
by the app.

### 9.14 Client state and diagnostic surface

`syncState`: `Idle → Queued → BackingUp → Downloading → Applying → Success(at) |
RowCountMismatch(delta) | Offline | AuthRequired | Error(code)`. The Settings screen shows
`lastBackupAt`, `lastDownloadAt` (as a human-readable server time decoded from the token when
possible), pending dirty counts per table, conflict count, last row-count check, and a "Sync now"
action. A failed run never changes local task state beyond the pages that already committed.

### 9.15 Minimal client surface

```kotlin
interface SyncApi {
    @POST("v1/sync/backup")
    suspend fun backup(
        @Header("Idempotency-Key") key: String,
        @Body body: BackupRequest
    ): BackupResponse

    @GET("v1/sync/updates")
    suspend fun updates(
        @Query("lastDownloadAt") token: String?,
        @Query("full") full: Boolean = false,
        @Query("limit") limit: Int = 500
    ): UpdatesResponse
}

data class BackupRequest(
    val deviceId: String,
    val lastBackupAt: String?,        // ISO-8601; stored locally as epoch ms
    val tables: TableRows
)

data class TableRows(
    val tasks: List<TaskRowDto>,          // dirty rows only
    @SerialName("action_items") val actionItems: List<ActionItemRowDto>
)

data class TaskRowDto(
    val syncId: String, val taskName: String, val description: String,
    val priority: Int, val context: String, val achieved: Boolean,
    val creationTime: String, val updatedAt: String, val deleted: Boolean
)

data class ActionItemRowDto(
    val syncId: String, val taskSyncId: String, val description: String,
    val achieved: Boolean, val updatedAt: String, val deleted: Boolean
)

data class AcceptedDto(val table: String, val syncId: String, val storedUpdatedAt: String)
data class RejectedDto(
    val table: String, val syncId: String, val reason: String,
    val sentUpdatedAt: String, val serverUpdatedAt: String,
    val row: SyncRowDto   // authoritative row
)

data class TableCounts(val live: Int, val deleted: Int)
data class UpdatesResponse(
    val serverTime: String,
    val mode: String,                 // "full" | "incremental"
    val next: String,                 // stored as lastDownloadAt
    val hasMore: Boolean,
    val tables: TableRows,
    val counts: Map<String, TableCounts>?   // present on the final page
)

// DAO additions (same shape for action_items)
@Query("SELECT * FROM tasks WHERE dirty = 1 AND updated_at <= :cutoff")
suspend fun dirtyTasks(cutoff: Long): List<TaskEntity>

@Query("SELECT * FROM action_items WHERE dirty = 1 AND updated_at <= :cutoff")
suspend fun dirtyActionItems(cutoff: Long): List<ActionItemEntity>

@Query("UPDATE tasks SET updated_at = :storedUpdatedAt, dirty = 0 WHERE sync_id = :syncId AND updated_at = :localSentUpdatedAt")
suspend fun confirmTaskBackedUp(syncId: String, localSentUpdatedAt: Long, storedUpdatedAt: Long): Int   // 0 = edited meanwhile

@Query("UPDATE action_items SET updated_at = :storedUpdatedAt, dirty = 0 WHERE sync_id = :syncId AND updated_at = :localSentUpdatedAt")
suspend fun confirmActionItemBackedUp(syncId: String, localSentUpdatedAt: Long, storedUpdatedAt: Long): Int

@Query("SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL")
suspend fun liveTaskCount(): Int

@Query("SELECT COUNT(*) FROM action_items WHERE deleted_at IS NULL")
suspend fun liveActionItemCount(): Int

@Query("UPDATE tasks SET achieved = :achieved WHERE sync_id = :syncId")
suspend fun updateAchievedFromSync(syncId: String, achieved: Boolean)  // never bumps updated_at
```

## 10. Backend design

```
a2d-cloud/
├── pyproject.toml                 # fastapi, uvicorn, sqlalchemy, pydantic-settings, argon2-cffi,
│                                  # pyjwt, jinja2, itsdangerous, pytest
├── supervisord.conf               # program defs for uvicorn app + cloudflared (dev default)
├── .gitignore                     # data/, .venv/, __pycache__/, .env
├── schema/a2d-tasks-v1.schema.json
├── migrations/
│   ├── control/0001_init.sql
│   └── tenant/0001_init.sql       # more versioned files appended, never edited
├── app/
│   ├── main.py                    # app factory, routers, static, templates
│   ├── config.py                  # pydantic-settings
│   ├── control_db.py              # control engine/session (identity + registry)
│   ├── tenant.py                  # TenantStore: dirs, engines, locks, provisioning
│   ├── models/                    # control-plane SQLAlchemy models
│   ├── schemas/                   # pydantic request/response + contract models
│   ├── api/                       # auth.py, sync.py, tasks.py, archives.py, io.py
│   ├── services/                  # backup.py, download.py, archive.py, exporter.py, importer.py
│   ├── web/                       # routes.py, templates/, static/
│   ├── security.py                # hashing, JWT, CSRF, rate limit
│   └── cli.py                     # create-user, migrate-users, archive, restore, restore-backup, ops-backup, purge-user, prune
├── tests/                         # pytest + httpx.AsyncClient
└── data/                          # local runtime root, gitignored; $A2D_DATA_DIR in deployment
```

Deployment assets — supervisord drop-in, env template, scripts, and runbook — live in
[`Deployment/a2d-cloud/`](../../Deployment/a2d-cloud/) (§14), separate from backend source.
`a2d-cloud/data/` is **not source and not committed**. It is the host-side runtime root for a local
checkout. Every `$A2D_DATA_DIR/...` path in this document is the host path, so
`$A2D_DATA_DIR/users/<userId>/tasks.db` is `a2d-cloud/data/users/<userId>/tasks.db` on the host
when `A2D_DATA_DIR` defaults to `a2d-cloud/data/`. `a2d-cloud/.gitignore` excludes `data/`; the
deployment folder excludes `.env` and logs.

### 10.1 On-disk layout

Runtime paths are rooted at `A2D_DATA_DIR`: `$A2D_APP_HOME/data` in the deployment, `a2d-cloud/data/`
in a local checkout. The control DB and tenant root default beneath it.

```
$A2D_DATA_DIR/                             # host path; a2d-cloud/data/ on the host checkout
├── control.db                     # identity, devices, tokens, archive registry, quotas
├── control.db-wal
├── users/
│   └── <userId>/                  # server-generated UUID, mode 0700
│       ├── tasks.db               # authoritative task/activity data
│       ├── tasks.db-wal
│       ├── tasks.db-shm
│       ├── meta.json              # derived ops snapshot (never authoritative)
│       ├── .lock                  # flock for migrate/restore/purge
│       └── archives/2026/11/<archiveId>.json.gz
└── tmp/
```

- `A2D_DATA_DIR` is the only root setting. The control plane defaults to
  `sqlite:///$A2D_DATA_DIR/control.db` and tenants to `$A2D_DATA_DIR/users`; `A2D_DATABASE_URL`
  overrides only the control plane (for Postgres). Local development runs with
  `A2D_DATA_DIR=./data`, so all state stays inside `a2d-cloud/data/`.
- `A2D_DATA_DIR` is created `0700`; `users/` and each tenant directory are `0700`. The service
  user must own the data directory.

- `userId` is a server-generated UUIDv4. The tenant store rejects anything not matching the UUID
  pattern and verifies that the resolved path stays under `/data/users`; the name is never derived
  from email, display name, or other client input.
- Registration creates the folder, the database, and the control rows as one logical operation. If a
  step fails the folder is removed and the control rows roll back; provisioning is idempotent.
- Account deletion revokes sessions, then removes the folder and the control rows. `purge-user`
  retries a partial deletion; an inactive account is never served.
- `meta.json` is regenerated from the control DB and the tenant DB. Deleting it loses only ops
  metadata, never task data or sync state.

### 10.2 Control database

Holds no task content. SQLite WAL by default at `$A2D_DATA_DIR/control.db`; `A2D_DATABASE_URL` may
point it at Postgres.

```sql
users(id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL,
      is_active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL);

devices(id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        name TEXT, platform TEXT, last_seen_at INTEGER, last_backup_at INTEGER,
        revoked_at INTEGER);

refresh_tokens(id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
               device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
               token_hash TEXT NOT NULL, expires_at INTEGER NOT NULL, revoked_at INTEGER);

user_storage(user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
             schema_version INTEGER NOT NULL, bytes_used INTEGER NOT NULL DEFAULT 0,
             folder_created_at INTEGER NOT NULL, last_migrated_at INTEGER);

archives(id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
         created_at INTEGER NOT NULL, kind TEXT NOT NULL, size_bytes INTEGER NOT NULL,
         sha256 TEXT NOT NULL, relpath TEXT NOT NULL, note TEXT);
```

`devices.last_backup_at` is the client-reported diagnostic copy from §9.2.1; it is never used to
select or merge rows. `user_storage` is an account-level registry/cache for folder bytes and schema
version; the tenant's `user_state.schema_version` is authoritative. The archive registry is
authoritative for listing and retention, while the archive files live under the owning tenant's
folder.

### 10.3 Tenant database

One SQLite file per account, WAL, with **no `user_id` columns**: the file is the tenant boundary.

```sql
user_state(id INTEGER PRIMARY KEY CHECK (id = 1),
           schema_version INTEGER NOT NULL,
           last_server_stamp INTEGER NOT NULL DEFAULT 0,
           created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL);

tasks(id TEXT PRIMARY KEY,                    -- syncId
      task_name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
      priority INTEGER NOT NULL, context TEXT NOT NULL DEFAULT 'No Context',
      achieved INTEGER NOT NULL DEFAULT 0, creation_time TEXT,
      updated_at INTEGER NOT NULL,            -- client-supplied version for LWW
      deleted_at INTEGER,                     -- tombstone
      server_updated_at INTEGER NOT NULL,     -- monotonic per-user download stamp
      origin_device TEXT NOT NULL DEFAULT 'server');  -- LWW tie-break
CREATE INDEX ix_tasks_server ON tasks(server_updated_at, id);
CREATE INDEX ix_tasks_deleted ON tasks(deleted_at);

action_items(id TEXT PRIMARY KEY,                    -- syncId
             task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,  -- parent syncId
             description TEXT NOT NULL, achieved INTEGER NOT NULL DEFAULT 0,
             updated_at INTEGER NOT NULL,             -- client-supplied version for LWW
             deleted_at INTEGER,                      -- tombstone
             server_updated_at INTEGER NOT NULL,      -- monotonic per-user download stamp
             origin_device TEXT NOT NULL DEFAULT 'server');  -- LWW tie-break
CREATE INDEX ix_action_items_server ON action_items(server_updated_at, id);
CREATE INDEX ix_action_items_deleted ON action_items(deleted_at);
CREATE INDEX ix_action_items_task ON action_items(task_id);

idempotency_keys(key TEXT PRIMARY KEY, response_json TEXT NOT NULL, created_at INTEGER NOT NULL);
```

`user_state` is a single row. `last_server_stamp` advances in the same transaction as the row writes
(`BEGIN IMMEDIATE`), so it cannot drift from the rows it orders. `schema_version` gates opening the
file. Reads and writes use a session bound to exactly one tenant; no query can join or expose two
accounts. The control DB is the only place that resolves a `userId`; API handlers take it from the
access token, never from a body or path parameter.

Each syncable table is an independent row feed with the same three columns: `sync_id`, `updated_at`
(client version), and `server_updated_at` (server order). `server_updated_at` is set to a fresh
monotonic stamp on insert, on a winning update, on a delete, on import, on web CRUD, and on archive
restore; an idempotent replay does not change it. Rows written by one request share a stamp and are
ordered by `(table_rank, id)`, with tasks before activities. A task tombstone also tombstones its live
children at that stamp, so no activity can remain live under a deleted task. There is no `change_log`,
`server_rev`, or cursor table; the download endpoint unions the two indexed feeds (§9.7). Row counts
for the final sync check are computed with `COUNT(*)` on `deleted_at IS NULL`.

### 10.4 Tenant lifecycle, connections, migrations

- `TenantStore` keeps an LRU cache of engines (`A2D_TENANT_CACHE_SIZE`, default 64) and disposes idle
  ones (`A2D_TENANT_IDLE_SECONDS`, default 300). Every connection sets `journal_mode=WAL`,
  `synchronous=NORMAL`, `busy_timeout=5000`, and `foreign_keys=ON`.
- The default deployment runs one uvicorn worker. Multiple workers are possible because each tenant
  owns its file, but in that mode schema changes are applied only by the CLI while requests are
  drained.
- Migrations are versioned SQL files (`migrations/tenant/NNNN_*.sql`) applied in order under the
  tenant `.lock` with `BEGIN IMMEDIATE`, updating `user_state.schema_version` and `meta.json`. They
  run lazily on first open or via `app.cli migrate-users` before a deploy. A file whose version is
  newer than the running code returns `503`; it is never downgraded.
- Quota: `A2D_USER_QUOTA_BYTES` (default 100 MB) counts `tasks.db` plus archives and is checked on
  backup, import, and archive creation. Exceeding it returns `413` with current usage; `GET /v1/me`
  reports it.
- Backup: `sqlite3 control.db ".backup …"` plus per-tenant `VACUUM INTO` snapshots, or stop the app
  for a filesystem snapshot. A live `tasks.db` is never copied while WAL is active.
- Archives are canonical JSON under the tenant folder; the control-DB registry is authoritative.
  Restore takes that tenant's `.lock` and touches no other account.

### 10.5 Postgres note

The control plane can run on Postgres. Tenant storage is file-based in v1; `TenantStore` is the
extension point for a future schema-per-user Postgres backend (the same `user_state`, `tasks`, and
`action_items` shape in one schema per account). Row-level `user_id` scoping is deliberately not the
default because it weakens the folder-level isolation guarantee.

## 11. Web app

Server-rendered pages, same origin, secure `HttpOnly` cookie session, CSRF token on every form:

| Page | Contents |
| --- | --- |
| `/login` | Email/password; no public registration when disabled |
| `/` | Dashboard: task counts, completed %, gate milestone output, per-device `lastBackupAt`/`lastSeenAt` |
| `/tasks` | Read-only sortable/filterable list (priority, context, text search) |
| `/tasks/{syncId}` | Read-only task and activity view (editing arrives in 1.1) |
| `/import` | **v1 write path:** drag-and-drop JSON/CSV, conflict policy, dry-run report, then apply |
| `/export` | **v1 write path:** download JSON or CSV, copy curl snippet |
| `/archives` | List, download, verify, SHA-256, retention note; "create archive" for the operator |
| `/settings` | Devices (revoke), change password, delete account, sync/archive status |
| `/healthz` | Liveness/readiness for the edge |

Version 1 deliberately has no task-edit controls in the browser: import and export are the only web
writes, and both go through validation plus a dry-run before anything is stored. The CRUD endpoints
exist for 1.1 and for scripted tooling.

The web UI mirrors the app's completion rule: a task shows "Completed" only when it has at least one
activity and all are achieved; the gate milestone badge is recomputed from the same two-tier rule
for display only.

## 12. Android changes

### Dependencies

`androidx.work`, `androidx.datastore` or `security-crypto`, Retrofit + OkHttp + kotlinx.serialization,
`androidx.room:room-testing` + Robolectric for JVM migration tests. Add `INTERNET` and
`ACCESS_NETWORK_STATE` to the manifest.

### Room v2 (one migration)

SQLite cannot add a `NOT NULL` column without a default, and Room validates column defaults against
the entity. The migration therefore **recreates both tables** (`tasks`, `action_items`) with the
exact v2 schema rather than using bare `ALTER TABLE`, so the entity and database stay identical with
no `@ColumnInfo(defaultValue = …)` coupling:

```sql
-- target shape; the Kotlin Migration renames the old table, creates these, copies rows with
-- generated UUIDs, then drops the old tables
tasks(id INTEGER PRIMARY KEY NOT NULL, taskName TEXT NOT NULL, description TEXT NOT NULL,
      priority INTEGER NOT NULL, context TEXT NOT NULL, achieved INTEGER NOT NULL,
      creationTime TEXT NOT NULL,
      sync_id TEXT NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER, dirty INTEGER NOT NULL);

action_items(id INTEGER PRIMARY KEY NOT NULL, taskId INTEGER NOT NULL, description TEXT NOT NULL,
             achieved INTEGER NOT NULL,
             sync_id TEXT NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER,
             dirty INTEGER NOT NULL);

CREATE UNIQUE INDEX index_tasks_sync_id ON tasks(sync_id);
CREATE INDEX index_tasks_dirty ON tasks(dirty, updated_at);
CREATE UNIQUE INDEX index_action_items_sync_id ON action_items(sync_id);
CREATE INDEX index_action_items_dirty ON action_items(dirty, updated_at);
```

Backfill: assign `UUID.randomUUID()` to every task and activity, set `updated_at = now` on each row,
`dirty = 1` on every row so the first backup uploads the whole dataset, and convert `creationTime` to
UTC ISO-8601. Initialise the `sync_meta` DataStore with `last_backup_at = 0` and
`last_download_at = null`. Switch `exportSchema` to `true` and commit `schemas/2.json` so Room
validates the result when the database opens.

`TaskRepositoryImpl` is the single write path. It marks each row it changes `dirty = 1` with a fresh
`updated_at`. Deleting a task tombstones the task **and every child activity**; deleting an activity
tombstones that activity and marks the parent dirty when `achieved` changes. A delete is always a
tombstone (`deleted_at` set), never a row removal. A separate `purgeTombstones` job removes **client**
tombstones older than 30 days only after the server has confirmed the delete; the server keeps its own
tombstones for 90 days.

### New components

| Component | Responsibility |
| --- | --- |
| `data/remote/A2DApi` | Retrofit interface for auth, `sync/backup`, `sync/updates`, archives, export |
| `data/remote/dto/*` | Contract DTOs mirroring `a2d-tasks-v1.schema.json` |
| `data/auth/TokenStore` | Access/refresh tokens in `EncryptedSharedPreferences`, device ID, base URL |
| `data/sync/SyncEngine` | Orchestrate backup → download, apply page transactions, advance both watermarks, report state |
| `domain/sync/RowMerge` | Pure, unit-testable per-table row mapping/merge helpers (no Android deps) |
| `work/SyncWorker` | Login/app-start/periodic entry point, retry and backoff |
| `di/NetworkModule` | OkHttp/Retrofit/serialization, auth interceptor, 401 refresh |
| `ui/settings/SyncScreen` | Server URL, login/logout, `lastBackupAt`/`lastDownloadAt`, pending dirty counts, row-count check, sync now, auto-sync toggle, conflict log |

Login flow: Settings → server URL (prefilled from `BuildConfig.A2D_CLOUD_BASE_URL`, editable for
private deployments) → email/password → tokens stored → `SyncWorker.enqueueImmediate()`. Logging out
keeps local data and disables sync; "remove local copy" is a separate destructive action.

Disaster recovery on a device: install, sign in, and the first run has `last_download_at = null`, so
it performs a full download from the server replica (paged by `next`). To restore an older server
archive, the operator restores it server-side (§14); the device then full-downloads the restored
state or imports the downloaded JSON through the same validated import path — never by copying a
SQLite file.

Optional phase 2b: Android file import/export through the Storage Access Framework, reusing the same
contract and validation as the server.

## 13. Security and privacy

Threat model: a single operator hosts the service for themselves or a few known users; the data is
sensitive (ADHD/health-adjacent). Priorities are tamper resistance, tenant isolation, and not
leaking content.

- Passwords: Argon2id with per-user salt; generic login errors; rate limit per IP + per account.
- Tokens: short-lived access JWT (`sub`, `deviceId`, `jti`), rotating refresh tokens stored hashed;
  device revocation invalidates both; logout revokes the session.
- Tenant isolation: account task data lives in a separate `/data/users/<userId>/tasks.db`; a request
  opens exactly one tenant file from the token's `userId`. Control-plane queries are filtered by
  `user_id`; tests assert `404` (not `403`) for other users' IDs to avoid enumeration.
- Tenant filesystem: folders are `0700` and named by server-generated UUID; emails and task text never
  appear in paths. The tenant store validates the id pattern and that the resolved path stays under
  `/data/users` before opening, and refuses a symlinked tenant directory.
- Transport: TLS terminated at the Cloudflare edge; origin traffic travels outbound through the
  tunnel; HSTS enabled at the edge; secure cookies (`HttpOnly`, `SameSite=Lax`).
- Edge: WAF + rate limiting on `/v1/auth/login`, bot fight mode, TLS minimum 1.2, cache bypass for
  `/v1/*` and all HTML. Real client IP comes from `CF-Connecting-IP`; the app trusts proxy headers
  only from the tunnel network.
- Cloudflare Access is **optional and web-only**. If enabled, scope the Access application to the UI
  paths (`/`, `/tasks*`, `/import`, `/export`, `/archives*`, `/settings*`, `/login*`) and leave
  `/v1/*` outside it — otherwise the Android client receives an HTML login redirect instead of
  JSON. Protecting `/v1/*` instead requires per-device Access service tokens, which adds secret
  distribution to the app and is not in v1.
- CSRF: token on all browser mutations; JSON API uses `Bearer` and is not cookie-authenticated.
- Storage: volume-level encryption recommended; no task text in logs; request bodies never logged.
- Import safety: size/row caps, schema validation, reject unknown `formatVersion`, escape all
  rendered text (Jinja autoescape), CSV injection prefixing.
- Archives: authenticated download only; restore is CLI-only (§14) to prevent a compromised session
  from wiping the live DB.
- Account deletion revokes sessions and removes only that account's folder, control rows, and archived
  objects; `purge-user` retries a partial deletion.
- No third-party analytics or CDN assets in the web UI.
- Android: tokens in Keystore-backed storage; no token/Task content in logcat; optional certificate
  pinning left as a deployment choice.
- v1 has no E2EE: the operator can read everything. This must be stated in the deployment doc. E2EE
  would break the browser-editing requirement unless keys are managed in the browser.

## 14. Deployment and operations

Deployment assets live in `Deployment/a2d-cloud/` and are the canonical copy; this section summarizes
them.

```
Deployment/a2d-cloud/
├── README.md              # runbook: install, update, invite, backup, restore, rollback
├── .env.example           # deployment + application settings template
├── .gitignore             # .env, logs
├── lib.sh                 # shared shell helpers
├── deploy.sh              # validate, build venv, migrate, write supervisord drop-in, start
├── invite.sh              # create an invited account
├── backup.sh              # control DB + tenant DB snapshots + archives, with retention
├── restore.sh             # archive restore or full backup restore
├── supervisord.conf.example   # generated drop-in for the app + cloudflared programs
└── a2d-cloud.cron.example # nightly backup/archive/prune schedule
```

The service runs **without Docker**. `deploy.sh` creates a system user, a Python venv, prepares the
data directory, applies tenant migrations, and writes a supervisord drop-in at
`/etc/supervisor/conf.d/a2d-cloud.conf` that runs two programs:

- `a2d-app` — uvicorn serving the FastAPI app, listening on `A2D_BIND_HOST:A2D_BIND_PORT`
  (default `127.0.0.1:8000`, **localhost only**);
- `a2d-cloudflared` — the Cloudflare Tunnel client, outbound to Cloudflare.

Nothing publishes an inbound port; `cloudflared` dials out and the tunnel hostname is the only entry
point. No TLS certificate, reverse proxy, or dynamic DNS is needed on the host.

```bash
cd Deployment/a2d-cloud
cp .env.example .env && chmod 600 .env       # fill secrets; never commit
sudo ./deploy.sh                              # first install and every update
sudo ./invite.sh user@example.com
sudo ./backup.sh                              # timestamped snapshot under $A2D_APP_HOME/data/backups
sudo ./restore.sh archive <archiveId>         # stops app, restores, restarts
```

The deploy script is idempotent: it validates `.env`, checks the backend source, creates the system
user, installs the backend into a venv, prepares the data directory (owned by the service user),
runs tenant migrations as that user, writes the supervisord drop-in, reloads supervisord, and waits
for the app health check before reporting success. Runtime state lives in
`$A2D_APP_HOME/data` (default `../../a2d-cloud/data`), a host directory. The Docker path
`/data` from earlier drafts has been replaced by `A2D_DATA_DIR`, which defaults to the same host path.

Cloudflare-side setup (one time, in the existing account):

1. Add the domain to Cloudflare and create a Zero Trust tunnel (Networks → Tunnels).
2. Add a public hostname, e.g. `a2d.example.com` → service `http://app:8000`.
3. Copy the tunnel token into `.env` as `CLOUDFLARE_TUNNEL_TOKEN`.
4. Optional: enable Access for the **web paths only** (§13) and add WAF/rate-limit rules.

The template is `Deployment/a2d-cloud/.env.example`. Required values are `A2D_JWT_SECRET` (32+ random
bytes), `A2D_PUBLIC_BASE_URL`, and `CLOUDFLARE_TUNNEL_TOKEN`; the rest have safe defaults. `.env` is
mode `0600`, gitignored, and never baked into the image.

Operations:

- `./invite.sh user@example.com` (invite-only onboarding) creates the control rows and
  `$A2D_APP_HOME/data/users/<userId>/tasks.db` with the current tenant schema.
- `./deploy.sh` applies pending tenant migrations before starting the new image; for a manual run,
  `runuser -u $A2D_RUN_USER -- env -C $A2D_APP_HOME $A2D_VENV_DIR/bin/python -m app.cli migrate-users`.
- `runuser -u $A2D_RUN_USER -- env -C $A2D_APP_HOME $A2D_VENV_DIR/bin/python -m app.cli archive --user …`
  creates a manual archive; `./restore.sh archive <archiveId>` stops the app, restores through the
  CLI lock, and restarts it.
- `runuser -u $A2D_RUN_USER -- env -C $A2D_APP_HOME $A2D_VENV_DIR/bin/python -m app.cli purge-user --id …`
  retries removal of an inactive account's folder and rows.
- Daily `prune` job (iterates tenants): expired tokens, tombstones older than
  `A2D_TOMBSTONE_RETENTION_DAYS`, archives beyond retention, and idempotency keys older than 24 h.
  Run it from host cron (`$A2D_VENV_DIR/bin/python -m app.cli prune`), not inside a multi-worker
  server; `a2d-cloud.cron.example` has the schedule.
- Daily `archive` job (hash-gated): one archive per user if that user's canonical dataset changed
  (`app.cli archive --all`).
- Backup: `./backup.sh` invokes `app.cli ops-backup`, which writes a timestamped recovery set under
  `$A2D_APP_HOME/data/backups/`: a consistent `VACUUM INTO` snapshot of the control DB and every
  tenant `tasks.db`, plus tenant archives and `meta.json`, with a checksummed manifest and retention
  (`A2D_BACKUP_KEEP`, default 14). Backups are written by the service user, so inspect them with
  `sudo` when needed. `./restore.sh backup <backupId>` invokes `app.cli restore-backup`, which verifies
  the manifest, leaves the previous state in `$A2D_APP_HOME/data/pre-restore-*`, re-stamps every
  restored row, and tombstones rows absent from the backup so every device re-downloads the restored
  state. A non-SQLite control plane (`A2D_DATABASE_URL`) must be dumped separately with `pg_dump`.
- Health: `/healthz` (process) and `/readyz` (control DB, `$A2D_DATA_DIR` writable, tenant root
  readable), reachable through the tunnel; `./deploy.sh` waits for the app health check.
- Capacity: monitor free disk, open file descriptors, and the tenant cache hit rate. Each active
  tenant holds one SQLite connection; the default deployment runs a single uvicorn worker.
- Logs: structured JSON, request ID, status, latency, user ID — never task text.
- Host firewall: deny all inbound; allow outbound 443/7844 for `cloudflared`. No TLS certificates,
  open 80/443, or dynamic DNS are needed on the host.
- Sync/import calls must finish under Cloudflare's ~100 s origin timeout (524). Backup batches and
  download pages are capped, and archive generation streams to disk; the 10 MB import cap stays well
  inside the edge limit.
- Access to the box from the LAN is out of scope; the tunnel hostname is the only supported entry
  point.

## 15. Phased delivery

| Phase | Deliverable | Estimate |
| --- | --- | --- |
| P0 | `a2d-cloud/` skeleton, control DB models/migrations, tenant provisioning + `TenantStore`, auth, contract schema, supervisord deployment + `Deployment/a2d-cloud/` env/scripts/drop-in + Cloudflare Tunnel | 4–5 d |
| P1 | `/v1/sync/backup` + `/v1/sync/updates` + per-table `server_updated_at` union keyset + row LWW + tombstones + idempotency + batching + full download + final row counts + pytest coverage | 3–4 d |
| P2 | Android Room v2 migration, contract DTOs, SyncEngine (backup → download, compare-and-clear apply, row-count check), TokenStore, WorkManager, **login UI with 'Continue with email' button** | 4–5 d |
| P3 | Web UI (read-only list/detail) + **required** JSON/CSV import/export with dry-run | 3 d |
| P4 | Server-side archives (daily/manual), retention, verify, CLI restore | 2 d |
| P5 | Hardening: rate limits, CSRF, retention jobs, tenant migration/quota tooling, docs, runbook, migration/contract tests | 3 d |
| **Total** | | **~19–23 dev-days** |

Each phase lands behind a build flag; P0–P1 can ship without touching the app.

## 16. Test plan

Server (`pytest`, `httpx.AsyncClient`, temp control DB + temp tenant root):

- [ ] Auth: login, refresh rotation, revoked device, rate limit, generic errors.
- [ ] Isolation: user B cannot read/update/delete user A's task, activity, archive; returns 404.
- [ ] Provisioning: `create-user` creates a `0700` folder, control rows, and a `tasks.db` at the
      current schema; repeating it is idempotent; a forced failure leaves no folder behind.
- [ ] Tenant store: the token's `userId` selects the file; a body/path `userId` is ignored; an
      invalid id, `..`, absolute path, or symlinked tenant directory is rejected.
- [ ] Tenant migrations: versioned files apply in order, rerun is a no-op, a newer `schema_version`
      returns `503`, and one tenant's failure does not advance another's version.
- [ ] Quotas: backup/import/archive over `A2D_USER_QUOTA_BYTES` returns `413` with usage and does not
      affect other tenants.
- [ ] Deletion: account delete removes only that tenant folder and its control rows; `purge-user`
      completes a partial delete.
- [ ] Concurrency: two users syncing in parallel use separate files and do not block each other.
- [ ] Ops backup: a `VACUUM INTO` tenant snapshot and a control-DB backup restore logins, devices, and
      tasks for the intended account only; a full backup restore re-stamps restored rows and writes
      tombstones for rows absent from the backup, so a client with a newer download token converges.
- [ ] Ops backup failure: a non-SQLite `A2D_DATABASE_URL` is rejected instead of writing an
      incomplete recovery set; a checksum mismatch aborts before live data is moved.
- [ ] Backup: per-table insert, update, stale rejection, equal-timestamp device tie-break,
      equal-version idempotent replay, future-clock clamp, tombstone propagation and resurrection by
      a newer edit, task-before-activity FK order, task tombstone cascading to live children,
      batch size/`413` behaviour, `429` with `Retry-After`, idempotent retry with the same key.
- [ ] Download: incremental page after a token, equal-`server_updated_at` paging by
      `(table_rank, syncId)`, parent task before child activity within a stamp, `hasMore`/`next`
      termination, tombstones returned per table, full download with `full=true`, per-table
      absence-as-deletion, `410` for an expired token, idempotent apply.
- [ ] Row counts: the final page reports `live`/`deleted` per table; a dropped backup batch or a
      skipped download page produces a count mismatch; `live` excludes tombstones and is unaffected
      by the 30/90-day retention difference.
- [ ] Watermarks: a backup accepted twice does not bump `server_updated_at`; an import or web edit
      does; a second no-op run returns empty `tables`.
- [ ] Import: valid JSON, invalid schema, unknown `formatVersion`, duplicate `syncId` under each
      policy, CSV header variants, 10 MB/row caps, dry-run writes nothing.
- [ ] Export: deterministic ordering, round-trip equals re-import, CSV formula escaping.
- [ ] Archives: hash verify, unchanged-hash skip, retention pruning, CLI restore reproduces counts
      and stamps all restored rows so devices re-download.
- [ ] Contract: fixtures validate against `a2d-tasks-v1.schema.json`; schema hash matches Android.
- [ ] Edge/proxy: `CF-Connecting-IP` honoured for rate limiting, spoofed `X-Forwarded-For` ignored,
      `/v1/*` reachable when Access protects only the UI paths, 10 MB import cap enforced.

Android (JVM, matching the existing suite style):

- [ ] `RowMerge`: row DTO → entity with local ID allocation that never collides with `id*10+n`;
      existing `syncId` keeps its local id.
- [ ] Sync apply: new/updated/deleted/rejected task and activity rows applied in table order,
      `lastDownloadAt` persisted only after a page commits, gate recomputation after apply.
- [ ] `lastBackupAt` advances only after the last accepted batch; a mid-run edit keeps
      `updated_at > cutoff` and `dirty = 1`.
- [ ] Compare-and-clear: a local edit during sync leaves `dirty = 1` instead of being overwritten;
      a server-clamped accepted timestamp is written back locally; a local edit newer than a server
      tombstone is kept and re-pushed (resurrection).
- [ ] Full download reconciles absence per table (children before tasks or via cascade) and keeps
      dirty rows.
- [ ] Row-count check: matching counts for both tables → `Success`; a deliberate mismatch with no
      pending dirty rows → `RowCountMismatch` and a full download on the next run; pending dirty rows
      defer the check.
- [x] Room migration 1→2 with Robolectric: UUID backfill on tasks and activities, `dirty=1` on both,
      UTC conversion, unique indexes, `last_backup_at = 0`/`last_download_at = null`, and a
      successful open against exported `schemas/2.json`.
- [ ] Dirty marking on every repository mutation in both tables; deleting a task tombstones its
      children; server-applied rows stay `dirty = 0`.
- [ ] WorkManager retry/backoff and no-op when signed out.

Manual:

- [ ] Two devices + browser: create/edit/delete on Android, export and re-import on the web, then
      converge after sync; live `tasks` and `action_items` counts match on both devices and the cloud.
- [ ] Airplane mode edits, reconnect, no data loss; conflict notice shown.
- [ ] Restore an archive into a fresh account and compare SHA-256 of export.
- [ ] Take a device offline past the tombstone window, delete a task elsewhere, reconnect: the
      device receives `410`, full-downloads, and does not resurrect the deleted task.
- [ ] Login with wrong password, expired token, revoked device, unreachable server.
- [ ] Reach the app only via the Cloudflare hostname; confirm no host port is listening and the
      tunnel reconnects after a network drop.

## 17. Affected files

Android:

- `A2D/app/src/main/AndroidManifest.xml`
- `A2D/app/build.gradle.kts`, `A2D/gradle/libs.versions.toml`
- `A2D/app/src/main/java/com/a2d/app/data/local/{A2DDatabase.kt,entity/*,dao/TaskDao.kt}`
- `A2D/app/src/main/java/com/a2d/app/data/repository/TaskRepositoryImpl.kt`
- `A2D/app/src/main/java/com/a2d/app/domain/model/{Task.kt,ActionItem.kt}`
- `A2D/app/src/main/java/com/a2d/app/ui/{MainActivity.kt,task/TaskViewModel.kt}`
- new `data/remote/*`, `data/auth/*`, `data/sync/*`, `domain/sync/*`, `work/*`, `ui/settings/*`
- tests: `A2D/app/src/test/java/com/a2d/app/{RowMergeTest,SyncApplyTest,RoomMigrationTest}.kt`

Cloud (new): `a2d-cloud/**` as laid out in §10. Deployment: `Deployment/a2d-cloud/{README.md,.env.example,.gitignore,supervisord.conf.example,deploy.sh,invite.sh,backup.sh,restore.sh,lib.sh,a2d-cloud.cron.example}`.

## 18. Docs to update

- [ ] `README.md` — architecture, toolchain, repository map, run commands (on implementation)
- [ ] `AGENTS.md` — new file map, sync boundaries, "never edit gate semantics for sync" rule
- [ ] `docs/adr/0002-cloud-sync-architecture.md` — accepted decision record (created 2026-11-16)
- [ ] `Deployment/a2d-cloud/` — supervisord drop-in, env template, deploy/invite/backup/restore scripts, cron example, and runbook (deploy, invite, archive/restore, TLS, retention)
- [ ] `CHANGELOG.md` — 1.1.0 entry
- [ ] `docs/history/implementation-log/YYYY-MM.md` — verified commit only after green tests
- [ ] `docs/known-issues.md` — if LWW conflicts or clock skew become recurring
- [ ] `docs/INDEX.md` — add this record (done) and the runbook/ADR when created

## 19. Risks and rollback

| Risk | Mitigation |
| --- | --- |
| LWW silently discards a concurrent edit | Row-level merge, explicit `rejected` list with the authoritative server row, conflict log in Settings; row granularity keeps loss small |
| Room migration corrupts data | Table recreation with exported schema, Robolectric migration test, automatic pre-migration file copy to app storage |
| Upload watermark drifts or the device clock rolls back | `dirty = 1` is the real upload selector, not a raw timestamp range; `lastBackupAt` is only the run cutoff; compare-and-clear prevents confirming a row edited mid-run; the server clamps future timestamps and the client adopts the clamped version |
| Download watermark expires or a restore invalidates old positions | `410 full_download_required` clears the token and triggers a full download; absence is reconciled as deletion while dirty rows are preserved |
| Accepted rows echo back forever | A row applied from the server is stored `dirty = 0`; an idempotent backup replay does not bump `server_updated_at`; tests assert a second run transfers nothing |
| A server write forgets to bump `server_updated_at` | One `touch_row()` helper per syncable table is the only way to mutate a row (import, web CRUD, restore, backup); a test asserts each path advances the download feed |
| A request copies `tasks` but fails before `action_items` | Both tables are applied in one tenant transaction, tasks first; a failed request rolls back every row and is retried with the same idempotency key |
| A task and an activity row from different devices produce an inconsistent gate result | Both rows are accepted independently, then `achieved` is recomputed from the resulting activity set during apply; a task tombstone still removes the whole task |
| Row counts diverge after a run | The final page's live counts are compared per table; a mismatch with no pending dirty rows fails as `RowCountMismatch` and schedules a full download, never an automatic delete |
| Archive restore leaves devices with stale rows | Restore stamps every restored row and writes tombstones for rows absent from the archive, so the normal download converges every device |
| Tenant migration half-applied or one tenant lags | Versioned SQL in one transaction per tenant under a `.lock`; `user_state.schema_version` gates opening; `migrate-users` reports per-tenant results and a newer version returns `503` |
| Many tenants exhaust file descriptors or disk | LRU engine cache with idle disposal, per-user quota, `/readyz` checks, and disk/FD alerts; the target scale is a handful of accounts |
| Path traversal or symlink escape via a tenant id | Server-generated UUIDs, strict pattern check, resolved-path containment check under `/data/users`, and refusal of symlinked tenant directories |
| Losing `control.db` loses logins but not task data (or vice versa) | Back up the control DB and all tenant snapshots as one recovery unit; archives alone cannot restore accounts |
| Noisy-neighbour I/O between tenants | Per-tenant files bound the blast radius; SQLite `busy_timeout` and per-user request limits keep one device from monopolising the process |
| Token theft | Short access TTL, rotating refresh, revocation, Keystore storage, TLS only |
| Server compromise exposes sensitive tasks | Minimal host, volume encryption, no body logging, invite-only, Cloudflare WAF, optional Access, account delete/export |
| Raw `.db` restore temptation | Not implemented; restores are canonical JSON through the same validated importer |
| Import ID collisions | `syncId` identity + global `max(id)+1` allocation; never reuse `id*10+n` |
| Sync batch recompute exposes the shared arity-keyed gate pins (two tasks with equal arity share a bucket) | Serialise gate recompute on one mutex during apply; follow-up fix namespaces pins by task `syncId`; gate state is never persisted or synced |
| Scope creep into real-time sync | Non-goals are explicit; LWW only in v1 |
| Cloudflare outage or tunnel disconnection blocks cloud access | App stays offline-first and queues changes; tunnel runs `restart: unless-stopped`; Cloudflare status checked before blaming the app; direct LAN access is not a supported fallback in v1 |
| Cloudflare Access misconfiguration returns HTML to the app | Access is scoped to UI paths only; `/v1/*` stays outside it; a smoke test asserts JSON error responses from a signed-out client |

Rollback:

- Server can be stopped with zero impact on the app; local Room data is authoritative offline.
- Android: `SYNC_ENABLED` build flag off hides Settings and never schedules the worker; the v2
  migration recreates the tables and is forward-only — a downgrade needs a fresh install (documented).
- Delete `a2d-cloud/` and `Deployment/a2d-cloud/` to abandon the backend; the app still builds and runs.

## 20. Decision log

Approved by the project owner on **2026-11-16**; recorded in
[ADR 0002](../adr/0002-cloud-sync-architecture.md).

| # | Question | Resolution |
| --- | --- | --- |
| Q1 | Hosting / ingress | Supervisord on a small VPS or home host behind an **existing Cloudflare account using Cloudflare Tunnel**; Caddy removed, no inbound ports |
| Q2 | Account model | **Invite-only email + password** (Argon2id, rotating JWTs); no OIDC in v1 |
| Q3 | Default database | **Control plane SQLite WAL** (Postgres optional); each account's task data is its own `tasks.db` file |
| Q4 | Web UI stack | **FastAPI + Jinja2 + HTMX** |
| Q5 | Import/export scope | **Cloud JSON/CSV import and export required in v1**; Android file import/export (SAF) deferred to 1.1.x |
| Q6 | Web write scope | **Read-only task browsing in v1**; import/export are the only web write paths; direct editing in 1.1 |
| Q7 | End-to-end encryption | **Not in v1**; volume encryption only; revisit only if the browser requirement changes |
| Q8 | Raw `.db` artifacts | **No**; logical canonical JSON only |
| Q9 | Cloudflare Access | **Optional, web UI paths only**; `/v1/*` stays outside Access so the app receives JSON, not a login redirect. Off by default |
| Q10 | Sync model | **Cloud sync = cloud backup + download cloud update**, implemented as table-to-table row copy both ways for `tasks` and `action_items`. Upload sends dirty rows (`lastBackupAt` cutoff); download returns rows after `lastDownloadAt`. UUID `syncId` is preserved and local numeric IDs stay local. Exactly two checkpoints; no `change_log`, sequence cursor, or `fullSnapshot` request. Live row counts per table are the final check |
| Q11 | Multi-user storage | **One folder and one authoritative `tasks.db` per registered account** under `/data/users/<userId>/`; a control DB holds identity, devices, tokens, archive metadata, and quotas. Sync state that affects correctness lives in database tables; `meta.json` is derived ops metadata only |

Deferred to later releases: direct web editing (1.1), Android SAF import/export (1.1.x), Access
service tokens for the app, multi-user invite UI, E2EE, real-time collaboration.

## 21. Acceptance checklist

- [x] ADR 0002 accepted and linked here
- [ ] `Deployment/a2d-cloud/deploy.sh` deploys with one command; app is reachable only through the Cloudflare tunnel hostname
- [ ] Contract schema shared and validated by both sides
- [ ] Android Room v2 migration tested; 11 existing tests still pass
- [ ] Login → incremental backup + download verified on two devices
- [ ] Browser read-only views work; **cloud JSON and CSV import/export round-trip verified**
- [ ] A no-op second sync uploads zero rows and downloads zero rows
- [ ] Live `tasks` and `action_items` row counts match between the device and the cloud after a completed sync
- [ ] Archive download, verify, and CLI restore verified; restore re-stamps rows for device convergence
- [ ] `410` → full download verified after the tombstone window
- [ ] Each registered account has `0700` `/data/users/<userId>/tasks.db`; deleting it leaves other accounts intact
- [ ] No task content in logs; cross-user access returns 404
- [ ] `/v1/*` returns JSON when Cloudflare Access is enabled on the UI paths
- [ ] README, AGENT, CHANGELOG, runbook, and implementation log updated
