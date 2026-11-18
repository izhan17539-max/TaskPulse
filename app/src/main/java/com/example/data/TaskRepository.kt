package com.example.data

import android.content.Context
import android.os.Build
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TaskRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthorizedUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para acceder a la nube.")
        check(uid == expectedUserId) { "La sesión cambió; operación cancelada." }
        return uid
    }

    fun currentDeviceLabel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val combined = when {
            model.isBlank() -> "Android"
            manufacturer.isNotBlank() && !model.startsWith(manufacturer, ignoreCase = true) ->
                "$manufacturer $model"
            else -> model
        }
        return combined.take(80).ifBlank { "Android" }
    }

    fun observeTasks(userId: String): Flow<SyncedCollectionResult<TaskItem>> = flow {
        val uid = requireAuthorizedUid(userId)
        val path = "users/$uid/tasks"
        emitAll(
            db.collection("users").document(uid).collection("tasks")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val tasks = snapshot.toObjects(
                        TaskItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    val status = when {
                        snapshot.metadata.hasPendingWrites() -> CloudSyncStatus.SYNCING
                        snapshot.metadata.isFromCache -> CloudSyncStatus.OFFLINE
                        else -> CloudSyncStatus.SYNCED
                    }
                    SyncedCollectionResult(items = tasks, syncStatus = status)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeLists(userId: String): Flow<List<TaskListItem>> = flow {
        val uid = requireAuthorizedUid(userId)
        val path = "users/$uid/lists"
        emitAll(
            db.collection("users").document(uid).collection("lists")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TaskListItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createTask(
        userId: String,
        title: String,
        notes: String?,
        priority: TaskPriority,
        listId: String?,
        categoryLabel: String,
        dueDateMillis: Long?
    ): Result<Unit> {
        var docPath = "users/$userId/tasks"
        return try {
            val uid = requireAuthorizedUid(userId)
            val cleanTitle = title.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 120) {
                "El título debe tener entre 1 y 120 caracteres."
            }
            val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
            require(cleanNotes == null || cleanNotes.length <= 1000) {
                "Las notas no pueden superar los 1000 caracteres."
            }
            val cleanCategory = categoryLabel.trim().ifEmpty { "General" }.take(50)
            val cleanListId = listId?.trim()?.takeIf { it.isNotEmpty() }

            val docRef = db.collection("users").document(uid).collection("tasks").document()
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "title" to cleanTitle,
                "notes" to cleanNotes,
                "completed" to false,
                "priority" to priority.name,
                "listId" to cleanListId,
                "categoryLabel" to cleanCategory,
                "dueDateMillis" to dueDateMillis,
                "deviceName" to currentDeviceLabel(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(
        userId: String,
        taskId: String,
        completed: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(taskId.isNotBlank()) { "El ID de tarea es obligatorio." }
            val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "completed" to completed,
                    "deviceName" to currentDeviceLabel(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        userId: String,
        taskId: String,
        title: String,
        notes: String?,
        completed: Boolean,
        priority: TaskPriority,
        listId: String?,
        categoryLabel: String,
        dueDateMillis: Long?
    ): Result<Unit> {
        var docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(taskId.isNotBlank()) { "El ID de tarea es obligatorio." }
            val cleanTitle = title.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 120) {
                "El título debe tener entre 1 y 120 caracteres."
            }
            val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
            require(cleanNotes == null || cleanNotes.length <= 1000) {
                "Las notas no pueden superar los 1000 caracteres."
            }
            val cleanCategory = categoryLabel.trim().ifEmpty { "General" }.take(50)
            val cleanListId = listId?.trim()?.takeIf { it.isNotEmpty() }

            val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
            docPath = docRef.path

            val updates = mapOf<String, Any>(
                "title" to cleanTitle,
                "notes" to (cleanNotes ?: FieldValue.delete()),
                "completed" to completed,
                "priority" to priority.name,
                "listId" to (cleanListId ?: FieldValue.delete()),
                "categoryLabel" to cleanCategory,
                "dueDateMillis" to (dueDateMillis ?: FieldValue.delete()),
                "deviceName" to currentDeviceLabel(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        var docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(taskId.isNotBlank()) { "El ID de tarea es obligatorio." }
            val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearCompletedTasks(userId: String, taskIds: List<String>): Result<Unit> {
        val path = "users/$userId/tasks"
        return try {
            val uid = requireAuthorizedUid(userId)
            if (taskIds.isEmpty()) return Result.success(Unit)
            val tasksRef = db.collection("users").document(uid).collection("tasks")
            taskIds.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { taskId ->
                    if (taskId.isNotBlank()) {
                        batch.delete(tasksRef.document(taskId))
                    }
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createList(
        userId: String,
        name: String,
        colorHex: String,
        iconKey: ListIconKey
    ): Result<Unit> {
        var docPath = "users/$userId/lists"
        return try {
            val uid = requireAuthorizedUid(userId)
            val cleanName = name.trim()
            require(cleanName.isNotEmpty() && cleanName.length <= 50) {
                "El nombre de la lista debe tener entre 1 y 50 caracteres."
            }
            val cleanColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(colorHex)) colorHex else "#2563EB"
            val docRef = db.collection("users").document(uid).collection("lists").document()
            docPath = docRef.path
            val payload = mapOf<String, Any>(
                "userId" to uid,
                "name" to cleanName,
                "colorHex" to cleanColor,
                "iconKey" to iconKey.key,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateList(
        userId: String,
        listId: String,
        name: String,
        colorHex: String,
        iconKey: ListIconKey
    ): Result<Unit> {
        var docPath = "users/$userId/lists/$listId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(listId.isNotBlank()) { "El ID de lista es obligatorio." }
            val cleanName = name.trim()
            require(cleanName.isNotEmpty() && cleanName.length <= 50) {
                "El nombre de la lista debe tener entre 1 y 50 caracteres."
            }
            val cleanColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(colorHex)) colorHex else "#2563EB"
            val docRef = db.collection("users").document(uid).collection("lists").document(listId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "colorHex" to cleanColor,
                    "iconKey" to iconKey.key,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteList(userId: String, listId: String): Result<Unit> {
        var docPath = "users/$userId/lists/$listId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(listId.isNotBlank()) { "El ID de lista es obligatorio." }
            val userRef = db.collection("users").document(uid)
            val listRef = userRef.collection("lists").document(listId)
            docPath = listRef.path

            val linkedTasks = userRef.collection("tasks")
                .whereEqualTo("listId", listId)
                .get()
                .await()

            val batch = db.batch()
            batch.delete(listRef)
            linkedTasks.documents.forEach { taskDoc ->
                batch.update(
                    taskDoc.reference,
                    mapOf(
                        "listId" to FieldValue.delete(),
                        "categoryLabel" to "General",
                        "deviceName" to currentDeviceLabel(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
