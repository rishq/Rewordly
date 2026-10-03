package com.rewordly.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {
    @Test
    fun unknownStoredName_isDroppedInsteadOfFailing() {
        assertNull(AiProvider.fromName("GEMINI_V2"))
        assertNull(AiProvider.fromName(null))
        assertNull(AiProvider.fromName(""))
    }

    @Test
    fun storedNames_roundTrip() {
        AiProvider.entries.forEach { provider ->
            assertEquals(provider, AiProvider.fromName(provider.name))
        }
    }

    @Test
    fun everyProvider_isUsableWithoutExtraConfiguration() {
        AiProvider.entries.forEach { provider ->
            assertTrue("${provider.name} needs an https base", provider.baseUrl.startsWith("https://"))
            assertTrue("${provider.name} base must end in a slash", provider.baseUrl.endsWith("/"))
            assertTrue("${provider.name} needs a default model", provider.defaultModel.isNotBlank())
            assertTrue(
                "${provider.name} default must be one of its suggestions",
                provider.defaultModel in provider.suggestedModels,
            )
            assertTrue("${provider.name} needs a console url", provider.keyConsoleUrl.startsWith("https://"))
        }
    }

    @Test
    fun theDefault_isTheCheapestTierAndIsListedFirst() {
        // Word generation is a small, tightly-specified structured task, so the default is deliberately the
        // cheap tier rather than the flagship. Keeping it first in the list makes the one-tap choices read
        // cheapest to strongest, and pins the convention against a future reorder.
        AiProvider.entries.forEach { provider ->
            assertEquals(
                "${provider.name} should default to the first (cheapest) suggestion",
                provider.suggestedModels.first(),
                provider.defaultModel,
            )
        }
    }

    @Test
    fun blankModel_fallsBackToTheProviderDefault() {
        val settings = AiSettings(provider = AiProvider.ANTHROPIC, model = "   ")
        assertEquals(AiProvider.ANTHROPIC.defaultModel, settings.effectiveModel)
    }

    @Test
    fun customModel_isTrimmedAndUsed() {
        val settings = AiSettings(provider = AiProvider.OPENAI, model = "  gpt-6-luna  ")
        assertEquals("gpt-6-luna", settings.effectiveModel)
    }

    @Test
    fun effectiveModel_isEmptyWithoutAProvider() {
        assertEquals("", AiSettings(model = "gpt-6-luna").effectiveModel)
    }

    @Test
    fun isReady_requiresBothAProviderAndAKey() {
        assertFalse(AiSettings().isReady)
        assertFalse(AiSettings(provider = AiProvider.OPENAI).isReady)
        assertFalse(AiSettings(apiKey = "sk-test").isReady)
        assertTrue(AiSettings(provider = AiProvider.OPENAI, apiKey = "sk-test").isReady)
    }

    @Test
    fun providers_coverTheThreeStylesOfferedInSettings() {
        assertEquals(AiApiStyle.OPENAI, AiProvider.OPENAI.apiStyle)
        assertEquals(AiApiStyle.ANTHROPIC, AiProvider.ANTHROPIC.apiStyle)
        assertEquals(AiApiStyle.GEMINI, AiProvider.GOOGLE.apiStyle)
    }
}
