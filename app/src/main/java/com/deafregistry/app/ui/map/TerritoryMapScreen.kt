package com.deafregistry.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.util.GpsPoint
import com.deafregistry.app.util.LocationHelper
import com.deafregistry.app.util.MapsUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.osmdroid.config.Configuration
import org.osmdroid.library.R as OsmR
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

// Same order everywhere (pins, legend, filter chips, status count row) so the visual language
// stays consistent across the whole module, per the spec.
private val STATUS_ORDER = listOf("RV", "BS", "Transferred", "Unlocated")

// No per-status color convention existed anywhere in the app before this screen (checked
// AllIndividualsScreen/ReportsScreen/MunicipalityStatisticsScreen/DashboardScreen) - defined once
// here and reused for pins, legend dots, filter chips and the info card's status badge.
fun statusColor(status: String): Color = when (status) {
    "RV" -> Color(0xFF2E7D32)
    "BS" -> Color(0xFF1565C0)
    "Transferred" -> Color(0xFFE65100)
    "Unlocated" -> Color(0xFFC62828)
    else -> Color(0xFF616161)
}

private enum class SortMode(val label: String) {
    NEAREST("Nearest"), FARTHEST("Farthest"), NAME("Name")
}

private enum class DistanceRange(val meters: Double?, val label: String) {
    ALL(null, "All"),
    M500(500.0, "500 m"),
    KM1(1000.0, "1 km"),
    KM5(5000.0, "5 km"),
    KM10(10000.0, "10 km"),
    KM25(25000.0, "25 km")
}

private const val LOW_ACCURACY_THRESHOLD_METERS = 50f
private const val LOCATION_POLL_INTERVAL_MS = 10_000L

/** A mapped Deaf Contact paired with its distance from the user's current location, if known. */
private data class MappedContact(val individual: DeafIndividualEntity, val distanceMeters: Double?)

