package com.deafregistry.app.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deafregistry.app.BuildConfig
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.GenericViewModelFactory
import com.deafregistry.app.util.BiometricUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onSignUp: () -> Unit) {
    val viewModel: LoginViewModel = viewModel(
        factory = GenericViewModelFactory { LoginViewModel(ServiceLocator.authRepository, ServiceLocator.sessionManager) }
    )
    val state by viewModel.uiState.collectAsState()
    val usernameFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val biometricHardwareAvailable = remember { BiometricUtil.isAvailable(context) }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    // Sets focus to the Username field whenever the Login screen appears - both on a normal app
    // launch and, more importantly, right after an explicit Logout, so the next person at the
    // device lands straight on the Username field instead of the previous user's leftover state.
    LaunchedEffect(Unit) {
        usernameFocusRequester.requestFocus()
    }

    Scaffold { padding: PaddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Deaf and Mute Registry", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Sign in to manage the registry",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(usernameFocusRequester)
                    .onFocusChanged { viewModel.onUsernameFocusChanged(it.isFocused) }
            )
            if (state.showUsernameSuggestions) {
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column {
                        state.rememberedUsernames.forEach { rememberedUsername ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // A plain onClick loses focus before onUsernameFocusChanged(false)
                                    // fires, which would hide this list before the tap registers -
                                    // selectRememberedUsername() closes it explicitly instead.
                                    .clickable { viewModel.selectRememberedUsername(rememberedUsername) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(rememberedUsername, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            if (state.passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (state.passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = state.rememberMe, onCheckedChange = viewModel::onRememberMeChange)
                Text("Remember password", style = MaterialTheme.typography.bodyMedium)
            }

            if (biometricHardwareAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.biometricEnabled,
                        onCheckedChange = viewModel::onBiometricEnabledChange,
                        enabled = state.rememberMe
                    )
                    Text(
                        "Enable Biometric Login",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.rememberMe) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::openForgotPasswordDialog) { Text("Forgot Password?") }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::login, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Log In")
                }
            }

            if (biometricHardwareAvailable && state.biometricAvailableForUsername) {
                Spacer(Modifier.height(8.dp))
                val activity = context as? FragmentActivity
                OutlinedButton(
                    onClick = {
                        val username = state.username.trim()
                        activity?.let {
                            BiometricUtil.authenticate(
                                activity = it,
                                onSuccess = { viewModel.loginWithBiometric(username) },
                                onError = { }
                            )
                        }
                    },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Login with Biometrics")
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignUp) { Text("Don't have an account? Sign Up") }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Made by: EMF IT Solutions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }
    }

    if (state.showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissForgotPasswordDialog,
            title = { Text("Forgot Password") },
            text = {
                if (state.forgotPasswordResult != null) {
                    Text(state.forgotPasswordResult!!)
                } else {
                    Column {
                        Text(
                            "This app can't email you a reset link directly - submit your username below and an administrator will review your request and reset your password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.forgotPasswordUsername,
                            onValueChange = viewModel::onForgotPasswordUsernameChange,
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.forgotPasswordNote,
                            onValueChange = viewModel::onForgotPasswordNoteChange,
                            label = { Text("Note (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.forgotPasswordSubmitting) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            },
            confirmButton = {
                if (state.forgotPasswordResult != null) {
                    TextButton(onClick = viewModel::dismissForgotPasswordDialog) { Text("Close") }
                } else {
                    TextButton(
                        enabled = !state.forgotPasswordSubmitting && state.forgotPasswordUsername.isNotBlank(),
                        onClick = viewModel::submitForgotPassword
                    ) { Text("Submit") }
                }
            },
            dismissButton = {
                if (state.forgotPasswordResult == null) {
                    TextButton(onClick = viewModel::dismissForgotPasswordDialog) { Text("Cancel") }
                }
            }
        )
    }
}
