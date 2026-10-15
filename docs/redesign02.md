# Task UI Redesign – Proposal 02 (redesign02.md)

## Overview

This document specifies a new iteration of the task UI for the A2D Android app, building on the previous redesign.

Key new requirements:

1. **Editable task name and description**:
   - An edit button next to the task label allows inline editing of:
     - Task name.
     - Task description.
   - Changes are persisted when:
     - The user taps the edit button again (to stop editing), or
     - The user taps outside the editing area (focus lost).
2. **Collapsible/expandable task body in the list**:
   - In the task list, each task card has:
     - A header that is tappable to toggle visibility of the body (collapse/expand).
     - A body that, when tapped, opens the full **Task Detail** screen.
3. **Progress as percentage in the task header**:
   - The task header shows:
     - Task name.
     - Edit button.
     - Progress percentage (e.g. “60%”).
   - Progress is computed from activity completion:
     - `progress = (completedCount / totalCount) * 100`.

All previous behaviors (checkboxes, done styling, delete with confirmation, detail screen, etc.) remain unless explicitly changed here.

---

## 1. Domain & ViewModel

### Task completion and progress

Keep the existing completion rule:

```kotlin
val isCompleted = activities.isNotEmpty() && activities.all { it.achieved }
```

Add a derived progress value:

```kotlin
val progressPercent: Int
    get() = if (activities.isEmpty()) {
        0
    } else {
        val completed = activities.count { it.achieved }
        (completed * 100 / activities.size)
    }
```

This can be:

- A computed property on `Task`, or
- A derived value in the ViewModel / UI state.

### ViewModel operations

In addition to existing operations, the ViewModel must support:

- `updateTask(taskId: Long, newName: String, newDescription: String)`
  - Validates and trims input.
  - Updates the task in the repository.
  - Emits updated `tasks` flow.

Existing operations remain:

- `toggleActivity(taskId, activityId, achieved)`
- `addActivity(taskId)`
- `deleteActivity(taskId, activityId)`
- `deleteTask(taskId)`

---

## 2. Task List Screen

### Task Card Structure

Each task card is divided into:

1. **Header** (always visible):
   - Task name.
   - Edit button (next to the name).
   - Progress percentage.
   - Optional task-level actions (e.g. delete) can remain if desired.
2. **Body** (collapsible):
   - Task description (when not editing).
   - Activity list:
     - Checkboxes.
     - Activity descriptions with done styling.
     - Per-activity delete buttons.
   - “Add activity” button below the list.
   - Empty state: only “Add activity” when there are no activities.

Behavior:

- Tapping the **header**:
  - Toggles the body visibility (collapse/expand).
- Tapping the **body** (excluding interactive elements like checkboxes, buttons, edit field):
  - Navigates to the **Task Detail** screen for that task.
- Tapping the **edit button**:
  - Enters edit mode for task name and description.
  - Shows editable text fields in the body.
  - On tapping edit again or tapping outside, changes are persisted.

---

## 3. Editable Task Name & Description

### Edit Mode

When the user taps the edit button:

- The task card enters **edit mode**.
- In the body:
  - Task name is shown in an `OutlinedTextField`.
  - Task description is shown in another `OutlinedTextField` (possibly multi-line).
- The edit button changes state (e.g. icon or tooltip) to indicate “Done editing”.

Exiting edit mode:

- When the user:
  - Taps the edit button again, or
  - Taps outside the editing area (focus lost),
- The current values in the text fields are:
  - Validated (e.g. non-empty name).
  - Sent to `updateTask(taskId, newName, newDescription)`.
- The card returns to **view mode**.

### View Mode

In view mode:

- Task name is displayed as `Text` with a prominent style.
- Task description is displayed below it (if non-empty).
- Edit button is shown next to the name in the header.

---

## 4. Collapse/Expand Behavior

### Header Tap

- Tapping anywhere on the header (except the edit button and other controls) toggles:
  - `isExpanded` state for that task card.
- When collapsed:
  - Only the header is visible.
  - Body (description, activities, buttons) is hidden.
- When expanded:
  - Body is visible as before.

Implementation:

- Maintain `isExpanded` per task in:
  - Local UI state (e.g. a `MutableMap<Long, Boolean>` keyed by task ID), or
  - ViewModel state if you want it to survive configuration changes.

### Body Tap

- Tapping the body (excluding interactive elements) navigates to the Task Detail screen:
  ```kotlin
  navController.navigate("taskDetail/$taskId")
  ```
- This does not interfere with:
  - Checkbox taps.
  - Delete button taps.
  - Edit field interactions.

