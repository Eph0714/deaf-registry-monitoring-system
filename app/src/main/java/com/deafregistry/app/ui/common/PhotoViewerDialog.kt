package com.deafregistry.app.ui.common

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.deafregistry.app.util.ExportUtils
import kotlinx.coroutines.launch

/**
 * Full-screen photo viewer with a Download button, used for both user and deaf-individual photos.
 * [photoUrl] can be either a real network URL or a local, not-yet-synced file path -
 * [ExportUtils.writeImageToUri] handles both the same way, so the button always works.
 * Download opens the system's "Save As" picker so the user chooses exactly where to save it.
 */
@Composable
fun PhotoViewerDialog(photoUrl: String, fileName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri: Uri? ->
        if (uri != null) {
            isDownloading = true
            scope.launch {
                runCatching { ExportUtils.writeImageToUri(context, uri, photoUrl) }
                    .onSuccess { Toast.makeText(context, "Saved", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_LONG).show() }
                isDownloading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Row(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                IconButton(
                    onClick = { saveLauncher.launch(fileName) },
                    enabled = !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

/** Resolves a possibly-relative legacy `/uploads/...` photo path into a full URL; passes full URLs through unchanged. */
fun resolvePhotoUrl(raw: String?, apiBaseUrl: String): String? =
    raw?.let { if (it.startsWith("/uploads")) apiBaseUrl.removeSuffix("/api/") + it else it }
