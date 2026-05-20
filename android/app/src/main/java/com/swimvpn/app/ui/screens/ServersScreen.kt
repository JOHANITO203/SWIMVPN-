package com.swimvpn.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import com.swimvpn.app.config.ActiveConfigMetadata
import com.swimvpn.app.data.network.ServerGroup
import com.swimvpn.app.data.network.ServerNode
import com.swimvpn.app.ui.components.SwimCircularIconButton
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.components.SwimDockDestination
import com.swimvpn.app.ui.components.SwimMetaballDock
import com.swimvpn.app.ui.formatBytes
import com.swimvpn.app.ui.theme.SwimDesignTokens
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class ServerSourceTab {
    IMPORTED,
    PREMIUM,
}

data class ImportedConfigSummaryUi(
    val totalQuotaGb: String,
    val quotaCaption: String,
    val expiresOn: String,
    val expiresCaption: String,
)

data class ServerNodeUi(
    val id: String,
    val countryName: String,
    val flagEmoji: String,
    val latencyLabel: String,
    val isSelected: Boolean,
)

data class ServerScreenUiState(
    val selectedTab: ServerSourceTab,
    val aiActive: Boolean,
    val importedConfig: ImportedConfigSummaryUi?,
    val importedNodes: List<ServerNodeUi>,
    val premiumNodes: List<ServerNodeUi>,
    val selectedNodeId: String?,
)

@Composable
fun ServersScreen(
    serverGroups: List<ServerGroup>,
    activeServerId: String?,
    activeConfigMetadata: ActiveConfigMetadata?,
    onSelectServer: (ServerNode) -> Unit,
    onImportAccessClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onProfileClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    recommendedServerId: String? = null,
    isRecommendedServerValidated: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(ServerSourceTab.IMPORTED) }
    val sourceGroups = remember(serverGroups) {
        serverGroups.groupBy { it.source.lowercase(Locale.ROOT) }
    }
    val importedServers = remember(sourceGroups) { sourceGroups["imported"].orEmpty().flatMap { it.servers } }
    val premiumServers = remember(sourceGroups) { sourceGroups["backend"].orEmpty().flatMap { it.servers } }

    LaunchedEffect(importedServers.isEmpty(), premiumServers.isNotEmpty()) {
        if (importedServers.isEmpty() && premiumServers.isNotEmpty()) {
            selectedTab = ServerSourceTab.PREMIUM
        }
    }

    val uiState = remember(
        selectedTab,
        activeServerId,
        activeConfigMetadata,
        importedServers,
        premiumServers,
        recommendedServerId,
        isRecommendedServerValidated,
    ) {
        ServerScreenUiState(
            selectedTab = selectedTab,
            aiActive = isRecommendedServerValidated && recommendedServerId != null,
            importedConfig = activeConfigMetadata.toImportedConfigSummaryUi(),
            importedNodes = importedServers.toNodeUi(activeServerId),
            premiumNodes = premiumServers.toNodeUi(activeServerId),
            selectedNodeId = activeServerId,
        )
    }
    val nodesById = remember(importedServers, premiumServers) {
        (importedServers + premiumServers).associateBy { it.id }
    }

    ServerScreen(
        uiState = uiState,
        onTabSelected = { selectedTab = it },
        onNodeSelected = { nodeId -> nodesById[nodeId]?.let(onSelectServer) },
        onImportAccessClick = onImportAccessClick,
        onSubscribeClick = onSubscribeClick,
        onProfileClick = onProfileClick,
        onDockNavigate = { item ->
            when (item) {
                com.swimvpn.app.ui.components.NavDockItem.HOME -> onHomeClick()
                com.swimvpn.app.ui.components.NavDockItem.SERVERS -> Unit
                com.swimvpn.app.ui.components.NavDockItem.SUBSCRIPTION -> onSubscribeClick()
                com.swimvpn.app.ui.components.NavDockItem.SETTINGS -> onSettingsClick()
            }
        },
        modifier = modifier,
    )
}

