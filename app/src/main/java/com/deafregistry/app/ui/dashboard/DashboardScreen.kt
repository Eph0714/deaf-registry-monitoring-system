package com.deafregistry.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.deafregistry.app.BuildConfig
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.GenericViewModelFactory
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenDeafRecords: () -> Unit,
    onOpenAllIndividuals: (title: String, sort: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(
        factory = GenericViewModelFactory {
            DashboardViewModel(
                ServiceLocator.referenceDataRepository,
                ServiceLocator.syncManager,
                ServiceLocator.sessionManager,
                ServiceLocator.userRepository,
                ServiceLocator.reportRepository,
                ServiceLocator.networkMonitor
            )
        }
    )
    val state by viewModel.uiState.collectAsState()
    // The ViewModel survives navigating to Admin screens and back (same nav back-stack entry),
    // so re-fetch the user/pending-approval counts on every visit - otherwise adding/removing a
    // user or approving/declining a signup while off this screen leaves it showing a stale number.
    LaunchedEffect(Unit) { viewModel.refreshUserCounts() }
    val totalDeafRecords = state.municipalities.sumOf { it.deafCount }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val session by ServiceLocator.sessionManager.session.collectAsState()
    val userEmail = session?.email ?: ""
    val photoUrl = session?.photoUrl?.let {
        if (it.startsWith("/uploads")) BuildConfig.API_BASE_URL.removeSuffix("/api/") + it else it
    }
    val context = LocalContext.current

    fun closeDrawer(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingPhotoFile?.let { file ->
            scope.launch { runCatching { ServiceLocator.authRepository.uploadProfilePhoto(file.absolutePath) } }
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val file = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            scope.launch { runCatching { ServiceLocator.authRepository.uploadProfilePhoto(file.absolutePath) } }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "profile_capture_${System.currentTimeMillis()}.jpg")
            pendingPhotoFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            takePictureLauncher.launch(uri)
        }
    }
    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val file = File(context.cacheDir, "profile_capture_${System.currentTimeMillis()}.jpg")
            pendingPhotoFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                userName = state.userName,
                userEmail = userEmail,
                photoUrl = photoUrl,
                isAdmin = state.isAdmin,
                isOnline = state.isOnline,
                pendingSyncCount = state.pendingSyncCount,
                onAvatarClick = { showPhotoSourceDialog = true },
                onNavigateSearch = { closeDrawer(onOpenSearch) },
                onNavigateDeafRecords = { closeDrawer(onOpenDeafRecords) },
                onNavigateReports = { closeDrawer(onOpenReports) },
                onNavigateAdmin = { closeDrawer(onOpenAdmin) },
                onSync = { closeDrawer(viewModel::sync) },
                onLogout = {
                    closeDrawer {
                        ServiceLocator.authRepository.logout()
                        onLogout()
                    }
                }
            )
        }
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Dashboard",
                onMenuClick = { scope.launch { drawerState.open() } },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
                    if (state.isAdmin) {
                        IconButton(onClick = onOpenAdmin) {
                            if (state.pendingApprovalCount > 0) {
                                BadgedBox(badge = {
                                    Badge { Text(state.pendingApprovalCount.toString()) }
                                }) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin (${state.pendingApprovalCount} pending approvals)")
                                }
                            } else {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin")
                            }
                        }
                    }
                    UserAvatar(state.userName, photoUrl) { showPhotoSourceDialog = true }
                }
            )
        }
    ) { padding: PaddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = { viewModel.sync() },
            modifier = Modifier.padding(padding)
        ) {
            if (state.municipalities.isEmpty() && !state.isSyncing) {
                EmptyState("No municipalities yet. Pull down to sync with the server.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                                        )
                                    )
                                )
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text(
                                    "Registry Dashboard",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Welcome, ${state.userName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                                Text(
                                    "Track deaf records, municipalities, and user administration in one place.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }

                    item {
                        SyncStatusRow(
                            isOnline = state.isOnline,
                            isSyncing = state.isSyncing,
                            syncError = state.syncError,
                            pendingSyncCount = state.pendingSyncCount,
                            onSync = { viewModel.sync() }
                        )
                    }

                    item {
                        DashboardQuickActionsRow(
                            onOpenSearch = onOpenSearch,
                            onOpenReports = onOpenReports,
                            onOpenAdmin = onOpenAdmin,
                            onOpenMunicipality = onOpenDeafRecords,
                            isAdmin = state.isAdmin
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DashboardMetricCard(
                                title = "Deaf Records",
                                value = totalDeafRecords.toString(),
                                subtitle = "Registry overview",
                                onClick = { onOpenAllIndividuals("All Deaf Records", "all") },
                                modifier = Modifier.weight(1f)
                            )
                            DashboardMetricCard(
                                title = "Municipalities",
                                value = state.municipalities.size.toString(),
                                subtitle = "Local directory",
                                onClick = onOpenDeafRecords,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.isAdmin) {
                                DashboardMetricCard(
                                    title = "Users",
                                    value = state.totalUsers.toString(),
                                    subtitle = "Access control",
                                    onClick = onOpenAdmin,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (state.isAdmin) {
                        item {
                            DashboardActionCard(
                                title = "User Management",
                                subtitle = "Manage access, accounts, and active users.",
                                onClick = onOpenAdmin
                            )
                        }
                    }

                    if (state.isAdmin) {
                        item {
                            DashboardActivityCard(
                                title = "Follow-Up Needed",
                                icon = Icons.Default.WarningAmber,
                                items = state.pendingFollowUps.take(3).map { it.fullName to (it.municipality + "/" + it.barangay) },
                                subtitle = "Over 30 days without a visit"
                            )
                        }

                        item {
                            DashboardInsightCard(
                                title = "Monitoring Status",
                                icon = Icons.Default.TrendingUp,
                                rows = state.byStatus.take(4).map { it.monitoringStatus to it.total },
                                total = state.municipalities.sumOf { it.deafCount }
                            )
                        }

                        item {
                            DashboardInsightCard(
                                title = "Skill Levels",
                                icon = Icons.Default.TrendingUp,
                                rows = state.bySkill.take(4).map { it.skillLevel to it.total },
                                total = state.municipalities.sumOf { it.deafCount }
                            )
                        }

                        item {
                            DashboardActivityCard(
                                title = "Latest Visits",
                                icon = Icons.Default.History,
                                items = state.recentVisits.take(5).map {
                                    it.fullName to "${it.conductorName ?: "—"} • ${it.visitDateTime}"
                                },
                                subtitle = "Most recently recorded visits"
                            )
                        }

                    }
                }
            }
        }
    }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text("Update Profile Photo") },
            text = { Text("Choose a source for your new profile photo.") },
            confirmButton = {
                TextButton(onClick = { showPhotoSourceDialog = false; launchCamera() }) { Text("Camera") }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false; pickImageLauncher.launch("image/*") }) { Text("Gallery") }
            }
        )
    }
}

