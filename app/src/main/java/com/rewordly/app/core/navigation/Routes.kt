package com.rewordly.app.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object OnboardingRoute

@Serializable
data object HomeRoute

@Serializable
data object LearnRoute

@Serializable
data object ReviewRoute

/** [query] pre-fills the search field, e.g. when opening a related word. */
@Serializable
data class SearchRoute(val query: String = "")

@Serializable
data object ProfileRoute

@Serializable
data object SettingsRoute

@Serializable
data class WordDetailsRoute(val wordId: String)
