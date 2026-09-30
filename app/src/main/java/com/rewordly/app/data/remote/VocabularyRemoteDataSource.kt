package com.rewordly.app.data.remote

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.VocabularyApi
import com.rewordly.app.core.network.model.WordDto
import com.rewordly.app.core.network.safeApiCall
import javax.inject.Inject

/** Remote vocabulary source. Prepared for later steps; STEP 1 only uses bundled local data. */
class VocabularyRemoteDataSource @Inject constructor(
    private val api: VocabularyApi,
) {
    suspend fun fetchWords(language: String, translationLanguage: String): AppResult<List<WordDto>> =
        safeApiCall { api.getWords(language, translationLanguage) }
}
