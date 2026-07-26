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
    /** True only when the username AND password fields currently, exactly match a remembered
     * account that has Biometric Login enabled - the Login screen uses this to decide whether to
     * show the shortcut. Deliberately re-checked on every keystroke in either field (see
     * computeBiometricAvailability): biometric is a shortcut for re-submitting what's already
     * shown on screen, not a way to log in with blank/edited-away fields, so clearing or changing
     * either field immediately hides the button again. */
    val biometricAvailableForUsername: Boolean = false,
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
                rememberedUsernames = sessionManager.rememberedUsernames(),
                biometricAvailableForUsername = computeBiometricAvailability(sessionManager, lastUsername ?: "", lastPassword ?: "")
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
        val newPassword = rememberedPassword ?: ""
        _uiState.value = _uiState.value.copy(
            username = value,
            password = newPassword,
            rememberMe = rememberedPassword != null,
            biometricEnabled = rememberedPassword != null && sessionManager.canUseBiometricFor(trimmed),
            biometricAvailableForUsername = computeBiometricAvailability(sessionManager, trimmed, newPassword),
            error = null
        )
    }

    fun onUsernameFocusChanged(focused: Boolean) {
        _uiState.value = _uiState.value.copy(showUsernameSuggestions = focused && _uiState.value.rememberedUsernames.isNotEmpty())
    }

    /** Selecting a remembered username from the suggestion list - same effect as typing it in full. */
    fun selectRememberedUsername(username: String) {
        val rememberedPassword = sessionManager.rememberedPasswordFor(username)
        val newPassword = rememberedPassword ?: ""
        _uiState.value = _uiState.value.copy(
            username = username,
            password = newPassword,
            rememberMe = rememberedPassword != null,
            biometricEnabled = sessionManager.canUseBiometricFor(username),
            biometricAvailableForUsername = computeBiometricAvailability(sessionManager, username, newPassword),
            showUsernameSuggestions = false,
            error = null
        )
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(
            password = value,
            biometricAvailableForUsername = computeBiometricAvailability(sessionManager, _uiState.value.username.trim(), value),
            error = null
        )
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
        _uiState.value = _uiState.value.copy(
            biometricAvailableForUsername = computeBiometricAvailability(sessionManager, _uiState.value.username.trim(), _uiState.value.password)
        )
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
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Login failed: ${com.deafregistry.app.util.friendlyMessage(e)}")
            }
        }
    }

    /** Called after a successful BiometricPrompt auth - the Login screen only ever offers this
     * button when the visible fields already exactly match a biometric-enabled remembered
     * account (see biometricAvailableForUsername), so re-reading the stored password here (rather
     * than trusting whatever's currently in state) just confirms that hasn't changed underneath
     * it and submits. */
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
                forgotPasswordResult = result.getOrElse { "Request failed: ${com.deafregistry.app.util.friendlyMessage(it)}" }
            )
        }
    }
}

/** True only when [username]/[password] are both non-blank, the username has Biometric Login
 * enabled, and [password] is exactly the password currently remembered for it - i.e. the visible
 * fields genuinely show the enrolled account, not just a username match with an empty or edited
 * password field. */
private fun computeBiometricAvailability(sessionManager: SessionManager, username: String, password: String): Boolean {
    if (username.isBlank() || password.isBlank()) return false
    if (!sessionManager.canUseBiometricFor(username)) return false
    return sessionManager.rememberedPasswordFor(username) == password
}
