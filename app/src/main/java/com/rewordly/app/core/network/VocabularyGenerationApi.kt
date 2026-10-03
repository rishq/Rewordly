package com.rewordly.app.core.network

import com.rewordly.app.core.network.model.GenerationResponseDto
import com.rewordly.app.core.network.model.TextRequestDto
import com.rewordly.app.core.network.model.TopicRequestDto
import com.rewordly.app.core.network.model.WordRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Backend-mediated AI generation. The app never talks to an AI provider and holds no provider key.
 * Responses are wrapped in [Response] so rate-limit headers and error bodies stay readable.
 * The full contract lives in docs/ai-backend-contract.md.
 */
interface VocabularyGenerationApi {
    @POST("v1/vocabulary/topic")
    suspend fun generateByTopic(@Body request: TopicRequestDto): Response<GenerationResponseDto>

    @POST("v1/vocabulary/from-text")
    suspend fun generateFromText(@Body request: TextRequestDto): Response<GenerationResponseDto>

    @POST("v1/vocabulary/word")
    suspend fun exploreWord(@Body request: WordRequestDto): Response<GenerationResponseDto>
}

/** Whether a backend URL was supplied at build time. The placeholder host ends in ".invalid". */
data class BackendConfig(val baseUrl: String) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && !baseUrl.substringAfter("://").substringBefore('/').endsWith(".invalid")
}
