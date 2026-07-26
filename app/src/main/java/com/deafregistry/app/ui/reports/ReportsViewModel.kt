package com.deafregistry.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.remote.dto.ByBarangayDto
import com.deafregistry.app.data.remote.dto.ByConductorDto
import com.deafregistry.app.data.remote.dto.ByGenderDto
import com.deafregistry.app.data.remote.dto.ByMunicipalityDto
import com.deafregistry.app.data.remote.dto.ByMunicipalityStatusDto
import com.deafregistry.app.data.remote.dto.ByStatusDto
import com.deafregistry.app.data.remote.dto.BySkillDto
import com.deafregistry.app.data.remote.dto.NotVisitedDto
import com.deafregistry.app.data.remote.dto.RecentVisitDto
import com.deafregistry.app.data.repository.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ReportsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val total: Int = 0,
    val byMunicipality: List<ByMunicipalityDto> = emptyList(),
    val byMunicipalityStatus: List<ByMunicipalityStatusDto> = emptyList(),
    val byBarangay: List<ByBarangayDto> = emptyList(),
    val byGender: List<ByGenderDto> = emptyList(),
    val bySkill: List<BySkillDto> = emptyList(),
    val byStatus: List<ByStatusDto> = emptyList(),
    val byConductor: List<ByConductorDto> = emptyList(),
    val recentVisits: List<RecentVisitDto> = emptyList(),
    val notVisited: List<NotVisitedDto> = emptyList()
)

class ReportsViewModel(private val reportRepository: ReportRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val total = reportRepository.summary().total
                val byMunicipality = reportRepository.byMunicipality()
                val byMunicipalityStatus = reportRepository.byMunicipalityStatus()
                val byBarangay = reportRepository.byBarangay()
                val byGender = reportRepository.byGender()
                val bySkill = reportRepository.bySkill()
                val byStatus = reportRepository.byStatus()
                val byConductor = reportRepository.byConductor()
                val recentVisits = reportRepository.recentVisits()
                val notVisited = reportRepository.notVisited()
                _uiState.value = ReportsUiState(
                    isLoading = false, total = total, byMunicipality = byMunicipality,
                    byMunicipalityStatus = byMunicipalityStatus, byBarangay = byBarangay,
                    byGender = byGender, bySkill = bySkill, byStatus = byStatus, byConductor = byConductor,
                    recentVisits = recentVisits, notVisited = notVisited
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Could not load reports (are you online?): ${com.deafregistry.app.util.friendlyMessage(e)}")
            }
        }
    }
}
