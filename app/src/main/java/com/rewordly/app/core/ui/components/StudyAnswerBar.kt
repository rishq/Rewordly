package com.rewordly.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rewordly.app.core.ui.theme.Dimens

/**
 * The two answers of a study card, side by side and impossible to miss: the left half says the word is
 * known/remembered, the right half says it still has to be studied. Tapping either half is exactly the
 * same as swiping the card that way, which also makes the gesture usable with a screen reader.
 */
@Composable
fun StudyAnswerBar(
    rememberedLabel: String,
    forgottenLabel: String,
    onAnswer: (remembered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ANSWER_BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        AnswerHalf(
            label = rememberedLabel,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            iconFirst = true,
            container = scheme.primary,
            content = scheme.onPrimary,
            enabled = enabled,
            onClick = { onAnswer(true) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(ANSWER_REMEMBERED_TAG),
        )
        AnswerHalf(
            label = forgottenLabel,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            iconFirst = false,
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurface,
            enabled = enabled,
            onClick = { onAnswer(false) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(ANSWER_FORGOT_TAG),
        )
    }
}

@Composable
private fun AnswerHalf(
    label: String,
    icon: ImageVector,
    iconFirst: Boolean,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = Dimens.spaceMd, vertical = Dimens.spaceSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconFirst) AnswerIcon(icon)
            Text(text = label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            if (!iconFirst) AnswerIcon(icon)
        }
    }
}

@Composable
private fun AnswerIcon(icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Dimens.iconSm))
}

private val ANSWER_BAR_HEIGHT = 72.dp

/** Stable handles for UI tests: the two halves of [StudyAnswerBar]. */
const val ANSWER_REMEMBERED_TAG = "answer_remembered"
const val ANSWER_FORGOT_TAG = "answer_forgot"
