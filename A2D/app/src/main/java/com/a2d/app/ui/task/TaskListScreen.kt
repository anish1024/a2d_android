@file:OptIn(ExperimentalFoundationApi::class)

package com.a2d.app.ui.task

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
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
    onMoveTask: (Long, Boolean) -> Unit,
    onMoveTaskActivity: (Long, Long, Boolean) -> Unit,
    onCreateSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val expandedStates = remember { mutableStateMapOf<Long, Boolean>() }
    val editingStates = remember { mutableStateMapOf<Long, Boolean>() }
    var draggingTaskId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (milestoneOutput == 1) "Milestone Complete [GATE 1]" else "Milestone Incomplete [GATE 0]",
                color = if (milestoneOutput == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onCreateSample) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Task")
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
                    onDeleteTask = { onDeleteTask(task.id) },
                    onExportCsv = { exportTaskToCsv(context, task) },
                    onImportCsv = { showImportPlaceholder(context) },
                    onMoveTask = { taskId, up -> onMoveTask(taskId, up) },
                    onMoveTaskActivity = { taskId, activityId, up -> onMoveTaskActivity(taskId, activityId, up) },
                    isDragging = draggingTaskId == task.id,
                    onDragStateChange = { dragging ->
                        draggingTaskId = if (dragging) task.id else null
                    },
                    modifier = Modifier.animateItem()
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
    onDeleteTask: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onMoveTask: (Long, Boolean) -> Unit,
    onMoveTaskActivity: (Long, Long, Boolean) -> Unit,
    isDragging: Boolean,
    onDragStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTaskDeleteDialog by remember { mutableStateOf(false) }
    var pendingActivityDeleteId by remember { mutableStateOf<Long?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var draggingActivityId by remember { mutableStateOf<Long?>(null) }
    val activityListState = rememberLazyListState()

    var draftName by remember(task.id) { mutableStateOf(task.taskName) }
    var draftDescription by remember(task.id) { mutableStateOf(task.description) }

    fun exitEditMode(saveChanges: Boolean = true) {
        if (isEditing) {
            if (saveChanges) {
                onSaveEdit(draftName, draftDescription)
            }
            onEditingChange(false)
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    color = Color(0xFF555555),
                    width = 2.dp
                )
            ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isDragging) 12.dp else 1.dp
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle for task reordering - only visible in edit mode
                if (isEditing) {
                    DragHandle(
                        onMoveUp = { onMoveTask(task.id, true) },
                        onMoveDown = { onMoveTask(task.id, false) },
                        onDragStateChange = onDragStateChange
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onHeaderClick() }
                ) {
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
                        text = { Text(if (isEditing) "Save" else "Edit Task") },
                        onClick = {
                            showMenu = false
                            if (isEditing) exitEditMode() else onEditingChange(true)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isEditing) Icons.Filled.Done else Icons.Filled.Edit,
                                contentDescription = null
                            )
                        }
                    )
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
                            onMoveTask(task.id, true)
                        },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Move Down") },
                        onClick = {
                            showMenu = false
                            onMoveTask(task.id, false)
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

            // Divider separating task header from activity list body
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                thickness = 1.dp,
                color = Color(0xFFC5EDE9)
            )

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = draftName,
                            onValueChange = { draftName = it },
                            label = { Text("Task name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draftDescription,
                            onValueChange = { draftDescription = it },
                            label = { Text("Task description") },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        LazyColumn(
                            state = activityListState,
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(task.activities, key = { it.id }) { activity ->
                                ActivityRow(
                                    activity = activity,
                                    onMoveUp = { onMoveTaskActivity(task.id, activity.id, true) },
                                    onMoveDown = { onMoveTaskActivity(task.id, activity.id, false) },
                                    onToggleActivity = { onToggleActivity(activity.id, it) },
                                    onDelete = { onDeleteActivity(activity.id) },
                                    onUpdateActivity = { newDesc -> onUpdateActivity(activity.id, newDesc) },
                                    isEditing = isEditing,
                                    isDragging = draggingActivityId == activity.id,
                                    onDragStateChange = { dragging ->
                                        draggingActivityId = if (dragging) activity.id else null
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        TaskActivityScrollbar(
                            listState = activityListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                        )
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
}

/**
 * A compact, Android-compatible vertical scrollbar that works with [LazyListState].
 * Draws a thin track plus a proportional thumb that tracks the scroll position, and only
 * renders when the list is actually scrollable. (Compose Desktop's `VerticalScrollbar` /
 * `rememberScrollbarAdapter` are not available on Android.)
 */
@Composable
fun TaskActivityScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val scrollable by remember {
        derivedStateOf { listState.canScrollBackward || listState.canScrollForward }
    }
    if (!scrollable) return

    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems == 0 || visibleItems.isEmpty()) return

    val visibleCount = visibleItems.size
    val thumbFraction = (visibleCount.toFloat() / totalItems).coerceIn(0.08f, 1f)
    val firstIndex = visibleItems.first().index
    val maxFirstIndex = (totalItems - visibleCount).coerceAtLeast(1)
    val scrollFraction = (firstIndex.toFloat() / maxFirstIndex).coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier.width(6.dp)) {
        val trackHeight = maxHeight
        val thumbHeight = trackHeight * thumbFraction
        val thumbTop = (trackHeight - thumbHeight) * scrollFraction

        // Track (subtle background)
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
        )
        // Thumb (scroll position indicator)
        Box(
            Modifier
                .offset(y = thumbTop)
                .fillMaxWidth()
                .height(thumbHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        )
    }
}

@Composable
fun ActivityRow(
    activity: ActionItem,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleActivity: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onUpdateActivity: (String) -> Unit,
    isEditing: Boolean = false,
    isDragging: Boolean = false,
    onDragStateChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf(false) }
    var draftDescription by remember(activity.id) { mutableStateOf(activity.description) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
            .graphicsLayer {
                alpha = if (isDragging) 0.9f else 1f
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
    ) {
        // Drag handle for activity reordering - only visible in edit mode
        if (isEditing) {
            DragHandle(
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDragStateChange = onDragStateChange
            )
        }
        Text(
            text = if (editingActivity) draftDescription else activity.description,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = TextUnit(
                    MaterialTheme.typography.titleMedium.fontSize.value - 1f,
                    TextUnitType.Sp
                ),
            ),
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
            modifier = Modifier
                .weight(1f)
                .clickable { onToggleActivity(!activity.achieved) }
        )
        // 3-dot menu for Edit/Delete
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
                text = { Text("Edit") },
                onClick = {
                    showMenu = false
                    editingActivity = true
                },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }
            )
        }
    }

    // Edit mode for activity description
    if (editingActivity) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = draftDescription,
                onValueChange = { draftDescription = it },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { editingActivity = false }) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    onUpdateActivity(draftDescription)
                    editingActivity = false
                }) {
                    Text("Save")
                }
            }
        }
    }
}

