package com.deafregistry.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.local.entity.BarangayEntity
import com.deafregistry.app.data.local.entity.MunicipalityEntity
import com.deafregistry.app.data.local.entity.TeacherEntity
import com.deafregistry.app.data.repository.DeafIndividualRepository
import com.deafregistry.app.data.repository.ReferenceDataRepository
import com.deafregistry.app.data.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DeafEditorFormState(
    val uuid: String? = null,
    val fullName: String = "",
    val birthDate: String = "",
    val gender: String = "Male",
    val municipalityId: Int? = null,
    val barangayId: Int? = null,
    val purok: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val skillLevel: String = "Natural",
    val monitoringStatus: String = "Unlocated",
    val assignedTeacherId: Int? = null,
    val assignedDate: String = "",
    val contactNumber: String = "",
    val email: String = "",
    val maritalStatus: String = "",
    val emergencyContactName: String = "",
    val emergencyContactNumber: String = "",
    val notes: String = "",
    val localPhotoPath: String? = null,
    val existingPhotoUrl: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

class DeafEditorViewModel(
    private val existingUuid: String?,
    initialMunicipalityId: Int?,
    private val deafIndividualRepository: DeafIndividualRepository,
    private val referenceDataRepository: ReferenceDataRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _form = MutableStateFlow(DeafEditorFormState(uuid = existingUuid, municipalityId = initialMunicipalityId))
    val form: StateFlow<DeafEditorFormState> = _form

    private val _municipalities = MutableStateFlow<List<MunicipalityEntity>>(emptyList())
    val municipalities: StateFlow<List<MunicipalityEntity>> = _municipalities

    private val _barangays = MutableStateFlow<List<BarangayEntity>>(emptyList())
    val barangays: StateFlow<List<BarangayEntity>> = _barangays

    private val _teachers = MutableStateFlow<List<TeacherEntity>>(emptyList())
    val teachers: StateFlow<List<TeacherEntity>> = _teachers

    init {
        viewModelScope.launch {
            _municipalities.value = referenceDataRepository.getAllMunicipalitiesCached()
            _teachers.value = referenceDataRepository.getAllTeachersCached()

            if (existingUuid != null) {
                val existing = deafIndividualRepository.getById(existingUuid)
                if (existing != null) {
                    _form.value = _form.value.copy(
                        uuid = existing.uuid,
                        fullName = existing.fullName,
                        birthDate = existing.birthDate ?: "",
                        gender = existing.gender,
                        municipalityId = existing.municipalityId,
                        barangayId = existing.barangayId,
                        purok = existing.purok ?: "",
                        latitude = existing.latitude,
                        longitude = existing.longitude,
                        skillLevel = existing.skillLevel,
                        monitoringStatus = existing.monitoringStatus,
                        assignedTeacherId = existing.assignedTeacherId,
                        assignedDate = existing.assignedDate ?: "",
                        contactNumber = existing.contactNumber ?: "",
                        email = existing.email ?: "",
                        maritalStatus = existing.maritalStatus ?: "",
                        emergencyContactName = existing.emergencyContactName ?: "",
                        emergencyContactNumber = existing.emergencyContactNumber ?: "",
                        notes = existing.notes ?: "",
                        existingPhotoUrl = existing.photoUrl,
                        localPhotoPath = existing.localPhotoPath
                    )
                    loadBarangays(existing.municipalityId)
                }
            } else {
                initialMunicipalityId?.let { loadBarangays(it) }
            }
            _form.value = _form.value.copy(isLoading = false)
        }
    }

    private fun loadBarangays(municipalityId: Int) {
        viewModelScope.launch {
            _barangays.value = referenceDataRepository.getBarangaysForMunicipality(municipalityId)
        }
    }

    fun update(transform: (DeafEditorFormState) -> DeafEditorFormState) {
        _form.value = transform(_form.value)
    }

    fun onMunicipalitySelected(id: Int) {
        _form.value = _form.value.copy(municipalityId = id, barangayId = null)
        loadBarangays(id)
    }

    fun onLocationCaptured(lat: Double, lon: Double) {
        _form.value = _form.value.copy(latitude = lat, longitude = lon)
    }

    fun onLocationReset() {
        _form.value = _form.value.copy(latitude = null, longitude = null)
    }

    fun onPhotoSelected(path: String) {
        _form.value = _form.value.copy(localPhotoPath = path)
    }

    fun save() {
        val f = _form.value
        if (f.fullName.isBlank() || f.municipalityId == null || f.barangayId == null) {
            _form.value = f.copy(error = "Full name, municipality and barangay are required")
            return
        }
        if (f.latitude != null && (f.latitude < -90.0 || f.latitude > 90.0)) {
            _form.value = f.copy(error = "Latitude must be between -90 and 90")
            return
        }
        if (f.longitude != null && (f.longitude < -180.0 || f.longitude > 180.0)) {
            _form.value = f.copy(error = "Longitude must be between -180 and 180")
            return
        }
        if ((f.latitude == null) != (f.longitude == null)) {
            _form.value = f.copy(error = "Please provide both latitude and longitude, or leave both blank")
            return
        }
        _form.value = f.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                if (f.uuid == null) {
                    val newUuid = deafIndividualRepository.createLocal(
                        fullName = f.fullName.trim(),
                        birthDate = f.birthDate.ifBlank { null },
                        gender = f.gender,
                        barangayId = f.barangayId,
                        purok = f.purok.ifBlank { null },
                        municipalityId = f.municipalityId,
                        latitude = f.latitude,
                        longitude = f.longitude,
                        skillLevel = f.skillLevel,
                        monitoringStatus = f.monitoringStatus,
                        assignedTeacherId = f.assignedTeacherId,
                        assignedDate = f.assignedDate.ifBlank { null },
                        contactNumber = f.contactNumber.ifBlank { null },
                        email = f.email.ifBlank { null },
                        maritalStatus = f.maritalStatus.ifBlank { null },
                        emergencyContactName = f.emergencyContactName.ifBlank { null },
                        emergencyContactNumber = f.emergencyContactNumber.ifBlank { null },
                        notes = f.notes.ifBlank { null }
                    )
                    f.localPhotoPath?.let { deafIndividualRepository.setLocalPhoto(newUuid, it) }
                } else {
                    deafIndividualRepository.updateLocal(
                        uuid = f.uuid,
                        fullName = f.fullName.trim(),
                        birthDate = f.birthDate.ifBlank { null },
                        gender = f.gender,
                        barangayId = f.barangayId,
                        purok = f.purok.ifBlank { null },
                        municipalityId = f.municipalityId,
                        latitude = f.latitude,
                        longitude = f.longitude,
                        skillLevel = f.skillLevel,
                        monitoringStatus = f.monitoringStatus,
                        assignedTeacherId = f.assignedTeacherId,
                        assignedDate = f.assignedDate.ifBlank { null },
                        contactNumber = f.contactNumber.ifBlank { null },
                        email = f.email.ifBlank { null },
                        maritalStatus = f.maritalStatus.ifBlank { null },
                        emergencyContactName = f.emergencyContactName.ifBlank { null },
                        emergencyContactNumber = f.emergencyContactNumber.ifBlank { null },
                        notes = f.notes.ifBlank { null }
                    )
                    if (f.localPhotoPath != null && f.localPhotoPath != f.existingPhotoUrl) {
                        deafIndividualRepository.setLocalPhoto(f.uuid, f.localPhotoPath)
                    }
                }
                _form.value = _form.value.copy(isSaving = false, saved = true)
                runCatching { syncManager.sync() }
            } catch (e: Exception) {
                _form.value = _form.value.copy(isSaving = false, error = com.deafregistry.app.util.friendlyMessage(e))
            }
        }
    }
}
