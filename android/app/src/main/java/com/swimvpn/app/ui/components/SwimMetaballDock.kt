package com.swimvpn.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.swimvpn.app.R
import com.swimvpn.app.ui.theme.SwimDesignTokens
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

enum class SwimDockDestination {
    Home,
    Servers,
    Subscription,
    Settings,
}

enum class NavDockItem {
    HOME,
    SERVERS,
    SUBSCRIPTION,
    SETTINGS,
}

private var lastDockItemForCrossScreenMotion: NavDockItem = NavDockItem.HOME

private enum class DockNodeVisualState {
    IdleInactive,
    PressedInactive,
    ActiveRest,
    ActivePressed,
    TransitioningFrom,
    TransitioningTo,
}

@Composable
fun SwimMetaballDock(
    active: SwimDockDestination,
    onHome: () -> Unit,
    onServers: () -> Unit,
    onSubscription: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = DockTokens.Height,
    width: Dp = DockTokens.Width,
    showActiveLabel: Boolean = true,
) {
    MetaballNavDock(
        selectedItem = active.toNavItem(),
        onItemSelected = { item ->
            when (item) {
                NavDockItem.HOME -> onHome()
                NavDockItem.SERVERS -> onServers()
                NavDockItem.SUBSCRIPTION -> onSubscription()
                NavDockItem.SETTINGS -> onSettings()
            }
        },
        modifier = modifier,
        height = height,
        width = width,
        showActiveLabel = showActiveLabel,
    )
}

