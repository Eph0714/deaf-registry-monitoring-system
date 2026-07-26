package com.deafregistry.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.BuildConfig
import com.deafregistry.app.data.local.dao.MunicipalityWithCount
import com.deafregistry.app.data.remote.dto.AppVersionDto
import com.deafregistry.app.data.remote.dto.BySkillDto
import com.deafregistry.app.data.remote.dto.ByStatusDto
import com.deafregistry.app.data.remote.dto.NotVisitedDto
import com.deafregistry.app.data.remote.dto.RecentVisitDto
import com.deafregistry.app.data.repository.ChatRepository
import com.deafregistry.app.data.repository.ReferenceDataRepository
import com.deafregistry.app.data.repository.ReportRepository
import com.deafregistry.app.data.repository.SettingsRepository
import com.deafregistry.app.data.repository.UserRepository
import com.deafregistry.app.data.session.SessionManager
import com.deafregistry.app.data.sync.SyncManager
import com.deafregistry.app.util.NetworkMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class DashboardUiState(
    val municipalities: List<MunicipalityWithCount> = emptyList(),
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val isOnline: Boolean = true,
    val userName: String = "",
    val isAdmin: Boolean = false,
    val totalUsers: Int = 0,
    val pendingApprovalCount: Int = 0,
    val recentVisits: List<RecentVisitDto> = emptyList(),
    val pendingFollowUps: List<NotVisitedDto> = emptyList(),
    val byStatus: List<ByStatusDto> = emptyList(),
    val bySkill: List<BySkillDto> = emptyList(),
    val pendingSyncCount: Int = 0,
    val updateInfo: AppVersionDto? = null,
    val showUpdateDialog: Boolean = false,
    val overdueDaysThreshold: Int = 30,
    val unreadChatCount: Int = 0
)

