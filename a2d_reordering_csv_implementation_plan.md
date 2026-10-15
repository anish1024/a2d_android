# A2D: Step-by-Step Implementation Guide for Persistent Reordering and Activity CSV

**Purpose:** Give a coding agent such as OpenCode using Qwen 3.4 9B a
precise, bounded implementation procedure for adding persistent
task/activity reordering and activity CSV import/export to the existing
A2D Android app.

**Project root:** `A2D/` (the repository's Android Gradle project
directory; do not rename it to `A2DApp/`).

**Status at guide creation:** These are proposed features. The
implementation log records the existing baseline as 15/15 unit tests
passing and a successful debug build. Do not describe the new features
as implemented until their tests and build pass.

------------------------------------------------------------------------

## 1. Read these files before changing code

Read the files in this order:

1.  `./docs/Graph_Handbook.md` --- rules for editing the YAML graph contract.
2.  `./a2d_graph_reordering_csv_contract.yaml` --- intended architecture
    and node-level contract for these features.
3.  `./a2d_reordering_csv_implementation_plan.md` --- current implementation history
    and verified baseline.
4.  `./README.md` --- existing project instructions and documented
    behavior.
5.  Existing Kotlin source and tests named in Section 3.

If the repository uses different filenames or locations for these
documents, locate them and report the actual paths before proceeding. Do
not fabricate missing source files.

### Authority and conflict rules

Use these sources in this order:

1.  `./docs/Graph_Handbook.md` for what is legal in the YAML graph schema.
2.  `./a2d_graph_reordering_csv_contract.yaml` - The revised YAML contract for the intended feature design.
3.  The implementation log `./a2d_implementation_log.md` and actual current code for existing behavior
    and exact paths.
4.  This guide for implementation sequence and acceptance checks.

If the YAML and actual source conflict, stop and report the precise
conflict before changing architecture. Do not silently rewrite unrelated
code. Do not add unsupported YAML keys, protocols, layouts, or type
descriptors.

### Required agent behavior

-   Work in small phases. Finish and verify one phase before starting
    the next.
-   Inspect actual code before editing it. Do not replace whole files
    just because a change is needed.
-   Keep existing task editing, activity editing, completion/progress
    display, delete confirmations, navigation, and AND-gate behavior
    intact.
-   Do not make unrelated formatting, refactoring, dependency upgrades,
    or UI redesign changes.
-   Do not add a CSV library or any other dependency until checking
    whether the project already has a suitable one. If a dependency is
    needed, justify it and keep it minimal.
-   Never claim a build or test passed unless you ran the command and
    observed its exit status.
-   After each phase, list files changed, commands run, results, and
    unresolved issues. Update/Append this progress report into `a2d_implementation_log.md`.
-   If a key decision cannot be established from the source, stop and
    ask rather than guess.

------------------------------------------------------------------------

## 2. Approved feature decisions (do not change)

### Reordering

-   Users can reorder tasks in the task list.
-   Users can reorder activities within a task.
-   Primary interaction: drag-and-drop using a dedicated drag handle.
-   Accessible alternative: **Move Up** and **Move Down** actions. Do
    not make dragging the only way to reorder.
-   Order is persistent and separate from database IDs and task
    priority.
-   The task list and task detail/card activity lists must display the
    same persisted activity order.
-   Persist only when a drag ends or a Move Up/Down action is selected;
    do not write to Room on every pointer movement.
-   A reorder request must be rejected if the submitted IDs are not an
    exact permutation of the current items (for example, duplicates,
    missing IDs, IDs belonging to another task, or stale IDs).

### CSV import/export

-   CSV actions are available from both the task card and Task Detail
    screen.
-   Export columns, in this exact order:
    `position,description,achieved,id`.
-   Import requires `position`, `description`, and `achieved`. The `id`
    column is optional and informational only.
-   Imported activities are **appended** to the selected task. Existing
    activities are never replaced or deleted by CSV import.
