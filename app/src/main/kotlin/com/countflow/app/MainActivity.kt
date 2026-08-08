package com.countflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.countflow.app.navigation.CountFlowNavHost
import com.countflow.core.designsystem.theme.CountFlowTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * CountFlow's single activity.
 *
 * The app is deliberately single-activity: every destination is a composable registered on the
 * nav graph, which keeps navigation state in one place and avoids activity-transition cost.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate so the splash theme is installed before the
        // window is created.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CountFlowTheme {
                CountFlowNavHost()
            }
        }
    }
}
