package com.rewordly.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.rewordly.app.R
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.core.ui.theme.RewordlyTextStyles

/** Home card with today's review workload, its progress and the entry point into a review session. */
@Composable
fun ReviewDueCard(dueToday: Int, reviewedToday: Int, onStartReview: () -> Unit, modifier: Modifier = Modifier) {
    val workload = dueToday + reviewedToday
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(Dimens.iconLg))
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                    Text(
                        text = stringResource(R.string.home_review_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.review_due_today, dueToday, dueToday),
                        style = RewordlyTextStyles.statValue,
                    )
                }
            }
            if (reviewedToday > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                    LinearProgressIndicator(
                        progress = { reviewedToday.toFloat() / workload },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surface,
                    )
                    Text(
                        text = stringResource(R.string.home_review_progress, reviewedToday, workload),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            PrimaryButton(
                text = stringResource(R.string.action_start_review),
                onClick = onStartReview,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Friendly completion state for Home when no review is due. */
@Composable
fun ReviewDoneCard(reviewedToday: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPadding)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(Dimens.iconLg))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                Text(
                    text = stringResource(R.string.home_review_done_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.home_review_done_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (reviewedToday > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.words_count, reviewedToday, reviewedToday),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
