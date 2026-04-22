package com.swimvpn.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

object AppThemePreference {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
}

// --- COLORS ---
val SwimBlueMain = Color(0xFF4A9ED7) // Bleu du corps du requin
val SwimBlueFace = Color(0xFFA6D8F0) // Bleu clair du visage
val SwimNavyMouth = Color(0xFF0A3151) // Bleu marine de la bouche
val ElectricBlue = Color(0xFF4A9ED7) // Alias pour compatibilité
val RedAlert = Color(0xFFEF4444)

// Light Palette cohérente avec le logo
val BgLight = Color(0xFFF8FBFE)
val CardLight = Color(0xFFFFFFFF)
val TextLight = Color(0xFF0A3151) // Utilisation du Navy pour le texte
val TextSecondaryLight = Color(0xFF64748B)

// Dark Palette
val BgDark = Color(0xFF051626)
val CardDark = Color(0xFF0A2239)
val TextDark = Color(0xFFE2E8F0)
val TextSecondaryDark = Color(0xFF94A3B8)

private val LightColorScheme = lightColorScheme(
    primary = SwimBlueMain,
    background = BgLight,
    surface = CardLight,
    onPrimary = Color.White,
    onBackground = TextLight,
    onSurface = TextLight,
    surfaceVariant = Color(0xFFEBF5FF),
    onSurfaceVariant = TextSecondaryLight
)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    background = BgDark,
    surface = CardDark,
    onPrimary = Color.White,
    onBackground = TextDark,
    onSurface = TextDark,
    surfaceVariant = BgDark,
    onSurfaceVariant = TextSecondaryDark
)

// --- TYPOGRAPHY ---
val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default, // Idéalement remplacer par Inter
        fontWeight = FontWeight.Black,   // Poids 900
        fontSize = 32.sp,
        letterSpacing = 2.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
)

// --- SHAPES ---
val Shapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(48.dp) // 3rem radius
)

@Composable
fun SwimVpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
