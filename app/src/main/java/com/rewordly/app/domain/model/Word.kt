package com.rewordly.app.domain.model

data class Word(
    val id: String,
    val language: LearningLanguage,
    val text: String,
    val translation: WordTranslation,
    val pronunciation: String,
    val partOfSpeech: PartOfSpeech,
    val difficulty: Difficulty,
    val examples: List<WordExample> = emptyList(),
    val forms: List<String> = emptyList(),
    val relatedWords: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val audioUrl: String? = null,
)

/** Translation of a word into the learner's native language (e.g. "ru"). */
data class WordTranslation(
    val languageCode: String,
    val text: String,
)

data class WordExample(
    val id: String,
    val wordId: String,
    val text: String,
    val translation: String,
)

enum class PartOfSpeech { NOUN, VERB, ADJECTIVE, ADVERB, PRONOUN, PREPOSITION, CONJUNCTION, INTERJECTION, PHRASE }

/** CEFR-style difficulty level. */
enum class Difficulty { A1, A2, B1, B2, C1, C2 }
