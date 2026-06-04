package com.swimvpn.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swimvpn.app.R
import com.swimvpn.app.ui.theme.LocalSwimVisualTokens
import com.swimvpn.app.ui.theme.SwimDesignTokens
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

enum class SwimDockDestination {
    Home,
    Servers,
    Proxy,
    Subscription,
    Settings,
}

enum class NavDockItem {
    HOME,
    SERVERS,
    PROXY,
    SUBSCRIPTION,
    SETTINGS,
}

private var lastDockItemForCrossScreenMotion: NavDockItem = NavDockItem.HOME

@Composable
fun SwimMetaballDock(
    active: SwimDockDestination,
    onHome: () -> Unit,
    onServers: () -> Unit,
    onProxy: () -> Unit = {},
    onSubscription: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = DockTokens.Height,
    width: Dp = DockTokens.Width,
) {
    MetaballNavDock(
        selectedItem = active.toNavItem(),
        onItemSelected = { item ->
            when (item) {
                NavDockItem.HOME -> onHome()
                NavDockItem.SERVERS -> onServers()
                NavDockItem.PROXY -> onProxy()
                NavDockItem.SUBSCRIPTION -> onSubscription()
                NavDockItem.SETTINGS -> onSettings()
            }
        },
        modifier = modifier,
        height = height,
        width = width,
    )
}

@Composable
fun MetaballNavDock(
    selectedItem: NavDockItem,
    onItemSelected: (NavDockItem) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = DockTokens.Height,
    width: Dp = DockTokens.Width,
) {
    val tokens = LocalSwimVisualTokens.current
    val homeLabel = stringResource(R.string.dock_home)
    val serversLabel = stringResource(R.string.dock_servers)
    val subscriptionLabel = stringResource(R.string.dock_subscription)
    val accountLabel = stringResource(R.string.dock_account)
    // 4 rendered wells (Proxy is reached from Settings, not the dock).
    val items = remember(homeLabel, serversLabel, subscriptionLabel, accountLabel) {
        listOf(
            DockEntry(NavDockItem.HOME, homeLabel, DockGlyphs.Home),
            DockEntry(NavDockItem.SERVERS, serversLabel, DockGlyphs.Servers),
            DockEntry(NavDockItem.SUBSCRIPTION, subscriptionLabel, DockGlyphs.Subscription),
            DockEntry(NavDockItem.SETTINGS, accountLabel, DockGlyphs.Account),
        )
    }
    val centers = DockTokens.Centers
    val selectedIndex = items.indexOfFirst { it.item == selectedItem }.coerceAtLeast(0)
    val initialIndex = items.indexOfFirst { it.item == lastDockItemForCrossScreenMotion }
        .takeIf { it >= 0 } ?: selectedIndex

    // Layer-1 grey membrane: static union of perfect-circle bulges joined by EXACT tangent bridges.
    val membrane = remember(centers) { buildMembranePath(centers, DockTokens.CenterY, DockTokens.BulgeRadius) }

    // Active well slides smoothly between centers on selection.
    val activeCenterX = remember { Animatable(centers[initialIndex]) }
    LaunchedEffect(selectedItem) {
        activeCenterX.animateTo(
            targetValue = centers[selectedIndex],
            animationSpec = tween(durationMillis = SwimDesignTokens.Motion.DockTransitionMs, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        )
        lastDockItemForCrossScreenMotion = selectedItem
    }

    Box(
        modifier = modifier.width(width).height(height),
        contentAlignment = Alignment.TopStart,
    ) {
        val activeX = activeCenterX.value
        val spacingHalf = if (centers.size > 1) (centers[1] - centers[0]) / 2f else DockTokens.BulgeRadius

        Canvas(modifier = Modifier.width(width).height(height)) {
            val sx = size.width / DockTokens.Width.value
            val sy = size.height / DockTokens.Height.value
            withTransform({ scale(sx, sy, pivot = Offset.Zero) }) {
                val cy = DockTokens.CenterY
                val bulgeR = DockTokens.BulgeRadius
                val wellR = DockTokens.WellRadius
                val activeWellR = DockTokens.ActiveWellRadius
                val glowR = DockTokens.GlowRadius

                // --- Layer 1 : grey membrane (waves) ---
                drawPath(
                    path = membrane,
                    brush = Brush.verticalGradient(
                        colors = listOf(GreyTop, GreyMid, GreyBottom),
                        startY = cy - bulgeR,
                        endY = cy + bulgeR,
                    ),
                )
                // subtle top rim on the membrane
                drawPath(path = membrane, color = Color.White.copy(alpha = 0.06f), style = Stroke(width = 1f))

                // --- Layer 2 : deep-black wells (all positions) ---
                centers.forEach { cx ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(WellTop, WellMid, WellBottom),
                            center = Offset(cx, cy - wellR * 0.30f),
                            radius = wellR * 1.25f,
                        ),
                        radius = wellR,
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = wellR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1f),
                    )
                }

                // --- Active well : glow + purple core + highlight (slides) ---
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.color.homePurplePrimary.copy(alpha = 0.55f),
                            tokens.color.homePurplePrimary.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        center = Offset(activeX, cy),
                        radius = glowR,
                    ),
                    radius = glowR,
                    center = Offset(activeX, cy),
                )
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(tokens.material.purpleCoreTop, tokens.material.purpleCoreMid, tokens.material.purpleCoreBottom),
                        startY = cy - activeWellR,
                        endY = cy + activeWellR,
                    ),
                    radius = activeWellR,
                    center = Offset(activeX, cy),
                )
                // specular top-left highlight on the active core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.40f), Color.Transparent),
                        center = Offset(activeX - activeWellR * 0.32f, cy - activeWellR * 0.40f),
                        radius = activeWellR * 0.85f,
                    ),
                    radius = activeWellR * 0.85f,
                    center = Offset(activeX - activeWellR * 0.32f, cy - activeWellR * 0.40f),
                )
                drawCircle(
                    color = tokens.color.homeStrokeActive.copy(alpha = 0.5f),
                    radius = activeWellR,
                    center = Offset(activeX, cy),
                    style = Stroke(width = 1f),
                )

                // --- Layer 3 : white geometric glyphs ---
                val isz = DockTokens.IconSize.value
                items.forEachIndexed { index, entry ->
                    val cx = centers[index]
                    val isActive = abs(cx - activeX) < spacingHalf
                    val tint = if (isActive) Color.White else IconMuted
                    withTransform({
                        translate(cx - isz / 2f, cy - isz / 2f)
                        scale(isz / 24f, isz / 24f, pivot = Offset.Zero)
                    }) {
                        drawPath(entry.glyph.fill, color = tint)
                        entry.glyph.stroke?.let { s ->
                            drawPath(s, color = tint, style = Stroke(width = entry.glyph.strokeWidth))
                        }
                    }
                }
            }
        }

        // Tap targets (one per well).
        items.forEachIndexed { index, entry ->
            val cx = centers[index]
            val r = DockTokens.WellRadius
            Box(
                modifier = Modifier
                    .offset(x = (cx - r).dp, y = (DockTokens.CenterY - r).dp)
                    .size((r * 2f).dp)
                    .semantics {
                        contentDescription = entry.label
                        role = Role.Tab
                    }
                    .pointerInput(entry.item) {
                        detectTapGestures(onTap = { onItemSelected(entry.item) })
                    },
            )
        }
    }
}

