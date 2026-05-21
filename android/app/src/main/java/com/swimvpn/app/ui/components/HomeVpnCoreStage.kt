package com.swimvpn.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swimvpn.app.ui.orb.VpnOrbState
import com.swimvpn.app.ui.orb3d.OrbRenderQuality
import com.swimvpn.app.ui.orb3d.SwimHolographicOrb3D
import com.swimvpn.app.ui.orb3d.SwimParticleOrbState
import com.swimvpn.app.ui.theme.LocalSwimVisualTokens
import com.swimvpn.app.ui.theme.SwimDesignTokens

@Composable
fun HomeVpnCoreStage(
    state: VpnOrbState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = SwimDesignTokens.Home.OrbSize,
    enabled: Boolean = true,
    isReducedMotionEnabled: Boolean = false,
) {
    val core = state.coreVisuals()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.962f else 1f,
        animationSpec = spring(stiffness = 560f, dampingRatio = 0.82f),
        label = "homeVpnCorePressScale",
    )
    val pressEnergy by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 90 else 240, easing = FastOutSlowInEasing),
        label = "homeVpnCorePressEnergy",
    )
    val accent by animateColorAsState(
        targetValue = core.accent,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "homeVpnCoreAccent",
    )
    val infinite = rememberInfiniteTransition(label = "homeVpnCoreBreathing")
    val breath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isReducedMotionEnabled) 7600 else core.breathingMs,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "homeVpnCoreBreath",
    )
    val stageBreath = 1f + (breath - 0.5f) * core.stageBreathRange
    val buttonBreath = 1f + (breath - 0.5f) * core.buttonBreathRange
    val buttonSize = size * core.buttonSizeRatio
    val bowlSize = buttonSize * 0.67f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        SwimHolographicOrb3D(
            state = state.toHolographicState(),
            isReducedMotionEnabled = isReducedMotionEnabled,
            quality = if (isReducedMotionEnabled) OrbRenderQuality.Low else OrbRenderQuality.Auto,
            interactionEnabled = false,
            renderBehindCompose = false,
            modifier = Modifier
                .matchParentSize()
                .scale(stageBreath),
        )

        VpnHardwarePowerCore(
            accent = accent,
            enabled = enabled,
            isConnecting = state == VpnOrbState.CONNECTING,
            pressScale = pressScale,
            breathScale = buttonBreath,
            glowAlpha = core.buttonGlowAlpha + pressEnergy * 0.14f,
            shellLift = core.shellLift + pressEnergy * 0.10f,
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(buttonSize),
            bowlSize = bowlSize,
        )
    }
}

