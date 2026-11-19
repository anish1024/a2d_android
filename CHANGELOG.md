# Changelog

All notable changes to A2D (Adult ADHD Assistant) are recorded here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/).

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-18
- Last verified against: code baseline after REDESIGN-02 round 6 (task description dedupe + Android scrollbar fix)

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
- **Task card completion (FEAT-0001 / redesign-02 round 3).** Restored the missing task/activity
  edit operations and the per-card affordances from the FEAT-0001 spec + Option B:
  - `TaskViewModel` gained `updateTask(taskId, newName, newDescription)` and
    `updateActivity(taskId, activityId, newDescription)`; `MainActivity` wires them to the list screen.
  - `TaskListScreen.TaskCard` gained inline edit mode (Edit/Done icon + name/description fields) and a
    per-card 3-dot overflow menu (**Edit Task**, **Export CSV**, **Import CSV**, **Move Up**, **Move Down**, **Delete**);
    header tap toggles the activity body with internal scrolling.
  - **Drag-and-drop reordering.** A `DragHandle` composable (using `detectVerticalDragGestures`) lets
    users reorder items by dragging up/down:
    - A drag handle icon appears **before the task name** in each task card header; drag gestures
      trigger `TaskViewModel.moveTask(taskId, up/down)`.
    - A drag handle icon appears **before each activity** in the activity list; drag gestures trigger
      `TaskViewModel.moveActivity(taskId, activityId, up/down)`.
    - Tasks are reordered by swapping their `priority` values in Room; activities are reordered by
      swapping `description`/`achieved` while preserving stable IDs.
    - The task-level **Edit** button was removed from the header and moved into the 3-dot menu as
      **Edit Task**, so the header row now shows drag handle + title + overflow menu only.
  - CSV export/import and move up/down are placeholders (visible Toast feedback) until backend/SAF wiring.
- `A2DDatabase` bumped from version 1 → 2 and now exposes a singleton `getDatabase(context)` that
  installs `MIGRATION_1_2`; `AppModule` delegates to it.
- `TaskEntity` / `ActionItemEntity` gain `syncId`, `updatedAt`, `deleted`, `dirty` fields (defaults
  added so existing call sites compile unchanged).

### Fixed

- Task card duplicate description: the task description is now rendered exactly once per task card
  (in the card header, directly below the task name); the redundant copy previously shown in the
  expanded activity body was removed.
- Activity list scrollbar: the `VerticalScrollbar` / `rememberScrollbarAdapter` APIs turned out to be
  Compose Desktop-only and are not part of the Compose-for-Android artifact, so the build failed with
  `Unresolved reference`. Replaced with a custom `TaskActivityScrollbar` composable driven off
  `LazyListState` (proportional thumb) that renders only when the list is scrollable — visible scrollbars
  now work on Android devices.
- Task drag-and-drop reorder (REDESIGN-02 round 5) and Edit Task/Save task flow (round 5) remain
  fixed on top of the task card redesign.

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
