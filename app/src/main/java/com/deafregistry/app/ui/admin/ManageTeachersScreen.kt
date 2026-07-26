package com.deafregistry.app.ui.admin

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTeachersScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.referenceDataRepository
    val teachers by repo.observeTeachers().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newContact by remember { mutableStateOf("") }
    var showBulkReassignDialog by remember { mutableStateOf(false) }
    var bulkMessage by remember { mutableStateOf<String?>(null) }
    var editingTeacher by remember { mutableStateOf<com.deafregistry.app.data.local.entity.TeacherEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editContact by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "BS Conductors",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showBulkReassignDialog = true }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Bulk Reassign")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newName = ""; newContact = ""; showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding: PaddingValues ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            bulkMessage?.let { msg ->
                item { Text(msg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp)) }
            }
            items(teachers, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${t.contactNumber ?: "No contact"} • ${t.assignedCount} assigned",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            editingTeacher = t
                            editName = t.name
                            editContact = t.contactNumber ?: ""
                        }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { repo.deleteTeacher(t.id) }
                                runCatching { repo.refreshAll() }
                            }
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add BS Conductor") },
            text = {
                Column {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") })
                    OutlinedTextField(value = newContact, onValueChange = { newContact = it }, label = { Text("Contact Number (optional)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    scope.launch {
                        runCatching { repo.createTeacher(newName.trim(), newContact.ifBlank { null }) }
                        runCatching { repo.refreshAll() }
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    editingTeacher?.let { target ->
        AlertDialog(
            onDismissRequest = { editingTeacher = null },
            title = { Text("Edit BS Conductor") },
            text = {
                Column {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") })
                    OutlinedTextField(value = editContact, onValueChange = { editContact = it }, label = { Text("Contact Number (optional)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editingTeacher = null
                    scope.launch {
                        runCatching { repo.updateTeacher(target.id, editName.trim(), editContact.ifBlank { null }) }
                        runCatching { repo.refreshAll() }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingTeacher = null }) { Text("Cancel") } }
        )
    }

    if (showBulkReassignDialog) {
        BulkReassignDialog(
            teachers = teachers.map { it.id to it.name },
            onDismiss = { showBulkReassignDialog = false },
            onConfirm = { fromId, toId, reason ->
                showBulkReassignDialog = false
                scope.launch {
                    val result = runCatching { repo.bulkReassignTeacher(fromId, toId, reason) }
                    result.onSuccess { bulkMessage = "${it.reassignedCount} individual(s) reassigned" }
                    result.onFailure { bulkMessage = "Reassignment failed: ${com.deafregistry.app.util.friendlyMessage(it)}" }
                    runCatching { repo.refreshAll() }
                    runCatching { ServiceLocator.deafIndividualRepository.refreshFromServer() }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkReassignDialog(
    teachers: List<Pair<Int, String>>,
    onDismiss: () -> Unit,
    onConfirm: (fromId: Int, toId: Int, reason: String?) -> Unit
) {
    var fromId by remember { mutableStateOf<Int?>(null) }
    var toId by remember { mutableStateOf<Int?>(null) }
    var reason by remember { mutableStateOf("") }
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk Reassign Teacher") },
        text = {
            Column {
                ExposedDropdownMenuBox(expanded = fromExpanded, onExpandedChange = { fromExpanded = it }) {
                    OutlinedTextField(
                        value = teachers.firstOrNull { it.first == fromId }?.second ?: "From teacher",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("From") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }) {
                        teachers.forEach { (id, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { fromId = id; fromExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = toExpanded, onExpandedChange = { toExpanded = it }) {
                    OutlinedTextField(
                        value = teachers.firstOrNull { it.first == toId }?.second ?: "To teacher",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = toExpanded, onDismissRequest = { toExpanded = false }) {
                        teachers.forEach { (id, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { toId = id; toExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { fromId?.let { f -> toId?.let { t -> onConfirm(f, t, reason.ifBlank { null }) } } },
                enabled = fromId != null && toId != null && fromId != toId
            ) { Text("Reassign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
