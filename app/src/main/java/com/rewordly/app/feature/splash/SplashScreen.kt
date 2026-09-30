package com.rewordly.app.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.usecase.StartDestination

@Composable
fun SplashScreen(onFinished: (StartDestination) -> Unit, viewModel: SplashViewModel = hiltViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(destination) {
        destination?.let(currentOnFinished)
    }
    SplashContent()
}

@Composable
private fun SplashContent() {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(durationMillis = 500)) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value }
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg, Alignment.CenterVertically),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_mark),
                contentDescription = null,
                modifier = Modifier.size(Dimens.logoSize),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
