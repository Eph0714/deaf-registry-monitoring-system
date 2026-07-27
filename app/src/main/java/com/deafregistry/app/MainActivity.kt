package com.deafregistry.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.navigation.AppNavGraph
import com.deafregistry.app.ui.theme.DeafRegistryTheme

// FragmentActivity (not the usual Compose ComponentActivity) - BiometricPrompt requires a
// FragmentActivity host to attach its dialog fragment to.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Applies the last-known theme (or the default, on first ever launch) before the first
        // frame - even the pre-login screen should render in whichever theme was last set,
        // without needing an authenticated fetch first.
        ServiceLocator.settingsRepository.applyCachedTheme()
        ServiceLocator.sessionManager.recordActivity()
        setContent {
            DeafRegistryTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavGraph(sessionManager = ServiceLocator.sessionManager)
                }
            }
        }
    }

    // Called by the system on every touch/key/trackball event that reaches this Activity while
    // it's in the foreground - the standard Android signal for "the user is still actively using
    // this screen," used here to drive the 5-minute idle auto-logout (see
    // SessionManager.recordActivity()/shouldTimeOut() and AppNavGraph's periodic check).
    override fun onUserInteraction() {
        super.onUserInteraction()
        ServiceLocator.sessionManager.recordActivity()
    }

    // Catches the case where the app was backgrounded (or the device locked) for longer than the
    // idle timeout - onUserInteraction() alone wouldn't fire again until the user touches the
    // screen, by which point they'd already see the app as if still logged in for a moment.
    // Checking here logs them out the instant the app becomes visible again, before that happens.
    override fun onResume() {
        super.onResume()
        if (ServiceLocator.sessionManager.shouldTimeOut()) {
            ServiceLocator.sessionManager.clear()
        }
    }
}
