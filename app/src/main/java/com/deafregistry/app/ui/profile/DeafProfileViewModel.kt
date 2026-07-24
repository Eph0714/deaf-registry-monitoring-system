package com.deafregistry.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import com.deafregistry.app.data.local.entity.RemarkEntity
import com.deafregistry.app.data.local.entity.VisitEntity
import com.deafregistry.app.data.remote.dto.AssignmentHistoryDto
import com.deafregistry.app.data.repository.DeafIndividualRepository
import com.deafregistry.app.data.repository.ReferenceDataRepository
import com.deafregistry.app.data.repository.RemarkRepository
import com.deafregistry.app.data.repository.VisitRepository
import com.deafregistry.app.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeafProfileUiState(
    val individual: DeafIndividualEntity? = null,
    val visits: List<VisitEntity> = emptyList(),
    val isRecordingVisit: Boolean = false,
    val message: String? = null
)

class DeafProfileViewModel(
    private val uuid: String,
    private val deafIndividualRepository: DeafIndividualRepository,
    private val visitRepository: VisitRepository,
    private val remarkRepository: RemarkRepository,
    private val sessionManager: SessionManager,
    private val referenceDataRepository: ReferenceDataRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    private val _assignmentHistory = MutableStateFlow<List<AssignmentHistoryDto>>(emptyList())
    val assignmentHistory: StateFlow<List<AssignmentHistoryDto>> = _assignmentHistory.asStateFlow()

    val uiState: StateFlow<DeafProfileUiState> = combine(
        deafIndividualRepository.observeById(uuid),
        visitRepository.observeForDeaf(uuid),
        _message
    ) { individual, visits, message ->
        DeafProfileUiState(individual = individual, visits = visits, message = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeafProfileUiState())

    init {
        viewModelScope.launch {
            val individual = deafIndividualRepository.getById(uuid)
            individual?.serverId?.let { serverId ->
                try {
                    visitRepository.refreshForDeaf(uuid, serverId)
                } catch (e: Exception) {
                    // offline; local cache still shown
                }
            }
        }
        loadAssignmentHistory()
    }

    fun loadAssignmentHistory() {
        viewModelScope.launch {
            val serverId = deafIndividualRepository.getById(uuid)?.serverId ?: return@launch
            runCatching { referenceDataRepository.getAssignmentHistory(serverId) }
                .onSuccess { _assignmentHistory.value = it }
        }
    }

    fun remarksFor(visitUuid: String) = remarkRepository.observeForVisit(visitUuid)

    fun addManualVisit(latitude: Double?, longitude: Double?, visitDateTime: String, publisherName: String, remarks: String) {
        viewModelScope.launch {
            val session = sessionManager.session.value
            val individual = deafIndividualRepository.getById(uuid) ?: return@launch
            val publisher = publisherName.ifBlank { session?.name ?: individual.assignedTeacherName }
            val visitUuid = visitRepository.recordVisit(
                deafUuid = uuid,
                latitude = latitude,
                longitude = longitude,
                conductorId = individual.assignedTeacherId,
                conductorName = publisher,
                visitDateTime = visitDateTime
            )
            if (remarks.isNotBlank()) {
                remarkRepository.addRemark(visitUuid, session?.userId, publisher ?: "Unknown", remarks)
            }
            _message.value = "Visit added"
        }
    }

    fun addRemark(visitUuid: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val session = sessionManager.session.value
            remarkRepository.addRemark(visitUuid, session?.userId, session?.name ?: "Unknown", text)
            _message.value = "Remark added"
        }
    }

    fun editRemark(uuid: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            remarkRepository.editRemark(uuid, text)
            _message.value = "Remark updated"
        }
    }

    fun deleteRemark(uuid: String) {
        viewModelScope.launch {
            remarkRepository.deleteRemark(uuid)
            _message.value = "Remark deleted"
        }
    }

    fun canModifyRemark(remark: RemarkEntity): Boolean {
        val session = sessionManager.session.value ?: return false
        return session.role == "admin" || (remark.userId != null && remark.userId == session.userId)
    }

    fun deleteIndividual(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deafIndividualRepository.deleteLocal(uuid)
            onDeleted()
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
