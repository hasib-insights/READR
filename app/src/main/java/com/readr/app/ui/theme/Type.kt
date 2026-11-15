package com.readr.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Book-inspired typography: serif display and title styles over a clean
 * system sans body, so no font files need to ship in the APK.
 */
fun readrTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Serif),
        displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Serif),
        displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif),
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}