-   Imported rows receive fresh local database IDs. Never reuse the
    source CSV `id`.
-   Parse and validate the complete file before writing anything.
-   Show the user a preview/count and require confirmation before
    appending.
-   Cancellation, malformed CSV, validation failure, or failed
    transaction must not partially mutate the selected task.
-   Use Android's Storage Access Framework (SAF): a document picker for
    import and a create-document flow for export.
-   CSV handling must support quoted commas, escaped quotes, UTF-8 text,
    and embedded newlines. Never parse CSV by simply splitting on commas
    or lines.

------------------------------------------------------------------------

## 3. Existing code map

The implementation log identifies the following existing files under
`A2D/app/src/main/java/com/a2d/app/`:

  -----------------------------------------------------------------------------------------
  File                                      Current responsibility  Planned work
  ----------------------------------------- ----------------------- -----------------------
  `data/local/entity/TaskEntity.kt`         Room task row           Add persisted task
                                                                    `position`.

  `data/local/entity/ActionItemEntity.kt`   Room activity row       Add persisted activity
                                                                    `position`.

  `data/local/dao/TaskDao.kt`               Room reads/writes and   Add transactional
                                            task/activity           ordering and append
                                            replacement transaction operations; ensure
                                                                    reads have
                                                                    deterministic ordering.

  `data/local/A2DDatabase.kt`               Room database version 1 Raise to version 2 and
                                                                    register migration 1→2.

  `data/repository/TaskRepositoryImpl.kt`   Maps Room records to    Map and sort by
                                            domain `Task` and       persisted position;
                                            `ActionItem`; currently implement validated
                                            sorts activities by ID  transactional
                                                                    operations.

  `ui/task/TaskViewModel.kt`                Task state,             Add task/activity
                                            task/activity           reorder and CSV
                                            operations, progress    orchestration
                                            and gate recalculation  operations.

  `ui/task/TaskListScreen.kt`               Editable/collapsible    Add task reorder UI,
                                            task cards and activity activity reorder UI,
                                            rows                    CSV actions, and
                                                                    accessible
                                                                    alternatives. Preserve
                                                                    inline edit/focus
                                                                    fixes.

  `ui/task/TaskDetailScreen.kt`             Full-screen             Add activity reorder UI
                                            task/activity view and  and CSV actions.
                                            activity inline editing 

  `ui/MainActivity.kt`                      Navigation and          Wire new
                                            ViewModel-to-screen     callbacks/actions only
                                            callback wiring         where needed.

  `domain/model/Task.kt`                    Domain model with       Usually no new fields
                                            `isCompleted` and       needed; preserve
                                            `progressPercent`       current completion/gate
                                                                    distinction.
  -----------------------------------------------------------------------------------------

The implementation log also identifies tests under
`A2D/app/src/test/java/com/a2d/app/`, including `TaskDomainTest.kt`,
`SerialAndGateProcessorTest.kt`, and `IntegratedGateTaskFlowTest.kt`.

**Important:** Confirm the actual repository paths and signatures before
editing. The table is based on the implementation log, not a live
filesystem scan.

### New production file

Create a dedicated codec, using the existing package conventions:

-   Suggested path:
    `A2D/app/src/main/java/com/a2d/app/data/csv/ActivityCsvCodec.kt`
-   If the project does not use a `data/csv` package convention, choose
    the smallest consistent package and document the actual path.
-   The codec should parse/serialize CSV data and validate the CSV
    shape. It should not access Room, Android UI, or a ViewModel.

Do not create separate file-picker helper classes unless the existing
screen architecture makes them necessary. Prefer the simplest
maintainable change.

------------------------------------------------------------------------

## 4. Phase 0 --- Inspect and establish a safe baseline

**Do not edit source files in this phase.**

1.  Confirm the working directory and locate the Android project:

    ``` bash
    pwd
    ls
    cd A2D
    ```

2.  Read the source files listed in Section 3.

