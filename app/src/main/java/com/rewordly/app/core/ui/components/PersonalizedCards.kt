package com.rewordly.app.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.rewordly.app.R
import com.rewordly.app.core.ui.labelRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.RecommendationReason

/**
 * Summary of the recommended session: how much there is to review and how many new words the plan
 * is willing to introduce today. Both actions are always available, so the user keeps control.
 */
@Composable
fun TodayPlanCard(
    reviewCount: Int,
    newWordCount: Int,
    easedOff: Boolean,
    onStartReview: () -> Unit,
    onStartLearning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.home_plan_title), modifier = modifier) {
        if (reviewCount == 0 && newWordCount == 0) {
            Text(
                text = stringResource(R.string.home_plan_all_done_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.home_plan_summary, reviewCount, newWordCount),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (easedOff) {
            Text(
                text = stringResource(R.string.home_plan_eased_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (reviewCount > 0) {
            PrimaryButton(
                text = stringResource(R.string.action_start_review),
                icon = Icons.Outlined.Replay,
                onClick = onStartReview,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (newWordCount > 0) {
            SecondaryButton(
                text = stringResource(R.string.action_learn_new),
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                onClick = onStartLearning,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A recommended word with the reason it was recommended, so the suggestion is never opaque. */
@Composable
fun RecommendedWordItem(
    word: String,
    translation: String,
    difficulty: Difficulty,
    reason: RecommendationReason,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = Dimens.minTouchTarget)
                .padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXxs),
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(reason.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Pill(text = stringResource(difficulty.labelRes).substringBefore(' '))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
