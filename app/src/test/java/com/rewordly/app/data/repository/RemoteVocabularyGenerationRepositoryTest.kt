package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.BackendConfig
import com.rewordly.app.core.network.VocabularyGenerationApi
import com.rewordly.app.core.network.model.GenerationResponseDto
import com.rewordly.app.core.network.model.TextRequestDto
import com.rewordly.app.core.network.model.TopicRequestDto
import com.rewordly.app.core.network.model.WordRequestDto
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GenerationInput
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationSettings
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteVocabularyGenerationRepositoryTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val validBody = """
        { "items": [ {
            "word": "deploy", "translation": "развернуть", "difficulty": "B1", "part_of_speech": "verb",
            "definition": "To put software into use.", "definition_translation": "Запустить программу в работу.",
            "examples": [ { "english_text": "We deploy on Fridays.", "russian_translation": "Мы выкатываем по пятницам." } ]
        } ] }
    """.trimIndent()

    private class FakeApi : VocabularyGenerationApi {
        var topicCall: suspend (TopicRequestDto) -> Response<GenerationResponseDto> = { error("not scripted") }
        var textCall: suspend (TextRequestDto) -> Response<GenerationResponseDto> = { error("not scripted") }
        var wordCall: suspend (WordRequestDto) -> Response<GenerationResponseDto> = { error("not scripted") }
        val topicRequests = mutableListOf<TopicRequestDto>()
        val textRequests = mutableListOf<TextRequestDto>()
        val wordRequests = mutableListOf<WordRequestDto>()

        override suspend fun generateByTopic(request: TopicRequestDto): Response<GenerationResponseDto> {
            topicRequests += request
            return topicCall(request)
        }

        override suspend fun generateFromText(request: TextRequestDto): Response<GenerationResponseDto> {
            textRequests += request
            return textCall(request)
        }

        override suspend fun exploreWord(request: WordRequestDto): Response<GenerationResponseDto> {
            wordRequests += request
            return wordCall(request)
        }
    }

    private val api = FakeApi()
    private val repository = RemoteVocabularyGenerationRepository(api, json, BackendConfig("https://api.example.com/"))
    private val topicRequest = GenerationRequest(
        GenerationInput.Topic("Software Development"),
        GenerationSettings(level = Difficulty.B1, wordCount = 10),
    )

    private fun ok(body: String, vararg headers: String): Response<GenerationResponseDto> {
        val dto = json.decodeFromString<GenerationResponseDto>(body)
        return Response.success(dto, okhttp3.Headers.headersOf(*headers))
    }

    private fun failure(code: Int, body: String = "{}", vararg headers: String): Response<GenerationResponseDto> {
        val raw = okhttp3.Response.Builder()
            .code(code).message("error").protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://api.example.com/").build())
            .headers(okhttp3.Headers.headersOf(*headers))
            .build()
        return Response.error(body.toResponseBody("application/json".toMediaType()), raw)
    }

    private fun error(result: AppResult<*>) = (result as AppResult.Failure).error

    // ---- requests ----

    @Test
    fun topicRequest_carriesLevelCountOptionsAndExclusions() = runTest {
        api.topicCall = { ok(validBody) }
        repository.generate(topicRequest.copy(exclude = listOf("api")))
        val sent = api.topicRequests.single()
        assertEquals("Software Development", sent.topic)
        assertEquals("B1", sent.level)
        assertEquals(10, sent.count)
        assertEquals(listOf("api"), sent.exclude)
        assertTrue(sent.options.includeExamples && sent.options.includeSynonyms && sent.options.includePronunciation)
        assertEquals("ru", sent.translationLanguage)
    }

    @Test
    fun eachModeUsesItsOwnEndpoint() = runTest {
        api.textCall = { ok(validBody) }
        api.wordCall = { ok(validBody) }
        val settings = GenerationSettings()
        repository.generate(GenerationRequest(GenerationInput.Text("  some pasted text  "), settings))
        repository.generate(GenerationRequest(GenerationInput.SingleWord(" deploy "), settings))
        assertEquals("some pasted text", api.textRequests.single().text)
        assertEquals("deploy", api.wordRequests.single().word)
        assertTrue(api.topicRequests.isEmpty())
    }

    // ---- success ----

    @Test
    fun successfulResponse_isValidatedAndMapped() = runTest {
        api.topicCall = { ok(validBody) }
        val result = (repository.generate(topicRequest) as AppResult.Success).data
        assertEquals("deploy", result.items.single().word)
    }

    @Test
    fun usage_isRemembered_fromBodyAndHeaders() = runTest {
        val body = validBody.replaceFirst("{", "{ \"usage\": { \"daily_used\": 4, \"daily_limit\": 20 },")
        api.topicCall = { ok(body, "X-RateLimit-Remaining", "16") }
        assertNull(repository.usage.value)
        repository.generate(topicRequest)
        val usage = repository.usage.value!!
        assertEquals(16, usage.remainingRequests)
        assertEquals(4, usage.dailyUsed)
        assertEquals(20, usage.dailyLimit)
    }

    // ---- failures ----

    @Test
    fun http200WithNoUsableItems_isStillAnError() = runTest {
        api.topicCall = { ok("""{ "items": [ { "word": "oops" } ] }""") }
        assertTrue(error(repository.generate(topicRequest)) is AppError.InvalidResponse)
    }

    @Test
    fun emptyItems_areAnEmptyResponse() = runTest {
        api.topicCall = { ok("""{ "items": [] }""") }
        assertEquals(AppError.EmptyResponse, error(repository.generate(topicRequest)))
    }

    @Test
    fun missingBody_isAnEmptyResponse() = runTest {
        api.topicCall = { Response.success<GenerationResponseDto>(null) }
        assertEquals(AppError.EmptyResponse, error(repository.generate(topicRequest)))
    }

    @Test
    fun malformedJson_isAnInvalidResponse() = runTest {
        api.topicCall = { throw SerializationException("Unexpected JSON token") }
        assertTrue(error(repository.generate(topicRequest)) is AppError.InvalidResponse)
    }

    @Test
    fun rateLimit_readsRetryAfterFromTheHeader() = runTest {
        api.topicCall = { failure(429, "{}", "Retry-After", "42") }
        val error = error(repository.generate(topicRequest)) as AppError.RateLimited
        assertEquals(42, error.retryAfterSeconds)
        assertEquals(42, repository.usage.value?.retryAfterSeconds)
    }

    @Test
    fun rateLimit_fallsBackToTheErrorBody() = runTest {
        api.topicCall = { failure(429, """{ "error": { "code": "rate_limited", "retry_after_seconds": 90 } }""") }
        val error = error(repository.generate(topicRequest)) as AppError.RateLimited
        assertEquals(90, error.retryAfterSeconds)
    }

    @Test
    fun rateLimit_withoutAnyHint_stillReportsRateLimited() = runTest {
        api.topicCall = { failure(429, "not json at all") }
        assertNull((error(repository.generate(topicRequest)) as AppError.RateLimited).retryAfterSeconds)
    }

    @Test
    fun serverErrors_keepTheirStatusCode() = runTest {
        api.topicCall = { failure(503) }
        assertEquals(503, (error(repository.generate(topicRequest)) as AppError.Server).code)
    }

    @Test
    fun unauthorized_isReportedAsSuch() = runTest {
        api.topicCall = { failure(401) }
        assertTrue(error(repository.generate(topicRequest)) is AppError.Unauthorized)
    }

    @Test
    fun ioProblems_areNetworkOrTimeoutErrors() = runTest {
        api.topicCall = { throw IOException("offline") }
        assertTrue(error(repository.generate(topicRequest)) is AppError.Network)
        api.topicCall = { throw SocketTimeoutException("slow") }
        assertTrue(error(repository.generate(topicRequest)) is AppError.Timeout)
    }

    @Test
    fun withoutABackendUrl_nothingIsSent() = runTest {
        val unconfigured =
            RemoteVocabularyGenerationRepository(api, json, BackendConfig("https://api.rewordly.invalid/"))
        assertEquals(AppError.BackendNotConfigured, error(unconfigured.generate(topicRequest)))
        assertTrue(api.topicRequests.isEmpty())
    }

    // ---- cancellation ----

    @Test
    fun cancellingTheCaller_cancelsTheRequest_withoutProducingAResult() = runTest {
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Response<GenerationResponseDto>>()
        api.topicCall = {
            started.complete(Unit)
            never.await()
        }
        var finished: AppResult<*>? = null
        val job = launch { finished = repository.generate(topicRequest) }
        advanceUntilIdle()
        assertTrue(started.isCompleted)

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertNull("a cancelled request must not be reported as an error or result", finished)
        assertFalse(never.isCompleted)
    }

    @Test
    fun backendConfig_recognisesThePlaceholderHost() {
        assertFalse(BackendConfig("https://api.rewordly.invalid/").isConfigured)
        assertFalse(BackendConfig("").isConfigured)
        assertTrue(BackendConfig("https://api.example.com/v1/").isConfigured)
    }
}
