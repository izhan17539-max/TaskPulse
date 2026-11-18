package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.CloudSyncStatus
import com.example.data.ListIconKey
import com.example.data.SyncedCollectionResult
import com.example.data.TaskItem
import com.example.data.TaskListItem
import com.example.data.TaskPriority
import com.example.data.TaskRepository
import com.example.data.UiState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TaskFilter(val labelEs: String) {
    ALL("Todas"),
    PENDING("Pendientes"),
    HIGH_PRIORITY("Alta prioridad"),
    TODAY("Hoy"),
    COMPLETED("Completadas")
}

enum class TaskSortOption(val labelEs: String) {
    PRIORITY("Prioridad"),
    RECENT("Recientes"),
    DUE_DATE("Fecha límite")
}

data class TasksFilterState(
    val filter: TaskFilter = TaskFilter.ALL,
    val selectedListId: String? = null,
    val searchQuery: String = "",
    val sortOption: TaskSortOption = TaskSortOption.PRIORITY
)

data class TasksDashboardData(
    val allTasks: List<TaskItem>,
    val filteredTasks: List<TaskItem>,
    val lists: List<TaskListItem>,
    val syncStatus: CloudSyncStatus,
    val pendingCount: Int,
    val completedCount: Int,
    val highPriorityPendingCount: Int
)

