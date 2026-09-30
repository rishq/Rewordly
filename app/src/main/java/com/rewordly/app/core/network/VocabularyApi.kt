package com.rewordly.app.core.network

import com.rewordly.app.core.network.model.WordDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Future vocabulary content endpoint. Not called in STEP 1 — the app uses bundled local data. */
interface VocabularyApi {
    @GET("v1/words")
    suspend fun getWords(
        @Query("language") language: String,
        @Query("translation_language") translationLanguage: String,
    ): List<WordDto>
}