3.  Inspect `TaskDao.getAllTasks()` and determine the exact current
    task-list ordering. The implementation log explicitly records
    activity ordering by ascending activity ID, but does not establish
    the task-list ordering rule sufficiently for migration. Do not
    assume it.

4.  Inspect the task/activity mapping in all repository read paths:

    -   `getAllTasks()`
    -   `getTaskSnapshot()`
    -   `getTask(id)`

5.  Inspect `TaskViewModel` mutation methods and gate semantics:

    -   `toggleActivity`
    -   `addActivity`
    -   `deleteActivity`
    -   `deleteTask`
    -   `updateTask`
    -   `updateActivity`
    -   pipeline recalculation and current `milestoneOutput`

6.  Inspect how `TaskListScreen` and `TaskDetailScreen` share
    `ActivityRow`, hoist edit state, and wire callbacks through
    `MainActivity`.

7.  Inspect `app/build.gradle.kts`, `gradle/libs.versions.toml`,
    existing test dependencies, Room configuration, and Android
    `minSdk`.

8.  Run baseline commands from `A2D/`:

    ``` bash
    ./gradlew test
    ./gradlew assembleDebug
    ```

9.  Record exact test counts, build result, and any pre-existing
    failures.

**Phase 0 exit criteria**

-   The current task-list ordering rule is known and written down.
-   Existing tests/build results are recorded.
-   Exact source paths and method signatures are known.
-   No source files have changed.

If a baseline command fails, report whether it appears pre-existing and
diagnose it before implementing features.

------------------------------------------------------------------------

## 5. Phase 1 --- Add persistent position fields and Room migration

### Step 1.1: Update Room entities

In `TaskEntity.kt`:

-   Add an integer `position` field.
-   Preserve all existing columns and meanings: `id`, `taskName`,
    `description`, `priority`, `context`, `achieved`, and
    `creationTime`.
-   Do not use `position` as the primary key or alter the task ID.

In `ActionItemEntity.kt`:

-   Add an integer `position` field.
-   Preserve `id`, `taskId`, `description`, `achieved`, the foreign key
    cascade, and the task index.
-   Do not use `position` as the activity ID.

Check all entity constructors and mapping sites after adding fields;
update every required constructor call.

### Step 1.2: Add Room migration 1→2

In `A2DDatabase.kt`:

-   Change database version from `1` to `2`.
-   Add a `Migration(1, 2)` implementation.
-   Register it in the existing `Room.databaseBuilder(...)` in
    `di/AppModule.kt`.
-   Do not use destructive migration or delete the database.
-   Preserve all tasks, activities, IDs, completion flags, descriptions,
    and task metadata.

Migration requirements:

1.  Add a non-null `position` column to `tasks`.
2.  Add a non-null `position` column to `action_items`.
3.  Backfill positions deterministically.
4.  Task positions must preserve the legacy visible task-list order
    confirmed in Phase 0.
5.  Activity positions must preserve the legacy ascending activity-ID
    order documented in the implementation log.
6.  Positions should be contiguous from zero within each list where
    practical.
7.  The migration must be safe for a database containing zero, one, or
    many tasks/activities.

SQLite migration implementation must be compatible with the app's
Android SQLite versions. Prefer simple SQL supported by the project's
minimum Android version. Do not assume a window function is available
without checking compatibility. If using correlated updates, verify the
SQL against the actual table and column names.

**Do not guess the legacy task order.** If the old task list has no
explicit order, determine the effective behavior from the existing query
and app behavior, document the chosen deterministic migration order, and
avoid claiming it preserves a guaranteed order that was never specified.

### Step 1.3: Register migration

Find the current `Room.databaseBuilder` call. Add
`.addMigrations(MIGRATION_1_2)` using the project's existing database
initialization pattern. Avoid adding a second database
singleton/provider.

### Step 1.4: Add migration tests

