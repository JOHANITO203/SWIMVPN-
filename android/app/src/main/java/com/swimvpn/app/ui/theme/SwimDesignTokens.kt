package com.swimvpn.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp

object SwimDesignTokens {
    object Color {
        val HomeBackgroundDeep = ComposeColor(0xFF07070B)
        val HomeBackgroundBase = ComposeColor(0xFF0D0C13)
        val HomeSurfaceBase = ComposeColor(0xFF131317)
        val HomeSurfaceElevated = ComposeColor(0xFF1A1A20)
        val HomeSurfaceHighlight = ComposeColor(0xFF24232B)
        val HomePurplePrimary = ComposeColor(0xFF8A6AF1)
        val HomePurpleActive = ComposeColor(0xFFA489E7)
        val HomePurpleDeep = ComposeColor(0xFF443677)
        val HomeSuccessGreen = ComposeColor(0xFF35D978)
        val HomeTextPrimary = ComposeColor(0xFFF3F1F6)
        val HomeTextSecondary = ComposeColor(0xFFA6A1B3)
        val HomeTextMuted = ComposeColor(0xFF6E6978)
        val HomeDividerSubtle = ComposeColor.White.copy(alpha = 0.08f)
        val HomeStrokeSubtle = ComposeColor.White.copy(alpha = 0.10f)
        val HomeTopHighlight = ComposeColor.White.copy(alpha = 0.08f)

        val BackgroundDeep = HomeBackgroundDeep
        val BackgroundBase = HomeBackgroundBase
        val SurfaceBase = HomeSurfaceBase
        val SurfaceElevated = HomeSurfaceElevated
        val SurfaceHighlight = HomeSurfaceHighlight
        val PurplePrimary = HomePurplePrimary
        val PurpleActive = HomePurpleActive
        val PurpleDeep = HomePurpleDeep
        val SuccessGreen = HomeSuccessGreen
        val TextPrimary = HomeTextPrimary
        val TextSecondary = HomeTextSecondary
        val TextMuted = HomeTextMuted
        val DividerSubtle = HomeDividerSubtle
        val StrokeSubtle = HomeStrokeSubtle
    }

    object Shape {
        val Pill = RoundedCornerShape(percent = 50)
        val HardwareCard = RoundedCornerShape(48.dp)
        val LargeHardwareCard = RoundedCornerShape(52.dp)
        val Control = RoundedCornerShape(28.dp)
    }

    object Elevation {
        val HardwareButton = 14.dp
        val HardwareSurface = 18.dp
        val Dock = 24.dp
    }

    object Spacing {
        val ScreenHorizontal = 36.dp
        val TopInsetVisual = 40.dp
        val StatusToServer = 32.dp
        val ServerToStats = 22.dp
        val DockBottom = 32.dp
        val MinimumTouchTarget = 48.dp
    }

    object Home {
        val ProfileButtonSize = 68.dp
        val ProfileIconSize = 28.dp
        val ServerPillHeight = 90.dp
        val ServerBadgeSize = 62.dp
        val StatsCardHeight = 168.dp
        val StatsIconSize = 26.dp
        val DockHeight = 104.dp
        val DockInactiveNode = 78.dp
        val DockActiveNode = 96.dp
        val DockNodeSize = DockInactiveNode
        val DockActiveNodeSize = DockActiveNode
        val OrbSize = 328.dp
        val PowerButtonSize = 206.dp
        val PowerButtonMin = 168.dp
        val PowerIconSize = 62.dp
        val ProtectedDot = 11.dp
    }
}
