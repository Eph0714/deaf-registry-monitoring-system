package com.deafregistry.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.deafregistry.app.data.remote.dto.UserDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.ZoomableAsyncImage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Fetches a fresh copy of the logged-in user's own record when opened (rather than relying on the
 * cached session) so Last Login always reflects the current server state, then shows it alongside
 * a zoomable photo. Falls back to the [session]-derived values if the live fetch fails (e.g.
 * offline) - Last Login just isn't shown in that case, since there's nothing cached for it.
 */
@Composable
fun ViewProfileDialog(
    sessionName: String,
    sessionEmail: String,
    sessionRole: String,
    photoUrl: String?,
    onDismiss: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<UserDto?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { ServiceLocator.authRepository.fetchProfile() }
            .onSuccess { profile = it }
            .onFailure { loadFailed = true }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (photoUrl != null) {
                        ZoomableAsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile photo",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                            Text(
                                sessionName.trim().firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                ProfileInfoRow("Full Name", profile?.name ?: sessionName)
                ProfileInfoRow("Username", profile?.email ?: sessionEmail)
                ProfileInfoRow("User Role", roleLabel(profile?.role ?: sessionRole))
                ProfileInfoRow(
                    "Last Login",
                    when {
                        loading -> null
                        profile?.lastLoginAt != null -> formatLastLogin(profile?.lastLoginAt)
                        loadFailed -> "Not available offline"
                        else -> "—"
                    }
                )

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value == null) {
            CircularProgressIndicator(modifier = Modifier.height(14.dp).padding(top = 2.dp))
        } else {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** The backend returns raw Postgres TIMESTAMP strings ("YYYY-MM-DD HH:MM:SS.ffffff") - reformatted
 * here for a friendlier profile display; falls back to the raw string if parsing ever fails. */
private fun formatLastLogin(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return runCatching {
        val parsed = LocalDateTime.parse(raw.take(19).replace(' ', 'T'))
        parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
    }.getOrDefault(raw)
}
