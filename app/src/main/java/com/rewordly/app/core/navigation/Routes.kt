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
data object SavedRoute

@Serializable
data object SettingsRoute

/** Import, export and backup of the local learning data. */
@Serializable
data object DataManagementRoute

/** Optional vocabulary check that estimates the CEFR level. */
@Serializable
data object PlacementRoute

@Serializable
data object HistoryRoute

@Serializable
data object AiHomeRoute

/** [mode] is a GenerationMode name. */
@Serializable
data class AiGenerateRoute(val mode: String)

@Serializable
data class AiPreviewRoute(val historyId: String)

@Serializable
data object AiHistoryRoute

@Serializable
data class WordDetailsRoute(val wordId: String)
