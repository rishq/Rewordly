package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.BackendConfig
import com.rewordly.app.core.network.VocabularyGenerationApi
import com.rewordly.app.core.network.model.ErrorEnvelopeDto
import com.rewordly.app.core.network.model.GenerationOptionsDto
import com.rewordly.app.core.network.model.GenerationResponseDto
import com.rewordly.app.core.network.model.TextRequestDto
import com.rewordly.app.core.network.model.TopicRequestDto
import com.rewordly.app.core.network.model.WordRequestDto
import com.rewordly.app.core.network.toNetworkAppError
import com.rewordly.app.data.remote.GenerationResponseMapper
import com.rewordly.app.domain.model.GenerationInput
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationResult
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.domain.repository.VocabularyGenerationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Talks to the project's own backend, which relays to an AI provider. Nothing here knows which provider that is.
 * Content (pasted text, generated words) is never logged.
 */
@Singleton
class RemoteVocabularyGenerationRepository @Inject constructor(
    private val api: VocabularyGenerationApi,
    private val json: Json,
    private val config: BackendConfig,
) : VocabularyGenerationRepository {
    private val _usage = MutableStateFlow<UsageInfo?>(null)
    override val usage: StateFlow<UsageInfo?> = _usage.asStateFlow()

    override suspend fun generate(request: GenerationRequest): AppResult<GenerationResult> {
        if (!config.isConfigured) return AppResult.Failure(AppError.BackendNotConfigured)

        val response = try {
            send(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            // The server answered 2xx with a body that is not the agreed JSON.
            return AppResult.Failure(AppError.InvalidResponse("malformed json", e))
        } catch (e: Throwable) {
            return AppResult.Failure(e.toNetworkAppError())
        }

        val headerUsage = usageFromHeaders(response)
        if (!response.isSuccessful) {
            val error = errorFor(response, headerUsage)
            (error as? AppError.RateLimited)?.let { rememberUsage(UsageInfo(retryAfterSeconds = it.retryAfterSeconds)) }
            return AppResult.Failure(error)
        }
        val body = response.body() ?: return AppResult.Failure(AppError.EmptyResponse)

        val result = GenerationResponseMapper.map(json, body, request, headerUsage)
        (result as? AppResult.Success)?.data?.usage?.let(::rememberUsage)
        return result
    }

    private suspend fun send(request: GenerationRequest): Response<GenerationResponseDto> {
        val settings = request.settings
        val options = GenerationOptionsDto(
            includeExamples = settings.includeExamples,
            includeSynonyms = settings.includeSynonyms,
            includePronunciation = settings.includePronunciation,
        )
        val level = settings.level.name
        return when (val input = request.input) {
            is GenerationInput.Topic -> api.generateByTopic(
                TopicRequestDto(input.topic.trim(), level, settings.wordCount, request.exclude, options),
            )
            is GenerationInput.Text -> api.generateFromText(
                TextRequestDto(input.text.trim(), level, settings.wordCount, request.exclude, options),
            )
            is GenerationInput.SingleWord -> api.exploreWord(WordRequestDto(input.word.trim(), level, options))
        }
    }

    private fun errorFor(response: Response<*>, headerUsage: UsageInfo?): AppError = when (response.code()) {
        429 -> AppError.RateLimited(headerUsage?.retryAfterSeconds ?: retryAfterFromBody(response))
        401, 403 -> AppError.Unauthorized()
        else -> AppError.Server(response.code())
    }

    private fun retryAfterFromBody(response: Response<*>): Int? = try {
        response.errorBody()?.string()?.let { json.decodeFromString<ErrorEnvelopeDto>(it).error?.retryAfterSeconds }
    } catch (e: Exception) {
        null
    }

    private fun usageFromHeaders(response: Response<*>): UsageInfo? {
        val remaining = response.headers()["X-RateLimit-Remaining"]?.trim()?.toIntOrNull()
        val retryAfter = response.headers()["Retry-After"]?.trim()?.toIntOrNull()
        return if (remaining == null && retryAfter == null) {
            null
        } else {
            UsageInfo(remainingRequests = remaining, retryAfterSeconds = retryAfter)
        }
    }

    private fun rememberUsage(info: UsageInfo) {
        _usage.update { previous ->
            GenerationResponseMapper.mergeUsage(info, previous)?.copy(retryAfterSeconds = info.retryAfterSeconds)
        }
    }
}
