package com.rewordly.app.feature.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.ExampleSentence
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.SectionCard
import com.rewordly.app.core.ui.components.VocabularyCard
import com.rewordly.app.core.ui.components.WordChips
import com.rewordly.app.core.ui.labelRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.core.ui.theme.RewordlyTextStyles
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
private fun WordDetailsContent(item: WordWithProgress, onSearchWord: (String) -> Unit, modifier: Modifier = Modifier) {
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
            SectionCard(title = stringResource(R.string.word_section_translation)) {
                Text(word.translation.text, style = RewordlyTextStyles.cardTranslation)
            }
            SectionCard(title = stringResource(R.string.word_section_pronunciation)) {
                Text(word.pronunciation, style = RewordlyTextStyles.pronunciation)
            }
            SectionCard(title = stringResource(R.string.word_section_part_of_speech)) {
                Text(stringResource(word.partOfSpeech.labelRes), style = MaterialTheme.typography.bodyLarge)
            }
            SectionCard(title = stringResource(R.string.word_section_difficulty)) {
                Text(stringResource(word.difficulty.labelRes), style = MaterialTheme.typography.bodyLarge)
            }
            if (word.examples.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.word_section_examples)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                        word.examples.forEach { ExampleSentence(example = it) }
                    }
                }
            }
            if (word.forms.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.word_section_forms)) {
                    Text(word.forms.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (word.relatedWords.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.word_section_related)) {
                    WordChips(words = word.relatedWords, onClick = onSearchWord)
                }
            }
            if (word.synonyms.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.word_section_synonyms)) {
                    WordChips(words = word.synonyms, onClick = onSearchWord)
                }
            }
        }
    }
}
