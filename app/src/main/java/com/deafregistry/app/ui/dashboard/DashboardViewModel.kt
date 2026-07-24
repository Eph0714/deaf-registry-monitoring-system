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

        networkMonitor.observe()
            .onEach { online ->
                val wasOffline = !_uiState.value.isOnline
                _uiState.value = _uiState.value.copy(isOnline = online)
                if (online && wasOffline) sync()
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            try {
                val users = userRepository.list()
                _uiState.value = _uiState.value.copy(totalUsers = users.size)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(totalUsers = 0)
            }
        }

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

        refreshPendingSyncCount()
        sync()
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
