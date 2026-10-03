package com.rewordly.app.core.datastore

import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.model.AiSettings
import com.rewordly.app.testing.FakeSecretCipher
import com.rewordly.app.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPreferencesDataSourceTest {
    private val store = InMemoryPreferencesDataStore()
    private val cipher = FakeSecretCipher()
    private val source = AiPreferencesDataSource(store, cipher)

    /** Everything actually written to preferences, whatever it is keyed under. */
    private suspend fun storedValues(): List<String> = store.data.first().asMap().values.filterIsInstance<String>()

    @Test
    fun anEmptyStore_readsAsUnconfigured() = runTest {
        assertEquals(AiSettings(), source.settings.first())
    }

    @Test
    fun theKeyIsNeverWrittenInTheClear() = runTest {
        source.setApiKey(API_KEY)
        assertFalse("the raw key reached preferences", storedValues().contains(API_KEY))
        assertTrue("nothing was encrypted", storedValues().any { it.startsWith("enc:") })
    }

    @Test
    fun theKeyRoundTripsThroughTheCipher() = runTest {
        source.setApiKey(API_KEY)
        assertEquals(API_KEY, source.settings.first().apiKey)
    }

    @Test
    fun aPastedKeyLosesItsSurroundingWhitespace() = runTest {
        source.setApiKey("  $API_KEY\n")
        assertEquals(API_KEY, source.settings.first().apiKey)
    }

    @Test
    fun clearingTheKey_removesEveryTraceOfIt() = runTest {
        source.setApiKey(API_KEY)
        source.setApiKey(null)
        assertEquals("", source.settings.first().apiKey)
        assertTrue(storedValues().isEmpty())
    }

    @Test
    fun aBlankKey_isTreatedAsNoKeyAtAll() = runTest {
        source.setApiKey(API_KEY)
        source.setApiKey("   ")
        assertEquals("", source.settings.first().apiKey)
    }

    @Test
    fun switchingProvider_clearsTheModelThatBelongedToTheOldOne() = runTest {
        source.setProvider(AiProvider.OPENAI)
        source.setModel("gpt-6-luna")
        source.setProvider(AiProvider.ANTHROPIC)
        val settings = source.settings.first()
        assertEquals(AiProvider.ANTHROPIC, settings.provider)
        assertEquals("", settings.model)
        assertEquals(AiProvider.ANTHROPIC.defaultModel, settings.effectiveModel)
    }

    @Test
    fun reselectingTheSameProvider_keepsTheModel() = runTest {
        source.setProvider(AiProvider.OPENAI)
        source.setModel("gpt-6-luna")
        source.setProvider(AiProvider.OPENAI)
        assertEquals("gpt-6-luna", source.settings.first().model)
    }

    @Test
    fun theKeySurvivesAProviderChange() = runTest {
        source.setApiKey(API_KEY)
        source.setProvider(AiProvider.GOOGLE)
        assertEquals(API_KEY, source.settings.first().apiKey)
    }

    @Test
    fun clearingTheProvider_keepsTheKeyForLater() = runTest {
        source.setProvider(AiProvider.GOOGLE)
        source.setApiKey(API_KEY)
        source.setProvider(null)
        val settings = source.settings.first()
        assertEquals(null, settings.provider)
        assertEquals(API_KEY, settings.apiKey)
        assertFalse(settings.isReady)
    }

    @Test
    fun aBlankModel_restoresTheProviderDefault() = runTest {
        source.setProvider(AiProvider.OPENAI)
        source.setModel("gpt-6-luna")
        source.setModel("   ")
        assertEquals("", source.settings.first().model)
    }

    @Test
    fun aStoredModelIsTrimmed() = runTest {
        source.setProvider(AiProvider.OPENAI)
        source.setModel("  gpt-6-luna  ")
        assertEquals("gpt-6-luna", source.settings.first().model)
    }

    private companion object {
        const val API_KEY = "sk-proj-abcdefghijklmnop"
    }
}
