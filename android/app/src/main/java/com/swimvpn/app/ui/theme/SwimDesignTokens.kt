package com.swimvpn.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp

data class SwimColorTokens(
    val homeBackgroundDeep: ComposeColor,
    val homeBackgroundBase: ComposeColor,
    val homeSurfaceBase: ComposeColor,
    val homeSurfaceElevated: ComposeColor,
    val homeSurfaceHighlight: ComposeColor,
    val homePurplePrimary: ComposeColor,
    val homePurpleActive: ComposeColor,
    val homePurpleDeep: ComposeColor,
    val homeSuccessGreen: ComposeColor,
    val homeTextPrimary: ComposeColor,
    val homeTextSecondary: ComposeColor,
    val homeTextMuted: ComposeColor,
    val homeDividerSubtle: ComposeColor,
    val homeStrokeSubtle: ComposeColor,
    val homeTopHighlight: ComposeColor,
)

data class SwimMaterialTokens(
    val shellTop: ComposeColor,
    val shellMid: ComposeColor,
    val shellBottom: ComposeColor,
    val bowlTop: ComposeColor,
    val bowlMid: ComposeColor,
    val bowlBottom: ComposeColor,
    val purpleCoreTop: ComposeColor,
    val purpleCoreMid: ComposeColor,
    val purpleCoreBottom: ComposeColor,
    val outerDarkVeil: ComposeColor,
    val bowlInnerShadow: ComposeColor,
)

data class SwimHighlightTokens(
    val innerTop: ComposeColor,
    val bowlRim: ComposeColor,
    val bodyStroke: ComposeColor,
    val skinSheen: ComposeColor,
    val purpleEdge: ComposeColor,
)

data class SwimVisualTokens(
    val color: SwimColorTokens,
    val material: SwimMaterialTokens,
    val highlight: SwimHighlightTokens,
)

object SwimDesignTokens {
    val Dark = SwimVisualTokens(
        color = SwimColorTokens(
            homeBackgroundDeep = ComposeColor(0xFF07070B),
            homeBackgroundBase = ComposeColor(0xFF0D0C13),
            homeSurfaceBase = ComposeColor(0xFF131317),
            homeSurfaceElevated = ComposeColor(0xFF1A1A20),
            homeSurfaceHighlight = ComposeColor(0xFF24232B),
            homePurplePrimary = ComposeColor(0xFF8A6AF1),
            homePurpleActive = ComposeColor(0xFFA489E7),
            homePurpleDeep = ComposeColor(0xFF443677),
            homeSuccessGreen = ComposeColor(0xFF35D978),
            homeTextPrimary = ComposeColor(0xFFF3F1F6),
            homeTextSecondary = ComposeColor(0xFFA6A1B3),
            homeTextMuted = ComposeColor(0xFF6E6978),
            homeDividerSubtle = ComposeColor.White.copy(alpha = 0.08f),
            homeStrokeSubtle = ComposeColor.White.copy(alpha = 0.10f),
            homeTopHighlight = ComposeColor.White.copy(alpha = 0.08f),
        ),
        material = SwimMaterialTokens(
            shellTop = ComposeColor.White.copy(alpha = 0.07f),
            shellMid = ComposeColor(0xFF17171C),
            shellBottom = ComposeColor(0xFF07070B),
            bowlTop = ComposeColor(0xFF101116),
            bowlMid = ComposeColor(0xFF05060A),
            bowlBottom = ComposeColor.Black.copy(alpha = 0.96f),
            purpleCoreTop = ComposeColor(0xFFB89AFF),
            purpleCoreMid = ComposeColor(0xFF8A6AF1),
            purpleCoreBottom = ComposeColor(0xFF5D3BD8),
            outerDarkVeil = ComposeColor.Black.copy(alpha = 0.40f),
            bowlInnerShadow = ComposeColor.Black.copy(alpha = 0.60f),
        ),
        highlight = SwimHighlightTokens(
            innerTop = ComposeColor.White.copy(alpha = 0.08f),
            bowlRim = ComposeColor.White.copy(alpha = 0.04f),
            bodyStroke = ComposeColor.White.copy(alpha = 0.055f),
            skinSheen = ComposeColor.White.copy(alpha = 0.18f),
            purpleEdge = ComposeColor(0xFF8A6AF1).copy(alpha = 0.11f),
        ),
    )

