package com.rewordly.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.rewordly.app.R
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.core.ui.theme.RewordlyTextStyles
import com.rewordly.app.domain.model.WordExample

@Composable
fun ExampleSentence(example: WordExample, modifier: Modifier = Modifier, showTranslation: Boolean = true) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .padding(vertical = Dimens.spaceLg)
                    .padding(start = Dimens.spaceLg)
                    .width(Dimens.spaceXs)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
            )
            Column(
                modifier = Modifier.padding(Dimens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Text(
                    text = stringResource(R.string.quoted, example.text),
                    style = RewordlyTextStyles.example,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (showTranslation) {
                    Text(
                        text = example.translation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
