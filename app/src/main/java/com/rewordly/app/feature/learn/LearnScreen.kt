package com.rewordly.app.feature.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.rewordly.app.core.ui.theme.extraColors
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
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The card and the panel scroll; the two answers stay pinned to the bottom of the screen.
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            // The card takes most of the room the screen has, so the word sits in a long card and the
            // control under it still fits: a fixed height would push the control off the screen.
            val cardHeight = (maxHeight * CARD_HEIGHT_FRACTION)
                .coerceIn(Dimens.vocabularyCardMinHeight, MAX_CARD_HEIGHT)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
            ) {
                SwipeableCard(
                    leftLabel = remembered,
                    rightLabel = forgotten,
                    onSwipeLeft = { onEvent(LearnUiEvent.Answer(remembered = true)) },
                    onSwipeRight = { onEvent(LearnUiEvent.Answer(remembered = false)) },
                    enabled = !state.isSubmitting && !state.correctPick,
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
                        StudyCard(
                            word = word,
                            revealed = revealed,
                            englishFirst = state.isFirstEncounter,
                            cardHeight = cardHeight,
                            saved = state.current.progress.isSaved,
                            onToggleSaved = { onEvent(LearnUiEvent.ToggleSaved) },
                            onOpenWord = { onOpenWord(state.current.word.id) },
                        )
                    }
                }
                // A word the user has never met has nothing to recall, so the only thing to do is to read it.
                // A word in progress can be checked three ways, and the panel below answers the chosen way.
                if (state.isFirstEncounter) {
                    if (!state.revealed) {
                        RevealButton(onClick = { onEvent(LearnUiEvent.Reveal) })
                    }
                } else {
                    LearnModeSelector(state = state, onEvent = onEvent)
                }
                // The panel below grows and collapses instead of shoving the answer bar around.
                val fadeTween = tween<Float>(motionMillis(200))
                val sizeTween = tween<IntSize>(motionMillis(200))
                AnimatedContent(
                    targetState = state.panel,
                    transitionSpec = {
                        (fadeIn(fadeTween) + expandVertically(sizeTween)) togetherWith
                            (fadeOut(fadeTween) + shrinkVertically(sizeTween))
                    },
                    label = "learn-mode-panel",
                ) { panel ->
                    when (panel) {
                        LearnPanel.TYPE -> LearnTypingPanel(state = state, onEvent = onEvent)
                        LearnPanel.OPTIONS -> LearnOptionsPanel(state = state, onEvent = onEvent)
                        LearnPanel.VERDICT -> LearnVerdictPanel(state = state)
                        LearnPanel.NONE -> Box(Modifier.fillMaxWidth())
                    }
                }
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
        StudyAnswerBar(
            rememberedLabel = remembered,
            forgottenLabel = forgotten,
            onAnswer = { onEvent(LearnUiEvent.Answer(remembered = it)) },
            enabled = !state.isSubmitting && !state.correctPick,
            modifier = Modifier
                .widthIn(max = Dimens.maxContentWidth)
                .padding(top = Dimens.spaceSm),
        )
    }
}

/**
 * The card face. A word in progress is shown as its Russian translation with the English word hidden,
 * because the English word is what the user has to recall. A brand new word is the other way round: the
 * English word is the new information and the translation is what the user reveals.
 */
@Composable
private fun StudyCard(
    word: Word,
    revealed: Boolean,
    englishFirst: Boolean,
    cardHeight: Dp,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    onOpenWord: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = remember(colors) {
        Brush.verticalGradient(listOf(colors.primaryContainer, colors.surfaceContainerLowest))
    }
    val revealDuration = 250
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = cardHeight),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = Dimens.cardElevation),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = cardHeight)
                    .background(background)
                    .padding(Dimens.cardPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd, Alignment.CenterVertically),
            ) {
                if (englishFirst) {
                    EnglishFace(word)
                } else {
                    Text(
                        text = word.translation.text,
                        style = RewordlyTextStyles.cardWord,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
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
                        if (englishFirst) {
                            Text(
                                text = word.translation.text,
                                style = RewordlyTextStyles.cardWord,
                                color = colors.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            EnglishFace(word)
                        }
                    }
                }
            }
            // Saving and the full entry are one overflow menu in the corner, not two buttons on the screen.
            CardOverflowMenu(
                saved = saved,
                onToggleSaved = onToggleSaved,
                onOpenWord = onOpenWord,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.spaceSm),
            )
        }
    }
}

/** The English word and how to say it. */
@Composable
private fun EnglishFace(word: Word) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = word.text,
        style = RewordlyTextStyles.cardWord,
        color = colors.onSurface,
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

/** The two actions that do not belong on the card face, behind one button in the corner. */
@Composable
private fun CardOverflowMenu(
    saved: Boolean,
    onToggleSaved: () -> Unit,
    onOpenWord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val moreLabel = stringResource(R.string.learn_more_actions)
    val saveLabel = stringResource(if (saved) R.string.action_unsave else R.string.action_save)
    val openLabel = stringResource(R.string.learn_open_word)
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = moreLabel },
        ) {
            Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(saveLabel) },
                onClick = {
                    expanded = false
                    onToggleSaved()
                },
            )
            DropdownMenuItem(
                text = { Text(openLabel) },
                onClick = {
                    expanded = false
                    onOpenWord()
                },
            )
        }
    }
}

