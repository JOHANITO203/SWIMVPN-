package com.swimvpn.desktop.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Strong ease-out — built-in curves are too weak to feel intentional (Emil). Snappy, premium. */
val SwimEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Hover + press feedback for any pressable element: a barely-there scale-up on mouse hover (desktop)
 * and a scale-down on press, both eased with [SwimEaseOut]. Pass the SAME [interactionSource] you give
 * to `clickable` so the states stay in sync. This is the single biggest "feels alive" upgrade.
 */
@Composable
fun Modifier.interactive(
    interactionSource: MutableInteractionSource,
    pressScale: Float = 0.97f,
    hoverScale: Float = 1.02f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val target = if (pressed) pressScale else if (hovered) hoverScale else 1f
    val scale by animateFloatAsState(target, tween(140, easing = SwimEaseOut), label = "interactiveScale")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
