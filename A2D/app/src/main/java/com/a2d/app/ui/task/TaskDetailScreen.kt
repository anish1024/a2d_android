package com.a2d.app.ui.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a2d.app.domain.model.Task

@Composable
fun TaskDetailScreen(
    taskId: Long,
    task: Task?,
    onNavigateBack: () -> Unit,
    onToggleActivity: (Long, Long, Boolean) -> Unit,
    onAddActivity: (Long) -> Unit,
    onDeleteActivity: (Long, Long) -> Unit,
    onDeleteTask: (Long) -> Unit
) {
    var showTaskDeleteDialog by remember { mutableStateOf(false) }
    var pendingActivityDeleteId by remember { mutableStateOf<Long?>(null) }

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
                        onCheckedChange = {
                            onToggleActivity(taskId, activity.id, it)
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
