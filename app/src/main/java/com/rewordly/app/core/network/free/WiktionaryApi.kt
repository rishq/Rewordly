package com.rewordly.app.core.network.free

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Wiktionary's own action API, used for the wikitext of one page.
 *
 * The parsed page is not used on purpose: the rendered HTML is heavy and loses the pronunciation
 * templates, while the wikitext carries them verbatim for the parser to read.
 *
 * The fixed query parameters live in the path so the interface stays a single call. `redirects=1` lets
 * an inflected form such as "ran" resolve to its lemma instead of failing.
 */
interface WiktionaryApi {
    @GET("w/api.php?action=parse&prop=wikitext&format=json&formatversion=2&redirects=1")
    suspend fun wikitext(@Query("page") page: String): WiktionaryParseResponse
}

@Serializable
data class WiktionaryParseResponse(
    @SerialName("parse") val parse: WiktionaryParse? = null,
    /** Set instead of [parse] when the page does not exist. */
    @SerialName("error") val error: WiktionaryError? = null,
)

@Serializable
data class WiktionaryParse(
    @SerialName("title") val title: String = "",
    @SerialName("wikitext") val wikitext: String = "",
)

@Serializable
data class WiktionaryError(
    @SerialName("code") val code: String = "",
    @SerialName("info") val info: String = "",
)