Use the existing test setup and Room testing dependency if already
present. If migration testing requires a dependency, add only the
minimal compatible test dependency and explain why.

Test at least:

-   Empty version-1 database migrates successfully.
-   Multiple tasks and activities migrate.
-   All old task/activity IDs and values survive.
-   Achievement flags survive.
-   Legacy order is preserved according to the verified rule.
-   Opening the migrated database does not require destructive fallback.

**Phase 1 verification**

From `A2D/`:

``` bash
./gradlew test
./gradlew compileDebugKotlin
```

Do not continue until migration and entity tests pass. If full migration
tests are blocked by unavailable infrastructure, report the blocker and
do not mark migration verified.

------------------------------------------------------------------------

## 6. Phase 2 --- Implement ordering in DAO and repository

### Step 2.1: Make reads deterministic

Update `TaskDao` queries and/or repository mapping so task lists use
task `position` and each task's activities use activity `position`.

-   Add explicit `ORDER BY position ASC` where appropriate.
-   If relation loading does not guarantee the order of child rows, sort
    the mapped activity list explicitly by `position`.
-   Apply the same rule in all three repository read paths listed in
    Phase 0.
-   Use a stable tie-breaker such as ID only as a defensive secondary
    sort, not as the primary user-controlled order.

### Step 2.2: Add DAO transaction operations

Implement DAO-level operations for:

-   Updating positions for an ordered list of task IDs.
-   Updating positions for an ordered list of activity IDs scoped to one
    task.
-   Appending a list of new activity rows to one task.

Use Room `@Transaction` or an equivalent transaction pattern consistent
with the existing DAO. Avoid exposing a partially updated order to
observers.

For a reorder operation, write normalized positions `0..n-1` in the
requested order. Use a safe update strategy if a unique position
constraint exists; do not add a unique constraint unless the schema
contract calls for it.

For append, assign positions after the current maximum position within
the selected task. Empty task means the first imported position starts
at zero.

### Step 2.3: Add repository methods

Implement repository operations that:

-   Load the current list from Room.
-   Validate the requested ID list against the current list.
-   Reject duplicates, missing IDs, foreign IDs, stale IDs, or a list
    whose size differs from the current list.
-   Perform the DAO write transaction only after validation.
-   For activities, validate every ID belongs to the selected task.
-   For CSV append, create fresh local IDs using the project's existing
    ID strategy. Inspect the current `addActivity` implementation and
    centralize or reuse its ID strategy if appropriate; do not blindly
    copy source CSV IDs.
-   Preserve descriptions and achievement values for existing
    activities.
-   Keep the new activities' relative order equal to the CSV's row order
    (use CSV `position` to determine the intended import row order if
    the file rows are not already ordered).

If repository operations can race with another mutation, validate and
write within one transaction or revalidate inside the transactional
boundary. Do not rely only on UI-side validation.

### Step 2.4: Add repository tests

Test:

-   Task reorder persists after a fresh repository read.
-   Activity reorder persists after a fresh repository read.
-   Invalid permutations are rejected and make no writes.
-   IDs, priority, descriptions, and achievement flags are unchanged by
    reordering.
-   Imported rows append after existing activities.
-   Appended positions are contiguous and correct.
-   Import of an empty CSV follows a deliberate, documented behavior and
    never deletes existing activities.

**Phase 2 verification**

Run:

``` bash
./gradlew test
./gradlew compileDebugKotlin
```

Stop and fix any failure before starting UI work.

------------------------------------------------------------------------

## 7. Phase 3 --- Implement drag-and-drop and accessible reordering

### Step 3.1: ViewModel APIs

Add clear operations to `TaskViewModel`, adapting names to the current
Kotlin style:

-   `reorderTasks(orderedTaskIds: List<Long>)`
-   `reorderActivities(taskId: Long, orderedActivityIds: List<Long>)`

The ViewModel should:

1.  Launch work using the existing coroutine scope/pattern.
2.  Call repository operations.
3.  Surface a recoverable error if validation/persistence fails, using
    the project's existing UI error pattern if one exists.
