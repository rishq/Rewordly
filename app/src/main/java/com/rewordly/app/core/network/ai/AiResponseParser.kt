package com.rewordly.app.core.network.ai

import com.rewordly.app.domain.model.AiApiStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pulls the assistant text out of a provider's answer, and the JSON object out of that text.
 *
 * Navigation walks the raw element tree instead of typed DTOs: all three vendors add response fields freely, and
 * a strict deserializer would turn a harmless addition into a failed generation. Nothing here trusts the answer;
 * [com.rewordly.app.data.remote.GenerationResponseMapper] still validates every item afterwards.
 */
object AiResponseParser {
    /** Provider error messages are shown to the user; keep them to something a dialog can display. */
    const val MAX_DETAIL_LENGTH = 300

    fun text(json: Json, style: AiApiStyle, body: String): String? {
        val root = parse(json, body) ?: return null
        val text = when (style) {
            AiApiStyle.OPENAI -> openAiText(root)
            AiApiStyle.ANTHROPIC -> anthropicText(root)
            AiApiStyle.GEMINI -> geminiText(root)
        }
        return text?.takeIf { it.isNotBlank() }
    }

    /**
     * The explanation a provider attached to an error. Every vendor nests it differently, so all the shapes seen
     * in practice are accepted and an empty string is returned when there is nothing usable.
     */
    fun errorDetail(json: Json, body: String): String {
        val root = parse(json, body) as? JsonObject ?: return ""
        val message = stringAt(root, "error", "message")
            ?: stringAt(root, "error", "detail")
            ?: root.stringOrNull("message")
            ?: root.stringOrNull("detail")
        return message?.trim()?.take(MAX_DETAIL_LENGTH).orEmpty()
    }

    /**
     * Finds the JSON object inside an answer. Models sometimes wrap it in a code fence or add a sentence around
     * it despite being told not to, so the outermost braces are used rather than assuming a bare object.
     */
    fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        val end = text.lastIndexOf('}')
        if (end <= start) return null
        return text.substring(start, end + 1)
    }

    /** Responses API first, chat completions as the fallback shape. */
    private fun openAiText(root: JsonElement): String? {
        val obj = root as? JsonObject ?: return null
        val output = obj["output"] as? JsonArray
        if (output != null) {
            val joined = output.objects()
                .filter { it.stringOrNull("type") == "message" }
                .flatMap { (it["content"] as? JsonArray).orEmpty() }
                .mapNotNull { it as? JsonObject }
                .filter { it.stringOrNull("type") == "output_text" }
                .mapNotNull { it.stringOrNull("text") }
                .joinToString("")
            if (joined.isNotBlank()) return joined
        }
        return (obj["choices"] as? JsonArray).orEmpty()
            .objects()
            .mapNotNull { (it["message"] as? JsonObject)?.stringOrNull("content") }
            .joinToString("")
            .takeIf { it.isNotBlank() }
    }

    /** Anthropic returns a list of blocks; only the text blocks carry the answer. */
    private fun anthropicText(root: JsonElement): String? {
        val obj = root as? JsonObject ?: return null
        return (obj["content"] as? JsonArray).orEmpty()
            .objects()
            .filter { it.stringOrNull("type") == "text" }
            .mapNotNull { it.stringOrNull("text") }
            .joinToString("")
            .takeIf { it.isNotBlank() }
    }

    private fun geminiText(root: JsonElement): String? {
        val obj = root as? JsonObject ?: return null
        return (obj["candidates"] as? JsonArray).orEmpty()
            .objects()
            .mapNotNull { it["content"] as? JsonObject }
            .flatMap { (it["parts"] as? JsonArray).orEmpty() }
            .objects()
            .mapNotNull { it.stringOrNull("text") }
            .joinToString("")
            .takeIf { it.isNotBlank() }
    }

    private fun parse(json: Json, body: String): JsonElement? = try {
        json.parseToJsonElement(body)
    } catch (e: Exception) {
        null
    }

    /** Works on the plain list type so it can follow an `orEmpty()` on a missing array. */
    private fun List<JsonElement>.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun stringAt(obj: JsonObject, vararg path: String): String? {
        var current: JsonElement = obj
        for (key in path) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return (current as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
