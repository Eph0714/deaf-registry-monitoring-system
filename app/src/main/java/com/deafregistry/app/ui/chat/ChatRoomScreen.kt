package com.deafregistry.app.ui.chat

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.deafregistry.app.BuildConfig
import com.deafregistry.app.data.remote.dto.ChatMessageDto
import com.deafregistry.app.data.remote.dto.ChatParticipantDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.resolvePhotoUrl
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

private val EMOJI_OPTIONS = listOf("😀", "😂", "😍", "👍", "🙏", "😢", "🎉", "❤️", "👏", "🤔")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(onBack: () -> Unit, onManageSessions: () -> Unit) {
    val viewModel: ChatRoomViewModel = viewModel(
        factory = com.deafregistry.app.ui.common.GenericViewModelFactory {
            ChatRoomViewModel(ServiceLocator.chatRepository, ServiceLocator.sessionManager, ServiceLocator.settingsRepository)
        }
    )
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showEmojiRow by remember { mutableStateOf(false) }
    var pendingRemoveTarget by remember { mutableStateOf<ChatParticipantDto?>(null) }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = LocalDateTime.now()
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Chat",
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::toggleSearch) { Icon(Icons.Default.Search, contentDescription = "Search messages") }
                    IconButton(onClick = viewModel::toggleParticipants) {
                        val count = state.session?.participantCount ?: 0
                        if (count > 0) {
                            BadgedBox(badge = { Badge { Text(count.toString()) } }) {
                                Icon(Icons.Default.Groups, contentDescription = "Participants")
                            }
                        } else {
                            Icon(Icons.Default.Groups, contentDescription = "Participants")
                        }
                    }
                    if (state.isAdmin) {
                        IconButton(onClick = onManageSessions) { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Manage Chat Sessions") }
                    }
                    var notificationsEnabled by remember { mutableStateOf(ServiceLocator.settingsRepository.chatNotificationsEnabled()) }
                    IconButton(onClick = {
                        notificationsEnabled = !notificationsEnabled
                        ServiceLocator.settingsRepository.setChatNotificationsEnabled(notificationsEnabled)
                    }) {
                        Icon(
                            if (notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = if (notificationsEnabled) "Disable chat notifications" else "Enable chat notifications"
                        )
                    }
                }
            )
        }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val session = state.session
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (session == null) {
                EmptyState("No chat session is currently scheduled.")
            } else {
                ChatHeader(
                    sessionName = session.sessionName,
                    status = session.status,
                    participantCount = session.participantCount ?: 0,
                    startDatetime = session.startDatetime,
                    endDatetime = session.endDatetime,
                    now = now
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isOwn = message.userId == state.currentUserId,
                            isAdmin = state.isAdmin,
                            onDelete = { viewModel.deleteMessage(message.id) },
                            onTogglePin = { viewModel.togglePin(message) }
                        )
                    }
                }

                if (showEmojiRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    ) {
                        EMOJI_OPTIONS.forEach { emoji ->
                            Text(
                                emoji,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .clickable { viewModel.onMessageInputChange(state.messageInput + emoji) }
                            )
                        }
                    }
                }

                state.sendError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                }

                val inputEnabled = session.status == "open"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showEmojiRow = !showEmojiRow }, enabled = inputEnabled) {
                        Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji")
                    }
                    OutlinedTextField(
                        value = state.messageInput,
                        onValueChange = viewModel::onMessageInputChange,
                        enabled = inputEnabled,
                        placeholder = {
                            Text(
                                when (session.status) {
                                    "scheduled" -> "Chat session has not started yet."
                                    "closed" -> "This chat session has ended."
                                    else -> "Type a message"
                                }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = viewModel::sendMessage,
                        enabled = inputEnabled && state.messageInput.isNotBlank() && !state.sending
                    ) {
                        if (state.sending) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }

    if (state.showSearch) {
        SearchDialog(
            query = state.searchQuery,
            date = state.searchDate,
            results = state.searchResults,
            searching = state.searching,
            onQueryChange = viewModel::onSearchQueryChange,
            onDateChange = viewModel::onSearchDateChange,
            onSearch = viewModel::runSearch,
            onDismiss = viewModel::toggleSearch
        )
    }

    if (state.showParticipants) {
        ParticipantsDialog(
            participants = state.participants,
            loading = state.loadingParticipants,
            isAdmin = state.isAdmin,
            currentUserId = state.currentUserId,
            onDismiss = viewModel::toggleParticipants,
            onMute = { userId, mute -> viewModel.muteParticipant(userId, mute) },
            onRemove = { pendingRemoveTarget = it }
        )
    }

    pendingRemoveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemoveTarget = null },
            title = { Text("Remove from chat?") },
            text = { Text("${target.name} will no longer be able to send messages or rejoin this chat session.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeParticipant(target.userId)
                    pendingRemoveTarget = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoveTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ChatHeader(
    sessionName: String,
    status: String,
    participantCount: Int,
    startDatetime: String,
    endDatetime: String,
    now: LocalDateTime
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(sessionName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            StatusPill(status)
            Spacer(Modifier.height(4.dp))
            when (status) {
                "open" -> {
                    val end = parseServerDateTime(endDatetime)
                    val remaining = end?.let { Duration.between(now, it) } ?: Duration.ZERO
                    Text("Ends in: ${formatCountdown(remaining)}", style = MaterialTheme.typography.bodyMedium)
                }
                "scheduled" -> {
                    val start = parseServerDateTime(startDatetime)
                    val remaining = start?.let { Duration.between(now, it) } ?: Duration.ZERO
                    Text("Starts in: ${formatCountdown(remaining)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "This chat session has not started yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                "closed" -> {
                    Text(
                        "This chat session has ended.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("$participantCount Participant${if (participantCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (emoji, label, color) = when (status) {
        "open" -> Triple("🟢", "Open", androidx.compose.ui.graphics.Color(0xFF2E7D32))
        "scheduled" -> Triple("🟡", "Starting Soon", androidx.compose.ui.graphics.Color(0xFFF9A825))
        "closed" -> Triple("🔴", "Closed", androidx.compose.ui.graphics.Color(0xFFC62828))
        else -> Triple("⚪", status, androidx.compose.ui.graphics.Color.Gray)
    }
    Text("$emoji $label", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun MessageBubble(
    message: ChatMessageDto,
    isOwn: Boolean,
    isAdmin: Boolean,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        if (!isOwn) {
            val photo = resolvePhotoUrl(message.userPhotoUrl, BuildConfig.API_BASE_URL)
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (photo != null) {
                    AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(message.userName.trim().firstOrNull()?.uppercase() ?: "?", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!isOwn) {
                Text(message.userName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 14.dp, topEnd = 14.dp,
                    bottomStart = if (isOwn) 14.dp else 2.dp,
                    bottomEnd = if (isOwn) 2.dp else 14.dp
                ),
                color = if (isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (message.isPinned) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PushPin, contentDescription = "Pinned",
                                modifier = Modifier.size(12.dp),
                                tint = if (isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Pinned", style = MaterialTheme.typography.labelSmall,
                                color = if (isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        message.message,
                        color = if (isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatClockTime(message.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isAdmin) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.PushPin, contentDescription = if (message.isPinned) "Unpin" else "Pin", modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete message", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDialog(
    query: String,
    date: String,
    results: List<ChatMessageDto>?,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Messages") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Username, full name, or message text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = onDateChange,
                    label = { Text("Date (YYYY-MM-DD, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                if (searching) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (results != null) {
                    if (results.isEmpty()) {
                        Text("No matching messages found.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(modifier = Modifier.height(240.dp)) {
                            items(results, key = { it.id }) { message ->
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    Text("${message.userName} • ${formatServerDateTime(message.sentAt)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(message.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSearch) { Text("Search") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParticipantsDialog(
    participants: List<ChatParticipantDto>,
    loading: Boolean,
    isAdmin: Boolean,
    currentUserId: Int,
    onDismiss: () -> Unit,
    onMute: (Int, Boolean) -> Unit,
    onRemove: (ChatParticipantDto) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Participants") },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (participants.isEmpty()) {
                Text("No one has joined this chat session yet.")
            } else {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(participants, key = { it.userId }) { participant ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val photo = resolvePhotoUrl(participant.photoUrl, BuildConfig.API_BASE_URL)
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (photo != null) {
                                    AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Text(participant.name.trim().firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(participant.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    participant.username ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (participant.isOnline) "Online" else "Last active ${formatServerDateTime(participant.lastActiveAt, "MMM d, h:mm a")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (participant.isOnline) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isAdmin && participant.userId != currentUserId) {
                                IconButton(onClick = { onMute(participant.userId, !participant.isMuted) }) {
                                    Icon(
                                        if (participant.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = if (participant.isMuted) "Unmute" else "Mute",
                                        tint = if (participant.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onRemove(participant) }) {
                                    Icon(Icons.Default.PersonRemove, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
