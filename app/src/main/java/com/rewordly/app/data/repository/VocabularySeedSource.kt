package com.rewordly.app.data.repository

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
/** Supplies the bundled starter vocabulary content for [OfflineVocabularyRepository.ensureSeeded]. */
fun interface VocabularySeedSource {

    fun read(): String
}

private const val SEED_ASSET = "seed_vocabulary.csv"

@Module
@InstallIn(SingletonComponent::class)
internal object VocabularySeedModule {

    /** Reads the bundled CSV asset through the application context. */
    @Provides
    fun provideSeedSource(@ApplicationContext context: Context): VocabularySeedSource =
        VocabularySeedSource { context.assets.open(SEED_ASSET).use { it.readBytes().decodeToString() } }
}
