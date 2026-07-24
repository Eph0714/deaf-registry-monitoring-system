package com.deafregistry.app.ui.municipality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.local.entity.BarangayEntity
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import com.deafregistry.app.data.repository.DeafIndividualRepository
import com.deafregistry.app.data.repository.ReferenceDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

data class MunicipalityFilters(
    val query: String = "",
    val barangayId: Int? = null,
    val skillLevel: String? = null,
    val monitoringStatus: String? = null,
    val gender: String? = null
)

class MunicipalityListViewModel(
    private val municipalityId: Int,
    private val referenceDataRepository: ReferenceDataRepository,
    private val deafIndividualRepository: DeafIndividualRepository,
    initialBarangayId: Int? = null
) : ViewModel() {

    private val _filters = MutableStateFlow(MunicipalityFilters(barangayId = initialBarangayId))
    val filters: StateFlow<MunicipalityFilters> = _filters

    private val _barangays = MutableStateFlow<List<BarangayEntity>>(emptyList())
    val barangays: StateFlow<List<BarangayEntity>> = _barangays

    val individuals: StateFlow<List<DeafIndividualEntity>> = _filters
        .flatMapLatest { f ->
            deafIndividualRepository.observeForMunicipality(
                municipalityId, f.barangayId, f.skillLevel, f.monitoringStatus, f.gender, f.query
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        referenceDataRepository.observeBarangays(municipalityId)
            .onEach { _barangays.value = it }
            .launchIn(viewModelScope)
    }

    fun updateQuery(query: String) {
        _filters.value = _filters.value.copy(query = query)
    }

    fun updateBarangay(id: Int?) {
        _filters.value = _filters.value.copy(barangayId = id)
    }

    fun updateSkill(skill: String?) {
        _filters.value = _filters.value.copy(skillLevel = skill)
    }

    fun updateStatus(status: String?) {
        _filters.value = _filters.value.copy(monitoringStatus = status)
    }

    fun updateGender(gender: String?) {
        _filters.value = _filters.value.copy(gender = gender)
    }

    fun clearFilters() {
        _filters.value = MunicipalityFilters()
    }
}