@Composable
fun MetaballNavDock(
    selectedItem: NavDockItem,
    onItemSelected: (NavDockItem) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = DockTokens.Height,
    width: Dp = DockTokens.Width,
    showActiveLabel: Boolean = true,
) {
    val homeLabel = stringResource(R.string.dock_home)
    val serversLabel = stringResource(R.string.dock_servers)
    val subscriptionLabel = stringResource(R.string.dock_subscription)
    val accountLabel = stringResource(R.string.dock_account)
    val items = remember(homeLabel, serversLabel, subscriptionLabel, accountLabel) {
        listOf(
            DockItem(NavDockItem.HOME, homeLabel, Icons.Default.Home),
            DockItem(NavDockItem.SERVERS, serversLabel, Icons.Default.Storage),
            DockItem(NavDockItem.SUBSCRIPTION, subscriptionLabel, Icons.Default.CreditCard),
            DockItem(NavDockItem.SETTINGS, accountLabel, Icons.Default.Settings),
        )
    }
    val selectedIndex = items.indexOfFirst { it.item == selectedItem }.coerceAtLeast(0)
    val initialIndex = items.indexOfFirst { it.item == lastDockItemForCrossScreenMotion }.takeIf { it >= 0 } ?: selectedIndex
    val activeCenter = remember { Animatable(DockTokens.Centers[initialIndex]) }
    val transitionProgress = remember { Animatable(1f) }
    var previousCenter by remember { mutableStateOf(DockTokens.Centers[initialIndex]) }
    var pressedItem by remember { mutableStateOf<NavDockItem?>(null) }

    LaunchedEffect(selectedItem) {
        previousCenter = activeCenter.value
        transitionProgress.snapTo(0f)
        val transitionSpec = tween<Float>(
            durationMillis = SwimDesignTokens.Motion.DockTransitionMs,
            easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
        )
        val activeJob = launch {
            activeCenter.animateTo(
                targetValue = DockTokens.Centers[selectedIndex],
                animationSpec = transitionSpec,
            )
        }
        transitionProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SwimDesignTokens.Motion.DockTransitionMs,
                easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
            ),
        )
        activeJob.join()
        lastDockItemForCrossScreenMotion = selectedItem
    }

    val breathing = rememberInfiniteTransition(label = "dock-active-breathing")
    val activeBreathScale by breathing.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SwimDesignTokens.Motion.DockBreathingMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dock-active-scale",
    )
    val activeGlowAlpha by breathing.animateFloat(
        initialValue = SwimDesignTokens.Motion.DockGlowIdleAlpha,
        targetValue = SwimDesignTokens.Motion.DockGlowPeakAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SwimDesignTokens.Motion.DockBreathingMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dock-active-glow",
    )
    Box(
        modifier = modifier
            .width(width)
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        DockBodyCanvas(
            activeCenterDp = activeCenter.value,
            previousCenterDp = previousCenter,
            transitionProgress = transitionProgress.value,
            activeGlowAlpha = activeGlowAlpha,
            modifier = Modifier.fillMaxSize(),
        )
        items.forEachIndexed { index, item ->
            val selected = item.item == selectedItem
            val pressed = pressedItem == item.item
            val state = when {
                selected && pressed -> DockNodeVisualState.ActivePressed
                selected -> DockNodeVisualState.ActiveRest
                pressed -> DockNodeVisualState.PressedInactive
                abs(activeCenter.value - DockTokens.Centers[index]) < 2f -> DockNodeVisualState.TransitioningTo
                else -> DockNodeVisualState.IdleInactive
            }
            val outerSize = if (selected) DockTokens.ActiveOuterDiameter else DockTokens.InactiveOuterDiameter
            val pressScale = if (pressed) SwimDesignTokens.Motion.PressScale else if (selected) activeBreathScale else 1f
            DockNodeButton(
                item = item,
                state = state,
                selected = selected,
                showActiveLabel = showActiveLabel,
                renderContent = false,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = DockTokens.Centers[index].dp - outerSize / 2f,
                        y = DockTokens.CenterY.dp - outerSize / 2f,
                    )
                    .scale(pressScale)
                    .pointerInput(item.item) {
                        detectTapGestures(
                            onPress = {
                                pressedItem = item.item
                                val released = tryAwaitRelease()
                                pressedItem = null
                                if (released) {
                                    onItemSelected(item.item)
                                }
                            },
                        )
                    },
            )
        }
        ActiveCoreLayer(
            activeCenterDp = activeCenter.value,
            transitionProgress = transitionProgress.value,
            glowAlpha = activeGlowAlpha,
            breathScale = activeBreathScale,
            modifier = Modifier.fillMaxSize(),
        )
        items.forEachIndexed { index, item ->
            val selected = item.item == selectedItem
            val outerSize = if (selected) DockTokens.ActiveOuterDiameter else DockTokens.InactiveOuterDiameter
            val iconSize = if (selected) DockTokens.ActiveIconSize else DockTokens.InactiveIconSize
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = DockTokens.Centers[index].dp - outerSize / 2f,
                        y = DockTokens.CenterY.dp - outerSize / 2f,
                    )
                    .size(outerSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(iconSize),
                )
                if (selected && showActiveLabel) {
                    Text(
                        text = item.label,
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = (-12).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DockBodyCanvas(
    activeCenterDp: Float,
    previousCenterDp: Float,
    transitionProgress: Float,
    activeGlowAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val sx = size.width / DockTokens.Width.value
        val sy = size.height / DockTokens.Height.value
        val cy = DockTokens.CenterY * sy
        val centers = DockTokens.Centers.map { it * sx }
        val activeCenter = activeCenterDp * sx
        val previousCenter = previousCenterDp * sx
        val settling = (1f - transitionProgress).coerceIn(0f, 1f)
        val radii = centers.map { center ->
            val distance = abs(center - activeCenter) / (DockTokens.CenterSpacing * sx)
            val previousDistance = abs(center - previousCenter) / (DockTokens.CenterSpacing * sx)
            val influence = max(
                (1f - distance).coerceIn(0f, 1f),
                (1f - previousDistance).coerceIn(0f, 1f) * settling * 0.72f,
            )
            (DockTokens.InactiveOuterRadius + (DockTokens.ActiveOuterRadius - DockTokens.InactiveOuterRadius) * influence) * sx
        }
        val body = buildMetaballPath(centers, radii, cy)

        drawPath(
            path = body,
            color = Color.Black.copy(alpha = 0.55f),
            style = Stroke(width = 18.dp.toPx()),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.42f * activeGlowAlpha),
                    SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.10f * activeGlowAlpha),
                    Color.Transparent,
                ),
                center = Offset(activeCenter, cy),
                radius = DockTokens.ActiveGlowRadius * sx,
            ),
            radius = DockTokens.ActiveGlowRadius * sx,
            center = Offset(activeCenter, cy),
        )
        drawPath(
            path = body,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.07f),
                    SwimDesignTokens.Material.ShellMid,
                    SwimDesignTokens.Material.ShellBottom,
                ),
                startY = 0f,
                endY = size.height,
            ),
        )
        drawPath(
            path = body,
            brush = Brush.verticalGradient(
                colors = listOf(
                    SwimDesignTokens.Highlight.InnerTop,
                    Color.Transparent,
                    Color.Black.copy(alpha = SwimDesignTokens.Shadow.InnerBottomAlpha),
                ),
                startY = 0f,
                endY = size.height,
            ),
        )
        drawPath(
            path = body,
            color = SwimDesignTokens.Highlight.BodyStroke,
            style = Stroke(width = 0.8.dp.toPx()),
        )
    }
}

