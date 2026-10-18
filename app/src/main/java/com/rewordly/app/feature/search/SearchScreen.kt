package com.rewordly.app.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.WordListItem
import com.rewordly.app.core.ui.filterRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.DifficultyFilter
import com.rewordly.app.domain.model.StatusFilter
import com.rewordly.app.domain.model.VocabularyFilters
import com.rewordly.app.feature.add.AddWordSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onOpenWord: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    SearchSurface(onOpenWord = onOpenWord, embedded = false, viewModel = viewModel)
}

/** The search UI embedded as a section inside the profile tab, without its own top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSection(onOpenWord: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    SearchSurface(onOpenWord = onOpenWord, embedded = true, viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSurface(onOpenWord: (String) -> Unit, embedded: Boolean, viewModel: SearchViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filters by viewModel.activeFilters.collectAsStateWithLifecycle()

    /** The word the add sheet was opened with, or null while it is closed. */
    var addWordFor by remember { mutableStateOf<String?>(null) }
    SearchContent(
        query = viewModel.query,
        onQueryChange = viewModel::onQueryChange,
        uiState = uiState,
        filters = filters,
        onFiltersChange = viewModel::onFiltersChange,
        onOpenWord = onOpenWord,
        embedded = embedded,
        onAddWord = { word -> addWordFor = word },
    )
    addWordFor?.let { word ->
        AddWordSheet(
            initialWord = word,
            onDismiss = { addWordFor = null },
            onOpenWord = { wordId ->
                addWordFor = null
                onOpenWord(wordId)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    uiState: SearchUiState,
    filters: VocabularyFilters,
    onFiltersChange: (VocabularyFilters) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Inside the profile tab: no own top bar, and plain lists because the parent already scrolls. */
    embedded: Boolean = false,
    /** Opens the add-word sheet for the given word; blank means the user starts from an empty field. */
    onAddWord: (String) -> Unit = {},
) {
    if (embedded) {
        SearchBody(
            query,
            onQueryChange,
            uiState,
            filters,
            onFiltersChange,
            onOpenWord,
            onAddWord,
            embedded = true,
            modifier = modifier,
        )
    } else {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.search_title)) },
                    actions = {
                        IconButton(onClick = { onAddWord("") }) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_word_action))
                        }
                    },
                )
            },
        ) { padding ->
            SearchBody(
                query,
                onQueryChange,
                uiState,
                filters,
                onFiltersChange,
                onOpenWord,
                onAddWord,
                embedded = false,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBody(
    query: String,
    onQueryChange: (String) -> Unit,
    uiState: SearchUiState,
    filters: VocabularyFilters,
    onFiltersChange: (VocabularyFilters) -> Unit,
    onOpenWord: (String) -> Unit,
    onAddWord: (String) -> Unit,
    embedded: Boolean,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { keyboard?.hide() },
            ),
        )
        FilterRow(filters = filters, onFiltersChange = onFiltersChange)
        when (val state = uiState) {
            SearchUiState.Loading -> LoadingState()
            is SearchUiState.NoResults -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.search_empty_title),
                message = if (state.query.isBlank()) {
                    stringResource(R.string.search_empty_filters_message)
                } else {
                    stringResource(R.string.search_empty_message, state.query)
                },
                action = {
                    // An empty query with active filters is not a word to look up, so the offer only
                    // appears when the user actually typed something that is missing.
                    if (state.query.isNotBlank()) {
                        PrimaryButton(
                            text = stringResource(R.string.add_word_empty_action, state.query.trim()),
                            onClick = { onAddWord(state.query.trim()) },
                            icon = Icons.Outlined.Add,
                        )
                    }
                },
            )
            is SearchUiState.Results -> if (embedded) {
                Column(
                    modifier = Modifier.padding(horizontal = Dimens.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    state.words.forEach { item ->
                        WordListItem(item = item, onClick = { onOpenWord(item.word.id) })
                    }
                }
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
}

@Composable
private fun FilterRow(filters: VocabularyFilters, onFiltersChange: (VocabularyFilters) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            DifficultyFilter.entries.forEach { option ->
                FilterChip(
                    selected = filters.difficulty == option,
                    onClick = { onFiltersChange(filters.copy(difficulty = option)) },
                    label = { Text(stringResource(option.filterRes)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            StatusFilter.entries.forEach { option ->
                FilterChip(
                    selected = filters.status == option,
                    onClick = { onFiltersChange(filters.copy(status = option)) },
                    label = { Text(stringResource(option.filterRes)) },
                )
            }
        }
    }
}
