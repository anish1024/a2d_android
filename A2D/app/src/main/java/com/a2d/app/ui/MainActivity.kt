@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.a2d.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.a2d.app.ui.auth.AuthViewModel
import com.a2d.app.ui.auth.LogoutState
import androidx.navigation.navArgument
import com.a2d.app.ui.logicgate.LogicGateScreen
import com.a2d.app.ui.logicgate.LogicGateViewModel
import com.a2d.app.ui.login.LoginScreen
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
                // onLogin removed; login is no longer the start destination
                val context = LocalContext.current
                val authVm: AuthViewModel = hiltViewModel()
                val logoutState by authVm.logoutState.collectAsState()
                LaunchedEffect(logoutState) {
                    if (logoutState is LogoutState.Success) {
                        authVm.resetLogoutState()
                        navController.navigate("login") {
                            popUpTo("taskList") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                
                // Auth gate - prevent access to taskList and logicGate without authentication
                LaunchedEffect(authVm.isAuthenticated.value) {
                    val isAuth = authVm.isAuthenticated.value
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    
                    if (!isAuth && (currentRoute == "taskList" || currentRoute?.startsWith("taskDetail/") == true || currentRoute == "logicGate")) {
                        navController.navigate("login") {
                            popUpTo("taskList") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (authVm.isAuthenticated.value) {
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
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "taskList",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("login") {
                            LoginScreen(
                                onContinue = {
                                    navController.navigate("taskList") {
                                        popUpTo("login") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable("taskList") {
                            val taskVm: TaskViewModel = hiltViewModel()
                            val tasks by taskVm.tasks.collectAsState()
                            val milestone by taskVm.milestoneOutput.collectAsState()
                            var showMenu by remember { mutableStateOf(false) }
                            val coroutineScope = rememberCoroutineScope()

                            Scaffold(
                                topBar = {
                                    TopAppBar(
                                        title = { Text("A2D Focus Tasks") },
                                        actions = {
                                            IconButton(onClick = { showMenu = true }) {
                                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                                            }
                                            DropdownMenu(
                                                expanded = showMenu,
                                                onDismissRequest = { showMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Sync") },
                                                    onClick = {
                                                        showMenu = false
                                                        coroutineScope.launch {
                                                            val message = taskVm.syncNow()
                                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Sign Out") },
                                                    onClick = {
                                                        showMenu = false
                                                        authVm.logout()
                                                    },
                                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) }
                                                )
                                            }
                                        }
                                    )
                                }
                            ) { innerPadding ->
                                TaskListScreen(
                                    tasks = tasks,
                                    milestoneOutput = milestone,
                                    welcomeText = authVm.userEmail,
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
                                    onSaveEdit = { taskId, name, description ->
                                        taskVm.updateTask(taskId, name, description)
                                    },
                                    onUpdateActivity = { taskId, activityId, description ->
                                        taskVm.updateActivity(taskId, activityId, description)
                                    },
                                    onMoveTask = { taskId, up -> taskVm.moveTask(taskId, up) },
                                    onMoveTaskActivity = { taskId, activityId, up ->
                                        taskVm.moveActivity(taskId, activityId, up)
                                    },
                                    onCreateSample = {
                                        val newId = System.currentTimeMillis()
                                        taskVm.createSampleTask(newId, "Task #$newId", 1)
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
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
                                },
                                onMoveTaskActivity = { taskId, activityId, up ->
                                    taskVm.moveActivity(taskId, activityId, up)
                                },
                                onUpdateActivity = { id, activityId, newDesc ->
                                    taskVm.updateActivity(id, activityId, newDesc)
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
