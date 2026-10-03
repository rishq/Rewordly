package com.rewordly.app.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.rewordly.app.domain.model.LearningLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local [TextToSpeech] implementation. All calls are one-way binder calls on top of an
 * asynchronous init, so nothing here blocks the main thread.
 */
@Singleton
class AndroidTtsPronunciationEngine @Inject constructor(
    @ApplicationContext context: Context,
) : PronunciationEngine {
    private val ready = CompletableDeferred<Unit>()
    private val _speakingText = MutableStateFlow<String?>(null)
    override val speakingText: StateFlow<String?> = _speakingText.asStateFlow()
    private val tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready.complete(Unit)
            } else {
                ready.completeExceptionally(IllegalStateException("TextToSpeech init failed: $status"))
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) = finish(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
        })
    }

    override suspend fun speak(text: String, language: LearningLanguage): Boolean {
        if (text.isBlank()) return false
        return runCatching {
            ready.await()
            val result = tts.setLanguage(Locale.forLanguageTag(language.tag))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false
            _speakingText.value = text
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
            true
        }.getOrDefault(false)
    }

    override fun stop() {
        tts.stop()
        _speakingText.value = null
    }

    private fun finish(utteranceId: String?) {
        if (utteranceId != null && utteranceId == _speakingText.value) {
            _speakingText.value = null
        }
    }
}
