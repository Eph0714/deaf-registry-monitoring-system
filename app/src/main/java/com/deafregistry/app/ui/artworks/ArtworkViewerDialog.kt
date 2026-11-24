package com.deafregistry.app.ui.artworks

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.deafregistry.app.data.remote.dto.ArtworkDto
import com.deafregistry.app.util.ExportUtils
import kotlinx.coroutines.launch

/**
 * Full-screen artwork viewer - swipe left/right between every image in the same folder (in the
 * grid's current, always-name-ascending order), same pinch-zoom-per-image and Download button as
 * [com.deafregistry.app.ui.common.PhotoViewerDialog]. A separate composable rather than extending
 * that shared dialog, since PhotoViewerDialog is used elsewhere (profile photos, chat) for a
 * single image and changing its shape there isn't warranted.
 */
@Composable
fun ArtworkViewerDialog(artworks: List<ArtworkDto>, startIndex: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex) { artworks.size }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    val current = artworks[pagerState.currentPage]
    var isDownloaded by remember(current.fileName) {
        mutableStateOf(ExportUtils.isAlreadyDownloaded(context, current.fileName))
    }

    // Saves straight into this app's own private storage - no destination picker (see
    // ExportUtils.downloadImageToAppStorage).
    fun downloadImage() {
        isDownloading = true
        downloadProgress = 0f
        scope.launch {
            runCatching {
                ExportUtils.downloadImageToAppStorage(context, current.fileName, current.publicUrlOptimized) { written, total ->
                    downloadProgress = if (total > 0) written.toFloat() / total else 0f
                }
            }
                .onSuccess {
                    isDownloaded = true
                    Toast.makeText(context, "Saved", Toast.LENGTH_LONG).show()
                }
                .onFailure { Toast.makeText(context, "Download failed: ${com.deafregistry.app.util.friendlyMessage(it)}", Toast.LENGTH_LONG).show() }
            isDownloading = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableArtworkPage(
                    model = artworks[page].publicUrlOptimized,
                    contentDescription = artworks[page].fileName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                if (isDownloading) {
                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Downloading… ${(downloadProgress * 100).toInt()}%",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                    )
                }
                Text(
                    "${current.fileName}  (${pagerState.currentPage + 1}/${artworks.size})",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                )
            }
            Row(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                if (!isDownloaded) {
                    IconButton(
                        onClick = { downloadImage() },
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

/**
 * Pinch-zoom (1x-5x) + pan-while-zoomed + double-tap-to-reset, same as the shared
 * ZoomableAsyncImage - except it only reacts to a single-finger drag when already zoomed in.
 * ZoomableAsyncImage's gesture detector (via detectTransformGestures) consumes every pointer
 * movement unconditionally, single-finger included, even when that movement has no visual
 * effect at 1x zoom - which silently eats the swipe before HorizontalPager's own drag detector
 * (an ancestor, processed after descendants in the same gesture pass) ever sees it. This instead
 * only starts consuming once there's a second finger down (an actual pinch) or the image is
 * already zoomed in, so a plain one-finger swipe at 1x falls through untouched and the pager
 * still changes pages.
 */
@Composable
private fun ZoomableArtworkPage(model: Any?, contentDescription: String?, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val isMultiTouch = event.changes.size > 1
                        if ((isMultiTouch || scale > 1f) && (zoomChange != 1f || panChange != Offset.Zero)) {
                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                            offset = if (scale <= 1f) Offset.Zero else offset + panChange
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
