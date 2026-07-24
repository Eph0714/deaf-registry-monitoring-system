package com.deafregistry.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.local.dao.MunicipalityWithCount
import com.deafregistry.app.data.remote.dto.BySkillDto
import com.deafregistry.app.data.remote.dto.ByStatusDto
import com.deafregistry.app.data.remote.dto.NotVisitedDto
import com.deafregistry.app.data.remote.dto.RecentVisitDto
import com.deafregistry.app.data.repository.ReferenceDataRepository
import com.deafregistry.app.data.repository.ReportRepository
import com.deafregistry.app.data.repository.UserRepository
import com.deafregistry.app.data.session.SessionManager
import com.deafregistry.app.data.sync.SyncManager
import com.deafregistry.app.util.NetworkMonitor
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
    val pendingSyncCount: Int = 0
)

class DashboardViewModel(
    private val referenceDataRepository: ReferenceDataRepository,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val reportRepository: ReportRepository,
    private val networkMonitor: NetworkMonitor
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

        // Reports are admin-only (server-enforced too) - conductors never see this data, so
        // skip the calls entirely rather than hitting an expected 403.
        if (sessionManager.isAdmin()) {
            viewModelScope.launch {
                try {
                    val recentVisits = reportRepository.recentVisits(5)
                    val pendingFollowUps = reportRepository.notVisited(30)
                    val byStatus = reportRepository.byStatus()
                    val bySkill = reportRepository.bySkill()
                    _uiState.value = _uiState.value.copy(
                        recentVisits = recentVisits,
                        pendingFollowUps = pendingFollowUps,
                        byStatus = byStatus,
                        bySkill = bySkill
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

        refreshPendingSyncCount()
        // Pull-only refresh on screen load - downloads fresh data but never pushes local edits.
        // Local edits only leave the device when the user taps Sync (see sync() below).
        viewModelScope.launch {
            runCatching { syncManager.pull() }
        }
    }

    fun sync() {
        _uiState.value = _uiState.value.copy(isSyncing = true, syncError = null)
        viewModelScope.launch {
            try {
                syncManager.sync()
                _uiState.value = _uiState.value.copy(isSyncing = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSyncing = false, syncError = "Sync failed: ${e.message}")
            }
            refreshPendingSyncCount()
        }
    }

    private fun refreshPendingSyncCount() {
        viewModelScope.launch {
            runCatching { syncManager.pendingCount() }
                .onSuccess { count -> _uiState.value = _uiState.value.copy(pendingSyncCount = count) }
        }
    }
}
