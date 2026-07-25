package com.deafregistry.app.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSharingSettingsScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.settingsRepository
    val scope = rememberCoroutineScope()
    var ttlText by remember { mutableStateOf(repo.cachedLocationShareTtl().toString()) }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { repo.refreshLocationShareTtl() }.onSuccess { ttlText = it.toString() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Location Sharing", onBack = onBack) }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Team location share duration",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "A shared location automatically clears from Team Locations (on every user's Dashboard) this many minutes after it was shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ttlText,
                onValueChange = { ttlText = it.filter { c -> c.isDigit() } },
                label = { Text("Minutes") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val minutes = ttlText.toIntOrNull()
                    if (minutes == null || minutes < 1) {
                        message = "Enter a valid number of minutes"
                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        val result = runCatching { repo.updateLocationShareTtl(minutes) }
                        isSaving = false
                        result.onSuccess { message = "Saved" }
                        result.onFailure { message = "Failed to save: ${it.message}" }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
