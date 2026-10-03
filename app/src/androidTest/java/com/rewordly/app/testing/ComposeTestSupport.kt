package com.rewordly.app.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.rewordly.app.core.audio.LocalPronunciationEngine
import com.rewordly.app.core.audio.PronunciationEngine
import com.rewordly.app.core.ui.theme.RewordlyTheme
import com.rewordly.app.domain.model.LearningLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePronunciationEngine : PronunciationEngine {
    private val _speakingText = MutableStateFlow<String?>(null)
    override val speakingText: StateFlow<String?> = _speakingText.asStateFlow()
    val spoken = mutableListOf<String>()

    override suspend fun speak(text: String, language: LearningLanguage): Boolean {
        spoken += text
        _speakingText.value = text
        return true
    }

    override fun stop() {
        _speakingText.value = null
    }
}

/** Wraps content in the app theme and a fake speech engine so components can be tested in isolation. */
fun ComposeContentTestRule.setRewordlyContent(content: @Composable () -> Unit) {
    setContent {
        RewordlyTheme {
            CompositionLocalProvider(LocalPronunciationEngine provides FakePronunciationEngine()) {
                content()
            }
        }
    }
}

/** Resolves a string from the app resources so assertions work in any interface language. */
fun uiString(id: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

/** Resolves a plural string from the app resources. */
fun uiPlural(id: Int, quantity: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(id, quantity, *args)
