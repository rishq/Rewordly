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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.rewordly.app.R
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.core.ui.theme.RewordlyTextStyles
import com.rewordly.app.domain.model.WordExample

/** One English sentence with its Russian translation, an optional play button and target highlighting. */
@Composable
fun ExampleSentence(
    example: WordExample,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
    targetWord: String? = null,
    playable: Boolean = false,
) {
    val segments = remember(example.text, targetWord) { segmentSentence(example.text, targetWord) }
    val highlight = SpanStyle(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        background = MaterialTheme.colorScheme.primaryContainer,
    )
    val open = stringResource(R.string.quote_open)
    val close = stringResource(R.string.quote_close)
    val sentence = remember(segments, highlight, open, close) {
        buildAnnotatedString {
            append(open)
            segments.forEach { segment ->
                if (segment.highlighted) {
                    withStyle(highlight) { append(segment.text) }
                } else {
                    append(segment.text)
                }
            }
            append(close)
        }
    }
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
                modifier = Modifier
                    .weight(1f)
                    .padding(Dimens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Text(
                    text = sentence,
                    style = RewordlyTextStyles.example,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Words added from the free dictionaries carry an English example with no translation,
                // so the line is only shown when there is something to show.
                if (showTranslation && example.translation.isNotBlank()) {
                    Text(
                        text = example.translation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (playable) {
                PronunciationButton(
                    text = example.text,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = Dimens.spaceSm),
                )
            }
        }
    }
}

/** A sentence split into runs, marking only whole-word matches of [target] as highlighted. */
data class SentenceSegment(val text: String, val highlighted: Boolean)

fun segmentSentence(sentence: String, target: String?): List<SentenceSegment> {
    val needle = target?.trim().orEmpty()
    if (needle.isEmpty()) return listOf(SentenceSegment(sentence, false))
    val pattern = Regex("(?<![\\p{L}])" + Regex.escape(needle) + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
    val segments = mutableListOf<SentenceSegment>()
    var cursor = 0
    pattern.findAll(sentence).forEach { match ->
        if (match.range.first > cursor) {
            segments += SentenceSegment(sentence.substring(cursor, match.range.first), false)
        }
        segments += SentenceSegment(match.value, true)
        cursor = match.range.last + 1
    }
    if (cursor < sentence.length) segments += SentenceSegment(sentence.substring(cursor), false)
    return segments.ifEmpty { listOf(SentenceSegment(sentence, false)) }
}
