package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QuickLinkDarkColorScheme = darkColorScheme(
    primary = ElectricBluePrimary,
    onPrimary = Color.White,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = TextPrimary,
    secondary = EmeraldLive,
    onSecondary = Color.Black,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = CyanLaser,
    onTertiary = Color.Black,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = CrimsonDanger,
    onError = Color.White,
    outline = CardBorderSubtle
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = QuickLinkDarkColorScheme,
        typography = Typography,
        content = content
    )
}
