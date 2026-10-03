package com.rewordly.app.feature.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.rewordly.app.R
import com.rewordly.app.core.common.AppError
import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.model.AiSettings
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GeneratedExample
import com.rewordly.app.domain.model.GeneratedWord
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.PreviewItem
import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.feature.ai.generate.AiGenerateContent
import com.rewordly.app.feature.ai.generate.AiGenerateEvent
import com.rewordly.app.feature.ai.generate.AiGenerateUiState
import com.rewordly.app.feature.ai.generate.GenerationStatus
import com.rewordly.app.feature.ai.preview.AI_PREVIEW_LIST_TAG
import com.rewordly.app.feature.ai.preview.AiPreviewContent
import com.rewordly.app.feature.ai.preview.AiPreviewEvent
import com.rewordly.app.feature.ai.preview.AiPreviewUiState
import com.rewordly.app.feature.ai.preview.PreviewBusy
import com.rewordly.app.feature.ai.preview.PreviewMessage
import com.rewordly.app.feature.ai.preview.SaveBar
import com.rewordly.app.testing.setRewordlyContent
import com.rewordly.app.testing.uiPlural
import com.rewordly.app.testing.uiString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AiScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<AiGenerateEvent>()
    private val previewEvents = mutableListOf<AiPreviewEvent>()
    private var openedWordId: String? = null

    // ---- hub ----

    @Test
    fun hub_offersTheThreeWorkflows_andHistory() {
        val opened = mutableListOf<GenerationMode>()
        var history = false
        compose.setRewordlyContent {
            AiHomeContent(
                usage = null,
                ai = AiSettings(),
                onOpenMode = { opened += it },
                onOpenHistory = { history = true },
            )
        }
        compose.onNodeWithTag("ai_mode_topic").performClick()
        compose.onNodeWithTag("ai_mode_text").performClick()
        compose.onNodeWithTag("ai_mode_word").performClick()
        compose.onNodeWithText(uiString(R.string.ai_action_history)).performScrollTo().performClick()
        assertEquals(listOf(GenerationMode.TOPIC, GenerationMode.TEXT, GenerationMode.WORD), opened)
        assertTrue(history)
    }

    @Test
    fun hub_showsOnlyTheUsageTheBackendReported() {
        compose.setRewordlyContent {
            AiHomeContent(UsageInfo(remainingRequests = 12, dailyUsed = 8, dailyLimit = 20), AiSettings(), {}, {})
        }
        compose.onNodeWithText(uiString(R.string.ai_usage_remaining, 12)).assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.ai_usage_daily, 8, 20)).assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.ai_usage_monthly, 1, 2)).assertDoesNotExist()
    }

    @Test
    fun hub_namesTheUsersOwnProvider_onceAKeyIsSet() {
        compose.setRewordlyContent {
            AiHomeContent(
                usage = null,
                ai = AiSettings(provider = AiProvider.OPENAI, apiKey = "sk-test"),
                onOpenMode = {},
                onOpenHistory = {},
            )
        }
        compose.onNodeWithText(uiString(R.string.ai_provider_notice_own, AiProvider.OPENAI.displayName))
            .assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.ai_provider_notice)).assertDoesNotExist()
    }

    // ---- topic form ----

    private fun showForm(state: AiGenerateUiState) {
        compose.setRewordlyContent { AiGenerateContent(state = state, onEvent = { events += it }) }
    }

    @Test
    fun topicForm_showsTopicsLevelsCountsAndOptions() {
        showForm(AiGenerateUiState(mode = GenerationMode.TOPIC))
        TopicPreset.entries.forEach { compose.onNodeWithTag("ai_preset_${it.name}").assertExists() }
        compose.onNodeWithTag("ai_preset_CUSTOM").assertExists()
        Difficulty.entries.forEach { compose.onNodeWithTag("ai_level_${it.name}").assertExists() }
        GenerationSettings.WORD_COUNT_OPTIONS.forEach { compose.onNodeWithTag("ai_count_$it").assertExists() }
        compose.onNodeWithTag("ai_opt_examples").assertIsOn()
        compose.onNodeWithTag("ai_opt_synonyms").assertIsOn()
        compose.onNodeWithTag("ai_opt_pronunciation").assertIsOn()
    }

    @Test
    fun topicForm_sendsSelectionAndSettingChanges() {
        showForm(AiGenerateUiState(mode = GenerationMode.TOPIC))
        compose.onNodeWithTag("ai_preset_TRAVEL").performScrollTo().performClick()
        compose.onNodeWithTag("ai_level_C1").performScrollTo().performClick()
        compose.onNodeWithTag("ai_count_20").performScrollTo().performClick()
        compose.onNodeWithTag("ai_opt_synonyms").performScrollTo().performClick()
        compose.onNodeWithTag("ai_generate").performScrollTo().performClick()

        assertEquals(AiGenerateEvent.SelectPreset(TopicPreset.TRAVEL), events[0])
        assertEquals(Difficulty.C1, (events[1] as AiGenerateEvent.SettingsChanged).settings.level)
        assertEquals(20, (events[2] as AiGenerateEvent.SettingsChanged).settings.wordCount)
        assertEquals(false, (events[3] as AiGenerateEvent.SettingsChanged).settings.includeSynonyms)
        assertEquals(AiGenerateEvent.Submit, events[4])
    }

    @Test
    fun topicForm_customTopicShowsAnInputField() {
        showForm(AiGenerateUiState(mode = GenerationMode.TOPIC, preset = null, customTopic = ""))
        compose.onNodeWithTag("ai_topic_input").performTextInput("Software Development")
        assertEquals(AiGenerateEvent.CustomTopicChanged("Software Development"), events.single())
    }

    @Test
    fun topicForm_presetHidesTheCustomField() {
        showForm(AiGenerateUiState(mode = GenerationMode.TOPIC, preset = TopicPreset.SCIENCE))
        compose.onNodeWithTag("ai_topic_input").assertDoesNotExist()
    }

    @Test
    fun topicForm_reflectsTheRememberedSettings() {
        val settings = GenerationSettings(Difficulty.A2, 15, includeExamples = false)
        showForm(AiGenerateUiState(mode = GenerationMode.TOPIC, settings = settings))
        compose.onNodeWithTag("ai_opt_examples").performScrollTo().assertIsOff()
    }

    // ---- text form ----

    @Test
    fun textForm_showsThePrivacyNoticeAndACounter() {
        showForm(AiGenerateUiState(mode = GenerationMode.TEXT, text = "hello"))
        compose.onNodeWithTag("ai_privacy_notice").assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.ai_text_counter, 5, 5000)).assertIsDisplayed()
        compose.onNodeWithTag("ai_count_5").assertExists()
    }

    @Test
    fun textForm_sendsTypedText_andShowsAValidationError() {
        showForm(
            AiGenerateUiState(
                mode = GenerationMode.TEXT,
                text = "too short",
                inputError = com.rewordly.app.domain.usecase.InputError.TOO_SHORT,
            ),
        )
        compose.onNodeWithText(uiString(R.string.ai_text_too_short, 40)).assertIsDisplayed()
        compose.onNodeWithTag("ai_text_input").performTextInput("more")
        assertTrue(events.single() is AiGenerateEvent.TextChanged)
    }

    @Test
    fun textForm_privacyDialog_confirmsOrCancels() {
        showForm(AiGenerateUiState(mode = GenerationMode.TEXT, showPrivacyDialog = true))
        compose.onNodeWithText(uiString(R.string.ai_privacy_title)).assertIsDisplayed()
        compose.onNodeWithTag("ai_privacy_confirm").performClick()
        compose.onNodeWithText(uiString(R.string.action_cancel)).performClick()
        assertEquals(listOf(AiGenerateEvent.ConfirmPrivacy, AiGenerateEvent.DismissPrivacy), events)
    }

    // ---- single word form ----

    @Test
    fun wordForm_hasNoCountPicker_andSendsTheTypedWord() {
        showForm(AiGenerateUiState(mode = GenerationMode.WORD))
        compose.onNodeWithTag("ai_count_5").assertDoesNotExist()
        compose.onNodeWithTag("ai_word_input").performTextInput("serendipity")
        assertEquals(AiGenerateEvent.WordChanged("serendipity"), events.single())
    }

    @Test
    fun wordForm_offersASpellingSuggestion_withoutApplyingIt() {
        showForm(AiGenerateUiState(mode = GenerationMode.WORD, word = "neccesary", suggestion = "necessary"))
        compose.onNodeWithText(uiString(R.string.ai_suggestion, "necessary")).assertIsDisplayed()
        compose.onNodeWithTag("ai_apply_suggestion").performClick()
        assertEquals(AiGenerateEvent.ApplySuggestion, events.single())
    }

    @Test
    fun wordForm_invalidInputShowsAnError() {
        showForm(
            AiGenerateUiState(
                mode = GenerationMode.WORD,
                word = "123",
                inputError = com.rewordly.app.domain.usecase.InputError.INVALID,
            ),
        )
        compose.onNodeWithText(uiString(R.string.ai_word_invalid)).assertIsDisplayed()
    }

    // ---- generation states ----

    @Test
    fun loading_showsIndeterminateProgressAndCancel_andDisablesGenerate() {
        showForm(AiGenerateUiState(mode = GenerationMode.TOPIC, status = GenerationStatus.Loading))
        compose.onNodeWithText(uiString(R.string.ai_loading)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("ai_generate").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("ai_cancel").performScrollTo().performClick()
        assertEquals(AiGenerateEvent.Cancel, events.single())
    }

    @Test
    fun idle_hasNoStatusMessageAndAnEnabledGenerateButton() {
        showForm(AiGenerateUiState(mode = GenerationMode.TOPIC))
        compose.onNodeWithTag("ai_generate").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("ai_cancel").assertDoesNotExist()
        compose.onNodeWithTag("ai_status_error").assertDoesNotExist()
    }

    @Test
    fun emptyResponse_isExplained() {
        showForm(AiGenerateUiState(mode = GenerationMode.TOPIC, status = GenerationStatus.Empty))
        compose.onNodeWithText(uiString(R.string.ai_empty_response)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun invalidResponse_hasItsOwnMessage() {
        showForm(
            AiGenerateUiState(
                mode = GenerationMode.TOPIC,
                status = GenerationStatus.Failed(AppError.InvalidResponse("x")),
            ),
        )
        compose.onNodeWithText(uiString(R.string.error_invalid_response)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rateLimit_withRetryAfter_showsTheWait() {
        showForm(
            AiGenerateUiState(mode = GenerationMode.TOPIC, status = GenerationStatus.Failed(AppError.RateLimited(30))),
        )
        compose.onNodeWithText(uiPlural(R.plurals.ai_rate_limit_retry, 30, 30)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rateLimit_inMinutes_isRoundedUp() {
        showForm(
            AiGenerateUiState(mode = GenerationMode.TOPIC, status = GenerationStatus.Failed(AppError.RateLimited(90))),
        )
        compose.onNodeWithText(
            uiPlural(R.plurals.ai_rate_limit_retry_minutes, 2, 2),
        ).performScrollTo().assertIsDisplayed()
    }

    // ---- preview ----

    private fun word(text: String, translation: String = "перевод", level: Difficulty = Difficulty.B1) = GeneratedWord(
        word = text,
        translation = translation,
        pronunciation = "/x/",
        partOfSpeech = PartOfSpeech.NOUN,
        difficulty = level,
        definition = "Definition.",
        definitionTranslation = "Определение.",
        examples = listOf(GeneratedExample("We use $text daily.", "Мы используем это ежедневно.")),
        synonyms = emptyList(),
        relatedWords = emptyList(),
    )

    private fun previewState(
        items: List<PreviewItem>,
        busy: PreviewBusy = PreviewBusy.None,
        message: PreviewMessage? = null,
        canRegenerate: Boolean = true,
        error: AppError? = null,
    ) = AiPreviewUiState.Content(
        entry = GenerationHistoryEntry("g1", GenerationMode.TOPIC, "Tech", Difficulty.B1, 10, items.size, 0L, true),
        items = items,
        canRegenerate = canRegenerate,
        busy = busy,
        message = message,
        error = error,
    )

    private val sample = listOf(
        PreviewItem(word("deploy", "развернуть"), existingWordId = null, selected = true),
        PreviewItem(word("compile", "компилировать", Difficulty.B2), existingWordId = null, selected = false),
        PreviewItem(word("refactor"), existingWordId = "en-refactor", selected = false),
    )

    private fun showPreview(state: AiPreviewUiState) {
        compose.setRewordlyContent {
            Column {
                AiPreviewContent(state, { previewEvents += it }, { openedWordId = it }, Modifier.weight(1f))
                (state as? AiPreviewUiState.Content)?.let { SaveBar(it) { event -> previewEvents += event } }
            }
        }
    }

    @Test
    fun preview_showsWordTranslationDifficultyAndOneExample() {
        showPreview(previewState(sample))
        compose.onNodeWithText("deploy").assertIsDisplayed()
        compose.onNodeWithText("развернуть").assertIsDisplayed()
        compose.onNodeWithText("We use deploy daily.").assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.difficulty_b2)).assertExists()
    }

    @Test
    fun preview_checkboxesReflectSelection_andSendToggles() {
        showPreview(previewState(sample))
        compose.onNodeWithTag("ai_check_0").assertIsOn()
        compose.onNodeWithTag("ai_check_1").assertIsOff().performClick()
        assertEquals(AiPreviewEvent.Toggle(1), previewEvents.single())
    }

    @Test
    fun preview_duplicate_isMarked_notSelectable_andOpensTheExistingWord() {
        showPreview(previewState(sample))
        // The list is lazy, so the third card only exists once the list has been scrolled to it.
        compose.onNodeWithTag(AI_PREVIEW_LIST_TAG).performScrollToNode(hasTestTag("ai_check_2"))
        compose.onNodeWithTag("ai_check_2").assertIsNotEnabled()
        compose.onNodeWithText(uiString(R.string.ai_preview_duplicate)).assertExists()
        compose.onNodeWithTag("ai_open_existing_2").performClick()
        assertEquals("en-refactor", openedWordId)
        assertTrue(previewEvents.isEmpty())
    }

    @Test
    fun preview_saveButton_countsSelectedWords_andSendsSave() {
        showPreview(previewState(sample))
        compose.onNodeWithText(uiString(R.string.ai_preview_save, 1)).assertIsDisplayed()
        compose.onNodeWithTag("ai_save").assertIsEnabled().performClick()
        assertEquals(AiPreviewEvent.Save, previewEvents.single())
    }

    @Test
    fun preview_saveIsDisabled_whenNothingIsSelected() {
        showPreview(previewState(sample.map { it.copy(selected = false) }))
        compose.onNodeWithTag("ai_save").assertIsNotEnabled()
        compose.onNodeWithText(uiString(R.string.ai_preview_save, 0)).assertIsDisplayed()
    }

    @Test
    fun preview_bulkSelectionAndRegenerationActions() {
        showPreview(previewState(sample))
        compose.onNodeWithTag("ai_select_all").performClick()
        compose.onNodeWithTag("ai_select_none").performClick()
        compose.onNodeWithTag("ai_regenerate_all").performClick()
        // The per-word actions live at the bottom of each card, below the fold on a small screen.
        compose.onNodeWithTag("ai_regen_0").performScrollTo().performClick()
        compose.onNodeWithTag("ai_details_1").performScrollTo().performClick()
        assertEquals(
            listOf(
                AiPreviewEvent.SetAllSelected(true),
                AiPreviewEvent.SetAllSelected(false),
                AiPreviewEvent.RegenerateAll,
                AiPreviewEvent.RegenerateWord(0),
                AiPreviewEvent.OpenDetail(1),
            ),
            previewEvents,
        )
    }

    @Test
    fun preview_detailsSheet_showsTheFullCard() {
        showPreview(previewState(sample).copy(detailIndex = 0))
        compose.onNodeWithText(uiString(R.string.learn_definition)).assertExists()
    }

    @Test
    fun preview_whenRegenerationIsUnavailable_explainsWhy() {
        showPreview(previewState(sample, canRegenerate = false))
        compose.onNodeWithText(uiString(R.string.ai_preview_regeneration_unavailable)).assertIsDisplayed()
        compose.onNodeWithTag("ai_regenerate_all").assertDoesNotExist()
        compose.onNodeWithTag("ai_regen_0").assertDoesNotExist()
    }

    @Test
    fun preview_whileBusy_actionsAreDisabled() {
        showPreview(previewState(sample, busy = PreviewBusy.RegeneratingAll))
        compose.onNodeWithTag("ai_regenerate_all").assertIsNotEnabled()
        compose.onNodeWithTag("ai_save").assertIsNotEnabled()
        compose.onNodeWithText(uiString(R.string.ai_preview_regenerating)).assertIsDisplayed()
    }

    @Test
    fun preview_afterSaving_confirmsHowManyWordsWereAdded() {
        showPreview(previewState(sample, message = PreviewMessage.Saved(2)))
        compose.onNodeWithText(uiPlural(R.plurals.ai_saved_message, 2, 2)).assertIsDisplayed()
    }

    @Test
    fun preview_error_isShown() {
        showPreview(previewState(sample, error = AppError.RateLimited(null)))
        compose.onNodeWithText(uiString(R.string.error_rate_limited)).assertIsDisplayed()
    }

    @Test
    fun preview_missingGeneration_showsANotFoundMessage() {
        showPreview(AiPreviewUiState.NotFound)
        compose.onNodeWithText(uiString(R.string.ai_preview_not_found)).assertIsDisplayed()
    }
}
