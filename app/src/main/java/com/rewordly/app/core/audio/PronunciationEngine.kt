package com.rewordly.app.core.audio

import androidx.compose.runtime.staticCompositionLocalOf
import com.rewordly.app.domain.model.LearningLanguage
import kotlinx.coroutines.flow.StateFlow

/**
 * Speaks learning content. Implemented by Android TTS for now; a remote pronunciation
 * provider can replace it later without touching the UI.
 */
interface PronunciationEngine {
    /** Text currently being spoken, or null while idle. */
    val speakingText: StateFlow<String?>

    /** Speaks [text] and interrupts anything already playing. Never blocks the calling thread. */
    suspend fun speak(text: String, language: LearningLanguage): Boolean

    fun stop()
}

val LocalPronunciationEngine = staticCompositionLocalOf<PronunciationEngine> {
    error("PronunciationEngine not provided. Wrap the app in CompositionLocalProvider(LocalPronunciationEngine ...).")
}
