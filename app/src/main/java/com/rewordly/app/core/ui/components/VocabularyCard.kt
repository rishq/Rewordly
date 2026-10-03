package com.rewordly.app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.rewordly.app.R
import com.rewordly.app.core.ui.labelRes
import com.rewordly.app.core.ui.motionMillis
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.core.ui.theme.RewordlyTextStyles
import com.rewordly.app.core.ui.theme.RewordlyTheme
import com.rewordly.app.domain.model.Word

/** The visual centerpiece: a large contextual vocabulary card. */
@Composable
fun VocabularyCard(
    word: Word,
    isSaved: Boolean,
    isLearned: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showTranslation: Boolean = true,
    expanded: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val background = remember(colors) {
        Brush.verticalGradient(listOf(colors.primaryContainer, colors.surfaceContainerLowest))
    }
    val partOfSpeech = stringResource(word.partOfSpeech.labelRes)
    val difficulty = stringResource(word.difficulty.labelRes)
    val visibleExamples = word.examples.take(MAX_EXAMPLES_ON_CARD)
    // The headline is merged into a single node so a screen reader reads it as one phrase, but the
    // individual texts stay in the tree; clearing them would hide the word from tooling as well.
    val headlineDescription = listOfNotNull(
        word.text,
        stringResource(R.string.a11y_pronunciation, word.pronunciation),
        partOfSpeech,
        word.translation.text.takeIf { showTranslation },
        difficulty,
        stringResource(R.string.status_saved).takeIf { isSaved },
        stringResource(R.string.status_learned).takeIf { isLearned },
        stringResource(if (expanded) R.string.a11y_card_collapse else R.string.a11y_card_expand),
    ).joinToString(separator = ". ")

    val cardModifier = modifier
        .fillMaxWidth()
        .heightIn(min = Dimens.vocabularyCardMinHeight)
    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.background(background)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.vocabularyCardMinHeight)
                    .padding(Dimens.cardPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd, Alignment.CenterVertically),
            ) {
                CardHeader(word = word, difficulty = difficulty, isSaved = isSaved, isLearned = isLearned)
                Column(
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = headlineDescription },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                ) {
                    Text(
                        text = word.text,
                        style = RewordlyTextStyles.cardWord,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = word.pronunciation,
                        style = RewordlyTextStyles.pronunciation,
                        color = colors.onSurfaceVariant,
                    )
                    Pill(text = partOfSpeech)
                    if (showTranslation) {
                        HorizontalDivider(
                            modifier = Modifier
                                .width(Dimens.spaceHuge)
                                .padding(vertical = Dimens.spaceXs),
                            color = colors.outlineVariant,
                        )
                        Text(
                            text = word.translation.text,
                            style = RewordlyTextStyles.cardTranslation,
                            color = colors.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                val expandDuration = motionMillis(EXPAND_MILLIS)
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(expandDuration)) + expandVertically(tween(expandDuration)),
                    exit = fadeOut(tween(expandDuration)) + shrinkVertically(tween(expandDuration)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                    ) {
                        if (word.definition.isNotBlank()) {
                            CardLabel(text = stringResource(R.string.learn_definition))
                            Text(
                                text = word.definition,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                            )
                            Text(
                                text = word.definitionTranslation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        visibleExamples.forEach { example ->
                            ExampleSentence(example = example, targetWord = word.text, playable = true)
                        }
                    }
                }
            }
        }
    }
    val elevation = CardDefaults.elevatedCardElevation(defaultElevation = Dimens.cardElevation)
    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = cardModifier,
            shape = MaterialTheme.shapes.extraLarge,
            elevation = elevation,
        ) {
            content()
        }
    } else {
        ElevatedCard(modifier = cardModifier, shape = MaterialTheme.shapes.extraLarge, elevation = elevation) {
            content()
        }
    }
}

@Composable
private fun CardHeader(word: Word, difficulty: String, isSaved: Boolean, isLearned: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        Pill(text = difficulty)
        Box(Modifier.weight(1f))
        PronunciationButton(text = word.text)
        if (isSaved) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(Dimens.iconMd),
            )
        }
        if (isLearned) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = RewordlyTheme.extendedColors.success,
                modifier = Modifier.size(Dimens.iconMd),
            )
        }
    }
}

@Composable
private fun CardLabel(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Dimens.spaceSm, vertical = Dimens.spaceXxs),
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Dimens.spaceMd, vertical = Dimens.spaceXs),
        )
    }
}

private const val MAX_EXAMPLES_ON_CARD = 2
private const val EXPAND_MILLIS = 250
