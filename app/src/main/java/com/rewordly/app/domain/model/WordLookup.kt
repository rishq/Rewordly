package com.rewordly.app.domain.model

/**
 * What the free public dictionaries know about a word the user typed.
 *
 * These sources are community maintained, keyless and imperfect: no single one has everything, and a
 * missing transcription is normal rather than a failure. A missing *translation* is different — a card
 * without one teaches nothing — so a word nobody could translate is [NotFound], not a half-empty [Found].
 */
sealed interface WordLookup {
    data class Found(
        val word: String,
        val translation: String,
        val transcription: String = "",
        val partOfSpeech: PartOfSpeech? = null,
        val definition: String = "",
        val examples: List<String> = emptyList(),
    ) : WordLookup

    /** No source knew the word, or none of them had a translation for it. */
    data object NotFound : WordLookup
}

/** Stable local id for a looked-up word, derived from its normalized text. */
fun WordLookup.Found.localId(): String = LOOKUP_ID_PREFIX + WordKeys.normalize(word).replace(' ', '-')

/**
 * The vocabulary entry created when the user adds a looked-up word.
 *
 * No free source reports a CEFR level, so the app's default level is used instead of guessing one from
 * the word's length or the definition's wording. The transcription is stored as the source spelled it,
 * and an empty one stays empty: the card simply shows no pronunciation rather than a made-up one.
 */
fun WordLookup.Found.toWord(): Word {
    val id = localId()
    return Word(
        id = id,
        language = LearningLanguage.ENGLISH,
        text = word.trim(),
        translation = WordTranslation(TRANSLATION_LANGUAGE, translation),
        pronunciation = transcription,
        partOfSpeech = partOfSpeech ?: PartOfSpeech.PHRASE,
        difficulty = DEFAULT_DIFFICULTY,
        definition = definition,
        // The free sources return English sentences only; a translated example is not something they offer.
        examples = examples.mapIndexed { index, sentence -> WordExample("$id-ex-$index", id, sentence, "") },
        source = WordSource.LOOKUP,
    )
}

private const val LOOKUP_ID_PREFIX = "dict-"

/** Mirrors the AI path, which also teaches into Russian. */
private const val TRANSLATION_LANGUAGE = "ru"

private val DEFAULT_DIFFICULTY = Difficulty.B1
