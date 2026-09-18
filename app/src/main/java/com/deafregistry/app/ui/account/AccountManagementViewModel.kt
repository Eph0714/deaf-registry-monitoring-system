package com.deafregistry.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.repository.AuthRepository
import com.deafregistry.app.data.session.SessionManager
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val MIN_USERNAME_LENGTH = 4
private const val MIN_PASSWORD_LENGTH = 8

data class AccountManagementUiState(
    val currentUsername: String = "",
    val newUsername: String = "",
    val confirmNewUsername: String = "",
    val isSavingUsername: Boolean = false,
    val usernameError: String? = null,
    val usernameSuccess: String? = null,
    val usernameAttemptedSubmit: Boolean = false,

    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val showCurrentPassword: Boolean = false,
    val showNewPassword: Boolean = false,
    val showConfirmNewPassword: Boolean = false,
    val isSavingPassword: Boolean = false,
    val passwordError: String? = null,
    val passwordSuccess: String? = null,
    val passwordAttemptedSubmit: Boolean = false
)

/** Backs [AccountManagementScreen] - self-service username/password change for the logged-in
 * user. All the server-side plumbing (uniqueness check, hashing, audit log, cache rename) already
 * lives in AuthRepository/SessionManager; this just drives the two independent form sections. */
class AccountManagementViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AccountManagementUiState(currentUsername = sessionManager.session.value?.username ?: "")
    )
    val uiState: StateFlow<AccountManagementUiState> = _uiState

    fun onNewUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(newUsername = value, usernameError = null, usernameSuccess = null)
    }

    fun onConfirmNewUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmNewUsername = value, usernameError = null, usernameSuccess = null)
    }

    private fun usernameValidationError(state: AccountManagementUiState): String? {
        val trimmed = state.newUsername.trim()
        if (trimmed.length < MIN_USERNAME_LENGTH) return "Username must be at least $MIN_USERNAME_LENGTH characters"
        if (trimmed.equals(state.currentUsername, ignoreCase = true)) return "This is already your current username"
        if (state.newUsername != state.confirmNewUsername) return "Usernames do not match"
        return null
    }

    fun saveUsername() {
        val state = _uiState.value
        val problem = usernameValidationError(state)
        if (problem != null) {
            _uiState.value = state.copy(usernameError = problem, usernameAttemptedSubmit = true)
            return
        }
        _uiState.value = state.copy(isSavingUsername = true, usernameError = null)
        viewModelScope.launch {
            val result = runCatching { authRepository.changeUsername(state.newUsername.trim()) }
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSavingUsername = false,
                    currentUsername = sessionManager.session.value?.username ?: state.newUsername.trim(),
                    newUsername = "",
                    confirmNewUsername = "",
                    usernameAttemptedSubmit = false,
                    usernameSuccess = "Username updated successfully"
                )
            }
            result.onFailure {
                _uiState.value = _uiState.value.copy(isSavingUsername = false, usernameError = friendlyMessage(it))
            }
        }
    }

    fun onCurrentPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(currentPassword = value, passwordError = null, passwordSuccess = null)
    }

    fun onNewPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(newPassword = value, passwordError = null, passwordSuccess = null)
    }

    fun onConfirmNewPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmNewPassword = value, passwordError = null, passwordSuccess = null)
    }

    fun toggleShowCurrentPassword() { _uiState.value = _uiState.value.copy(showCurrentPassword = !_uiState.value.showCurrentPassword) }
    fun toggleShowNewPassword() { _uiState.value = _uiState.value.copy(showNewPassword = !_uiState.value.showNewPassword) }
    fun toggleShowConfirmNewPassword() { _uiState.value = _uiState.value.copy(showConfirmNewPassword = !_uiState.value.showConfirmNewPassword) }

    private fun passwordValidationError(state: AccountManagementUiState): String? {
        if (state.currentPassword.isBlank()) return "Enter your current password"
        if (state.newPassword.length < MIN_PASSWORD_LENGTH) return "Password must be at least $MIN_PASSWORD_LENGTH characters"
        if (!state.newPassword.any { it.isLetter() } || !state.newPassword.any { it.isDigit() }) {
            return "Password must include both letters and numbers"
        }
        if (state.newPassword != state.confirmNewPassword) return "Passwords do not match"
        if (state.newPassword == state.currentPassword) return "New password must be different from your current password"
        return null
    }

    fun savePassword() {
        val state = _uiState.value
        val problem = passwordValidationError(state)
        if (problem != null) {
            _uiState.value = state.copy(passwordError = problem, passwordAttemptedSubmit = true)
            return
        }
        _uiState.value = state.copy(isSavingPassword = true, passwordError = null)
        viewModelScope.launch {
            val result = runCatching { authRepository.changePassword(state.currentPassword, state.newPassword) }
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSavingPassword = false,
                    currentPassword = "",
                    newPassword = "",
                    confirmNewPassword = "",
                    passwordAttemptedSubmit = false,
                    passwordSuccess = "Password changed successfully"
                )
            }
            result.onFailure {
                _uiState.value = _uiState.value.copy(isSavingPassword = false, passwordError = friendlyMessage(it))
            }
        }
    }
}
