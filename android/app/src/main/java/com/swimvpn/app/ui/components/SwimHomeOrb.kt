package com.swimvpn.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swimvpn.app.ui.theme.SwimDesignTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(14000), repeatMode = RepeatMode.Restart),
        label = "meshPhase",
    )
    val interactionSource = remember { MutableInteractionSource() }
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
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val meshRadius = size.minDimension * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.30f),
                        SwimDesignTokens.Color.PurpleDeep.copy(alpha = 0.16f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = meshRadius * 1.28f,
                ),
                radius = meshRadius * 1.28f,
                center = center,
            )
            repeat(3) { index ->
                val path = Path()
                val offset = phase + index * 63f
                val points = 96
                for (i in 0..points) {
                    val t = (i / points.toFloat()) * 2f * PI.toFloat()
                    val wave = sin(t * (2.2f + index * 0.25f) + offset * PI.toFloat() / 180f) * meshRadius * (0.035f + index * 0.01f)
                    val rx = meshRadius * (1f - index * 0.035f) + wave
                    val ry = meshRadius * (0.82f + index * 0.045f) - wave * 0.30f
                    val x = center.x + cos(t + index * 0.30f) * rx
                    val y = center.y + sin(t + index * 0.42f) * ry
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.34f - index * 0.06f),
                    style = Stroke(width = (1.6f - index * 0.2f).dp.toPx()),
                )
            }
            val dotStep = 9
            for (ring in 0..8) {
                val r = meshRadius * (0.54f + ring * 0.052f)
                for (angle in 0 until 360 step dotStep) {
                    val rad = (angle + phase * 0.18f + ring * 8f) * PI.toFloat() / 180f
                    val flatten = 0.80f + ring * 0.018f
                    val x = center.x + cos(rad) * r
                    val y = center.y + sin(rad) * r * flatten
                    val alpha = 0.10f + ring * 0.025f
                    drawCircle(
                        color = SwimDesignTokens.Color.PurpleActive.copy(alpha = alpha.coerceAtMost(0.34f)),
                        radius = 1.1.dp.toPx(),
                        center = Offset(x, y),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(powerButtonSize)
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
                            SwimDesignTokens.Color.PurplePrimary.copy(alpha = SwimDesignTokens.StartButton.OuterGlowAlpha),
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
