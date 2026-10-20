package com.rewordly.app.data.repository

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** Reads bundled pack documents for [OfflineVocabularyRepository]. */
fun interface WordPackSource {

    fun read(assetName: String): String
}

@Module
@InstallIn(SingletonComponent::class)
internal object WordPackModule {

    /** Reads a bundled asset through the application context. */
    @Provides
    fun provideWordPackSource(@ApplicationContext context: Context): WordPackSource =
        WordPackSource { assetName -> context.assets.open(assetName).use { it.readBytes().decodeToString() } }
}
