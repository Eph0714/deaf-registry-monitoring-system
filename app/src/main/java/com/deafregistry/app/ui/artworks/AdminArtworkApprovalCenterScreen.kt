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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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

private val TABS = listOf(
    "Pending Uploads" to ("pending" to "upload"),
    "Pending Deletions" to ("pending" to "delete"),
    "Approved" to ("approved" to null),
    "Rejected" to ("rejected" to null)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminArtworkApprovalCenterScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.artworksRepository
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var requests by remember { mutableStateOf(listOf<ArtworkRequestDto>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var rejectTarget by remember { mutableStateOf<ArtworkRequestDto?>(null) }

    suspend fun reload() {
        val (status, type) = TABS[selectedTab].second
        runCatching { repo.listRequests(status, type) }
            .onSuccess { requests = it; error = null }
            .onFailure { error = friendlyMessage(it) }
    }

    LaunchedEffect(selectedTab) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Artwork Approval Center", onBack = onBack) }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                TABS.forEachIndexed { index, (label, _) ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (requests.isEmpty()) {
                EmptyState("Nothing here.")
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(requests, key = { it.id }) { request ->
                        RequestCard(
                            request = request,
                            onApprove = {
                                scope.launch {
                                    runCatching { repo.approveRequest(request.id) }
                                        .onSuccess { reload() }
                                        .onFailure { error = friendlyMessage(it) }
                                }
                            },
                            onReject = { rejectTarget = request }
                        )
                    }
                }
            }
        }
    }

    rejectTarget?.let { request ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Reject Request") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Rejection reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { repo.rejectRequest(request.id, reason.trim()) }
                            .onSuccess { rejectTarget = null; reload() }
                            .onFailure { error = friendlyMessage(it) }
                    }
                }, enabled = reason.isNotBlank()) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { rejectTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RequestCard(request: ArtworkRequestDto, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${request.requestedByName ?: "A user"} • ${if (request.requestType == "upload") "Upload" else "Deletion"}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Folder: ${request.folderName ?: "—"} • ${request.items.size} image(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            request.reason?.takeIf { it.isNotBlank() }?.let {
                Text("Reason: $it", style = MaterialTheme.typography.bodySmall)
            }
            request.rejectionReason?.takeIf { it.isNotBlank() }?.let {
                Text("Rejected: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Submitted ${request.createdAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (request.items.isNotEmpty()) {
                LazyRow(Modifier.padding(top = 8.dp)) {
                    items(request.items, key = { it.id }) { item ->
                        AsyncImage(
                            model = item.publicUrlThumbnail,
                            contentDescription = item.fileName,
                            modifier = Modifier.size(64.dp).padding(end = 6.dp)
                        )
                    }
                }
            }

            if (request.status == "pending") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    IconButton(onClick = onApprove) { Icon(Icons.Default.Check, contentDescription = "Approve") }
                    IconButton(onClick = onReject) { Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
