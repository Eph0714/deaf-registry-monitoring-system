package com.deafregistry.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Upper-right corner profile trigger for the Dashboard's top bar - tapping the avatar opens a
 * dropdown with a header (bigger photo + name + role) and three actions. Material3's DropdownMenu
 * already renders as a rounded, shadowed surface, so no custom Popup styling was needed to satisfy
 * that part of the design spec.
 */
@Composable
fun UserProfileMenu(
    userName: String,
    userRole: String,
    photoUrl: String?,
    onViewProfile: () -> Unit,
    onUpdatePhoto: () -> Unit,
    onLogout: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            AvatarContent(userName, photoUrl, MaterialTheme.typography.labelLarge, MaterialTheme.colorScheme.onPrimary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(260.dp)
                .onKeyEvent {
                    if (it.key == Key.Escape) {
                        expanded = false
                        true
                    } else {
                        false
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarContent(userName, photoUrl, MaterialTheme.typography.headlineSmall, MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    userName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    roleLabel(userRole),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("View Profile Image") },
                leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                onClick = { expanded = false; onViewProfile() }
            )
            DropdownMenuItem(
                text = { Text("Update Profile Image") },
                leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                onClick = { expanded = false; onUpdatePhoto() }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Log Out", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onLogout() }
            )
        }
    }
}

@Composable
private fun AvatarContent(
    userName: String,
    photoUrl: String?,
    textStyle: androidx.compose.ui.text.TextStyle,
    letterColor: androidx.compose.ui.graphics.Color
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
            style = textStyle,
            color = letterColor,
            fontWeight = FontWeight.Bold
        )
    }
}

fun roleLabel(role: String): String = when (role) {
    "admin" -> "Administrator"
    "super_admin" -> "Super Administrator"
    "conductor" -> "BS Conductor"
    else -> role.replaceFirstChar { it.uppercase() }
}