/**
 * A pure view over the existing Deaf Individual records - reads the same reactive Room Flow
 * every other screen does (ServiceLocator.deafIndividualRepository.observeAllActive()), so any
 * add/edit/delete/status-change/sync updates this map automatically with no extra wiring.
 * Uses osmdroid (OpenStreetMap tiles) rather than Google Maps - this project has no Maps API key
 * or billing-enabled Cloud project set up anywhere, and osmdroid needs neither.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerritoryMapScreen(onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { ServiceLocator.deafIndividualRepository.observeAllActive() }
    val all by flow.collectAsState(initial = emptyList<DeafIndividualEntity>())

    var searchText by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<DeafIndividualEntity?>(null) }
    var focusTarget by remember { mutableStateOf<DeafIndividualEntity?>(null) }
    var clusterList by remember { mutableStateOf<List<DeafIndividualEntity>?>(null) }
    var panelExpanded by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var distanceRange by remember { mutableStateOf(DistanceRange.ALL) }
    var recenterSignal by remember { mutableStateOf(0) }
    // Purely a map-visibility toggle - distance calculations and Nearest sorting keep working
    // off the real fix underneath even while the on-map marker is hidden.
    var showUserLocationOnMap by remember { mutableStateOf(true) }

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    // Temporary, in-memory only - never written to the Deaf Contact database (spec §13). Used
    // solely for map positioning and distance calculations for as long as this screen is open.
    var userLocation by remember { mutableStateOf<GpsPoint?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationPermissionGranted = granted
    }

    // Polling instead of a continuous location-update callback keeps this simple and self-
    // contained to the screen's lifecycle - 10s is frequent enough to reflect real movement
    // without needlessly draining battery (spec §9).
    LaunchedEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) {
            userLocation = null
            return@LaunchedEffect
        }
        while (true) {
            LocationHelper.getCurrentLocation(context)?.let { userLocation = it }
            delay(LOCATION_POLL_INTERVAL_MS)
        }
    }

    val query = searchText.trim()
    val filtered = remember(all, query, statusFilter) {
        all.filter { individual ->
            (statusFilter == null || individual.monitoringStatus == statusFilter) &&
                (query.isEmpty() || matchesSearch(individual, query))
        }
    }
    val searchSuggestions = remember(all, query) {
        if (query.isEmpty()) emptyList() else all.filter { matchesSearch(it, query) }.take(6)
    }

    val totalCount = all.size
    val mappedCount = remember(all) { all.count { it.latitude != null && it.longitude != null } }
    val unmappedCount = totalCount - mappedCount
    val statusCounts = remember(all) { STATUS_ORDER.associateWith { s -> all.count { it.monitoringStatus == s } } }

    // Distance from the user's live GPS fix to each mapped, status/search-filtered contact -
    // null when the user's location isn't known yet. Contacts without coordinates never reach
    // this list (spec §11: unmapped contacts don't participate in distance calc or Nearest).
    val withDistance = remember(filtered, userLocation) {
        filtered.mapNotNull { individual ->
            val lat = individual.latitude ?: return@mapNotNull null
            val lon = individual.longitude ?: return@mapNotNull null
            val distance = userLocation?.let { haversineMeters(it.latitude, it.longitude, lat, lon) }
            MappedContact(individual, distance)
        }
    }

    val distanceFiltered = remember(withDistance, distanceRange) {
        val maxMeters = distanceRange.meters
        if (maxMeters == null || userLocation == null) withDistance
        else withDistance.filter { it.distanceMeters != null && it.distanceMeters <= maxMeters }
    }

    val sortedContacts = remember(distanceFiltered, sortMode) {
        when (sortMode) {
            SortMode.NEAREST -> distanceFiltered.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            SortMode.FARTHEST -> distanceFiltered.sortedByDescending { it.distanceMeters ?: -1.0 }
            SortMode.NAME -> distanceFiltered.sortedBy { it.individual.fullName }
        }
    }

    val mapIndividuals = sortedContacts.map { it.individual }
    val fitKey: Any = mapIndividuals.map { it.uuid } to (userLocation != null)

    val selectedDistance = remember(selected, userLocation) {
        val ind = selected
        val loc = userLocation
        if (ind?.latitude != null && ind.longitude != null && loc != null) {
            haversineMeters(loc.latitude, loc.longitude, ind.latitude, ind.longitude)
        } else null
    }

    fun requestOrRefreshLocation() {
        if (locationPermissionGranted) {
            scope.launch {
                LocationHelper.getCurrentLocation(context)?.let { userLocation = it }
                recenterSignal++
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Territory Map", onBack = onBack) }
    ) { padding: PaddingValues ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            OsmTerritoryMap(
                individuals = mapIndividuals,
                userLocation = userLocation,
                showLocationMarker = showUserLocationOnMap,
                focusTarget = focusTarget,
                fitKey = fitKey,
                recenterSignal = recenterSignal,
                onMarkerClick = { selected = it },
                onClusterClick = { overlapping -> clusterList = overlapping }
            )

            ControlPanel(
                expanded = panelExpanded,
                onToggleExpanded = { panelExpanded = !panelExpanded },
                totalCount = totalCount,
                mappedCount = mappedCount,
                unmappedCount = unmappedCount,
                statusCounts = statusCounts,
                searchText = searchText,
                onSearchChange = { searchText = it },
                statusFilter = statusFilter,
                onStatusFilterChange = { statusFilter = it },
                searchSuggestions = searchSuggestions,
                onPickSuggestion = { picked ->
                    searchText = ""
                    focusTarget = picked
                    selected = picked
                },
                locationPermissionGranted = locationPermissionGranted,
                onRequestLocationPermission = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                userLocation = userLocation,
                sortMode = sortMode,
                onSortModeChange = { sortMode = it },
                distanceRange = distanceRange,
                onDistanceRangeChange = { distanceRange = it },
                nearestContacts = if (sortMode == SortMode.NEAREST && userLocation != null) sortedContacts.take(5) else emptyList(),
                onSelectContact = { contact ->
                    focusTarget = contact.individual
                    selected = contact.individual
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            Legend(modifier = Modifier.align(Alignment.TopStart).padding(top = 76.dp, start = 8.dp))
            // OSM's usage policy requires this attribution to be visible on-screen.
            Text(
                "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .padding(2.dp)
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 104.dp, end = 12.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                IconButton(onClick = { showUserLocationOnMap = !showUserLocationOnMap }) {
                    Icon(
                        if (showUserLocationOnMap) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (showUserLocationOnMap) "Hide my location" else "Show my location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 44.dp, end = 12.dp)
                    .size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                IconButton(onClick = { requestOrRefreshLocation() }) {
                    Icon(Icons.Default.MyLocation, contentDescription = "My Location", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    selected?.let { individual ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheetState) {
            IndividualInfoCard(
                individual = individual,
                distanceMeters = selectedDistance,
                onViewRecord = {
                    selected = null
                    onOpenProfile(individual.uuid)
                },
                onNavigate = {
                    val lat = individual.latitude
                    val lon = individual.longitude
                    if (lat != null && lon != null) MapsUtil.navigateTo(context, lat, lon)
                }
            )
        }
    }

    clusterList?.let { overlapping ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { clusterList = null }, sheetState = sheetState) {
            ClusterListSheet(
                individuals = overlapping,
                userLocation = userLocation,
                onSelect = { individual ->
                    clusterList = null
                    selected = individual
                }
            )
        }
    }
}

/** "Search All"-style match, same fields AllIndividualsScreen already searches (see its
 * matchesSearch), plus purok/address since the spec calls that out specifically for the map. */
