package com.rewordly.app.core.ui.components

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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.rewordly.app.R
import com.rewordly.app.core.ui.labelRes
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
) {
    val colors = MaterialTheme.colorScheme
    val background = remember(colors) {
        Brush.verticalGradient(listOf(colors.primaryContainer, colors.surfaceContainerLowest))
    }
    val partOfSpeech = stringResource(word.partOfSpeech.labelRes)
    val difficulty = stringResource(word.difficulty.labelRes)
    val cardDescription = listOfNotNull(
        word.text,
        stringResource(R.string.a11y_pronunciation, word.pronunciation),
        partOfSpeech,
        word.translation.text.takeIf { showTranslation },
        difficulty,
        stringResource(R.string.status_saved).takeIf { isSaved },
        stringResource(R.string.status_learned).takeIf { isLearned },
    ).joinToString(separator = ". ")

    val cardModifier = modifier
        .fillMaxWidth()
        .heightIn(min = Dimens.vocabularyCardMinHeight)
    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .background(background)
                .clearAndSetSemantics { contentDescription = cardDescription },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.vocabularyCardMinHeight)
                    .padding(Dimens.cardPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd, Alignment.CenterVertically),
            ) {
                CardHeader(difficulty = difficulty, isSaved = isSaved, isLearned = isLearned)
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
private fun CardHeader(difficulty: String, isSaved: Boolean, isLearned: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        Pill(text = difficulty)
        Box(Modifier.weight(1f))
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
