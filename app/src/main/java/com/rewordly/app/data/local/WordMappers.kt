package com.rewordly.app.data.local

import com.rewordly.app.core.database.entity.PopulatedWord
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordExample
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordTranslation
import com.rewordly.app.domain.model.WordWithProgress

fun PopulatedWord.toDomain(): WordWithProgress = WordWithProgress(
    word = Word(
        id = word.id,
        language = LearningLanguage.fromTag(word.language) ?: LearningLanguage.DEFAULT,
        text = word.text,
        translation = WordTranslation(word.translationLanguage, word.translation),
        pronunciation = word.pronunciation,
        partOfSpeech = enumValueOrDefault(word.partOfSpeech, PartOfSpeech.PHRASE),
        difficulty = enumValueOrDefault(word.difficulty, Difficulty.A1),
        examples = examples.sortedBy { it.position }.map {
            WordExample(id = it.id, wordId = it.wordId, text = it.text, translation = it.translation)
        },
        forms = word.forms,
        relatedWords = word.relatedWords,
        synonyms = word.synonyms,
        audioUrl = word.audioUrl,
    ),
    progress = progress?.toDomain() ?: WordProgress(wordId = word.id),
)

fun WordProgressEntity.toDomain(): WordProgress = WordProgress(
    wordId = wordId,
    status = enumValueOrDefault(status, WordStatus.NEW),
    isSaved = isSaved,
    correctAnswers = correctAnswers,
    incorrectAnswers = incorrectAnswers,
    lastReviewedAt = lastReviewedAt,
    nextReviewAt = nextReviewAt,
)

fun WordProgress.toEntity(updatedAt: Long): WordProgressEntity = WordProgressEntity(
    wordId = wordId,
    status = status.name,
    isSaved = isSaved,
    correctAnswers = correctAnswers,
    incorrectAnswers = incorrectAnswers,
    lastReviewedAt = lastReviewedAt,
    nextReviewAt = nextReviewAt,
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
    forms = forms,
    relatedWords = relatedWords,
    synonyms = synonyms,
    audioUrl = audioUrl,
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

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
