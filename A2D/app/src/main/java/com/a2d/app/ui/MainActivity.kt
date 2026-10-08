package com.a2d.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.a2d.app.ui.logicgate.LogicGateScreen
import com.a2d.app.ui.logicgate.LogicGateViewModel
import com.a2d.app.ui.task.TaskDetailScreen
import com.a2d.app.ui.task.TaskListScreen
import com.a2d.app.ui.task.TaskViewModel
import com.a2d.app.ui.theme.A2DTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            A2DTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val onTaskList = currentRoute == "taskList" || currentRoute?.startsWith("taskDetail/") == true

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = onTaskList,
                                onClick = {
                                    navController.navigate("taskList") {
                                        popUpTo("taskList") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                label = { Text("Tasks") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = currentRoute == "logicGate",
                                onClick = {
                                    navController.navigate("logicGate") {
                                        popUpTo("taskList") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                label = { Text("Logic Gate") },
                                icon = {}
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "taskList",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("taskList") {
                            val taskVm: TaskViewModel = hiltViewModel()
                            val tasks by taskVm.tasks.collectAsState()
                            val milestone by taskVm.milestoneOutput.collectAsState()

                            TaskListScreen(
                                tasks = tasks,
                                milestoneOutput = milestone,
                                onTaskClick = { taskId ->
                                    navController.navigate("taskDetail/$taskId")
                                },
                                onToggleActivity = { taskId, activityId, achieved ->
                                    taskVm.toggleActivity(taskId, activityId, achieved)
                                },
                                onAddActivity = { taskId -> taskVm.addActivity(taskId) },
                                onDeleteActivity = { taskId, activityId ->
                                    taskVm.deleteActivity(taskId, activityId)
                                },
                                onDeleteTask = { taskId -> taskVm.deleteTask(taskId) },
                                onCreateSample = {
                                    val newId = System.currentTimeMillis()
                                    taskVm.createSampleTask(newId, "Task #$newId", 1)
                                }
                            )
                        }
                        composable(
                            route = "taskDetail/{taskId}",
                            arguments = listOf(navArgument("taskId") { type = NavType.LongType })
                        ) { entry ->
                            val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                            val taskVm: TaskViewModel = hiltViewModel()
                            val currentTask by taskVm.currentTask.collectAsState()

                            LaunchedEffect(taskId) { taskVm.selectTask(taskId) }

                            TaskDetailScreen(
                                taskId = taskId,
                                task = currentTask,
                                onNavigateBack = { navController.navigateUp() },
                                onToggleActivity = { id, activityId, achieved ->
                                    taskVm.toggleActivity(id, activityId, achieved)
                                },
                                onAddActivity = { id -> taskVm.addActivity(id) },
                                onDeleteActivity = { id, activityId ->
                                    taskVm.deleteActivity(id, activityId)
                                },
                                onDeleteTask = { id ->
                                    taskVm.deleteTask(id)
                                    navController.navigateUp()
                                }
                            )
                        }
                        composable("logicGate") {
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
