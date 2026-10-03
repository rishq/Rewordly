package com.rewordly.app.domain.model

/** One multiple-choice question of the optional placement check. */
data class PlacementQuestion(
    val id: String,
    /** The English word the user has to recognise. */
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    /** The CEFR level this question is taken from. */
    val level: Difficulty,
)

/**
 * The approximate level the placement check produced. This is a rough hint for recommendations,
 * never an official English proficiency result, and the user can always set the level by hand.
 */
data class PlacementResult(
    val level: Difficulty,
    val correctAnswers: Int,
    val answered: Int,
) {
    /** With very few answers the estimate is barely better than a guess. */
    val isConfident: Boolean get() = answered >= MIN_CONFIDENT_ANSWERS

    companion object {
        const val MIN_CONFIDENT_ANSWERS = 4
    }
}
