package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.BackendConfig
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationResult
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.domain.repository.AiSettingsRepository
import com.rewordly.app.domain.repository.VocabularyGenerationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Decides where a generation goes.
 *
 * The user's own provider wins whenever a key is configured, because that is the path they explicitly set up and
 * they pay for it. Otherwise the project's backend is used, which is only present when the app was built with a
 * backend URL. With neither, the error says what to do instead of failing as a generic server problem.
 */
@Singleton
class AiAwareVocabularyGenerationRepository @Inject constructor(
    @OwnProviderGeneration private val direct: VocabularyGenerationRepository,
    @BackendGeneration private val backend: VocabularyGenerationRepository,
    private val aiSettings: AiSettingsRepository,
    private val backendConfig: BackendConfig,
) : VocabularyGenerationRepository {
    /**
     * Mirrors whichever path served the last generation. The user's own provider reports no request quota, so
     * switching to it clears any numbers the backend had reported earlier rather than leaving stale ones on screen.
     */
    private val _usage = MutableStateFlow<UsageInfo?>(null)
    override val usage: StateFlow<UsageInfo?> = _usage.asStateFlow()

    override suspend fun generate(request: GenerationRequest): AppResult<GenerationResult> {
        val result = if (aiSettings.settings.first().isReady) direct.generate(request) else throughBackend(request)
        _usage.value = (result as? AppResult.Success)?.data?.usage
        return result
    }

    private suspend fun throughBackend(request: GenerationRequest): AppResult<GenerationResult> =
        if (backendConfig.isConfigured) {
            backend.generate(request)
        } else {
            AppResult.Failure(AppError.AiNotConfigured)
        }
}
