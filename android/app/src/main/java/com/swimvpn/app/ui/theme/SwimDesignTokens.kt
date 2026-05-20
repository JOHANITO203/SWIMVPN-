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

    object Material {
        val ShellTop = ComposeColor.White.copy(alpha = 0.07f)
        val ShellMid = ComposeColor(0xFF17171C)
        val ShellBottom = ComposeColor(0xFF07070B)
        val BowlTop = ComposeColor(0xFF101116)
        val BowlMid = ComposeColor(0xFF05060A)
        val BowlBottom = ComposeColor.Black.copy(alpha = 0.96f)
        val PurpleCoreTop = ComposeColor(0xFFB89AFF)
        val PurpleCoreMid = ComposeColor(0xFF8A6AF1)
        val PurpleCoreBottom = ComposeColor(0xFF5D3BD8)
        val OuterDarkVeil = ComposeColor.Black.copy(alpha = 0.40f)
        val BowlInnerShadow = ComposeColor.Black.copy(alpha = 0.60f)
    }

    object Highlight {
        val InnerTop = ComposeColor.White.copy(alpha = 0.08f)
        val BowlRim = ComposeColor.White.copy(alpha = 0.04f)
        val BodyStroke = ComposeColor.White.copy(alpha = 0.055f)
        val SkinSheen = ComposeColor.White.copy(alpha = 0.18f)
        val PurpleEdge = Color.PurplePrimary.copy(alpha = 0.11f)
    }

    object Shadow {
        val HardwareButton = 14.dp
        val HardwareSurface = 18.dp
        val Dock = 24.dp
        val StartButton = 28.dp
        val UserButton = 14.dp
        val ActiveIconGlow = 16.dp
        val InnerBottomAlpha = 0.45f
    }

    object Layer {
        const val OuterShell = 1
        const val RecessedBowl = 2
        const val Core = 3
        const val IconPlane = 4
        const val SkinOverlay = 5
        const val LocalGlow = 6
    }

    object Motion {
        const val PressScale = 0.96f
        const val DockTransitionMs = 280
        const val DockBreathingMs = 4200
        const val DockBreathingScale = 1.01f
        const val DockGlowIdleAlpha = 0.68f
        const val DockGlowPeakAlpha = 0.74f
        const val ScreenEnterMs = 320
        const val StaggerMs = 30
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

    object Dock {
        val Width = 320.dp
        val Height = 89.dp
        const val CenterY = 45f
        const val CenterSpacing = 77f
        val InactiveOuterDiameter = 80.dp
        val ActiveOuterDiameter = 89.dp
        val InactiveBowlDiameter = 54.dp
        val ActiveBowlDiameter = 58.dp
        val ActiveCoreDiameter = 58.dp
        val InactiveIconSize = 18.dp
        val ActiveIconSize = 22.dp
        const val InactiveOuterRadius = 40f
        const val ActiveOuterRadius = 44.5f
        const val ActiveGlowRadius = 70f
        const val ValleyDepth = 14f
        val Centers = listOf(44.5f, 121.5f, 198.5f, 275.5f)
    }

    object StartButton {
        val OuterShadow = Shadow.StartButton
        val BowlDiameter = 138.dp
        val ProgressDiameter = 104.dp
        val IconSize = 74.dp
        val IconGlow = Shadow.ActiveIconGlow
        const val OuterGlowRadius = 0.82f
        const val OuterGlowAlpha = 0.38f
        const val ShellHighlightAlpha = 0.075f
        const val RingRadiusRatio = 0.78f
        const val RingStrokeDp = 2f
        const val BowlRadiusMultiplier = 1.08f
        const val IconGlowAlpha = 0.42f
    }

    object UserButton {
        val Size = Home.ProfileButtonSize
        val IconSize = Home.ProfileIconSize
        const val GlowAlpha = 0.16f
        const val GlowRadius = 0.82f
        const val BowlRatio = 0.68f
        const val IconAlpha = 1f
    }

    object Servers {
        val TopPadding = 16.dp
        val HorizontalPadding = 24.dp
        val CompactHorizontalPadding = 18.dp
        val SegmentedHeight = 50.dp
        val SegmentedMaxWidth = 312.dp
        val AiCardRadius = 52.dp
        val AiCardHeight = 190.dp
        val AiBadgeSize = 64.dp
        val QuotaPillHeight = 76.dp
        val ActionPillHeight = 62.dp
        val NodeRowHeight = 60.dp
        val NodeFlagSize = 46.dp
        val NodeSelectorSize = 34.dp
        val DockBottomPadding = 28.dp
        val DockReservedHeight = 184.dp
    }

    object Subscription {
        val TopPadding = 18.dp
        val HorizontalPadding = 24.dp
        val CompactHorizontalPadding = 18.dp
        val TitleBottomGap = 18.dp
        val CardGap = 16.dp
        val BasicCardHeight = 184.dp
        val PremiumCardHeight = 236.dp
        val PlatinumCardHeight = 236.dp
        val CompactBasicCardHeight = 172.dp
        val CompactPremiumCardHeight = 222.dp
        val CompactPlatinumCardHeight = 222.dp
        val PlanIconSize = 62.dp
        val CompactPlanIconSize = 54.dp
        val CtaHeight = 48.dp
        val PaymentMethodHeight = 42.dp
        val GuaranteeIconSize = 42.dp
        val DockBottomPadding = 28.dp
        val DockReservedHeight = 192.dp
    }
}
