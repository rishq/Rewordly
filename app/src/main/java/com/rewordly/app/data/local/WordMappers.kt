package com.rewordly.app.data.local

import com.rewordly.app.core.database.entity.DailyActivityEntity
import com.rewordly.app.core.database.entity.PopulatedWord
import com.rewordly.app.core.database.entity.ReviewLogEntity
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity
import com.rewordly.app.domain.model.DailyActivity
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.ReviewKind
import com.rewordly.app.domain.model.ReviewLogEntry
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordExample
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordSource
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordTranslation
import com.rewordly.app.domain.model.WordWithProgress
import java.time.LocalDate

fun PopulatedWord.toDomain(): WordWithProgress = WordWithProgress(
    word = Word(
        id = word.id,
        language = LearningLanguage.fromTag(word.language) ?: LearningLanguage.DEFAULT,
        text = word.text,
        translation = WordTranslation(word.translationLanguage, word.translation),
        pronunciation = word.pronunciation,
        partOfSpeech = enumValueOrDefault(word.partOfSpeech, PartOfSpeech.PHRASE),
        difficulty = enumValueOrDefault(word.difficulty, Difficulty.A1),
        definition = word.definition,
        definitionTranslation = word.definitionTranslation,
        examples = examples.sortedBy { it.position }.map {
            WordExample(id = it.id, wordId = it.wordId, text = it.text, translation = it.translation)
        },
        forms = word.forms,
        relatedWords = word.relatedWords,
        synonyms = word.synonyms,
        audioUrl = word.audioUrl,
        source = wordSourceOf(word.source),
        topic = word.topic,
    ),
    progress = progress?.toDomain() ?: WordProgress(wordId = word.id),
)

fun WordProgressEntity.toDomain(): WordProgress = WordProgress(
    wordId = wordId,
    status = enumValueOrDefault(status, WordStatus.NEW),
    isSaved = isSaved,
    views = views,
    correctAnswers = correctAnswers,
    incorrectAnswers = incorrectAnswers,
    lastViewedAt = lastViewedAt,
    lastReviewedAt = lastReviewedAt,
    nextReviewAt = nextReviewAt,
    repetitionCount = repetitionCount,
    easeFactor = easeFactor,
    intervalDays = intervalDays,
    consecutiveCorrect = consecutiveCorrect,
    consecutiveIncorrect = consecutiveIncorrect,
)

fun WordProgress.toEntity(updatedAt: Long): WordProgressEntity = WordProgressEntity(
    wordId = wordId,
    status = status.name,
    isSaved = isSaved,
    views = views,
    correctAnswers = correctAnswers,
    incorrectAnswers = incorrectAnswers,
    lastViewedAt = lastViewedAt,
    lastReviewedAt = lastReviewedAt,
    nextReviewAt = nextReviewAt,
    repetitionCount = repetitionCount,
    easeFactor = easeFactor,
    intervalDays = intervalDays,
    consecutiveCorrect = consecutiveCorrect,
    consecutiveIncorrect = consecutiveIncorrect,
    updatedAt = updatedAt,
)

fun Word.toEntity(createdAt: Long): WordEntity = WordEntity(
    id = id,
    language = language.tag,
    text = text,
    translation = translation.text,
    translationLanguage = translation.languageCode,
    pronunciation = pronunciation,
    partOfSpeech = partOfSpeech.name,
    difficulty = difficulty.name,
    definition = definition,
    definitionTranslation = definitionTranslation,
    forms = forms,
    relatedWords = relatedWords,
    synonyms = synonyms,
    audioUrl = audioUrl,
    source = source.name,
    topic = topic,
    createdAt = createdAt,
)

fun Word.exampleEntities(): List<WordExampleEntity> = examples.mapIndexed { index, example ->
    WordExampleEntity(
        id = example.id,
        wordId = id,
        text = example.text,
        translation = example.translation,
        position = index,
    )
}

fun LocalDate.toStorageKey(): String = toString()

fun ReviewLogEntity.toDomain(): ReviewLogEntry = ReviewLogEntry(
    wordId = wordId,
    kind = ReviewKind.entries.firstOrNull { it.name == kind } ?: ReviewKind.REVIEW,
    quality = quality,
    reviewedAt = reviewedAt,
    durationMs = durationMs,
)

fun DailyActivityEntity.toDomain(): DailyActivity = DailyActivity(
    date = LocalDate.parse(day),
    wordsLearned = wordsLearned,
    wordsReviewed = wordsReviewed,
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

/** Unknown values fall back to BUNDLED so a row written by a future version stays readable. */
private fun wordSourceOf(name: String): WordSource =
    WordSource.entries.firstOrNull { it.name == name } ?: WordSource.BUNDLED
