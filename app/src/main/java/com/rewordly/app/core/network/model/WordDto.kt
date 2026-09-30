package com.rewordly.app.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire format for vocabulary content served by a future content backend. */
@Serializable
data class WordDto(
    @SerialName("id") val id: String,
    @SerialName("language") val language: String,
    @SerialName("text") val text: String,
    @SerialName("translation") val translation: String,
    @SerialName("translation_language") val translationLanguage: String,
    @SerialName("pronunciation") val pronunciation: String? = null,
    @SerialName("part_of_speech") val partOfSpeech: String? = null,
    @SerialName("difficulty") val difficulty: String? = null,
    @SerialName("examples") val examples: List<WordExampleDto> = emptyList(),
)

@Serializable
data class WordExampleDto(
    @SerialName("id") val id: String,
    @SerialName("text") val text: String,
    @SerialName("translation") val translation: String,
)
