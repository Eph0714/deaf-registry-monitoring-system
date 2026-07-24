package com.deafregistry.app.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.remote.dto.UserDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import kotlinx.coroutines.launch

private val ROLES = listOf("conductor", "admin")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.userRepository
    val teachers by ServiceLocator.referenceDataRepository.observeTeachers().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf(listOf<UserDto>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("conductor") }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var editingUser by remember { mutableStateOf<UserDto?>(null) }
    var editName by remember { mutableStateOf("") }
    var editRole by remember { mutableStateOf("conductor") }
    var editTeacherId by remember { mutableStateOf<Int?>(null) }
    var editActive by remember { mutableStateOf(true) }
    var editNewPassword by remember { mutableStateOf("") }
    var editRoleMenuExpanded by remember { mutableStateOf(false) }
    var editTeacherMenuExpanded by remember { mutableStateOf(false) }

    suspend fun reload() {
        runCatching { users = repo.list() }.onFailure { error = it.message }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "User Accounts", onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                newName = ""; newEmail = ""; newPassword = ""; newRole = "conductor"; showAddDialog = true
            }) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { padding: PaddingValues ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            items(users, key = { it.id }) { user ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(user.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${user.email} • ${user.role}" + (user.teacherName?.let { " • $it" } ?: "") +
                                    if (user.isActive == 0) " • Inactive" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (user.isActive == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            editingUser = user
                            editName = user.name
                            editRole = user.role
                            editTeacherId = user.teacherId
                            editActive = user.isActive != 0
                            editNewPassword = ""
                        }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { repo.deactivate(user.id) }
                                reload()
                            }
                        }) { Icon(Icons.Default.Delete, contentDescription = "Deactivate") }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add User") },
            text = {
                Column {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") })
                    OutlinedTextField(value = newEmail, onValueChange = { newEmail = it }, label = { Text("Email") })
                    OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("Temporary Password") })
                    ExposedDropdownMenuBox(expanded = roleMenuExpanded, onExpandedChange = { roleMenuExpanded = it }) {
                        OutlinedTextField(
                            value = newRole,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Role") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenuExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                            ROLES.forEach { role ->
                                DropdownMenuItem(text = { Text(role) }, onClick = { newRole = role; roleMenuExpanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    scope.launch {
                        runCatching { repo.create(newName.trim(), newEmail.trim(), newPassword, newRole, null) }
                            .onFailure { error = it.message }
                        reload()
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    editingUser?.let { user ->
        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = { Text("Edit User") },
            text = {
                Column {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") })
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    ExposedDropdownMenuBox(expanded = editRoleMenuExpanded, onExpandedChange = { editRoleMenuExpanded = it }) {
                        OutlinedTextField(
                            value = editRole,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Role") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editRoleMenuExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = editRoleMenuExpanded, onDismissRequest = { editRoleMenuExpanded = false }) {
                            ROLES.forEach { role ->
                                DropdownMenuItem(text = { Text(role) }, onClick = { editRole = role; editRoleMenuExpanded = false })
                            }
                        }
                    }

                    ExposedDropdownMenuBox(expanded = editTeacherMenuExpanded, onExpandedChange = { editTeacherMenuExpanded = it }) {
                        OutlinedTextField(
                            value = teachers.firstOrNull { it.id == editTeacherId }?.name ?: "None",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Linked BS Conductor") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editTeacherMenuExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = editTeacherMenuExpanded, onDismissRequest = { editTeacherMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("None") }, onClick = { editTeacherId = null; editTeacherMenuExpanded = false })
                            teachers.forEach { teacher ->
                                DropdownMenuItem(
                                    text = { Text(teacher.name) },
                                    onClick = { editTeacherId = teacher.id; editTeacherMenuExpanded = false }
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Active", modifier = Modifier.weight(1f))
                        Switch(checked = editActive, onCheckedChange = { editActive = it })
                    }

                    OutlinedTextField(
                        value = editNewPassword,
                        onValueChange = { editNewPassword = it },
                        label = { Text("Reset Password (optional)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = user
                    editingUser = null
                    scope.launch {
                        runCatching { repo.update(target.id, editName.trim(), editRole, editTeacherId, editActive) }
                            .onFailure { error = it.message }
                        if (editNewPassword.isNotBlank()) {
                            runCatching { repo.resetPassword(target.id, editNewPassword) }
                                .onFailure { error = it.message }
                        }
                        reload()
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingUser = null }) { Text("Cancel") } }
        )
    }
}
