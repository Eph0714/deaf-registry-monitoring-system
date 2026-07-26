package com.deafregistry.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.remote.dto.PublicBarangayDto
import com.deafregistry.app.data.remote.dto.PublicMunicipalityDto
import com.deafregistry.app.data.repository.AuthRepository
import com.deafregistry.app.data.repository.ReferenceDataRepository
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val MIN_USERNAME_LENGTH = 4
private const val MIN_PASSWORD_LENGTH = 8

data class SignUpUiState(
    val name: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val showPassword: Boolean = false,
    val showConfirmPassword: Boolean = false,
    val contactNumber: String = "",
    val municipalities: List<PublicMunicipalityDto> = emptyList(),
    val loadingMunicipalities: Boolean = false,
    val municipalityId: Int? = null,
    val barangays: List<PublicBarangayDto> = emptyList(),
    val loadingBarangays: Boolean = false,
    val barangayId: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Only populated after a submit attempt fails validation - drives per-field required-field
     * highlighting without nagging the user before they've tried to save. */
    val attemptedSubmit: Boolean = false,
    val successMessage: String? = null
)

class SignUpViewModel(
    private val authRepository: AuthRepository,
    private val referenceDataRepository: ReferenceDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState

    init {
        _uiState.value = _uiState.value.copy(loadingMunicipalities = true)
        viewModelScope.launch {
            val result = runCatching { referenceDataRepository.getPublicMunicipalities() }
            _uiState.value = _uiState.value.copy(
                loadingMunicipalities = false,
                municipalities = result.getOrDefault(emptyList()),
                error = result.exceptionOrNull()?.let { "Couldn't load municipalities: ${friendlyMessage(it)}" }
            )
        }
    }

    fun onNameChange(value: String) { _uiState.value = _uiState.value.copy(name = value, error = null) }
    fun onUsernameChange(value: String) { _uiState.value = _uiState.value.copy(username = value, error = null) }
    fun onPasswordChange(value: String) { _uiState.value = _uiState.value.copy(password = value, error = null) }
    fun onConfirmPasswordChange(value: String) { _uiState.value = _uiState.value.copy(confirmPassword = value, error = null) }
    fun onContactNumberChange(value: String) { _uiState.value = _uiState.value.copy(contactNumber = value, error = null) }
    fun toggleShowPassword() { _uiState.value = _uiState.value.copy(showPassword = !_uiState.value.showPassword) }
    fun toggleShowConfirmPassword() { _uiState.value = _uiState.value.copy(showConfirmPassword = !_uiState.value.showConfirmPassword) }

    fun onMunicipalitySelected(id: Int) {
        _uiState.value = _uiState.value.copy(municipalityId = id, barangayId = null, barangays = emptyList(), error = null, loadingBarangays = true)
        viewModelScope.launch {
            val result = runCatching { referenceDataRepository.getPublicBarangays(id) }
            _uiState.value = _uiState.value.copy(
                loadingBarangays = false,
                barangays = result.getOrDefault(emptyList()),
                error = result.exceptionOrNull()?.let { "Couldn't load barangays: ${friendlyMessage(it)}" }
            )
        }
    }

    fun onBarangaySelected(id: Int) {
        _uiState.value = _uiState.value.copy(barangayId = id, error = null)
    }

    private fun validationError(state: SignUpUiState): String? {
        if (state.name.isBlank()) return "Full name is required"
        if (state.username.trim().length < MIN_USERNAME_LENGTH) return "Username must be at least $MIN_USERNAME_LENGTH characters"
        if (state.contactNumber.isBlank()) return "Contact number is required"
        if (state.municipalityId == null) return "Please select a municipality"
        if (state.barangayId == null) return "Please select a barangay"
        if (state.password.length < MIN_PASSWORD_LENGTH) return "Password must be at least $MIN_PASSWORD_LENGTH characters"
        if (!state.password.any { it.isLetter() } || !state.password.any { it.isDigit() }) {
            return "Password must include both letters and numbers"
        }
        if (state.password != state.confirmPassword) return "Passwords do not match"
        return null
    }

    fun signup() {
        val state = _uiState.value
        val problem = validationError(state)
        if (problem != null) {
            _uiState.value = state.copy(error = problem, attemptedSubmit = true)
            return
        }
        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val municipalityName = state.municipalities.first { it.id == state.municipalityId }.name
                val barangayName = state.barangays.first { it.id == state.barangayId }.name
                val message = authRepository.signup(
                    name = state.name.trim(),
                    username = state.username.trim(),
                    password = state.password,
                    contactNumber = state.contactNumber.trim(),
                    location = "$municipalityName / $barangayName"
                )
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = friendlyMessage(e))
            }
        }
    }
}
