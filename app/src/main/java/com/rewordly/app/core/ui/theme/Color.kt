package com.rewordly.app.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand palette: indigo primary, neutral-violet secondary, warm coral tertiary (streaks/accents).
private val Indigo10 = Color(0xFF000C62)
private val Indigo20 = Color(0xFF1B2678)
private val Indigo30 = Color(0xFF343D96)
private val Indigo40 = Color(0xFF4C56AF)
private val Indigo80 = Color(0xFFBDC2FF)
private val Indigo90 = Color(0xFFDFE0FF)

private val Slate10 = Color(0xFF181A2C)
private val Slate20 = Color(0xFF2D2F42)
private val Slate30 = Color(0xFF434659)
private val Slate40 = Color(0xFF5B5D72)
private val Slate80 = Color(0xFFC4C5DD)
private val Slate90 = Color(0xFFE0E1F9)

private val Coral10 = Color(0xFF3F0400)
private val Coral20 = Color(0xFF5F150B)
private val Coral30 = Color(0xFF7E2B1F)
private val Coral40 = Color(0xFF9C4234)
private val Coral80 = Color(0xFFFFB4A8)
private val Coral90 = Color(0xFFFFDAD4)

private val Green10 = Color(0xFF00210E)
private val Green20 = Color(0xFF00391C)
private val Green30 = Color(0xFF135232)
private val Green40 = Color(0xFF2E6A44)
private val Green80 = Color(0xFF96D5A6)
private val Green90 = Color(0xFFB1F1C1)

private val Neutral4 = Color(0xFF0E0E13)
private val Neutral6 = Color(0xFF131318)
private val Neutral10 = Color(0xFF1B1B21)
private val Neutral12 = Color(0xFF1F1F25)
private val Neutral17 = Color(0xFF2A292F)
private val Neutral20 = Color(0xFF303036)
private val Neutral22 = Color(0xFF35343A)
private val Neutral24 = Color(0xFF39383F)
private val Neutral87 = Color(0xFFDBD9E0)
private val Neutral90 = Color(0xFFE4E1E9)
private val Neutral92 = Color(0xFFEAE7EF)
private val Neutral94 = Color(0xFFEFEDF4)
private val Neutral95 = Color(0xFFF2EFF7)
private val Neutral96 = Color(0xFFF5F2FA)
private val Neutral98 = Color(0xFFFBF8FF)
private val White = Color(0xFFFFFFFF)

private val NeutralVariant30 = Color(0xFF46464F)
private val NeutralVariant50 = Color(0xFF777680)
private val NeutralVariant60 = Color(0xFF91909A)
private val NeutralVariant80 = Color(0xFFC7C5D0)
private val NeutralVariant90 = Color(0xFFE3E1EC)

internal val LightColorScheme = lightColorScheme(
    primary = Indigo40,
    onPrimary = White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    inversePrimary = Indigo80,
    secondary = Slate40,
    onSecondary = White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Coral40,
    onTertiary = White,
    tertiaryContainer = Coral90,
    onTertiaryContainer = Coral10,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    surfaceTint = Indigo40,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    surfaceBright = Neutral98,
    surfaceDim = Neutral87,
    surfaceContainerLowest = White,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90,
)

internal val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    inversePrimary = Indigo40,
    secondary = Slate80,
    onSecondary = Slate20,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Coral80,
    onTertiary = Coral20,
    tertiaryContainer = Coral30,
    onTertiaryContainer = Coral90,
    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    surfaceTint = Indigo80,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    surfaceBright = Neutral24,
    surfaceDim = Neutral6,
    surfaceContainerLowest = Neutral4,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22,
)

/** Semantic colors not covered by Material's ColorScheme. */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

internal val LightExtendedColors = ExtendedColors(
    success = Green40,
    onSuccess = White,
    successContainer = Green90,
    onSuccessContainer = Green10,
)

internal val DarkExtendedColors = ExtendedColors(
    success = Green80,
    onSuccess = Green20,
    successContainer = Green30,
    onSuccessContainer = Green90,
)

internal val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
