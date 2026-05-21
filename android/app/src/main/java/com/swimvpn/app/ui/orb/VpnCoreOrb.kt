package com.swimvpn.app.ui.orb

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (PI * 2.0).toFloat()

data class VpnOrbVisualConfig(
    val meshAlpha: Float,
    val glowAlpha: Float,
    val particleAlpha: Float,
    val deformationAmplitudeDp: Float,
    val rotationSpeed: Float,
    val powerAlpha: Float,
    val powerColor: Color,
)

@Composable
fun VpnCoreOrb(
    state: VpnOrbState,
    modifier: Modifier = Modifier,
    isReducedMotionEnabled: Boolean = false,
    onClick: () -> Unit = {},
) {
    val config = state.visualConfig(isReducedMotionEnabled)
    val meshAlpha by animateFloatAsState(
        targetValue = config.meshAlpha,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "orbMeshAlpha",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = config.glowAlpha,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "orbGlowAlpha",
    )
    val particleAlpha by animateFloatAsState(
        targetValue = config.particleAlpha,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "orbParticleAlpha",
    )
    val powerAlpha by animateFloatAsState(
        targetValue = config.powerAlpha,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "orbPowerAlpha",
    )
    val powerColor by animateColorAsState(
        targetValue = config.powerColor,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "orbPowerColor",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = tween(110, easing = FastOutSlowInEasing),
        label = "orbPressScale",
    )
    val orbitTransition = rememberInfiniteTransition(label = "orbCanvasLoop")
    val orbitProgress by orbitTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isReducedMotionEnabled) 30000 else 12000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbCanvasProgress",
    )

    Box(
        modifier = modifier.size(VpnCoreOrbDimens.OrbSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val densityScale = size.minDimension / VpnCoreOrbDimens.OrbSize.toPx()
            val outerShadowRadius = VpnCoreOrbDimens.ButtonOuterShadow.toPx() / 2f * densityScale
            val outerShellRadius = VpnCoreOrbDimens.ButtonOuterShell.toPx() / 2f * densityScale * pressScale
            val middleRingRadius = VpnCoreOrbDimens.ButtonMiddleRing.toPx() / 2f * densityScale * pressScale
            val innerBowlRadius = VpnCoreOrbDimens.ButtonInnerBowl.toPx() / 2f * densityScale * pressScale

            drawOrganicOrbitalMesh(
                center = center,
                densityScale = densityScale,
                meshAlpha = meshAlpha,
                glowAlpha = glowAlpha,
                particleAlpha = particleAlpha,
                deformationAmplitude = config.deformationAmplitudeDp.dp.toPx() * densityScale,
                time = orbitProgress * TWO_PI * config.rotationSpeed,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.64f),
                        Color.Black.copy(alpha = 0.38f),
                        Color.Transparent,
                    ),
                    center = Offset(center.x, center.y + 10.dp.toPx() * densityScale),
                    radius = outerShadowRadius * 1.22f,
                ),
                radius = outerShadowRadius * 1.22f,
                center = Offset(center.x, center.y + 10.dp.toPx() * densityScale),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        VpnCoreOrbTokens.ButtonOuterTop,
                        VpnCoreOrbTokens.ButtonOuterBottom,
                    ),
                    center = Offset(center.x - outerShellRadius * 0.26f, center.y - outerShellRadius * 0.32f),
                    radius = outerShellRadius * 1.18f,
                ),
                radius = outerShellRadius,
                center = center,
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.44f),
                radius = middleRingRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx() * densityScale),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF101016),
                        VpnCoreOrbTokens.ButtonInner,
                        Color(0xFF030305),
                    ),
                    center = Offset(center.x - innerBowlRadius * 0.18f, center.y - innerBowlRadius * 0.26f),
                    radius = innerBowlRadius * 1.12f,
                ),
                radius = innerBowlRadius,
                center = center,
            )
            drawCircle(
                color = VpnCoreOrbTokens.ButtonRim.copy(alpha = 0.72f),
                radius = innerBowlRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx() * densityScale),
            )

            drawPowerIcon(
                center = center,
                size = VpnCoreOrbDimens.PowerIconSize.toPx() * densityScale,
                strokeWidth = VpnCoreOrbDimens.PowerIconStroke.toPx() * densityScale,
                color = powerColor,
                alpha = powerAlpha,
                glowRadius = 18.dp.toPx() * densityScale,
            )
        }

        Box(
            modifier = Modifier
                .size(VpnCoreOrbDimens.ButtonOuterShell)
                .scale(pressScale)
                .clip(CircleShape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        )
    }
}

