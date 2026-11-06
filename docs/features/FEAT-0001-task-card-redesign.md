# FEAT-0001 — Task Card Redesign

- Status: Implemented
- Owner: project maintainer
- Last updated: 2026-11-06
- Related: [Implementation log](../history/implementation-log.md), [README §4.3](../../README.md#43-presentation-layer--ui-architecture)
- Target version: 1.0.0

> **Status: implemented.** This record combines the delivered summary and the original design
> specification. Current behavior is described in
> [README §4.3](../../README.md#43-presentation-layer--ui-architecture). Historical per-task progress
> reports are in the [implementation log](../history/implementation-log.md).

## Delivered implementation

Summary of tasks completed under the Task Card Redesign v1.0 specification. The original design is
preserved in [Original specification v1.0](#original-specification-v10) below. Per-task progress
reports are in the [implementation log](../history/implementation-log.md).

| # | Task | Status | What was delivered |
| --- | --- | --- | --- |
| T1 | Domain completion rule & ID ordering | ✅ | `Task.isCompleted` = `activities.isNotEmpty() && activities.all { it.achieved }`; `TaskRepositoryImpl` maps activities with `.sortedBy { it.id }` (all 3 query methods) so items always render in ID order; 3 new unit tests in `TaskDomainTest` |
| T2 | ViewModel operations | ✅ | `TaskViewModel` now exposes `tasks`, `currentTask: StateFlow<Task?>` (derived via `combine(tasks, selectedTaskId)`), `selectTask(taskId)`, and the spec operations `toggleActivity(taskId, activityId, achieved)`, `addActivity(taskId)`, `deleteActivity(taskId, activityId)`, `deleteTask(taskId)` — all Room-backed with Tier-1 gate re-evaluation and Tier-2 pipeline recalculation |
| T3 | Jetpack Navigation | ✅ | Added `androidx.navigation:navigation-compose:2.8.2` (+ `material-icons-core`); `MainActivity` now hosts a `NavHost` with routes `taskList`, `taskDetail/{taskId}` (Long arg) and `logicGate`; bottom tabs retained, Tasks tab stays active on the detail route |
| T4 | TaskListScreen | ✅ | Compact `ElevatedCard` per task: × task-delete in header, optional description, checkbox activity list (strikethrough + 60 % fade when done), per-activity 🗑 delete, `+ Add activity` button; activity list capped at 200 dp for internal scrolling; card body taps open the detail screen; both deletes use confirmation `AlertDialog`s. Replaces the old `TaskScreen.kt` (removed) |
| T5 | TaskDetailScreen | ✅ | Full-screen view: back arrow (`navigateUp()`), task header with green "Completed" / neutral "In progress" badge pill, full activity list with shared `ActivityRow`, task-delete button in the top bar (confirm → delete + navigate back), `+ Add activity` below the list, loading indicator while the task flow resolves |
| T6 | Verification | ✅ | `./gradlew test` → **11/11 tests pass**; `./gradlew assembleDebug` → BUILD SUCCESSFUL (`app-debug.apk`, 10.3 MB). No Room schema changes required |
| T7 | Documentation | ✅ | This feature record + per-task entries in the [implementation log](../history/implementation-log.md) |

**Behavior notes**

- Completion rule used everywhere in the UI: a task is completed only when it has **at least one** activity and **all** activities are checked (the empty task is *not* completed — distinct from the Tier-1 gate's `achieved` flag, which is kept for the milestone pipeline).
- All delete operations (task-level and per-activity, in both list and detail views) require confirmation via `AlertDialog`.
- Activities are never reordered — they always display in original ID order; new activities append at the end (globally unique monotonic IDs).
- No inline expand/collapse of activity items; tapping a card navigates to the detail screen instead.

---

## Original specification v1.0

> **Historical design document.** This specification was implemented in 1.0.0. The "Files to
> Modify" section is historical: `TaskScreen.kt` was split into `TaskListScreen.kt` and
> `TaskDetailScreen.kt` and no longer exists. Current behavior is described in
> [README §4.3](../../README.md#43-presentation-layer--ui-architecture).


### Overview

This document specifies the redesigned task UI for the A2D Android app.

Key changes:

- **Task List Screen**:
  - Shows compact task cards.
  - Each card is tappable to open the **Task Detail** screen.
  - Each card has:
    - Task-level delete button.
    - Per-activity delete buttons.
    - Checkboxes for each activity.
    - “Add activity” button below the activity list.
- **Task Detail Screen** (full-screen):
  - Opens when a task card is tapped.
  - Shows:
    - Back button to return to the task list.
    - Task header with title, description, and completion indicator.
    - Full activity list with checkboxes and done styling.
    - Per-activity delete buttons.
    - Task-level delete button.
    - “Add activity” button below the (possibly empty) activity list.
- **Completion rule**:
  - A task is completed when:
    - It has at least one activity item, and
    - All activity items are checked (`achieved == true`).
- **Confirmation dialogs**:
  - Required for:
    - Task-level delete (both list and detail views).
    - Per-activity delete (both list and detail views).

No inline expand/collapse or reordering of activity items is used. Items remain in original order by ID.

---

### 1. Domain & ViewModel

#### Task completion rule

A task is considered completed when:

```kotlin
completed = activities.isNotEmpty() && activities.all { it.achieved }
```

This logic should be implemented in:

- The domain model (`Task`), or
- The ViewModel when computing UI state.

The existing implementation already uses this pattern. [1]

#### ViewModel operations

The shared `TaskViewModel` (or separate ViewModels per screen) must expose:

- `tasks: StateFlow<List<Task>>` for the task list.
- `currentTask: StateFlow<Task?>` for the selected task in the detail screen.
- Operations:
  - `toggleActivity(taskId: Long, activityId: Long, achieved: Boolean)`
  - `addActivity(taskId: Long)`
  - `deleteActivity(taskId: Long, activityId: Long)`
  - `deleteTask(taskId: Long)`

All operations must:

- Update the underlying Room database via the repository.
- Emit updated `tasks` / `currentTask` flows so UI recomposes.

No sorting or reordering logic is needed; items are displayed in ID order.

---

### 2. Task List Screen

#### Task Card Layout (compact)

Each task card shows:

- Task title.
- Optional task description.
- A list of activity items:
  - Each with:
    - Checkbox.
    - Description.
    - Delete icon.
  - Displayed in original order by ID.
  - Done items styled with strikethrough + slight fade.
- “Add activity” button below the activity list.
- Task-level delete button (e.g. in the header).
- Entire card is tappable to open the Task Detail screen.

Example layout:

```text
┌──────────────────────────────────────────────┐
│ ×  Task title                                │
│    Task description (optional)               │
│                                              │
│    ☐ Activity 1                       🗑     │
│    ☑ Activity 2 (done)              🗑     │
│    ☐ Activity 3                       🗑     │
│          (scrollable if more items)          │
│                                              │
│    [ + Add activity ]                         │
└──────────────────────────────────────────────┘
```

Behavior:

- Tapping the card body (excluding buttons) navigates to the Task Detail screen.
- Tapping `×` opens a confirmation dialog:
  - “Delete task?”
  - “This will delete the task and all of its activities.”
  - Confirm → call `deleteTask(taskId)`.
- Tapping per-activity delete opens a confirmation dialog:
  - “Delete this activity?”
  - Confirm → call `deleteActivity(taskId, activityId)`.
- Tapping “Add activity”:
  - Calls `addActivity(taskId)`.
  - New item appears at the end of the list (by ID order).

#### Activity row styling

For each activity item:

- Checkbox bound to `activity.achieved`.
- Description styled as:

  ```kotlin
  Text(
      text = activity.description,
      style = MaterialTheme.typography.bodyLarge,
      color = if (activity.achieved) {
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
      } else {
          MaterialTheme.colorScheme.onSurface
      },
      textDecoration = if (activity.achieved) {
          TextDecoration.LineThrough
      } else {
          null
      }
  )
  ```

- Delete icon on the right.

The activity list uses a `LazyColumn` with a max height constraint to allow internal scrolling when the list is long.

#### Empty state

When a task has no activities:

- Show an empty `LazyColumn` (or a simple placeholder with no text).
- Show the “Add activity” button below the list.
- No extra hint text is required.

---

### 3. Task Detail Screen (full-screen)

#### Navigation

- Full-screen Compose destination.
- Receives `taskId: Long` as a parameter.
- Has a **back button** in the top app bar (or as an IconButton) that calls `navigateUp()`.

---

#### Header

At the top of the detail screen:

- Task title.
- Optional task description.
- Completion indicator:
  - Text: “Completed” / “In progress”.
  - Badge (e.g. `AssistChip` or `Card` with small text).
  - Color change:
    - Completed: green-themed.
    - In progress: default/neutral or amber-themed.

Example:

```text
┌──────────────────────────────────────────────┐
│ ←  Task title                                │
│    Task description (optional)               │
│    [Completed]  (green badge)                │
└──────────────────────────────────────────────┘
```

Implementation:

```kotlin
val isCompleted = task.activities.isNotEmpty() &&
        task.activities.all { it.achieved }

val statusText = if (isCompleted) "Completed" else "In progress"
val statusColor = if (isCompleted) {
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
} else {
    MaterialTheme.colorScheme.surfaceVariant
}
```

Use a `Row` or `AssistChip` to show the badge next to the title/description.

---

#### Activity List

- Displayed as a `LazyColumn`.
- Shows all activity items for the task in **original order by ID**.
- Each row contains:
  - Checkbox.
  - Activity description (with done styling).
  - Delete icon.

Behavior:

- Checkbox toggles `achieved` via `toggleActivity(taskId, activityId, newCheckedState)`.
- Delete icon opens a confirmation dialog:
  - “Delete this activity?”
  - Confirm → call `deleteActivity(taskId, activityId)`.

Styling is identical to the task list screen.

---

#### Task-level delete

- A task delete button (e.g. trash icon or “Delete task” text button) is shown in the header or top app bar.
- Tapping it opens a confirmation dialog:
  - “Delete task?”
  - “This will delete the task and all of its activities.”
  - Confirm → call `deleteTask(taskId)` and navigate back to the task list.

---

#### Add Activity Button

- Placed **below** the activity list.
- Label: “Add activity” with a `+` icon.
- Behavior:
  - Calls `addActivity(taskId)`.
  - New item appears at the end of the list (by ID order).

Layout:

```text
[LazyColumn of activity items]

[ + Add activity ]
```

When the list is empty, the layout is:

```text
[Empty LazyColumn]

[ + Add activity ]
```

No extra hint text is shown.

---

### 4. Confirmation Dialogs

#### Task delete dialog

Used in both list and detail views.

Content:

- Title: “Delete task?”
- Text: “This will delete the task and all of its activities.”
- Buttons:
  - “Cancel”
  - “Delete” (destructive, e.g. red tint)

Behavior:

- On confirm: call `deleteTask(taskId)`.
- In detail view, navigate back to the task list after deletion.

#### Activity delete dialog

Used in both list and detail views.

Content:

- Title: “Delete activity?”
- Text: “This will remove this activity from the task.”
- Buttons:
  - “Cancel”
  - “Delete”

Behavior:

- On confirm: call `deleteActivity(taskId, activityId)`.

---

### 5. UI Architecture

#### Screens

- `TaskListScreen`:
  - Shows a `LazyColumn` of compact task cards.
  - Each card includes:
    - Header with task title and task delete button.
    - Activity list with checkboxes and delete icons.
    - “Add activity” button.
  - Tapping the card body navigates to `TaskDetailScreen`.
- `TaskDetailScreen`:
  - Full-screen composable.
  - Receives `taskId: Long`.
  - Displays:
    - Back button.
    - Task header with completion indicator.
    - Activity list with checkboxes and delete icons.
    - Task delete button.
    - “Add activity” button.

#### Navigation

Using Jetpack Navigation:

- Routes:
  - `taskList` → `TaskListScreen`
  - `taskDetail/{taskId}` → `TaskDetailScreen`
- From `TaskListScreen`:
  ```kotlin
  navController.navigate("taskDetail/$taskId")
  ```
- Back button in `TaskDetailScreen`:
  ```kotlin
  IconButton(onClick = { navController.navigateUp() }) { ... }
  ```

---

### 6. Implementation Notes

#### Compose components

Suggested decomposition:

- `TaskListScreen.kt`:
  - `TaskListScreen(viewModel: TaskViewModel)`
  - `TaskCard(task, onTaskClick, onToggleActivity, onAddActivity, onDeleteActivity, onDeleteTask)`
  - `ActivityRow(activity, onCheckedChange, onDelete)`
- `TaskDetailScreen.kt`:
  - `TaskDetailScreen(viewModel: TaskViewModel, taskId: Long, onNavigateBack: () -> Unit)`
  - `TaskDetailHeader(task, onDeleteTask)`
  - `ActivityRow(...)` (shared or duplicated)
  - `AddActivityButton(onClick)`

#### State handling

- Use `StateFlow<List<Task>>` from the ViewModel.
- For the detail screen, derive `currentTask` as:
  ```kotlin
  val currentTask = tasks.map { list ->
      list.find { it.id == taskId }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
  ```
- All mutations go through ViewModel methods to keep business logic out of composables.

#### Theming

- Use Material 3 components:
  - `ElevatedCard` for task cards.
  - `LazyColumn` for activity lists.
  - `AssistChip` or similar for completion badge.
  - Standard `AlertDialog` for confirmation dialogs.

---

### 7. Files to Modify

Based on the existing implementation: [1]

- `A2D/app/src/main/java/com/a2d/app/ui/task/TaskScreen.kt`
  - **Historical target — this file has been removed.** The implementation now lives in
    `TaskListScreen.kt` (list view) and `TaskDetailScreen.kt` (detail view).
- `A2D/app/src/main/java/com/a2d/app/ui/task/TaskViewModel.kt`
  - Add/update:
    - `toggleActivity(...)`
    - `addActivity(...)`
    - `deleteActivity(...)`
    - `deleteTask(...)`
    - `currentTask` state for detail screen.
- Navigation setup:
  - Update `MainActivity.kt` or your navigation graph to include the `taskDetail` route.

No Room schema changes are required; the existing `TaskEntity` / `ActionItemEntity` structure supports this design. [1]

---

### 8. Summary of Behaviors

- Task list:
  - Compact cards with activities, checkboxes, delete icons, and “Add activity”.
  - Task delete with confirmation.
  - Tap card → open detail screen.
- Task detail:
  - Full-screen view with back button.
  - Completion indicator (text + badge + color).
  - Full activity list with checkboxes, done styling, and delete icons.
  - Task delete with confirmation.
  - “Add activity” button below the list.
- Completion rule:
  - Task completed when all activities are checked and at least one activity exists.
- No reordering; items stay in ID order.
- Done items: strikethrough + slight fade.
- Confirmation dialogs for all delete operations.


---
