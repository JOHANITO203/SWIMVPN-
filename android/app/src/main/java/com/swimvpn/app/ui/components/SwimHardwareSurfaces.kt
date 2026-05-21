package com.swimvpn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.ui.theme.SwimDesignTokens
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun Modifier.swimDarkLuxuryBackground(): Modifier = drawWithCache {
    val baseWash = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.94f),
            SwimDesignTokens.Color.HomeBackgroundBase.copy(alpha = 0.82f),
            SwimDesignTokens.Color.HomeBackgroundDeep.copy(alpha = 0.98f),
        ),
        startY = 0f,
        endY = size.height,
    )
    val orbHalo = Brush.radialGradient(
        colors = listOf(
            SwimDesignTokens.Color.HomePurpleActive.copy(alpha = 0.18f),
            SwimDesignTokens.Color.HomePurplePrimary.copy(alpha = 0.11f),
            SwimDesignTokens.Color.HomePurpleDeep.copy(alpha = 0.045f),
            Color.Transparent,
        ),
        center = Offset(size.width * 0.52f, size.height * 0.28f),
        radius = size.width * 0.74f,
    )
    val sideGlow = Brush.radialGradient(
        colors = listOf(
            SwimDesignTokens.Color.HomePurplePrimary.copy(alpha = 0.28f),
            SwimDesignTokens.Color.HomePurpleDeep.copy(alpha = 0.13f),
            Color.Transparent,
        ),
        center = Offset(size.width * 0.98f, size.height * 0.34f),
        radius = size.width * 0.82f,
    )
    val leftSheen = Brush.radialGradient(
        colors = listOf(
            SwimDesignTokens.Color.HomeSurfaceHighlight.copy(alpha = 0.050f),
            Color.Transparent,
        ),
        center = Offset(size.width * 0.18f, size.height * 0.42f),
        radius = size.width * 0.58f,
    )
    val lowerVignette = Brush.radialGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.70f),
        ),
        center = Offset(size.width * 0.16f, size.height * 0.92f),
        radius = size.width * 1.04f,
    )
    val verticalVignette = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.30f),
            Color.Transparent,
            Color.Black.copy(alpha = 0.38f),
        ),
        startY = 0f,
        endY = size.height,
    )

    onDrawBehind {
        drawRect(SwimDesignTokens.Color.HomeBackgroundDeep)
        drawRect(brush = baseWash)
        drawRect(brush = orbHalo)
        drawRect(brush = sideGlow)
        drawRect(brush = leftSheen)
        drawRect(brush = lowerVignette)
        drawRect(brush = verticalVignette)
    }
}

@Composable
fun SwimDarkLuxuryBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .swimDarkLuxuryBackground(),
        content = content
    )
}

