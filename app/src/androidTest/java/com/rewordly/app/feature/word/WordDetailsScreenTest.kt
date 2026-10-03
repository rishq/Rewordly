package com.rewordly.app.feature.word

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.rewordly.app.R
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.testing.setRewordlyContent
import com.rewordly.app.testing.uiString
import org.junit.Rule
import org.junit.Test

class WordDetailsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val word: Word = MockVocabulary.words.first { it.text == "achieve" }
    private val item = WordWithProgress(
        word = word,
        progress = WordProgress(
            wordId = word.id,
            status = WordStatus.LEARNING,
            isSaved = true,
            views = 4,
            correctAnswers = 2,
            incorrectAnswers = 1,
        ),
    )

    @Test
    fun showsDefinitionsExamplesAndRelations() {
        compose.setRewordlyContent { WordDetailsContent(item = item, onSearchWord = {}) }

        compose.onNodeWithText("achieve").assertIsDisplayed()
        compose.onNodeWithText("достигать").assertIsDisplayed()
        // Everything below the card has to be scrolled to: the screen is smaller than the entry.
        compose.onNodeWithText(word.definition).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("She achieved all her goals this year.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("В этом году она достигла всех своих целей.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("accomplish").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun showsDifficultyAndLearningProgress() {
        compose.setRewordlyContent { WordDetailsContent(item = item, onSearchWord = {}) }

        compose.onNodeWithText(uiString(R.string.word_section_difficulty)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.status_learning)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.word_reviews_value, 2, 1)).performScrollTo().assertIsDisplayed()
    }
}
