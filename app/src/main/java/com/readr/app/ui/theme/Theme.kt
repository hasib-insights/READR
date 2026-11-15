package com.readr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = OnSageContainer,
    secondary = WalnutBrown,
    onSecondary = Color.White,
    secondaryContainer = WalnutContainer,
    onSecondaryContainer = OnWalnutContainer,
    tertiary = SageDark,
    background = WarmIvory,
    onBackground = OnSurfaceCharcoal,
    surface = CreamSurface,
    onSurface = OnSurfaceCharcoal,
    surfaceVariant = SurfaceVariantCream,
    onSurfaceVariant = OnSurfaceVariantWarm,
    outline = OutlineWarm,
    error = ErrorClay,
)

private val DarkColors = darkColorScheme(
    primary = SageLight,
    onPrimary = Color(0xFF1C2B1B),
    primaryContainer = NightSageContainer,
    onPrimaryContainer = Color(0xFFD8E6D2),
    secondary = Color(0xFFD3B693),
    onSecondary = Color(0xFF2B2118),
    secondaryContainer = NightWalnutContainer,
    onSecondaryContainer = Color(0xFFEADFC8),
    tertiary = SageLight,
    background = InkNight,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightOnSurfaceVariant,
    outline = NightOutline,
    error = Color(0xFFE08A6D),
)

@Composable
fun READRTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = readrTypography(),
        content = content,
    )
}
