package com.deafregistry.app.ui.chat

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.remote.dto.ChatRecurringScheduleDto
import com.deafregistry.app.data.remote.dto.ChatSingleScheduleDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val RETENTION_OPTIONS = listOf("immediate" to "Immediate", "24h" to "24 hours", "7d" to "7 days")

/**
 * A one-off exception for a single calendar date - at most one per date, and when enabled it
 * outranks whatever a Recurring Schedule would otherwise generate that day (see
 * chatScheduler.js::generateSingleTimeSessions/generateRecurringSessions - Single-Time > Recurring
 * > Chat Closed). Conflict detection against active recurring schedules happens client-side here
 * before saving, since the recurring list is already loaded for this exact check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSingleSchedulesScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.chatRepository
    val scope = rememberCoroutineScope()
    var schedules by remember { mutableStateOf(listOf<ChatSingleScheduleDto>()) }
    var recurringSchedules by remember { mutableStateOf(listOf<ChatRecurringScheduleDto>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ChatSingleScheduleDto?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatSingleScheduleDto?>(null) }

    suspend fun reload() {
        runCatching { schedules = repo.listSingleSchedules() }.onFailure { error = friendlyMessage(it) }
        runCatching { recurringSchedules = repo.listRecurringSchedules() }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Single-Time Schedules", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingSchedule = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Single-Time Schedule")
            }
        }
    ) { padding: PaddingValues ->
        if (schedules.isEmpty() && error == null) {
            EmptyState("No single-time schedules yet. Add one for a one-off exception on a specific date.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(schedules, key = { it.id }) { schedule ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(schedule.sessionName, style = MaterialTheme.typography.titleMedium)
                                    schedule.remarks?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (schedule.status == "scheduled") {
                                    Switch(
                                        checked = schedule.isActive,
                                        onCheckedChange = { active ->
                                            scope.launch {
                                                runCatching {
                                                    repo.updateSingleSchedule(
                                                        schedule.id, schedule.sessionName, schedule.scheduleDate, schedule.startTime, schedule.endTime,
                                                        schedule.remarks, schedule.retentionPolicy, active, null
                                                    )
                                                }.onFailure { error = friendlyMessage(it) }
                                                reload()
                                            }
                                        }
                                    )
                                }
                            }
                            Text(schedule.scheduleDate, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${formatTime(schedule.startTime)} → ${formatTime(schedule.endTime)}" +
                                    (if (schedule.endTime <= schedule.startTime) " (next day)" else "") +
                                    " • ${schedule.status}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (schedule.status == "scheduled") {
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
    }

    if (showEditor) {
        SingleScheduleEditorDialog(
            existing = editingSchedule,
            recurringSchedules = recurringSchedules,
            onDismiss = { showEditor = false },
            onSave = { name, date, start, end, remarks, retention, isActive, conflictedRecurringId ->
                showEditor = false
                scope.launch {
                    val target = editingSchedule
                    runCatching {
                        if (target == null) repo.createSingleSchedule(name, date, start, end, remarks, retention, isActive, conflictedRecurringId)
                        else repo.updateSingleSchedule(target.id, name, date, start, end, remarks, retention, isActive, conflictedRecurringId)
                    }.onFailure { error = friendlyMessage(it) }
                    reload()
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this single-time schedule?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        runCatching { repo.deleteSingleSchedule(target.id) }.onFailure { error = friendlyMessage(it) }
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

/** Postgres's EXTRACT(DOW) convention (0=Sunday..6=Saturday), matching chat_recurring_schedules -
 * java.time's DayOfWeek is ISO (1=Monday..7=Sunday), so Sunday needs mapping to 0 via `% 7`. */
private fun postgresDayOfWeek(date: LocalDate): Int = date.dayOfWeek.value % 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleScheduleEditorDialog(
    existing: ChatSingleScheduleDto?,
    recurringSchedules: List<ChatRecurringScheduleDto>,
    onDismiss: () -> Unit,
    onSave: (name: String, date: String, start: String, end: String, remarks: String?, retention: String, isActive: Boolean, conflictedRecurringId: Int?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.sessionName ?: "") }
    var remarks by remember { mutableStateOf(existing?.remarks ?: "") }
    var selectedDate by remember {
        mutableStateOf(existing?.scheduleDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now())
    }
    var startTime by remember { mutableStateOf(existing?.startTime?.let { runCatching { LocalTime.parse(it.take(5)) }.getOrNull() } ?: LocalTime.of(8, 0)) }
    var endTime by remember { mutableStateOf(existing?.endTime?.let { runCatching { LocalTime.parse(it.take(5)) }.getOrNull() } ?: LocalTime.of(9, 0)) }
    var retention by remember { mutableStateOf(existing?.retentionPolicy ?: "immediate") }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }
    var retentionMenuExpanded by remember { mutableStateOf(false) }
    var attemptedSave by remember { mutableStateOf(false) }
    // Set only while the 3-button conflict dialog is showing, so its buttons know which recurring
    // schedule the conflict was against (needed for the audit-log details on save).
    var conflictingSchedule by remember { mutableStateOf<ChatRecurringScheduleDto?>(null) }

    fun trySave() {
        attemptedSave = true
        if (name.isBlank() || endTime == startTime) return
        val dow = postgresDayOfWeek(selectedDate)
        val conflict = recurringSchedules.firstOrNull { it.isActive && dow in it.daysOfWeek }
        if (conflict != null) {
            conflictingSchedule = conflict
        } else {
            onSave(name.trim(), selectedDate.toString(), startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                endTime.format(DateTimeFormatter.ofPattern("HH:mm")), remarks.trim().ifBlank { null }, retention, isActive, null)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Single-Time Schedule" else "Edit Single-Time Schedule") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Chat Room Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = selectedDate.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Schedule Date") },
                    trailingIcon = {
                        IconButton(onClick = {
                            android.app.DatePickerDialog(
                                context,
                                { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) },
                                selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth
                            ).show()
                        }) { Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

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
                if (endTime == startTime) {
                    if (attemptedSave) {
                        Text("Start and end time cannot be the same", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (!endTime.isAfter(startTime)) {
                    Text("Ends the next day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") }, minLines = 2, modifier = Modifier.fillMaxWidth())

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

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Status: ${if (isActive) "Enabled" else "Disabled"}", modifier = Modifier.weight(1f))
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { trySave() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    conflictingSchedule?.let { conflict ->
        AlertDialog(
            onDismissRequest = { conflictingSchedule = null },
            title = { Text("Recurring Schedule Detected") },
            text = {
                Text(
                    "A recurring chat schedule (\"${conflict.sessionName}\", ${formatTime(conflict.startTime)}–${formatTime(conflict.endTime)}) " +
                        "already exists on this day. Would you like the single-time schedule to temporarily replace it for this date only?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    conflictingSchedule = null
                    onSave(name.trim(), selectedDate.toString(), startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        endTime.format(DateTimeFormatter.ofPattern("HH:mm")), remarks.trim().ifBlank { null }, retention, true, conflict.id)
                }) { Text("Override Schedule") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        conflictingSchedule = null
                        onSave(name.trim(), selectedDate.toString(), startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            endTime.format(DateTimeFormatter.ofPattern("HH:mm")), remarks.trim().ifBlank { null }, retention, false, conflict.id)
                    }) { Text("Keep Recurring") }
                    TextButton(onClick = { conflictingSchedule = null }) { Text("Cancel") }
                }
            }
        )
    }
}
