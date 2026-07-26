package com.deafregistry.app.ui.location

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.util.GpsPoint
import com.deafregistry.app.util.LocationHelper
import kotlinx.coroutines.launch

/**
 * Dedicated Location Sharing screen, reached from the Dashboard's Quick Access tile (moved off
 * the main form so it doesn't grow too tall - see DashboardScreen.kt's DashboardQuickActionsRow,
 * whose tile shows a count badge of how many teammates currently have a location shared).
 * Self-contained: captures/shares this device's GPS and fetches the team locations list directly
 * via ServiceLocator, same pattern as CalendarScreen/AllIndividualsScreen.
 */
@Composable
fun LocationSharingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var myCoordinates by remember { mutableStateOf<GpsPoint?>(null) }
    var isCapturingLocation by remember { mutableStateOf(false) }
    var isSharingLocation by remember { mutableStateOf(false) }
    var teamLocations by remember { mutableStateOf<List<com.deafregistry.app.data.remote.dto.UserLocationDto>>(emptyList()) }

    fun loadTeamLocations() {
        scope.launch {
            runCatching { ServiceLocator.authRepository.getUserLocations() }
                .onSuccess { teamLocations = it }
        }
    }
    LaunchedEffect(Unit) { loadTeamLocations() }

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
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
                .onFailure { Toast.makeText(context, "Failed to share location: ${com.deafregistry.app.util.friendlyMessage(it)}", Toast.LENGTH_LONG).show() }
            isSharingLocation = false
        }
    }
    fun openInMaps(lat: Double, lng: Double, label: String) {
        val mapsUrl = "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)))
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")))
            }.onFailure { fallbackError ->
                if (fallbackError is ActivityNotFoundException) {
                    Toast.makeText(context, "No app found to open this location", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Location Sharing", onBack = onBack) }
    ) { padding: PaddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("My Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            myCoordinates?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Lat: ${it.latitude}, Lng: ${it.longitude}",
                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { openInMaps(it.latitude, it.longitude, "My Location") }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showMyCoordinates() }, enabled = !isCapturingLocation) {
                    if (isCapturingLocation) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Show my coordinates")
                    }
                }
                Button(onClick = { shareMyLocation() }, enabled = myCoordinates != null && !isSharingLocation) {
                    if (isSharingLocation) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "Team Locations (${teamLocations.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            if (teamLocations.isEmpty()) {
                Text(
                    "No one has shared their location yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                teamLocations.forEach { loc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openInMaps(loc.sharedLatitude, loc.sharedLongitude, loc.name) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
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
