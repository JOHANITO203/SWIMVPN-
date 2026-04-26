package com.swimvpn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun MeshBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF04111F) else Color(0xFFF4F8FB)
    val blobColor1 = Color(0xFF4A9ED7).copy(alpha = if (isDark) 0.15f else 0.25f)
    val blobColor2 = Color(0xFF0A3151).copy(alpha = if (isDark) 0.35f else 0.1f)
    val blobColor3 = Color(0xFF5EEAD4).copy(alpha = if (isDark) 0.15f else 0.2f)

    Box(modifier = modifier.fillMaxSize().background(bgColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(blobColor1, Color.Transparent)),
                radius = size.width * 0.8f,
                center = Offset(0f, size.height * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(blobColor2, Color.Transparent)),
                radius = size.width * 0.9f,
                center = Offset(size.width, size.height * 0.8f)
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(blobColor3, Color.Transparent)),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.5f, size.height * 0.5f)
            )
        }
        content()
    }
}

@Composable
fun Modifier.glassEffect(shape: Shape): Modifier {
    val isDark = isSystemInDarkTheme()
    val glassBg = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.4f)
    val borderGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.2f else 0.6f),
            Color.White.copy(alpha = 0f)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    return this
        .clip(shape)
        .background(glassBg)
        .border(0.5.dp, borderGradient, shape)
}
