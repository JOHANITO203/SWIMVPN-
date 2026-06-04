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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swimvpn.app.ui.orb.VpnOrbState
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

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Only a rotating halo orbits the button (connecting = bright comet, connected = subtle arc);
        // all decorative background layers were removed — identical in dark + light theme.
        VpnAuroraStage(
            state = state,
            accent = accent,
            reducedMotion = isReducedMotionEnabled,
            modifier = Modifier
                .matchParentSize()
                .scale(stageBreath),
        )

        VpnHardwarePowerCore(
            state = state,
            accent = accent,
            enabled = enabled,
            pressScale = pressScale,
            breathScale = buttonBreath,
            glowAlpha = core.buttonGlowAlpha + pressEnergy * 0.14f,
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(buttonSize),
        )
    }
}

@Composable
private fun VpnAuroraStage(
    state: VpnOrbState,
    accent: Color,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "vpnAurora")
    val spinRaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (state == VpnOrbState.CONNECTING) 1100 else 9000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "auroraSpin",
    )
    // The connecting halo is functional feedback (not decoration), so it keeps spinning even under
    // reduced motion; only the idle/decorative spin is frozen.
    val spin = if (reducedMotion && state != VpnOrbState.CONNECTING) 0f else spinRaw
    val showRing = state == VpnOrbState.CONNECTING || state == VpnOrbState.CONNECTED
    val ringStrength = if (state == VpnOrbState.CONNECTING) 0.85f else 0.42f

    Box(modifier = modifier) {
        // Only the rotating halo remains around the button (all other external layers removed):
        // a bright fast comet while connecting, a subtle slow arc when connected.
        Canvas(modifier = Modifier.matchParentSize()) {
            if (!showRing) return@Canvas
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.43f
            val connecting = state == VpnOrbState.CONNECTING
            val haloR = radius * 1.08f
            rotate(degrees = spin, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = if (connecting) listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0f),
                            accent.copy(alpha = 0.9f),
                            Color.White,
                            Color.Transparent,
                        ) else listOf(
                            Color.Transparent,
                            accent.copy(alpha = ringStrength),
                            Color.Transparent,
                        ),
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = if (connecting) 130f else 150f,
                    useCenter = false,
                    topLeft = Offset(center.x - haloR, center.y - haloR),
                    size = Size(haloR * 2f, haloR * 2f),
                    style = Stroke(width = (if (connecting) 5f else 2.4f).dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun VpnHardwarePowerCore(
    state: VpnOrbState,
    accent: Color,
    enabled: Boolean,
    pressScale: Float,
    breathScale: Float,
    glowAlpha: Float,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    // Face brightness — the lamp "turns on" when connected; partial while connecting / unstable.
    val lit by animateFloatAsState(
        targetValue = when (state) {
            VpnOrbState.CONNECTED -> 1f
            VpnOrbState.UNSTABLE -> 0.85f
            VpnOrbState.CONNECTING -> 0.45f
            VpnOrbState.DISCONNECTED -> 0f
        },
        animationSpec = tween(620, easing = FastOutSlowInEasing),
        label = "coreLit",
    )
    // Theme-aware palette: dark = chrome/black groove/dark face + white glyph; light = brushed
    // silver + light recessed groove + light face + dark glyph. The lit "lamp" uses the accent.
    val light = LocalSwimVisualTokens.current == SwimDesignTokens.Light
    val chromeA = if (light) Color(0xFFC8C3D8) else ChromeDark
    val chromeB = if (light) Color(0xFFFCFBFE) else ChromeLight
    val chromeC = if (light) Color(0xFFEAE6F4) else ChromeLight2
    val bevelTop = if (light) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.18f)
    val bevelBot = if (light) Color(0xFF3D305C).copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.40f)
    val grooveColor = if (light) Color(0xFFD6CFE6) else GrooveBlack
    val grooveShadowA = if (light) Color(0xFF3D305C).copy(alpha = 0.26f) else Color.Black.copy(alpha = 0.9f)
    val grooveShadowB = if (light) Color(0xFF3D305C).copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.25f)
    val grooveRim = if (light) Color(0xFF3D305C).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f)
    val faceTopOff = if (light) Color(0xFFF5F2FB) else FaceDarkTop
    val faceMidOff = if (light) Color(0xFFE7E2F3) else FaceDarkMid
    val faceBotOff = if (light) Color(0xFFD7D0E9) else FaceDarkBottom
    val faceHiBase = if (light) 0.45f else 0.30f
    val faceBottomShadowMax = if (light) 0.16f else 0.45f
    val glyphOff = if (light) Color(0xFF35304A) else Color.White
    Box(
        modifier = modifier
            .scale(pressScale * breathScale)
            .shadow(
                elevation = SwimDesignTokens.Shadow.StartButton,
                shape = CircleShape,
                clip = false,
                spotColor = accent.copy(alpha = glowAlpha * 0.6f),
            )
            .clip(CircleShape)
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
            val c = Offset(size.width / 2f, size.height / 2f)
            val outerR = size.minDimension / 2f
            val grooveR = outerR * 0.825f
            val faceR = outerR * 0.63f

            // Layer 1 — chromed ring (metallic sweep + bevel)
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(chromeA, chromeB, chromeA, chromeC, chromeA, chromeB, chromeA, chromeC, chromeA),
                    center = c,
                ),
                radius = outerR,
                center = c,
            )
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(bevelTop, Color.Transparent, bevelBot),
                    startY = c.y - outerR,
                    endY = c.y + outerR,
                ),
                radius = outerR,
                center = c,
            )

            // Layer 2 — recessed groove (gives the ring its 3D relief)
            drawCircle(color = grooveColor, radius = grooveR, center = c)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(grooveShadowA, grooveShadowB, Color.Transparent),
                    center = Offset(c.x, c.y - grooveR * 0.18f),
                    radius = grooveR,
                ),
                radius = grooveR,
                center = c,
            )
            drawCircle(color = grooveRim, radius = grooveR, center = c, style = Stroke(width = 1.2.dp.toPx()))

            // lamp glow under the face (grows with `lit`)
            if (lit > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.55f * lit), Color.Transparent),
                        center = c,
                        radius = grooveR * (0.9f + 0.25f * lit),
                    ),
                    radius = grooveR * (0.9f + 0.25f * lit),
                    center = c,
                )
            }

            // Layer 3 — domed face (off -> lit accent)
            val faceTop = lerpColor(faceTopOff, lerpColor(accent, Color.White, 0.45f), lit)
            val faceMid = lerpColor(faceMidOff, accent, lit)
            val faceBot = lerpColor(faceBotOff, lerpColor(accent, Color.Black, 0.35f), lit)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(faceTop, faceMid, faceBot),
                    center = Offset(c.x, c.y - faceR * 0.34f),
                    radius = faceR * 1.18f,
                ),
                radius = faceR,
                center = c,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = faceHiBase + 0.25f * lit), Color.Transparent),
                    center = Offset(c.x - faceR * 0.20f, c.y - faceR * 0.42f),
                    radius = faceR * 0.8f,
                ),
                radius = faceR * 0.8f,
                center = Offset(c.x - faceR * 0.10f, c.y - faceR * 0.22f),
            )
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = faceBottomShadowMax * (1f - lit))),
                    startY = c.y - faceR,
                    endY = c.y + faceR,
                ),
                radius = faceR,
                center = c,
            )

            // geometric power glyph (dark off -> white lit)
            drawPowerGlyph(c, faceR * 0.40f, lerpColor(glyphOff, Color.White, lit), 2.6.dp.toPx())
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** Geometric power symbol: a ring with a top gap + a vertical bar. */
private fun DrawScope.drawPowerGlyph(center: Offset, r: Float, color: Color, stroke: Float) {
    drawArc(
        color = color,
        startAngle = -58f,
        sweepAngle = 296f,
        useCenter = false,
        topLeft = Offset(center.x - r, center.y - r),
        size = Size(r * 2f, r * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - r * 1.28f),
        end = Offset(center.x, center.y - r * 0.12f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private val ChromeDark = Color(0xFF26272A)
private val ChromeLight = Color(0xFF5C5E63)
private val ChromeLight2 = Color(0xFF56585C)
private val GrooveBlack = Color(0xFF050506)
private val FaceDarkTop = Color(0xFF3C3D42)
private val FaceDarkMid = Color(0xFF26272B)
private val FaceDarkBottom = Color(0xFF161719)

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
        accent = SwimDesignTokens.Color.TextMuted,
        breathingMs = 5600,
        stageBreathRange = 0.010f,
        buttonBreathRange = 0.006f,
        buttonGlowAlpha = 0.22f,
        shellLift = 0.18f,
        buttonSizeRatio = 0.62f,
    )
    VpnOrbState.CONNECTING -> HomeVpnCoreVisuals(
        accent = SwimDesignTokens.Material.PurpleCoreTop,
        breathingMs = 2800,
        stageBreathRange = 0.020f,
        buttonBreathRange = 0.014f,
        buttonGlowAlpha = 0.42f,
        shellLift = 0.34f,
        buttonSizeRatio = 0.63f,
    )
    VpnOrbState.CONNECTED -> HomeVpnCoreVisuals(
        accent = SwimDesignTokens.Color.PurpleActive,
        breathingMs = 4300,
        stageBreathRange = 0.014f,
        buttonBreathRange = 0.010f,
        buttonGlowAlpha = 0.34f,
        shellLift = 0.24f,
        buttonSizeRatio = 0.62f,
    )
    VpnOrbState.UNSTABLE -> HomeVpnCoreVisuals(
        accent = SwimDesignTokens.Color.Warning,
        breathingMs = 3400,
        stageBreathRange = 0.018f,
        buttonBreathRange = 0.012f,
        buttonGlowAlpha = 0.30f,
        shellLift = 0.22f,
        buttonSizeRatio = 0.62f,
    )
}

