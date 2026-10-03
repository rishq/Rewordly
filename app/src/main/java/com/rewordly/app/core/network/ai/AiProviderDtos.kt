package com.rewordly.app.core.network.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A chat turn, shared by the OpenAI and Anthropic shapes. */
@Serializable
data class ChatMessageDto(val role: String, val content: String)

/**
 * OpenAI Responses API. `input` accepts a plain string, which keeps the body to the three fields that matter.
 * No token cap is sent on purpose: the parameter was renamed between generations and a wrong name is a hard
 * rejection, while the prompt already bounds the answer by item count.
 */
@Serializable
data class OpenAiResponsesRequestDto(
    val model: String,
    val input: String,
    val instructions: String,
)

/** OpenAI chat completions, used only when the Responses endpoint is not available on the account. */
@Serializable
data class OpenAiChatRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
)

@Serializable
data class AnthropicRequestDto(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ChatMessageDto>,
)

@Serializable
data class GeminiRequestDto(
    @SerialName("system_instruction") val systemInstruction: GeminiContentDto,
    val contents: List<GeminiContentDto>,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfigDto,
)

@Serializable
data class GeminiContentDto(val parts: List<GeminiPartDto>, val role: String? = null)

@Serializable
data class GeminiPartDto(val text: String)

@Serializable
data class GeminiGenerationConfigDto(
    @SerialName("maxOutputTokens") val maxOutputTokens: Int,
    @SerialName("responseMimeType") val responseMimeType: String,
)
