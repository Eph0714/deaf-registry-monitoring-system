package com.deafregistry.app.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONFIRM_PHRASE = "RESET ALL DATA"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetAllDataScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Reset All Data", onBack = onBack) }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("This is irreversible", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This permanently deletes on the server:\n" +
                            "• All deaf individual records\n" +
                            "• All visits and remarks\n" +
                            "• All teacher assignment history\n" +
                            "• All activity log entries\n" +
                            "• All other user accounts (including admins and conductors)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Kept: municipalities, barangays, BS Conductor records, app settings, and your own Super Admin account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { confirmText = ""; showConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = !isWorking
                    ) { Text(if (isWorking) "Resetting..." else "Reset All Data") }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isWorking) showConfirmDialog = false },
            title = { Text("Are you absolutely sure?") },
            text = {
                Column {
                    Text("Type \"$CONFIRM_PHRASE\" to confirm. This cannot be undone.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        label = { Text(CONFIRM_PHRASE) },
                        enabled = !isWorking
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isWorking = true
                        scope.launch {
                            runCatching {
                                ServiceLocator.adminRepository.resetAllData()
                                // The server-side wipe doesn't update this device's local Room cache by
                                // itself (this app is offline-first) - without this, the app keeps showing
                                // the old records from before the reset until something else happens to
                                // clear/resync them, making the reset look like it did nothing.
                                withContext(Dispatchers.IO) { ServiceLocator.database.clearAllTables() }
                                ServiceLocator.syncManager.pull()
                            }
                                .onSuccess {
                                    Toast.makeText(context, "All data has been reset", Toast.LENGTH_LONG).show()
                                    showConfirmDialog = false
                                    onBack()
                                }
                                .onFailure {
                                    Toast.makeText(context, "Reset failed: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            isWorking = false
                        }
                    },
                    enabled = confirmText == CONFIRM_PHRASE && !isWorking
                ) { Text("Reset All Data", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }, enabled = !isWorking) { Text("Cancel") }
            }
        )
    }
}
