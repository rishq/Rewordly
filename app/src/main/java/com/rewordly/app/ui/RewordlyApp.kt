package com.rewordly.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rewordly.app.core.navigation.HomeRoute
import com.rewordly.app.core.navigation.NotificationDestination
import com.rewordly.app.core.navigation.OnboardingRoute
import com.rewordly.app.core.navigation.TopLevelDestination
import com.rewordly.app.core.notifications.NotificationNavigationBus
import com.rewordly.app.domain.usecase.StartDestination

@Composable
fun RewordlyApp(
    notificationNavigation: NotificationNavigationBus,
    startDestination: StartDestination,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { currentDestination.isOn(it) }
    val pending by notificationNavigation.pending.collectAsStateWithLifecycle()

    // Applied once the host is past the splash/onboarding, so a notification tap cannot land on a
    // screen that is about to be replaced and cannot be applied twice.
    LaunchedEffect(pending, currentDestination) {
        val target = pending ?: return@LaunchedEffect
        if (currentDestination.isTransientStart()) return@LaunchedEffect
        notificationNavigation.consume()
        navController.navigateToNotification(target)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTopLevel != null) {
                RewordlyBottomBar(
                    current = currentTopLevel,
                    onSelect = { destination -> navController.navigateToTopLevel(destination) },
                )
            }
        },
    ) { padding ->
        RewordlyNavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}

@Composable
private fun RewordlyBottomBar(current: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

/** Onboarding decides the start destination itself, so a pending tap waits for it. */
private fun NavDestination?.isTransientStart(): Boolean {
    if (this == null) return true
    return hierarchy.any { it.hasRoute(OnboardingRoute::class) }
}

private fun NavHostController.navigateToNotification(destination: NotificationDestination) {
    val topLevel = when (destination) {
        NotificationDestination.HOME -> TopLevelDestination.HOME
        NotificationDestination.LEARN -> TopLevelDestination.LEARN
        NotificationDestination.REVIEW -> TopLevelDestination.REVIEW
    }
    navigateToTopLevel(topLevel)
}

fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
