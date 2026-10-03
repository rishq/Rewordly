package com.rewordly.app.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Options shared by every generation endpoint. */
@Serializable
data class GenerationOptionsDto(
    @SerialName("include_examples") val includeExamples: Boolean,
    @SerialName("include_synonyms") val includeSynonyms: Boolean,
    @SerialName("include_pronunciation") val includePronunciation: Boolean,
)

@Serializable
data class TopicRequestDto(
    @SerialName("topic") val topic: String,
    @SerialName("level") val level: String,
    @SerialName("count") val count: Int,
    @SerialName("exclude") val exclude: List<String>,
    @SerialName("options") val options: GenerationOptionsDto,
    @SerialName("translation_language") val translationLanguage: String = "ru",
)

@Serializable
data class TextRequestDto(
    @SerialName("text") val text: String,
    @SerialName("level") val level: String,
    @SerialName("count") val count: Int,
    @SerialName("exclude") val exclude: List<String>,
    @SerialName("options") val options: GenerationOptionsDto,
    @SerialName("translation_language") val translationLanguage: String = "ru",
)

@Serializable
data class WordRequestDto(
    @SerialName("word") val word: String,
    @SerialName("level") val level: String,
    @SerialName("options") val options: GenerationOptionsDto,
    @SerialName("translation_language") val translationLanguage: String = "ru",
)

/**
 * Every field is nullable and items arrive as raw [JsonElement]s: the backend relays model output, so a single
 * odd item must not discard the rest. Real validation happens in the mapper, never in the deserializer.
 */
@Serializable
data class GenerationResponseDto(
    @SerialName("items") val items: List<JsonElement>? = null,
    @SerialName("suggested_correction") val suggestedCorrection: String? = null,
    @SerialName("usage") val usage: UsageDto? = null,
)

@Serializable
data class GeneratedWordDto(
    @SerialName("word") val word: String? = null,
    @SerialName("translation") val translation: String? = null,
    @SerialName("pronunciation") val pronunciation: String? = null,
    @SerialName("part_of_speech") val partOfSpeech: String? = null,
    @SerialName("difficulty") val difficulty: String? = null,
    @SerialName("definition") val definition: String? = null,
    @SerialName("definition_translation") val definitionTranslation: String? = null,
    @SerialName("examples") val examples: List<GeneratedExampleDto>? = null,
    @SerialName("synonyms") val synonyms: List<String>? = null,
    @SerialName("related_words") val relatedWords: List<String>? = null,
)

@Serializable
data class GeneratedExampleDto(
    @SerialName("english_text") val englishText: String? = null,
    @SerialName("russian_translation") val russianTranslation: String? = null,
)

@Serializable
data class UsageDto(
    @SerialName("remaining_requests") val remainingRequests: Int? = null,
    @SerialName("daily_used") val dailyUsed: Int? = null,
    @SerialName("daily_limit") val dailyLimit: Int? = null,
    @SerialName("monthly_used") val monthlyUsed: Int? = null,
    @SerialName("monthly_limit") val monthlyLimit: Int? = null,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int? = null,
)

/** Body of non-2xx answers: `{"error": {"code": "...", "message": "...", "retry_after_seconds": 30}}`. */
@Serializable
data class ErrorEnvelopeDto(@SerialName("error") val error: ErrorBodyDto? = null)

@Serializable
data class ErrorBodyDto(
    @SerialName("code") val code: String? = null,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int? = null,
)
