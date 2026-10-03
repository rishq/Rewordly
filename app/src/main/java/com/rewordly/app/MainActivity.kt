package com.rewordly.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rewordly.app.core.audio.LocalPronunciationEngine
import com.rewordly.app.core.audio.PronunciationEngineEntryPoint
import com.rewordly.app.core.common.AppLocaleManager
import com.rewordly.app.core.navigation.NotificationDestination
import com.rewordly.app.core.notifications.NotificationNavigationBus
import com.rewordly.app.core.ui.theme.RewordlyTheme
import com.rewordly.app.core.ui.theme.isDark
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.ui.RewordlyApp
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var notificationNavigation: NotificationNavigationBus

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Injected by super.onCreate(), so the intent can be handed over right away.
        consumeNotificationIntent(intent)
        // Keep the system splash until preferences are loaded so theme/language never flicker.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .filterIsInstance<MainUiState.Ready>()
                    .map { it.settings.interfaceLanguage }
                    .distinctUntilChanged()
                    .collect(AppLocaleManager::apply)
            }
        }

        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val themeMode = (uiState as? MainUiState.Ready)?.settings?.themeMode ?: ThemeMode.SYSTEM
            val darkTheme = themeMode.isDark()

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            RewordlyTheme(darkTheme = darkTheme) {
                val pronunciationEngine = EntryPointAccessors
                    .fromApplication(applicationContext, PronunciationEngineEntryPoint::class.java)
                    .pronunciationEngine()
                CompositionLocalProvider(LocalPronunciationEngine provides pronunciationEngine) {
                    RewordlyApp(notificationNavigation = notificationNavigation)
                }
            }
        }
    }

    /**
     * Handles a tap on a notification while the app is already running. The activity is `singleTop`,
     * so this reuses the existing instance instead of stacking a second one.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
    }

    private fun consumeNotificationIntent(intent: Intent?) {
        val destination = NotificationDestination.fromExtra(intent?.getStringExtra(NotificationDestination.EXTRA))
        if (destination != null) notificationNavigation.request(destination)
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
