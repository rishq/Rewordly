package com.rewordly.app.data.repository

import com.rewordly.app.domain.repository.AiSettingsRepository
import com.rewordly.app.domain.repository.DataManagementRepository
import com.rewordly.app.domain.repository.GenerationHistoryRepository
import com.rewordly.app.domain.repository.GenerationSettingsRepository
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.ReviewRepository
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyGenerationRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindVocabularyRepository(impl: OfflineVocabularyRepository): VocabularyRepository

    @Binds
    abstract fun bindProgressRepository(impl: DefaultProgressRepository): ProgressRepository

    @Binds
    abstract fun bindReviewRepository(impl: OfflineReviewRepository): ReviewRepository

    @Binds
    @BackendGeneration
    abstract fun bindBackendGeneration(impl: RemoteVocabularyGenerationRepository): VocabularyGenerationRepository

    @Binds
    @OwnProviderGeneration
    abstract fun bindOwnProviderGeneration(impl: DirectVocabularyGenerationRepository): VocabularyGenerationRepository

    @Binds
    abstract fun bindVocabularyGenerationRepository(
        impl: AiAwareVocabularyGenerationRepository,
    ): VocabularyGenerationRepository

    @Binds
    abstract fun bindAiSettingsRepository(impl: DataStoreAiSettingsRepository): AiSettingsRepository

    @Binds
    abstract fun bindGenerationHistoryRepository(impl: OfflineGenerationHistoryRepository): GenerationHistoryRepository

    @Binds
    abstract fun bindGenerationSettingsRepository(
        impl: DataStoreGenerationSettingsRepository,
    ): GenerationSettingsRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindDataManagementRepository(impl: OfflineDataManagementRepository): DataManagementRepository
}
