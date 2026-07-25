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
    val loggedIn: Boolean = false,
    /** A previously-remembered email shown as a tappable suggestion - tapping it fills both
     * the email and password fields. Null once selected or once the user edits either field
     * by hand. */
    val rememberedEmailSuggestion: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // Held privately (not put in uiState) so the password never shows up in the UI until the
    // user explicitly taps the remembered-account suggestion.
    private val rememberedCredentials = sessionManager.rememberedCredentials()

    private val _uiState = MutableStateFlow(
        LoginUiState(
            rememberMe = rememberedCredentials != null,
            rememberedEmailSuggestion = rememberedCredentials?.first
        )
    )
    val uiState: StateFlow<LoginUiState> = _uiState

    /** Fills both fields from the remembered credentials - called when the user taps the suggestion. */
    fun selectRememberedAccount() {
        val (email, password) = rememberedCredentials ?: return
        _uiState.value = _uiState.value.copy(email = email, password = password, rememberedEmailSuggestion = null, error = null)
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null, rememberedEmailSuggestion = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null, rememberedEmailSuggestion = null)
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
