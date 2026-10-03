package com.rewordly.app.core.network.ai

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.model.GenerationResponseDto
import com.rewordly.app.core.network.toNetworkAppError
import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.service.AiPromptBuilder
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Sends one generation request straight to the provider the user chose, using the key the user supplied.
 *
 * The app is the API client here, so there is no intermediary that could sanitise a bad answer: everything the
 * provider returns goes through [com.rewordly.app.data.remote.GenerationResponseMapper] before it is trusted.
 * Neither the key nor the prompt is ever logged.
 */
@Singleton
class AiProviderClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun generate(
        provider: AiProvider,
        model: String,
        apiKey: String,
        prompt: AiPromptBuilder.Prompt,
    ): AppResult<GenerationResponseDto> {
        val first = attempt(provider, model, apiKey, prompt, fallback = false)
        // An account without the newer OpenAI endpoint answers "no such path"; the older chat shape still works.
        val fallbackExists = AiRequestFactory.fallbackPath(provider.apiStyle) != null
        val retried = if (first is Attempt.EndpointMissing && fallbackExists) {
            attempt(provider, model, apiKey, prompt, fallback = true)
        } else {
            first
        }
        return when (retried) {
            is Attempt.Body -> decode(retried.text)
            is Attempt.Failed -> AppResult.Failure(retried.error)
            is Attempt.EndpointMissing -> AppResult.Failure(
                AppError.ProviderRejected(provider.displayName, retried.status, retried.detail),
            )
        }
    }

    private fun decode(text: String): AppResult<GenerationResponseDto> {
        val raw = AiResponseParser.extractJsonObject(text)
            ?: return AppResult.Failure(AppError.InvalidResponse("no json object in the answer"))
        return try {
            AppResult.Success(json.decodeFromString(GenerationResponseDto.serializer(), raw))
        } catch (e: SerializationException) {
            AppResult.Failure(AppError.InvalidResponse("malformed json", e))
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(AppError.InvalidResponse("malformed json", e))
        }
    }

    private suspend fun attempt(
        provider: AiProvider,
        model: String,
        apiKey: String,
        prompt: AiPromptBuilder.Prompt,
        fallback: Boolean,
    ): Attempt {
        val path = if (fallback) {
            AiRequestFactory.fallbackPath(provider.apiStyle) ?: return Attempt.Failed(AppError.Unknown())
        } else {
            AiRequestFactory.primaryPath(provider.apiStyle, model)
        }
        val url = (provider.baseUrl + path).toHttpUrlOrNull()
            ?: return Attempt.Failed(AppError.InvalidResponse("unusable provider url"))
        val body = AiRequestFactory.body(json, provider.apiStyle, model, prompt, fallback)
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(AiRequestFactory.CONTENT_TYPE.toMediaType()))
            .apply {
                val headers = AiRequestFactory.headers(provider.apiStyle, apiKey) +
                    AiRequestFactory.extraHeaders(provider.apiStyle)
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build()

        val response = try {
            client.newCall(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return Attempt.Failed(e.toNetworkAppError())
        }

        return response.use { result ->
            val body = result.body?.string().orEmpty()
            when {
                // The answer has to be pulled out of the vendor's envelope first: decoding the raw body would
                // read the envelope itself rather than what the model said.
                result.isSuccessful -> AiResponseParser.text(json, provider.apiStyle, body)
                    ?.let { Attempt.Body(it) }
                    ?: Attempt.Failed(AppError.InvalidResponse("the answer carried no text"))
                result.code in ENDPOINT_MISSING_CODES -> Attempt.EndpointMissing(
                    result.code,
                    AiResponseParser.errorDetail(json, body),
                )
                result.code in ACCESS_DENIED_CODES -> Attempt.Failed(AppError.InvalidApiKey(provider.displayName))
                result.code == RATE_LIMITED_CODE -> Attempt.Failed(
                    AppError.RateLimited(retryAfterSeconds(result)),
                )
                result.code >= SERVER_ERROR_FLOOR -> Attempt.Failed(AppError.Server(result.code))
                else -> {
                    val detail = AiResponseParser.errorDetail(json, body)
                    Attempt.Failed(AppError.ProviderRejected(provider.displayName, result.code, detail))
                }
            }
        }
    }

    private fun retryAfterSeconds(response: Response): Int? = response.header("Retry-After")?.trim()?.toIntOrNull()

    /** The result of one HTTP attempt, kept separate so the fallback decision stays readable. */
    private sealed interface Attempt {
        data class Body(val text: String) : Attempt

        /** The path does not exist on this account. [detail] is the provider's own wording. */
        data class EndpointMissing(val status: Int, val detail: String) : Attempt

        data class Failed(val error: AppError) : Attempt
    }

    private companion object {
        val ENDPOINT_MISSING_CODES = setOf(404, 405)
        val ACCESS_DENIED_CODES = setOf(401, 403)
        const val RATE_LIMITED_CODE = 429
        const val SERVER_ERROR_FLOOR = 500
    }
}

/**
 * Bridges OkHttp's callback API to coroutines so cancelling the caller cancels the call, which is what the
 * generation screens rely on when the user leaves the screen mid-request.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        },
    )
}