4.  Avoid recalculating completion/gates for order-only changes unless
    inspection shows the current gate implementation depends on order.
    If order affects gate semantics, preserve the existing documented
    semantics and test it.
5.  Not write to Room during each drag movement.

### Step 3.2: Task reorder in `TaskListScreen.kt`

-   Add a dedicated drag handle to each task card.
-   Starting a drag must require interaction with the handle, not a
    normal tap on the card.
-   Keep card navigation, collapse/expand, inline task editing, activity
    editing, checkboxes, and delete buttons functional.
-   On drag completion, submit the final ordered task IDs once.
-   Add accessible Move Up and Move Down actions. Disable or omit Move
    Up for the first item and Move Down for the last item.
-   Do not change task priority when order changes.
-   Do not reorder based on task ID after the user has reordered.

Choose the simplest drag-and-drop implementation compatible with the
current Compose dependencies and supported Android versions. Before
adding a library, inspect the project's dependencies and prefer
existing/native support. If native support requires a substantial custom
implementation, explain the tradeoff and make a small isolated
implementation.

### Step 3.3: Activity reorder in both screens

Implement activity reorder behavior consistently in:

-   `TaskListScreen.kt` (activity list within a task card)
-   `TaskDetailScreen.kt` (full-screen activity list)

Requirements:

-   Reuse the same persisted position data and ViewModel operation.
-   Add a dedicated drag handle to each activity row.
-   Add accessible Move Up/Move Down actions.
-   Persist only after drop or an explicit move action.
-   Ensure drag gestures do not accidentally trigger card navigation,
    collapse/expand, inline edit, checkbox toggle, or delete.
-   Ensure editing state and focus handling from Bugfix B1 remain
    intact. Do not replace the current focus/deferred-commit logic with
    a simpler pattern unless the behavior is preserved and tested.

### Step 3.4: UI and ViewModel regression checks

Verify manually or through suitable UI tests:

-   Task card still opens Task Detail when tapped outside controls.
-   Task edit and activity edit can be entered repeatedly.
-   Switching between task name and description fields does not exit
    edit mode.
-   Checkbox, delete confirmation, add activity, collapse/expand, and
    navigation still work.
-   Dragging from a handle reorders; dragging from ordinary content does
    not start a reorder.
-   Move Up/Down gives the same order as dragging.
-   Order persists after navigating away/back and after app restart.

**Phase 3 verification**

Run:

``` bash
./gradlew test
./gradlew assembleDebug
```

Do not report drag-and-drop as fully verified from compilation alone. If
no device/emulator is available, explicitly mark gesture behavior as
awaiting device verification.

------------------------------------------------------------------------

## 8. Phase 4 --- Implement and test the CSV codec

### Step 4.1: Define a small CSV row model

Inside the CSV package, define the smallest model needed to represent an
import/export row. Do not add it to the Room entity or domain model
unless the architecture requires that.

The row must represent:

-   `position`: integer ordering value.
-   `description`: text.
-   `achieved`: Boolean.
-   `id`: optional informational source ID on import; local ID on
    export.

The source ID is never an instruction to overwrite or reuse a local
database ID.

### Step 4.2: Implement `ActivityCsvCodec`

Suggested public operations (adapt signatures to local conventions):

-   Parse CSV text into validated rows.
-   Serialize ordered activity rows into CSV text.

Codec requirements:

-   UTF-8 text.
-   Exact export header order: `position,description,achieved,id`.
-   Import requires `position`, `description`, and `achieved`; `id` may
    be absent and is ignored for identity.
-   Header matching should be case-insensitive only if that behavior is
    deliberately implemented and tested; otherwise require the
    documented lowercase header.
-   Handle commas in quoted descriptions, escaped double quotes (`""`),
    Unicode, CRLF/LF line endings, and embedded newlines in quoted
    fields.
