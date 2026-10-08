package com.a2d.app.ui.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a2d.app.domain.model.Task

@Composable
fun TaskScreen(
    tasks: List<Task>,
    milestoneOutput: Int,
    onToggleAction: (Task, Long) -> Unit,
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
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(task.taskName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (task.achieved) "COMPLETED" else "PRIORITY ${task.priority}",
                                color = if (task.achieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Text(task.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        task.activities.forEach { action ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = action.achieved,
                                    onCheckedChange = { onToggleAction(task, action.id) }
                                )
                                Text(action.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        TextButton(
                            onClick = { onDeleteTask(task.id) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
