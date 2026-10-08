package com.a2d.app.ui.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
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
    onCreateSample: () -> Unit
) {
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
                    onTaskClick = { onTaskClick(task.id) },
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
    onTaskClick: () -> Unit,
    onToggleActivity: (Long, Boolean) -> Unit,
    onAddActivity: () -> Unit,
    onDeleteActivity: (Long) -> Unit,
    onDeleteTask: () -> Unit
) {
    var showTaskDeleteDialog by remember { mutableStateOf(false) }
    var pendingActivityDeleteId by remember { mutableStateOf<Long?>(null) }

    ElevatedCard(
        onClick = onTaskClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showTaskDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.taskName, style = MaterialTheme.typography.titleMedium)
                    if (task.description.isNotBlank()) {
                        Text(
                            task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                        onCheckedChange = { onToggleActivity(activity.id, it) },
                        onDelete = { pendingActivityDeleteId = activity.id }
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
                onDeleteActivity(activityId)
            },
            onDismiss = { pendingActivityDeleteId = null }
        )
    }
}

@Composable
fun ActivityRow(
    activity: ActionItem,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = activity.achieved,
            onCheckedChange = onCheckedChange
        )
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
