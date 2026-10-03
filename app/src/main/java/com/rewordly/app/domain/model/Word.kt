package com.rewordly.app.domain.model

data class Word(
    val id: String,
    val language: LearningLanguage,
    val text: String,
    val translation: WordTranslation,
    val pronunciation: String,
    val partOfSpeech: PartOfSpeech,
    val difficulty: Difficulty,
    val definition: String = "",
    val definitionTranslation: String = "",
    val examples: List<WordExample> = emptyList(),
    val forms: List<String> = emptyList(),
    val relatedWords: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val audioUrl: String? = null,
    val source: WordSource = WordSource.BUNDLED,
    /**
     * Comma-separated topic names, as read from an imported file. Empty for bundled and generated
     * words, which are categorized by [com.rewordly.app.domain.service.TopicTaxonomy] instead.
     */
    val topic: String = "",
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

/** Where a word came from: shipped with the app, saved from an AI generation, or read from a file. */
enum class WordSource { BUNDLED, GENERATED, IMPORTED }