    val Light = SwimVisualTokens(
        color = SwimColorTokens(
            homeBackgroundDeep = ComposeColor(0xFFF8F4FF),
            homeBackgroundBase = ComposeColor(0xFFF1EBFC),
            homeSurfaceBase = ComposeColor(0xFFFFFFFF),
            homeSurfaceElevated = ComposeColor(0xFFFAF7FF),
            homeSurfaceHighlight = ComposeColor(0xFFEFE7FF),
            homePurplePrimary = ComposeColor(0xFF7C5BE8),
            homePurpleActive = ComposeColor(0xFF6D4FD8),
            homePurpleDeep = ComposeColor(0xFFD8CCFF),
            homeSuccessGreen = ComposeColor(0xFF168A4B),
            homeTextPrimary = ComposeColor(0xFF17121F),
            homeTextSecondary = ComposeColor(0xFF625A72),
            homeTextMuted = ComposeColor(0xFF8A8296),
            homeDividerSubtle = ComposeColor(0xFF24164A).copy(alpha = 0.10f),
            homeStrokeSubtle = ComposeColor(0xFF24164A).copy(alpha = 0.12f),
            homeTopHighlight = ComposeColor.White.copy(alpha = 0.74f),
        ),
        material = SwimMaterialTokens(
            shellTop = ComposeColor.White.copy(alpha = 0.84f),
            shellMid = ComposeColor(0xFFF6F0FF),
            shellBottom = ComposeColor(0xFFE5DCF5),
            bowlTop = ComposeColor(0xFFFFFFFF),
            bowlMid = ComposeColor(0xFFF2ECFB),
            bowlBottom = ComposeColor(0xFFE4DBF0),
            purpleCoreTop = ComposeColor(0xFFB89AFF),
            purpleCoreMid = ComposeColor(0xFF8A6AF1),
            purpleCoreBottom = ComposeColor(0xFF5D3BD8),
            outerDarkVeil = ComposeColor(0xFF24164A).copy(alpha = 0.10f),
            bowlInnerShadow = ComposeColor(0xFF24164A).copy(alpha = 0.18f),
        ),
        highlight = SwimHighlightTokens(
            innerTop = ComposeColor.White.copy(alpha = 0.78f),
            bowlRim = ComposeColor.White.copy(alpha = 0.56f),
            bodyStroke = ComposeColor(0xFF24164A).copy(alpha = 0.075f),
            skinSheen = ComposeColor.White.copy(alpha = 0.64f),
            purpleEdge = ComposeColor(0xFF7C5BE8).copy(alpha = 0.16f),
        ),
    )

    private var activeVisualTokens: SwimVisualTokens = Dark

    fun activateVisualTokens(tokens: SwimVisualTokens) {
        activeVisualTokens = tokens
    }

    val Current: SwimVisualTokens
        get() = activeVisualTokens

    object Color {
        val HomeBackgroundDeep get() = Current.color.homeBackgroundDeep
        val HomeBackgroundBase get() = Current.color.homeBackgroundBase
        val HomeSurfaceBase get() = Current.color.homeSurfaceBase
        val HomeSurfaceElevated get() = Current.color.homeSurfaceElevated
        val HomeSurfaceHighlight get() = Current.color.homeSurfaceHighlight
        val HomePurplePrimary get() = Current.color.homePurplePrimary
        val HomePurpleActive get() = Current.color.homePurpleActive
        val HomePurpleDeep get() = Current.color.homePurpleDeep
        val HomeSuccessGreen get() = Current.color.homeSuccessGreen
        val HomeTextPrimary get() = Current.color.homeTextPrimary
        val HomeTextSecondary get() = Current.color.homeTextSecondary
        val HomeTextMuted get() = Current.color.homeTextMuted
        val HomeDividerSubtle get() = Current.color.homeDividerSubtle
        val HomeStrokeSubtle get() = Current.color.homeStrokeSubtle
        val HomeTopHighlight get() = Current.color.homeTopHighlight

        val BackgroundDeep get() = HomeBackgroundDeep
        val BackgroundBase get() = HomeBackgroundBase
        val SurfaceBase get() = HomeSurfaceBase
        val SurfaceElevated get() = HomeSurfaceElevated
        val SurfaceHighlight get() = HomeSurfaceHighlight
        val PurplePrimary get() = HomePurplePrimary
        val PurpleActive get() = HomePurpleActive
        val PurpleDeep get() = HomePurpleDeep
        val SuccessGreen get() = HomeSuccessGreen
        val TextPrimary get() = HomeTextPrimary
        val TextSecondary get() = HomeTextSecondary
        val TextMuted get() = HomeTextMuted
        val DividerSubtle get() = HomeDividerSubtle
        val StrokeSubtle get() = HomeStrokeSubtle
    }

    object Shape {
        val Pill = RoundedCornerShape(percent = 50)
        val HardwareCard = RoundedCornerShape(48.dp)
        val LargeHardwareCard = RoundedCornerShape(52.dp)
        val Control = RoundedCornerShape(28.dp)
    }

    object Material {
        val ShellTop get() = Current.material.shellTop
        val ShellMid get() = Current.material.shellMid
        val ShellBottom get() = Current.material.shellBottom
        val BowlTop get() = Current.material.bowlTop
        val BowlMid get() = Current.material.bowlMid
        val BowlBottom get() = Current.material.bowlBottom
        val PurpleCoreTop get() = Current.material.purpleCoreTop
        val PurpleCoreMid get() = Current.material.purpleCoreMid
        val PurpleCoreBottom get() = Current.material.purpleCoreBottom
        val OuterDarkVeil get() = Current.material.outerDarkVeil
        val BowlInnerShadow get() = Current.material.bowlInnerShadow
    }

    object Highlight {
        val InnerTop get() = Current.highlight.innerTop
        val BowlRim get() = Current.highlight.bowlRim
        val BodyStroke get() = Current.highlight.bodyStroke
        val SkinSheen get() = Current.highlight.skinSheen
        val PurpleEdge get() = Current.highlight.purpleEdge
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
        const val DockTransitionMs = 540
        const val DockBreathingMs = 4200
        const val DockBreathingScale = 1.01f
        const val DockGlowIdleAlpha = 0.70f
        const val DockGlowPeakAlpha = 0.80f
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

val LocalSwimVisualTokens = compositionLocalOf { SwimDesignTokens.Dark }
