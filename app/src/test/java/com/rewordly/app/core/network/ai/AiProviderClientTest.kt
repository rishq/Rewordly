package com.rewordly.app.core.network.ai

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.model.GenerationResponseDto
import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.service.AiPromptBuilder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the real [AiProviderClient] against scripted answers.
 *
 * An OkHttp interceptor stands in for the network, so the request that would have gone out is recorded verbatim
 * and the reply is whatever the test wants. That covers the parts the factory and parser tests cannot: the URL,
 * the applied headers, unwrapping the vendor envelope, the status mapping and the fallback decision.
 */
class AiProviderClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val prompt = AiPromptBuilder.Prompt(system = "SYSTEM", user = "USER")

    private val sent = mutableListOf<Request>()
    private val scripted = ArrayDeque<Pair<Int, String>>()

    /** The answer the model is pretending to have produced, as the app expects to receive it. */
    private val answer =
        """{"items":[{"word":"voyage","translation":"путешествие","definition":"A long trip."}]}"""

    private fun enqueue(code: Int, body: String = "{}") {
        scripted += code to body
    }

    private fun client(): AiProviderClient {
        val interceptor = Interceptor { chain ->
            sent += chain.request()
            val (code, body) = scripted.removeFirst()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("scripted")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return AiProviderClient(OkHttpClient.Builder().addInterceptor(interceptor).build(), json)
    }

    private suspend fun generate(
        provider: AiProvider = AiProvider.OPENAI,
        model: String = provider.defaultModel,
        key: String = "sk-test",
    ) = client().generate(provider, model, key, prompt)

    /** A Responses API answer, including a reasoning item the client must skip over. */
    private fun openAiResponses(text: String) = buildJsonObject {
        putJsonArray("output") {
            addJsonObject {
                put("type", "reasoning")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "reasoning_text")
                        put("text", "thinking")
                    }
                }
            }
            addJsonObject {
                put("type", "message")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "output_text")
                        put("text", text)
                    }
                }
            }
        }
    }.toString()

    private fun openAiChat(text: String) = buildJsonObject {
        putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", text) } } }
    }.toString()

    private fun anthropic(vararg blocks: String) = buildJsonObject {
        putJsonArray("content") {
            addJsonObject {
                put("type", "thinking")
                put("thinking", "hmm")
            }
            blocks.forEach { block ->
                addJsonObject {
                    put("type", "text")
                    put("text", block)
                }
            }
        }
    }.toString()

    private fun gemini(text: String) = buildJsonObject {
        putJsonArray("candidates") {
            addJsonObject {
                putJsonObject("content") { putJsonArray("parts") { addJsonObject { put("text", text) } } }
            }
        }
    }.toString()

    private fun itemsOf(result: AppResult<GenerationResponseDto>) = (result as AppResult.Success).data.items.orEmpty()

    @Test
    fun theRequestGoesToTheProvidersOwnPathWithItsOwnAuthHeader() = runTest {
        enqueue(200, openAiResponses(answer))
        val result = generate()
        val request = sent.single()
        assertEquals("https://api.openai.com/v1/responses", request.url.toString())
        assertEquals("Bearer sk-test", request.header("Authorization"))
        assertEquals("POST", request.method)
        // The answer must come from the message item, not from the envelope around it.
        assertEquals(1, itemsOf(result).size)
    }

    @Test
    fun geminiCarriesTheModelInThePathAndTheKeyInItsHeader() = runTest {
        enqueue(200, gemini(answer))
        generate(provider = AiProvider.GOOGLE, model = "gemini-3.8-flash", key = "goog-key")
        val request = sent.single()
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent",
            request.url.toString(),
        )
        assertEquals("goog-key", request.header("x-goog-api-key"))
        assertEquals(null, request.header("Authorization"))
    }

    @Test
    fun anthropicSendsTheVersionHeaderAndNoBearerToken() = runTest {
        enqueue(200, anthropic(answer))
        generate(provider = AiProvider.ANTHROPIC)
        val request = sent.single()
        assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
        assertEquals("sk-test", request.header("x-api-key"))
        assertEquals(AiRequestFactory.ANTHROPIC_VERSION, request.header("anthropic-version"))
        assertEquals(null, request.header("Authorization"))
    }

    @Test
    fun textSplitAcrossSeveralBlocks_isJoinedBeforeParsing() = runTest {
        enqueue(200, anthropic("""{"items":[{"word":"voy""", """age","translation":"путешествие"}]}"""))
        assertEquals(1, itemsOf(generate(provider = AiProvider.ANTHROPIC)).size)
    }

    @Test
    fun aFencedAnswer_isStillParsed() = runTest {
        enqueue(200, anthropic("```json\n$answer\n```"))
        assertEquals(1, itemsOf(generate(provider = AiProvider.ANTHROPIC)).size)
    }

    @Test
    fun openAiFallsBackToChatCompletions_whenTheNewerPathIsMissing() = runTest {
        enqueue(404, """{"error":{"message":"Unknown path"}}""")
        enqueue(200, openAiChat(answer))
        val result = generate()
        assertEquals(1, itemsOf(result).size)
        assertEquals(2, sent.size)
        assertEquals("/v1/responses", sent[0].url.encodedPath)
        assertEquals("/v1/chat/completions", sent[1].url.encodedPath)
    }

    @Test
    fun aMissingEndpointWithoutAFallbackIsReportedWithTheProviderMessage() = runTest {
        enqueue(404, """{"error":{"message":"Unknown path"}}""")
        val result = generate(provider = AiProvider.GOOGLE)
        assertEquals(1, sent.size)
        assertEquals(
            AppResult.Failure(AppError.ProviderRejected(AiProvider.GOOGLE.displayName, 404, "Unknown path")),
            result,
        )
    }

    @Test
    fun aRejectedKey_doesNotTriggerTheFallbackAndNamesTheProvider() = runTest {
        enqueue(401, """{"error":{"message":"Incorrect API key provided"}}""")
        val result = generate()
        assertEquals("a bad key must not be retried on the other endpoint", 1, sent.size)
        assertEquals(AppResult.Failure(AppError.InvalidApiKey(AiProvider.OPENAI.displayName)), result)
    }

    @Test
    fun aRejectedModel_surfacesWhatTheProviderSaid() = runTest {
        enqueue(400, """{"error":{"message":"The model `gpt-9` does not exist"}}""")
        val result = generate(model = "gpt-9")
        assertEquals(
            AppResult.Failure(
                AppError.ProviderRejected(AiProvider.OPENAI.displayName, 400, "The model `gpt-9` does not exist"),
            ),
            result,
        )
    }

    @Test
    fun aRateLimit_isReportedWithoutInventingAWaitTime() = runTest {
        enqueue(429, """{"error":{"message":"slow down"}}""")
        assertEquals(AppResult.Failure(AppError.RateLimited(null)), generate())
    }

    @Test
    fun aServerErrorIsReportedWithItsStatusCode() = runTest {
        enqueue(503, "")
        assertEquals(AppResult.Failure(AppError.Server(503)), generate())
    }

    @Test
    fun anAnswerWithoutJson_isAnInvalidResponseRatherThanACrash() = runTest {
        enqueue(200, anthropic("I would rather not."))
        val result = generate(provider = AiProvider.ANTHROPIC)
        assertTrue((result as AppResult.Failure).error is AppError.InvalidResponse)
    }

    @Test
    fun aSuccessfulStatusWithNoTextAtAll_isAnInvalidResponse() = runTest {
        enqueue(200, anthropic())
        val result = generate(provider = AiProvider.ANTHROPIC)
        assertTrue((result as AppResult.Failure).error is AppError.InvalidResponse)
    }
}
