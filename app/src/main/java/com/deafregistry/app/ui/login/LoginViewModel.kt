package com.deafregistry.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.repository.AuthRepository
import com.deafregistry.app.data.repository.LoginException
import com.deafregistry.app.data.session.SessionManager
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val biometricEnabled: Boolean = false,
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
    /** Every previously-remembered username, for the "remembered accounts" suggestion list shown
     * when the username field gains focus. */
    val rememberedUsernames: List<String> = emptyList(),
    val showUsernameSuggestions: Boolean = false,
    val showForgotPasswordDialog: Boolean = false,
    val forgotPasswordUsername: String = "",
    val forgotPasswordNote: String = "",
    val forgotPasswordSubmitting: Boolean = false,
    val forgotPasswordResult: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(run {
        // Auto-fill only applies on a normal app launch - never right after an explicit Logout,
        // so a different person picking up the device can't immediately log back in as the
        // previous user without typing anything. consumeLoggedOutFlag() also resets the flag, so
        // this suppression only ever applies to the one Login screen visit right after a logout.
        if (sessionManager.consumeLoggedOutFlag()) {
            LoginUiState(rememberedUsernames = sessionManager.rememberedUsernames())
        } else {
            val lastUsername = sessionManager.lastRememberedUsername()
            val lastPassword = lastUsername?.let { sessionManager.rememberedPasswordFor(it) }
            LoginUiState(
                username = lastUsername ?: "",
                password = lastPassword ?: "",
                rememberMe = lastUsername != null && lastPassword != null,
                biometricEnabled = lastUsername != null && sessionManager.canUseBiometricFor(lastUsername),
                rememberedUsernames = sessionManager.rememberedUsernames()
            )
        }
    })
    val uiState: StateFlow<LoginUiState> = _uiState

    /**
     * Typing/selecting a different username looks up whether THAT specific username has a
     * remembered password - auto-filling it and checking Remember Password if so, or clearing
     * both if the username isn't remembered. Matches every other field on the form resetting its
     * error on edit.
     */
    fun onUsernameChange(value: String) {
        val trimmed = value.trim()
        val rememberedPassword = sessionManager.rememberedPasswordFor(trimmed)
        _uiState.value = _uiState.value.copy(
            username = value,
            password = rememberedPassword ?: "",
            rememberMe = rememberedPassword != null,
            biometricEnabled = rememberedPassword != null && sessionManager.canUseBiometricFor(trimmed),
            error = null
        )
    }

    fun onUsernameFocusChanged(focused: Boolean) {
        _uiState.value = _uiState.value.copy(showUsernameSuggestions = focused && _uiState.value.rememberedUsernames.isNotEmpty())
    }

    /** Selecting a remembered username from the suggestion list - same effect as typing it in full. */
    fun selectRememberedUsername(username: String) {
        val rememberedPassword = sessionManager.rememberedPasswordFor(username)
        _uiState.value = _uiState.value.copy(
            username = username,
            password = rememberedPassword ?: "",
            rememberMe = rememberedPassword != null,
            biometricEnabled = sessionManager.canUseBiometricFor(username),
            showUsernameSuggestions = false,
            error = null
        )
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    /**
     * Unchecking before login immediately forgets any stored password for the current username -
     * per spec this doesn't wait for a successful login, it takes effect right away. Also turns
     * off Biometric Login for that username (handled inside forgetCredentials), since biometric
     * login depends on the password still being remembered.
     */
    fun onRememberMeChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = value, biometricEnabled = if (value) _uiState.value.biometricEnabled else false)
        if (!value) {
            val username = _uiState.value.username.trim()
            if (username.isNotEmpty()) {
                sessionManager.forgetCredentials(username)
                _uiState.value = _uiState.value.copy(rememberedUsernames = sessionManager.rememberedUsernames())
            }
        }
    }

    /** Only meaningful while Remember Password is checked - the actual on/off toggle is persisted
     * at login time (see login() below) alongside rememberCredentials(), same as rememberMe. */
    fun onBiometricEnabledChange(value: Boolean) {
        if (!_uiState.value.rememberMe) return
        _uiState.value = _uiState.value.copy(biometricEnabled = value)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(passwordVisible = !_uiState.value.passwordVisible)
    }

    fun login() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Username and password are required")
            return
        }
        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val username = state.username.trim()
                authRepository.login(username, state.password)
                // Re-saving on every successful login (not just the first time) is what keeps a
                // changed password up to date in storage, per the validation requirement that a
                // stale remembered password gets refreshed after the next successful login.
                if (state.rememberMe) {
                    sessionManager.rememberCredentials(username, state.password)
                    sessionManager.setBiometricEnabled(username, state.biometricEnabled)
                } else {
                    sessionManager.forgetCredentials(username)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loggedIn = true,
                    rememberedUsernames = sessionManager.rememberedUsernames()
                )
            } catch (e: LoginException) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Login failed: ${friendlyMessage(e)}")
            }
        }
    }

    /** The account a tap on "Login with Biometrics" should use - prefers the currently-typed
     * username if it has biometric enabled, otherwise falls back to any other remembered account
     * that does (this app is mainly used on a single device per conductor, so in practice there's
     * usually at most one). Returns null when no account has ever had Biometric Login enabled on
     * this device - the button is always shown regardless (see LoginScreen), but a real fingerprint
     * scan only means anything if it's actually linked to a saved account already. */
    fun biometricLoginUsername(): String? {
        val typed = _uiState.value.username.trim()
        if (typed.isNotBlank() && sessionManager.canUseBiometricFor(typed)) return typed
        return sessionManager.rememberedUsernames().firstOrNull { sessionManager.canUseBiometricFor(it) }
    }

    /** Called when "Login with Biometrics" is tapped but biometricLoginUsername() found nothing to
     * log into - shown instead of ever opening the system fingerprint/face prompt for nothing. */
    fun onBiometricNotEnrolled() {
        _uiState.value = _uiState.value.copy(
            error = "Biometric login isn't set up on this device yet. Log in normally with \"Remember Password\" and \"Enable Biometric Login\" checked to set it up."
        )
    }

    /** Called after a successful BiometricPrompt auth for [username] (already validated by
     * biometricLoginUsername() to have a remembered password) - fills the form from the stored
     * credentials and submits. */
    fun loginWithBiometric(username: String) {
        val password = sessionManager.rememberedPasswordFor(username) ?: return
        _uiState.value = _uiState.value.copy(username = username, password = password, rememberMe = true)
        login()
    }

    fun openForgotPasswordDialog() {
        _uiState.value = _uiState.value.copy(
            showForgotPasswordDialog = true,
            forgotPasswordUsername = _uiState.value.username,
            forgotPasswordNote = "",
            forgotPasswordResult = null
        )
    }

    fun dismissForgotPasswordDialog() {
        _uiState.value = _uiState.value.copy(showForgotPasswordDialog = false)
    }

    fun onForgotPasswordUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(forgotPasswordUsername = value)
    }

    fun onForgotPasswordNoteChange(value: String) {
        _uiState.value = _uiState.value.copy(forgotPasswordNote = value)
    }

    fun submitForgotPassword() {
        val username = _uiState.value.forgotPasswordUsername.trim()
        if (username.isBlank()) return
        _uiState.value = _uiState.value.copy(forgotPasswordSubmitting = true)
        viewModelScope.launch {
            val result = runCatching { authRepository.forgotPassword(username, _uiState.value.forgotPasswordNote.trim().ifBlank { null }) }
            _uiState.value = _uiState.value.copy(
                forgotPasswordSubmitting = false,
                forgotPasswordResult = result.getOrElse { "Request failed: ${friendlyMessage(it)}" }
            )
        }
    }
}