@Composable
private fun ActiveCoreLayer(
    activeCenterDp: Float,
    transitionProgress: Float,
    glowAlpha: Float,
    breathScale: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val sx = size.width / DockTokens.Width.value
        val sy = size.height / DockTokens.Height.value
        val cy = DockTokens.CenterY * sy
        val activeCenter = Offset(activeCenterDp * sx, cy)
        val progress = transitionProgress.coerceIn(0f, 1f)
        val radius = DockTokens.ActiveCoreDiameter.toPx() / 2f * breathScale

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.22f * glowAlpha),
                    SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.08f * glowAlpha),
                    Color.Transparent,
                ),
                center = activeCenter,
                radius = radius * 1.62f,
            ),
            radius = radius * 1.62f,
            center = activeCenter,
        )
        val fillRadius = radius * (0.72f + progress * 0.28f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SwimDesignTokens.Material.PurpleCoreTop,
                    SwimDesignTokens.Material.PurpleCoreMid,
                    SwimDesignTokens.Material.PurpleCoreBottom,
                ),
                center = Offset(activeCenter.x - radius * 0.22f, activeCenter.y - radius * 0.28f),
                radius = radius * 1.18f,
            ),
            radius = fillRadius,
            center = activeCenter,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = fillRadius,
            center = activeCenter,
            style = Stroke(width = 0.8.dp.toPx()),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SwimDesignTokens.Highlight.SkinSheen,
                    Color.Transparent,
                ),
                center = Offset(activeCenter.x - radius * 0.28f, activeCenter.y - radius * 0.34f),
                radius = radius * 0.74f,
            ),
            radius = radius * 0.74f,
            center = Offset(activeCenter.x - radius * 0.10f, activeCenter.y - radius * 0.16f),
        )
    }
}

@Composable
private fun DockNodeButton(
    item: DockItem,
    state: DockNodeVisualState,
    selected: Boolean,
    showActiveLabel: Boolean,
    renderContent: Boolean,
    modifier: Modifier = Modifier,
) {
    val outerSize = if (selected) DockTokens.ActiveOuterDiameter else DockTokens.InactiveOuterDiameter
    val bowlSize = if (selected) DockTokens.ActiveBowlDiameter else DockTokens.InactiveBowlDiameter
    val iconSize = if (selected) DockTokens.ActiveIconSize else DockTokens.InactiveIconSize

    Box(modifier = modifier.size(outerSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f
            val bowlRadius = bowlSize.toPx() / 2f
            val pressedDepth = when (state) {
                DockNodeVisualState.ActivePressed,
                DockNodeVisualState.PressedInactive -> 0.12f
                else -> 0f
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.035f - pressedDepth * 0.01f),
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    ),
                    center = Offset(center.x - outerRadius * 0.22f, center.y - outerRadius * 0.30f),
                    radius = outerRadius * 1.15f,
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
                        SwimDesignTokens.Material.BowlTop,
                        SwimDesignTokens.Material.BowlMid,
                        SwimDesignTokens.Material.BowlBottom,
                    ),
                    center = Offset(center.x, center.y + bowlRadius * 0.24f),
                    radius = bowlRadius * 1.08f,
                ),
                radius = bowlRadius,
                center = center,
            )
            drawCircle(
                color = SwimDesignTokens.Material.BowlInnerShadow,
                radius = bowlRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )
            drawCircle(
                color = SwimDesignTokens.Highlight.BowlRim,
                radius = bowlRadius,
                center = center,
                style = Stroke(width = 0.8.dp.toPx()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = Offset(center.x - bowlRadius * 0.30f, center.y - bowlRadius * 0.44f),
                    radius = bowlRadius * 0.76f,
                ),
                radius = bowlRadius * 0.76f,
                center = Offset(center.x - bowlRadius * 0.10f, center.y - bowlRadius * 0.20f),
            )
        }
        if (renderContent) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
                modifier = Modifier.size(iconSize),
            )
            if (selected && showActiveLabel) {
                Text(
                    text = item.label,
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-12).dp),
                )
            }
        }
    }
}

