package com.swimvpn.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Desktop theme: reuses the exact SwimVPN visual tokens (ported verbatim from Android)
// to keep the same UI characteristics. The Android window-insets / font-resource code is
// intentionally dropped (not applicable on desktop); v1 uses the platform default font.

private val DarkColorScheme = darkColorScheme(
    primary = SwimDesignTokens.Dark.color.homePurplePrimary,
    primaryContainer = SwimDesignTokens.Dark.material.purpleCoreBottom,
    onPrimary = Color.White,
    onPrimaryContainer = SwimDesignTokens.Dark.color.homeTextPrimary,
    secondary = SwimDesignTokens.Dark.color.homePurpleActive,
    background = SwimDesignTokens.Dark.color.homeBackgroundDeep,
    surface = SwimDesignTokens.Dark.color.homeSurfaceBase,
    surfaceVariant = SwimDesignTokens.Dark.material.bowlMid,
    onBackground = SwimDesignTokens.Dark.color.homeTextPrimary,
    onSurface = SwimDesignTokens.Dark.color.homeTextPrimary,
    onSurfaceVariant = SwimDesignTokens.Dark.color.homeTextSecondary,
    outline = SwimDesignTokens.Dark.color.homeStrokeMedium,
    outlineVariant = SwimDesignTokens.Dark.color.homeStrokeSubtle,
    error = SwimDesignTokens.Dark.color.homeDanger,
)

private val LightColorScheme = lightColorScheme(
    primary = SwimDesignTokens.Light.color.homePurplePrimary,
    primaryContainer = SwimDesignTokens.Light.color.homePurpleDeep,
    onPrimary = Color.White,
    onPrimaryContainer = SwimDesignTokens.Light.color.homeTextPrimary,
    secondary = SwimDesignTokens.Light.color.homePurpleActive,
    background = SwimDesignTokens.Light.color.homeBackgroundDeep,
    surface = SwimDesignTokens.Light.color.homeSurfaceBase,
    surfaceVariant = SwimDesignTokens.Light.material.bowlMid,
    onBackground = SwimDesignTokens.Light.color.homeTextPrimary,
    onSurface = SwimDesignTokens.Light.color.homeTextPrimary,
    onSurfaceVariant = SwimDesignTokens.Light.color.homeTextSecondary,
    outline = SwimDesignTokens.Light.color.homeStrokeMedium,
    outlineVariant = SwimDesignTokens.Light.color.homeStrokeSubtle,
    error = SwimDesignTokens.Light.color.homeDanger,
)

@Composable
fun SwimVpnTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val tokens = if (dark) SwimDesignTokens.Dark else SwimDesignTokens.Light
    SwimDesignTokens.activateVisualTokens(tokens)
    CompositionLocalProvider(LocalSwimVisualTokens provides tokens) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            content = content,
        )
    }
}
