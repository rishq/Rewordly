package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.BackendConfig
import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.model.GenerationInput
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationResult
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.testing.FakeAiSettingsRepository
import com.rewordly.app.testing.FakeVocabularyGenerationRepository
import com.rewordly.app.testing.generatedWord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The unconfigured default a stock build is shipped with. */
private const val NO_BACKEND = "https://api.rewordly.invalid/"
private const val WITH_BACKEND = "https://api.example.com/"

class AiAwareVocabularyGenerationRepositoryTest {
    private val ai = FakeAiSettingsRepository()
    private val direct = FakeVocabularyGenerationRepository()
    private val backend = FakeVocabularyGenerationRepository()
    private val request = GenerationRequest(GenerationInput.Topic("Travel"), GenerationSettings())

    private fun repository(baseUrl: String = NO_BACKEND) = AiAwareVocabularyGenerationRepository(
        direct = direct,
        backend = backend,
        aiSettings = ai,
        backendConfig = BackendConfig(baseUrl),
    )

    private suspend fun configureOwnProvider() {
        ai.setProvider(AiProvider.OPENAI)
        ai.setApiKey("sk-test")
    }

    @Test
    fun withAKey_generationGoesToTheUsersOwnProvider() = runTest {
        configureOwnProvider()
        direct.enqueue(generatedWord("voyage"))
        assertTrue(repository(WITH_BACKEND).generate(request) is AppResult.Success)
        assertEquals(1, direct.requests.size)
        assertTrue(backend.requests.isEmpty())
    }

    @Test
    fun withAKey_theOwnProviderIsPreferredEvenWhenABackendExists() = runTest {
        configureOwnProvider()
        direct.enqueue(generatedWord("voyage"))
        repository(WITH_BACKEND).generate(request)
        assertTrue("the backend should not have been called", backend.requests.isEmpty())
    }

    @Test
    fun withAKey_theOwnProviderWorksWithoutABackend() = runTest {
        configureOwnProvider()
        direct.enqueue(generatedWord("voyage"))
        // A stock build has no backend URL at all; a user key must still be enough.
        assertTrue(repository(NO_BACKEND).generate(request) is AppResult.Success)
    }

    @Test
    fun withoutAKey_aConfiguredBackendIsUsed() = runTest {
        backend.enqueue(generatedWord("voyage"))
        assertTrue(repository(WITH_BACKEND).generate(request) is AppResult.Success)
        assertEquals(1, backend.requests.size)
        assertTrue(direct.requests.isEmpty())
    }

    @Test
    fun withoutAKeyOrBackend_theErrorSaysWhatToDo() = runTest {
        assertEquals(AppResult.Failure(AppError.AiNotConfigured), repository(NO_BACKEND).generate(request))
    }

    @Test
    fun aProviderWithoutAKey_doesNotCountAsConfigured() = runTest {
        ai.setProvider(AiProvider.OPENAI)
        assertEquals(AppResult.Failure(AppError.AiNotConfigured), repository(NO_BACKEND).generate(request))
    }

    @Test
    fun aFailureFromTheOwnProvider_isPassedThroughUntouched() = runTest {
        configureOwnProvider()
        val failure = AppResult.Failure(AppError.InvalidApiKey(AiProvider.OPENAI.displayName))
        direct.results += failure
        assertEquals(failure, repository(WITH_BACKEND).generate(request))
        assertTrue("a failed own-provider call must not fall back", backend.requests.isEmpty())
    }

    @Test
    fun usage_isMirroredFromWhicheverPathAnswered() = runTest {
        backend.results += AppResult.Success(
            GenerationResult(listOf(generatedWord("voyage")), usage = UsageInfo(remainingRequests = 7)),
        )
        val repository = repository(WITH_BACKEND)
        repository.generate(request)
        assertEquals(7, repository.usage.value?.remainingRequests ?: -1)
    }

    @Test
    fun switchingToTheOwnProvider_clearsStaleBackendUsage() = runTest {
        backend.results += AppResult.Success(
            GenerationResult(listOf(generatedWord("voyage")), usage = UsageInfo(remainingRequests = 7)),
        )
        val repository = repository(WITH_BACKEND)
        repository.generate(request)
        assertEquals(7, repository.usage.value?.remainingRequests ?: -1)

        // The own provider reports no request quota, so the old numbers must not linger on screen.
        configureOwnProvider()
        direct.enqueue(generatedWord("voyage"))
        repository.generate(request)
        assertNull(repository.usage.value)
    }
}
