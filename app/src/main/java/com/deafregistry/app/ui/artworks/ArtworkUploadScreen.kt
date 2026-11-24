package com.deafregistry.app.ui.artworks

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.launch
import java.io.File

// Mirrors the backend's artwork_max_batch_size setting (see artworks.controller.js's
// maxBatchSize()) - kept in sync by hand since this is a client-side UX cap (picker limit +
// pre-upload check), not something the app fetches at runtime.
private const val MAX_IMAGES_PER_UPLOAD = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkUploadScreen(folderId: Int, onBack: () -> Unit, onUploaded: () -> Unit) {
    val isAdmin = ServiceLocator.sessionManager.isAdmin()
    val repo = ServiceLocator.artworksRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFiles by remember { mutableStateOf(listOf<File>()) }
    var note by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_UPLOAD)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val files = uris.mapNotNull { uri ->
                runCatching {
                    val file = File(context.cacheDir, "artwork_${System.currentTimeMillis()}_${uri.lastPathSegment}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    file
                }.getOrNull()
            }
            // The picker's own maxItems only caps a single invocation - re-selecting after
            // already having some picked could otherwise push the combined batch past the limit.
            val combined = selectedFiles + files
            if (combined.size > MAX_IMAGES_PER_UPLOAD) {
                error = "You can upload at most $MAX_IMAGES_PER_UPLOAD images at once"
            } else {
                selectedFiles = combined
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Upload Images", onBack = onBack) }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                if (isAdmin) "Images you upload are published immediately."
                else "Your images will be reviewed by an admin before appearing in the gallery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer()

            OutlinedButton(onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Text(" Select Images", modifier = Modifier.padding(start = 4.dp))
            }

            Spacer()

            if (selectedFiles.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(selectedFiles) { file ->
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                            AsyncImage(
                                model = file,
                                contentDescription = file.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { selectedFiles = selectedFiles - file },
                                modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer()
            }

            if (!isAdmin) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note to admin (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer()
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer() }

            if (isUploading) {
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Uploading… ${(uploadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer()
            }

            Button(
                onClick = {
                    isUploading = true
                    uploadProgress = 0f
                    error = null
                    scope.launch {
                        val onProgress: (Long, Long) -> Unit = { sent, total ->
                            uploadProgress = if (total > 0) sent.toFloat() / total else 0f
                        }
                        val result = if (isAdmin) {
                            runCatching { repo.uploadDirect(folderId, selectedFiles, onProgress) }
                        } else {
                            runCatching { repo.uploadRequest(folderId, selectedFiles, note.trim().takeIf { it.isNotBlank() }, onProgress) }
                        }
                        isUploading = false
                        result.onSuccess { onUploaded() }.onFailure { error = friendlyMessage(it) }
                    }
                },
                enabled = selectedFiles.isNotEmpty() && !isUploading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isAdmin) "Publish" else "Submit for Approval")
            }
        }
    }
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
}
