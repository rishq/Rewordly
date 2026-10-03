package com.rewordly.app.core.network.ai

import com.rewordly.app.domain.model.AiApiStyle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiResponseParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun text(style: AiApiStyle, body: String) = AiResponseParser.text(json, style, body)

    @Test
    fun openAiResponsesText_isReadFromTheMessageItem() {
        val body = """
            {"id":"resp_1","output":[
              {"type":"reasoning","summary":[]},
              {"type":"message","role":"assistant","content":[{"type":"output_text","text":"{\"items\":[]}"}]}
            ]}
        """.trimIndent()
        assertEquals("{\"items\":[]}", text(AiApiStyle.OPENAI, body))
    }

    @Test
    fun openAiReasoningItems_areNeverMistakenForTheAnswer() {
        val body = """
            {"output":[
              {"type":"reasoning","content":[{"type":"reasoning_text","text":"thinking out loud"}]},
              {"type":"message","content":[{"type":"output_text","text":"ANSWER"}]}
            ]}
        """.trimIndent()
        assertEquals("ANSWER", text(AiApiStyle.OPENAI, body))
    }

    @Test
    fun openAiChatFallbackText_isReadFromChoices() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"ANSWER"}}]}"""
        assertEquals("ANSWER", text(AiApiStyle.OPENAI, body))
    }

    @Test
    fun anthropicText_isJoinedFromTheTextBlocksOnly() {
        val body = """
            {"id":"msg_1","content":[
              {"type":"thinking","thinking":"hmm"},
              {"type":"text","text":"{\"items\":"},
              {"type":"text","text":"[]}"}
            ]}
        """.trimIndent()
        assertEquals("{\"items\":[]}", text(AiApiStyle.ANTHROPIC, body))
    }

    @Test
    fun geminiText_isReadFromTheFirstCandidate() {
        val body = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"ANSWER"}]},"finishReason":"STOP"}]}
        """.trimIndent()
        assertEquals("ANSWER", text(AiApiStyle.GEMINI, body))
    }

    @Test
    fun unexpectedShapes_yieldNullInsteadOfThrowing() {
        assertNull(text(AiApiStyle.OPENAI, """{"output":[]}"""))
        assertNull(text(AiApiStyle.ANTHROPIC, """{"content":[]}"""))
        assertNull(text(AiApiStyle.GEMINI, """{"candidates":[]}"""))
        assertNull(text(AiApiStyle.OPENAI, "not json at all"))
        assertNull(text(AiApiStyle.GEMINI, """{"candidates":[{"content":{"parts":[]}}]}"""))
    }

    @Test
    fun blankAnswers_countAsMissing() {
        assertNull(text(AiApiStyle.ANTHROPIC, """{"content":[{"type":"text","text":"   "}]}"""))
    }

    @Test
    fun jsonObject_isExtractedFromACodeFence() {
        val answer = "```json\n{\"items\":[{\"word\":\"run\"}]}\n```"
        assertEquals("{\"items\":[{\"word\":\"run\"}]}", AiResponseParser.extractJsonObject(answer))
    }

    @Test
    fun jsonObject_isExtractedFromSurroundingProse() {
        val answer = "Sure! Here it is: {\"items\":[]} Hope that helps."
        assertEquals("{\"items\":[]}", AiResponseParser.extractJsonObject(answer))
    }

    @Test
    fun jsonObject_spansNestedBraces() {
        val answer = "{\"items\":[{\"examples\":[{\"english_text\":\"a {b}\"}]}]}"
        assertEquals(answer, AiResponseParser.extractJsonObject(answer))
    }

    @Test
    fun missingJsonObject_yieldsNull() {
        assertNull(AiResponseParser.extractJsonObject("I cannot help with that."))
        assertNull(AiResponseParser.extractJsonObject("}"))
        assertNull(AiResponseParser.extractJsonObject(""))
    }

    @Test
    fun errorDetail_readsTheNestedProviderMessage() {
        val openAi = """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}"""
        assertEquals("Incorrect API key provided", AiResponseParser.errorDetail(json, openAi))

        val anthropic = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        assertEquals("invalid x-api-key", AiResponseParser.errorDetail(json, anthropic))

        val gemini = """{"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}"""
        assertEquals("API key not valid", AiResponseParser.errorDetail(json, gemini))
    }

    @Test
    fun errorDetail_fallsBackToABareMessageAndToleratesRubbish() {
        assertEquals("plain", AiResponseParser.errorDetail(json, """{"message":"plain"}"""))
        assertEquals("", AiResponseParser.errorDetail(json, "not json"))
        assertEquals("", AiResponseParser.errorDetail(json, """{"error":{}}"""))
    }

    @Test
    fun errorDetail_isTruncatedSoADialogCanShowIt() {
        val long = "x".repeat(AiResponseParser.MAX_DETAIL_LENGTH * 3)
        val detail = AiResponseParser.errorDetail(json, """{"error":{"message":"$long"}}""")
        assertEquals(AiResponseParser.MAX_DETAIL_LENGTH, detail.length)
    }
}