@Composable
fun ServerScreen(
    uiState: ServerScreenUiState,
    onTabSelected: (ServerSourceTab) -> Unit,
    onNodeSelected: (String) -> Unit,
    onImportAccessClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onProfileClick: () -> Unit,
    onDockNavigate: (com.swimvpn.app.ui.components.NavDockItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    SwimDarkLuxuryBackground(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 390.dp || maxHeight < 760.dp
            val horizontalPadding = if (compact) {
                SwimDesignTokens.Servers.CompactHorizontalPadding
            } else {
                SwimDesignTokens.Servers.HorizontalPadding
            }
            val dockBottomPadding = SwimDesignTokens.Servers.DockBottomPadding

            SwimCircularIconButton(
                icon = Icons.Default.Person,
                contentDescription = "Profile",
                onClick = onProfileClick,
                size = if (compact) 56.dp else 62.dp,
                iconSize = if (compact) 23.dp else 26.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = if (compact) 26.dp else 32.dp, end = horizontalPadding),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = SwimDesignTokens.Servers.TopPadding + if (compact) 20.dp else 42.dp,
                    bottom = SwimDesignTokens.Servers.DockReservedHeight + dockBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "selector") {
                    MotionReveal(visible = entered, delayMillis = 40) {
                        ServerSourceSegmentedControl(
                            selectedTab = uiState.selectedTab,
                            onTabSelected = onTabSelected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .width(SwimDesignTokens.Servers.SegmentedMaxWidth),
                        )
                    }
                }

                item(key = "ai-card") {
                    MotionReveal(visible = entered, delayMillis = 80) {
                        AiStatusCard(
                            aiActive = uiState.aiActive,
                            config = uiState.importedConfig,
                            compact = compact,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item(key = "actions") {
                    MotionReveal(visible = entered, delayMillis = 130) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ServerActionPill(
                                title = "Import Access",
                                subtitle = "Add your configs",
                                icon = Icons.Default.Download,
                                onClick = onImportAccessClick,
                                modifier = Modifier.weight(1f),
                            )
                            ServerActionPill(
                                title = "Subscribe",
                                subtitle = "Get premium access",
                                icon = Icons.Default.WorkspacePremium,
                                onClick = onSubscribeClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item(key = "section-content") {
                    AnimatedContent(
                        targetState = uiState.selectedTab,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 8 })
                                .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(180)) { -it / 10 })
                                .using(SizeTransform(clip = false))
                        },
                        label = "serverSourceContent",
                    ) { tab ->
                        val nodes = if (tab == ServerSourceTab.IMPORTED) uiState.importedNodes else uiState.premiumNodes
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (nodes.isEmpty()) {
                                EmptyServerSourcePill(tab = tab)
                            } else {
                                nodes.forEachIndexed { index, node ->
                                    MotionReveal(
                                        visible = entered,
                                        delayMillis = 160 + (index * SwimDesignTokens.Motion.StaggerMs).coerceAtMost(240),
                                    ) {
                                        ServerNodeRow(
                                            node = node,
                                            onClick = { onNodeSelected(node.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SwimMetaballDock(
                active = SwimDockDestination.Servers,
                onHome = { onDockNavigate(com.swimvpn.app.ui.components.NavDockItem.HOME) },
                onServers = { onDockNavigate(com.swimvpn.app.ui.components.NavDockItem.SERVERS) },
                onSubscription = { onDockNavigate(com.swimvpn.app.ui.components.NavDockItem.SUBSCRIPTION) },
                onSettings = { onDockNavigate(com.swimvpn.app.ui.components.NavDockItem.SETTINGS) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = dockBottomPadding),
            )
        }
    }
}

@Composable
private fun ServerSourceSegmentedControl(
    selectedTab: ServerSourceTab,
    onTabSelected: (ServerSourceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(SwimDesignTokens.Servers.SegmentedHeight)
            .shadow(12.dp, SwimDesignTokens.Shape.Pill, clip = false)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.Pill),
    ) {
        val indicatorOffset by animateFloatAsState(
            targetValue = if (selectedTab == ServerSourceTab.IMPORTED) 0f else 1f,
            animationSpec = tween(280, easing = PremiumEase),
            label = "server-tab-indicator",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(SwimDesignTokens.Servers.SegmentedHeight - 10.dp)
                .align(Alignment.CenterStart)
                .offset(x = ((SwimDesignTokens.Servers.SegmentedMaxWidth.value / 2f) * indicatorOffset).dp)
                .padding(start = 5.dp, end = 5.dp)
                .shadow(18.dp, SwimDesignTokens.Shape.Pill, spotColor = SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.35f))
                .clip(SwimDesignTokens.Shape.Pill)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SwimDesignTokens.Material.PurpleCoreTop,
                            SwimDesignTokens.Material.PurpleCoreMid,
                            SwimDesignTokens.Material.PurpleCoreBottom,
                        )
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            SegmentLabel(
                text = "Imported Servers",
                selected = selectedTab == ServerSourceTab.IMPORTED,
                onClick = { onTabSelected(ServerSourceTab.IMPORTED) },
                modifier = Modifier.weight(1f),
            )
            SegmentLabel(
                text = "Premium Servers",
                selected = selectedTab == ServerSourceTab.PREMIUM,
                onClick = { onTabSelected(ServerSourceTab.PREMIUM) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentLabel(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(targetValue = if (selected) 1f else 0.98f, label = "segment-scale")
    val fontSize = fixedSp(15)
    Box(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else SwimDesignTokens.Color.TextMuted,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AiStatusCard(
    aiActive: Boolean,
    config: ImportedConfigSummaryUi?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleSize = fixedSp(if (compact) 21 else 23)
    val bodySize = fixedSp(if (compact) 14 else 15)
    Box(
        modifier = modifier
            .height(if (compact) 196.dp else SwimDesignTokens.Servers.AiCardHeight)
            .shadow(SwimDesignTokens.Shadow.HardwareSurface, RoundedCornerShape(SwimDesignTokens.Servers.AiCardRadius), clip = false)
            .clip(RoundedCornerShape(SwimDesignTokens.Servers.AiCardRadius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, RoundedCornerShape(SwimDesignTokens.Servers.AiCardRadius))
            .drawBehind {
                drawRect(SwimDesignTokens.Highlight.InnerTop, size = Size(size.width, 1.dp.toPx()))
            }
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AiOrbBadge()
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (aiActive) "AI active" else "AI ready",
                    color = SwimDesignTokens.Color.TextPrimary,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    text = "Finding the best servers\nfor your connection",
                    color = SwimDesignTokens.Color.TextSecondary,
                    fontSize = bodySize,
                    lineHeight = fixedSp(if (compact) 17 else 18),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ActivityDot(active = aiActive)
        }

        QuotaPill(
            config = config ?: ImportedConfigSummaryUi(
                totalQuotaGb = "Unknown",
                quotaCaption = "Import required",
                expiresOn = "Unknown",
                expiresCaption = "No config",
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun AiOrbBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(SwimDesignTokens.Servers.AiBadgeSize)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        SwimDesignTokens.Material.PurpleCoreTop,
                        SwimDesignTokens.Material.PurpleCoreMid,
                        SwimDesignTokens.Material.PurpleCoreBottom,
                        SwimDesignTokens.Material.BowlBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.38f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            repeat(5) { index ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f + index * 0.015f),
                    radius = size.minDimension * (0.20f + index * 0.06f),
                    center = c,
                    style = Stroke(width = 0.8.dp.toPx()),
                )
            }
        }
        Text("AI", color = Color.White, fontSize = fixedSp(22), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ActivityDot(active: Boolean) {
    val color = if (active) SwimDesignTokens.Color.SuccessGreen else SwimDesignTokens.Color.TextMuted
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .size(18.dp)
            .drawBehind {
                drawCircle(color.copy(alpha = 0.30f), radius = size.minDimension * 1.4f)
            }
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun QuotaPill(config: ImportedConfigSummaryUi, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SwimDesignTokens.Servers.QuotaPillHeight)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Color.SurfaceHighlight.copy(alpha = 0.48f),
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.DividerSubtle, SwimDesignTokens.Shape.Pill)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuotaColumn(label = "Total quota", value = config.totalQuotaGb, caption = config.quotaCaption, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .height(58.dp)
                .width(1.dp)
                .background(SwimDesignTokens.Color.DividerSubtle)
        )
        QuotaColumn(label = "Expires on", value = config.expiresOn, caption = config.expiresCaption, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuotaColumn(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = SwimDesignTokens.Color.TextSecondary, fontSize = fixedSp(13), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = SwimDesignTokens.Color.TextPrimary, fontSize = fixedSp(19), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, color = SwimDesignTokens.Color.TextSecondary, fontSize = fixedSp(13), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ServerActionPill(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(110), label = "action-press")
    Row(
        modifier = modifier
            .height(SwimDesignTokens.Servers.ActionPillHeight)
            .scale(scale)
            .shadow(10.dp, SwimDesignTokens.Shape.Pill, clip = false)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.Pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SwimDesignTokens.Color.PurpleActive, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, color = SwimDesignTokens.Color.TextPrimary, fontSize = fixedSp(15), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = SwimDesignTokens.Color.TextSecondary, fontSize = fixedSp(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ServerNodeRow(node: ServerNodeUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val outline = if (node.isSelected) SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.82f) else SwimDesignTokens.Color.StrokeSubtle
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(90), label = "node-press")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SwimDesignTokens.Servers.NodeRowHeight)
            .scale(scale)
            .shadow(if (node.isSelected) 12.dp else 6.dp, SwimDesignTokens.Shape.Pill, spotColor = SwimDesignTokens.Color.PurplePrimary.copy(alpha = if (node.isSelected) 0.18f else 0f))
            .clip(SwimDesignTokens.Shape.Pill)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, outline, SwimDesignTokens.Shape.Pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(SwimDesignTokens.Servers.NodeFlagSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(node.flagEmoji, fontSize = fixedSp(26))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(node.countryName, color = SwimDesignTokens.Color.TextPrimary, fontSize = fixedSp(18), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(node.latencyLabel, color = SwimDesignTokens.Color.PurpleActive, fontSize = fixedSp(12), maxLines = 1)
        }
        SelectionCircle(selected = node.isSelected)
    }
}

@Composable
private fun SelectionCircle(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(SwimDesignTokens.Servers.NodeSelectorSize)
            .clip(CircleShape)
            .background(
                if (selected) {
                    Brush.radialGradient(
                        listOf(
                            SwimDesignTokens.Material.PurpleCoreTop,
                            SwimDesignTokens.Material.PurpleCoreMid,
                            SwimDesignTokens.Material.PurpleCoreBottom,
                        )
                    )
                } else {
                    Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)))
                }
            )
            .border(1.dp, if (selected) Color.Transparent else SwimDesignTokens.Color.StrokeSubtle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black.copy(alpha = 0.86f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyServerSourcePill(tab: ServerSourceTab) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(SwimDesignTokens.Material.ShellBottom)
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.Pill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (tab == ServerSourceTab.IMPORTED) "No imported servers yet" else "No premium servers available",
            color = SwimDesignTokens.Color.TextSecondary,
            fontSize = fixedSp(14),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MotionReveal(
    visible: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, delayMillis, PremiumEase)) +
            slideInVertically(tween(320, delayMillis, PremiumEase)) { 18 },
        exit = fadeOut(tween(120)),
    ) {
        content()
    }
}

private fun List<ServerNode>.toNodeUi(activeServerId: String?): List<ServerNodeUi> =
    map { server ->
        ServerNodeUi(
            id = server.id,
            countryName = server.country.ifBlank { server.city.ifBlank { "Unknown" } },
            flagEmoji = getFlagEmoji(server.countryCode.orEmpty()),
            latencyLabel = if (server.ping > 0) "${server.ping} latency" else "Latency pending",
            isSelected = server.id == activeServerId,
        )
    }

private fun ActiveConfigMetadata?.toImportedConfigSummaryUi(): ImportedConfigSummaryUi? {
    if (this == null) return null
    val totalBytes = trafficTotalBytes
    val usedBytes = trafficUsedBytes ?: 0L
    val quotaValue = when {
        totalBytes != null && totalBytes > 0L -> formatBytes(totalBytes)
        else -> "Unlimited"
    }
    val quotaCaption = when {
        totalBytes != null && totalBytes > 0L -> "${formatBytes(usedBytes)} used"
        else -> "Unlimited"
    }
    return ImportedConfigSummaryUi(
        totalQuotaGb = quotaValue,
        quotaCaption = quotaCaption,
        expiresOn = expiresAt?.let(::formatServerExpiryDate) ?: "Unknown",
        expiresCaption = expiresAt?.let(::formatServerExpiryCaption) ?: "No expiry",
    )
}

fun getFlagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return "🌐"
    val firstLetter = Character.codePointAt(countryCode.uppercase(Locale.ROOT), 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(countryCode.uppercase(Locale.ROOT), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
}

private fun formatServerExpiryDate(value: String): String {
    return runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val output = SimpleDateFormat("MMM d, yyyy", Locale.US)
        output.format(input.parse(value) ?: return value.take(10))
    }.getOrElse { value.take(10) }
}

private fun formatServerExpiryCaption(value: String): String {
    return runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val expiry = input.parse(value)?.time ?: return "Expiry set"
        val days = TimeUnit.MILLISECONDS.toDays(expiry - System.currentTimeMillis())
        when {
            days > 1 -> "In $days days"
            days == 1L -> "In 1 day"
            days == 0L -> "Today"
            else -> "Expired"
        }
    }.getOrElse { "Expiry set" }
}

private val PremiumEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
private fun fixedSp(value: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { value.dp.toSp() }
}
