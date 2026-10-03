package com.rewordly.app.core.ui

import androidx.annotation.StringRes
import com.rewordly.app.R
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.FileProblem
import com.rewordly.app.domain.model.BackupIssue
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.DifficultyFilter
import com.rewordly.app.domain.model.DuplicateKind
import com.rewordly.app.domain.model.ImportIssueReason
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningGoal
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.RecommendationReason
import com.rewordly.app.domain.model.StatusFilter
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.TopicPreset

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
val DifficultyFilter.filterRes: Int
    get() = when (this) {
        DifficultyFilter.ALL -> R.string.filter_all
        DifficultyFilter.A1 -> R.string.filter_a1
        DifficultyFilter.A2 -> R.string.filter_a2
        DifficultyFilter.B1 -> R.string.filter_b1
        DifficultyFilter.B2 -> R.string.filter_b2
    }

@get:StringRes
val StatusFilter.filterRes: Int
    get() = when (this) {
        StatusFilter.ALL -> R.string.filter_all
        StatusFilter.NEW -> R.string.status_new
        StatusFilter.LEARNING -> R.string.status_learning
        StatusFilter.LEARNED -> R.string.status_learned
        StatusFilter.SAVED -> R.string.status_saved
    }

@get:StringRes
val TopicPreset.labelRes: Int
    get() = when (this) {
        TopicPreset.TECHNOLOGY -> R.string.ai_topic_technology
        TopicPreset.PROGRAMMING -> R.string.ai_topic_programming
        TopicPreset.BUSINESS -> R.string.ai_topic_business
        TopicPreset.TRAVEL -> R.string.ai_topic_travel
        TopicPreset.SCIENCE -> R.string.ai_topic_science
        TopicPreset.EVERYDAY -> R.string.ai_topic_everyday
        TopicPreset.MOVIES -> R.string.ai_topic_movies
        TopicPreset.COMMUNICATION -> R.string.ai_topic_communication
        TopicPreset.EDUCATION -> R.string.ai_topic_education
    }

@get:StringRes
val LearningGoal.labelRes: Int
    get() = when (this) {
        LearningGoal.CASUAL -> R.string.goal_casual
        LearningGoal.TRAVEL -> R.string.goal_travel
        LearningGoal.WORK -> R.string.goal_work
        LearningGoal.ACADEMIC -> R.string.goal_academic
    }

@get:StringRes
val RecommendationReason.labelRes: Int
    get() = when (this) {
        RecommendationReason.DUE_FOR_REVIEW -> R.string.reason_due_for_review
        RecommendationReason.OFTEN_MISTAKEN -> R.string.reason_often_mistaken
        RecommendationReason.DIFFICULT -> R.string.reason_difficult
        RecommendationReason.MATCHES_LEVEL -> R.string.reason_matches_level
        RecommendationReason.MATCHES_INTERESTS -> R.string.reason_matches_interests
        RecommendationReason.NEW_FOR_YOU -> R.string.reason_new_for_you
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
        is AppError.RateLimited -> R.string.error_rate_limited
        is AppError.InvalidResponse -> R.string.error_invalid_response
        AppError.BackendNotConfigured -> R.string.error_backend_not_configured
        AppError.AiNotConfigured -> R.string.error_ai_not_configured
        is AppError.InvalidApiKey -> R.string.error_invalid_api_key
        is AppError.ProviderRejected -> R.string.error_provider_rejected
        AppError.Offline -> R.string.error_offline
        is AppError.FileAccess -> when (this.problem) {
            FileProblem.UNREADABLE -> R.string.error_file_unreadable
            FileProblem.TOO_LARGE -> R.string.error_file_too_large
            FileProblem.WRITE_FAILED -> R.string.error_file_write_failed
        }
    }

/** Why a backup file could not be used. Shown instead of an [AppError]: the file was readable. */
@get:StringRes
val BackupIssue.messageRes: Int
    get() = when (this) {
        BackupIssue.UNREADABLE -> R.string.data_backup_unreadable
        BackupIssue.NOT_A_BACKUP -> R.string.data_backup_not_a_backup
        BackupIssue.UNSUPPORTED_VERSION -> R.string.data_backup_unsupported_version
        BackupIssue.EMPTY -> R.string.data_backup_empty
        BackupIssue.TOO_LARGE -> R.string.data_backup_too_large
    }

/** Why a row of an imported file was skipped. */
@get:StringRes
val ImportIssueReason.messageRes: Int
    get() = when (this) {
        ImportIssueReason.MISSING_WORD -> R.string.data_issue_missing_word
        ImportIssueReason.MISSING_TRANSLATION -> R.string.data_issue_missing_translation
        ImportIssueReason.MALFORMED_ROW -> R.string.data_issue_malformed_row
        ImportIssueReason.INVALID_FORMAT -> R.string.data_issue_invalid_format
        ImportIssueReason.UNSUPPORTED_VERSION -> R.string.data_issue_unsupported_version
        ImportIssueReason.TOO_LONG -> R.string.data_issue_too_long
        ImportIssueReason.LIMIT_EXCEEDED -> R.string.data_issue_limit_exceeded
    }

/** Where an imported word already appears. Null for [DuplicateKind.NONE], which is never shown. */
@get:StringRes
val DuplicateKind.messageRes: Int?
    get() = when (this) {
        DuplicateKind.NONE -> null
        DuplicateKind.WITHIN_FILE -> R.string.data_duplicate_within_file
        DuplicateKind.EXISTING -> R.string.data_duplicate_existing
    }
