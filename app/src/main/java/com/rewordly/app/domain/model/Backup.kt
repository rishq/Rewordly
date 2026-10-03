package com.rewordly.app.domain.model

import kotlinx.serialization.Serializable

/**
 * A full local backup: vocabulary, examples, learning progress, spaced-repetition scheduling and the
 * preferences that matter for learning. Everything is plain JSON so a backup stays readable and can be
 * diffed by a human.
 *
 * [schemaVersion] is what makes a future format change detectable: a backup written by a newer app
 * version is refused instead of being half-understood.
 *
 * [schemaVersion] and [words] deliberately have no default value. The app writes files with
 * `encodeDefaults = false`, which omits any property that equals its default - a backup of an empty
 * vocabulary would then lose its `words` key entirely and stop being recognisable as a backup.
 */
@Serializable
data class VocabularyBackup(
    val schemaVersion: Int,
    val createdAt: Long = 0,
    val appVersion: String = "",
    val words: List<BackupWord>,
    val progress: List<BackupProgress> = emptyList(),
    val reviewLog: List<BackupReviewLog> = emptyList(),
    val activity: List<BackupDay> = emptyList(),
    val settings: BackupSettings? = null,
) {
    companion object {
        /** Bump only together with a migration that keeps older backups readable. */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupWord(
    val id: String,
    val language: String = "en",
    val text: String,
    val translation: String,
    val translationLanguage: String = "ru",
    val pronunciation: String = "",
    val partOfSpeech: String = "",
    val difficulty: String = "",
    val definition: String = "",
    val definitionTranslation: String = "",
    val topic: String = "",
    val examples: List<BackupExample> = emptyList(),
    val forms: List<String> = emptyList(),
    val relatedWords: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val source: String = "BUNDLED",
    val createdAt: Long = 0,
)

@Serializable
data class BackupExample(
    val id: String = "",
    val text: String = "",
    val translation: String = "",
    val position: Int = 0,
)

/** Mirrors `word_progress`, including every spaced-repetition field. */
@Serializable
data class BackupProgress(
    val wordId: String,
    val status: String = "NEW",
    val isSaved: Boolean = false,
    val views: Int = 0,
    val correctAnswers: Int = 0,
    val incorrectAnswers: Int = 0,
    val lastViewedAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long? = null,
    val updatedAt: Long = 0,
    val repetitionCount: Int = 0,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val consecutiveCorrect: Int = 0,
    val consecutiveIncorrect: Int = 0,
)

@Serializable
data class BackupReviewLog(
    val wordId: String,
    val sessionId: String,
    val kind: String = "REVIEW",
    val quality: Int = 0,
    val reviewedAt: Long = 0,
    val durationMs: Long? = null,
    val intervalBefore: Int = 0,
    val intervalAfter: Int = 0,
)

@Serializable
data class BackupDay(val day: String, val wordsLearned: Int = 0, val wordsReviewed: Int = 0)

/** Only the preferences that shape learning; unrelated flags stay on the device. */
@Serializable
data class BackupSettings(
    val interfaceLanguage: String = "",
    val themeMode: String = "",
    val dailyGoal: Int = 0,
    val notificationsEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val level: String? = null,
    val learningGoal: String = "",
    val interests: List<String> = emptyList(),
    val sessionLength: Int = 0,
)

/** Why a backup file was refused. Mapped to a localized message by the UI. */
enum class BackupIssue { UNREADABLE, NOT_A_BACKUP, UNSUPPORTED_VERSION, EMPTY, TOO_LARGE }

/** Outcome of reading a backup file. */
sealed interface BackupDecodeResult {
    data class Valid(val backup: VocabularyBackup) : BackupDecodeResult

    data class Invalid(val issue: BackupIssue) : BackupDecodeResult
}