@Composable
private fun VpnHardwarePowerCore(
    accent: Color,
    enabled: Boolean,
    isConnecting: Boolean,
    pressScale: Float,
    breathScale: Float,
    glowAlpha: Float,
    shellLift: Float,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    bowlSize: Dp,
) {
    val tokens = LocalSwimVisualTokens.current
    Box(
        modifier = modifier
            .scale(pressScale * breathScale)
            .shadow(
                elevation = SwimDesignTokens.Shadow.StartButton,
                shape = CircleShape,
                clip = false,
                spotColor = accent.copy(alpha = glowAlpha * 0.62f),
                ambientColor = Color.Black.copy(alpha = 0.72f),
            )
            .clip(CircleShape)
            .border(1.dp, accent.copy(alpha = 0.22f + glowAlpha * 0.18f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = glowAlpha),
                        accent.copy(alpha = glowAlpha * 0.18f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius * 1.36f,
                ),
                radius = radius * 1.36f,
                center = center,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tokens.material.shellTop.copy(alpha = 0.09f + shellLift * 0.05f),
                        tokens.material.shellMid.copy(alpha = 0.98f),
                        tokens.material.shellBottom,
                    ),
                    center = Offset(center.x - radius * 0.24f, center.y - radius * 0.36f),
                    radius = radius * 1.16f,
                ),
                radius = radius,
                center = center,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tokens.highlight.skinSheen.copy(alpha = 0.16f + shellLift * 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(center.x - radius * 0.28f, center.y - radius * 0.36f),
                    radius = radius * 0.72f,
                ),
                radius = radius * 0.72f,
                center = Offset(center.x - radius * 0.08f, center.y - radius * 0.12f),
            )

            drawCircle(
                color = Color.Black.copy(alpha = 0.58f),
                radius = radius * 0.82f,
                center = Offset(center.x, center.y + radius * 0.09f),
            )

            drawCircle(
                color = accent.copy(alpha = 0.18f + glowAlpha * 0.20f),
                radius = radius * 0.91f,
                center = center,
                style = Stroke(width = 1.25.dp.toPx()),
            )
        }

        Box(
            modifier = Modifier
                .size(bowlSize)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(1.dp, tokens.highlight.innerTop.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.material.bowlTop.copy(alpha = 0.98f),
                            tokens.material.bowlMid.copy(alpha = 0.98f),
                            tokens.material.bowlBottom,
                        ),
                        center = Offset(center.x, center.y + radius * 0.20f),
                        radius = radius * 1.12f,
                    ),
                    radius = radius,
                    center = center,
                )

                drawCircle(
                    color = tokens.material.bowlInnerShadow,
                    radius = radius * 0.98f,
                    center = center,
                    style = Stroke(width = 4.dp.toPx()),
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = glowAlpha * 0.50f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius * 0.92f,
                    ),
                    radius = radius * 0.92f,
                    center = center,
                )

                if (isConnecting) {
                    drawArc(
                        color = accent.copy(alpha = 0.48f),
                        startAngle = -90f,
                        sweepAngle = 238f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .size(bowlSize * 0.54f)
                    .shadow(
                        elevation = SwimDesignTokens.StartButton.IconGlow,
                        shape = CircleShape,
                        spotColor = accent.copy(alpha = SwimDesignTokens.StartButton.IconGlowAlpha),
                    ),
            )
        }
    }
}

private data class HomeVpnCoreVisuals(
    val accent: Color,
    val breathingMs: Int,
    val stageBreathRange: Float,
    val buttonBreathRange: Float,
    val buttonGlowAlpha: Float,
    val shellLift: Float,
    val buttonSizeRatio: Float,
)

private fun VpnOrbState.coreVisuals(): HomeVpnCoreVisuals = when (this) {
    VpnOrbState.DISCONNECTED -> HomeVpnCoreVisuals(
        accent = Color(0xFF8F7BCB),
        breathingMs = 5600,
        stageBreathRange = 0.010f,
        buttonBreathRange = 0.006f,
        buttonGlowAlpha = 0.22f,
        shellLift = 0.18f,
        buttonSizeRatio = 0.49f,
    )
    VpnOrbState.CONNECTING -> HomeVpnCoreVisuals(
        accent = SwimDesignTokens.Material.PurpleCoreTop,
        breathingMs = 2800,
        stageBreathRange = 0.020f,
        buttonBreathRange = 0.014f,
        buttonGlowAlpha = 0.42f,
        shellLift = 0.34f,
        buttonSizeRatio = 0.51f,
    )
    VpnOrbState.CONNECTED -> HomeVpnCoreVisuals(
        accent = SwimDesignTokens.Color.PurpleActive,
        breathingMs = 4300,
        stageBreathRange = 0.014f,
        buttonBreathRange = 0.010f,
        buttonGlowAlpha = 0.34f,
        shellLift = 0.24f,
        buttonSizeRatio = 0.50f,
    )
    VpnOrbState.UNSTABLE -> HomeVpnCoreVisuals(
        accent = Color(0xFFFF7AF6),
        breathingMs = 3400,
        stageBreathRange = 0.018f,
        buttonBreathRange = 0.012f,
        buttonGlowAlpha = 0.30f,
        shellLift = 0.22f,
        buttonSizeRatio = 0.50f,
    )
}

private fun VpnOrbState.toHolographicState(): SwimParticleOrbState = when (this) {
    VpnOrbState.DISCONNECTED -> SwimParticleOrbState.DISCONNECTED
    VpnOrbState.CONNECTING -> SwimParticleOrbState.CONNECTING
    VpnOrbState.CONNECTED -> SwimParticleOrbState.CONNECTED
    VpnOrbState.UNSTABLE -> SwimParticleOrbState.UNSTABLE
}
