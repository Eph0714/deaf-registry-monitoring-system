package com.deafregistry.app.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.util.BiometricUtil
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.launch

/**
 * Lets the currently logged-in user turn Biometric Login on/off for their own account without
 * having to log out first - previously the only way to enable it was the checkbox on the Login
 * screen at the moment of a fresh login. This app can't enroll a fingerprint itself (only the
 * device OS can - see BiometricUtil.openEnrollSettings's doc), so "registering" here means: prove
 * you know the account password once, then confirm with a live device biometric/PIN scan, and
 * this device's already-encrypted "remembered password" store (the same one Remember Password
 * uses) gets linked to biometric unlock for next time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricLoginSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by ServiceLocator.sessionManager.session.collectAsState()
    val username = session?.username ?: ""
    val hardwareAvailable = remember { BiometricUtil.isAvailable(context) }
    var enabled by remember(username) { mutableStateOf(ServiceLocator.sessionManager.canUseBiometricFor(username)) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun registerFingerprint() {
        isSaving = true
        message = null
        scope.launch {
            val verified = runCatching { ServiceLocator.authRepository.login(username, password) }
            if (verified.isFailure) {
                isSaving = false
                isError = true
                message = "Incorrect password."
                return@launch
            }
            val activity = context as? FragmentActivity
            if (activity == null) {
                isSaving = false
                isError = true
                message = friendlyMessage(IllegalStateException("no FragmentActivity"))
                return@launch
            }
            BiometricUtil.authenticate(
                activity = activity,
                onSuccess = {
                    ServiceLocator.sessionManager.rememberCredentials(username, password)
                    ServiceLocator.sessionManager.setBiometricEnabled(username, true)
                    isSaving = false
                    isError = false
                    enabled = true
                    password = ""
                    message = "Biometric Login is now enabled for your account."
                },
                onError = { errText ->
                    isSaving = false
                    isError = true
                    message = "Fingerprint verification didn't complete: $errText"
                }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Biometric Login", onBack = onBack) }
    ) { padding: PaddingValues ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            if (!hardwareAvailable) {
                Text(
                    "Your device doesn't support biometric login yet, or no fingerprint, face, or PIN is registered on it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { BiometricUtil.openEnrollSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Set Up in Device Settings")
                }
            } else if (enabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Biometric Login is enabled for \"$username\" on this device. You can use your fingerprint, face, or device PIN to log in next time.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        ServiceLocator.sessionManager.setBiometricEnabled(username, false)
                        enabled = false
                        message = "Biometric Login has been disabled for your account."
                        isError = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Disable Biometric Login") }
            } else {
                Text(
                    "Enter your password once to link this device's fingerprint, face, or PIN to your account (\"$username\") - after that, you can log in with a fingerprint scan instead of typing your password.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; message = null },
                    label = { Text("Current Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { registerFingerprint() },
                    enabled = password.isNotBlank() && !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Register Fingerprint")
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
