package com.swimvpn.desktop.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.swimvpn.desktop.theme.SwimDesignTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Visible purple scrollbar — an obvious, grab-able target so a mouse WITHOUT a scroll wheel can
 * still move through long lists (drag the thumb or click the track). Always faintly visible, brightens
 * on hover.
 */
@Composable
fun swimScrollbarStyle(): ScrollbarStyle {
    val tokens = SwimDesignTokens.Current
    return ScrollbarStyle(
        minimalHeight = 32.dp,
        thickness = 10.dp,
        shape = RoundedCornerShape(5.dp),
        hoverDurationMillis = 250,
        unhoverColor = tokens.color.homePurplePrimary.copy(alpha = 0.40f),
        hoverColor = tokens.color.homePurplePrimary,
    )
}

/**
 * Keyboard scrolling for a focused scroll area — arrows (line), PageUp/PageDown (page), Home/End
 * (top/bottom). Another no-wheel "déplacement" option. Returns true when it consumed the key.
 */
fun handleScrollKeys(e: KeyEvent, scroll: ScrollState, scope: CoroutineScope): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    when (e.key) {
        Key.DirectionDown -> scope.launch { scroll.scrollBy(90f) }
        Key.DirectionUp -> scope.launch { scroll.scrollBy(-90f) }
        Key.PageDown -> scope.launch { scroll.scrollBy(520f) }
        Key.PageUp -> scope.launch { scroll.scrollBy(-520f) }
        Key.MoveHome -> scope.launch { scroll.animateScrollTo(0) }
        Key.MoveEnd -> scope.launch { scroll.animateScrollTo(scroll.maxValue) }
        else -> return false
    }
    return true
}
