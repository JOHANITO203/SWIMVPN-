package com.swimvpn.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swimvpn.app.ui.theme.SwimDesignTokens

enum class SwimOrbState {
    Active,
    Transitioning,
    Idle,
    Error,
}

@Composable
fun SwimPowerOrb(
    state: SwimOrbState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    orbSize: Dp = SwimDesignTokens.Home.OrbSize,
    powerButtonSize: Dp = SwimDesignTokens.Home.PowerButtonSize,
) {
    val transition = rememberInfiniteTransition(label = "swimOrb")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (state == SwimOrbState.Active || state == SwimOrbState.Transitioning) 1.03f else 0.99f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbPulse",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.78f),
        label = "startButtonPressScale",
    )
    val pressResponse by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 90 else 260, easing = FastOutSlowInEasing),
        label = "startButtonPressResponse",
    )
    val accent = when (state) {
        SwimOrbState.Error -> Color(0xFFF87171)
        SwimOrbState.Idle -> SwimDesignTokens.Color.TextMuted
        else -> SwimDesignTokens.Color.PurpleActive
    }

    Box(
        modifier = modifier
            .size(orbSize)
            .scale(pulse),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(powerButtonSize)
                .scale(pressScale)
                .shadow(
                    SwimDesignTokens.StartButton.OuterShadow,
                    CircleShape,
                    spotColor = SwimDesignTokens.Color.PurplePrimary.copy(alpha = SwimDesignTokens.StartButton.OuterGlowAlpha)
                )
                .clip(CircleShape)
                .border(2.dp, SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.18f), CircleShape)
                .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension / 2f
                val ringRadius = outerRadius * SwimDesignTokens.StartButton.RingRadiusRatio
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SwimDesignTokens.Color.PurplePrimary.copy(
                                alpha = SwimDesignTokens.StartButton.OuterGlowAlpha + pressResponse * 0.16f
                            ),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = outerRadius * SwimDesignTokens.StartButton.OuterGlowRadius,
                    ),
                    radius = outerRadius * SwimDesignTokens.StartButton.OuterGlowRadius,
                    center = center,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SwimDesignTokens.Material.ShellTop.copy(alpha = SwimDesignTokens.StartButton.ShellHighlightAlpha),
                            SwimDesignTokens.Material.ShellMid,
                            SwimDesignTokens.Material.ShellBottom,
                        ),
                        center = Offset(center.x - outerRadius * 0.22f, center.y - outerRadius * 0.30f),
                        radius = outerRadius * 1.18f,
                    ),
                    radius = outerRadius,
                    center = center,
                )
                drawCircle(
                    color = SwimDesignTokens.Material.OuterDarkVeil,
                    radius = outerRadius * 0.88f,
                    center = Offset(center.x, center.y + outerRadius * 0.10f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SwimDesignTokens.Material.ShellMid,
                            SwimDesignTokens.Material.ShellBottom,
                        ),
                        center = center,
                        radius = ringRadius * 1.08f,
                    ),
                    radius = ringRadius,
                    center = center,
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.62f),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = SwimDesignTokens.StartButton.RingStrokeDp.dp.toPx()),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SwimDesignTokens.Highlight.SkinSheen.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset(center.x - outerRadius * 0.25f, center.y - outerRadius * 0.34f),
                        radius = outerRadius * 0.64f,
                    ),
                    radius = outerRadius * 0.64f,
                    center = Offset(center.x - outerRadius * 0.06f, center.y - outerRadius * 0.12f),
                )
                if (pressResponse > 0.01f) {
                    drawCircle(
                        color = accent.copy(alpha = 0.26f * pressResponse),
                        radius = outerRadius * (0.72f + pressResponse * 0.18f),
                        center = center,
                        style = Stroke(width = (2.2f + pressResponse * 2.2f).dp.toPx()),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(SwimDesignTokens.StartButton.BowlDiameter)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(1.dp, SwimDesignTokens.Highlight.InnerTop, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val bowlRadius = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SwimDesignTokens.Material.BowlTop,
                                SwimDesignTokens.Material.BowlMid,
                                SwimDesignTokens.Material.BowlBottom,
                            ),
                            center = Offset(center.x, center.y + bowlRadius * 0.22f),
                            radius = bowlRadius * SwimDesignTokens.StartButton.BowlRadiusMultiplier,
                        ),
                        radius = bowlRadius,
                        center = center,
                    )
                    drawCircle(
                        color = SwimDesignTokens.Material.BowlInnerShadow,
                        radius = bowlRadius,
                        center = center,
                        style = Stroke(width = 4.dp.toPx()),
                    )
                    drawCircle(
                        color = SwimDesignTokens.Highlight.BowlRim,
                        radius = bowlRadius,
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SwimDesignTokens.Highlight.SkinSheen,
                                Color.Transparent,
                            ),
                            center = Offset(center.x - bowlRadius * 0.28f, center.y - bowlRadius * 0.36f),
                            radius = bowlRadius * 0.74f,
                        ),
                        radius = bowlRadius * 0.74f,
                        center = Offset(center.x - bowlRadius * 0.08f, center.y - bowlRadius * 0.16f),
                    )
                }
                if (state == SwimOrbState.Transitioning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SwimDesignTokens.StartButton.ProgressDiameter),
                        color = SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.48f),
                        strokeWidth = 3.dp,
                        trackColor = Color.Transparent,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .size(SwimDesignTokens.StartButton.IconSize)
                        .shadow(
                            SwimDesignTokens.StartButton.IconGlow,
                            CircleShape,
                            spotColor = accent.copy(alpha = SwimDesignTokens.StartButton.IconGlowAlpha)
                        ),
                )
            }
        }
    }
}
