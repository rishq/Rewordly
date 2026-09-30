package com.rewordly.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rewordly.app.core.navigation.HomeRoute
import com.rewordly.app.core.navigation.LearnRoute
import com.rewordly.app.core.navigation.OnboardingRoute
import com.rewordly.app.core.navigation.ProfileRoute
import com.rewordly.app.core.navigation.ReviewRoute
import com.rewordly.app.core.navigation.SearchRoute
import com.rewordly.app.core.navigation.SettingsRoute
import com.rewordly.app.core.navigation.SplashRoute
import com.rewordly.app.core.navigation.TopLevelDestination
import com.rewordly.app.core.navigation.WordDetailsRoute
import com.rewordly.app.domain.usecase.StartDestination
import com.rewordly.app.feature.home.HomeScreen
import com.rewordly.app.feature.learn.LearnScreen
import com.rewordly.app.feature.onboarding.OnboardingScreen
import com.rewordly.app.feature.profile.ProfileScreen
import com.rewordly.app.feature.review.ReviewScreen
import com.rewordly.app.feature.search.SearchScreen
import com.rewordly.app.feature.settings.SettingsScreen
import com.rewordly.app.feature.splash.SplashScreen
import com.rewordly.app.feature.word.WordDetailsScreen

@Composable
fun RewordlyNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val openWord: (String) -> Unit = { id -> navController.navigate(WordDetailsRoute(id)) }

    NavHost(
        navController = navController,
        startDestination = SplashRoute,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<SplashRoute> {
            SplashScreen(
                onFinished = { start ->
                    val target: Any = if (start == StartDestination.HOME) HomeRoute else OnboardingRoute
                    navController.navigate(target) { popUpTo<SplashRoute> { inclusive = true } }
                },
            )
        }
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
                onStartReview = { navController.navigateToTopLevel(TopLevelDestination.REVIEW) },
                onOpenWord = openWord,
            )
        }
        composable<LearnRoute> {
            LearnScreen(onOpenWord = openWord)
        }
        composable<ReviewRoute> {
            ReviewScreen(onOpenWord = openWord)
        }
        composable<SearchRoute> {
            SearchScreen(onOpenWord = openWord)
        }
        composable<ProfileRoute> {
            ProfileScreen(onOpenSettings = { navController.navigate(SettingsRoute) })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<WordDetailsRoute> {
            WordDetailsScreen(
                onBack = { navController.popBackStack() },
                onSearchWord = { query -> navController.navigate(SearchRoute(query)) },
            )
        }
    }
}
