package com.rewordly.app.domain.model

enum class GenerationMode { TOPIC, TEXT, WORD }

/** Ready-made topics. A custom topic is any free text, so it is not part of this enum. */
enum class TopicPreset(val apiName: String) {
    TECHNOLOGY("Technology"),
    PROGRAMMING("Programming"),
    BUSINESS("Business"),
    TRAVEL("Travel"),
    SCIENCE("Science"),
    EVERYDAY("Everyday English"),
    MOVIES("Movies"),
    COMMUNICATION("Communication"),
    EDUCATION("Education"),
}

data class GenerationSettings(
    val level: Difficulty = Difficulty.B1,
    val wordCount: Int = DEFAULT_WORD_COUNT,
    val includeExamples: Boolean = true,
    val includeSynonyms: Boolean = true,
    val includePronunciation: Boolean = true,
) {
    companion object {
        const val DEFAULT_WORD_COUNT = 10
        val WORD_COUNT_OPTIONS = listOf(5, 10, 15, 20)
    }
}

sealed interface GenerationInput {
    data class Topic(val topic: String) : GenerationInput

    data class Text(val text: String) : GenerationInput

    data class SingleWord(val word: String) : GenerationInput
}

data class GenerationRequest(
    val input: GenerationInput,
    val settings: GenerationSettings,
    /** Words that must not come back again (already in the preview or already saved). */
    val exclude: List<String> = emptyList(),
) {
    val mode: GenerationMode
        get() = when (input) {
            is GenerationInput.Topic -> GenerationMode.TOPIC
            is GenerationInput.Text -> GenerationMode.TEXT
            is GenerationInput.SingleWord -> GenerationMode.WORD
        }
}

data class GeneratedExample(val english: String, val russian: String)

data class GeneratedWord(
    val word: String,
    val translation: String,
    val pronunciation: String,
    val partOfSpeech: PartOfSpeech,
    val difficulty: Difficulty,
    val definition: String,
    val definitionTranslation: String,
    val examples: List<GeneratedExample>,
    val synonyms: List<String>,
    val relatedWords: List<String>,
) {
    /** Case and whitespace insensitive identity used for duplicate detection. */
    val key: String get() = WordKeys.normalize(word)
}

object WordKeys {
    private val whitespace = Regex("\\s+")

    fun normalize(text: String): String = text.trim().replace(whitespace, " ").lowercase()
}

/** Usage limits reported by the backend. Every field is optional: show only what the backend verified. */
data class UsageInfo(
    val remainingRequests: Int? = null,
    val dailyUsed: Int? = null,
    val dailyLimit: Int? = null,
    val monthlyUsed: Int? = null,
    val monthlyLimit: Int? = null,
    val retryAfterSeconds: Int? = null,
) {
    val isEmpty: Boolean
        get() = remainingRequests == null && dailyUsed == null && dailyLimit == null &&
            monthlyUsed == null && monthlyLimit == null
}

data class GenerationResult(
    val items: List<GeneratedWord>,
    /** Spelling suggestion for a single word request. Never applied without the user's consent. */
    val suggestedCorrection: String? = null,
    val usage: UsageInfo? = null,
    /** Items the backend returned that failed validation and were discarded. */
    val discardedItems: Int = 0,
)

/** A generated word shown in the preview, with its relation to the local vocabulary. */
data class PreviewItem(
    val word: GeneratedWord,
    /** Id of the word that already exists locally, or null when the word is new. */
    val existingWordId: String?,
    val selected: Boolean,
) {
    val isDuplicate: Boolean get() = existingWordId != null
}

data class GenerationHistoryEntry(
    val id: String,
    val mode: GenerationMode,
    /** Topic or word. For pasted text only a neutral summary such as the length, never the text. */
    val description: String,
    val level: Difficulty,
    val requestedCount: Int,
    val resultCount: Int,
    val createdAt: Long,
    val hasResult: Boolean,
)

/** Result of saving previewed words. */
data class SaveSummary(val savedIds: List<String>, val skippedExistingIds: List<String>)

/** Stable local id for a saved generated word, derived from its normalized text. */
fun GeneratedWord.localId(): String = "ai-" + key.replace(' ', '-')

/** The vocabulary entry created when the user confirms saving this generated word. */
fun GeneratedWord.toWord(): Word {
    val id = localId()
    return Word(
        id = id,
        language = LearningLanguage.ENGLISH,
        text = word.trim(),
        translation = WordTranslation("ru", translation),
        pronunciation = pronunciation,
        partOfSpeech = partOfSpeech,
        difficulty = difficulty,
        definition = definition,
        definitionTranslation = definitionTranslation,
        examples = examples.mapIndexed { index, example ->
            WordExample("$id-ex-$index", id, example.english, example.russian)
        },
        synonyms = synonyms,
        relatedWords = relatedWords,
        source = WordSource.GENERATED,
    )
}
