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
    /** Every previously-remembered email, for the "remembered accounts" suggestion list shown
     * when the email field gains focus. */
    val rememberedEmails: List<String> = emptyList(),
    val showEmailSuggestions: Boolean = false
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(run {
        val lastEmail = sessionManager.lastRememberedEmail()
        val lastPassword = lastEmail?.let { sessionManager.rememberedPasswordFor(it) }
        LoginUiState(
            email = lastEmail ?: "",
            password = lastPassword ?: "",
            rememberMe = lastEmail != null && lastPassword != null,
            rememberedEmails = sessionManager.rememberedEmails()
        )
    })
    val uiState: StateFlow<LoginUiState> = _uiState

    /**
     * Typing/selecting a different email looks up whether THAT specific email has a remembered
     * password - auto-filling it and checking Remember Password if so, or clearing both if the
     * email isn't remembered. Matches every other field on the form resetting its error on edit.
     */
    fun onEmailChange(value: String) {
        val rememberedPassword = sessionManager.rememberedPasswordFor(value.trim())
        _uiState.value = _uiState.value.copy(
            email = value,
            password = rememberedPassword ?: "",
            rememberMe = rememberedPassword != null,
            error = null
        )
    }

    fun onEmailFocusChanged(focused: Boolean) {
        _uiState.value = _uiState.value.copy(showEmailSuggestions = focused && _uiState.value.rememberedEmails.isNotEmpty())
    }

    /** Selecting a remembered email from the suggestion list - same effect as typing it in full. */
    fun selectRememberedEmail(email: String) {
        val rememberedPassword = sessionManager.rememberedPasswordFor(email)
        _uiState.value = _uiState.value.copy(
            email = email,
            password = rememberedPassword ?: "",
            rememberMe = rememberedPassword != null,
            showEmailSuggestions = false,
            error = null
        )
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    /**
     * Unchecking before login immediately forgets any stored password for the current email -
     * per spec this doesn't wait for a successful login, it takes effect right away.
     */
    fun onRememberMeChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = value)
        if (!value) {
            val email = _uiState.value.email.trim()
            if (email.isNotEmpty()) {
                sessionManager.forgetCredentials(email)
                _uiState.value = _uiState.value.copy(rememberedEmails = sessionManager.rememberedEmails())
            }
        }
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
                val email = state.email.trim()
                authRepository.login(email, state.password)
                // Re-saving on every successful login (not just the first time) is what keeps a
                // changed password up to date in storage, per the validation requirement that a
                // stale remembered password gets refreshed after the next successful login.
                if (state.rememberMe) {
                    sessionManager.rememberCredentials(email, state.password)
                } else {
                    sessionManager.forgetCredentials(email)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loggedIn = true,
                    rememberedEmails = sessionManager.rememberedEmails()
                )
            } catch (e: LoginException) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Login failed: ${e.message}")
            }
        }
    }
}