private fun buildMetaballPath(
    centers: List<Float>,
    radii: List<Float>,
    cy: Float,
): Path {
    val firstX = centers.first()
    val lastX = centers.last()
    val firstR = radii.first()
    val lastR = radii.last()

    return Path().apply {
        moveTo(firstX - firstR, cy)
        cubicTo(firstX - firstR, cy - firstR * 0.72f, firstX - firstR * 0.56f, cy - firstR, firstX, cy - firstR)
        for (i in 0 until centers.lastIndex) {
            val cx1 = centers[i]
            val cx2 = centers[i + 1]
            val r1 = radii[i]
            val r2 = radii[i + 1]
            val d = cx2 - cx1
            val wx = (cx1 + cx2) / 2f
            val valley = minOf(r1, r2) - DockTokens.ValleyDepth
            cubicTo(cx1 + r1 * 0.54f, cy - r1, wx - d * 0.16f, cy - valley, wx, cy - valley)
            cubicTo(wx + d * 0.16f, cy - valley, cx2 - r2 * 0.54f, cy - r2, cx2, cy - r2)
        }
        cubicTo(lastX + lastR * 0.56f, cy - lastR, lastX + lastR, cy - lastR * 0.72f, lastX + lastR, cy)
        cubicTo(lastX + lastR, cy + lastR * 0.72f, lastX + lastR * 0.56f, cy + lastR, lastX, cy + lastR)
        for (i in centers.lastIndex downTo 1) {
            val cx1 = centers[i]
            val cx2 = centers[i - 1]
            val r1 = radii[i]
            val r2 = radii[i - 1]
            val d = cx1 - cx2
            val wx = (cx1 + cx2) / 2f
            val valley = minOf(r1, r2) - DockTokens.ValleyDepth
            cubicTo(cx1 - r1 * 0.54f, cy + r1, wx + d * 0.16f, cy + valley, wx, cy + valley)
            cubicTo(wx - d * 0.16f, cy + valley, cx2 + r2 * 0.54f, cy + r2, cx2, cy + r2)
        }
        cubicTo(firstX - firstR * 0.56f, cy + firstR, firstX - firstR, cy + firstR * 0.72f, firstX - firstR, cy)
        close()
    }
}

private fun SwimDockDestination.toNavItem(): NavDockItem =
    when (this) {
        SwimDockDestination.Home -> NavDockItem.HOME
        SwimDockDestination.Servers -> NavDockItem.SERVERS
        SwimDockDestination.Subscription -> NavDockItem.SUBSCRIPTION
        SwimDockDestination.Settings -> NavDockItem.SETTINGS
    }

private object DockTokens {
    val Width = SwimDesignTokens.Dock.Width
    val Height = SwimDesignTokens.Dock.Height
    const val CenterY = SwimDesignTokens.Dock.CenterY
    const val CenterSpacing = SwimDesignTokens.Dock.CenterSpacing
    val InactiveOuterDiameter = SwimDesignTokens.Dock.InactiveOuterDiameter
    val ActiveOuterDiameter = SwimDesignTokens.Dock.ActiveOuterDiameter
    val InactiveBowlDiameter = SwimDesignTokens.Dock.InactiveBowlDiameter
    val ActiveBowlDiameter = SwimDesignTokens.Dock.ActiveBowlDiameter
    val ActiveCoreDiameter = SwimDesignTokens.Dock.ActiveCoreDiameter
    val InactiveIconSize = SwimDesignTokens.Dock.InactiveIconSize
    val ActiveIconSize = SwimDesignTokens.Dock.ActiveIconSize
    const val InactiveOuterRadius = SwimDesignTokens.Dock.InactiveOuterRadius
    const val ActiveOuterRadius = SwimDesignTokens.Dock.ActiveOuterRadius
    const val ActiveGlowRadius = SwimDesignTokens.Dock.ActiveGlowRadius
    const val ValleyDepth = SwimDesignTokens.Dock.ValleyDepth
    val Centers = SwimDesignTokens.Dock.Centers
}

private data class DockItem(
    val item: NavDockItem,
    val label: String,
    val icon: ImageVector,
)
