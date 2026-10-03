package com.rewordly.app.core.network.ai

import com.rewordly.app.domain.model.AiApiStyle
import com.rewordly.app.domain.service.AiPromptBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRequestFactoryTest {
    private val json = Json { explicitNulls = false }
    private val prompt = AiPromptBuilder.Prompt(system = "SYSTEM", user = "USER")

    private fun body(style: AiApiStyle, fallback: Boolean = false) =
        json.parseToJsonElement(AiRequestFactory.body(json, style, "some-model", prompt, fallback)).jsonObject

    @Test
    fun eachVendor_getsTheKeyInTheHeaderItExpects() {
        assertEquals(
            "Bearer sk-test",
            AiRequestFactory.headers(AiApiStyle.OPENAI, "sk-test")["Authorization"],
        )
        assertEquals("sk-test", AiRequestFactory.headers(AiApiStyle.ANTHROPIC, "sk-test")["x-api-key"])
        assertEquals("sk-test", AiRequestFactory.headers(AiApiStyle.GEMINI, "sk-test")["x-goog-api-key"])
    }

    @Test
    fun noVendorReceivesTheKeyInMoreThanOnePlace() {
        AiApiStyle.entries.forEach { style ->
            val headers = AiRequestFactory.headers(style, "sk-test") + AiRequestFactory.extraHeaders(style)
            val carryingTheKey = headers.count { (_, value) -> value.contains("sk-test") }
            assertEquals("$style leaks the key into $carryingTheKey headers", 1, carryingTheKey)
        }
    }

    @Test
    fun anthropicCarriesTheDatedVersionHeader() {
        assertEquals(
            AiRequestFactory.ANTHROPIC_VERSION,
            AiRequestFactory.extraHeaders(AiApiStyle.ANTHROPIC)["anthropic-version"],
        )
        assertTrue(AiRequestFactory.extraHeaders(AiApiStyle.OPENAI).isEmpty())
    }

    @Test
    fun paths_matchEachVendorsApi() {
        assertEquals("responses", AiRequestFactory.primaryPath(AiApiStyle.OPENAI, "gpt-6-luna"))
        assertEquals("messages", AiRequestFactory.primaryPath(AiApiStyle.ANTHROPIC, "claude-sonnet-5-5"))
        assertEquals(
            "models/gemini-3.8-flash:generateContent",
            AiRequestFactory.primaryPath(AiApiStyle.GEMINI, "gemini-3.8-flash"),
        )
    }

    @Test
    fun onlyOpenAiHasASecondEndpointToTry() {
        assertEquals("chat/completions", AiRequestFactory.fallbackPath(AiApiStyle.OPENAI))
        assertNull(AiRequestFactory.fallbackPath(AiApiStyle.ANTHROPIC))
        assertNull(AiRequestFactory.fallbackPath(AiApiStyle.GEMINI))
    }

    @Test
    fun openAiPrimaryBody_usesTheResponsesShape() {
        val body = body(AiApiStyle.OPENAI)
        assertEquals("some-model", body["model"]?.jsonPrimitive?.content)
        assertEquals("USER", body["input"]?.jsonPrimitive?.content)
        assertEquals("SYSTEM", body["instructions"]?.jsonPrimitive?.content)
        // The prompt belongs in instructions, never mixed into the user turn.
        assertTrue(body["messages"] == null)
    }

    @Test
    fun openAiFallbackBody_usesTheChatShape() {
        val messages = body(AiApiStyle.OPENAI, fallback = true)["messages"]?.jsonArray
            ?: error("no messages array")
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("SYSTEM", messages[0].jsonObject["content"]?.jsonPrimitive?.content)
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("USER", messages[1].jsonObject["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun anthropicBody_carriesTheSystemPromptAndAMaxTokensCeiling() {
        val body = body(AiApiStyle.ANTHROPIC)
        assertEquals("SYSTEM", body["system"]?.jsonPrimitive?.content)
        // max_tokens is required by Anthropic; omitting it is a hard rejection.
        assertEquals(
            AiRequestFactory.MAX_OUTPUT_TOKENS.toString(),
            body["max_tokens"]?.jsonPrimitive?.content,
        )
        assertEquals(1, body["messages"]?.jsonArray?.size)
    }

    @Test
    fun geminiBody_splitsSystemInstructionFromContents() {
        val body = body(AiApiStyle.GEMINI)
        val system = body["system_instruction"]?.jsonObject
        val systemText = system?.get("parts")?.jsonArray?.first()?.jsonObject?.get("text")?.jsonPrimitive?.content
        assertEquals("SYSTEM", systemText)
        val contents = body["contents"]?.jsonArray ?: error("no contents")
        assertEquals("user", contents.first().jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals(
            "USER",
            contents.first().jsonObject["parts"]?.jsonArray?.first()?.jsonObject?.get("text")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun geminiAsksForAJsonAnswer() {
        val config = body(AiApiStyle.GEMINI)["generationConfig"]?.jsonObject ?: error("no generationConfig")
        assertEquals("application/json", config["responseMimeType"]?.jsonPrimitive?.content)
    }

    @Test
    fun theModelTravelsInTheBody_exceptForGeminiWhichPutsItInThePath() {
        // Gemini has no model field at all: it is part of the generateContent URL, asserted in paths_matchEachVendorsApi.
        assertEquals("some-model", body(AiApiStyle.OPENAI)["model"]?.jsonPrimitive?.content)
        assertEquals("some-model", body(AiApiStyle.ANTHROPIC)["model"]?.jsonPrimitive?.content)
        assertNull(body(AiApiStyle.GEMINI)["model"])
    }
}
