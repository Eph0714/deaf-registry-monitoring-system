package com.deafregistry.app.ui.artworks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deafregistry.app.data.remote.dto.ArtworkDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkRecycleBinScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.artworksRepository
    val scope = rememberCoroutineScope()
    var artworks by remember { mutableStateOf(listOf<ArtworkDto>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var permanentDeleteTarget by remember { mutableStateOf<ArtworkDto?>(null) }

    suspend fun reload() {
        runCatching { repo.listRecycleBin() }
            .onSuccess { artworks = it; error = null }
            .onFailure { error = friendlyMessage(it) }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Recycle Bin", onBack = onBack) }
    ) { padding: PaddingValues ->
        if (artworks.isEmpty() && error == null) {
            EmptyState("Recycle Bin is empty.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(artworks, key = { it.id }) { artwork ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp)) {
                            AsyncImage(
                                model = artwork.publicUrlThumbnail,
                                contentDescription = artwork.fileName,
                                modifier = Modifier.size(56.dp)
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(artwork.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(
                                    "Deleted ${artwork.deletedAt ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { repo.restoreArtwork(artwork.id) }
                                                .onSuccess { reload() }
                                                .onFailure { error = friendlyMessage(it) }
                                        }
                                    }) { Text("Restore") }
                                    TextButton(onClick = { permanentDeleteTarget = artwork }) {
                                        Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    permanentDeleteTarget?.let { artwork ->
        AlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            title = { Text("Permanently delete \"${artwork.fileName}\"?") },
            text = { Text("This cannot be undone - the original and optimized files will be removed from storage.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { repo.permanentlyDelete(artwork.id) }
                            .onSuccess { permanentDeleteTarget = null; reload() }
                            .onFailure { error = friendlyMessage(it) }
                    }
                }) { Text("Delete Forever") }
            },
            dismissButton = { TextButton(onClick = { permanentDeleteTarget = null }) { Text("Cancel") } }
        )
    }
}
