package com.deafregistry.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** Distinguishes *why* biometric auth isn't usable right now, since the Biometric Login settings
 * screen needs to show a different message/action for each case: a device with no sensor at all
 * can't do anything, while a device with a sensor but nothing enrolled just needs the user to add
 * one in Settings. */
enum class BiometricStatus { AVAILABLE, NOT_ENROLLED, NO_HARDWARE }

object BiometricUtil {

    /** Whether this device can do biometric (or device PIN/pattern) auth right now - checked
     * before ever showing a "Login with Biometrics" option, so the button never appears on a
     * device with no usable hardware/enrollment. */
    fun isAvailable(context: Context): Boolean = status(context) == BiometricStatus.AVAILABLE

    /** Finer-grained read of [isAvailable] - see [BiometricStatus]. */
    fun status(context: Context): BiometricStatus =
        when (BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            else -> BiometricStatus.NO_HARDWARE
        }

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )
        // Combining DEVICE_CREDENTIAL with BIOMETRIC_STRONG means the system supplies its own PIN
        // fallback and cancel affordance - setNegativeButtonText() isn't allowed alongside it.
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Login")
            .setSubtitle("Use your fingerprint, face, or device PIN to log in")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()
        prompt.authenticate(promptInfo)
    }

    /** Opens the device's own biometric/PIN enrollment screen - this app can never enroll a
     * fingerprint itself (Android doesn't expose raw fingerprint data to apps at all, only a
     * yes/no match against whatever's already enrolled at the OS level), so this is the closest
     * thing to a "register your fingerprint" action available from within the app. */
    fun openEnrollSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, ALLOWED_AUTHENTICATORS
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        runCatching { context.startActivity(intent) }
    }
}
