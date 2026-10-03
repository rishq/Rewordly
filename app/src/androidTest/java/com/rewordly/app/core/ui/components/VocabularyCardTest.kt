package com.rewordly.app.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rewordly.app.R
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.Word
import com.rewordly.app.testing.setRewordlyContent
import com.rewordly.app.testing.uiString
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VocabularyCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val beautiful: Word = MockVocabulary.words.first { it.text == "beautiful" }

    @Test
    fun showsWordPronunciationAndTranslation() {
        compose.setRewordlyContent {
            VocabularyCard(word = beautiful, isSaved = false, isLearned = false)
        }
        compose.onNodeWithText("beautiful").assertIsDisplayed()
        compose.onNodeWithText("красивый").assertIsDisplayed()
        compose.onNodeWithText(beautiful.pronunciation).assertIsDisplayed()
    }

    @Test
    fun collapsedCard_hidesDefinitionAndExamples() {
        compose.setRewordlyContent {
            VocabularyCard(word = beautiful, isSaved = false, isLearned = false, expanded = false)
        }
        compose.onNodeWithText("pleasing to look at or experience").assertDoesNotExist()
    }

    @Test
    fun expandedCard_showsDefinitionAndFirstExample() {
        compose.setRewordlyContent {
            VocabularyCard(word = beautiful, isSaved = false, isLearned = false, expanded = true)
        }
        compose.onNodeWithText(uiString(R.string.learn_definition)).assertIsDisplayed()
        compose.onNodeWithText("pleasing to look at or experience").assertIsDisplayed()
        compose.onNodeWithText("У неё красивый голос.", substring = true).assertIsDisplayed()
    }

    @Test
    fun cardClick_isForwarded() {
        var clicks = 0
        compose.setRewordlyContent {
            VocabularyCard(word = beautiful, isSaved = false, isLearned = false, onClick = { clicks++ })
        }
        compose.onNodeWithText("beautiful").performClick()
        assertTrue(clicks > 0)
    }

    @Test
    fun pronounceButton_exposesItsPurpose() {
        compose.setRewordlyContent {
            VocabularyCard(word = beautiful, isSaved = false, isLearned = false)
        }
        compose.onNodeWithContentDescription(uiString(R.string.a11y_pronounce, "beautiful")).assertIsDisplayed()
    }
}
