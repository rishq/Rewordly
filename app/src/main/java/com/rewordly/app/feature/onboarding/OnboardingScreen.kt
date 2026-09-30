package com.rewordly.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.theme.Dimens
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

private val pages = listOf(
    OnboardingPage(Icons.Outlined.AutoStories, R.string.onboarding_title_1, R.string.onboarding_body_1),
    OnboardingPage(Icons.Outlined.FormatQuote, R.string.onboarding_title_2, R.string.onboarding_body_2),
    OnboardingPage(Icons.Outlined.LocalFireDepartment, R.string.onboarding_title_3, R.string.onboarding_body_3),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(completed) {
        if (completed) currentOnFinished()
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Dimens.screenPadding),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(Dimens.minTouchTarget)) {
                if (!isLastPage) {
                    TextButton(onClick = viewModel::complete, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                OnboardingPageContent(pages[index])
            }
            PageIndicator(count = pages.size, current = pagerState.currentPage)
            PrimaryButton(
                text = stringResource(if (isLastPage) R.string.action_get_started else R.string.action_next),
                onClick = {
                    if (isLastPage) {
                        viewModel.complete()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceXl),
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXl, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.onboardingIllustration)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(Dimens.logoSize * 0.7f),
            )
        }
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = Dimens.maxContentWidth)
                .semantics { heading() },
        )
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = Dimens.maxContentWidth),
        )
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    val description = stringResource(R.string.a11y_page_indicator, current + 1, count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.spaceLg)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) Dimens.spaceXl else Dimens.spaceSm, label = "indicatorWidth")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "indicatorColor",
            )
            Box(
                modifier = Modifier
                    .height(Dimens.spaceSm)
                    .width(width)
                    .background(color, CircleShape),
            )
        }
    }
}
