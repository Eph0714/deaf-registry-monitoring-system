package com.deafregistry.app.ui.common

import android.widget.Toast
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
 * Full-screen photo viewer, used for both user and deaf-individual photos. [allowDownload]
 * should be false when [photoUrl] is a local, not-yet-synced file path rather than a real network
 * URL - there's nothing to download over HTTP in that case, so the button is hidden entirely.
 */
@Composable
fun PhotoViewerDialog(photoUrl: String, fileName: String, onDismiss: () -> Unit, allowDownload: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Row(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                if (allowDownload) {
                    IconButton(
                        onClick = {
                            isDownloading = true
                            scope.launch {
                                runCatching { ExportUtils.downloadImage(context, photoUrl, fileName) }
                                    .onSuccess { Toast.makeText(context, "Saved to $it", Toast.LENGTH_LONG).show() }
                                    .onFailure { Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_LONG).show() }
                                isDownloading = false
                            }
                        },
                        enabled = !isDownloading
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                        }
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
