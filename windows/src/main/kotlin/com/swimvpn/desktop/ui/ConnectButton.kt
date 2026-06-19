package com.swimvpn.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swimvpn.desktop.theme.SwimDesignTokens
import com.swimvpn.desktop.vpn.VpnState

/**
 * The SWIMVPN connect "power" control — the signature hardware-grammar button: a recessed dark
 * bowl, a purple core that lights when active, a thin rim and an outer glow. Same token palette
 * as the Android Home so the desktop app shares the visual identity.
 */
@Composable
fun ConnectButton(state: VpnState, onClick: () -> Unit) {
    val tokens = SwimDesignTokens.Current
    val active = state == VpnState.CONNECTED
    val connecting = state == VpnState.CONNECTING
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    // Idle hover halo: a faint purple bloom hints the button is pressable, premium feel.
    val hoverGlow by animateFloatAsState(
        if (hovered && !active && !connecting) 1f else 0f,
        tween(220, easing = SwimEaseOut), label = "hoverGlow",
    )

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val coreAlpha = when {
        active -> 1f
        connecting -> pulse
        else -> 0.5f
    }
    val coreTop = if (active || connecting) tokens.material.purpleCoreTop else tokens.color.homeTextMuted
    val coreMid = if (active || connecting) tokens.material.purpleCoreMid else tokens.color.homeSurfaceHighlight
    val coreBottom = if (active || connecting) tokens.material.purpleCoreBottom else tokens.material.bowlBottom

    Box(
        modifier = Modifier
            .size(206.dp)
            .interactive(src, pressScale = 0.96f, hoverScale = 1.015f)
            .clip(CircleShape)
            .drawBehind {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f
                // Idle hover halo
                if (hoverGlow > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to tokens.material.purpleCoreMid.copy(alpha = 0.20f * hoverGlow),
                            1f to Color.Transparent,
                            center = c, radius = r,
                        ),
                        radius = r, center = c,
                    )
                }
                // Outer glow (only when lit)
                if (active || connecting) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to tokens.material.purpleCoreMid.copy(alpha = 0.38f * coreAlpha),
                            1f to Color.Transparent,
                            center = c, radius = r,
                        ),
                        radius = r,
                    )
                }
                // Recessed bowl
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to tokens.material.bowlTop,
                        1f to tokens.material.bowlBottom,
                        center = Offset(c.x, c.y - r * 0.2f), radius = r * 0.95f,
                    ),
                    radius = r * 0.92f, center = c,
                )
                // Purple core
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to coreTop.copy(alpha = coreAlpha),
                        0.55f to coreMid.copy(alpha = coreAlpha),
                        1f to coreBottom.copy(alpha = coreAlpha),
                        center = Offset(c.x, c.y - r * 0.18f), radius = r * 0.62f,
                    ),
                    radius = r * 0.58f, center = c,
                )
                // Thin rim
                drawCircle(
                    color = if (active) tokens.color.homeStrokeActive else tokens.color.homeStrokeSubtle,
                    radius = r * 0.92f, center = c,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
            }
            .clickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = "Connecter",
            tint = if (active || connecting) tokens.color.homeTextPrimary else tokens.color.homeTextSecondary,
            modifier = Modifier.size(62.dp),
        )
    }
}