@Composable
fun SwimCircularIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = SwimDesignTokens.Home.ProfileButtonSize,
    iconSize: Dp = SwimDesignTokens.Home.ProfileIconSize,
    enabled: Boolean = true,
    iconTint: Color = SwimDesignTokens.Color.HomeTextPrimary
) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val outerRadius = this.size.minDimension / 2f
                val bowlRadius = outerRadius * SwimDesignTokens.UserButton.BowlRatio
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SwimDesignTokens.Color.HomePurplePrimary.copy(alpha = SwimDesignTokens.UserButton.GlowAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = this.size.minDimension * SwimDesignTokens.UserButton.GlowRadius
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SwimDesignTokens.Material.ShellTop,
                            SwimDesignTokens.Material.ShellMid,
                            SwimDesignTokens.Material.ShellBottom
                        ),
                        center = Offset(center.x - outerRadius * 0.18f, center.y - outerRadius * 0.30f),
                        radius = outerRadius * 1.18f
                    ),
                    radius = outerRadius,
                    center = center
                )
                drawCircle(
                    color = SwimDesignTokens.Material.OuterDarkVeil,
                    radius = outerRadius * 0.86f,
                    center = Offset(center.x, center.y + outerRadius * 0.10f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SwimDesignTokens.Material.BowlTop,
                            SwimDesignTokens.Material.BowlMid,
                            SwimDesignTokens.Material.BowlBottom
                        ),
                        center = Offset(center.x, center.y + bowlRadius * 0.22f),
                        radius = bowlRadius * 1.08f
                    ),
                    radius = bowlRadius,
                    center = center
                )
                drawCircle(
                    color = SwimDesignTokens.Material.BowlInnerShadow,
                    radius = bowlRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = SwimDesignTokens.Highlight.BowlRim,
                    radius = bowlRadius,
                    center = center,
                    style = Stroke(width = 0.8.dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SwimDesignTokens.Highlight.SkinSheen,
                            Color.Transparent
                        ),
                        center = Offset(center.x - bowlRadius * 0.28f, center.y - bowlRadius * 0.38f),
                        radius = bowlRadius * 0.72f
                    ),
                    radius = bowlRadius * 0.72f,
                    center = Offset(center.x - bowlRadius * 0.10f, center.y - bowlRadius * 0.16f)
                )
            }
            .shadow(SwimDesignTokens.Shadow.UserButton, CircleShape, clip = false)
            .clip(CircleShape)
            .border(1.dp, SwimDesignTokens.Color.HomeStrokeSubtle, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) iconTint.copy(alpha = SwimDesignTokens.UserButton.IconAlpha) else SwimDesignTokens.Color.HomeTextMuted,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun SwimPillSurface(
    modifier: Modifier = Modifier,
    minHeight: Dp = SwimDesignTokens.Home.ServerPillHeight,
    shape: Shape = SwimDesignTokens.Shape.Pill,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(minHeight)
            .shadow(SwimDesignTokens.Elevation.HardwareSurface, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SwimDesignTokens.Color.HomeSurfaceHighlight.copy(alpha = 0.82f),
                        Color(0xFF0E0E12).copy(alpha = 0.96f)
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.HomeStrokeSubtle, shape)
            .then(clickableModifier)
            .drawBehind {
                drawRect(
                    color = SwimDesignTokens.Color.HomeTopHighlight,
                    size = Size(size.width, 1.5.dp.toPx())
                )
            },
        content = content
    )
}

@Composable
fun SwimHardwareCard(
    modifier: Modifier = Modifier,
    height: Dp = SwimDesignTokens.Home.StatsCardHeight,
    shape: Shape = SwimDesignTokens.Shape.HardwareCard,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .height(height)
            .shadow(SwimDesignTokens.Elevation.HardwareSurface, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SwimDesignTokens.Color.HomeSurfaceElevated,
                        SwimDesignTokens.Color.HomeSurfaceBase,
                        SwimDesignTokens.Color.HomeBackgroundDeep
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.HomeDividerSubtle, shape)
            .drawBehind {
                drawRect(
                    color = Color.White.copy(alpha = 0.07f),
                    size = Size(size.width, 1.dp.toPx())
                )
            },
        content = content
    )
}

@Composable
fun SwimPowerButtonSurface(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    buttonSize: Dp = SwimDesignTokens.Home.PowerButtonMin
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SwimDesignTokens.Color.HomePurplePrimary.copy(alpha = 0.52f),
                            Color.Transparent
                        ),
                        radius = this.size.minDimension * 0.78f
                    )
                )
            }
            .shadow(22.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SwimDesignTokens.Color.HomeSurfaceHighlight,
                        SwimDesignTokens.Color.HomeSurfaceElevated,
                        SwimDesignTokens.Color.HomeBackgroundDeep
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.HomePurpleActive.copy(alpha = 0.25f), CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.74f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF17151F),
                            Color(0xFF09090D),
                            Color.Black
                        )
                    )
                )
                .drawBehind {
                    drawArc(
                        color = Color.White.copy(alpha = 0.12f),
                        startAngle = 210f,
                        sweepAngle = 120f,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        size = Size(size.width * 0.78f, size.height * 0.78f),
                        topLeft = Offset(size.width * 0.11f, size.height * 0.10f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) SwimDesignTokens.Color.HomePurpleActive else SwimDesignTokens.Color.HomeTextMuted,
                modifier = Modifier.size(SwimDesignTokens.Home.PowerIconSize)
            )
        }
    }
}