/** The single control of a brand new word: read the translation. */
@Composable
private fun RevealButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = scheme.onSurface,
        border = BorderStroke(width = 1.dp, color = scheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .padding(horizontal = Dimens.spaceMd, vertical = Dimens.spaceSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconMd),
            )
            Text(
                text = stringResource(R.string.learn_mode_see),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * The three ways to verify a word in progress, as separate buttons the user taps to choose from.
 *
 * Only one mode can be active, so the buttons the user did not pick leave the screen: the card is
 * answered the way the user chose, not the way that happened to be pressed last. The empty slots keep
 * their width, so the row does not jump when the other buttons disappear.
 */
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
            visible = state.answerMode == null || state.answerMode == StudyMode.TYPE,
            onClick = { onEvent(LearnUiEvent.SelectMode(StudyMode.TYPE)) },
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            label = seeLabel,
            icon = Icons.Outlined.Visibility,
            selected = state.answerMode == StudyMode.SEE,
            visible = state.answerMode == null || state.answerMode == StudyMode.SEE,
            onClick = { onEvent(LearnUiEvent.SelectMode(StudyMode.SEE)) },
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            label = optionsLabel,
            icon = Icons.Outlined.GridView,
            selected = state.answerMode == StudyMode.OPTIONS,
            visible = state.answerMode == null || state.answerMode == StudyMode.OPTIONS,
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
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) {
        // The slot keeps its width, so the row does not jump when the other modes leave the screen.
        Spacer(modifier)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val motion = tween<Color>(motionMillis(150))
    val content by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.onSurface,
        animationSpec = motion,
        label = "mode-content",
    )
    val border by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.outline,
        animationSpec = motion,
        label = "mode-border",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
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
        // The options belong to one card. Without the key the buttons are reused from card to card, and
        // the green flash of the previous card keeps fading into a button of the next one, which looks
        // like a wrong answer being marked as the right one.
        key(state.current.word.id) {
            // Rows of two equal cells; a trailing spacer keeps an odd last cell the same width as the rest,
            // so the grid never stretches one lonely option across the full line.
            state.variants.chunked(OPTION_COLUMNS).forEach { rowVariants ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    rowVariants.forEach { variant ->
                        val picked = variant == state.pickedVariant
                        VariantButton(
                            label = variant,
                            feedback = when {
                                !picked -> VariantFeedback.NONE
                                state.correctPick -> VariantFeedback.CORRECT
                                else -> VariantFeedback.WRONG
                            },
                            enabled = !state.correctPick,
                            onClick = { onEvent(LearnUiEvent.PickVariant(variant)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(OPTION_COLUMNS - rowVariants.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * A wrong answer ends the panel: the buttons are gone and the right word stands in their place, so the
 * screen gives back the space and the answer is what the user reads.
 */
@Composable
private fun LearnVerdictPanel(state: LearnUiState.Content) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        Text(
            text = stringResource(R.string.learn_type_mismatch),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.error,
        )
        Text(
            text = stringResource(R.string.learn_correct_answer),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = state.current.word.text,
            style = RewordlyTextStyles.cardTranslation,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** How the picked option reads back: green on the way to the next card, red for another try. */
private enum class VariantFeedback { NONE, CORRECT, WRONG }

@Composable
private fun VariantButton(
    label: String,
    feedback: VariantFeedback,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val good = MaterialTheme.extraColors.know
    val motion = tween<Color>(motionMillis(150))
    val container by animateColorAsState(
        targetValue = when (feedback) {
            VariantFeedback.CORRECT -> good.copy(alpha = 0.18f)
            VariantFeedback.WRONG -> scheme.error.copy(alpha = 0.14f)
            VariantFeedback.NONE -> Color.Transparent
        },
        animationSpec = motion,
        label = "variant-container",
    )
    val content by animateColorAsState(
        targetValue = when (feedback) {
            VariantFeedback.CORRECT -> good
            VariantFeedback.WRONG -> scheme.error
            VariantFeedback.NONE -> scheme.onSurface
        },
        animationSpec = motion,
        label = "variant-content",
    )
    val border by animateColorAsState(
        targetValue = when (feedback) {
            VariantFeedback.CORRECT -> good
            VariantFeedback.WRONG -> scheme.error
            VariantFeedback.NONE -> scheme.outline
        },
        animationSpec = motion,
        label = "variant-border",
    )
    val scale by animateFloatAsState(
        targetValue = if (feedback == VariantFeedback.NONE) 1f else 1.04f,
        animationSpec = tween(motionMillis(150)),
        label = "variant-scale",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = label },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = BorderStroke(width = 1.dp, color = border),
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

private val MODE_BUTTON_SIZE = 88.dp
private val VARIANT_BUTTON_MIN_HEIGHT = 56.dp
private const val OPTION_COLUMNS = 2

/** How much of the room on screen the card takes, so the control under it always stays on screen. */
private const val CARD_HEIGHT_FRACTION = 0.62f
private val MAX_CARD_HEIGHT = 520.dp
