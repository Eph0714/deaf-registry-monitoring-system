package com.deafregistry.app.ui.artworks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deafregistry.app.data.remote.dto.ArtworkRequestDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkMyRequestsScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.artworksRepository
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf(listOf<ArtworkRequestDto>()) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        runCatching { repo.listMyRequests() }
            .onSuccess { requests = it; error = null }
            .onFailure { error = friendlyMessage(it) }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "My Requests", onBack = onBack) }
    ) { padding: PaddingValues ->
        if (requests.isEmpty() && error == null) {
            EmptyState("You haven't submitted any upload or deletion requests.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(requests, key = { it.id }) { request ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (request.requestType == "upload") "Upload request" else "Deletion request",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "Folder: ${request.folderName ?: "—"} • ${request.items.size} image(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AssistChip(onClick = {}, label = { Text(statusLabel(request.status)) })
                            }
                            request.rejectionReason?.takeIf { it.isNotBlank() }?.let {
                                Text("Reason for rejection: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (request.items.isNotEmpty()) {
                                LazyRow(Modifier.padding(top = 8.dp)) {
                                    items(request.items, key = { it.id }) { item ->
                                        AsyncImage(
                                            model = item.publicUrlThumbnail,
                                            contentDescription = item.fileName,
                                            modifier = Modifier.size(56.dp).padding(end = 6.dp)
                                        )
                                    }
                                }
                            }
                            if (request.status == "pending") {
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { repo.cancelRequest(request.id) }
                                            .onSuccess { reload() }
                                            .onFailure { error = friendlyMessage(it) }
                                    }
                                }) { Text("Cancel Request") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String) = when (status) {
    "pending" -> "Pending"
    "approved" -> "Approved"
    "rejected" -> "Rejected"
    "cancelled" -> "Cancelled"
    else -> status
}
