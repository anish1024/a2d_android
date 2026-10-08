package com.a2d.app.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.a2d.app.domain.model.ActionItem
import com.a2d.app.domain.model.Task

@Composable
fun TaskListScreen(
    tasks: List<Task>,
    milestoneOutput: Int,
    onTaskClick: (Long) -> Unit,
    onToggleActivity: (Long, Long, Boolean) -> Unit,
    onAddActivity: (Long) -> Unit,
    onDeleteActivity: (Long, Long) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onSaveEdit: (Long, String, String) -> Unit,
    onUpdateActivity: (Long, Long, String) -> Unit,
    onCreateSample: () -> Unit
) {
    // Per-task UI state, keyed by task id (spec §8) — survives LazyColumn item disposal.
    val expandedStates = remember { mutableStateMapOf<Long, Boolean>() }
    val editingStates = remember { mutableStateMapOf<Long, Boolean>() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("A2D Focus Tasks", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = if (milestoneOutput == 1) "Milestone Complete [GATE 1]" else "Milestone Incomplete [GATE 0]",
                    color = if (milestoneOutput == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Button(onClick = onCreateSample) {
                Text("+ Task")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    isExpanded = expandedStates[task.id] ?: true,
                    isEditing = editingStates[task.id] ?: false,
                    onHeaderClick = {
                        expandedStates[task.id] = !(expandedStates[task.id] ?: true)
                    },
                    onEditingChange = { editing ->
                        editingStates[task.id] = editing
                        // Edit fields live in the body — always expand when entering edit mode.
                        if (editing) expandedStates[task.id] = true
                    },
                    onSaveEdit = { name, description ->
                        onSaveEdit(task.id, name, description)
                    },
                    onUpdateActivity = { activityId, description ->
                        onUpdateActivity(task.id, activityId, description)
                    },
                    onTaskBodyClick = { onTaskClick(task.id) },
                    onToggleActivity = { activityId, achieved ->
                        onToggleActivity(task.id, activityId, achieved)
                    },
                    onAddActivity = { onAddActivity(task.id) },
                    onDeleteActivity = { activityId -> onDeleteActivity(task.id, activityId) },
                    onDeleteTask = { onDeleteTask(task.id) }
                )
            }
        }
    }
}

