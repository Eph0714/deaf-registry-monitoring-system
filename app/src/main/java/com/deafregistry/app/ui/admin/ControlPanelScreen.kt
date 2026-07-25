package com.deafregistry.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar

data class AdminMenuItem(val title: String, val subtitle: String, val route: String, val badgeCount: Int = 0)

/**
 * Admin/Super Admin get every admin tool: reference data, system tools, and user/account
 * management. Conductors can open this screen too, but only see App Update (view-only) and
 * Theme Color (applies to their own device only) - see AppUpdateSettingsScreen/ThemeSettingsScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanelScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val isAdmin = ServiceLocator.sessionManager.isAdmin()
    var pendingApprovalCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (isAdmin) {
            runCatching { ServiceLocator.userRepository.pendingSignups() }
                .onSuccess { pendingApprovalCount = it.size }
        }
    }

    val items = buildList {
        if (isAdmin) {
            add(AdminMenuItem("Municipalities", "Add, rename, or remove municipalities", "admin_municipalities"))
            add(AdminMenuItem("Barangays", "Manage barangays per municipality", "admin_barangays"))
            add(AdminMenuItem("BS Conductors", "Manage teacher/conductor records", "admin_teachers"))
            add(AdminMenuItem("Backup & Restore", "Local device backup and server database backup", "admin_backup"))
            add(AdminMenuItem("Notification Settings", "Configure overdue-visit alert threshold", "admin_notifications"))
            add(AdminMenuItem("App Update", "Set the latest version so users get prompted to update", "admin_app_update"))
            add(AdminMenuItem("Theme Color", "Choose the color theme for your own device", "admin_theme"))
            add(AdminMenuItem("Location Sharing", "Set how long a shared location stays visible in Team Locations", "admin_location_sharing"))
            add(AdminMenuItem("User Accounts", "Manage app user accounts and roles", "admin_users"))
            add(AdminMenuItem("Pending User Approvals", "Approve or decline new self-service signups", "admin_pending_users", badgeCount = pendingApprovalCount))
            add(AdminMenuItem("User Log Report", "See who did what across the app", "admin_audit_log"))
            if (ServiceLocator.sessionManager.isSuperAdmin()) {
                add(AdminMenuItem("Reset All Data", "Super Admin only - wipe all registry data on the server", "admin_reset_data"))
            }
        } else {
            add(AdminMenuItem("App Update", "View the latest published version", "admin_app_update"))
            add(AdminMenuItem("Theme Color", "Choose the app theme for your own device", "admin_theme"))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "Control Panel", onBack = onBack)
        }
    ) { padding: PaddingValues ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(items) { item ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onNavigate(item.route) }
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.badgeCount > 0) {
                            Badge { Text(item.badgeCount.toString()) }
                        }
                    }
                }
            }
        }
    }
}
