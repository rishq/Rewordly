package com.rewordly.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rewordly.app.core.navigation.AiGenerateRoute
import com.rewordly.app.core.navigation.AiHistoryRoute
import com.rewordly.app.core.navigation.AiHomeRoute
import com.rewordly.app.core.navigation.AiPreviewRoute
import com.rewordly.app.core.navigation.DataManagementRoute
import com.rewordly.app.core.navigation.HistoryRoute
import com.rewordly.app.core.navigation.HomeRoute
import com.rewordly.app.core.navigation.LearnRoute
import com.rewordly.app.core.navigation.OnboardingRoute
import com.rewordly.app.core.navigation.PlacementRoute
import com.rewordly.app.core.navigation.ProfileRoute
import com.rewordly.app.core.navigation.SavedRoute
import com.rewordly.app.core.navigation.SearchRoute
import com.rewordly.app.core.navigation.SettingsRoute
import com.rewordly.app.core.navigation.TopLevelDestination
import com.rewordly.app.core.navigation.WordDetailsRoute
import com.rewordly.app.core.ui.motionMillis
import com.rewordly.app.domain.usecase.StartDestination
import com.rewordly.app.feature.ai.AiHistoryScreen
import com.rewordly.app.feature.ai.AiHomeScreen
import com.rewordly.app.feature.ai.generate.AiGenerateScreen
import com.rewordly.app.feature.ai.preview.AiPreviewScreen
import com.rewordly.app.feature.data.DataManagementScreen
import com.rewordly.app.feature.history.HistoryScreen
import com.rewordly.app.feature.home.HomeScreen
import com.rewordly.app.feature.learn.LearnScreen
import com.rewordly.app.feature.onboarding.OnboardingScreen
import com.rewordly.app.feature.placement.PlacementScreen
import com.rewordly.app.feature.profile.ProfileScreen
import com.rewordly.app.feature.saved.SavedScreen
import com.rewordly.app.feature.search.SearchScreen
import com.rewordly.app.feature.settings.SettingsScreen
import com.rewordly.app.feature.word.WordDetailsScreen

private const val NAV_MILLIS = 220

@Composable
fun RewordlyNavHost(
    navController: NavHostController,
    startDestination: StartDestination,
    modifier: Modifier = Modifier,
) {
    val openWord: (String) -> Unit = { id -> navController.navigate(WordDetailsRoute(id)) }
    // Collapses to zero when the user disabled animations system-wide.
    val motion = motionMillis(NAV_MILLIS)
    val navTween = tween<Float>(motion)

    NavHost(
        navController = navController,
        startDestination = if (startDestination == StartDestination.HOME) HomeRoute else OnboardingRoute,
        modifier = modifier,
        enterTransition = {
            fadeIn(navTween) + slideInHorizontally(tween<IntOffset>(motion)) { it / 12 }
        },
        exitTransition = { fadeOut(navTween) },
        popEnterTransition = { fadeIn(navTween) },
        popExitTransition = {
            fadeOut(navTween) + slideOutHorizontally(tween<IntOffset>(motion)) { it / 12 }
        },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                },
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                onContinueLearning = { navController.navigateToTopLevel(TopLevelDestination.LEARN) },
                onOpenWord = openWord,
                onOpenSaved = { navController.navigate(SavedRoute) },
                onOpenAi = { navController.navigate(AiHomeRoute) },
                onOpenPlacement = { navController.navigate(PlacementRoute) },
            )
        }
        composable<LearnRoute> {
            LearnScreen(onOpenWord = openWord)
        }
        composable<SearchRoute> {
            SearchScreen(onOpenWord = openWord)
        }
        composable<ProfileRoute> {
            ProfileScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenSearch = { navController.navigate(SearchRoute()) },
                onOpenWord = openWord,
            )
        }
        composable<SavedRoute> {
            SavedScreen(
                onBack = { navController.popBackStack() },
                onOpenWord = openWord,
            )
        }
        composable<AiHomeRoute> {
            AiHomeScreen(
                onBack = { navController.popBackStack() },
                onOpenMode = { mode -> navController.navigate(AiGenerateRoute(mode.name)) },
                onOpenHistory = { navController.navigate(AiHistoryRoute) },
            )
        }
        composable<AiGenerateRoute> {
            AiGenerateScreen(
                onBack = { navController.popBackStack() },
                onOpenPreview = { id -> navController.navigate(AiPreviewRoute(id)) },
            )
        }
        composable<AiPreviewRoute> {
            AiPreviewScreen(onBack = { navController.popBackStack() }, onOpenWord = openWord)
        }
        composable<AiHistoryRoute> {
            AiHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenEntry = { id -> navController.navigate(AiPreviewRoute(id)) },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPlacement = { navController.navigate(PlacementRoute) },
                onOpenDataManagement = { navController.navigate(DataManagementRoute) },
            )
        }
        composable<DataManagementRoute> {
            DataManagementScreen(onBack = { navController.popBackStack() })
        }
        composable<PlacementRoute> {
            PlacementScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<WordDetailsRoute> {
            WordDetailsScreen(
                onBack = { navController.popBackStack() },
                onSearchWord = { query -> navController.navigate(SearchRoute(query)) },
            )
        }
    }
}
