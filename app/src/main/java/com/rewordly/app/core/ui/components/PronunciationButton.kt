package com.rewordly.app.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.audio.LocalPronunciationEngine
import com.rewordly.app.core.audio.PronunciationEngine
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.LearningLanguage
import kotlinx.coroutines.launch

/**
 * Speaks [text] with the injected [PronunciationEngine]. Pass a [label] for a full button,
 * leave it null for an icon-only control. Tapping while speaking stops the playback.
 */
@Composable
fun PronunciationButton(
    text: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    language: LearningLanguage = LearningLanguage.DEFAULT,
    engine: PronunciationEngine = LocalPronunciationEngine.current,
) {
    val scope = rememberCoroutineScope()
    val speakingText by engine.speakingText.collectAsStateWithLifecycle()
    val isSpeaking = speakingText != null && speakingText == text
    val stopLabel = stringResource(R.string.action_stop)
    val description = stringResource(
        if (isSpeaking) R.string.a11y_stop_speech else R.string.a11y_pronounce,
        text,
    )
    val onClick: () -> Unit = {
        if (isSpeaking) {
            engine.stop()
        } else {
            scope.launch { engine.speak(text, language) }
        }
    }

    if (label != null) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = Dimens.buttonHeight),
        ) {
            SpeakingIcon(isSpeaking = isSpeaking)
            Spacer(Modifier.width(Dimens.spaceSm))
            Text(text = label)
        }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier
                .sizeIn(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)
                .semantics {
                    contentDescription = description
                    stateDescription = if (isSpeaking) stopLabel else ""
                },
        ) {
            SpeakingIcon(isSpeaking = isSpeaking)
        }
    }
}

@Composable
private fun SpeakingIcon(isSpeaking: Boolean) {
    val icon = if (isSpeaking) Icons.Filled.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp
    if (isSpeaking) {
        // The pulse only exists while speaking: an infinite transition left running while idle invalidates
        // the icon's layer every frame for no visible change, on every button on screen.
        val transition = rememberInfiniteTransition(label = "speaking")
        val pulse by transition.animateFloat(
            initialValue = PULSE_MIN,
            targetValue = PULSE_MAX,
            animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
            label = "pulse",
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(Dimens.iconSm)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                },
        )
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSm),
        )
    }
}

private const val PULSE_MIN = 0.9f
private const val PULSE_MAX = 1.1f
private const val PULSE_MILLIS = 450