class DashboardViewModel(
    private val referenceDataRepository: ReferenceDataRepository,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val reportRepository: ReportRepository,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        val session = sessionManager.session.value
        _uiState.value = _uiState.value.copy(
            userName = session?.name ?: "",
            isAdmin = sessionManager.isAdmin()
        )
        referenceDataRepository.observeMunicipalitiesWithCounts()
            .onEach { list -> _uiState.value = _uiState.value.copy(municipalities = list) }
            .launchIn(viewModelScope)

        // Only tracks connectivity for the status row / enabling the Sync button - it does NOT
        // auto-trigger a sync. Pushing local changes to the server happens exclusively when the
        // user taps Sync (see sync() below), even once the device comes back online.
        networkMonitor.observe()
            .onEach { online -> _uiState.value = _uiState.value.copy(isOnline = online) }
            .launchIn(viewModelScope)

        refreshUserCounts()
        refreshReportCards()
        refreshUnreadChatCount()

        refreshPendingSyncCount()
        // Pull-only refresh on screen load - downloads fresh data but never pushes local edits.
        // Local edits only leave the device when the user taps Sync (see sync() below).
        viewModelScope.launch {
            runCatching { syncManager.pull() }
        }

        checkForUpdate()
    }

    /**
     * This app isn't distributed through Google Play, so there's no automatic update channel -
     * App Update in Control Panel is what sets this value. Checked on Dashboard load and again
     * every time the user taps Sync or pulls to refresh (see sync() below), so a version an
     * admin just published shows up without needing to fully restart the app.
     */
    private fun checkForUpdate() {
        viewModelScope.launch {
            runCatching { settingsRepository.getLatestAppVersion() }
                .onSuccess { info ->
                    if (info.versionCode > BuildConfig.VERSION_CODE && !info.apkUrl.isNullOrBlank()) {
                        _uiState.value = _uiState.value.copy(updateInfo = info, showUpdateDialog = true)
                    }
                }
        }
    }

    /**
     * Reports are viewable by every role (server-enforced too - only export/print on
     * ReportsScreen stays admin-only), so these Dashboard summary cards - including Latest
     * Visits - fetch for everyone. Called on load and again from sync() so tapping Sync (or
     * pull-to-refresh, which also calls sync()) re-fetches the latest recorded visit instead of
     * only ever showing what was on screen when the Dashboard first opened.
     */
    private fun refreshReportCards() {
        viewModelScope.launch {
            try {
                val overdueDays = settingsRepository.cachedOverdueDays().toInt()
                val recentVisits = reportRepository.recentVisits(5)
                val pendingFollowUps = reportRepository.notVisited(overdueDays)
                val byStatus = reportRepository.byStatus()
                val bySkill = reportRepository.bySkill()
                _uiState.value = _uiState.value.copy(
                    recentVisits = recentVisits,
                    pendingFollowUps = pendingFollowUps,
                    byStatus = byStatus,
                    bySkill = bySkill,
                    overdueDaysThreshold = overdueDays
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    recentVisits = emptyList(),
                    pendingFollowUps = emptyList(),
                    byStatus = emptyList(),
                    bySkill = emptyList()
                )
            }
        }
    }

    private var updateReminderJob: Job? = null

    /**
     * Hides the dialog but keeps nagging - re-shows it a few minutes later, and keeps doing so
     * for the rest of the session, since dismissing (even via "Update Now", which launches the
     * system package installer but can't confirm the user actually completed that install) doesn't
     * mean the device is actually on the new version yet. Only a real app restart on the new build
     * stops this.
     */
    fun dismissUpdatePrompt() {
        _uiState.value = _uiState.value.copy(showUpdateDialog = false)
        updateReminderJob?.cancel()
        updateReminderJob = viewModelScope.launch {
            delay(UPDATE_REMINDER_INTERVAL_MS)
            if (_uiState.value.updateInfo != null) {
                _uiState.value = _uiState.value.copy(showUpdateDialog = true)
            }
        }
    }

    fun sync() {
        _uiState.value = _uiState.value.copy(isSyncing = true, syncError = null)
        viewModelScope.launch {
            try {
                syncManager.sync()
                _uiState.value = _uiState.value.copy(isSyncing = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSyncing = false, syncError = "Sync failed: ${com.deafregistry.app.util.friendlyMessage(e)}")
            }
            refreshPendingSyncCount()
        }
        refreshUserCounts()
        refreshReportCards()
        refreshUnreadChatCount()
        checkForUpdate()
    }

    /** Unread = messages in the currently-open chat session that arrived after this device last
     * had the Chat Room screen open (see SettingsRepository.lastSeenChatMessageId). Drives the
     * badge on the Dashboard's Chat tile - called on load, on Sync/pull-to-refresh, and whenever
     * the Dashboard is revisited (see the LaunchedEffect in DashboardScreen), same cadence as
     * refreshUserCounts(). */
    fun refreshUnreadChatCount() {
        viewModelScope.launch {
            val result = runCatching {
                val session = chatRepository.activeSession()
                if (session != null && session.status == "open") {
                    chatRepository.getMessages(session.id, settingsRepository.lastSeenChatMessageId()).size
                } else {
                    0
                }
            }
            _uiState.value = _uiState.value.copy(unreadChatCount = result.getOrDefault(0))
        }
    }

    /**
     * This ViewModel instance survives navigating away to Admin screens and back (it's scoped to
     * Dashboard's nav back-stack entry, not recreated on each visit), so a count fetched only in
     * init() would go stale the moment a user is added/removed or a signup is approved/declined
     * elsewhere - the Dashboard would keep showing the pre-change number until the app restarts.
     * Called from init, from sync(), and again by the screen itself every time it's revisited.
     */
    fun refreshUserCounts() {
        viewModelScope.launch {
            try {
                val users = userRepository.list()
                // Matches Manage Users' default view (deactivated accounts hidden behind "Show
                // deleted accounts") - counting is_active=false rows here made this tile disagree
                // with what an admin sees when they tap into it.
                _uiState.value = _uiState.value.copy(totalUsers = users.count { it.isActive != false })
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(totalUsers = 0)
            }
        }

        if (sessionManager.isAdmin()) {
            viewModelScope.launch {
                runCatching { userRepository.pendingSignups() }
                    .onSuccess { pending -> _uiState.value = _uiState.value.copy(pendingApprovalCount = pending.size) }
            }
        }
    }

    private fun refreshPendingSyncCount() {
        viewModelScope.launch {
            runCatching { syncManager.pendingCount() }
                .onSuccess { count -> _uiState.value = _uiState.value.copy(pendingSyncCount = count) }
        }
    }

    private companion object {
        const val UPDATE_REMINDER_INTERVAL_MS = 5 * 60 * 1000L
    }
}