@Composable
fun SwimOrbMesh(
    modifier: Modifier = Modifier,
    phase: Float = 0f
) {
    Canvas(modifier = modifier) {
        val radius = min(size.width, size.height) * 0.44f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SwimDesignTokens.Color.HomePurpleActive.copy(alpha = 0.28f),
                    SwimDesignTokens.Color.HomePurplePrimary.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 1.45f
            )
        )
        repeat(4) { index ->
            val inset = index * 13.dp.toPx()
            drawCircle(
                color = SwimDesignTokens.Color.HomePurpleActive.copy(alpha = 0.18f + index * 0.04f),
                radius = radius - inset,
                center = center,
                style = Stroke(width = (1.2f + index * 0.2f).dp.toPx())
            )
        }
        repeat(24) { index ->
            val angle = ((index * 15f) + phase).toDouble()
            val wave = sin(angle * 0.08).toFloat() * 8.dp.toPx()
            val pointRadius = radius + wave
            val point = Offset(
                x = center.x + cos(Math.toRadians(angle)).toFloat() * pointRadius,
                y = center.y + sin(Math.toRadians(angle)).toFloat() * pointRadius
            )
            drawCircle(
                color = SwimDesignTokens.Color.HomePurpleActive.copy(alpha = 0.34f),
                radius = 2.2.dp.toPx(),
                center = point
            )
        }
    }
}

@Composable
fun SwimProtectedStatus(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(SwimDesignTokens.Home.ProtectedDot)
                .drawBehind {
                    drawCircle(
                        color = SwimDesignTokens.Color.HomeSuccessGreen.copy(alpha = 0.35f),
                        radius = size.minDimension
                    )
                }
                .clip(CircleShape)
                .background(SwimDesignTokens.Color.HomeSuccessGreen)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = SwimDesignTokens.Color.HomeSuccessGreen,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SwimServerPill(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Default.Public,
    contentDescription: String? = null
) {
    SwimPillSurface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(SwimDesignTokens.Home.ServerBadgeSize)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                SwimDesignTokens.Color.HomePurpleDeep,
                                Color(0xFF171322)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = contentDescription,
                    tint = SwimDesignTokens.Color.HomePurpleActive,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = SwimDesignTokens.Color.HomeTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = SwimDesignTokens.Color.HomeTextSecondary,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(SwimDesignTokens.Home.ServerBadgeSize)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = SwimDesignTokens.Color.HomeTextPrimary.copy(alpha = 0.78f),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
fun SwimStatsCard(
    stats: List<SwimStatItem>,
    modifier: Modifier = Modifier
) {
    SwimHardwareCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stats.take(3).forEachIndexed { index, item ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.contentDescription,
                        tint = SwimDesignTokens.Color.HomePurplePrimary,
                        modifier = Modifier.size(SwimDesignTokens.Home.StatsIconSize)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = item.label,
                        color = SwimDesignTokens.Color.HomeTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.value.isNotBlank()) {
                        Text(
                            text = item.value,
                            color = SwimDesignTokens.Color.HomeTextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (index < stats.take(3).lastIndex) {
                    Box(
                        modifier = Modifier
                            .height(58.dp)
                            .width(1.dp)
                            .background(SwimDesignTokens.Color.HomeStrokeSubtle)
                    )
                }
            }
        }
    }
}

data class SwimStatItem(
    val label: String,
    val icon: ImageVector,
    val value: String = "",
    val contentDescription: String? = null
)
