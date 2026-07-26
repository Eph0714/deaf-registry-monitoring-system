package com.deafregistry.app.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.remote.dto.ChatSessionDto
import com.deafregistry.app.data.remote.dto.ChatStatusDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import kotlinx.coroutines.launch

/**
 * Sessions are now created exclusively by Recurring Schedules (see ChatRecurringSchedulesScreen) -
 * the old manual "New Chat Session"/"Edit" flow (a one-off pick-a-date-and-time dialog) was removed
 * per explicit request, so an admin/super_admin no longer has a way to schedule a session outside a
 * recurring template. This screen is left managing the lifecycle of whatever sessions the scheduler
 * already generated - open/close early, clear messages, or delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSessionManageScreen(onBack: () -> Unit, onOpenRecurringSchedules: () -> Unit, onOpenSingleSchedules: () -> Unit) {
    val repo = ServiceLocator.chatRepository
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf(listOf<ChatSessionDto>()) }
    var status by remember { mutableStateOf<ChatStatusDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var clearTarget by remember { mutableStateOf<ChatSessionDto?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatSessionDto?>(null) }

    suspend fun reload() {
        runCatching { sessions = repo.listSessions() }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
        runCatching { status = repo.chatStatus() }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Manage Chat Sessions",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenSingleSchedules) {
                        Icon(Icons.Default.Today, contentDescription = "Single-Time Schedules")
                    }
                    IconButton(onClick = onOpenRecurringSchedules) {
                        Icon(Icons.Default.EventRepeat, contentDescription = "Recurring Schedules")
                    }
                }
            )
        }
    ) { padding: PaddingValues ->
        if (sessions.isEmpty() && error == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                status?.let { ChatStatusCard(it) }
                EmptyState("No chat sessions yet. Set up a Recurring or Single-Time Schedule (top-right icons) to have sessions created automatically.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                status?.let { item { ChatStatusCard(it) } }
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
                                            runCatching { repo.openSession(session.id) }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
                                            reload()
                                        }
                                    }) { Text("Open") }
                                }
                                if (session.status == "open") {
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { repo.closeSession(session.id) }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
                                            reload()
                                        }
                                    }) { Text("Close") }
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

    clearTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { clearTarget = null },
            title = { Text("Clear all messages?") },
            text = { Text("This immediately deletes every message in \"${target.sessionName}\". This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    clearTarget = null
                    scope.launch {
                        runCatching { repo.clearMessages(target.id) }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
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
                        runCatching { repo.deleteSession(target.id) }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
                        reload()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

private fun formatTime(raw: String): String = runCatching {
    java.time.LocalTime.parse(raw.take(5)).format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
}.getOrDefault(raw)

/** Today's Chat Status widget: open/closed, which schedule type is currently in effect (if any),
 * its start/end time, and the next upcoming session - backed by the shared GET /api/chat/status
 * computation ChatRoomScreen's closed-state messaging also uses (see chat.controller.js::getChatStatus). */
@Composable
private fun ChatStatusCard(status: ChatStatusDto) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (status.isOpen) "🟢 Chat Open" else "🔴 Chat Closed",
                style = MaterialTheme.typography.titleMedium
            )
            status.activeSchedule?.let { active ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Schedule Type: ${if (active.type == "single") "Single-Time" else "Recurring"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "${formatClockTime(active.startDatetime)} – ${formatClockTime(active.endDatetime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            status.nextSchedule?.let { next ->
                Text(
                    "Next Scheduled Chat Session: ${next.dayLabel} ${formatTime(next.startTime)} – ${formatTime(next.endTime)}",
                    style = MaterialTheme.typography.bodySmall
                )
            } ?: Text("No upcoming chat sessions scheduled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
