package com.deafregistry.app.ui.admin

import androidx.compose.foundation.clickable
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

data class AdminMenuItem(val title: String, val subtitle: String, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val items = listOf(
        AdminMenuItem("Municipalities", "Add, rename, or remove municipalities", "admin_municipalities"),
        AdminMenuItem("Barangays", "Manage barangays per municipality", "admin_barangays"),
        AdminMenuItem("BS Conductors", "Manage teacher/conductor records", "admin_teachers"),
        AdminMenuItem("User Accounts", "Manage app user accounts and roles", "admin_users"),
        AdminMenuItem("Backup & Restore", "Local device backup and server database backup", "admin_backup"),
        AdminMenuItem("Notification Settings", "Configure overdue-visit alert threshold", "admin_notifications"),
        AdminMenuItem("Device Approvals", "Approve or reject new device logins", "admin_devices"),
        AdminMenuItem("Pending User Approvals", "Approve or reject new self-service signups", "admin_pending_users"),
        AdminMenuItem("User Log Report", "See who did what across the app", "admin_audit_log")
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "Administration", onBack = onBack)
        }
    ) { padding: PaddingValues ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(items) { item ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onNavigate(item.route) }
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
