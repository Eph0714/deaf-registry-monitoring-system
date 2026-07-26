package com.deafregistry.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.remote.dto.ChatMessageDto
import com.deafregistry.app.data.remote.dto.ChatNextScheduleDto
import com.deafregistry.app.data.remote.dto.ChatParticipantDto
import com.deafregistry.app.data.remote.dto.ChatSessionDto
import com.deafregistry.app.data.repository.ChatRepository
import com.deafregistry.app.data.repository.SettingsRepository
import com.deafregistry.app.data.session.SessionManager
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.util.NotificationHelper
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

data class ChatRoomUiState(
    val loading: Boolean = true,
    val session: ChatSessionDto? = null,
    val messages: List<ChatMessageDto> = emptyList(),
    val error: String? = null,
    val messageInput: String = "",
    val sending: Boolean = false,
    val sendError: String? = null,
    val isAdmin: Boolean = false,
    val currentUserId: Int = -1,
    val showParticipants: Boolean = false,
    val participants: List<ChatParticipantDto> = emptyList(),
    val loadingParticipants: Boolean = false,
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val searchDate: String = "",
    val searchResults: List<ChatMessageDto>? = null,
    val searching: Boolean = false,
    // Only populated while there's no active session - "chat unavailable, next available: ..." (see
    // chat.controller.js::getChatStatus, the same computation the admin dashboard card uses).
    val nextSchedule: ChatNextScheduleDto? = null
)

/**
 * Polls the active chat session every [POLL_INTERVAL_MS] while this ViewModel is alive (i.e. the
 * Chat Room screen is on screen) - this is the "real-time" mechanism for this app (see the
 * project's decision to use polling over WebSockets, given the backend's free-tier hosting has no
 * WebSocket infrastructure and sleeps when idle). The loop is a plain viewModelScope coroutine, so
 * it's automatically cancelled the moment the screen is left (onCleared).
 */
