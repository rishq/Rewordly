package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.ai.AiProviderClient
import com.rewordly.app.data.remote.GenerationResponseMapper
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationResult
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.domain.repository.AiSettingsRepository
import com.rewordly.app.domain.repository.VocabularyGenerationRepository
import com.rewordly.app.domain.service.AiPromptBuilder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Generation through the provider the user brought their own key for.
 *
 * The answer is handed to the same [GenerationResponseMapper] the backend path uses, so every existing rule
 * still applies: unusable items are dropped one by one, duplicates and excluded words are removed, and the
 * per-mode item limit is enforced. A vendor's answer is treated as no more trustworthy than the backend's.
 */
@Singleton
class DirectVocabularyGenerationRepository @Inject constructor(
    private val client: AiProviderClient,
    private val aiSettings: AiSettingsRepository,
    private val json: Json,
) : VocabularyGenerationRepository {
    /**
     * Always empty. [UsageInfo] describes request quotas, which only a metered backend can report; a provider
     * bills in tokens and exposes no equivalent, and inventing numbers would be worse than showing none.
     */
    private val _usage = MutableStateFlow<UsageInfo?>(null)
    override val usage: StateFlow<UsageInfo?> = _usage.asStateFlow()

    override suspend fun generate(request: GenerationRequest): AppResult<GenerationResult> {
        val settings = aiSettings.settings.first()
        val provider = settings.provider ?: return AppResult.Failure(AppError.AiNotConfigured)
        val apiKey = settings.apiKey
        if (apiKey.isBlank()) return AppResult.Failure(AppError.AiNotConfigured)

        val prompt = AiPromptBuilder.build(request)
        val model = settings.effectiveModel
        val dto = when (val result = client.generate(provider, model, apiKey, prompt)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        return GenerationResponseMapper.map(json, dto, request)
    }
}
