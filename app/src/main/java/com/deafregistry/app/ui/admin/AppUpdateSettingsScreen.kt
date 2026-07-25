package com.deafregistry.app.ui.admin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.deafregistry.app.BuildConfig
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import kotlinx.coroutines.launch

/**
 * Admin/Super Admin sets what version+download link every device gets prompted to update to
 * (see the Dashboard's update-available dialog) - this app isn't distributed through Google
 * Play, so there's no automatic update channel; this is the manual substitute.
 *
 * Conductors can open this screen too, but view-only - the fields are read-only and there's no
 * Save button, since the server rejects this PUT for anyone but admin/super_admin anyway.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateSettingsScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.settingsRepository
    val isAdmin = ServiceLocator.sessionManager.isAdmin()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var versionCodeText by remember { mutableStateOf("") }
    var versionName by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var releaseNotes by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { repo.getLatestAppVersion() }.onSuccess {
            if (it.versionCode > 0) versionCodeText = it.versionCode.toString()
            versionName = it.versionName ?: ""
            apkUrl = it.apkUrl ?: ""
            releaseNotes = it.releaseNotes ?: ""
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "App Update", onBack = onBack) }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "This device is running version ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isAdmin) {
                    "Set the newest published version below - anyone on an older version code will see an update prompt with a link to this URL."
                } else {
                    "View only - contact an administrator to change this."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = versionCodeText,
                onValueChange = { versionCodeText = it.filter { c -> c.isDigit() } },
                label = { Text("Version code (matches versionCode in build.gradle.kts)") },
                readOnly = !isAdmin,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = versionName,
                onValueChange = { versionName = it },
                label = { Text("Version name (e.g. 1.1)") },
                readOnly = !isAdmin,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apkUrl,
                onValueChange = { apkUrl = it },
                label = { Text("Download URL for the new APK") },
                readOnly = !isAdmin,
                trailingIcon = {
                    if (apkUrl.isNotBlank()) {
                        IconButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
                            }.onFailure {
                                if (it is ActivityNotFoundException) {
                                    Toast.makeText(context, "No app found to open this link", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open download link")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = releaseNotes,
                onValueChange = { releaseNotes = it },
                label = { Text("What's new (optional)") },
                readOnly = !isAdmin,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            if (isAdmin) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val code = versionCodeText.toIntOrNull()
                        if (code == null || code < 1) {
                            message = "Enter a valid version code"
                            return@Button
                        }
                        if (versionName.isBlank() || apkUrl.isBlank()) {
                            message = "Version name and download URL are required"
                            return@Button
                        }
                        isSaving = true
                        scope.launch {
                            val result = runCatching {
                                repo.updateLatestAppVersion(code, versionName.trim(), apkUrl.trim(), releaseNotes.trim())
                            }
                            isSaving = false
                            result.onSuccess { message = "Saved" }
                            result.onFailure { message = "Failed to save: ${it.message}" }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
