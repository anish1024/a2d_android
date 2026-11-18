package com.a2d.app.ui.task

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
    onCreateSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val expandedStates = remember { mutableStateMapOf<Long, Boolean>() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (milestoneOutput == 1) "Milestone Complete [GATE 1]" else "Milestone Incomplete [GATE 0]",
                    color = if (milestoneOutput == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onCreateSample) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("+ Task")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    isExpanded = expandedStates[task.id] ?: true,
                    onHeaderClick = {
                        expandedStates[task.id] = !(expandedStates[task.id] ?: true)
                    },
                    onTaskBodyClick = { onTaskClick(task.id) },
                    onToggleActivity = { activityId, achieved ->
                        onToggleActivity(task.id, activityId, achieved)
                    },
                    onAddActivity = { onAddActivity(task.id) },
                    onDeleteActivity = { activityId -> onDeleteActivity(task.id, activityId) },
                    onDeleteTask = { onDeleteTask(task.id) },
                    onExportCsv = { exportTaskToCsv(context, task) },
                    onImportCsv = { /* placeholder for future file picker */ },
                    onMoveActivityUp = { taskId ->
                        moveActivity(taskId, true)
                    },
                    onMoveActivityDown = { taskId ->
                        moveActivity(taskId, false)
                    },
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
fun TaskCard(
    task: Task,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    onTaskBodyClick: () -> Unit,
    onToggleActivity: (Long, Boolean) -> Unit,
    onAddActivity: () -> Unit,
    onDeleteActivity: (Long) -> Unit,
    onDeleteTask: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onMoveActivityUp: (Long) -> Unit,
    onMoveActivityDown: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTaskDeleteDialog by remember { mutableStateOf(false) }
    var pendingActivityDeleteId by remember { mutableStateOf<Long?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeaderClick() }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.taskName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onDeleteTask() }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Delete task",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            onClick = {
                                showMenu = false
                                onExportCsv()
                            },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Import CSV") },
                            onClick = {
                                showMenu = false
                                onImportCsv()
                            },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            onClick = {
                                showMenu = false
                                onMoveActivityUp(task.id)
                            },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            onClick = {
                                showMenu = false
                                onMoveActivityDown(task.id)
                            },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                showTaskDeleteDialog = true
                            },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }
                        )
                    }
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

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

    if (showImportDialog) {
        // Placeholder: import UI would be added here later
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import CSV") },
            text = { Text("Importing activities from CSV is not yet implemented.") },
            confirmButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("OK")
                }
            }
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

/** Export a task's activities to a CSV file (simplified implementation). */
private fun exportTaskToCsv(context: android.content.Context, task: Task) {
    // Simplified: just log / show toast (real implementation would write file via SAF)
    Toast.makeText(context, "CSV export: ${task.activities.size} activities", Toast.LENGTH_SHORT).show()
}

/** Move an activity within its task's activity list. */
private fun moveActivity(taskId: Long, up: Boolean) {
    // Placeholder: real implementation would update Room entities
}

/** Placeholder for importing activities from CSV. */
private fun importTaskCsv(taskId: Long, csv: String): Int {
    return 0
}