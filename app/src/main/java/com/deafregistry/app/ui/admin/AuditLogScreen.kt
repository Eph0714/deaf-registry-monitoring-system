package com.deafregistry.app.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.remote.dto.AuditLogDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import kotlinx.coroutines.launch

/**
 * Admin-only report of the audit_logs table - who did what, when, across the whole app.
 * Entries older than 7 days are automatically purged server-side; "Delete All" clears
 * everything immediately (logged as its own entry, so there's a record of the clearing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.userRepository
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf(listOf<AuditLogDto>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    suspend fun reload() {
        runCatching { logs = repo.auditLogs(200) }.onFailure { error = it.message }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "User Log Report",
                onBack = onBack,
                actions = {
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = { showDeleteAllConfirm = true }) {
                            Text("Delete All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding: PaddingValues ->
        if (logs.isEmpty() && error == null) {
            EmptyState("No activity recorded yet.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                item {
                    Text(
                        "Entries older than 7 days are removed automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(logs, key = { it.id }) { log -> AuditLogRow(log) }
            }
        }
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Delete all log entries?") },
            text = { Text("This permanently removes every activity log entry. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllConfirm = false
                    scope.launch {
                        runCatching { repo.deleteAllAuditLogs() }.onFailure { error = it.message }
                        reload()
                    }
                }) { Text("Delete All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AuditLogRow(log: AuditLogDto) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${log.action} • ${log.entityType}${log.entityId?.let { " #$it" } ?: ""}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                log.userName?.let { "$it (${log.userEmail})" } ?: "Unknown user",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                log.createdAt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            log.details?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
