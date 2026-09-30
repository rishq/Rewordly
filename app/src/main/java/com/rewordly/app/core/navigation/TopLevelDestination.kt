package com.rewordly.app.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.rewordly.app.R
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    LEARN(
        LearnRoute,
        LearnRoute::class,
        R.string.nav_learn,
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook,
    ),
    REVIEW(ReviewRoute, ReviewRoute::class, R.string.nav_review, Icons.Filled.Replay, Icons.Outlined.Replay),
    SEARCH(SearchRoute(), SearchRoute::class, R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
    PROFILE(ProfileRoute, ProfileRoute::class, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person),
}