/**
 * Drag handle that allows reordering items by dragging up/down.
 * Uses vertical drag gesture detection: each time the user drags by one item height,
 * the corresponding move callback is invoked once. The icon highlights and lifts while
 * dragging so the interaction feels responsive.
 */
@Composable
private fun DragHandle(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStateChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    val accumulatedOffset = remember { floatArrayOf(0f) }
    val density = LocalDensity.current
    val itemHeight = with(density) { 48.dp.toPx() }

    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = if (isDragging) 1.15f else 1f
                scaleY = if (isDragging) 1.15f else 1f
                alpha = if (isDragging) 0.85f else 1f
            }
            .clip(CircleShape)
            .background(
                if (isDragging) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        onDragStateChange(true)
                        accumulatedOffset[0] = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragStateChange(false)
                        accumulatedOffset[0] = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        onDragStateChange(false)
                        accumulatedOffset[0] = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedOffset[0] += dragAmount
                        while (accumulatedOffset[0] >= itemHeight) {
                            accumulatedOffset[0] -= itemHeight
                            onMoveDown()
                        }
                        while (accumulatedOffset[0] <= -itemHeight) {
                            accumulatedOffset[0] += itemHeight
                            onMoveUp()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { /* consume tap on drag handle */ })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "Drag to reorder",
            tint = if (isDragging) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
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

/** Export a task's activities to CSV (placeholder until SAF file write is wired). */
private fun exportTaskToCsv(context: android.content.Context, task: Task) {
    Toast.makeText(
        context,
        "CSV export: ${task.activities.size} activities (backend integration pending)",
        Toast.LENGTH_SHORT
    ).show()
}

/** Import activities from CSV (placeholder until SAF file pick is wired). */
private fun showImportPlaceholder(context: android.content.Context) {
    Toast.makeText(context, "CSV import pending backend integration", Toast.LENGTH_SHORT).show()
}