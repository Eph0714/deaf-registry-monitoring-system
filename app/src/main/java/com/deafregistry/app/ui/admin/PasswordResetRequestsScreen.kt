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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.remote.dto.PasswordResetRequestDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import kotlinx.coroutines.launch

/**
 * Admin/super-admin queue for "Forgot Password" requests submitted from the Login screen - this
 * app has no working email delivery for arbitrary users, so a reset here is the actual mechanism,
 * not just a notification. Resolving with a new password reuses the same Super Admin-vs-Super-Admin
 * guard the Manage Users Reset Password field already enforces (server-side, not just this screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordResetRequestsScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.userRepository
    val isSuperAdmin = ServiceLocator.sessionManager.isSuperAdmin()
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf(listOf<PasswordResetRequestDto>()) }
    var error by remember { mutableStateOf<String?>(null) }

    var resolvingRequest by remember { mutableStateOf<PasswordResetRequestDto?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }

    suspend fun reload() {
        runCatching { requests = repo.passwordResetRequests() }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Password Reset Requests", onBack = onBack) }
    ) { padding: PaddingValues ->
        if (requests.isEmpty() && error == null) {
            EmptyState("No password reset requests are waiting.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(requests, key = { it.id }) { request ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(request.username, style = MaterialTheme.typography.titleMedium)
                                request.note?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    "Requested ${request.requestedAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                resolvingRequest = request
                                newPassword = ""
                                newPasswordVisible = false
                            }) { Icon(Icons.Default.LockReset, contentDescription = "Reset Password") }
                            IconButton(onClick = {
                                scope.launch {
                                    runCatching { repo.resolvePasswordResetRequest(request.id, null) }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
                                    reload()
                                }
                            }) { Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    resolvingRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { resolvingRequest = null },
            title = { Text("Reset Password") },
            text = {
                Column {
                    Text(
                        "Set a new password for \"${request.username}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
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
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newPassword.isNotBlank(),
                    onClick = {
                        val target = request
                        resolvingRequest = null
                        scope.launch {
                            runCatching { repo.resolvePasswordResetRequest(target.id, newPassword) }.onFailure { error = com.deafregistry.app.util.friendlyMessage(it) }
                            reload()
                        }
                    }
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { resolvingRequest = null }) { Text("Cancel") } }
        )
    }
}
