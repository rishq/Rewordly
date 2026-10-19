package com.rewordly.app.feature.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.components.StudyAnswerBar
import com.rewordly.app.core.ui.components.SwipeableCard
import com.rewordly.app.core.ui.motionMillis
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.core.ui.theme.RewordlyTextStyles
import com.rewordly.app.domain.model.Word

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(onOpenWord: (String) -> Unit, viewModel: LearnViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.learn_title)) }) }) { padding ->
        when (val state = uiState) {
            LearnUiState.Loading -> LoadingState(Modifier.padding(padding))
            LearnUiState.Empty -> EmptyState(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.learn_empty_title),
                message = stringResource(R.string.learn_empty_message),
                modifier = Modifier.padding(padding),
            )
            is LearnUiState.Content -> LearnContent(
                state = state,
                onEvent = viewModel::onEvent,
                onOpenWord = onOpenWord,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
fun LearnContent(
    state: LearnUiState.Content,
    onEvent: (LearnUiEvent) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A brand new word is triaged ("do you know it?"), a word in progress is reviewed ("did you recall it?").
    val remembered = stringResource(
        if (state.isFirstEncounter) R.string.study_answer_known else R.string.study_answer_remembered,
    )
    val forgotten = stringResource(
        if (state.isFirstEncounter) R.string.study_answer_study else R.string.study_answer_forgot,
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
    ) {
        SwipeableCard(
            leftLabel = remembered,
            rightLabel = forgotten,
            onSwipeLeft = { onEvent(LearnUiEvent.Answer(remembered = true)) },
            onSwipeRight = { onEvent(LearnUiEvent.Answer(remembered = false)) },
            enabled = !state.isSubmitting,
            resetKey = state.current.word.id,
            modifier = Modifier
                .widthIn(max = Dimens.maxContentWidth)
                .fillMaxWidth(),
        ) {
            // Fading the outgoing word into the next one keeps the swipe-off, answer and new card one motion.
            Crossfade(
                targetState = state.current.word to state.revealed,
                animationSpec = tween(motionMillis(220)),
                label = "learn-card",
            ) { (word, revealed) ->
                StudyCard(word = word, revealed = revealed)
            }
        }
        LearnSecondaryActions(state = state, onEvent = onEvent, onOpenWord = onOpenWord)
        LearnModeSelector(state = state, onEvent = onEvent)
        // The panel below grows and collapses instead of shoving the answer bar around.
        val fadeTween = tween<Float>(motionMillis(200))
        val sizeTween = tween<IntSize>(motionMillis(200))
        AnimatedContent(
            targetState = state.answerMode,
            transitionSpec = {
                (fadeIn(fadeTween) + expandVertically(sizeTween)) togetherWith
                    (fadeOut(fadeTween) + shrinkVertically(sizeTween))
            },
            label = "learn-mode-panel",
        ) { mode ->
            when (mode) {
                StudyMode.TYPE -> LearnTypingPanel(state = state, onEvent = onEvent)
                StudyMode.OPTIONS -> LearnOptionsPanel(state = state, onEvent = onEvent)
                StudyMode.SEE, null -> Box(Modifier.fillMaxWidth())
            }
        }
        StudyAnswerBar(
            rememberedLabel = remembered,
            forgottenLabel = forgotten,
            onAnswer = { onEvent(LearnUiEvent.Answer(remembered = it)) },
            enabled = !state.isSubmitting,
            modifier = Modifier.widthIn(max = Dimens.maxContentWidth),
        )
        AnimatedVisibility(
            visible = state.submitFailed,
            enter = fadeIn(tween(motionMillis(200))) + expandVertically(tween(motionMillis(200))),
            exit = fadeOut(tween(motionMillis(150))) + shrinkVertically(tween(motionMillis(150))),
        ) {
            Text(
                text = stringResource(R.string.study_submit_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = Dimens.maxContentWidth)
                    .fillMaxWidth(),
            )
        }
    }
}

/**
 * The card faces the user with the Russian translation; the English word stays hidden until it is
 * revealed, so recalling it — by typing it or picking it out — is worth a real answer.
 */
@Composable
private fun StudyCard(word: Word, revealed: Boolean) {
    val colors = MaterialTheme.colorScheme
    val background = remember(colors) {
        Brush.verticalGradient(listOf(colors.primaryContainer, colors.surfaceContainerLowest))
    }
    val revealDuration = 250
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.vocabularyCardMinHeight),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = Dimens.cardElevation),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.vocabularyCardMinHeight)
                .background(background)
                .padding(Dimens.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd, Alignment.CenterVertically),
        ) {
            Text(
                text = word.translation.text,
                style = RewordlyTextStyles.cardWord,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            AnimatedVisibility(
                visible = revealed,
                enter = fadeIn(tween(revealDuration)) + expandVertically(tween(revealDuration)),
                exit = fadeOut(tween(revealDuration)) + shrinkVertically(tween(revealDuration)),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.width(Dimens.spaceHuge).padding(vertical = Dimens.spaceXs),
                        color = colors.outlineVariant,
                    )
                    Text(
                        text = word.text,
                        style = RewordlyTextStyles.cardWord,
                        color = colors.primary,
                        textAlign = TextAlign.Center,
                    )
                    if (word.pronunciation.isNotBlank()) {
                        Text(
                            text = word.pronunciation,
                            style = RewordlyTextStyles.pronunciation,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The three ways to verify the card, as separate buttons the user taps to choose from. */
@Composable
private fun LearnModeSelector(state: LearnUiState.Content, onEvent: (LearnUiEvent) -> Unit) {
    val typeLabel = stringResource(R.string.learn_mode_type)
    val seeLabel = stringResource(R.string.learn_mode_see)
    val optionsLabel = stringResource(R.string.learn_mode_options)
    Row(
        modifier = Modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        ModeButton(
            label = typeLabel,
            icon = Icons.Outlined.Keyboard,
            selected = state.answerMode == StudyMode.TYPE,
            onClick = { onEvent(LearnUiEvent.SelectMode(StudyMode.TYPE)) },
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            label = seeLabel,
            icon = Icons.Outlined.Visibility,
            selected = state.answerMode == StudyMode.SEE,
            onClick = { onEvent(LearnUiEvent.SelectMode(StudyMode.SEE)) },
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            label = optionsLabel,
            icon = Icons.Outlined.GridView,
            selected = state.answerMode == StudyMode.OPTIONS,
            onClick = { onEvent(LearnUiEvent.SelectMode(StudyMode.OPTIONS)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = tween<Color>(motionMillis(150))
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surface,
        animationSpec = motion,
        label = "mode-container",
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = motion,
        label = "mode-content",
    )
    val border by animateColorAsState(
        targetValue = if (selected) scheme.primary.copy(alpha = 0.5f) else scheme.outlineVariant,
        animationSpec = motion,
        label = "mode-border",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = BorderStroke(width = 1.dp, color = border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MODE_BUTTON_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Dimens.iconLg))
        }
    }
}

/** Typing the English word, with a per-letter hint and an explicit check. */
@Composable
private fun LearnTypingPanel(state: LearnUiState.Content, onEvent: (LearnUiEvent) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        OutlinedTextField(
            value = state.typed,
            onValueChange = { onEvent(LearnUiEvent.TypeText(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.learn_type_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            isError = state.typeMismatch,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    onEvent(LearnUiEvent.TypeCheck)
                    keyboard?.hide()
                },
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            SecondaryButton(
                text = stringResource(R.string.learn_type_hint_letter),
                onClick = { onEvent(LearnUiEvent.TypeHint) },
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.learn_type_check),
                icon = Icons.Outlined.Check,
                onClick = {
                    onEvent(LearnUiEvent.TypeCheck)
                    keyboard?.hide()
                },
                modifier = Modifier.weight(1f),
            )
        }
        AnimatedVisibility(
            visible = state.typeMismatch,
            enter = fadeIn(tween(motionMillis(200))) + expandVertically(tween(motionMillis(200))),
            exit = fadeOut(tween(motionMillis(150))) + shrinkVertically(tween(motionMillis(150))),
        ) {
            MismatchText()
        }
    }
}

/** Picking the English word from a short list of options, laid out as an even two-column grid. */
@Composable
private fun LearnOptionsPanel(state: LearnUiState.Content, onEvent: (LearnUiEvent) -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        // Rows of two equal cells; a trailing spacer keeps an odd last cell the same width as the rest,
        // so the grid never stretches one lonely option across the full line.
        state.variants.chunked(OPTION_COLUMNS).forEach { rowVariants ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                rowVariants.forEach { variant ->
                    VariantButton(
                        label = variant,
                        onClick = { onEvent(LearnUiEvent.PickVariant(variant)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(OPTION_COLUMNS - rowVariants.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        AnimatedVisibility(
            visible = state.typeMismatch,
            enter = fadeIn(tween(motionMillis(200))) + expandVertically(tween(motionMillis(200))),
            exit = fadeOut(tween(motionMillis(150))) + shrinkVertically(tween(motionMillis(150))),
        ) {
            MismatchText()
        }
    }
}

@Composable
private fun VariantButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VARIANT_BUTTON_MIN_HEIGHT)
                .padding(horizontal = Dimens.spaceMd, vertical = Dimens.spaceSm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MismatchText() {
    Text(
        text = stringResource(R.string.learn_type_mismatch),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Save and open the full entry: secondary to answering, so they are compact icon buttons. */
@Composable
private fun LearnSecondaryActions(
    state: LearnUiState.Content,
    onEvent: (LearnUiEvent) -> Unit,
    onOpenWord: (String) -> Unit,
) {
    val saved = state.current.progress.isSaved
    val saveLabel = stringResource(if (saved) R.string.action_unsave else R.string.action_save)
    val detailsDescription = stringResource(R.string.a11y_open_details, state.current.word.text)
    Row(
        modifier = Modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = { onEvent(LearnUiEvent.ToggleSaved) },
            modifier = Modifier.semantics { contentDescription = saveLabel },
        ) {
            Icon(
                imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
            )
        }
        FilledTonalIconButton(
            onClick = { onOpenWord(state.current.word.id) },
            modifier = Modifier.semantics { contentDescription = detailsDescription },
        ) {
            Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
        }
    }
}

private val MODE_BUTTON_SIZE = 88.dp
private val VARIANT_BUTTON_MIN_HEIGHT = 56.dp
private const val OPTION_COLUMNS = 2
