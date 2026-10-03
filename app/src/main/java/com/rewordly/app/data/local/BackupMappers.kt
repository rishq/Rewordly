package com.rewordly.app.data.local

import com.rewordly.app.core.database.entity.DailyActivityEntity
import com.rewordly.app.core.database.entity.ReviewLogEntity
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity
import com.rewordly.app.domain.model.BackupDay
import com.rewordly.app.domain.model.BackupExample
import com.rewordly.app.domain.model.BackupProgress
import com.rewordly.app.domain.model.BackupReviewLog
import com.rewordly.app.domain.model.BackupSettings
import com.rewordly.app.domain.model.BackupWord
import com.rewordly.app.domain.model.UserSettings

/**
 * Conversions between the Room rows and the backup file.
 *
 * Review log rows deliberately lose their auto-generated id: restoring a backup into a database that
 * already holds history must not collide on the primary key, and the unique (session, word, kind)
 * index is what actually keeps the history free of duplicates.
 */
fun WordEntity.toBackup(): BackupWord = BackupWord(
    id = id,
    language = language,
    text = text,
    translation = translation,
    translationLanguage = translationLanguage,
    pronunciation = pronunciation,
    partOfSpeech = partOfSpeech,
    difficulty = difficulty,
    definition = definition,
    definitionTranslation = definitionTranslation,
    topic = topic,
    forms = forms,
    relatedWords = relatedWords,
    synonyms = synonyms,
    source = source,
    createdAt = createdAt,
)

fun BackupWord.toEntity(): WordEntity = WordEntity(
    id = id,
    language = language,
    text = text,
    translation = translation,
    translationLanguage = translationLanguage,
    pronunciation = pronunciation,
    partOfSpeech = partOfSpeech,
    difficulty = difficulty,
    definition = definition,
    definitionTranslation = definitionTranslation,
    forms = forms,
    relatedWords = relatedWords,
    synonyms = synonyms,
    audioUrl = null,
    source = source,
    topic = topic,
    createdAt = createdAt,
)

/** Example sentences travel inside their word, so they need no separate lookup when restoring. */
fun WordExampleEntity.toBackup(): BackupExample = BackupExample(
    id = id,
    text = text,
    translation = translation,
    position = position,
)

fun WordProgressEntity.toBackup(): BackupProgress = BackupProgress(
    wordId = wordId,
    status = status,
    isSaved = isSaved,
    views = views,
    correctAnswers = correctAnswers,
    incorrectAnswers = incorrectAnswers,
    lastViewedAt = lastViewedAt,
    lastReviewedAt = lastReviewedAt,
    nextReviewAt = nextReviewAt,
    updatedAt = updatedAt,
    repetitionCount = repetitionCount,
    easeFactor = easeFactor,
    intervalDays = intervalDays,
    consecutiveCorrect = consecutiveCorrect,
    consecutiveIncorrect = consecutiveIncorrect,
)

fun BackupProgress.toEntity(): WordProgressEntity = WordProgressEntity(
    wordId = wordId,
    status = status,
    isSaved = isSaved,
    views = views,
    correctAnswers = correctAnswers,
    incorrectAnswers = incorrectAnswers,
    lastViewedAt = lastViewedAt,
    lastReviewedAt = lastReviewedAt,
    nextReviewAt = nextReviewAt,
    updatedAt = updatedAt,
    repetitionCount = repetitionCount,
    easeFactor = easeFactor,
    intervalDays = intervalDays,
    consecutiveCorrect = consecutiveCorrect,
    consecutiveIncorrect = consecutiveIncorrect,
)

fun ReviewLogEntity.toBackup(): BackupReviewLog = BackupReviewLog(
    wordId = wordId,
    sessionId = sessionId,
    kind = kind,
    quality = quality,
    reviewedAt = reviewedAt,
    durationMs = durationMs,
    intervalBefore = intervalBefore,
    intervalAfter = intervalAfter,
)

fun BackupReviewLog.toEntity(): ReviewLogEntity = ReviewLogEntity(
    wordId = wordId,
    sessionId = sessionId,
    kind = kind,
    quality = quality,
    reviewedAt = reviewedAt,
    durationMs = durationMs,
    intervalBefore = intervalBefore,
    intervalAfter = intervalAfter,
)

fun DailyActivityEntity.toBackup(): BackupDay = BackupDay(
    day = day,
    wordsLearned = wordsLearned,
    wordsReviewed = wordsReviewed,
)

fun BackupDay.toEntity(): DailyActivityEntity = DailyActivityEntity(
    day = day,
    wordsLearned = wordsLearned,
    wordsReviewed = wordsReviewed,
)

/** Only preferences that shape learning or the interface; nothing device specific is invented. */
fun UserSettings.toBackup(): BackupSettings = BackupSettings(
    interfaceLanguage = interfaceLanguage.tag,
    themeMode = themeMode.name,
    dailyGoal = dailyGoal,
    notificationsEnabled = notificationsEnabled,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    level = level?.name,
    learningGoal = learningGoal.name,
    interests = interests.map { it.name },
    sessionLength = sessionLength,
)
