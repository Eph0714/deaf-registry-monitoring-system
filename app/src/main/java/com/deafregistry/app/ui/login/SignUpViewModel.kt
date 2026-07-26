package com.deafregistry.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val contactNumber: String = "",
    val location: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class SignUpViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState

    fun onNameChange(value: String) { _uiState.value = _uiState.value.copy(name = value, error = null) }
    fun onEmailChange(value: String) { _uiState.value = _uiState.value.copy(email = value, error = null) }
    fun onUsernameChange(value: String) { _uiState.value = _uiState.value.copy(username = value, error = null) }
    fun onPasswordChange(value: String) { _uiState.value = _uiState.value.copy(password = value, error = null) }
    fun onConfirmPasswordChange(value: String) { _uiState.value = _uiState.value.copy(confirmPassword = value, error = null) }
    fun onContactNumberChange(value: String) { _uiState.value = _uiState.value.copy(contactNumber = value, error = null) }
    fun onLocationChange(value: String) { _uiState.value = _uiState.value.copy(location = value, error = null) }

    fun signup() {
        val state = _uiState.value
        if (state.name.isBlank() || state.email.isBlank() || state.username.isBlank() || state.password.isBlank() ||
            state.contactNumber.isBlank() || state.location.isBlank()
        ) {
            _uiState.value = state.copy(error = "Name, email, username, password, contact number and location are required")
            return
        }
        if (state.password.length < 8) {
            _uiState.value = state.copy(error = "Password must be at least 8 characters")
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(error = "Passwords do not match")
            return
        }
        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val message = authRepository.signup(
                    state.name.trim(), state.email.trim(), state.username.trim(), state.password,
                    state.contactNumber.trim(), state.location.trim()
                )
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Sign up failed: ${e.message}")
            }
        }
    }
}
