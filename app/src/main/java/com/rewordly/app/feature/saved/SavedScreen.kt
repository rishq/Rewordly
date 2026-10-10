package com.rewordly.app.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.components.WordListItem
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.feature.add.AddWordSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(onBack: () -> Unit, onOpenWord: (String) -> Unit, viewModel: SavedViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddWord by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.saved_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddWord = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_word_action))
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val state = uiState) {
            SavedUiState.Loading -> LoadingState(modifier)
            SavedUiState.NoSavedWords -> EmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = stringResource(R.string.saved_empty_title),
                message = stringResource(R.string.saved_empty_message),
                modifier = modifier,
            )
            SavedUiState.NoMatches -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.saved_no_matches_title),
                message = stringResource(R.string.saved_no_matches_message),
                modifier = modifier,
            )
            is SavedUiState.Content -> SavedContent(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onOpenWord = onOpenWord,
                modifier = modifier,
            )
        }
    }
    if (showAddWord) {
        AddWordSheet(
            initialWord = "",
            onDismiss = { showAddWord = false },
            onOpenWord = { wordId ->
                showAddWord = false
                onOpenWord(wordId)
            },
        )
    }
}

@Composable
fun SavedContent(
    state: SavedUiState.Content,
    onQueryChange: (String) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding),
            placeholder = { Text(stringResource(R.string.saved_search_hint)) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        if (state.words.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.saved_no_matches_title),
                message = stringResource(R.string.saved_no_matches_message),
                action = {
                    SecondaryButton(
                        text = stringResource(R.string.action_clear),
                        onClick = { onQueryChange("") },
                    )
                },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                items(state.words, key = { it.word.id }) { item ->
                    WordListItem(item = item, onClick = { onOpenWord(item.word.id) })
                }
            }
        }
    }
}
