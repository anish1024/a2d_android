package com.a2d.app.ui.task

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.a2d.app.domain.model.ActionItem
import com.a2d.app.domain.model.Task

@Composable
fun TaskDetailScreen(
    taskId: Long,
    task: Task?,
    onNavigateBack: () -> Unit,
    onToggleActivity: (Long, Long, Boolean) -> Unit,
    onAddActivity: (Long) -> Unit,
    onDeleteActivity: (Long, Long) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onUpdateActivity: (Long, Long, String) -> Unit
) {
    var showTaskDeleteDialog by remember { mutableStateOf(false) }
    var pendingActivityDeleteId by remember { mutableStateOf<Long?>(null) }

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
        currentOnUpdateActivity(taskId, id, draft)
    }

    fun startActivityEdit(activity: ActionItem) {
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showTaskDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete task",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (task == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            TaskDetailHeader(task = task)

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(task.activities, key = { it.id }) { activity ->
                    ActivityRow(
                        activity = activity,
                        isEditing = editingActivityId == activity.id,
                        editDraft = activityDraft,
                        focusRequester = activityFocusRequester,
                        onCheckedChange = {
                            onToggleActivity(taskId, activity.id, it)
                        },
                        onStartEdit = { startActivityEdit(activity) },
                        onEditDraftChange = { activityDraft = it },
                        onCommitEdit = { commitActivityEdit() },
                        onFieldFocusChange = { hasFocus ->
                            activityFieldFocused = hasFocus
                            if (hasFocus) activityFocusArmed = true
                        },
                        onDelete = { pendingActivityDeleteId = activity.id }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { onAddActivity(taskId) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add activity")
            }
        }
    }

    if (showTaskDeleteDialog) {
        TaskDeleteDialog(
            onConfirm = {
                showTaskDeleteDialog = false
                onDeleteTask(taskId)
            },
            onDismiss = { showTaskDeleteDialog = false }
        )
    }

    pendingActivityDeleteId?.let { activityId ->
        ActivityDeleteDialog(
            onConfirm = {
                pendingActivityDeleteId = null
                if (editingActivityId == activityId) clearActivityEdit()
                onDeleteActivity(taskId, activityId)
            },
            onDismiss = { pendingActivityDeleteId = null }
        )
    }
}

@Composable
fun TaskDetailHeader(task: Task) {
    val isCompleted = task.activities.isNotEmpty() && task.activities.all { it.achieved }

    val statusText = if (isCompleted) "Completed" else "In progress"
    val statusColor = if (isCompleted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column {
        Text(task.taskName, style = MaterialTheme.typography.headlineSmall)
        if (task.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                task.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = statusColor,
                contentColor = if (isCompleted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