/**
 * Metaball connector between two circles — EXACT tangent bridge (Bourke / Vachhar construction):
 * cubic curves leave each circle tangentially, so the necks are smooth with no kink. Returns null
 * when the circles are too far apart or one contains the other.
 */
private fun metaballBridge(
    c1x: Float, c1y: Float, r1: Float,
    c2x: Float, c2y: Float, r2: Float,
    v: Float = 0.5f, handle: Float = 2.6f,
): Path? {
    val d = hypot(c2x - c1x, c2y - c1y)
    if (d >= r1 + r2 || d <= abs(r1 - r2) || d == 0f) return null
    val halfPi = (Math.PI / 2.0).toFloat()
    val pi = Math.PI.toFloat()
    val u1 = acos(((r1 * r1 + d * d - r2 * r2) / (2f * r1 * d)).coerceIn(-1f, 1f))
    val u2 = acos(((r2 * r2 + d * d - r1 * r1) / (2f * r2 * d)).coerceIn(-1f, 1f))
    val a = atan2(c2y - c1y, c2x - c1x)
    val spread = acos(((r1 - r2) / d).coerceIn(-1f, 1f))
    val a1 = a + u1 + (spread - u1) * v
    val a2 = a - u1 - (spread - u1) * v
    val a3 = a + pi - u2 - (pi - u2 - spread) * v
    val a4 = a - pi + u2 + (pi - u2 - spread) * v
    val p1x = c1x + r1 * cos(a1); val p1y = c1y + r1 * sin(a1)
    val p2x = c1x + r1 * cos(a2); val p2y = c1y + r1 * sin(a2)
    val p3x = c2x + r2 * cos(a3); val p3y = c2y + r2 * sin(a3)
    val p4x = c2x + r2 * cos(a4); val p4y = c2y + r2 * sin(a4)
    val tot = r1 + r2
    val k = min(v * handle, hypot(p3x - p1x, p3y - p1y) / tot) * min(1f, d * 2f / tot)
    val h1 = k * r1; val h2 = k * r2
    val c1ax = p1x + h1 * cos(a1 - halfPi); val c1ay = p1y + h1 * sin(a1 - halfPi)
    val c3ax = p3x + h2 * cos(a3 + halfPi); val c3ay = p3y + h2 * sin(a3 + halfPi)
    val c4ax = p4x + h2 * cos(a4 - halfPi); val c4ay = p4y + h2 * sin(a4 - halfPi)
    val c2ax = p2x + h1 * cos(a2 + halfPi); val c2ay = p2y + h1 * sin(a2 + halfPi)
    return Path().apply {
        moveTo(p1x, p1y)
        cubicTo(c1ax, c1ay, c3ax, c3ay, p3x, p3y)
        lineTo(p4x, p4y)
        cubicTo(c4ax, c4ay, c2ax, c2ay, p2x, p2y)
        close()
    }
}

