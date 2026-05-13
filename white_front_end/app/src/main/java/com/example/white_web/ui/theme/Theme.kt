package com.example.white_web.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ================================================================
//  Warm Elegant Light Color Scheme — always active
// ================================================================
private val WarmLightColorScheme = lightColorScheme(
    primary                = primaryLight,
    onPrimary              = onPrimaryLight,
    primaryContainer       = primaryContainerLight,
    onPrimaryContainer     = onPrimaryContainerLight,
    secondary              = secondaryLight,
    onSecondary            = onSecondaryLight,
    secondaryContainer     = secondaryContainerLight,
    onSecondaryContainer   = onSecondaryContainerLight,
    tertiary               = tertiaryLight,
    onTertiary             = onTertiaryLight,
    tertiaryContainer      = tertiaryContainerLight,
    onTertiaryContainer    = onTertiaryContainerLight,
    error                  = errorLight,
    onError                = onErrorLight,
    errorContainer         = errorContainerLight,
    onErrorContainer       = onErrorContainerLight,
    background             = backgroundLight,
    onBackground           = onBackgroundLight,
    surface                = surfaceLight,
    onSurface              = onSurfaceLight,
    surfaceVariant         = surfaceVariantLight,
    onSurfaceVariant       = onSurfaceVariantLight,
    outline                = outlineLight,
    outlineVariant         = outlineVariantLight,
    scrim                  = scrimLight,
    inverseSurface         = inverseSurfaceLight,
    inverseOnSurface       = inverseOnSurfaceLight,
    inversePrimary         = inversePrimaryLight,
    surfaceDim             = surfaceDimLight,
    surfaceBright          = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow    = surfaceContainerLowLight,
    surfaceContainer       = surfaceContainerLight,
    surfaceContainerHigh   = surfaceContainerHighLight,
    surfaceContainerHighest= surfaceContainerHighestLight,
)

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

val unspecified_scheme = ColorFamily(
    Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified
)

@Composable
fun White_webTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WarmLightColorScheme,
        typography = Typography,
        content = content
    )
}
