package com.rewordly.app.core.network.ai

import com.rewordly.app.domain.model.AiApiStyle
import com.rewordly.app.domain.service.AiPromptBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds the provider-specific parts of a request: the path, the auth headers and the body.
 *
 * Kept free of any HTTP client so the exact bytes sent to each vendor can be asserted in a plain JVM test.
 */
object AiRequestFactory {
    const val CONTENT_TYPE = "application/json; charset=utf-8"

    /** The dated version every Anthropic request must carry; omitting it is a hard rejection. */
    const val ANTHROPIC_VERSION = "2023-06-01"

    /** Cap for the two vendors where the field is required or unambiguous. Not a target, just a ceiling. */
    const val MAX_OUTPUT_TOKENS = 8192

    /**
     * The path appended to the provider's base URL for the first attempt.
     *
     * OpenAI starts with the Responses API, which is where the current models are served.
     */
    fun primaryPath(style: AiApiStyle, model: String): String = when (style) {
        AiApiStyle.OPENAI -> "responses"
        AiApiStyle.ANTHROPIC -> "messages"
        AiApiStyle.GEMINI -> "models/$model:generateContent"
    }

    /**
     * The path to try when [primaryPath] answers "no such endpoint", or null when the style has no alternative.
     * Only OpenAI has two live generations of the same API.
     */
    fun fallbackPath(style: AiApiStyle): String? = when (style) {
        AiApiStyle.OPENAI -> "chat/completions"
        AiApiStyle.ANTHROPIC, AiApiStyle.GEMINI -> null
    }

    /**
     * The single header that carries the key for [style].
     *
     * Named separately because more than one place needs to know it: the request builder below, and the
     * debug logger, which has to redact every vendor's header. Keeping one list means a fourth provider is
     * covered everywhere as soon as it is added here.
     */
    fun authHeaderName(style: AiApiStyle): String = when (style) {
        AiApiStyle.OPENAI -> "Authorization"
        AiApiStyle.ANTHROPIC -> "x-api-key"
        AiApiStyle.GEMINI -> "x-goog-api-key"
    }

    /** Auth headers per vendor. The key is placed exactly where the vendor expects it, nowhere else. */
    fun headers(style: AiApiStyle, apiKey: String): Map<String, String> {
        val value = if (style == AiApiStyle.OPENAI) "Bearer $apiKey" else apiKey
        return mapOf(authHeaderName(style) to value, "Content-Type" to CONTENT_TYPE)
    }

    /** The Anthropic version header, which is separate from auth and required on every call. */
    fun extraHeaders(style: AiApiStyle): Map<String, String> = when (style) {
        AiApiStyle.ANTHROPIC -> mapOf("anthropic-version" to ANTHROPIC_VERSION)
        AiApiStyle.OPENAI, AiApiStyle.GEMINI -> emptyMap()
    }

    /**
     * Serializes the body for [style].
     *
     * @param fallback true when building the second OpenAI attempt, which uses the older chat shape.
     */
    fun body(
        json: Json,
        style: AiApiStyle,
        model: String,
        prompt: AiPromptBuilder.Prompt,
        fallback: Boolean = false,
    ): String = when (style) {
        AiApiStyle.OPENAI -> if (fallback) {
            json.encodeToString(
                OpenAiChatRequestDto(
                    model = model,
                    messages = listOf(ChatMessageDto("system", prompt.system), ChatMessageDto("user", prompt.user)),
                ),
            )
        } else {
            json.encodeToString(
                OpenAiResponsesRequestDto(model = model, input = prompt.user, instructions = prompt.system),
            )
        }
        AiApiStyle.ANTHROPIC -> json.encodeToString(
            AnthropicRequestDto(
                model = model,
                maxTokens = MAX_OUTPUT_TOKENS,
                system = prompt.system,
                messages = listOf(ChatMessageDto("user", prompt.user)),
            ),
        )
        AiApiStyle.GEMINI -> json.encodeToString(
            GeminiRequestDto(
                systemInstruction = GeminiContentDto(parts = listOf(GeminiPartDto(prompt.system))),
                contents = listOf(GeminiContentDto(role = "user", parts = listOf(GeminiPartDto(prompt.user)))),
                generationConfig = GeminiGenerationConfigDto(
                    maxOutputTokens = MAX_OUTPUT_TOKENS,
                    responseMimeType = "application/json",
                ),
            ),
        )
    }
}