@Composable
fun TaskCard(
    task: Task,
    isExpanded: Boolean,
    isEditing: Boolean,
    onHeaderClick: () -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onSaveEdit: (String, String) -> Unit,
    onUpdateActivity: (Long, String) -> Unit,
    onTaskBodyClick: () -> Unit,
    onToggleActivity: (Long, Boolean) -> Unit,
    onAddActivity: () -> Unit,
    onDeleteActivity: (Long) -> Unit,
    onDeleteTask: () -> Unit
) {
    var showTaskDeleteDialog by remember { mutableStateOf(false) }
    var pendingActivityDeleteId by remember { mutableStateOf<Long?>(null) }

    var draftName by remember { mutableStateOf(task.taskName) }
    var draftDescription by remember { mutableStateOf(task.description) }
    val nameFocusRequester = remember { FocusRequester() }
    var nameFocused by remember { mutableStateOf(false) }
    var descFocused by remember { mutableStateOf(false) }
    var focusArmed by remember { mutableStateOf(false) }

    val currentIsEditing by rememberUpdatedState(isEditing)
    val currentOnSaveEdit by rememberUpdatedState(onSaveEdit)
    val currentOnEditingChange by rememberUpdatedState(onEditingChange)

    fun exitEditMode() {
        if (currentIsEditing) {
            currentOnSaveEdit(draftName, draftDescription)
            currentOnEditingChange(false)
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            draftName = task.taskName
            draftDescription = task.description
            nameFocused = false
            descFocused = false
            focusArmed = false
            nameFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isEditing, focusArmed, nameFocused, descFocused) {
        if (currentIsEditing && focusArmed && !nameFocused && !descFocused) {
            withFrameNanos { }
            withFrameNanos { }
            if (currentIsEditing && focusArmed && !nameFocused && !descFocused) {
                currentOnSaveEdit(draftName, draftDescription)
                currentOnEditingChange(false)
            }
        }
    }

    // Activity-description edit state, hoisted here so it survives inner LazyColumn row disposal.
    var editingActivityId by remember { mutableStateOf<Long?>(null) }
    var activityDraft by remember { mutableStateOf("") }
    var activityFieldFocused by remember { mutableStateOf(false) }
    var activityFocusArmed by remember { mutableStateOf(false) }
    val activityFocusRequester = remember { FocusRequester() }

    val currentOnUpdateActivity by rememberUpdatedState(onUpdateActivity)

    fun clearActivityEdit() {
        editingActivityId = null
        activityFieldFocused = false
        activityFocusArmed = false
    }

    fun commitActivityEdit() {
        val id = editingActivityId ?: return
        val draft = activityDraft
        clearActivityEdit()
        currentOnUpdateActivity(id, draft)
    }

    fun startActivityEdit(activity: ActionItem) {
        exitEditMode()
        commitActivityEdit()
        editingActivityId = activity.id
        activityDraft = activity.description
        activityFieldFocused = false
        activityFocusArmed = false
    }

    LaunchedEffect(editingActivityId) {
        if (editingActivityId != null) {
            activityFieldFocused = false
            activityFocusArmed = false
            runCatching { activityFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(editingActivityId, activityFocusArmed, activityFieldFocused) {
        if (editingActivityId != null && activityFocusArmed && !activityFieldFocused) {
            withFrameNanos { }
            withFrameNanos { }
            if (editingActivityId != null && activityFocusArmed && !activityFieldFocused) {
                commitActivityEdit()
            }
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            TaskCardHeader(
                task = task,
                isEditing = isEditing,
                onHeaderClick = {
                    // Persist any in-progress edits before collapsing the body.
                    exitEditMode()
                    commitActivityEdit()
                    onHeaderClick()
                },
                onEditClick = {
                    if (isEditing) {
                        exitEditMode()
                    } else {
                        commitActivityEdit()
                        onEditingChange(true)
                    }
                },
                onDeleteClick = { showTaskDeleteDialog = true }
            )

            if (isExpanded) {
                TaskCardBody(
                    task = task,
                    isEditing = isEditing,
                    draftName = draftName,
                    draftDescription = draftDescription,
                    onDraftNameChange = { draftName = it },
                    onDraftDescriptionChange = { draftDescription = it },
                    nameFocusRequester = nameFocusRequester,
                    onNameFocusChange = { hasFocus ->
                        nameFocused = hasFocus
                        if (hasFocus) focusArmed = true
                    },
                    onDescFocusChange = { hasFocus ->
                        descFocused = hasFocus
                        if (hasFocus) focusArmed = true
                    },
                    onBodyClick = {
                        commitActivityEdit()
                        onTaskBodyClick()
                    },
                    onToggleActivity = onToggleActivity,
                    onAddActivity = onAddActivity,
                    onDeleteActivity = { pendingActivityDeleteId = it },
                    editingActivityId = editingActivityId,
                    activityDraft = activityDraft,
                    activityFocusRequester = activityFocusRequester,
                    onStartActivityEdit = { startActivityEdit(it) },
                    onActivityDraftChange = { activityDraft = it },
                    onCommitActivityEdit = { commitActivityEdit() },
                    onActivityFieldFocusChange = { hasFocus ->
                        activityFieldFocused = hasFocus
                        if (hasFocus) activityFocusArmed = true
                    }
                )
            }
        }
    }

    if (showTaskDeleteDialog) {
        TaskDeleteDialog(
            onConfirm = {
                showTaskDeleteDialog = false
                onDeleteTask()
            },
            onDismiss = { showTaskDeleteDialog = false }
        )
    }

    pendingActivityDeleteId?.let { activityId ->
        ActivityDeleteDialog(
            onConfirm = {
                pendingActivityDeleteId = null
                if (editingActivityId == activityId) clearActivityEdit()
                onDeleteActivity(activityId)
            },
            onDismiss = { pendingActivityDeleteId = null }
        )
    }
}

@Composable
fun TaskCardHeader(
    task: Task,
    isEditing: Boolean,
    onHeaderClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val editInteractionSource = remember { MutableInteractionSource() }
    val currentOnEditClick by rememberUpdatedState(onEditClick)
    LaunchedEffect(editInteractionSource) {
        editInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) currentOnEditClick()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onHeaderClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {},
            interactionSource = editInteractionSource,
            modifier = Modifier.focusProperties { canFocus = false }
        ) {
            Icon(
                imageVector = if (isEditing) Icons.Filled.Done else Icons.Filled.Edit,
                contentDescription = if (isEditing) "Done editing" else "Edit task"
            )
        }

        Text(
            text = task.taskName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.focusProperties { canFocus = false }
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Delete task",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun TaskCardBody(
    task: Task,
    isEditing: Boolean,
    draftName: String,
    draftDescription: String,
    onDraftNameChange: (String) -> Unit,
    onDraftDescriptionChange: (String) -> Unit,
    nameFocusRequester: FocusRequester,
    onNameFocusChange: (Boolean) -> Unit,
    onDescFocusChange: (Boolean) -> Unit,
    onBodyClick: () -> Unit,
    onToggleActivity: (Long, Boolean) -> Unit,
    onAddActivity: () -> Unit,
    onDeleteActivity: (Long) -> Unit,
    editingActivityId: Long?,
    activityDraft: String,
    activityFocusRequester: FocusRequester,
    onStartActivityEdit: (ActionItem) -> Unit,
    onActivityDraftChange: (String) -> Unit,
    onCommitActivityEdit: () -> Unit,
    onActivityFieldFocusChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isEditing) Modifier.clickable { onBodyClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (isEditing) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = onDraftNameChange,
                    label = { Text("Task name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
                        .onFocusChanged { onNameFocusChange(it.hasFocus) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = draftDescription,
                    onValueChange = onDraftDescriptionChange,
                    label = { Text("Task description") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onDescFocusChange(it.hasFocus) }
                )
            }
        } else if (task.description.isNotBlank()) {
            Text(
                task.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(task.activities, key = { it.id }) { activity ->
                ActivityRow(
                    activity = activity,
                    isEditing = editingActivityId == activity.id,
                    editDraft = activityDraft,
                    focusRequester = activityFocusRequester,
                    onCheckedChange = { onToggleActivity(activity.id, it) },
                    onStartEdit = { onStartActivityEdit(activity) },
                    onEditDraftChange = onActivityDraftChange,
                    onCommitEdit = onCommitActivityEdit,
                    onFieldFocusChange = onActivityFieldFocusChange,
                    onDelete = { onDeleteActivity(activity.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onAddActivity) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add activity")
        }
    }
}

@Composable
fun ActivityRow(
    activity: ActionItem,
    isEditing: Boolean,
    editDraft: String,
    focusRequester: FocusRequester,
    onCheckedChange: (Boolean) -> Unit,
    onStartEdit: () -> Unit,
    onEditDraftChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onFieldFocusChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val editInteractionSource = remember { MutableInteractionSource() }
    val currentIsEditing by rememberUpdatedState(isEditing)
    val currentOnStartEdit by rememberUpdatedState(onStartEdit)
    val currentOnCommitEdit by rememberUpdatedState(onCommitEdit)
    LaunchedEffect(editInteractionSource) {
        editInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                if (currentIsEditing) currentOnCommitEdit() else currentOnStartEdit()
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = activity.achieved,
            onCheckedChange = onCheckedChange
        )
        if (isEditing) {
            OutlinedTextField(
                value = editDraft,
                onValueChange = onEditDraftChange,
                label = { Text("Activity description") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommitEdit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFieldFocusChange(it.hasFocus) }
            )
        } else {
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
                },
                modifier = Modifier.weight(1f)
            )
        }
        IconButton(
            onClick = {},
            interactionSource = editInteractionSource,
            modifier = Modifier.focusProperties { canFocus = false }
        ) {
            Icon(
                imageVector = if (isEditing) Icons.Filled.Done else Icons.Filled.Edit,
                contentDescription = if (isEditing) "Done editing activity" else "Edit activity"
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete activity",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun TaskDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete task?") },
        text = { Text("This will delete the task and all of its activities.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ActivityDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete activity?") },
        text = { Text("This will remove this activity from the task.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