private fun buildMembranePath(centers: List<Float>, cy: Float, bulgeR: Float): Path {
    val bulges = centers.map { cx ->
        Path().apply { addOval(Rect(cx - bulgeR, cy - bulgeR, cx + bulgeR, cy + bulgeR)) }
    }
    var current = bulges.first()
    for (i in 1 until bulges.size) {
        val next = Path()
        next.op(current, bulges[i], PathOperation.Union)
        current = next
    }
    for (i in 0 until centers.size - 1) {
        metaballBridge(centers[i], cy, bulgeR, centers[i + 1], cy, bulgeR)?.let { bridge ->
            val next = Path()
            next.op(current, bridge, PathOperation.Union)
            current = next
        }
    }
    return current
}

private fun SwimDockDestination.toNavItem(): NavDockItem =
    when (this) {
        SwimDockDestination.Home -> NavDockItem.HOME
        SwimDockDestination.Servers -> NavDockItem.SERVERS
        SwimDockDestination.Proxy -> NavDockItem.PROXY
        SwimDockDestination.Subscription -> NavDockItem.SUBSCRIPTION
        SwimDockDestination.Settings -> NavDockItem.SETTINGS
    }

// --- Palette (dock-local; dark-first) ---
private val GreyTop = Color(0xFF4C4C54)
private val GreyMid = Color(0xFF3A3A42)
private val GreyBottom = Color(0xFF26262C)
private val WellTop = Color(0xFF15151A)
private val WellMid = Color(0xFF08080B)
private val WellBottom = Color(0xFF030305)
private val IconMuted = Color(0xFF8E8A98)

private class DockGlyph(
    val fill: Path,
    val stroke: Path? = null,
    val strokeWidth: Float = 1.8f,
)

private data class DockEntry(
    val item: NavDockItem,
    val label: String,
    val glyph: DockGlyph,
)

private fun parseGlyph(pathData: String, evenOdd: Boolean = false): Path =
    PathParser().parsePathString(pathData).toPath().apply {
        if (evenOdd) fillType = PathFillType.EvenOdd
    }

/** Geometric white glyphs (24×24 grid), drawn faithfully to the reference mock. */
private object DockGlyphs {
    val Home = DockGlyph(
        parseGlyph("M12 3.5 3.8 10.6a1 1 0 0 0-.35.76V20a1 1 0 0 0 1 1h4.05v-5.4a1.1 1.1 0 0 1 1.1-1.1h3a1.1 1.1 0 0 1 1.1 1.1V21h4.05a1 1 0 0 0 1-1v-8.64a1 1 0 0 0-.35-.76z"),
    )
    val Servers = DockGlyph(
        parseGlyph(
            "M5 4.5h14A2.5 2.5 0 0 1 21.5 7v1.5A2.5 2.5 0 0 1 19 11H5A2.5 2.5 0 0 1 2.5 8.5V7A2.5 2.5 0 0 1 5 4.5zM7.6 6.85a.95 .95 0 1 0 0 1.9 .95 .95 0 0 0 0-1.9zM5 13h14a2.5 2.5 0 0 1 2.5 2.5V17A2.5 2.5 0 0 1 19 19.5H5A2.5 2.5 0 0 1 2.5 17v-1.5A2.5 2.5 0 0 1 5 13zm2.6 2.35a.95 .95 0 1 0 0 1.9 .95 .95 0 0 0 0-1.9z",
            evenOdd = true,
        ),
    )
    val Subscription = DockGlyph(
        parseGlyph(
            "M4.5 5.5h15A2.5 2.5 0 0 1 22 8v8a2.5 2.5 0 0 1-2.5 2.5h-15A2.5 2.5 0 0 1 2 16V8a2.5 2.5 0 0 1 2.5-2.5zM3 9.2h18v2.3H3z",
            evenOdd = true,
        ),
    )
    val Account = DockGlyph(
        parseGlyph("M12 11.8a3.9 3.9 0 1 0 0-7.8 3.9 3.9 0 0 0 0 7.8zM12 13.4c-3.7 0-6.7 2.4-6.7 5.4 0 .9 .7 1.6 1.6 1.6h10.2c.9 0 1.6-.7 1.6-1.6 0-3-3-5.4-6.7-5.4z"),
    )
}

private object DockTokens {
    val Width = SwimDesignTokens.Dock.Width
    val Height = SwimDesignTokens.Dock.Height
    const val CenterY = SwimDesignTokens.Dock.CenterY
    const val BulgeRadius = SwimDesignTokens.Dock.BulgeRadius
    const val WellRadius = SwimDesignTokens.Dock.WellRadius
    const val ActiveWellRadius = SwimDesignTokens.Dock.ActiveWellRadius
    const val GlowRadius = SwimDesignTokens.Dock.GlowRadius
    val IconSize = SwimDesignTokens.Dock.IconSize
    val Centers = SwimDesignTokens.Dock.Centers
}
