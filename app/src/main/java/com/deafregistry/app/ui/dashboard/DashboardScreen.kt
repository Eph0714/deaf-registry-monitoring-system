package com.deafregistry.app.ui.dashboard

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.deafregistry.app.BuildConfig
import com.deafregistry.app.data.remote.dto.NotVisitedDto
import com.deafregistry.app.data.remote.dto.RecentVisitDto
import com.deafregistry.app.data.remote.dto.UserLocationDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.GenericViewModelFactory
import com.deafregistry.app.util.GpsPoint
import com.deafregistry.app.util.LocationHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenDeafRecords: () -> Unit,
    onOpenAllIndividuals: (title: String, sort: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenControlPanel: () -> Unit,
    onOpenAppUpdate: () -> Unit,
    onOpenUserAccounts: () -> Unit,
    onOpenProfile: (String) -> Unit,
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
                ServiceLocator.settingsRepository,
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
    var showAboutDialog by remember { mutableStateOf(false) }
    var myCoordinates by remember { mutableStateOf<GpsPoint?>(null) }
    var isCapturingLocation by remember { mutableStateOf(false) }
    var isSharingLocation by remember { mutableStateOf(false) }
    var teamLocations by remember { mutableStateOf<List<UserLocationDto>>(emptyList()) }

    fun loadTeamLocations() {
        scope.launch {
            runCatching { ServiceLocator.authRepository.getUserLocations() }
                .onSuccess { teamLocations = it }
        }
    }
    LaunchedEffect(Unit) { loadTeamLocations() }
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

    fun captureMyLocation() {
        scope.launch {
            isCapturingLocation = true
            myCoordinates = LocationHelper.getCurrentLocation(context)
            isCapturingLocation = false
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) captureMyLocation()
    }
    fun showMyCoordinates() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            captureMyLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    fun shareMyLocation() {
        val coords = myCoordinates ?: return
        scope.launch {
            isSharingLocation = true
            runCatching { ServiceLocator.authRepository.shareLocation(coords.latitude, coords.longitude) }
                .onSuccess { loadTeamLocations() }
                .onFailure { Toast.makeText(context, "Failed to share location: ${it.message}", Toast.LENGTH_LONG).show() }
            isSharingLocation = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                userName = state.userName,
                userEmail = userEmail,
                photoUrl = photoUrl,
                isOnline = state.isOnline,
                pendingSyncCount = state.pendingSyncCount,
                onAvatarClick = { showPhotoSourceDialog = true },
                onNavigateSearch = { closeDrawer(onOpenSearch) },
                onNavigateControlPanel = { closeDrawer(onOpenControlPanel) },
                onNavigateAbout = { closeDrawer { showAboutDialog = true } },
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
                    // Conductors can open Control Panel too now, just with a reduced menu (App
                    // Update view-only, Theme Color for their own device) - see ControlPanelScreen.
                    IconButton(onClick = onOpenControlPanel) {
                        if (state.pendingApprovalCount > 0) {
                            BadgedBox(badge = {
                                Badge { Text(state.pendingApprovalCount.toString()) }
                            }) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = "Control Panel (${state.pendingApprovalCount} pending approvals)")
                            }
                        } else {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Control Panel")
                        }
                    }
                    UserAvatar(state.userName, photoUrl) { showPhotoSourceDialog = true }
                }
            )
        }
    ) { padding: PaddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = { viewModel.sync(); loadTeamLocations() },
            modifier = Modifier.padding(padding)
        ) {
            if (state.municipalities.isEmpty() && !state.isSyncing) {
                EmptyState("No municipalities yet. Pull down to sync with the server.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
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
                            onSync = { viewModel.sync(); loadTeamLocations() }
                        )
                    }

                    item {
                        MyLocationCard(
                            coordinates = myCoordinates,
                            isCapturing = isCapturingLocation,
                            isSharing = isSharingLocation,
                            teamLocations = teamLocations,
                            onShowCoordinates = { showMyCoordinates() },
                            onShare = { shareMyLocation() },
                            onOpenInMaps = { lat, lng, label ->
                                val mapsUrl = "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)))
                                }.onFailure {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"))
                                        )
                                    }.onFailure { fallbackError ->
                                        if (fallbackError is ActivityNotFoundException) {
                                            Toast.makeText(context, "No app found to open this location", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        )
                    }

                    item {
                        DashboardQuickActionsRow(
                            onOpenSearch = onOpenSearch,
                            onOpenReports = onOpenReports,
                            onOpenMunicipality = onOpenDeafRecords,
                            onOpenAppUpdate = onOpenAppUpdate,
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
                                    subtitle = "Account management",
                                    onClick = onOpenUserAccounts,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }


                    item {
                        FollowUpNeededCard(
                            items = state.pendingFollowUps.take(3),
                            overdueDaysThreshold = state.overdueDaysThreshold,
                            onOpenProfile = onOpenProfile
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
                        LatestVisitsCard(
                            items = state.recentVisits.take(5),
                            onOpenProfile = onOpenProfile
                        )
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

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About") },
            text = { Text("Created to praise Jehovah and support Matthew 24:14") },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("OK") }
            }
        )
    }

    val visibleUpdateInfo = if (state.showUpdateDialog) state.updateInfo else null
    visibleUpdateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdatePrompt() },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("Version ${info.versionName ?: info.versionCode} is available. You're running ${BuildConfig.VERSION_NAME}.")
                    if (!info.releaseNotes.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(info.releaseNotes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissUpdatePrompt()
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
                    }.onFailure {
                        if (it is ActivityNotFoundException) {
                            Toast.makeText(context, "No app found to open this link", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Update Now") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdatePrompt() }) { Text("Later") }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
private fun MyLocationCard(
    coordinates: GpsPoint?,
    isCapturing: Boolean,
    isSharing: Boolean,
    teamLocations: List<UserLocationDto>,
    onShowCoordinates: () -> Unit,
    onShare: () -> Unit,
    onOpenInMaps: (Double, Double, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("My Location", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            coordinates?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Lat: ${it.latitude}, Lng: ${it.longitude}",
                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenInMaps(it.latitude, it.longitude, "My Location") }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShowCoordinates, enabled = !isCapturing) {
                    if (isCapturing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Show my coordinates")
                    }
                }
                Button(onClick = onShare, enabled = coordinates != null && !isSharing) {
                    if (isSharing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
            if (teamLocations.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Team Locations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                teamLocations.forEach { loc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenInMaps(loc.sharedLatitude, loc.sharedLongitude, loc.name) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                loc.name,
                                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${loc.role} — shared ${loc.sharedLocationAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardQuickActionsRow(
    onOpenSearch: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenMunicipality: () -> Unit,
    onOpenAppUpdate: () -> Unit,
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
                color = MaterialTheme.colorScheme.onSurface,
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
                    DashboardQuickActionTile("App Update", Icons.Default.SystemUpdate, onOpenAppUpdate, Modifier.weight(1f))
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                color = MaterialTheme.colorScheme.onSurface,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rows are clickable through to that individual's profile, same as FollowUpNeededCard. */
@Composable
private fun LatestVisitsCard(
    items: List<RecentVisitDto>,
    onOpenProfile: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = "Latest Visits", tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Latest Visits",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                "Most recently recorded visits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            items.forEach { visit ->
                Text(
                    "${visit.fullName} — ${visit.conductorName ?: "—"} • ${visit.visitDateTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProfile(visit.uuid) }
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Every row here already satisfies the overdue-days threshold (the server-side notVisited query
 * already filters out anyone under it), so there's nothing left to filter client-side - this just
 * displays the actual elapsed days per individual (always >= overdueDaysThreshold) and makes each
 * row clickable through to that individual's profile.
 */
@Composable
private fun FollowUpNeededCard(
    items: List<NotVisitedDto>,
    overdueDaysThreshold: Int,
    onOpenProfile: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, contentDescription = "Follow-Up Needed", tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Follow-Up Needed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                "Over $overdueDaysThreshold day${if (overdueDaysThreshold == 1) "" else "s"} without a visit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            items.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProfile(entry.uuid) }
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.fullName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${entry.municipality}/${entry.barangay}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        daysSinceLabel(entry.lastVisit),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun daysSinceLabel(lastVisit: String?): String {
    if (lastVisit.isNullOrBlank()) return "Never visited"
    val days = runCatching {
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(lastVisit.take(10)), java.time.LocalDate.now())
    }.getOrNull() ?: return "—"
    return "$days day${if (days == 1L) "" else "s"}"
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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            rows.forEach { (label, value) ->
                val percent = if (total > 0) (value.toFloat() / total.toFloat()) * 100f else 0f
                Column(Modifier.padding(top = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
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
    isOnline: Boolean,
    pendingSyncCount: Int,
    onAvatarClick: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateControlPanel: () -> Unit,
    onNavigateAbout: () -> Unit,
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
            // Conductors can open Control Panel too now, with a reduced menu (see ControlPanelScreen).
            NavigationDrawerItem(
                label = { Text("Control Panel") },
                selected = false,
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = onNavigateControlPanel,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text("About") },
                selected = false,
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                onClick = onNavigateAbout,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

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
