package com.deafregistry.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.repository.AuthRepository
import com.deafregistry.app.data.repository.LoginException
import com.deafregistry.app.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        sessionManager.rememberedCredentials()?.let { (email, password) ->
            _uiState.value = _uiState.value.copy(email = email, password = password, rememberMe = true)
        }
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onRememberMeChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = value)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(passwordVisible = !_uiState.value.passwordVisible)
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Email and password are required")
            return
        }
        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                authRepository.login(state.email.trim(), state.password)
                if (state.rememberMe) {
                    sessionManager.saveRememberedCredentials(state.email.trim(), state.password)
                } else {
                    sessionManager.clearRememberedCredentials()
                }
                _uiState.value = _uiState.value.copy(isLoading = false, loggedIn = true)
            } catch (e: LoginException) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Login failed: ${e.message}")
            }
        }
    }
}
