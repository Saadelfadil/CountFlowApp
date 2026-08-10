package com.countflow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.countflow.app.navigation.CountFlowNavHost
import com.countflow.core.designsystem.theme.CountFlowTheme
import com.countflow.core.domain.repository.PreferencesRepository
import com.countflow.core.domain.repository.ThemeMode
import com.countflow.core.domain.repository.UserPreferences
import com.countflow.core.notifications.AndroidNotificationSender
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
 *
 * [preferencesRepository] is read directly here, rather than through a ViewModel, purely to
 * decide [CountFlowTheme]'s two parameters (Session 14) — there is no other UI state at this
 * level, and a whole ViewModel for two fields would be ceremony without benefit. DataStore's
 * `Flow` already survives process death and reboot on its own, so no extra persistence is needed
 * here; collecting it live also means a change made in Settings recomposes this theme immediately,
 * with no restart.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private var pendingEventId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate so the splash theme is installed before the
        // window is created.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingEventId = intent.getStringExtra(AndroidNotificationSender.EXTRA_EVENT_ID)

        setContent {
            val preferences by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences.Default,
            )
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CountFlowTheme(darkTheme = darkTheme, dynamicColor = preferences.useDynamicColor) {
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
