package com.deafregistry.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.remote.dto.ChatRecurringScheduleDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val RETENTION_OPTIONS = listOf("immediate" to "Immediate", "24h" to "24 hours", "7d" to "7 days")

// Sunday-first (index 0), matching both Postgres's EXTRACT(DOW) and java.util.Calendar - the same
// convention the backend's chat_recurring_schedules.days_of_week column uses.
private val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/**
 * Lets an admin/super_admin define a recurring chat-session template ("every Mon/Wed/Fri,
 * 8:00-9:00 AM") instead of creating the same one-off session every time - the backend's
 * chatScheduler.js turns each active schedule into a real chat_sessions row on the days it's due.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRecurringSchedulesScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.chatRepository
    val scope = rememberCoroutineScope()
    var schedules by remember { mutableStateOf(listOf<ChatRecurringScheduleDto>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ChatRecurringScheduleDto?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatRecurringScheduleDto?>(null) }

    suspend fun reload() {
        runCatching { schedules = repo.listRecurringSchedules() }.onFailure { error = friendlyMessage(it) }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Recurring Schedules", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingSchedule = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Recurring Schedule")
            }
        }
    ) { padding: PaddingValues ->
        if (schedules.isEmpty() && error == null) {
            EmptyState("No recurring schedules yet. Add one so you don't have to create the same chat session every time.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(schedules, key = { it.id }) { schedule ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(schedule.sessionName, style = MaterialTheme.typography.titleMedium)
                                    schedule.description?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = schedule.isActive,
                                    onCheckedChange = { active ->
                                        scope.launch {
                                            runCatching {
                                                repo.updateRecurringSchedule(
                                                    schedule.id, schedule.sessionName, schedule.description, schedule.daysOfWeek,
                                                    schedule.startTime, schedule.endTime, schedule.retentionPolicy, active
                                                )
                                            }.onFailure { error = friendlyMessage(it) }
                                            reload()
                                        }
                                    }
                                )
                            }
                            Text(
                                schedule.daysOfWeek.sorted().joinToString(", ") { DAY_LABELS[it] },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "${formatTime(schedule.startTime)} → ${formatTime(schedule.endTime)} • Retention: ${schedule.retentionPolicy}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                TextButton(onClick = { editingSchedule = schedule; showEditor = true }) { Text("Edit") }
                                TextButton(onClick = { deleteTarget = schedule }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        RecurringScheduleEditorDialog(
            existing = editingSchedule,
            onDismiss = { showEditor = false },
            onSave = { name, description, days, start, end, retention ->
                showEditor = false
                scope.launch {
                    val target = editingSchedule
                    runCatching {
                        if (target == null) repo.createRecurringSchedule(name, description, days, start, end, retention)
                        else repo.updateRecurringSchedule(target.id, name, description, days, start, end, retention, target.isActive)
                    }.onFailure { error = friendlyMessage(it) }
                    reload()
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this recurring schedule?") },
            text = { Text("This stops \"${target.sessionName}\" from being auto-created going forward. Chat sessions it already created are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        runCatching { repo.deleteRecurringSchedule(target.id) }.onFailure { error = friendlyMessage(it) }
                        reload()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

private fun formatTime(raw: String): String = runCatching {
    LocalTime.parse(raw.take(5)).format(DateTimeFormatter.ofPattern("hh:mm a"))
}.getOrDefault(raw)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringScheduleEditorDialog(
    existing: ChatRecurringScheduleDto?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, days: List<Int>, start: String, end: String, retention: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.sessionName ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var selectedDays by remember { mutableStateOf(existing?.daysOfWeek?.toSet() ?: emptySet()) }
    var startTime by remember { mutableStateOf(existing?.startTime?.let { runCatching { LocalTime.parse(it.take(5)) }.getOrNull() } ?: LocalTime.of(8, 0)) }
    var endTime by remember { mutableStateOf(existing?.endTime?.let { runCatching { LocalTime.parse(it.take(5)) }.getOrNull() } ?: LocalTime.of(9, 0)) }
    var retention by remember { mutableStateOf(existing?.retentionPolicy ?: "immediate") }
    var retentionMenuExpanded by remember { mutableStateOf(false) }
    var attemptedSave by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Recurring Schedule" else "Edit Recurring Schedule") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Chat Room Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(12.dp))
                Text("Repeats on", style = MaterialTheme.typography.labelLarge)
                if (attemptedSave && selectedDays.isEmpty()) {
                    Text("Select at least one day", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LABELS.forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedDays.contains(index),
                            onClick = { selectedDays = if (selectedDays.contains(index)) selectedDays - index else selectedDays + index },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime.format(DateTimeFormatter.ofPattern("hh:mm a")),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Start Time") },
                        trailingIcon = {
                            IconButton(onClick = {
                                android.app.TimePickerDialog(context, { _, h, min -> startTime = LocalTime.of(h, min) }, startTime.hour, startTime.minute, false).show()
                            }) { Icon(Icons.Default.Schedule, contentDescription = "Pick start time") }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.height(0.dp))
                    OutlinedTextField(
                        value = endTime.format(DateTimeFormatter.ofPattern("hh:mm a")),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("End Time") },
                        trailingIcon = {
                            IconButton(onClick = {
                                android.app.TimePickerDialog(context, { _, h, min -> endTime = LocalTime.of(h, min) }, endTime.hour, endTime.minute, false).show()
                            }) { Icon(Icons.Default.Schedule, contentDescription = "Pick end time") }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (attemptedSave && !endTime.isAfter(startTime)) {
                    Text("End time must be after start time", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(expanded = retentionMenuExpanded, onExpandedChange = { retentionMenuExpanded = it }) {
                    OutlinedTextField(
                        value = RETENTION_OPTIONS.firstOrNull { it.first == retention }?.second ?: retention,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Message Retention") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = retentionMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = retentionMenuExpanded, onDismissRequest = { retentionMenuExpanded = false }) {
                        RETENTION_OPTIONS.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { retention = key; retentionMenuExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attemptedSave = true
                if (name.isNotBlank() && selectedDays.isNotEmpty() && endTime.isAfter(startTime)) {
                    val startStr = startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    val endStr = endTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    onSave(name.trim(), description.trim().ifBlank { null }, selectedDays.sorted(), startStr, endStr, retention)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
