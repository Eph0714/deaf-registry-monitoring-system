package com.deafregistry.app.ui.share

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.deafregistry.app.BuildConfig
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.util.GitHubUpdateChecker

// Same GitHub Releases page the app itself checks for updates against (see GitHubUpdateChecker)
// - falls back to this page link (rather than failing) if the API call doesn't succeed (offline,
// rate-limited), since a releases page link is still something a recipient can act on.
private const val RELEASES_PAGE_URL = "https://github.com/Eph0714/deaf-registry-monitoring-system/releases/latest"

@Composable
fun ShareAppScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var versionName by remember { mutableStateOf(BuildConfig.VERSION_NAME) }
    var apkUrl by remember { mutableStateOf(RELEASES_PAGE_URL) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val latest = runCatching { GitHubUpdateChecker.fetchLatest() }.getOrNull()
        if (latest != null && !latest.apkUrl.isNullOrBlank()) {
            versionName = latest.versionName ?: BuildConfig.VERSION_NAME
            apkUrl = latest.apkUrl
        }
        isLoading = false
    }

    val shareText = "Deaf Registry and Monitoring System v$versionName\nDownload: $apkUrl"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Share this App", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Share the latest version and its download link with others via text, Messenger, or any other app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = "v$versionName",
                onValueChange = {},
                readOnly = true,
                label = { Text("Version") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apkUrl,
                onValueChange = {},
                readOnly = true,
                label = { Text("APK download link") },
                trailingIcon = {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(apkUrl)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share app via"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
        }
    }
}
