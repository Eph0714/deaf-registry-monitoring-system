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
import com.deafregistry.app.data.remote.dto.ChatSessionDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val RETENTION_OPTIONS = listOf("immediate" to "Immediate", "24h" to "24 hours", "7d" to "7 days")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSessionManageScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.chatRepository
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf(listOf<ChatSessionDto>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<ChatSessionDto?>(null) }
    var clearTarget by remember { mutableStateOf<ChatSessionDto?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatSessionDto?>(null) }

    suspend fun reload() {
        runCatching { sessions = repo.listSessions() }.onFailure { error = it.message }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Manage Chat Sessions", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingSession = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Chat Session")
            }
        }
    ) { padding: PaddingValues ->
        if (sessions.isEmpty() && error == null) {
            EmptyState("No chat sessions have been created yet.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(sessions, key = { it.id }) { session ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(session.sessionName, style = MaterialTheme.typography.titleMedium)
                            session.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "${formatServerDateTime(session.startDatetime)} → ${formatServerDateTime(session.endDatetime)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Status: ${session.status} • Retention: ${session.retentionPolicy} • ${session.participantCount ?: 0} participant(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                if (session.status == "scheduled" || session.status == "closed") {
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { repo.openSession(session.id) }.onFailure { error = it.message }
                                            reload()
                                        }
                                    }) { Text("Open") }
                                }
                                if (session.status == "open") {
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { repo.closeSession(session.id) }.onFailure { error = it.message }
                                            reload()
                                        }
                                    }) { Text("Close") }
                                }
                                if (session.status != "expired") {
                                    TextButton(onClick = { editingSession = session; showEditor = true }) { Text("Edit") }
                                }
                                TextButton(onClick = { clearTarget = session }) { Text("Clear") }
                                TextButton(onClick = { deleteTarget = session }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        SessionEditorDialog(
            existing = editingSession,
            onDismiss = { showEditor = false },
            onSave = { name, description, start, end, retention ->
                showEditor = false
                scope.launch {
                    val target = editingSession
                    runCatching {
                        if (target == null) repo.createSession(name, description, start, end, retention)
                        else repo.updateSession(target.id, name, description, start, end, retention)
                    }.onFailure { error = it.message }
                    reload()
                }
            }
        )
    }

    clearTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { clearTarget = null },
            title = { Text("Clear all messages?") },
            text = { Text("This immediately deletes every message in \"${target.sessionName}\". This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    clearTarget = null
                    scope.launch {
                        runCatching { repo.clearMessages(target.id) }.onFailure { error = it.message }
                    }
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { clearTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this chat session?") },
            text = { Text("This permanently removes \"${target.sessionName}\", all of its messages, and its participant list. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        runCatching { repo.deleteSession(target.id) }.onFailure { error = it.message }
                        reload()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionEditorDialog(
    existing: ChatSessionDto?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, start: String, end: String, retention: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.sessionName ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    val initialStart = existing?.let { parseServerDateTime(it.startDatetime) } ?: LocalDateTime.now()
    val initialEnd = existing?.let { parseServerDateTime(it.endDatetime) } ?: LocalDateTime.now().plusHours(1)
    var startDate by remember { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember { mutableStateOf(initialStart.toLocalTime()) }
    var endDate by remember { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember { mutableStateOf(initialEnd.toLocalTime()) }
    var retention by remember { mutableStateOf(existing?.retentionPolicy ?: "immediate") }
    var retentionMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Chat Session" else "Edit Chat Session") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Chat Room Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(12.dp))
                Text("Start", style = MaterialTheme.typography.labelLarge)
                DateTimeRow(
                    date = startDate, time = startTime,
                    onPickDate = { onPicked ->
                        android.app.DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) }, startDate.year, startDate.monthValue - 1, startDate.dayOfMonth).show()
                    },
                    onDateChange = { startDate = it },
                    onPickTime = { onPicked ->
                        android.app.TimePickerDialog(context, { _, h, min -> onPicked(LocalTime.of(h, min)) }, startTime.hour, startTime.minute, false).show()
                    },
                    onTimeChange = { startTime = it }
                )

                Spacer(Modifier.height(8.dp))
                Text("End", style = MaterialTheme.typography.labelLarge)
                DateTimeRow(
                    date = endDate, time = endTime,
                    onPickDate = { onPicked ->
                        android.app.DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) }, endDate.year, endDate.monthValue - 1, endDate.dayOfMonth).show()
                    },
                    onDateChange = { endDate = it },
                    onPickTime = { onPicked ->
                        android.app.TimePickerDialog(context, { _, h, min -> onPicked(LocalTime.of(h, min)) }, endTime.hour, endTime.minute, false).show()
                    },
                    onTimeChange = { endTime = it }
                )

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
                val start = LocalDateTime.of(startDate, startTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val end = LocalDateTime.of(endDate, endTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                onSave(name.trim(), description.trim().ifBlank { null }, start, end, retention)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DateTimeRow(
    date: LocalDate,
    time: LocalTime,
    onPickDate: ((LocalDate) -> Unit) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onPickTime: ((LocalTime) -> Unit) -> Unit,
    onTimeChange: (LocalTime) -> Unit
) {
    Row(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Date") },
            trailingIcon = {
                IconButton(onClick = { onPickDate(onDateChange) }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                }
            },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.height(0.dp))
        OutlinedTextField(
            value = time.format(DateTimeFormatter.ofPattern("hh:mm a")),
            onValueChange = {},
            readOnly = true,
            label = { Text("Time") },
            trailingIcon = {
                IconButton(onClick = { onPickTime(onTimeChange) }) {
                    Icon(Icons.Default.Schedule, contentDescription = "Pick time")
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}