private fun matchesSearch(individual: DeafIndividualEntity, query: String): Boolean =
    individual.fullName.contains(query, ignoreCase = true) ||
        (individual.purok?.contains(query, ignoreCase = true) == true) ||
        individual.barangayName.contains(query, ignoreCase = true) ||
        individual.municipalityName.contains(query, ignoreCase = true) ||
        individual.monitoringStatus.contains(query, ignoreCase = true)

/** Great-circle distance between two coordinates, in meters. */
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusMeters = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m away" else "%.1f km away".format(meters / 1000.0)

/**
 * Floats over the full-screen map rather than pushing it down, so the map itself always has the
 * whole screen available - collapsed to a slim summary strip by default, expandable for the full
 * summary/search/filter/location controls.
 */
@Composable
private fun ControlPanel(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    totalCount: Int,
    mappedCount: Int,
    unmappedCount: Int,
    statusCounts: Map<String, Int>,
    searchText: String,
    onSearchChange: (String) -> Unit,
    statusFilter: String?,
    onStatusFilterChange: (String?) -> Unit,
    searchSuggestions: List<DeafIndividualEntity>,
    onPickSuggestion: (DeafIndividualEntity) -> Unit,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    userLocation: GpsPoint?,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    distanceRange: DistanceRange,
    onDistanceRangeChange: (DistanceRange) -> Unit,
    nearestContacts: List<MappedContact>,
    onSelectContact: (MappedContact) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (expanded) "Summary & Filters" else "$mappedCount mapped • $unmappedCount unmapped — tap for filters",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(bottom = 12.dp)) {
                    SummaryCardsRow(totalCount, mappedCount, unmappedCount, statusCounts)

                    OutlinedTextField(
                        value = searchText,
                        onValueChange = onSearchChange,
                        label = { Text("Search Deaf Contact...") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    SectionLabel("Monitoring Status")
                    StatusFilterChips(selected = statusFilter, onSelect = onStatusFilterChange)

                    if (!locationPermissionGranted) {
                        LocationPermissionBanner(onEnable = onRequestLocationPermission)
                    } else {
                        val accuracy = userLocation?.accuracyMeters
                        if (accuracy != null && accuracy > LOW_ACCURACY_THRESHOLD_METERS) {
                            LowAccuracyBanner()
                        }
                        SectionLabel("Distance")
                        DistanceRangeChips(selected = distanceRange, onSelect = onDistanceRangeChange)
                        SectionLabel("Sort By")
                        SortModeChips(selected = sortMode, onSelect = onSortModeChange)
                    }

                    if (searchSuggestions.isNotEmpty()) {
                        SearchSuggestionsList(searchSuggestions, onPickSuggestion)
                    }

                    if (nearestContacts.isNotEmpty()) {
                        NearestContactsList(nearestContacts, onSelectContact)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun LocationPermissionBanner(onEnable: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Current location is unavailable. Enable location permission to find the nearest Deaf contacts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onEnable) { Text("Enable Location") }
    }
}

@Composable
private fun LowAccuracyBanner() {
    Text(
        "Location accuracy is low. Move outdoors or enable high-accuracy location.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun SortModeChips(selected: SortMode, onSelect: (SortMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SortMode.entries.forEach { mode ->
            FilterChip(selected = selected == mode, onClick = { onSelect(mode) }, label = { Text(mode.label) })
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun DistanceRangeChips(selected: DistanceRange, onSelect: (DistanceRange) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DistanceRange.entries.forEach { range ->
            FilterChip(selected = selected == range, onClick = { onSelect(range) }, label = { Text(range.label) })
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun NearestContactsList(contacts: List<MappedContact>, onSelect: (MappedContact) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Nearest Contacts", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        contacts.forEach { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(contact) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(contact.individual.monitoringStatus)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(contact.individual.fullName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        contact.individual.monitoringStatus + (contact.distanceMeters?.let { " • ${formatDistance(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Shown when the user taps a cluster pin (multiple overlapping records at the current zoom
 * level) - lets them pick which one to open, instead of just auto-zooming in. */
@Composable
private fun ClusterListSheet(
    individuals: List<DeafIndividualEntity>,
    userLocation: GpsPoint?,
    onSelect: (DeafIndividualEntity) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(
            "${individuals.size} Records at This Location",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        individuals.forEach { individual ->
            val distance = userLocation?.let { loc ->
                val lat = individual.latitude
                val lon = individual.longitude
                if (lat != null && lon != null) haversineMeters(loc.latitude, loc.longitude, lat, lon) else null
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(individual) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(individual.monitoringStatus)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(individual.fullName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${individual.monitoringStatus} • ${individual.barangayName}, ${individual.municipalityName}" +
                            (distance?.let { " • ${formatDistance(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCardsRow(
    total: Int,
    mapped: Int,
    unmapped: Int,
    statusCounts: Map<String, Int>
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryChip("Total", total.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            SummaryChip("Mapped", mapped.toString(), Color(0xFF2E7D32), Modifier.weight(1f))
            SummaryChip("Unmapped", unmapped.toString(), Color(0xFF616161), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            STATUS_ORDER.forEach { status ->
                SummaryChip(status, (statusCounts[status] ?: 0).toString(), statusColor(status))
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(90.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusFilterChips(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        STATUS_ORDER.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(if (selected == status) null else status) },
                label = { Text(status) }
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SearchSuggestionsList(suggestions: List<DeafIndividualEntity>, onPick: (DeafIndividualEntity) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        LazyColumn(Modifier.heightIn(max = 220.dp)) {
            items(suggestions, key = { it.uuid }) { individual ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(individual) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape).background(statusColor(individual.monitoringStatus))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(individual.fullName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${individual.monitoringStatus} • ${individual.barangayName}, ${individual.municipalityName}" +
                                if (individual.latitude == null) " • No location" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            STATUS_ORDER.forEach { status ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(status)))
                    Spacer(Modifier.width(6.dp))
                    Text(status, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun IndividualInfoCard(
    individual: DeafIndividualEntity,
    distanceMeters: Double?,
    onViewRecord: () -> Unit,
    onNavigate: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(individual.fullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor(individual.monitoringStatus)))
            Spacer(Modifier.width(8.dp))
            Text(
                "Monitoring Status: ${individual.monitoringStatus}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        val address = listOfNotNull(individual.purok, individual.barangayName, individual.municipalityName)
            .joinToString(", ")
        Text("Address: $address", style = MaterialTheme.typography.bodyMedium)
        if (distanceMeters != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Distance: ${formatDistance(distanceMeters)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (individual.latitude != null && individual.longitude != null) {
            Spacer(Modifier.height(4.dp))
            val context = LocalContext.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        MapsUtil.openInMaps(context, individual.latitude, individual.longitude, individual.fullName)
                    }
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Coordinates: %.5f, %.5f".format(individual.latitude, individual.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            }
        } else {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text("No Location / Unmapped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onViewRecord, modifier = Modifier.weight(1f)) {
                Text("View Record")
            }
            if (individual.latitude != null && individual.longitude != null) {
                OutlinedButton(onClick = onNavigate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Navigate")
                }
            }
        }
    }
}

/** A group of one or more individuals close enough together at the current zoom level to render
 * as a single pin - osmdroid-android 6.1.20 dropped the RadiusMarkerClusterer it shipped in
 * older versions, so clustering is done here with a simple lat/lon grid instead. */
private data class MapCluster(val point: GeoPoint, val items: List<DeafIndividualEntity>)

private fun buildClusters(individuals: List<DeafIndividualEntity>, zoomLevel: Double): List<MapCluster> {
    if (individuals.isEmpty()) return emptyList()
    // Grid cell shrinks as zoom increases, so pins only merge when genuinely close on-screen.
    val cellDegrees = 30.0 / Math.pow(2.0, zoomLevel)
    val buckets = LinkedHashMap<Pair<Long, Long>, MutableList<DeafIndividualEntity>>()
    individuals.forEach { individual ->
        val lat = individual.latitude ?: return@forEach
        val lon = individual.longitude ?: return@forEach
        val key = Math.floor(lat / cellDegrees).toLong() to Math.floor(lon / cellDegrees).toLong()
        buckets.getOrPut(key) { mutableListOf() }.add(individual)
    }
    return buckets.values.map { group ->
        val avgLat = group.map { it.latitude!! }.average()
        val avgLon = group.map { it.longitude!! }.average()
        MapCluster(GeoPoint(avgLat, avgLon), group)
    }
}

@Composable
private fun OsmTerritoryMap(
    individuals: List<DeafIndividualEntity>,
    userLocation: GpsPoint?,
    showLocationMarker: Boolean,
    focusTarget: DeafIndividualEntity?,
    fitKey: Any,
    recenterSignal: Int,
    onMarkerClick: (DeafIndividualEntity) -> Unit,
    onClusterClick: (List<DeafIndividualEntity>) -> Unit
) {
    val individualsState = rememberUpdatedState(individuals)
    val onMarkerClickState = rememberUpdatedState(onMarkerClick)
    val onClusterClickState = rememberUpdatedState(onClusterClick)
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val contactsFolderRef = remember { mutableStateOf<FolderOverlay?>(null) }
    val userMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val accuracyCircleRef = remember { mutableStateOf<Polygon?>(null) }
    var lastFocusUuid by remember { mutableStateOf<String?>(null) }
    var lastRecenterHandled by remember { mutableStateOf(0) }
    var lastFitKey by remember { mutableStateOf<Any?>(null) }

    // Blinking "My Location" pulse, driven by motion rather than a static dot - the marker's
    // alpha is mutated directly (see the LaunchedEffect below) instead of going through
    // AndroidView's update{} block, so the blink runs smoothly without re-clustering contacts
    // on every animation frame.
    val infiniteTransition = rememberInfiniteTransition(label = "userLocationBlink")
    val blinkAlpha = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "userLocationBlinkAlpha"
    )
    LaunchedEffect(Unit) {
        snapshotFlow { blinkAlpha.value }.collect { alpha ->
            userMarkerRef.value?.let {
                it.alpha = alpha
                mapViewRef.value?.invalidate()
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            Configuration.getInstance().apply {
                userAgentValue = ctx.packageName
                val basePath = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                osmdroidBasePath = basePath
                osmdroidTileCache = File(basePath, "osmdroid_tiles")
            }
            val mapView = MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 3.0
                maxZoomLevel = 19.0
                controller.setZoom(6.0)
                controller.setCenter(GeoPoint(12.8797, 121.7740)) // Philippines, before any data loads
            }
            val contactsFolder = FolderOverlay()
            mapView.overlays.add(contactsFolder)
            contactsFolderRef.value = contactsFolder
            mapView.addMapListener(object : org.osmdroid.events.MapListener {
                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false
                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                    renderContactClusters(mapView, contactsFolder, individualsState.value, onMarkerClickState.value, onClusterClickState.value)
                    mapView.invalidate()
                    return false
                }
            })
            mapViewRef.value = mapView
            mapView
        },
        update = { mapView ->
            contactsFolderRef.value?.let { folder ->
                renderContactClusters(mapView, folder, individuals, onMarkerClick, onClusterClick)
            }
            updateUserLocationOverlay(
                mapView.context,
                mapView,
                if (showLocationMarker) userLocation else null,
                userMarkerRef,
                accuracyCircleRef
            )

            val focusUuid = focusTarget?.uuid
            when {
                focusTarget?.latitude != null && focusTarget.longitude != null && focusUuid != lastFocusUuid -> {
                    lastFocusUuid = focusUuid
                    mapView.controller.animateTo(GeoPoint(focusTarget.latitude, focusTarget.longitude))
                    mapView.controller.setZoom(16.0)
                }
                recenterSignal != lastRecenterHandled -> {
                    lastRecenterHandled = recenterSignal
                    userLocation?.let {
                        mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                        mapView.controller.setZoom(17.0)
                    }
                }
                fitKey != lastFitKey -> {
                    lastFitKey = fitKey
                    val points = individuals.mapNotNull { ind ->
                        val lat = ind.latitude ?: return@mapNotNull null
                        val lon = ind.longitude ?: return@mapNotNull null
                        GeoPoint(lat, lon)
                    }.toMutableList()
                    userLocation?.let { points.add(GeoPoint(it.latitude, it.longitude)) }
                    when {
                        points.size == 1 -> {
                            mapView.controller.setCenter(points.first())
                            mapView.controller.setZoom(15.0)
                        }
                        points.size > 1 -> {
                            val box = BoundingBox.fromGeoPoints(points)
                            mapView.post { mapView.zoomToBoundingBox(box, true, 120) }
                        }
                    }
                }
            }
            mapView.invalidate()
        }
    )

    DisposableEffect(Unit) {
        onDispose { mapViewRef.value?.onDetach() }
    }
}

/** Rebuilds only the Deaf Contact pins/clusters inside their own FolderOverlay - kept separate
 * from the "My Location" marker so that marker can be updated in place (position, blink alpha)
 * without forcing a full re-cluster on every animation frame or location poll. */
private fun renderContactClusters(
    mapView: MapView,
    folder: FolderOverlay,
    individuals: List<DeafIndividualEntity>,
    onMarkerClick: (DeafIndividualEntity) -> Unit,
    onClusterClick: (List<DeafIndividualEntity>) -> Unit
) {
    val context = mapView.context
    folder.items.clear()
    val clusters = buildClusters(individuals, mapView.zoomLevelDouble)
    clusters.forEach { cluster ->
        val marker = Marker(mapView).apply {
            position = cluster.point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        if (cluster.items.size == 1) {
            val individual = cluster.items.first()
            marker.title = individual.fullName
            marker.snippet = individual.monitoringStatus
            marker.icon = tintedMarkerIcon(context, statusColor(individual.monitoringStatus))
            marker.setOnMarkerClickListener { _, _ ->
                onMarkerClick(individual)
                true
            }
        } else {
            marker.icon = clusterIcon(context, cluster.items.size)
            // Overlapping records: let the user pick which one to open from a list, rather than
            // just auto-zooming in on the cluster.
            marker.setOnMarkerClickListener { _, _ ->
                onClusterClick(cluster.items)
                true
            }
        }
        folder.add(marker)
    }
}

/** Creates the "My Location" marker/accuracy-circle once and mutates them in place afterwards
 * (position, radius) instead of recreating them - the blink animation depends on this same
 * Marker instance staying alive across recompositions. */
private fun updateUserLocationOverlay(
    context: android.content.Context,
    mapView: MapView,
    userLocation: GpsPoint?,
    userMarkerRef: androidx.compose.runtime.MutableState<Marker?>,
    accuracyCircleRef: androidx.compose.runtime.MutableState<Polygon?>
) {
    if (userLocation == null) {
        userMarkerRef.value?.let { mapView.overlays.remove(it) }
        accuracyCircleRef.value?.let { mapView.overlays.remove(it) }
        userMarkerRef.value = null
        accuracyCircleRef.value = null
        return
    }

    val userPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

    val accuracy = userLocation.accuracyMeters
    if (accuracy != null && accuracy > 0f) {
        val existingCircle = accuracyCircleRef.value
        if (existingCircle == null) {
            val circle = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(userPoint, accuracy.toDouble())
                fillColor = android.graphics.Color.argb(40, 33, 150, 243)
                strokeColor = android.graphics.Color.argb(140, 33, 150, 243)
                strokeWidth = 2f
            }
            mapView.overlays.add(circle)
            accuracyCircleRef.value = circle
        } else {
            existingCircle.points = Polygon.pointsAsCircle(userPoint, accuracy.toDouble())
        }
    } else {
        accuracyCircleRef.value?.let { mapView.overlays.remove(it) }
        accuracyCircleRef.value = null
    }

    val existingMarker = userMarkerRef.value
    if (existingMarker == null) {
        val marker = Marker(mapView).apply {
            position = userPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = userLocationIcon(context)
            title = "My Location"
            snippet = "You are here"
        }
        mapView.overlays.add(marker)
        userMarkerRef.value = marker
    } else {
        existingMarker.position = userPoint
    }
}

/** osmdroid's bundled default marker drawable, tinted per monitoring status - avoids needing to
 * ship/draw a custom pin asset for each of the 4 statuses. */
private fun tintedMarkerIcon(context: android.content.Context, color: Color): Drawable? {
    val original = androidx.core.content.ContextCompat.getDrawable(context, OsmR.drawable.marker_default) ?: return null
    val wrapped = DrawableCompat.wrap(original.mutate())
    DrawableCompat.setTint(wrapped, color.toArgb())
    return wrapped
}

/** A plain filled circle with a count label, drawn on the fly - the visual for a cluster pin. */
private fun clusterIcon(context: android.content.Context, count: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val diameterPx = (40 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(diameterPx, diameterPx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val circlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#455A64")
        style = android.graphics.Paint.Style.FILL
    }
    val radius = diameterPx / 2f
    canvas.drawCircle(radius, radius, radius, circlePaint)
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 14f * density
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(count.toString(), radius, textY, textPaint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

/** A white-ringed blue dot, drawn on the fly - the "My Location" marker, visually distinct from
 * the pin-shaped, status-colored Deaf Contact markers per spec §1/§7. */
private fun userLocationIcon(context: android.content.Context): Drawable {
    val density = context.resources.displayMetrics.density
    val diameterPx = (26 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(diameterPx, diameterPx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val center = diameterPx / 2f
    val outerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center, outerPaint)
    val innerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#2962FF")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center - 3f * density, innerPaint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