class TasksViewModel(
    private val repository: TaskRepository,
    val currentUserId: String
) : ViewModel() {

    private val _filterState = MutableStateFlow(TasksFilterState())
    val filterState: StateFlow<TasksFilterState> = _filterState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val currentDeviceName: String = repository.currentDeviceLabel()

    private val tasksStream: StateFlow<UiState<SyncedCollectionResult<TaskItem>>> =
        repository.observeTasks(currentUserId)
            .map<SyncedCollectionResult<TaskItem>, UiState<SyncedCollectionResult<TaskItem>>> {
                UiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing tasks", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "No se pudieron sincronizar tus tareas."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val listsStream: StateFlow<UiState<List<TaskListItem>>> =
        repository.observeLists(currentUserId)
            .map<List<TaskListItem>, UiState<List<TaskListItem>>> { lists ->
                UiState.Success(lists.sortedBy { it.name.lowercase() })
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing lists", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "No se pudieron cargar tus listas."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val dashboardState: StateFlow<UiState<TasksDashboardData>> =
        combine(tasksStream, listsStream, _filterState) { tasksState, listsState, filters ->
            when {
                tasksState is UiState.Error -> UiState.Error(tasksState.message)
                listsState is UiState.Error -> UiState.Error(listsState.message)
                tasksState is UiState.Loading -> UiState.Loading
                tasksState is UiState.Success -> {
                    val lists = (listsState as? UiState.Success)?.data ?: emptyList()
                    val allTasks = tasksState.data.items
                    val filtered = applyFiltersAndSort(allTasks, filters)
                    val pendingCount = allTasks.count { !it.completed }
                    val completedCount = allTasks.count { it.completed }
                    val highPriorityPendingCount = allTasks.count {
                        !it.completed && it.priorityEnum == TaskPriority.HIGH
                    }
                    UiState.Success(
                        TasksDashboardData(
                            allTasks = allTasks,
                            filteredTasks = filtered,
                            lists = lists,
                            syncStatus = tasksState.data.syncStatus,
                            pendingCount = pendingCount,
                            completedCount = completedCount,
                            highPriorityPendingCount = highPriorityPendingCount
                        )
                    )
                }
                else -> UiState.Loading
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun setFilter(filter: TaskFilter) {
        _filterState.value = _filterState.value.copy(filter = filter)
    }

    fun selectList(listId: String?) {
        _filterState.value = _filterState.value.copy(selectedListId = listId)
    }

    fun setSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun setSortOption(option: TaskSortOption) {
        _filterState.value = _filterState.value.copy(sortOption = option)
    }

    fun createTask(
        title: String,
        notes: String?,
        priority: TaskPriority,
        listId: String?,
        categoryLabel: String,
        dueDateMillis: Long?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.createTask(
                userId = currentUserId,
                title = title,
                notes = notes,
                priority = priority,
                listId = listId,
                categoryLabel = categoryLabel,
                dueDateMillis = dueDateMillis
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Tarea guardada en la nube")
                    onDone()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Error al crear la tarea")
                }
            )
        }
    }

    fun updateTask(
        taskId: String,
        title: String,
        notes: String?,
        completed: Boolean,
        priority: TaskPriority,
        listId: String?,
        categoryLabel: String,
        dueDateMillis: Long?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateTask(
                userId = currentUserId,
                taskId = taskId,
                title = title,
                notes = notes,
                completed = completed,
                priority = priority,
                listId = listId,
                categoryLabel = categoryLabel,
                dueDateMillis = dueDateMillis
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Cambios sincronizados")
                    onDone()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Error al actualizar la tarea")
                }
            )
        }
    }

    fun toggleTaskCompleted(task: TaskItem) {
        viewModelScope.launch {
            val result = repository.toggleTaskCompleted(
                userId = currentUserId,
                taskId = task.id,
                completed = !task.completed
            )
            result.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "No se pudo actualizar el estado")
            }
        }
    }

    fun deleteTask(taskId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteTask(currentUserId, taskId)
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Tarea eliminada")
                    onDone()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Error al eliminar la tarea")
                }
            )
        }
    }

    fun clearCompletedTasks(allTasks: List<TaskItem>) {
        val completedIds = allTasks.filter { it.completed }.map { it.id }
        if (completedIds.isEmpty()) return
        viewModelScope.launch {
            val result = repository.clearCompletedTasks(currentUserId, completedIds)
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Tareas completadas eliminadas")
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Error al limpiar completadas")
                }
            )
        }
    }

    fun createList(
        name: String,
        colorHex: String,
        iconKey: ListIconKey,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.createList(currentUserId, name, colorHex, iconKey)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Lista creada en la nube")
                    onDone()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Error al crear la lista")
                }
            )
        }
    }

    fun updateList(
        listId: String,
        name: String,
        colorHex: String,
        iconKey: ListIconKey,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateList(currentUserId, listId, name, colorHex, iconKey)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Lista actualizada")
                    onDone()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Error al actualizar la lista")
                }
            )
        }
    }

    fun deleteList(listId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteList(currentUserId, listId)
            result.fold(
                onSuccess = {
                    if (_filterState.value.selectedListId == listId) {
                        _filterState.value = _filterState.value.copy(selectedListId = null)
                    }
                    _snackbarEvents.tryEmit("Lista eliminada")
                    onDone()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Error al eliminar la lista")
                }
            )
        }
    }

    private fun applyFiltersAndSort(
        tasks: List<TaskItem>,
        state: TasksFilterState
    ): List<TaskItem> {
        val query = state.searchQuery.trim().lowercase()
        val (todayStart, todayEnd) = todayMillisRange()

        val filtered = tasks.filter { task ->
            val matchesList = state.selectedListId == null || task.listId == state.selectedListId
            val matchesQuery = query.isEmpty() ||
                task.title.lowercase().contains(query) ||
                (task.notes?.lowercase()?.contains(query) == true) ||
                task.categoryLabel.lowercase().contains(query)
            val matchesFilter = when (state.filter) {
                TaskFilter.ALL -> true
                TaskFilter.PENDING -> !task.completed
                TaskFilter.HIGH_PRIORITY -> !task.completed && task.priorityEnum == TaskPriority.HIGH
                TaskFilter.TODAY -> {
                    val due = task.dueDateMillis
                    due != null && due in todayStart..todayEnd
                }
                TaskFilter.COMPLETED -> task.completed
            }
            matchesList && matchesQuery && matchesFilter
        }

        return when (state.sortOption) {
            TaskSortOption.PRIORITY -> filtered.sortedWith(
                compareBy<TaskItem> { it.completed }
                    .thenBy { it.priorityEnum.sortRank }
                    .thenBy { it.dueDateMillis ?: Long.MAX_VALUE }
                    .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            )
            TaskSortOption.RECENT -> filtered.sortedWith(
                compareBy<TaskItem> { it.completed }
                    .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            )
            TaskSortOption.DUE_DATE -> filtered.sortedWith(
                compareBy<TaskItem> { it.completed }
                    .thenBy { it.dueDateMillis ?: Long.MAX_VALUE }
                    .thenBy { it.priorityEnum.sortRank }
            )
        }
    }

    private fun todayMillisRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    companion object {
        private const val TAG = "TasksViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun factory(userId: String) = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TasksViewModel(
                    repository = TaskRepository(db),
                    currentUserId = userId
                )
            }
        }
    }
}