private fun DrawScope.drawOrganicOrbitalMesh(
    center: Offset,
    densityScale: Float,
    meshAlpha: Float,
    glowAlpha: Float,
    particleAlpha: Float,
    deformationAmplitude: Float,
    time: Float,
) {
    val baseRadius = VpnCoreOrbDimens.MeshBaseRadius.toPx() * densityScale
    val ringHalfThickness = VpnCoreOrbDimens.MeshThickness.toPx() * 0.52f * densityScale
    val outerRadius = baseRadius + ringHalfThickness

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                VpnCoreOrbTokens.PurpleSoftGlow.copy(alpha = glowAlpha * 0.24f),
                VpnCoreOrbTokens.PurplePrimary.copy(alpha = glowAlpha * 0.12f),
                Color.Transparent,
            ),
            center = center,
            radius = outerRadius * 1.22f,
        ),
        radius = outerRadius * 1.22f,
        center = center,
    )

    repeat(6) { strand ->
        val phase = strand * TWO_PI / 6f
        val path = Path()
        repeat(132) { index ->
            val theta = index / 131f * TWO_PI
            val wave1 = sin(theta * 3f + time * 0.72f + phase)
            val wave2 = sin(theta * 7f - time * 0.38f + phase * 1.7f)
            val wave3 = sin(theta * 11f + time * 0.18f)
            val radius = baseRadius + wave1 * deformationAmplitude + wave2 * deformationAmplitude * 0.35f + wave3 * deformationAmplitude * 0.16f
            val angle = theta + time * 0.18f + phase * 0.08f
            val x = center.x + cos(angle) * radius * 1.06f
            val y = center.y + sin(angle) * radius * 0.88f
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = VpnCoreOrbTokens.PurpleSecondary.copy(alpha = meshAlpha * (0.22f + strand * 0.025f)),
            style = Stroke(
                width = (0.8.dp.toPx() + strand * 0.05.dp.toPx()) * densityScale,
                cap = StrokeCap.Round,
            ),
        )
    }

    repeat(72) { index ->
        val seed = index + 1f
        val angle = seed * 2.39996f + time * (0.11f + (index % 5) * 0.008f)
        val radiusOffset = sin(seed * 1.73f) * ringHalfThickness
        val pulse = sin(time * 0.65f + seed * 0.41f) * deformationAmplitude * 0.52f
        val radius = baseRadius + radiusOffset + pulse
        val x = center.x + cos(angle) * radius * 1.06f
        val y = center.y + sin(angle) * radius * 0.88f
        val dotRadius = (0.65.dp.toPx() + (index % 4) * 0.16.dp.toPx()) * densityScale
        val alpha = particleAlpha * (0.20f + (index % 7) * 0.045f)
        drawCircle(
            color = VpnCoreOrbTokens.PurpleSoftGlow.copy(alpha = alpha.coerceAtMost(0.58f)),
            radius = dotRadius,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawPowerIcon(
    center: Offset,
    size: Float,
    strokeWidth: Float,
    color: Color,
    alpha: Float,
    glowRadius: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.34f * alpha),
                color.copy(alpha = 0.12f * alpha),
                Color.Transparent,
            ),
            center = center,
            radius = glowRadius * 2.3f,
        ),
        radius = glowRadius * 2.3f,
        center = center,
    )

    val radius = size * 0.34f
    val arcTopLeft = Offset(center.x - radius, center.y - radius * 0.46f)
    drawArc(
        color = color.copy(alpha = alpha),
        startAngle = 134f,
        sweepAngle = 272f,
        useCenter = false,
        topLeft = arcTopLeft,
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
    drawLine(
        color = color.copy(alpha = alpha),
        start = Offset(center.x, center.y - size * 0.40f),
        end = Offset(center.x, center.y - size * 0.02f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

private fun VpnOrbState.visualConfig(isReducedMotionEnabled: Boolean): VpnOrbVisualConfig {
    val config = when (this) {
        VpnOrbState.DISCONNECTED -> VpnOrbVisualConfig(
            meshAlpha = 0.22f,
            glowAlpha = 0.10f,
            particleAlpha = 0.14f,
            deformationAmplitudeDp = 3.5f,
            rotationSpeed = 0.58f,
            powerAlpha = 0.48f,
            powerColor = VpnCoreOrbTokens.Muted,
        )
        VpnOrbState.CONNECTING -> VpnOrbVisualConfig(
            meshAlpha = 0.58f,
            glowAlpha = 0.28f,
            particleAlpha = 0.30f,
            deformationAmplitudeDp = 9.5f,
            rotationSpeed = 1.35f,
            powerAlpha = 0.88f,
            powerColor = VpnCoreOrbTokens.PurpleSecondary,
        )
        VpnOrbState.CONNECTED -> VpnOrbVisualConfig(
            meshAlpha = 0.66f,
            glowAlpha = 0.32f,
            particleAlpha = 0.30f,
            deformationAmplitudeDp = 6.5f,
            rotationSpeed = 0.86f,
            powerAlpha = 1.0f,
            powerColor = VpnCoreOrbTokens.PurplePrimary,
        )
        VpnOrbState.UNSTABLE -> VpnOrbVisualConfig(
            meshAlpha = 0.52f,
            glowAlpha = 0.22f,
            particleAlpha = 0.28f,
            deformationAmplitudeDp = 11.0f,
            rotationSpeed = 1.05f,
            powerAlpha = 0.82f,
            powerColor = VpnCoreOrbTokens.PurpleSecondary,
        )
    }
    return if (isReducedMotionEnabled) {
        config.copy(
            meshAlpha = config.meshAlpha * 0.72f,
            glowAlpha = config.glowAlpha * 0.64f,
            particleAlpha = config.particleAlpha * 0.62f,
            deformationAmplitudeDp = config.deformationAmplitudeDp * 0.35f,
            rotationSpeed = config.rotationSpeed * 0.18f,
        )
    } else {
        config
    }
}
