package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.TaskItem
import com.example.data.TaskListItem
import com.example.data.UiState
import com.example.data.signOut
import com.example.ui.TasksViewModel
import com.example.ui.components.ListEditorSheet
import com.example.ui.components.TaskEditorSheet
import com.google.firebase.auth.FirebaseUser

private enum class WorkspaceTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    TASKS(R.string.nav_tasks, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle, "nav_tasks"),
    LISTS(R.string.nav_lists, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_lists"),
    CLOUD(R.string.nav_cloud, Icons.Filled.Cloud, Icons.Outlined.Cloud, "nav_cloud")
}

@Composable
fun MainWorkspaceScreen(
    user: FirebaseUser,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TasksViewModel = viewModel(
        key = user.uid,
        factory = TasksViewModel.factory(user.uid)
    )

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(WorkspaceTab.TASKS) }
    var showTaskEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var showListEditor by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<TaskListItem?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Ensure Android Back button returns from secondary tabs to Tasks tab cleanly
    BackHandler(enabled = currentTab != WorkspaceTab.TASKS) {
        currentTab = WorkspaceTab.TASKS
    }

    val availableLists = (dashboardState as? UiState.Success)?.data?.lists ?: emptyList()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        WorkspaceTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                when (currentTab) {
                    WorkspaceTab.TASKS -> {
                        ExtendedFloatingActionButton(
                            onClick = {
                                editingTask = null
                                showTaskEditor = true
                            },
                            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.action_new_task)) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_new_task")
                        )
                    }
                    WorkspaceTab.LISTS -> {
                        ExtendedFloatingActionButton(
                            onClick = {
                                editingList = null
                                showListEditor = true
                            },
                            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.action_new_list)) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_new_list")
                        )
                    }
                    WorkspaceTab.CLOUD -> Unit
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            if (currentTab != WorkspaceTab.CLOUD) {
                                FloatingActionButton(
                                    onClick = {
                                        if (currentTab == WorkspaceTab.LISTS) {
                                            editingList = null
                                            showListEditor = true
                                        } else {
                                            editingTask = null
                                            showTaskEditor = true
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = stringResource(R.string.action_new_task)
                                    )
                                }
                                Spacer(modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    ) {
                        WorkspaceTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentTab) {
                        WorkspaceTab.TASKS -> TasksTab(
                            userName = user.displayName,
                            dashboardState = dashboardState,
                            filterState = filterState,
                            onFilterChange = viewModel::setFilter,
                            onSelectList = viewModel::selectList,
                            onSearchQueryChange = viewModel::setSearchQuery,
                            onSortOptionChange = viewModel::setSortOption,
                            onToggleTask = viewModel::toggleTaskCompleted,
                            onEditTask = { task ->
                                editingTask = task
                                showTaskEditor = true
                            },
                            onDeleteTask = { task ->
                                viewModel.deleteTask(task.id)
                            },
                            onClearCompleted = viewModel::clearCompletedTasks,
                            onCreateTaskClick = {
                                editingTask = null
                                showTaskEditor = true
                            }
                        )

                        WorkspaceTab.LISTS -> ListsTab(
                            dashboardState = dashboardState,
                            onOpenListTasks = { list ->
                                viewModel.selectList(list.id)
                                currentTab = WorkspaceTab.TASKS
                            },
                            onEditList = { list ->
                                editingList = list
                                showListEditor = true
                            },
                            onCreateListClick = {
                                editingList = null
                                showListEditor = true
                            }
                        )

                        WorkspaceTab.CLOUD -> CloudAccountTab(
                            displayName = user.displayName,
                            email = user.email,
                            photoUrl = user.photoUrl?.toString(),
                            currentDeviceName = viewModel.currentDeviceName,
                            dashboardState = dashboardState,
                            onSignOutClick = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showTaskEditor) {
        val currentEditing = editingTask
        TaskEditorSheet(
            existingTask = currentEditing,
            availableLists = availableLists,
            initialListId = filterState.selectedListId,
            isSaving = isSaving,
            onDismiss = { showTaskEditor = false },
            onSave = { title, notes, priority, listId, categoryLabel, dueDateMillis ->
                if (currentEditing == null) {
                    viewModel.createTask(
                        title = title,
                        notes = notes,
                        priority = priority,
                        listId = listId,
                        categoryLabel = categoryLabel,
                        dueDateMillis = dueDateMillis,
                        onDone = { showTaskEditor = false }
                    )
                } else {
                    viewModel.updateTask(
                        taskId = currentEditing.id,
                        title = title,
                        notes = notes,
                        completed = currentEditing.completed,
                        priority = priority,
                        listId = listId,
                        categoryLabel = categoryLabel,
                        dueDateMillis = dueDateMillis,
                        onDone = { showTaskEditor = false }
                    )
                }
            },
            onDelete = currentEditing?.let { task ->
                {
                    viewModel.deleteTask(task.id) {
                        showTaskEditor = false
                    }
                }
            }
        )
    }

    if (showListEditor) {
        val currentEditingList = editingList
        ListEditorSheet(
            existingList = currentEditingList,
            isSaving = isSaving,
            onDismiss = { showListEditor = false },
            onSave = { name, colorHex, iconKey ->
                if (currentEditingList == null) {
                    viewModel.createList(
                        name = name,
                        colorHex = colorHex,
                        iconKey = iconKey,
                        onDone = { showListEditor = false }
                    )
                } else {
                    viewModel.updateList(
                        listId = currentEditingList.id,
                        name = name,
                        colorHex = colorHex,
                        iconKey = iconKey,
                        onDone = { showListEditor = false }
                    )
                }
            },
            onDelete = currentEditingList?.let { list ->
                {
                    viewModel.deleteList(list.id) {
                        showListEditor = false
                    }
                }
            }
        )
    }
}
