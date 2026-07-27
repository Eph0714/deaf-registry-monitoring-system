package com.deafregistry.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deafregistry.app.BuildConfig
import com.deafregistry.app.data.remote.dto.UserDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.PhotoViewerDialog
import com.deafregistry.app.ui.common.resolvePhotoUrl
import kotlinx.coroutines.launch

private val BASE_ROLES = listOf("conductor", "admin")
private val ALL_ROLES = listOf("conductor", "admin", "super_admin")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.userRepository
    val isSuperAdmin = ServiceLocator.sessionManager.isSuperAdmin()
    val roles = if (isSuperAdmin) ALL_ROLES else BASE_ROLES
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf(listOf<UserDto>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("conductor") }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var editingUser by remember { mutableStateOf<UserDto?>(null) }
    var editName by remember { mutableStateOf("") }
    var editUsername by remember { mutableStateOf("") }
    var editRole by remember { mutableStateOf("conductor") }
    var editTeacherId by remember { mutableStateOf<Int?>(null) }
    var editActive by remember { mutableStateOf(true) }
    var editNewPassword by remember { mutableStateOf("") }
    var editPasswordVisible by remember { mutableStateOf(false) }
    var editRoleMenuExpanded by remember { mutableStateOf(false) }
    var showInactive by remember { mutableStateOf(false) }
    var permanentDeleteTarget by remember { mutableStateOf<UserDto?>(null) }
    var viewingPhotoUser by remember { mutableStateOf<UserDto?>(null) }

    suspend fun reload() {
        runCatching { users = repo.list() }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "User Accounts", onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                newName = ""; newEmail = ""; newUsername = ""; newPassword = ""; newRole = "conductor"; newPasswordVisible = false; showAddDialog = true
            }) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { padding: PaddingValues ->
        val inactiveCount = users.count { it.isActive == false }
        val visibleUsers = if (showInactive) users else users.filter { it.isActive != false }

        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (inactiveCount > 0) {
                item {
                    TextButton(onClick = { showInactive = !showInactive }) {
                        Text(if (showInactive) "Hide deleted accounts" else "Show deleted accounts ($inactiveCount)")
                    }
                }
            }
            items(visibleUsers, key = { it.id }) { user ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val resolvedPhoto = resolvePhotoUrl(user.photoUrl, BuildConfig.API_BASE_URL)
                        if (resolvedPhoto != null) {
                            AsyncImage(
                                model = resolvedPhoto,
                                contentDescription = "${user.name}'s photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .clickable { viewingPhotoUser = user }
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .padding(4.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${user.username ?: user.email} • ${user.role}" + (user.teacherName?.let { " • $it" } ?: "") +
                                    if (user.isActive == false) " • Deleted" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (user.isActive == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val canManage = isSuperAdmin || user.role != "super_admin"
                        if (canManage) {
                            if (user.isActive == false) {
                                TextButton(onClick = {
                                    editingUser = user
                                    editName = user.name
                                    editUsername = user.username ?: ""
                                    editRole = user.role
                                    editTeacherId = user.teacherId
                                    editActive = true
                                    editNewPassword = ""
                                    editPasswordVisible = false
                                }) { Text("Restore") }
                                TextButton(onClick = { permanentDeleteTarget = user }) {
                                    Text("Delete Permanently", color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                IconButton(onClick = {
                                    editingUser = user
                                    editName = user.name
                                    editUsername = user.username ?: ""
                                    editRole = user.role
                                    editTeacherId = user.teacherId
                                    editActive = user.isActive != false
                                    editNewPassword = ""
                                    editPasswordVisible = false
                                }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                                IconButton(onClick = {
                                    scope.launch {
                                        runCatching { repo.deactivate(user.id) }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
                                        reload()
                                    }
                                }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                            }
                        }
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
                    OutlinedTextField(value = newName, onValueChange = { newName = it.uppercase() }, label = { Text("Name") })
                    OutlinedTextField(value = newEmail, onValueChange = { newEmail = it }, label = { Text("Email") })
                    OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, label = { Text("Username") })
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Temporary Password") },
                        visualTransformation = if (isSuperAdmin && newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            if (isSuperAdmin) {
                                IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                    Icon(
                                        if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (newPasswordVisible) "Hide password" else "Show password"
                                    )
                                }
                            }
                        }
                    )
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
                            roles.forEach { role ->
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
                        runCatching { repo.create(newName.trim(), newEmail.trim(), newUsername.trim(), newPassword, newRole, null) }
                            .onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
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
                    OutlinedTextField(value = editName, onValueChange = { editName = it.uppercase() }, label = { Text("Name") })
                    OutlinedTextField(value = editUsername, onValueChange = { editUsername = it }, label = { Text("Username") })
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
                            roles.forEach { role ->
                                DropdownMenuItem(text = { Text(role) }, onClick = { editRole = role; editRoleMenuExpanded = false })
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
                        label = { Text("Reset Password (optional)") },
                        visualTransformation = if (isSuperAdmin && editPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            if (isSuperAdmin) {
                                IconButton(onClick = { editPasswordVisible = !editPasswordVisible }) {
                                    Icon(
                                        if (editPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (editPasswordVisible) "Hide password" else "Show password"
                                    )
                                }
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = user
                    editingUser = null
                    scope.launch {
                        runCatching { repo.update(target.id, editName.trim(), editUsername.trim(), editRole, editTeacherId, editActive) }
                            .onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
                        if (editNewPassword.isNotBlank()) {
                            runCatching { repo.resetPassword(target.id, editNewPassword) }
                                .onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
                        }
                        reload()
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingUser = null }) { Text("Cancel") } }
        )
    }

    permanentDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    "This will permanently remove ${target.name} (${target.email}) from the system. " +
                        "This cannot be undone - the account cannot be restored afterward."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    permanentDeleteTarget = null
                    scope.launch {
                        runCatching { repo.permanentlyDelete(id) }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
                        reload()
                    }
                }) { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { permanentDeleteTarget = null }) { Text("Cancel") } }
        )
    }

    viewingPhotoUser?.let { target ->
        val resolvedPhoto = resolvePhotoUrl(target.photoUrl, BuildConfig.API_BASE_URL)
        if (resolvedPhoto != null) {
            PhotoViewerDialog(
                photoUrl = resolvedPhoto,
                fileName = "${target.name.replace(Regex("[^A-Za-z0-9-_ ]"), "").ifBlank { "user" }}.jpg",
                onDismiss = { viewingPhotoUser = null }
            )
        }
    }
}
