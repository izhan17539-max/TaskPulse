package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.TaskItem
import com.example.data.TaskListItem
import com.example.data.UiState
import com.example.ui.TaskFilter
import com.example.ui.TaskSortOption
import com.example.ui.TasksDashboardData
import com.example.ui.TasksFilterState
import com.example.ui.components.OverviewProgressCard
import com.example.ui.components.SyncStatusPill
import com.example.ui.components.TaskCard
import com.example.ui.components.parseColorHexSafe

@Composable
fun TasksTab(
    userName: String?,
    dashboardState: UiState<TasksDashboardData>,
    filterState: TasksFilterState,
    onFilterChange: (TaskFilter) -> Unit,
    onSelectList: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOptionChange: (TaskSortOption) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onClearCompleted: (List<TaskItem>) -> Unit,
    onCreateTaskClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSearchBar by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (dashboardState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Sincronizando tus tareas…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.error_generic_sync),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = dashboardState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            is UiState.Success -> {
                val data = dashboardState.data
                val listMap = remember(data.lists) { data.lists.associateBy { it.id } }
                val selectedList: TaskListItem? = filterState.selectedListId?.let { listMap[it] }

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header row
                    item(key = "header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (!userName.isNullOrBlank()) {
                                        "Hola, ${userName.substringBefore(" ")}"
                                    } else {
                                        "Mis tareas"
                                    },
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                SyncStatusPill(status = data.syncStatus)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        showSearchBar = !showSearchBar
                                        if (!showSearchBar) onSearchQueryChange("")
                                    },
                                    modifier = Modifier.testTag("toggle_search_button")
                                ) {
                                    Icon(
                                        imageVector = if (showSearchBar) Icons.Outlined.Close else Icons.Outlined.Search,
                                        contentDescription = stringResource(R.string.cd_search_tasks)
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier.testTag("sort_tasks_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Sort,
                                            contentDescription = "Ordenar tareas"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        TaskSortOption.entries.forEach { option ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = if (filterState.sortOption == option) {
                                                            "✓ ${option.labelEs}"
                                                        } else {
                                                            option.labelEs
                                                        }
                                                    )
                                                },
                                                onClick = {
                                                    onSortOptionChange(option)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search field
                    item(key = "search") {
                        AnimatedVisibility(visible = showSearchBar) {
                            OutlinedTextField(
                                value = filterState.searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Buscar por título, nota o categoría…") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Outlined.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (filterState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = stringResource(R.string.cd_clear_search)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_tasks_input")
                            )
                        }
                    }

                    // Overview card
                    item(key = "overview") {
                        OverviewProgressCard(
                            pendingCount = data.pendingCount,
                            completedCount = data.completedCount,
                            highPriorityPendingCount = data.highPriorityPendingCount
                        )
                    }

                    // Filter chips
                    item(key = "filters") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(TaskFilter.entries, key = { it.name }) { filter ->
                                    FilterChip(
                                        selected = filterState.filter == filter,
                                        onClick = { onFilterChange(filter) },
                                        label = { Text(filter.labelEs) },
                                        modifier = Modifier.testTag("filter_${filter.name}")
                                    )
                                }
                            }

                            if (selectedList != null) {
                                InputChip(
                                    selected = true,
                                    onClick = { onSelectList(null) },
                                    label = { Text("Lista: ${selectedList.name}") },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    parseColorHexSafe(
                                                        selectedList.colorHex,
                                                        MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Quitar filtro de lista",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Section header + clear completed action
                    if (data.filteredTasks.isNotEmpty() || data.completedCount > 0) {
                        item(key = "section_actions") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${data.filteredTasks.size} tareas visibles",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (data.completedCount > 0) {
                                    TextButton(
                                        onClick = { onClearCompleted(data.allTasks) },
                                        modifier = Modifier.testTag("clear_completed_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteSweep,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Limpiar completadas (${data.completedCount})")
                                    }
                                }
                            }
                        }
                    }

                    if (data.filteredTasks.isEmpty()) {
                        item(key = "empty") {
                            EmptyTasksState(
                                hasAnyTasks = data.allTasks.isNotEmpty(),
                                onCreateTaskClick = onCreateTaskClick,
                                onResetFilters = {
                                    onFilterChange(TaskFilter.ALL)
                                    onSelectList(null)
                                    onSearchQueryChange("")
                                }
                            )
                        }
                    } else {
                        items(
                            items = data.filteredTasks,
                            key = { it.id }
                        ) { task ->
                            val accent = task.listId?.let { id ->
                                listMap[id]?.let {
                                    parseColorHexSafe(it.colorHex, MaterialTheme.colorScheme.primary)
                                }
                            }
                            TaskCard(
                                task = task,
                                listAccentColor = accent,
                                onToggleComplete = { onToggleTask(task) },
                                onClick = { onEditTask(task) },
                                onDelete = { onDeleteTask(task) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTasksState(
    hasAnyTasks: Boolean,
    onCreateTaskClick: () -> Unit,
    onResetFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_tasks),
            contentDescription = stringResource(R.string.cd_empty_tasks),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .height(175.dp)
                .clip(MaterialTheme.shapes.large)
        )
        Text(
            text = if (hasAnyTasks) "Sin coincidencias" else stringResource(R.string.empty_tasks_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (hasAnyTasks) {
                "No hay tareas que coincidan con el filtro o búsqueda actual."
            } else {
                stringResource(R.string.empty_tasks_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (hasAnyTasks) {
            TextButton(onClick = onResetFilters) {
                Text("Mostrar todas las tareas")
            }
        } else {
            Button(
                onClick = onCreateTaskClick,
                modifier = Modifier.testTag("empty_create_task_button")
            ) {
                Text(stringResource(R.string.action_new_task))
            }
        }
    }
}
