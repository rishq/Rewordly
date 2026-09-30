package com.rewordly.app.data.repository

import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.SettingsRepository
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
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
