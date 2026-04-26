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
val SwimBlueMain = Color(0xFF4A9ED7) // Bleu du corps du requin
val SwimBlueFace = Color(0xFFA6D8F0) // Bleu clair du visage
val SwimNavyMouth = Color(0xFF0A3151) // Bleu marine de la bouche
val ElectricBlue = Color(0xFF4A9ED7) // Alias pour compatibilité
val RedAlert = Color(0xFFEF4444)

// Light Palette cohérente avec le logo
val BgLight = Color(0xFFF4F8FB)
val CardLight = Color(0xFFFFFFFF)
val TextLight = Color(0xFF0A3151) // Utilisation du Navy pour le texte
val TextSecondaryLight = Color(0xFF64748B)

// Dark Palette
val BgDark = Color(0xFF04111F)
val CardDark = Color(0xFF0B2034)
val TextDark = Color(0xFFE8F3FB)
val TextSecondaryDark = Color(0xFF9FB5C8)

private val LightColorScheme = lightColorScheme(
    primary = SwimBlueMain,
    primaryContainer = Color(0xFFD8EEFB),
    background = BgLight,
    surface = CardLight,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEAF4FA),
    onPrimary = Color.White,
    onBackground = TextLight,
    onSurface = TextLight,
    surfaceVariant = Color(0xFFEBF5FF),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFD9E6EE),
    outlineVariant = Color(0xFFE7EEF4),
    secondary = Color(0xFF0F766E),
    secondaryContainer = Color(0xFFDDF8F3),
    onSecondaryContainer = Color(0xFF0F3E3A),
    error = RedAlert
)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    primaryContainer = Color(0xFF123D5C),
    background = BgDark,
    surface = CardDark,
    surfaceContainer = Color(0xFF102A42),
    surfaceContainerHigh = Color(0xFF153650),
    onPrimary = Color.White,
    onBackground = TextDark,
    onSurface = TextDark,
    surfaceVariant = Color(0xFF122A41),
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF24455E),
    outlineVariant = Color(0xFF183248),
    secondary = Color(0xFF5EEAD4),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFD8FFFA),
    error = Color(0xFFF87171)
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
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