---

## 5. Task Header – Progress Percentage

### Layout

Example header layout:

```text
┌──────────────────────────────────────────────┐
│ [Edit]  Task name                   60%      │
└──────────────────────────────────────────────┘
```

Or:

```text
┌──────────────────────────────────────────────┐
│ Task name            [Edit]         60%      │
└──────────────────────────────────────────────┘
```

Components:

- Edit button:
  - `IconButton` with an edit icon (e.g. `Icons.Default.Edit`).
- Task name:
  - `Text` with `titleMedium` or similar.
- Progress percentage:
  - `Text` showing `progressPercent` followed by “%”.
  - Optionally colored based on progress (e.g. low: amber, high: green).

Example Compose snippet:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { onHeaderClick() }
        .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(onClick = { onEditClick() }) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit task"
        )
    }

    Text(
        text = task.name,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f)
    )

    Text(
        text = "${task.progressPercent}%",
        style = MaterialTheme.typography.labelLarge,
        color = if (task.progressPercent == 100) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}
```

---

## 6. Task Detail Screen

No major changes to the Task Detail screen from the previous spec, except:

- The header can continue to show:
  - Task name.
  - Description.
  - Completion indicator (text + badge + color).
- Editing of task name/description can be:
  - Added later if needed, or
  - Kept only in the list view for now.

All existing behaviors (back button, activity list, delete with confirmation, add activity) remain.

---

## 7. Confirmation Dialogs & Delete Behavior

Keep the previous decisions:

- Task-level delete:
  - Available in list and/or detail views (as previously decided).
  - Requires confirmation dialog before deletion.
- Per-activity delete:
  - Available in list and detail views.
  - Requires confirmation dialog before deletion.

This proposal does not change delete behavior; it only adds edit and collapse/expand features.

---

## 8. UI Architecture & State

### Composables

Suggested structure:

- `TaskListScreen.kt`:
  - `TaskListScreen(viewModel: TaskViewModel)`
  - `TaskCard(task, onTaskBodyClick, onEditToggle, onSaveEdit, onToggleActivity, onAddActivity, onDeleteActivity, onDeleteTask)`
    - Internally manages:
      - `isExpanded` (collapse/expand).
      - `isEditing` (edit mode).
      - Local text field states for name/description.
  - `TaskCardHeader(task, onHeaderClick, onEditClick, isEditing)`
  - `TaskCardBody(task, isEditing, onToggleActivity, onAddActivity, onDeleteActivity, onBodyClick)`
  - `ActivityRow(...)` (shared with detail screen if desired).

- `TaskDetailScreen.kt`:
  - As per the previous spec, unchanged except for any optional future edit support.

### State Management

- `isExpanded` per task:
  - Can be kept in local UI state:
    ```kotlin
    val expandedStates = remember { mutableStateMapOf<Long, Boolean>() }
    val isExpanded = expandedStates[task.id] ?: true
    ```
- `isEditing` per task:
  - Similarly:
    ```kotlin
    val editingStates = remember { mutableStateMapOf<Long, Boolean>() }
    val isEditing = editingStates[task.id] ?: false
    ```
- On exiting edit mode:
  - Call `viewModel.updateTask(task.id, newName, newDescription)`.

---

## 9. Files to Modify

Based on the existing implementation: [1]

- `A2D/app/src/main/java/com/a2d/app/ui/task/TaskScreen.kt`:
  - Refactor into:
    - `TaskListScreen.kt` with collapsible cards and inline edit.
    - `TaskDetailScreen.kt` (as before).
- `A2D/app/src/main/java/com/a2d/app/ui/task/TaskViewModel.kt`:
  - Add:
    - `updateTask(taskId, newName, newDescription)`.
    - Derived `progressPercent` logic (either in ViewModel or on `Task` model).
- Optionally:
  - Update navigation if not already using `taskDetail/{taskId}`.

No database schema changes are required; `TaskEntity` already has `taskName` and `description` fields. [1]

---

## 10. Summary of New Behaviors

- Task header:
  - Shows task name, edit button, and progress percentage.
- Edit mode:
  - Inline editing of task name and description.
  - Persist on:
    - Tapping edit button again, or
    - Tapping outside (focus lost).
- Task card body:
  - Collapsible via header tap.
  - Tapping body (outside controls) opens Task Detail screen.
- Progress:
  - Displayed as a percentage in the header.
  - Derived from activity completion.

This proposal preserves all prior functionality while adding inline editing, collapsible cards, and percentage-based progress in the task list.