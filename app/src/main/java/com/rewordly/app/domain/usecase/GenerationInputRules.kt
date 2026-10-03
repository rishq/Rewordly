package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.EnglishWord
import com.rewordly.app.domain.model.GenerationInput

enum class InputError { EMPTY, TOO_SHORT, TOO_LONG, INVALID }

/** Client side checks that run before anything is sent to the backend. */
object GenerationInputRules {
    const val MAX_TOPIC = 60
    const val MIN_TEXT = 40
    const val MAX_TEXT = 5_000
    const val MAX_WORD = 40

    fun topicError(topic: String): InputError? = when {
        topic.isBlank() -> InputError.EMPTY
        topic.trim().length > MAX_TOPIC -> InputError.TOO_LONG
        else -> null
    }

    fun textError(text: String): InputError? = when {
        text.isBlank() -> InputError.EMPTY
        text.trim().length < MIN_TEXT -> InputError.TOO_SHORT
        text.length > MAX_TEXT -> InputError.TOO_LONG
        else -> null
    }

    fun wordError(word: String): InputError? = when {
        word.isBlank() -> InputError.EMPTY
        word.trim().length > MAX_WORD -> InputError.TOO_LONG
        !EnglishWord.matches(word.trim()) -> InputError.INVALID
        else -> null
    }

    fun error(input: GenerationInput): InputError? = when (input) {
        is GenerationInput.Topic -> topicError(input.topic)
        is GenerationInput.Text -> textError(input.text)
        is GenerationInput.SingleWord -> wordError(input.word)
    }
}