class ChatRoomViewModel(
    private val chatRepository: ChatRepository,
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState

    private var pollingJob: Job? = null
    private var lastMessageId: Int? = null
    private var joinedSessionId: Int? = null

    // Local-only, per-process dedup so the foreground poll loop never fires the same milestone
    // notification twice for the same session.
    private var previousStatus: String? = null
    private var fiveMinWarnedSessionId: Int? = null
    private var sessionEndedNotifiedId: Int? = null

    // Guards against re-fetching "next available schedule" on every 4-second poll tick while the
    // room stays closed - only needs refreshing once per closed-period, reset the moment a session
    // becomes active again.
    private var nextScheduleFetchedThisClosedPeriod = false

    init {
        val session = sessionManager.session.value
        _uiState.value = _uiState.value.copy(isAdmin = sessionManager.isAdmin(), currentUserId = session?.userId ?: -1)
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshOnce() {
        val activeSession = runCatching { chatRepository.activeSession() }.getOrNull()
        val previousSession = _uiState.value.session

        // Covers both ways a session stops being open: it disappears entirely (auto-expired -
        // getActiveSession never returns 'expired' rows) or it's still returned but the admin
        // manually closed it early (status flips to 'closed' while the id stays the same).
        notifySessionEndedIfNeeded(previousSession, activeSession)

        if (activeSession == null) {
            if (!nextScheduleFetchedThisClosedPeriod) {
                nextScheduleFetchedThisClosedPeriod = true
                val next = runCatching { chatRepository.chatStatus() }.getOrNull()?.nextSchedule
                _uiState.value = _uiState.value.copy(nextSchedule = next)
            }
            _uiState.value = _uiState.value.copy(loading = false, session = null, messages = emptyList(), error = null)
            lastMessageId = null
            joinedSessionId = null
            previousStatus = null
            return
        }
        nextScheduleFetchedThisClosedPeriod = false

        if (joinedSessionId != activeSession.id) {
            lastMessageId = null
            joinedSessionId = activeSession.id
            _uiState.value = _uiState.value.copy(messages = emptyList())
            runCatching { chatRepository.joinSession(activeSession.id) }
        }

        var newMessages: List<ChatMessageDto> = emptyList()
        if (activeSession.status == "open" || activeSession.status == "closed") {
            newMessages = runCatching { chatRepository.getMessages(activeSession.id, lastMessageId) }.getOrDefault(emptyList())
            if (newMessages.isNotEmpty()) {
                lastMessageId = newMessages.last().id
                // Marks these messages as seen for the Dashboard's unread-count badge - the Chat
                // Room screen being open and actively polling IS "read" for this app's purposes.
                settingsRepository.setLastSeenChatMessageId(newMessages.last().id)
            }
        }

        notifySessionOpenedIfNeeded(activeSession)
        notifyNewMessagesIfNeeded(activeSession, newMessages)
        notifyFiveMinuteWarningIfNeeded(activeSession)
        previousStatus = activeSession.status

        _uiState.value = _uiState.value.copy(
            loading = false,
            session = activeSession,
            messages = _uiState.value.messages + newMessages,
            error = null
        )
    }

    // ---- Local notifications (foreground only - see class doc) ----

    private fun notificationsEnabled() = settingsRepository.chatNotificationsEnabled()

    private fun notifySessionOpenedIfNeeded(session: ChatSessionDto) {
        if (!notificationsEnabled()) return
        if (session.status == "open" && previousStatus != "open") {
            NotificationHelper.notify(
                ServiceLocator.appContext(), id = 3001,
                title = "Chat session opened",
                text = "\"${session.sessionName}\" is now open.",
                channelId = NotificationHelper.CHAT_CHANNEL_ID
            )
        }
    }

    private fun notifyNewMessagesIfNeeded(session: ChatSessionDto, newMessages: List<ChatMessageDto>) {
        if (!notificationsEnabled()) return
        val fromOthers = newMessages.filter { it.userId != _uiState.value.currentUserId }
        // lastMessageId being freshly set from null (first load) would otherwise notify for the
        // room's entire history at once - only notify once this session has already been polled.
        if (fromOthers.isEmpty() || previousStatus == null) return
        val last = fromOthers.last()
        NotificationHelper.notify(
            ServiceLocator.appContext(), id = 3002,
            title = session.sessionName,
            text = "${last.userName}: ${last.message}".take(200),
            channelId = NotificationHelper.CHAT_CHANNEL_ID
        )
    }

    private fun notifyFiveMinuteWarningIfNeeded(session: ChatSessionDto) {
        if (!notificationsEnabled() || session.status != "open") return
        val end = parseServerDateTime(session.endDatetime) ?: return
        val remaining = Duration.between(LocalDateTime.now(), end)
        if (!remaining.isNegative && remaining.seconds <= 5 * 60 && fiveMinWarnedSessionId != session.id) {
            fiveMinWarnedSessionId = session.id
            NotificationHelper.notify(
                ServiceLocator.appContext(), id = 3003,
                title = "Chat ending soon",
                text = "\"${session.sessionName}\" closes in 5 minutes.",
                channelId = NotificationHelper.CHAT_CHANNEL_ID
            )
        }
    }

    private fun notifySessionEndedIfNeeded(previousSession: ChatSessionDto?, newSession: ChatSessionDto?) {
        if (!notificationsEnabled()) return
        if (previousStatus != "open") return
        val name = previousSession?.sessionName ?: return
        val id = previousSession.id
        val stillOpenSameSession = newSession?.id == id && newSession.status == "open"
        if (stillOpenSameSession) return
        if (sessionEndedNotifiedId == id) return
        sessionEndedNotifiedId = id
        NotificationHelper.notify(
            ServiceLocator.appContext(), id = 3004,
            title = "Chat session ended",
            text = "\"$name\" has ended.",
            channelId = NotificationHelper.CHAT_CHANNEL_ID
        )
    }

    // ---- User actions ----

    fun onMessageInputChange(value: String) {
        _uiState.value = _uiState.value.copy(messageInput = value, sendError = null)
    }

    fun sendMessage() {
        val text = _uiState.value.messageInput.trim()
        val sessionId = _uiState.value.session?.id ?: return
        if (text.isBlank() || _uiState.value.sending) return
        _uiState.value = _uiState.value.copy(sending = true, sendError = null)
        viewModelScope.launch {
            runCatching { chatRepository.sendMessage(sessionId, text) }
                .onSuccess {
                    lastMessageId = it.id
                    _uiState.value = _uiState.value.copy(
                        sending = false,
                        messageInput = "",
                        messages = _uiState.value.messages + it
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(sending = false, sendError = friendlyMessage(it)) }
        }
    }

    fun deleteMessage(messageId: Int) {
        val sessionId = _uiState.value.session?.id ?: return
        viewModelScope.launch {
            runCatching { chatRepository.deleteMessage(sessionId, messageId) }
                .onSuccess { _uiState.value = _uiState.value.copy(messages = _uiState.value.messages.filterNot { it.id == messageId }) }
        }
    }

    fun togglePin(message: ChatMessageDto) {
        val sessionId = _uiState.value.session?.id ?: return
        viewModelScope.launch {
            val result = if (message.isPinned) {
                runCatching { chatRepository.unpinMessage(sessionId, message.id) }
            } else {
                runCatching { chatRepository.pinMessage(sessionId, message.id) }
            }
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map { if (it.id == message.id) it.copy(isPinned = !message.isPinned) else it }
                )
            }
        }
    }

    fun toggleParticipants() {
        val show = !_uiState.value.showParticipants
        _uiState.value = _uiState.value.copy(showParticipants = show)
        if (show) loadParticipants()
    }

    fun loadParticipants() {
        val sessionId = _uiState.value.session?.id ?: return
        _uiState.value = _uiState.value.copy(loadingParticipants = true)
        viewModelScope.launch {
            val result = runCatching { chatRepository.getParticipants(sessionId) }
            _uiState.value = _uiState.value.copy(
                loadingParticipants = false,
                participants = result.getOrDefault(_uiState.value.participants)
            )
        }
    }

    fun muteParticipant(userId: Int, mute: Boolean) {
        val sessionId = _uiState.value.session?.id ?: return
        viewModelScope.launch {
            runCatching {
                if (mute) chatRepository.muteParticipant(sessionId, userId) else chatRepository.unmuteParticipant(sessionId, userId)
            }.onSuccess { loadParticipants() }
        }
    }

    fun removeParticipant(userId: Int) {
        val sessionId = _uiState.value.session?.id ?: return
        viewModelScope.launch {
            runCatching { chatRepository.removeParticipant(sessionId, userId) }.onSuccess { loadParticipants() }
        }
    }

    fun toggleSearch() {
        _uiState.value = _uiState.value.copy(showSearch = !_uiState.value.showSearch, searchResults = null)
    }

    fun onSearchQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(searchQuery = value)
    }

    fun onSearchDateChange(value: String) {
        _uiState.value = _uiState.value.copy(searchDate = value)
    }

    fun runSearch() {
        val sessionId = _uiState.value.session?.id ?: return
        val query = _uiState.value.searchQuery.trim()
        val date = _uiState.value.searchDate.trim()
        if (query.isBlank() && date.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        _uiState.value = _uiState.value.copy(searching = true)
        viewModelScope.launch {
            val result = runCatching { chatRepository.searchMessages(sessionId, query, date) }
            _uiState.value = _uiState.value.copy(searching = false, searchResults = result.getOrDefault(emptyList()))
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 4000L
    }
}
