package com.deafregistry.app.ui.artworks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.deafregistry.app.data.remote.dto.ArtworkDto
import com.deafregistry.app.data.remote.dto.ArtworkFolderDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.GenericViewModelFactory
import com.deafregistry.app.util.ExportUtils
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkGalleryScreen(folderId: Int, onBack: () -> Unit, onUpload: (Int) -> Unit, onOpenSubfolder: (Int) -> Unit) {
    val isAdmin = ServiceLocator.sessionManager.isAdmin()
    val viewModel: ArtworkGalleryViewModel = viewModel(
        factory = GenericViewModelFactory { ArtworkGalleryViewModel(folderId, ServiceLocator.artworksRepository, isAdmin) }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Returning here from Upload (or anywhere else that was pushed on top) doesn't otherwise
    // re-trigger a load - this screen's ViewModel instance survives that round trip, so its state
    // would stay stale without an explicit refresh on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.load() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var renameTarget by remember { mutableStateOf<ArtworkDto?>(null) }
    var deleteRequestTarget by remember { mutableStateOf<ArtworkDto?>(null) }
    var directDeleteTarget by remember { mutableStateOf<ArtworkDto?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    var showNewSubfolderDialog by remember { mutableStateOf(false) }
    var subfolderToRename by remember { mutableStateOf<ArtworkFolderDto?>(null) }
    var subfolderToDelete by remember { mutableStateOf<ArtworkFolderDto?>(null) }
    var subfolderDeleteError by remember { mutableStateOf<String?>(null) }

    var bulkDownloadProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkDeleteRequest by remember { mutableStateOf(false) }
    var bulkActionError by remember { mutableStateOf<String?>(null) }

    fun bulkDownload() {
        val selected = state.artworks.filter { it.id in state.selectedIds }
        scope.launch {
            bulkDownloadProgress = 0 to selected.size
            var saved = 0
            var failed = 0
            selected.forEachIndexed { index, artwork ->
                bulkDownloadProgress = index to selected.size
                runCatching { ExportUtils.downloadImageToAppStorage(context, artwork.fileName, artwork.publicUrlOptimized) }
                    .onSuccess { saved++ }
                    .onFailure { failed++ }
            }
            bulkDownloadProgress = null
            val message = if (failed == 0) "Saved $saved image(s)" else "Saved $saved, $failed failed"
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.exitSelectionMode()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.selectionMode) {
                AppTopBar(
                    title = "${state.selectedIds.size} selected",
                    onBack = { viewModel.exitSelectionMode() },
                    actions = {
                        IconButton(onClick = { bulkDownload() }, enabled = bulkDownloadProgress == null) {
                            Icon(Icons.Default.Download, contentDescription = "Download selected")
                        }
                        IconButton(onClick = {
                            if (isAdmin) showBulkDeleteConfirm = true else showBulkDeleteRequest = true
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = if (isAdmin) "Delete selected" else "Request deletion for selected")
                        }
                    }
                )
            } else {
                AppTopBar(
                    title = state.folderName.ifBlank { "Artworks" },
                    onBack = onBack,
                    actions = {
                        if (isAdmin) {
                            IconButton(onClick = { showNewSubfolderDialog = true }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "New subfolder")
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!state.selectionMode) {
                FloatingActionButton(onClick = { onUpload(folderId) }) {
                    Icon(Icons.Default.Add, contentDescription = "Upload images")
                }
            }
        }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            bulkDownloadProgress?.let { (done, total) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Downloading ${done + 1} of $total…", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (state.subfolders.isEmpty() && state.artworks.isEmpty() && !state.isLoading) {
                EmptyState("This folder is empty.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.subfolders, key = { "folder-${it.id}" }) { folder ->
                        SubfolderCard(
                            folder = folder,
                            isAdmin = isAdmin,
                            onClick = { onOpenSubfolder(folder.id) },
                            onRename = { subfolderToRename = folder },
                            onDelete = { subfolderToDelete = folder }
                        )
                    }
                    itemsIndexed(state.artworks, key = { _, artwork -> artwork.id }) { index, artwork ->
                        ArtworkThumbnail(
                            artwork = artwork,
                            isAdmin = isAdmin,
                            selectionMode = state.selectionMode,
                            isSelected = artwork.id in state.selectedIds,
                            onClick = {
                                if (state.selectionMode) viewModel.toggleSelection(artwork.id) else viewerIndex = index
                            },
                            onLongClick = { viewModel.enterSelectionMode(artwork.id) },
                            onRename = { renameTarget = artwork },
                            onRequestDelete = { deleteRequestTarget = artwork },
                            onDirectDelete = { directDeleteTarget = artwork }
                        )
                    }
                }
            }
        }
    }

    viewerIndex?.let { index ->
        ArtworkViewerDialog(
            artworks = state.artworks,
            startIndex = index,
            onDismiss = { viewerIndex = null }
        )
    }

    renameTarget?.let { artwork ->
        var name by remember { mutableStateOf(artwork.fileName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Image") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameArtwork(artwork.id, name.trim()) { renameTarget = null }
                }, enabled = name.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    deleteRequestTarget?.let { artwork ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleteRequestTarget = null; actionError = null },
            title = { Text("Request Deletion") },
            text = {
                Column {
                    Text("This image stays visible until an admin approves your request.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.submitDeleteRequest(artwork.id, reason.trim()) { success, message ->
                        if (success) { deleteRequestTarget = null; actionError = null } else { actionError = message }
                    }
                }, enabled = reason.isNotBlank()) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { deleteRequestTarget = null; actionError = null }) { Text("Cancel") } }
        )
    }

    directDeleteTarget?.let { artwork ->
        AlertDialog(
            onDismissRequest = { directDeleteTarget = null },
            title = { Text("Delete \"${artwork.fileName}\"?") },
            text = { Text("It will be moved to the Recycle Bin.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteArtworkDirect(artwork.id) { _, _ -> directDeleteTarget = null }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { directDeleteTarget = null }) { Text("Cancel") } }
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete ${state.selectedIds.size} image(s)?") },
            text = { Text("They will be moved to the Recycle Bin.") },
            confirmButton = {
                TextButton(onClick = {
                    showBulkDeleteConfirm = false
                    viewModel.bulkDeleteDirect { succeeded, failed ->
                        val message = if (failed == 0) "Deleted $succeeded image(s)" else "Deleted $succeeded, $failed failed"
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showBulkDeleteRequest) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBulkDeleteRequest = false; bulkActionError = null },
            title = { Text("Request Deletion (${state.selectedIds.size} images)") },
            text = {
                Column {
                    Text("These images stay visible until an admin approves your request.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    bulkActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.bulkSubmitDeleteRequest(reason.trim()) { success, message ->
                        if (success) { showBulkDeleteRequest = false; bulkActionError = null } else { bulkActionError = message }
                    }
                }, enabled = reason.isNotBlank()) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showBulkDeleteRequest = false; bulkActionError = null }) { Text("Cancel") } }
        )
    }

    if (showNewSubfolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewSubfolderDialog = false },
            title = { Text("New Subfolder") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Folder name") }) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createSubfolder(name.trim()) { showNewSubfolderDialog = false }
                }, enabled = name.isNotBlank()) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewSubfolderDialog = false }) { Text("Cancel") } }
        )
    }

    subfolderToRename?.let { folder ->
        var name by remember { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { subfolderToRename = null },
            title = { Text("Rename Folder") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameSubfolder(folder.id, name.trim()) { subfolderToRename = null }
                }, enabled = name.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { subfolderToRename = null }) { Text("Cancel") } }
        )
    }

    subfolderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { subfolderToDelete = null; subfolderDeleteError = null },
            title = { Text("Delete \"${folder.name}\"?") },
            text = {
                Column {
                    Text("This cannot be undone.")
                    subfolderDeleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSubfolder(folder.id) { success, message ->
                        if (success) { subfolderToDelete = null; subfolderDeleteError = null } else { subfolderDeleteError = message }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { subfolderToDelete = null; subfolderDeleteError = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubfolderCard(
    folder: ArtworkFolderDto,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
      Column {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (folder.coverUrl != null) {
                AsyncImage(
                    model = folder.coverUrl,
                    contentDescription = folder.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(16.dp))
                }
            }
        }
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                folder.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isAdmin) {
                Box {
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
      }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ArtworkThumbnail(
    artwork: ArtworkDto,
    isAdmin: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onRequestDelete: () -> Unit,
    onDirectDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloaded by remember(artwork.fileName) {
        mutableStateOf(ExportUtils.isAlreadyDownloaded(context, artwork.fileName))
    }

    // Saves straight into this app's own private storage - no destination picker, nothing
    // written outside the app/device's app-local storage (see ExportUtils.downloadImageToAppStorage).
    fun downloadImage() {
        isDownloading = true
        downloadProgress = 0f
        scope.launch {
            runCatching {
                ExportUtils.downloadImageToAppStorage(context, artwork.fileName, artwork.publicUrlOptimized) { written, total ->
                    downloadProgress = if (total > 0) written.toFloat() / total else 0f
                }
            }
                .onSuccess {
                    isDownloaded = true
                    android.widget.Toast.makeText(context, "Saved", android.widget.Toast.LENGTH_LONG).show()
                }
                .onFailure {
                    android.widget.Toast.makeText(
                        context,
                        "Download failed: ${friendlyMessage(it)}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            isDownloading = false
        }
    }

    // Shares the actual image bytes (via a cache-dir file + FileProvider content:// Uri) rather
    // than just the remote URL as text - Messenger and most other share targets can't fetch an
    // arbitrary https:// link themselves, so the app has to fetch it first and hand over a real
    // local file.
    fun shareImage() {
        isDownloading = true
        downloadProgress = 0f
        scope.launch {
            runCatching {
                ExportUtils.prepareImageForShare(context, artwork.publicUrlOptimized, artwork.fileName) { written, total ->
                    downloadProgress = if (total > 0) written.toFloat() / total else 0f
                }
            }
                .onSuccess { uri ->
                    val mime = when (artwork.format.lowercase()) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share image via"))
                }
                .onFailure {
                    android.widget.Toast.makeText(
                        context,
                        "Share failed: ${friendlyMessage(it)}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            isDownloading = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
      Column {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            AsyncImage(
                model = artwork.publicUrlThumbnail,
                contentDescription = artwork.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            statusLabel(artwork)?.let { label ->
                AssistChip(
                    onClick = {},
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                )
            }
            if (selectionMode) {
                Icon(
                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                )
            }
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                )
            }
        }
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                artwork.fileName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!selectionMode && (isAdmin || (artwork.publishStatus == "published" && artwork.deletedStatus == "none"))) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Image options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (!isDownloaded) {
                            DropdownMenuItem(
                                text = { Text("Download") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                enabled = !isDownloading,
                                onClick = { menuExpanded = false; downloadImage() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            enabled = !isDownloading,
                            onClick = { menuExpanded = false; shareImage() }
                        )
                        if (isAdmin) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { menuExpanded = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = { menuExpanded = false; onDirectDelete() }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Request Deletion") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = { menuExpanded = false; onRequestDelete() }
                            )
                        }
                    }
                }
            }
        }
      }
    }
}

private fun statusLabel(artwork: ArtworkDto): String? = when {
    artwork.deletedStatus == "pending_delete" -> "Pending deletion"
    artwork.publishStatus == "pending" -> "Pending approval"
    artwork.publishStatus == "rejected" -> "Rejected"
    else -> null
}
