package com.rewordly.app.data.remote

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.EnglishWord
import com.rewordly.app.core.network.model.GeneratedExampleDto
import com.rewordly.app.core.network.model.GeneratedWordDto
import com.rewordly.app.core.network.model.GenerationResponseDto
import com.rewordly.app.core.network.model.UsageDto
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GeneratedExample
import com.rewordly.app.domain.model.GeneratedWord
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationResult
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.domain.model.WordKeys
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Turns an untrusted backend payload into domain models. A successful HTTP status says nothing about the quality
 * of AI output, so every item is validated here and invalid ones are dropped individually instead of failing
 * the whole generation.
 */
object GenerationResponseMapper {
    object Limits {
        const val MAX_WORD = 40
        const val MAX_TRANSLATION = 100
        const val MAX_PRONUNCIATION = 40
        const val MAX_DEFINITION = 300
        const val MAX_EXAMPLE = 250
        const val MAX_EXAMPLES = 3
        const val MAX_LIST_SIZE = 5
        const val MAX_LIST_ENTRY = 40
    }

    private val whitespace = Regex("\\s+")
    private val controlChars = Regex("[\\p{Cntrl}&&[^\\n\\t]]")
    private val cyrillic = Regex("\\p{IsCyrillic}")

    fun map(
        json: Json,
        dto: GenerationResponseDto,
        request: GenerationRequest,
        headerUsage: UsageInfo? = null,
    ): AppResult<GenerationResult> {
        val usage = mergeUsage(dto.usage?.toDomain(), headerUsage)
        val rawItems = dto.items.orEmpty()
        val suggestion = dto.suggestedCorrection?.let(::clean)?.takeIf(::isEnglishWord)

        if (rawItems.isEmpty()) {
            return if (suggestion != null) {
                AppResult.Success(GenerationResult(emptyList(), suggestion, usage))
            } else {
                AppResult.Failure(AppError.EmptyResponse)
            }
        }

        val excluded = request.exclude.map(WordKeys::normalize).toSet()
        val limit = if (request.mode == GenerationMode.WORD) 1 else request.settings.wordCount
        val seen = mutableSetOf<String>()
        var discarded = 0
        val accepted = mutableListOf<GeneratedWord>()
        for ((index, element) in rawItems.withIndex()) {
            // Anything past the limit is dropped regardless, so it is counted without being decoded and
            // validated field by field first.
            if (accepted.size >= limit) {
                discarded += rawItems.size - index
                break
            }
            val word = decode(json, element)?.let { validate(it, request) }
            when {
                word == null -> discarded++
                word.key in excluded || !seen.add(word.key) -> discarded++
                else -> accepted += word
            }
        }

        return when {
            accepted.isNotEmpty() -> AppResult.Success(GenerationResult(accepted, suggestion, usage, discarded))
            suggestion != null -> AppResult.Success(GenerationResult(emptyList(), suggestion, usage, discarded))
            else -> AppResult.Failure(AppError.InvalidResponse("no valid items"))
        }
    }

