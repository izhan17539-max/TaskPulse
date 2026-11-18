package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ListIconKey
import com.example.data.TaskItem
import com.example.data.TaskListItem
import com.example.data.TaskPriority
import java.util.Calendar

private val defaultCategories = listOf("General", "Trabajo", "Personal", "Compras", "Salud")

private val listColorPalette = listOf(
    "#2563EB",
    "#059669",
    "#D97706",
    "#E11D48",
    "#7C3AED",
    "#0891B2"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    existingTask: TaskItem?,
    availableLists: List<TaskListItem>,
    initialListId: String?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        notes: String?,
        priority: TaskPriority,
        listId: String?,
        categoryLabel: String,
        dueDateMillis: Long?
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(existingTask) { mutableStateOf(existingTask?.title ?: "") }
    var notes by remember(existingTask) { mutableStateOf(existingTask?.notes ?: "") }
    var priority by remember(existingTask) {
        mutableStateOf(existingTask?.priorityEnum ?: TaskPriority.MEDIUM)
    }
    var selectedListId by remember(existingTask, initialListId) {
        mutableStateOf(existingTask?.listId ?: initialListId)
    }
    var categoryLabel by remember(existingTask, initialListId, availableLists) {
        val initialList = availableLists.firstOrNull { it.id == (existingTask?.listId ?: initialListId) }
        mutableStateOf(existingTask?.categoryLabel ?: initialList?.name ?: "General")
    }
    var dueDateMillis by remember(existingTask) { mutableStateOf(existingTask?.dueDateMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    val trimmedTitle = title.trim()
    val isTitleValid = trimmedTitle.isNotEmpty() && trimmedTitle.length <= 120
    val isNotesValid = notes.trim().length <= 1000

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (existingTask == null) "Nueva tarea" else "Editar tarea",
                    style = MaterialTheme.typography.headlineSmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            enabled = !isSaving,
                            modifier = Modifier.testTag("editor_delete_task_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 120) title = it },
                label = { Text("Título de la tarea") },
                placeholder = { Text("Ej. Revisar presupuesto semanal") },
                singleLine = true,
                isError = title.isNotEmpty() && !isTitleValid,
                supportingText = { Text("${title.length}/120") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 1000) notes = it },
                label = { Text("Notas (opcional)") },
                placeholder = { Text("Detalles, enlaces o subtareas rápidas...") },
                minLines = 2,
                maxLines = 4,
                isError = !isNotesValid,
                supportingText = { Text("${notes.length}/1000") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_notes_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Prioridad",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TaskPriority.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = priority == option,
                            onClick = { priority = option },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TaskPriority.entries.size
                            ),
                            modifier = Modifier.testTag("priority_${option.name}")
                        ) {
                            Text(option.labelEs)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (availableLists.isNotEmpty()) "Lista o categoría" else "Categoría",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableLists.forEach { list ->
                        val isSelected = selectedListId == list.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedListId = null
                                    categoryLabel = "General"
                                } else {
                                    selectedListId = list.id
                                    categoryLabel = list.name
                                }
                            },
                            label = { Text(list.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            parseColorHexSafe(
                                                list.colorHex,
                                                MaterialTheme.colorScheme.primary
                                            )
                                        )
                                )
                            }
                        )
                    }
                    defaultCategories.forEach { cat ->
                        val isSelected = selectedListId == null && categoryLabel == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedListId = null
                                categoryLabel = cat
                            },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Fecha límite",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = dueDateMillis == null,
                        onClick = { dueDateMillis = null },
                        label = { Text("Sin fecha") }
                    )
                    FilterChip(
                        selected = dueDateMillis != null && formatDueDateLabel(dueDateMillis!!).first == "Hoy",
                        onClick = { dueDateMillis = endOfDayOffsetMillis(0) },
                        label = { Text("Hoy") }
                    )
                    FilterChip(
                        selected = dueDateMillis != null && formatDueDateLabel(dueDateMillis!!).first == "Mañana",
                        onClick = { dueDateMillis = endOfDayOffsetMillis(1) },
                        label = { Text("Mañana") }
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = dueDateMillis?.let { formatDueDateLabel(it).first } ?: "Elegir fecha",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        onSave(
                            trimmedTitle,
                            notes.trim().takeIf { it.isNotEmpty() },
                            priority,
                            selectedListId,
                            categoryLabel,
                            dueDateMillis
                        )
                    },
                    enabled = isTitleValid && isNotesValid && !isSaving,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("save_task_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dueDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListEditorSheet(
    existingList: TaskListItem?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String, iconKey: ListIconKey) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(existingList) { mutableStateOf(existingList?.name ?: "") }
    var colorHex by remember(existingList) { mutableStateOf(existingList?.colorHex ?: listColorPalette.first()) }
    var iconKey by remember(existingList) { mutableStateOf(existingList?.iconEnum ?: ListIconKey.FOLDER) }

    val trimmedName = name.trim()
    val isNameValid = trimmedName.isNotEmpty() && trimmedName.length <= 50

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (existingList == null) "Nueva lista" else "Editar lista",
                    style = MaterialTheme.typography.headlineSmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            enabled = !isSaving,
                            modifier = Modifier.testTag("editor_delete_list_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 50) name = it },
                label = { Text("Nombre de la lista") },
                placeholder = { Text("Ej. Lanzamiento móvil") },
                singleLine = true,
                supportingText = { Text("${name.length}/50") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("list_name_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Color identificador",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listColorPalette.forEach { hex ->
                        val parsed = parseColorHexSafe(hex, MaterialTheme.colorScheme.primary)
                        val selected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(parsed)
                                .then(
                                    if (selected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Icono",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ListIconKey.entries.forEach { key ->
                        FilterChip(
                            selected = iconKey == key,
                            onClick = { iconKey = key },
                            label = { Text(key.labelEs) },
                            leadingIcon = {
                                Icon(
                                    imageVector = listIconForKey(key),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { onSave(trimmedName, colorHex, iconKey) },
                    enabled = isNameValid && !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = parseColorHexSafe(colorHex, MaterialTheme.colorScheme.primary)
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("save_list_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

private fun endOfDayOffsetMillis(daysOffset: Int): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, daysOffset)
    cal.set(Calendar.HOUR_OF_DAY, 18)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
