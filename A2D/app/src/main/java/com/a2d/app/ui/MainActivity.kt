package com.a2d.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.a2d.app.ui.logicgate.LogicGateScreen
import com.a2d.app.ui.logicgate.LogicGateViewModel
import com.a2d.app.ui.task.TaskScreen
import com.a2d.app.ui.task.TaskViewModel
import com.a2d.app.ui.theme.A2DTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            A2DTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text("Tasks") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text("Logic Gate") },
                                icon = {}
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        if (selectedTab == 0) {
                            val taskVm: TaskViewModel = hiltViewModel()
                            val tasks by taskVm.tasks.collectAsState()
                            val milestone by taskVm.milestoneOutput.collectAsState()

                            TaskScreen(
                                tasks = tasks,
                                milestoneOutput = milestone,
                                onToggleAction = { task, id -> taskVm.toggleActionItem(task, id) },
                                onDeleteTask = { id -> taskVm.deleteTask(id) },
                                onCreateSample = {
                                    val newId = System.currentTimeMillis()
                                    taskVm.createSampleTask(newId, "Task #$newId", 1)
                                }
                            )
                        } else {
                            val gateVm: LogicGateViewModel = hiltViewModel()
                            val result by gateVm.lastResult.collectAsState()
                            val error by gateVm.errorMessage.collectAsState()

                            LogicGateScreen(
                                lastResult = result,
                                errorMessage = error,
                                onSendPulse = { pin, pulse -> gateVm.sendPulse(pin, pulse) }
                            )
                        }
                    }
                }
            }
        }
    }
}
