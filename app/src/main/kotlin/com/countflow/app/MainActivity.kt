package com.countflow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.countflow.app.navigation.CountFlowNavHost
import com.countflow.core.designsystem.theme.CountFlowTheme
import com.countflow.core.notifications.AndroidNotificationSender
import dagger.hilt.android.AndroidEntryPoint

/**
 * CountFlow's single activity.
 *
 * The app is deliberately single-activity: every destination is a composable registered on the
 * nav graph, which keeps navigation state in one place and avoids activity-transition cost.
 *
 * [pendingEventId] is the one exception to "everything is a composable's problem" — reading a
 * launching [Intent]'s extra is inherently an Activity-level concern. Both entry points an
 * activity can receive an intent through — [onCreate] (cold start) and [onNewIntent] (the app
 * already running, which is what a tapped notification actually hits in practice, since
 * `FLAG_ACTIVITY_CLEAR_TOP` reuses this single activity instance rather than creating a new one)
 * — feed the same field, so [CountFlowNavHost] only has to react to one thing changing.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingEventId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate so the splash theme is installed before the
        // window is created.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingEventId = intent.getStringExtra(AndroidNotificationSender.EXTRA_EVENT_ID)

        setContent {
            CountFlowTheme {
                CountFlowNavHost(
                    pendingEventId = pendingEventId,
                    onPendingEventIdConsumed = { pendingEventId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingEventId = intent.getStringExtra(AndroidNotificationSender.EXTRA_EVENT_ID)
    }
}
