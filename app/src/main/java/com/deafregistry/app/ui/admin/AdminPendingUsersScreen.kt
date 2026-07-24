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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.remote.dto.PendingSignupDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPendingUsersScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.userRepository
    val scope = rememberCoroutineScope()
    var signups by remember { mutableStateOf(listOf<PendingSignupDto>()) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        runCatching { signups = repo.pendingSignups() }.onFailure { error = it.message }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Pending User Approvals", onBack = onBack) }
    ) { padding: PaddingValues ->
        if (signups.isEmpty() && error == null) {
            EmptyState("No account requests are waiting for approval.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(signups, key = { it.id }) { signup ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(signup.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    signup.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "CP: ${signup.contactNumber ?: "—"} • ${signup.location ?: "Location not provided"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Requested ${signup.createdAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    runCatching { repo.approveSignup(signup.id) }.onFailure { error = it.message }
                                    reload()
                                }
                            }) { Icon(Icons.Default.Check, contentDescription = "Approve") }
                            IconButton(onClick = {
                                scope.launch {
                                    runCatching { repo.rejectSignup(signup.id) }.onFailure { error = it.message }
                                    reload()
                                }
                            }) { Icon(Icons.Default.Close, contentDescription = "Reject") }
                        }
                    }
                }
            }
        }
    }
}
