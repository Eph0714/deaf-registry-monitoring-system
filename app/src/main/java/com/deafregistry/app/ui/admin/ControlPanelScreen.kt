package com.deafregistry.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deafregistry.app.ui.common.AppTopBar

private val CONTROL_PANEL_ITEMS = listOf(
    AdminMenuItem("Municipalities", "Add, rename, or remove municipalities", "admin_municipalities"),
    AdminMenuItem("Barangays", "Manage barangays per municipality", "admin_barangays"),
    AdminMenuItem("BS Conductors", "Manage teacher/conductor records", "admin_teachers"),
    AdminMenuItem("Backup & Restore", "Local device backup and server database backup", "admin_backup"),
    AdminMenuItem("Notification Settings", "Configure overdue-visit alert threshold", "admin_notifications")
)

/** Admin/Super Admin only - reference-data and system tools, split out from the Users screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanelScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "Control Panel", onBack = onBack)
        }
    ) { padding: PaddingValues ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(CONTROL_PANEL_ITEMS) { item ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onNavigate(item.route) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
