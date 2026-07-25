package com.deafregistry.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.navigation.AppNavGraph
import com.deafregistry.app.ui.theme.DeafRegistryTheme

class MainActivity : ComponentActivity() {
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
