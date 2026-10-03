package com.rewordly.app.data.remote

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.model.GenerationResponseDto
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GenerationInput
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationResult
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.UsageInfo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GenerationResponseMapperTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun item(
        word: String? = "refactor",
        translation: String? = "рефакторить",
        difficulty: String? = "B2",
        definition: String? = "To restructure code without changing what it does.",
        example: String? = "We refactor the module before adding features.",
        exampleRu: String? = "Мы рефакторим модуль перед добавлением функций.",
        extra: String = "",
    ): String {
        fun field(name: String, value: String?) = value?.let { "\"$name\": \"$it\"," }.orEmpty()
        return """{ ${field("word", word)} ${field("translation", translation)} ${field("difficulty", difficulty)}
            ${field("definition", definition)} "part_of_speech": "verb", "pronunciation": "/ˌriːˈfæktər/",
            "definition_translation": "Изменить структуру кода, не меняя поведение.",
            "examples": [{ ${field(
            "english_text",
            example,
        )} "russian_translation": ${exampleRu?.let { "\"$it\"" } ?: "null"} }],
            "synonyms": ["restructure", "Refactor"], "related_words": ["code", "clean"] $extra }"""
    }

    private fun parse(vararg items: String, suggestion: String? = null, usage: String? = null): GenerationResponseDto {
        val sug = suggestion?.let { ", \"suggested_correction\": \"$it\"" }.orEmpty()
        val use = usage?.let { ", \"usage\": $it" }.orEmpty()
        return json.decodeFromString("""{ "items": [${items.joinToString(",")}] $sug $use }""")
    }

    private fun request(
        settings: GenerationSettings = GenerationSettings(level = Difficulty.B1, wordCount = 10),
        input: GenerationInput = GenerationInput.Topic("Programming"),
        exclude: List<String> = emptyList(),
    ) = GenerationRequest(input, settings, exclude)

    private fun map(dto: GenerationResponseDto, request: GenerationRequest = request(), header: UsageInfo? = null) =
        GenerationResponseMapper.map(json, dto, request, header)

    private fun success(result: AppResult<GenerationResult>) = (result as AppResult.Success).data

    // ---- mapping ----

    @Test
    fun validItem_isMappedToTheDomainModel() {
        val word = success(map(parse(item()))).items.single()
        assertEquals("refactor", word.word)
        assertEquals("рефакторить", word.translation)
        assertEquals(Difficulty.B2, word.difficulty)
        assertEquals(PartOfSpeech.VERB, word.partOfSpeech)
        assertEquals("/ˌriːˈfæktər/", word.pronunciation)
        assertEquals(1, word.examples.size)
        assertEquals("refactor", word.key)
    }

    @Test
    fun synonyms_dropTheWordItselfAndDuplicates() {
        val word = success(map(parse(item()))).items.single()
        assertEquals(listOf("restructure"), word.synonyms)
    }

    @Test
    fun settings_decideWhichOptionalPartsAreKept() {
        val off =
            request(GenerationSettings(includeExamples = false, includeSynonyms = false, includePronunciation = false))
        val word = success(map(parse(item()), off)).items.single()
        assertTrue(word.examples.isEmpty())
        assertTrue(word.synonyms.isEmpty())
        assertEquals("", word.pronunciation)
    }

    @Test
    fun wordMode_returnsOnlyOneItem() {
        val result =
            success(map(parse(item(), item(word = "deploy")), request(input = GenerationInput.SingleWord("refactor"))))
        assertEquals(1, result.items.size)
    }

    @Test
    fun resultIsLimitedToTheRequestedCount() {
        val items = (1..8).map { item(word = "word$it") }.toTypedArray()
        val result = success(map(parse(*items), request(GenerationSettings(wordCount = 5))))
        assertEquals(5, result.items.size)
        assertEquals(3, result.discardedItems)
    }

    // ---- required fields, invalid values ----

    @Test
    fun itemsMissingRequiredFields_areDropped_butValidOnesSurvive() {
        val result = success(
            map(
                parse(
                    item(word = null),
                    item(word = "ok"),
                    item(translation = null),
                    item(definition = null),
                    item(example = null),
                ),
            ),
        )
        assertEquals(listOf("ok"), result.items.map { it.word })
        assertEquals(4, result.discardedItems)
    }

    @Test
    fun invalidDifficulty_fallsBackToTheRequestedLevel() {
        val words = success(
            map(parse(item(difficulty = "intermediate"), item(word = "deploy", difficulty = null))),
        ).items
        assertTrue(words.all { it.difficulty == Difficulty.B1 })
    }

    @Test
    fun difficulty_isParsedCaseInsensitively() {
        assertEquals(Difficulty.C1, GenerationResponseMapper.parseDifficulty(" c1 "))
        assertNull(GenerationResponseMapper.parseDifficulty("D9"))
        assertNull(GenerationResponseMapper.parseDifficulty(null))
    }

    @Test
    fun unknownPartOfSpeech_becomesPhrase() {
        assertEquals(PartOfSpeech.PHRASE, GenerationResponseMapper.parsePartOfSpeech("phrasal verb?"))
        assertEquals(PartOfSpeech.ADJECTIVE, GenerationResponseMapper.parsePartOfSpeech("Adjective"))
    }

    @Test
    fun translationThatIsNotRussian_isRejected() {
        val result = map(parse(item(translation = "to restructure")))
        assertTrue((result as AppResult.Failure).error is AppError.InvalidResponse)
    }

    @Test
    fun wordsThatAreNotEnglish_orTooLong_areRejected() {
        val long = "a".repeat(GenerationResponseMapper.Limits.MAX_WORD + 1)
        val result =
            success(
                map(parse(item(word = "рефактор"), item(word = long), item(word = "<script>"), item(word = "fine"))),
            )
        assertEquals(listOf("fine"), result.items.map { it.word })
    }

    @Test
    fun excessivelyLongText_rejectsTheItem() {
        val tooLong = "x".repeat(GenerationResponseMapper.Limits.MAX_DEFINITION + 1)
        val longExample = "y ".repeat(GenerationResponseMapper.Limits.MAX_EXAMPLE)
        val result = map(parse(item(definition = tooLong), item(word = "other", example = longExample)))
        assertTrue((result as AppResult.Failure).error is AppError.InvalidResponse)
    }

    @Test
    fun withoutExamplesRequested_anItemNeedsNoValidExample() {
        val noExamples = request(GenerationSettings(includeExamples = false))
        assertEquals(1, success(map(parse(item(example = null)), noExamples)).items.size)
    }

    @Test
    fun controlCharactersAndExtraWhitespace_areCleaned() {
        val word = success(map(parse(item(translation = "  рефакто\\u0007рить   код ")))).items.single()
        assertEquals("рефакторить код", word.translation)
    }

    // ---- duplicates, exclusions ----

    @Test
    fun duplicatesInsideOneResponse_keepTheFirstOnly() {
        val result = success(map(parse(item(word = "Deploy"), item(word = "  deploy "), item(word = "DEPLOY"))))
        assertEquals(1, result.items.size)
        assertEquals("Deploy", result.items.single().word)
        assertEquals(2, result.discardedItems)
    }

    @Test
    fun excludedWords_areDroppedEvenIfTheBackendReturnsThem() {
        val result = success(map(parse(item(word = "alpha"), item(word = "beta")), request(exclude = listOf("Alpha"))))
        assertEquals(listOf("beta"), result.items.map { it.word })
    }

    // ---- empty, malformed ----

    @Test
    fun emptyItemList_isAnEmptyResponse() {
        val result = map(GenerationResponseDto(items = emptyList()))
        assertEquals(AppError.EmptyResponse, (result as AppResult.Failure).error)
        assertEquals(AppError.EmptyResponse, (map(GenerationResponseDto()) as AppResult.Failure).error)
    }

    @Test
    fun allItemsInvalid_isAnInvalidResponse() {
        val result = map(parse(item(word = null), item(translation = null)))
        assertTrue((result as AppResult.Failure).error is AppError.InvalidResponse)
    }

    @Test
    fun itemWithWrongFieldTypes_isDroppedWithoutFailingTheRest() {
        val broken = """{ "word": "broken", "examples": "not a list" }"""
        val notAnObject = "\"just a string\""
        val result = success(map(parse(broken, notAnObject, item())))
        assertEquals(listOf("refactor"), result.items.map { it.word })
        assertEquals(2, result.discardedItems)
    }

    @Test
    fun malformedJson_failsToDeserialize_soTheRepositoryCanReportInvalidResponse() {
        try {
            json.decodeFromString<GenerationResponseDto>("{ \"items\": [ {oops")
            fail("expected a SerializationException")
        } catch (expected: SerializationException) {
            // The repository turns this into AppError.InvalidResponse.
        }
    }

    // ---- spelling suggestion ----

    @Test
    fun suggestionWithoutItems_isReturnedInsteadOfAFailure() {
        val result =
            success(map(parse(suggestion = "necessary"), request(input = GenerationInput.SingleWord("neccesary"))))
        assertTrue(result.items.isEmpty())
        assertEquals("necessary", result.suggestedCorrection)
    }

    @Test
    fun suggestion_thatIsNotAWord_isIgnored() {
        val result = map(parse(suggestion = "<b>x</b>"))
        assertEquals(AppError.EmptyResponse, (result as AppResult.Failure).error)
    }

    // ---- usage ----

    @Test
    fun usage_fromTheBodyWins_headersFillTheGaps() {
        val body = """{ "daily_used": 3, "daily_limit": 20, "remaining_requests": 17 }"""
        val header = UsageInfo(remainingRequests = 99, retryAfterSeconds = 30)
        val usage = success(map(parse(item(), usage = body), header = header)).usage!!
        assertEquals(17, usage.remainingRequests)
        assertEquals(3, usage.dailyUsed)
        assertEquals(20, usage.dailyLimit)
        assertEquals(30, usage.retryAfterSeconds)
    }

    @Test
    fun noUsageAnywhere_meansNothingIsReported() {
        assertNull(success(map(parse(item()))).usage)
    }
}
