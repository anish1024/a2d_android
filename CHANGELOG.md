# Changelog

All notable changes to A2D (Adult ADHD Assistant) are recorded here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/).

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-18
- Last verified against: code baseline `8d9563d`

## [Unreleased]

### Added

- **Cloud sync (FEAT-0002) — Room v2 migration (Android P2, step 2).** Added sync metadata columns
  to `tasks` and `action_items`: `sync_id` (UUID), `updated_at` (LWW stamp), `deleted` (tombstone
  flag), and `dirty` (unsynced flag).
- `RoomMigration.MIGRATION_1_2` with `ALTER TABLE` column adds + UUID/`updated_at` backfill and
  `dirty = 1` for every existing row, so the first backup uploads the full dataset.
- Room schema export enabled (`exportSchema = true`, Room Gradle plugin); committed
  `A2D/app/schemas/com.a2d.app.data.local.A2DDatabase/2.json`.
- `RoomMigrationTest` (Robolectric + `room-testing`) covering the v1→v2 migration: data preservation,
  new columns, and backfill. **Note:** the test compiles and is wired into `testDebugUnitTest` but
  currently fails on this ARM64 host because Robolectric needs a native `conscrypt` library that is
  not available (environment limitation, not a code defect); the other 11 unit tests pass.

### Changed

- `A2D/app/src/main/java/com/a2d/app/ui/MainActivity.kt`: added a `TopAppBar` on the `taskList` route
  with a 3-dot overflow menu (`Icons.Filled.MoreVert`). The menu contains the **Sync** action (with a
  `Refresh` leading icon), wired to the placeholder `TaskViewModel.syncNow()` that returns a status
  string. This keeps the dashboard header clean and avoids misaligning the milestone / Add Task row.
- `A2D/app/src/main/java/com/a2d/app/ui/task/TaskListScreen.kt`: removed the standalone "Sync" button
  from the header; only the icon+text **Add Task** button remains there. The screen now accepts a
  `modifier` parameter so the host `Scaffold` can apply its inner padding.
- `TaskViewModel.syncNow()` now returns a `String` status message so the menu can show feedback
  ("Cloud sync is pending backend integration") until the real `SyncEngine` is wired in.
- `A2DDatabase` bumped from version 1 → 2 and now exposes a singleton `getDatabase(context)` that
  installs `MIGRATION_1_2`; `AppModule` delegates to it.
- `TaskEntity` / `ActionItemEntity` gain `syncId`, `updatedAt`, `deleted`, `dirty` fields (defaults
  added so existing call sites compile unchanged).

### Fixed

- Cloud deployment: `deploy.sh` now flattens the `a2d-cloud/` tarball wrapper and chowns the target;
  `lib.sh` creates the service group before the user and uses `ensurepip` for a working pip on
  Python 3.13; `A2D_BIND_HOST` defaults to `0.0.0.0`. See `Deployment/a2d-cloud/README.md` §13.

### Removed

### Security

## [1.0.0] — 2026-11-06 — versionCode 1

Initial Android release. The app version is defined in `A2D/app/build.gradle.kts`.

### Added

- Task list with per-task cards, activity checkboxes, and delete confirmations.
- Task detail screen with completion badge and activity management.
- Hierarchical two-tier AND-gate completion engine:
  - Tier 1 latches each task's activities.
  - Tier 2 evaluates the task list into a milestone output.
- Room persistence with `TaskEntity` / `ActionItemEntity` cascade relationships.
- Hilt dependency injection for the database, DAO, repository, and gate processors.
- Jetpack Compose + Material 3 UI with task list, task detail, and logic-gate screens.
- Standalone logic-gate playground for manual pulse testing.

### Security

- Release signing uses the local `A2D/a2d-release-key.jks` referenced by `A2D/keystore.properties`;
  the keystore is not committed.

### Release notes (user-facing)

Initial release of A2D: create tasks, break them into activities, and track completion with a
hierarchical AND-gate progress system. Includes a logic-gate playground for testing gate behavior.

### Build & verification

- Code baseline: `8d3b7f4`
- Commit: `pending` — no release commit/tag has been created
- Tests: `./gradlew test` → 11 tests (recorded baseline in README §6)
- Build: `./gradlew assembleDebug` → `A2D/app/build/outputs/apk/debug/app-debug.apk`
- Release status: Play Store submission pending
