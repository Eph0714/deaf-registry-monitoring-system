package com.deafregistry.app.ui.admin

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.util.BackupUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSuperAdmin = ServiceLocator.sessionManager.isSuperAdmin()

    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            runCatching { BackupUtil.backupTo(context, uri) }
                .onSuccess { Toast.makeText(context, "Local backup saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { BackupUtil.restoreFrom(context, uri) }
                .onSuccess { Toast.makeText(context, "Restored. Restart the app to apply.", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "Backup & Restore", onBack = onBack)
        }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Local Device Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Copies the on-device registry database to a file you choose.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { createDocLauncher.launch("deaf_registry_backup.db") }) { Text("Back Up Now") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                        enabled = isSuperAdmin
                    ) { Text("Restore From File") }
                    if (!isSuperAdmin) {
                        Text(
                            "Only the Super Admin can restore from a backup file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Server Database Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Triggers a MySQL dump on the server (requires connectivity).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch {
                            runCatching { ServiceLocator.adminRepository.createServerBackup() }
                                .onSuccess { Toast.makeText(context, "Server backup created", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_LONG).show() }
                        }
                    }) { Text("Trigger Server Backup") }
                }
            }
        }
    }
}
