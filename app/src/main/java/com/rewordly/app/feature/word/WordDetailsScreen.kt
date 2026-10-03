package com.rewordly.app.feature.word

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.ExampleSentence
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.Pill
import com.rewordly.app.core.ui.components.SectionCard
import com.rewordly.app.core.ui.components.VocabularyCard
import com.rewordly.app.core.ui.components.WordChips
import com.rewordly.app.core.ui.labelRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailsScreen(
    onBack: () -> Unit,
    onSearchWord: (String) -> Unit,
    viewModel: WordDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.word_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    val content = uiState as? WordDetailsUiState.Content
                    if (content != null) {
                        val saved = content.item.progress.isSaved
                        IconButton(onClick = viewModel::toggleSaved) {
                            Icon(
                                imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(
                                    if (saved) R.string.action_unsave else R.string.action_save,
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            WordDetailsUiState.Loading -> LoadingState(Modifier.padding(padding))
            WordDetailsUiState.NotFound -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.word_not_found_title),
                message = stringResource(R.string.word_not_found_message),
                modifier = Modifier.padding(padding),
            )
            is WordDetailsUiState.Content -> WordDetailsContent(
                item = state.item,
                onSearchWord = onSearchWord,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
fun WordDetailsContent(item: WordWithProgress, onSearchWord: (String) -> Unit, modifier: Modifier = Modifier) {
    val word = item.word
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.maxContentWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            VocabularyCard(
                word = word,
                isSaved = item.progress.isSaved,
                isLearned = item.progress.status == WordStatus.LEARNED,
            )
            if (word.definition.isNotBlank()) {
                SectionCard(title = stringResource(R.string.learn_definition)) {
                    Text(word.definition, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = word.definitionTranslation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (word.examples.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.word_section_examples)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                        word.examples.forEach { example ->
                            ExampleSentence(
                                example = example,
                                targetWord = word.text,
                                playable = true,
                            )
                        }
                    }
                }
            }
            if (word.forms.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.word_section_forms)) {
                    Text(word.forms.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (word.synonyms.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.word_section_synonyms)) {
                    WordChips(words = word.synonyms, onClick = onSearchWord)
                }
            }
            if (word.relatedWords.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.word_section_related)) {
                    WordChips(words = word.relatedWords, onClick = onSearchWord)
                }
            }
            SectionCard(title = stringResource(R.string.word_section_difficulty)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    Pill(text = stringResource(word.difficulty.labelRes))
                    Text(
                        text = stringResource(word.partOfSpeech.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            LearningProgressSection(item)
        }
    }
}

@Composable
private fun LearningProgressSection(item: WordWithProgress) {
    val progress = item.progress
    val status = stringResource(
        when (progress.status) {
            WordStatus.NEW -> R.string.status_new
            WordStatus.LEARNING -> R.string.status_learning
            WordStatus.LEARNED -> R.string.status_learned
        },
    )
    SectionCard(title = stringResource(R.string.word_section_progress)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Pill(text = status)
            Text(
                text = pluralStringResource(R.plurals.views_count, progress.views, progress.views),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = stringResource(R.string.word_reviews_value, progress.correctAnswers, progress.incorrectAnswers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        progress.lastViewedAt?.let { viewedAt ->
            Text(
                text = stringResource(R.string.word_last_viewed, relativeTime(viewedAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun relativeTime(millis: Long): String = DateUtils.getRelativeTimeSpanString(
    millis,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()
