package com.deafregistry.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.theme.AppThemeOption
import com.deafregistry.app.ui.theme.ThemeState

/**
 * Every user's theme choice applies only to their own device - saving never touches the server,
 * so one person's pick can never change what anyone else sees (SettingsRepository.
 * setLocalThemeOverride). Applies live immediately via ThemeState.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.settingsRepository
    var selected by remember { mutableStateOf(ThemeState.current) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Theme Color", onBack = onBack) }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Sets the color theme for your device only - it won't change anyone else's app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            ThemeOptionRow(
                title = "Light",
                subtitle = "White background, blue banner, black text",
                selected = selected == AppThemeOption.LIGHT_BLUE,
                onClick = { selected = AppThemeOption.LIGHT_BLUE }
            )
            Spacer(Modifier.height(8.dp))
            ThemeOptionRow(
                title = "Dark Purple",
                subtitle = "Dark background, light purple banner, white text",
                selected = selected == AppThemeOption.DARK_PURPLE,
                onClick = { selected = AppThemeOption.DARK_PURPLE }
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    repo.setLocalThemeOverride(selected)
                    message = "Saved on this device"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