@Composable
private fun SyncStatusRow(
    isOnline: Boolean,
    isSyncing: Boolean,
    syncError: String?,
    pendingSyncCount: Int,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isOnline) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        when {
                            isSyncing -> "Syncing…"
                            !isOnline -> "Offline — changes are saved on this device"
                            else -> "Online"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!isOnline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isSyncing && pendingSyncCount > 0) {
                    Text(
                        "$pendingSyncCount change${if (pendingSyncCount == 1) "" else "s"} waiting to sync — tap Sync to send to the server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                syncError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = onSync, enabled = !isSyncing) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sync")
                }
            }
        }
    }
}

@Composable
private fun DashboardQuickActionsRow(
    onOpenSearch: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenMunicipality: () -> Unit,
    isAdmin: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Quick Access",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardQuickActionTile("Search", Icons.Default.Search, onOpenSearch, Modifier.weight(1f))
                // Conductors can view reports now too, just not export/print them (see ReportsScreen).
                DashboardQuickActionTile("Reports", Icons.Default.BarChart, onOpenReports, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardQuickActionTile("Municipalities", Icons.Default.LocationCity, onOpenMunicipality, Modifier.weight(1f))
                if (isAdmin) {
                    DashboardQuickActionTile("Users", Icons.Default.AdminPanelSettings, onOpenAdmin, Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
private fun DashboardQuickActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.Black, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = title, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun DashboardActivityCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            items.forEach { (primary, secondary) ->
                Text(
                    "$primary — $secondary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DashboardInsightCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    rows: List<Pair<String, Int>>,
    total: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            rows.forEach { (label, value) ->
                val percent = if (total > 0) (value.toFloat() / total.toFloat()) * 100f else 0f
                Column(Modifier.padding(top = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Black)
                        Text("${value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percent / 100f)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50.dp))
                                .size(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserAvatar(userName: String, photoUrl: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                userName.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AppDrawerContent(
    userName: String,
    userEmail: String,
    photoUrl: String?,
    isAdmin: Boolean,
    isOnline: Boolean,
    pendingSyncCount: Int,
    onAvatarClick: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateDeafRecords: () -> Unit,
    onNavigateReports: () -> Unit,
    onNavigateAdmin: () -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit
) {
    ModalDrawerSheet {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
                        .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl != null) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            userName.trim().firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    userName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    userEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }

            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                label = { Text("Dashboard") },
                selected = true,
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                onClick = {},
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text("Search") },
                selected = false,
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                onClick = onNavigateSearch,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text("Deaf Records") },
                selected = false,
                icon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                onClick = onNavigateDeafRecords,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text("Reports") },
                selected = false,
                icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                onClick = onNavigateReports,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            if (isAdmin) {
                NavigationDrawerItem(
                    label = { Text("Admin") },
                    selected = false,
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                    onClick = onNavigateAdmin,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            NavigationDrawerItem(
                label = { Text(if (pendingSyncCount > 0) "Sync [$pendingSyncCount]" else "Sync") },
                selected = false,
                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                badge = if (!isOnline) {
                    { Text("Offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                } else null,
                onClick = onSync,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.weight(1f))

            HorizontalDivider()
            NavigationDrawerItem(
                label = { Text("Log Out") },
                selected = false,
                icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                onClick = onLogout,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
