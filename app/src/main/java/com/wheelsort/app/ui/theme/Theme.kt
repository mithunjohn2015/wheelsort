package com.wheelsort.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = SurfaceLight,
    secondary = BrandSecondary,
    background = BackgroundLight,
    surface = SurfaceLight,
    error = ActionDelete
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = SurfaceLight,
    secondary = BrandSecondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ActionDelete
)

@Composable
fun WheelSortTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WheelSortTypography,
        content = content
    )
}
