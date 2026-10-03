package com.rewordly.app.core.audio

import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    abstract fun bindPronunciationEngine(impl: AndroidTtsPronunciationEngine): PronunciationEngine
}

/** Lets the activity read the singleton engine for [LocalPronunciationEngine]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PronunciationEngineEntryPoint {
    fun pronunciationEngine(): PronunciationEngine
}
