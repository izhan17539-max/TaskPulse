package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskPriority(val labelEs: String, val sortRank: Int) {
    HIGH("Alta", 0),
    MEDIUM("Media", 1),
    LOW("Baja", 2);

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class ListIconKey(val key: String, val labelEs: String) {
    WORK("work", "Trabajo"),
    HOME("home", "Hogar"),
    SHOPPING("shopping", "Compras"),
    STAR("star", "Destacado"),
    BOLT("bolt", "Rápido"),
    FOLDER("folder", "Proyecto");

    companion object {
        fun fromRaw(raw: String?): ListIconKey =
            entries.firstOrNull { it.key == raw } ?: FOLDER
    }
}

enum class CloudSyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE
}

@Keep
@IgnoreExtraProperties
data class TaskListItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorHex: String = "#2563EB",
    val iconKey: String = ListIconKey.FOLDER.key,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val iconEnum: ListIconKey
        get() = ListIconKey.fromRaw(iconKey)
}

@Keep
@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String? = null,
    val completed: Boolean = false,
    val priority: String = TaskPriority.MEDIUM.name,
    val listId: String? = null,
    val categoryLabel: String = "General",
    val dueDateMillis: Long? = null,
    val deviceName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val priorityEnum: TaskPriority
        get() = TaskPriority.fromRaw(priority)
}

data class SyncedCollectionResult<T>(
    val items: List<T>,
    val syncStatus: CloudSyncStatus
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
