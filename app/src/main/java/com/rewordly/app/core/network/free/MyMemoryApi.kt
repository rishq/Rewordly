package com.rewordly.app.core.network.free

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * MyMemory, a free translation memory with a documented keyless endpoint.
 *
 * Only the translated text is deserialized. The endpoint also reports a status and a list of candidate
 * matches whose shapes are not stable (the status is a number in some responses and a string in others),
 * so reading them would buy nothing and break parsing; the repository treats a blank translation as a miss.
 */
interface MyMemoryApi {
    @GET("get")
    suspend fun translate(@Query("q") query: String, @Query("langpair") languagePair: String): MyMemoryResponse
}

@Serializable
data class MyMemoryResponse(
    @SerialName("responseData") val responseData: MyMemoryData? = null,
)

@Serializable
data class MyMemoryData(
    @SerialName("translatedText") val translatedText: String = "",
)
