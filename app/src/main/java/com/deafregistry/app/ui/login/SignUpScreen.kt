package com.deafregistry.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.GenericViewModelFactory
import com.deafregistry.app.ui.common.SearchableSelectField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(onBack: () -> Unit) {
    val viewModel: SignUpViewModel = viewModel(
        factory = GenericViewModelFactory { SignUpViewModel(ServiceLocator.authRepository, ServiceLocator.referenceDataRepository) }
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Create Account", onBack = onBack) }
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // imePadding() before verticalScroll() so the scroll viewport itself accounts
                // for the keyboard - see DeafEditorScreen for why the order matters.
                .imePadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.successMessage != null) {
                Spacer(Modifier.height(32.dp))
                Text("Request Submitted", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    state.successMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to Log In") }
            } else {
                Text(
                    "Request access to the Deaf and Mute Registry app. An administrator will review your request and approve your account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Full Name") },
                    singleLine = true,
                    isError = state.attemptedSubmit && state.name.isBlank(),
                    supportingText = { if (state.attemptedSubmit && state.name.isBlank()) Text("Required") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Username") },
                    singleLine = true,
                    isError = state.attemptedSubmit && state.username.trim().length < 4,
                    supportingText = { if (state.attemptedSubmit && state.username.trim().length < 4) Text("At least 4 characters") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.contactNumber,
                    onValueChange = viewModel::onContactNumberChange,
                    label = { Text("Contact Number") },
                    singleLine = true,
                    isError = state.attemptedSubmit && state.contactNumber.isBlank(),
                    supportingText = { if (state.attemptedSubmit && state.contactNumber.isBlank()) Text("Required") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                SearchableSelectField(
                    label = "Municipality",
                    options = state.municipalities.map { it.id to it.name },
                    selectedId = state.municipalityId,
                    onSelect = viewModel::onMunicipalitySelected,
                    loading = state.loadingMunicipalities,
                    isError = state.attemptedSubmit && state.municipalityId == null,
                    supportingText = if (state.attemptedSubmit && state.municipalityId == null) "Required" else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                SearchableSelectField(
                    label = "Barangay",
                    options = state.barangays.map { it.id to it.name },
                    selectedId = state.barangayId,
                    onSelect = viewModel::onBarangaySelected,
                    enabled = state.municipalityId != null,
                    loading = state.loadingBarangays,
                    isError = state.attemptedSubmit && state.barangayId == null,
                    supportingText = if (state.attemptedSubmit && state.barangayId == null) "Required" else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = viewModel::toggleShowPassword) {
                            Icon(
                                if (state.showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (state.showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    isError = state.attemptedSubmit && state.password.length < 8,
                    supportingText = { if (state.attemptedSubmit && state.password.length < 8) Text("At least 8 characters, with letters and numbers") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = if (state.showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = viewModel::toggleShowConfirmPassword) {
                            Icon(
                                if (state.showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (state.showConfirmPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    isError = state.attemptedSubmit && state.password != state.confirmPassword,
                    supportingText = { if (state.attemptedSubmit && state.password != state.confirmPassword) Text("Passwords do not match") },
                    modifier = Modifier.fillMaxWidth()
                )

                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = viewModel::signup, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Sign Up")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}