-   Do not use `split(",")` or `readLines()` as a CSV parser.
-   Parse Boolean values strictly as `true` or `false` (case handling
    must be deliberate and tested).
-   Reject malformed quoting, invalid position values, missing required
    columns, or invalid achievement values with a useful validation
    error.
-   Decide and document whether negative/duplicate positions are
    rejected or normalized. Recommended: reject negative positions and
    sort valid rows by `position`, using original row order as a
    tie-breaker if duplicate positions are permitted. Prefer rejecting
    duplicate positions for unambiguous imports.
-   Do not impose a nonblank-description restriction unless consistent
    with the existing app/domain rule. Inspect `addActivity`,
    `updateActivity`, and UI validation first.
-   Empty file/header-only file behavior must be explicit and tested.
-   Parsing must not touch Room or UI state.

Use a standards-compliant CSV parser/writer if one is already available.
If not, implement a small RFC-4180-compatible codec with tests rather
than adding a large dependency. Keep parsing and escaping in one class.

### Step 4.3: Codec unit tests

Add `ActivityCsvCodecTest.kt` or extend an existing appropriate test
class. Include:

1.  Export header and column order.
2.  Round trip for ordinary descriptions.
3.  Description containing a comma.
4.  Description containing a quote.
5.  Description containing an embedded newline.
6.  Unicode text.
7.  CRLF and LF input.
8.  Missing required header.
9.  Missing/invalid position.
10. Invalid `achieved` value.
11. Malformed quoted field.
12. Informational source ID does not become local identity.
13. Empty/header-only file behavior.
14. Multiple rows imported in `position` order.

**Phase 4 verification**

Run:

``` bash
./gradlew test
./gradlew compileDebugKotlin
```

Do not wire UI import/export until codec tests pass.

------------------------------------------------------------------------

## 9. Phase 5 --- Add CSV import/export file flows

### Step 5.1: Use Android Storage Access Framework

Use Activity Result APIs in the current Compose architecture:

-   Import: `OpenDocument` (request `text/*` or an appropriate
    CSV-compatible MIME type; account for providers that report CSV as
    `text/csv` or `text/comma-separated-values`).
-   Export: `CreateDocument("text/csv")`.

Inspect current Android/Compose versions and project conventions before
choosing exact launcher APIs. Do not add broad storage permissions for
this workflow; SAF should grant access to the selected document.

### Step 5.2: Read and write documents safely

Import:

1.  User selects a file.
2.  Read its content as UTF-8 using the returned content URI.
3.  Parse the entire file with `ActivityCsvCodec`.
4.  If invalid, show an understandable error and do not mutate Room.
5.  Show preview/count (at minimum: number of activities to append and
    any validation warning).
6.  Ask for confirmation.
7.  Only after confirmation, call the ViewModel/repository append
    operation.
8.  Show success/failure feedback.

Export:

1.  Request a destination using `CreateDocument`.
2.  Serialize the selected task's activities in current persisted
    position order.
3.  Write UTF-8 content to the returned URI.
4.  Handle cancellation and write errors without crashing.
5.  Report success only after the write completes.

Do not hold an Activity, Context, URI stream, or composable callback
longer than necessary. Follow existing lifecycle-safe patterns.

### Step 5.3: Import append semantics

The append operation must:

-   Keep all existing activities unchanged.
-   Generate fresh local activity IDs for every imported row.
-   Assign positions starting after the current maximum position.
-   Preserve each imported row's `description` and `achieved`.
-   Append in CSV position order.
-   Be transactional: either all valid rows are inserted or none are.
-   Never use the informational CSV `id` as a local ID.
-   Avoid partial inserts when a row is invalid; validate all rows
    before starting the transaction.

After successful import, let Room re-emit task state. Recompute
progress/completion/gate state only as required by the existing gate
semantics. Preserve the distinction between `Task.isCompleted` (requires
at least one activity and all achieved) and the Tier-1 gate's existing
`achieved` semantics.

