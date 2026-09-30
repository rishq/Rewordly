package com.rewordly.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rewordly.app.core.common.AppLocaleManager
import com.rewordly.app.core.ui.theme.RewordlyTheme
import com.rewordly.app.core.ui.theme.isDark
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.ui.RewordlyApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
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
                RewordlyApp()
            }
        }
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
