package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.PlacementQuestion
import com.rewordly.app.domain.model.PlacementResult
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordKeys

/**
 * Builds the short optional placement check from the vocabulary that is already on the device and
 * turns the answers into an approximate CEFR level. Pure and deterministic: the same vocabulary
 * always produces the same questions in the same order, from easy to hard.
 *
 * This is a study aid, not a certification. The caller shows the result as an estimate and lets the
 * user override it.
 */
object PlacementAssessor {
    /** How many questions each CEFR level contributes. */
    const val QUESTIONS_PER_LEVEL = 2

    /** Number of answer options per question, including the correct one. */
    const val OPTION_COUNT = 4

    /** Share of a level's questions that must be right to move past that level. */
    const val PASS_RATIO = 0.5f

    /** Questions ordered from A1 to C2; levels without enough vocabulary are skipped. */
    fun questions(words: List<Word>, perLevel: Int = QUESTIONS_PER_LEVEL): List<PlacementQuestion> =
        Difficulty.entries.flatMap { level ->
            val pool = words.filter { it.difficulty == level }.sortedBy { WordKeys.normalize(it.text) }
            pool.take(perLevel).mapNotNull { word ->
                optionsFor(word, words)?.let { (options, correctIndex) ->
                    PlacementQuestion(
                        id = "placement-${level.name}-${WordKeys.normalize(word.text)}",
                        prompt = word.text,
                        options = options,
                        correctIndex = correctIndex,
                        level = level,
                    )
                }
            }
        }

    /**
     * Estimates the level from the answers.
     *
     * Levels are walked from A1 upwards and the walk stops at the first level the user failed, so a
     * high level is only ever claimed after every easier level was passed. Skipped questions simply
     * do not count; with no answers at all the safest estimate is A1.
     */
    fun assess(questions: List<PlacementQuestion>, answers: Map<String, Boolean>): PlacementResult {
        var level = Difficulty.entries.first()
        var correct = 0
        var answered = 0
        for (candidate in Difficulty.entries) {
            val ofLevel = questions.filter { it.level == candidate }
            val given = ofLevel.mapNotNull { question -> answers[question.id]?.let { question to it } }
            if (given.isEmpty()) continue
            val correctOfLevel = given.count { it.second }
            correct += correctOfLevel
            answered += given.size
            if (correctOfLevel.toFloat() / given.size >= PASS_RATIO) {
                level = candidate
            } else {
                break
            }
        }
        return PlacementResult(level = level, correctAnswers = correct, answered = answered)
    }

    /**
     * Four options for [word]: the correct translation plus three deterministic distractors.
     * Words of the same level are preferred so no option is obviously out of place.
     */
    private fun optionsFor(word: Word, all: List<Word>): Pair<List<String>, Int>? {
        val correct = word.translation.text
        val sameLevel = all.filter { it.difficulty == word.difficulty }
        val ordered = (sameLevel + all).map { it.translation.text }
            .filter { it != correct }
            .distinct()
            .take(OPTION_COUNT - 1)
        if (ordered.size < OPTION_COUNT - 1) return null
        // Sorted so the position of the answer carries no information.
        val options = (ordered + correct).sorted()
        return options to options.indexOf(correct)
    }
}