### Step 5.4: Wire actions in both UI locations

Add import/export actions:

-   On the task card in `TaskListScreen.kt`.
-   On `TaskDetailScreen.kt`.

Use the selected task ID. If the card has a compact layout, a menu is
acceptable. Do not place import/export actions where they accidentally
trigger the card's navigation or collapse click handler.

Avoid duplicating parsing logic or database writes in composables. UI
launches the file picker and collects content; the codec
validates/serializes; ViewModel/repository coordinates task state and
persistence.

### Step 5.5: CSV integration tests

Test:

-   Successful import appends rows to a task with existing activities.
-   Existing activities retain IDs, order, descriptions, and achievement
    flags.
-   Imported rows receive distinct fresh local IDs even if CSV IDs
    collide with existing IDs.
-   CSV row order is respected.
-   Invalid CSV leaves database contents unchanged.
-   Cancelled picker causes no mutation.
-   Export contains all activities in persisted order and the exact
    header.
-   Import/export works for a task with zero activities.
-   Importing into one task does not modify another task.
-   Successful import updates displayed progress/completion and
    gate-derived state according to existing semantics.

**Phase 5 verification**

Run:

``` bash
./gradlew test
./gradlew assembleDebug
```

Manually test file picker import/export on a device or emulator when
available.

------------------------------------------------------------------------

## 10. Phase 6 --- Documentation and final verification

### Step 6.1: Update README

Add a clearly titled section covering:

-   Task and activity reordering.
-   Drag handle and accessible Move Up/Down actions.
-   Persisted positions and Room v1→v2 migration.
-   CSV export columns and examples.
-   Required import fields and informational `id`.
-   Append-only import semantics and fresh local IDs.
-   Import preview/confirmation and error/cancellation behavior.
-   How to test the features.

Do not mark the features complete until the final verification results
are known.

### Step 6.2: Update the implementation log

Append a new milestone with:

-   Exact files created, modified, and deleted (ideally none deleted).
-   Migration design and verified legacy ordering rule.
-   Reordering and CSV behavior.
-   Tests added and their results.
-   Commands run with exit status.
-   APK path and build result.
-   Manual device verification status.
-   Any known limitations.

Never include keystore passwords, API keys, or other secrets in the log.

### Step 6.3: Final verification commands

From `A2D/`, run each command and capture the actual result:

``` bash
./gradlew test
./gradlew compileDebugKotlin
./gradlew assembleDebug
```

If release compilation is affected by changed code, also run:

``` bash
./gradlew bundleRelease
```

Inspect the test report files under:
`A2D/app/build/test-results/testDebugUnitTest/`

Verify there are no regressions in the existing 15 tests and all new
tests pass. Report the actual total rather than assuming it remains 15.

### Final completion checklist

-   [ ] Database v1→v2 migration is tested and preserves existing data.
-   [ ] Task order persists and follows the verified legacy order on
    first migration.
-   [ ] Activity order persists and is identical on task cards and Task
    Detail.
-   [ ] Drag handle works without breaking ordinary card interactions.
-   [ ] Move Up/Down works and is accessible.
-   [ ] Reorder preserves IDs, priority, descriptions, and achievement
    flags.
-   [ ] CSV export has exact header order
    `position,description,achieved,id`.
-   [ ] CSV parser handles quoted commas, quotes, Unicode, and embedded
    newlines.
-   [ ] Import is append-only and assigns fresh local IDs.
-   [ ] Import is validated and confirmed before database mutation.
-   [ ] Invalid/cancelled imports do not mutate data.
-   [ ] Progress, completion, and gate state remain correct.
-   [ ] Existing inline task/activity editing and focus fixes still
    work.
-   [ ] Delete confirmation, add activity, navigation, and
    collapse/expand still work.
-   [ ] Unit tests, compile, and debug build pass.
-   [ ] README and implementation log are updated.
-   [ ] Device-only checks are explicitly marked complete or pending.