    /** Decodes one item, returning null for anything that is not an object with the expected field types. */
    private fun decode(json: Json, element: JsonElement): GeneratedWordDto? = try {
        json.decodeFromJsonElement(GeneratedWordDto.serializer(), element)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    fun validate(dto: GeneratedWordDto, request: GenerationRequest): GeneratedWord? {
        val settings = request.settings
        val word = dto.word?.let(::clean)?.takeIf { it.length <= Limits.MAX_WORD && EnglishWord.matches(it) }
            ?: return null
        val translation = dto.translation?.let(::clean)
            ?.takeIf { it.length <= Limits.MAX_TRANSLATION && cyrillic.containsMatchIn(it) }
            ?: return null
        val definition = dto.definition?.let(::clean)
            ?.takeIf { it.isNotEmpty() && it.length <= Limits.MAX_DEFINITION }
            ?: return null
        val definitionTranslation = dto.definitionTranslation?.let(::clean)
            ?.takeIf { it.length <= Limits.MAX_DEFINITION && cyrillic.containsMatchIn(it) }
            .orEmpty()
        val examples = if (settings.includeExamples) validExamples(dto.examples) else emptyList()
        // An example-based learning card without a single usable example is not worth saving.
        if (settings.includeExamples && examples.isEmpty()) return null

        return GeneratedWord(
            word = word,
            translation = translation,
            pronunciation = if (settings.includePronunciation) {
                dto.pronunciation?.let(::clean)?.takeIf { it.length <= Limits.MAX_PRONUNCIATION }.orEmpty()
            } else {
                ""
            },
            partOfSpeech = parsePartOfSpeech(dto.partOfSpeech),
            difficulty = parseDifficulty(dto.difficulty) ?: settings.level,
            definition = definition,
            definitionTranslation = definitionTranslation,
            examples = examples,
            synonyms = if (settings.includeSynonyms) cleanList(dto.synonyms, word) else emptyList(),
            relatedWords = cleanList(dto.relatedWords, word),
        )
    }

    fun parseDifficulty(raw: String?): Difficulty? =
        raw?.trim()?.uppercase()?.let { value -> Difficulty.entries.firstOrNull { it.name == value } }

    fun parsePartOfSpeech(raw: String?): PartOfSpeech {
        val value = raw?.trim()?.uppercase()?.replace(' ', '_') ?: return PartOfSpeech.PHRASE
        return PartOfSpeech.entries.firstOrNull { it.name == value } ?: PartOfSpeech.PHRASE
    }

    private fun validExamples(raw: List<GeneratedExampleDto>?): List<GeneratedExample> = raw.orEmpty()
        .mapNotNull { example ->
            val english = example.englishText?.let(
                ::clean,
            )?.takeIf { it.isNotEmpty() && it.length <= Limits.MAX_EXAMPLE }
            val russian = example.russianTranslation?.let(::clean)
                ?.takeIf { it.length <= Limits.MAX_EXAMPLE && cyrillic.containsMatchIn(it) }
            if (english != null && russian != null) GeneratedExample(english, russian) else null
        }
        .distinctBy { it.english.lowercase() }
        .take(Limits.MAX_EXAMPLES)

    private fun cleanList(raw: List<String>?, itself: String): List<String> = raw.orEmpty()
        .map(::clean)
        .filter { it.isNotEmpty() && it.length <= Limits.MAX_LIST_ENTRY && !it.equals(itself, ignoreCase = true) }
        .distinctBy { it.lowercase() }
        .take(Limits.MAX_LIST_SIZE)

    private fun isEnglishWord(value: String) = value.length <= Limits.MAX_WORD && EnglishWord.matches(value)

    private fun clean(value: String): String = value.replace(controlChars, "").replace(whitespace, " ").trim()

    private fun UsageDto.toDomain() = UsageInfo(
        remainingRequests = remainingRequests,
        dailyUsed = dailyUsed,
        dailyLimit = dailyLimit,
        monthlyUsed = monthlyUsed,
        monthlyLimit = monthlyLimit,
        retryAfterSeconds = retryAfterSeconds,
    )

    /** Body numbers win, headers fill the gaps. Returns null when nothing was reported. */
    fun mergeUsage(body: UsageInfo?, header: UsageInfo?): UsageInfo? {
        val merged = UsageInfo(
            remainingRequests = body?.remainingRequests ?: header?.remainingRequests,
            dailyUsed = body?.dailyUsed ?: header?.dailyUsed,
            dailyLimit = body?.dailyLimit ?: header?.dailyLimit,
            monthlyUsed = body?.monthlyUsed ?: header?.monthlyUsed,
            monthlyLimit = body?.monthlyLimit ?: header?.monthlyLimit,
            retryAfterSeconds = body?.retryAfterSeconds ?: header?.retryAfterSeconds,
        )
        return merged.takeUnless { it.isEmpty && it.retryAfterSeconds == null }
    }
}
