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
        setContent {
            DeafRegistryTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavGraph(sessionManager = ServiceLocator.sessionManager)
                }
            }
        }
    }
}