------------------------------------------------------------------------

## 11. Recommended OpenCode execution protocol for a smaller coding model

A smaller model is more reliable when given one bounded task at a time.
Do not ask it to implement all phases in one prompt.

For each phase:

1.  Give the agent the relevant source files and this guide.
2.  Ask it to restate the specific phase's acceptance criteria before
    editing.
3.  Ask it to inspect the actual code and propose the smallest file
    diff.
4.  Approve or direct that phase only; do not let it jump ahead.
5.  Ask it to implement only the current phase.
6.  Ask it to run the phase's verification commands.
7.  Ask it to review the diff for unrelated changes and regressions.
8.  Require a concise report: files changed, tests/commands, results,
    unresolved items.
9.  Proceed only if exit criteria pass.

### Prompt template

Copy this prompt and replace the phase name and steps:

> You are implementing the A2D Android app. Read `AGENTS.md`,
> `a2d_graph_reordering_csv_contract.yaml`,
> `a2d_implementation_log(2).md`, and
> `A2D_FEATURE_IMPLEMENTATION_GUIDE.md`.
>
> Implement **only Phase \[N\]: \[PHASE NAME\]** from the guide.
>
> Before editing, inspect the current implementation and list the exact
> files/methods you will change. If a required fact is unknown or the
> source conflicts with the contract, stop and explain it; do not guess.
>
> Preserve all existing behavior, especially inline task/activity
> editing and Bugfix B1 focus handling, completion/progress display,
> delete confirmation, navigation, and gate semantics. Do not perform
> unrelated refactoring or add dependencies without justification.
>
> Implement the smallest coherent change for this phase, add the
> specified tests, and run the specified commands. Do not claim success
> without command results. At the end, report changed files, tests and
> build results, remaining risks, and whether the phase exit criteria
> are met. Do not start the next phase.

### Suggested separate agent tasks

1.  **Inspection only:** establish baseline and confirm legacy ordering.
    No edits.
2.  **Database only:** entity fields, migration, registration, migration
    tests.
3.  **Persistence only:** DAO/repository ordering and transactions,
    repository tests.
4.  **ViewModel only:** validated reorder operations and unit tests.
5.  **UI reordering only:** handles and Move Up/Down; no CSV work yet.
6.  **CSV codec only:** parser/serializer and codec tests; no UI file
    picker yet.
7.  **CSV integration only:** SAF, preview/confirmation, append
    transaction, UI actions, integration tests.
8.  **Final verification only:** regression review, README,
    implementation log, test/build report.

**Do not combine database migration, drag-and-drop UI, CSV parsing, and
SAF in one generation request.** If a phase is too large for one
reliable change, split it into smaller tasks and verify each.

------------------------------------------------------------------------

## 12. Known decisions and open questions

### Already decided --- do not ask again

-   Drag-and-drop uses a handle.
-   Move Up/Down accessible actions are required.
-   CSV import appends activities.
-   CSV export columns are `position,description,achieved,id`.
-   CSV `id` is informational only.
-   CSV actions are available on Task Card and Task Detail.

### Must be confirmed from source before implementation

-   Exact existing task-list order for migration backfill.
-   Exact current activity ID generation strategy and whether it is safe
    to reuse.
-   Current Compose dependencies and the least invasive supported
    drag-and-drop implementation.
-   Current test infrastructure for Room migrations and UI/file-picker
    tests.
-   Whether empty activity descriptions are allowed by the current
    domain behavior.

Do not pause to ask the user about already-approved decisions.
Investigate the open questions in the code. If a question cannot be
resolved from the source, report the exact evidence and ask only that
targeted question.

------------------------------------------------------------------------

**Definition of done:** the new features are implemented in the existing
A2D project, persisted safely, tested against the old behavior,
documented, and built successfully. A successful compile alone is not
proof that drag gestures, SAF flows, migration correctness, or append
semantics work.
