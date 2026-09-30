package com.rewordly.app.core.ui

import androidx.annotation.StringRes
import com.rewordly.app.R
import com.rewordly.app.core.common.AppError
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.ThemeMode

@get:StringRes
val PartOfSpeech.labelRes: Int
    get() = when (this) {
        PartOfSpeech.NOUN -> R.string.pos_noun
        PartOfSpeech.VERB -> R.string.pos_verb
        PartOfSpeech.ADJECTIVE -> R.string.pos_adjective
        PartOfSpeech.ADVERB -> R.string.pos_adverb
        PartOfSpeech.PRONOUN -> R.string.pos_pronoun
        PartOfSpeech.PREPOSITION -> R.string.pos_preposition
        PartOfSpeech.CONJUNCTION -> R.string.pos_conjunction
        PartOfSpeech.INTERJECTION -> R.string.pos_interjection
        PartOfSpeech.PHRASE -> R.string.pos_phrase
    }

@get:StringRes
val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.A1 -> R.string.difficulty_a1
        Difficulty.A2 -> R.string.difficulty_a2
        Difficulty.B1 -> R.string.difficulty_b1
        Difficulty.B2 -> R.string.difficulty_b2
        Difficulty.C1 -> R.string.difficulty_c1
        Difficulty.C2 -> R.string.difficulty_c2
    }

@get:StringRes
val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

@get:StringRes
val InterfaceLanguage.labelRes: Int
    get() = when (this) {
        InterfaceLanguage.RUSSIAN -> R.string.language_russian
        InterfaceLanguage.ENGLISH -> R.string.language_english
    }

@get:StringRes
val LearningLanguage.labelRes: Int
    get() = when (this) {
        LearningLanguage.ENGLISH -> R.string.learning_language_english
    }

@get:StringRes
val AppError.messageRes: Int
    get() = when (this) {
        is AppError.Network -> R.string.error_network
        is AppError.Timeout -> R.string.error_timeout
        is AppError.Server -> R.string.error_server
        is AppError.Unauthorized -> R.string.error_server
        AppError.EmptyResponse -> R.string.error_empty_response
        is AppError.Database -> R.string.error_database
        is AppError.Unknown -> R.string.error_unknown
    }
