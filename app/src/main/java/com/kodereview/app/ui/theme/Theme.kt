package com.kodereview.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryColor,
    onPrimary = SurfaceColor,
    primaryContainer = PrimaryVariant,
    secondary = ModifierColor,
    background = EditorBackground,
    surface = SurfaceColor,
    surfaceVariant = SurfaceVariant,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorColor,
    onError = SurfaceColor
)

@Composable
fun KodeReviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
