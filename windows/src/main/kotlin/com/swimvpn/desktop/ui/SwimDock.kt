package com.swimvpn.desktop.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.swimvpn.desktop.state.NavTab
import com.swimvpn.desktop.theme.SwimDesignTokens

private data class DockItem(val tab: NavTab, val icon: ImageVector, val label: String)

private val items = listOf(
    DockItem(NavTab.HOME, Icons.Filled.Home, "Accueil"),
    DockItem(NavTab.SERVERS, Icons.Filled.Dns, "Serveurs"),
    DockItem(NavTab.SUBSCRIPTION, Icons.Filled.CreditCard, "Abonnement"),
    DockItem(NavTab.ACCOUNT, Icons.Filled.Person, "Compte"),
)

/** The SWIMVPN navigation dock — grey membrane + 4 recessed wells + a sliding purple active well,
 *  matching the Android metaball dock grammar (same tokens, same 4 destinations). */
@Composable
fun SwimDock(active: NavTab, onSelect: (NavTab) -> Unit) {
    val tokens = SwimDesignTokens.Current
    val d = SwimDesignTokens.Dock
    val activeIndex = items.indexOfFirst { it.tab == active }.coerceAtLeast(0)
    val targetX by animateFloatAsState(
        targetValue = d.Centers[activeIndex],
        animationSpec = tween(SwimDesignTokens.Motion.DockTransitionMs, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "dockActive",
    )

    Box(modifier = Modifier.size(d.Width, d.Height)) {
        Canvas(modifier = Modifier.size(d.Width, d.Height)) {
            val s = size.width / 320f // design space → px
            // Layer 1 — grey membrane (rounded bar)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to tokens.material.shellTop,
                    1f to tokens.material.shellBottom,
                ),
                topLeft = Offset(8 * s, (d.CenterY - 34) * s),
                size = androidx.compose.ui.geometry.Size(size.width - 16 * s, 68 * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(34 * s, 34 * s),
            )
            // Layer 2 — recessed wells
            d.Centers.forEach { cx ->
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to tokens.material.bowlTop,
                        1f to tokens.material.bowlBottom,
                        center = Offset(cx * s, (d.CenterY - 6) * s), radius = d.WellRadius * s,
                    ),
                    radius = d.WellRadius * s, center = Offset(cx * s, d.CenterY * s),
                )
            }
            // Layer 3 — active well (glow + purple core + rim)
            val ac = Offset(targetX * s, d.CenterY * s)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to tokens.material.purpleCoreMid.copy(alpha = 0.40f),
                    1f to Color.Transparent, center = ac, radius = d.GlowRadius * s,
                ),
                radius = d.GlowRadius * s, center = ac,
            )
            drawCircle(
                brush = Brush.verticalGradient(
                    0f to tokens.material.purpleCoreTop,
                    1f to tokens.material.purpleCoreBottom,
                    startY = (d.CenterY - d.ActiveWellRadius) * s, endY = (d.CenterY + d.ActiveWellRadius) * s,
                ),
                radius = d.ActiveWellRadius * s, center = ac,
            )
            drawCircle(
                color = tokens.color.homeStrokeActive, radius = d.ActiveWellRadius * s, center = ac,
                style = Stroke(width = 1.5f * s),
            )
        }
        // Glyphs (clickable), positioned at the well centers.
        items.forEachIndexed { i, item ->
            val cx = SwimDesignTokens.Dock.Centers[i].dp
            val isActive = item.tab == active
            Box(
                modifier = Modifier
                    .offset(x = cx - 23.dp, y = SwimDesignTokens.Dock.CenterY.dp - 23.dp)
                    .size(46.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(item.tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (isActive) tokens.color.homeTextPrimary else tokens.color.homeTextMuted,
                    modifier = Modifier.size(if (isActive) 26.dp else 24.dp),
                )
            }
        }
    }
}
