package com.swimvpn.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.swimvpn.app.R

object AppThemePreference {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
}

// --- COLORS ---
val SwimBlueMain = Color(0xFF8A6AF1) // Legacy alias: current SwimVPN accent is violet.
val SwimBlueFace = Color(0xFFB89AFF)
val SwimNavyMouth = Color(0xFF17121F)
val ElectricBlue = Color(0xFF8A6AF1) // Legacy alias kept for compatibility.
val RedAlert = Color(0xFFEF4444)

// Light is derived from the current SwimVPN dark visual language.
val BgLight = SwimDesignTokens.Light.color.homeBackgroundDeep
val CardLight = SwimDesignTokens.Light.color.homeSurfaceBase
val TextLight = SwimDesignTokens.Light.color.homeTextPrimary
val TextSecondaryLight = SwimDesignTokens.Light.color.homeTextSecondary

// Dark is the canonical SwimVPN theme.
val BgDark = SwimDesignTokens.Dark.color.homeBackgroundDeep
val CardDark = SwimDesignTokens.Dark.color.homeSurfaceBase
val TextDark = SwimDesignTokens.Dark.color.homeTextPrimary
val TextSecondaryDark = SwimDesignTokens.Dark.color.homeTextSecondary

private val LightColorScheme = lightColorScheme(
    primary = SwimDesignTokens.Light.color.homePurplePrimary,
    primaryContainer = Color(0xFFE7DDFF),
    background = BgLight,
    surface = CardLight,
    surfaceContainer = Color(0xFFFCFAFF),
    surfaceContainerHigh = Color(0xFFF0E9FF),
    onPrimary = Color.White,
    onPrimaryContainer = Color(0xFF21123F),
    onBackground = TextLight,
    onSurface = TextLight,
    surfaceVariant = Color(0xFFEDE6F8),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFD7CCE7),
    outlineVariant = Color(0xFFE8DFF2),
    secondary = SwimDesignTokens.Light.color.homePurpleActive,
    secondaryContainer = Color(0xFFEDE6FF),
    onSecondary = Color.White,
    onSecondaryContainer = Color(0xFF24164A),
    error = RedAlert,
    errorContainer = Color(0xFFFFE2E2),
    onErrorContainer = Color(0xFF6F1111)
)

private val DarkColorScheme = darkColorScheme(
    primary = SwimDesignTokens.Dark.color.homePurplePrimary,
    primaryContainer = SwimDesignTokens.Dark.material.purpleCoreBottom,
    background = BgDark,
    surface = CardDark,
    surfaceContainer = SwimDesignTokens.Dark.color.homeSurfaceElevated,
    surfaceContainerHigh = SwimDesignTokens.Dark.color.homeSurfaceHighlight,
    onPrimary = Color.White,
    onPrimaryContainer = Color.White,
    onBackground = TextDark,
    onSurface = TextDark,
    surfaceVariant = SwimDesignTokens.Dark.color.homeSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color.White.copy(alpha = 0.12f),
    outlineVariant = Color.White.copy(alpha = 0.08f),
    secondary = SwimDesignTokens.Dark.material.purpleCoreTop,
    secondaryContainer = SwimDesignTokens.Dark.color.homePurpleDeep,
    onSecondary = Color(0xFF110A24),
    onSecondaryContainer = Color.White,
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF5C1212),
    onErrorContainer = Color(0xFFFFD7D7)
)

// --- TYPOGRAPHY ---
val PoppinsFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_black, FontWeight.Black)
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Black, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineLarge = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        letterSpacing = 2.sp
    ),
    headlineMedium = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

// --- SHAPES ---
val Shapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(48.dp)
)

@Composable
fun SwimVpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val visualTokens = if (darkTheme) SwimDesignTokens.Dark else SwimDesignTokens.Light
    SwimDesignTokens.activateVisualTokens(visualTokens)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSwimVisualTokens provides visualTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
