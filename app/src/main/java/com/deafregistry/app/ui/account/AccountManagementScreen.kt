package com.deafregistry.app.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.GenericViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(onBack: () -> Unit) {
    val viewModel: AccountManagementViewModel = viewModel(
        factory = GenericViewModelFactory {
            AccountManagementViewModel(ServiceLocator.authRepository, ServiceLocator.sessionManager)
        }
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Account Management", onBack = onBack) }
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AccountInfoCard(state, viewModel)
            Spacer(Modifier.height(16.dp))
            SecurityCard(state, viewModel)
        }
    }
}

@Composable
private fun AccountInfoCard(state: AccountManagementUiState, viewModel: AccountManagementViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(icon = Icons.Default.AccountCircle, title = "Account Information")
            Spacer(Modifier.height(4.dp))
            Text(
                "Change the username you use to log in. It must be unique.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.currentUsername,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Current Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.newUsername,
                onValueChange = viewModel::onNewUsernameChange,
                label = { Text("New Username") },
                singleLine = true,
                isError = state.usernameAttemptedSubmit && state.newUsername.trim().length < 4,
                supportingText = {
                    if (state.usernameAttemptedSubmit && state.newUsername.trim().length < 4) Text("At least 4 characters")
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.confirmNewUsername,
                onValueChange = viewModel::onConfirmNewUsernameChange,
                label = { Text("Confirm New Username") },
                singleLine = true,
                isError = state.usernameAttemptedSubmit && state.newUsername != state.confirmNewUsername,
                supportingText = {
                    if (state.usernameAttemptedSubmit && state.newUsername != state.confirmNewUsername) Text("Usernames do not match")
                },
                modifier = Modifier.fillMaxWidth()
            )

            state.usernameError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.usernameSuccess?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::saveUsername,
                enabled = !state.isSavingUsername,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSavingUsername) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Change Username")
                }
            }
        }
    }
}

@Composable
private fun SecurityCard(state: AccountManagementUiState, viewModel: AccountManagementViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(icon = Icons.Default.Lock, title = "Security")
            Spacer(Modifier.height(4.dp))
            Text(
                "Change your password. You'll need to enter your current password first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            PasswordField(
                value = state.currentPassword,
                onValueChange = viewModel::onCurrentPasswordChange,
                label = "Current Password",
                show = state.showCurrentPassword,
                onToggleShow = viewModel::toggleShowCurrentPassword,
                isError = state.passwordAttemptedSubmit && state.currentPassword.isBlank(),
                supportingText = if (state.passwordAttemptedSubmit && state.currentPassword.isBlank()) "Required" else null
            )
            Spacer(Modifier.height(12.dp))
            PasswordField(
                value = state.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = "New Password",
                show = state.showNewPassword,
                onToggleShow = viewModel::toggleShowNewPassword,
                isError = state.passwordAttemptedSubmit && state.newPassword.length < 8,
                supportingText = if (state.passwordAttemptedSubmit && state.newPassword.length < 8) {
                    "At least 8 characters, with letters and numbers"
                } else null
            )
            Spacer(Modifier.height(12.dp))
            PasswordField(
                value = state.confirmNewPassword,
                onValueChange = viewModel::onConfirmNewPasswordChange,
                label = "Confirm New Password",
                show = state.showConfirmNewPassword,
                onToggleShow = viewModel::toggleShowConfirmNewPassword,
                isError = state.passwordAttemptedSubmit && state.newPassword != state.confirmNewPassword,
                supportingText = if (state.passwordAttemptedSubmit && state.newPassword != state.confirmNewPassword) {
                    "Passwords do not match"
                } else null
            )

            state.passwordError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.passwordSuccess?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::savePassword,
                enabled = !state.isSavingPassword,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSavingPassword) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Change Password")
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    show: Boolean,
    onToggleShow: () -> Unit,
    isError: Boolean,
    supportingText: String?
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleShow) {
                Icon(
                    if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (show) "Hide password" else "Show password"
                )
            }
        },
        isError = isError,
        supportingText = { if (supportingText != null) Text(supportingText) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
