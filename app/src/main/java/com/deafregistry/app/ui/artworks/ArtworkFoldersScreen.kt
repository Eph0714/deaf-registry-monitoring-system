package com.deafregistry.app.ui.artworks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.deafregistry.app.data.remote.dto.ArtworkFolderDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.GenericViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkFoldersScreen(onBack: () -> Unit, onOpenFolder: (Int) -> Unit) {
    val isAdmin = ServiceLocator.sessionManager.isAdmin()
    val viewModel: ArtworkFoldersViewModel = viewModel(
        factory = GenericViewModelFactory { ArtworkFoldersViewModel(ServiceLocator.artworksRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<ArtworkFolderDto?>(null) }
    var folderToDelete by remember { mutableStateOf<ArtworkFolderDto?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // Clears the dashboard tile's unread badge - this screen is the entry point everyone opening
    // Artworks from a notification lands on, whether they're the admin reviewing a new request
    // or the conductor checking a decision on their own.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { ServiceLocator.artworksRepository.markNotificationsRead() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Artworks", onBack = onBack) },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New Folder")
                }
            }
        }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search folders") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            if (state.folders.isEmpty() && !state.isLoading) {
                EmptyState("No artwork folders yet.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.folders, key = { it.id }) { folder ->
                        FolderCard(
                            folder = folder,
                            isAdmin = isAdmin,
                            onClick = { onOpenFolder(folder.id) },
                            onRename = { folderToRename = folder },
                            onDelete = { folderToDelete = folder }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        FolderNameDialog(
            title = "New Folder",
            initialValue = "",
            onConfirm = { name -> viewModel.createFolder(name) { showCreateDialog = false } },
            onDismiss = { showCreateDialog = false }
        )
    }

    folderToRename?.let { folder ->
        FolderNameDialog(
            title = "Rename Folder",
            initialValue = folder.name,
            onConfirm = { name -> viewModel.renameFolder(folder.id, name) { folderToRename = null } },
            onDismiss = { folderToRename = null }
        )
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete \"${folder.name}\"?") },
            text = {
                Column {
                    Text("This cannot be undone.")
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folder.id) { success, message ->
                        if (success) { folderToDelete = null; deleteError = null } else { deleteError = message }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null; deleteError = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FolderNameDialog(title: String, initialValue: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Folder name") })
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderCard(
    folder: ArtworkFolderDto,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
                if (folder.coverUrl != null) {
                    AsyncImage(
                        model = folder.coverUrl,
                        contentDescription = folder.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(24.dp))
                    }
                }
                if (isAdmin) {
                    Box(Modifier.align(Alignment.TopEnd)) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Folder options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { menuExpanded = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = { menuExpanded = false; onDelete() }
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "${folder.imageCount} image${if (folder.imageCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
