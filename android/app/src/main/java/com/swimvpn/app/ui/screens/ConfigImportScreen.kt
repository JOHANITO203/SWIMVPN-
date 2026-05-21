package com.swimvpn.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.swimvpn.app.QrScannerView
import com.swimvpn.app.config.ConfigPreview
import com.swimvpn.app.config.ConfigRepository
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.SwimVpnProfile
import com.swimvpn.app.ui.components.ImportConfigDialog
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.theme.SwimDesignTokens
import kotlinx.coroutines.launch

@Composable
fun ConfigImportScreen(
    configRepository: ConfigRepository,
    onBack: () -> Unit,
    onProfileSelected: (SwimVpnProfile) -> Unit = {},
    onProfilesImported: (List<SwimVpnProfile>) -> Unit = {},
    showToast: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var showImportDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importPreview by remember { mutableStateOf<ConfigPreview?>(null) }
    var importedProfiles by remember { mutableStateOf<List<SwimVpnProfile>>(emptyList()) }
    var activeProfileId by remember { mutableStateOf<String?>(null) }
    var isInitialLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isInitialLoading = true
        try {
            importedProfiles = configRepository.getAllProfiles()
            activeProfileId = configRepository.getActiveProfile()?.id
        } finally {
            isInitialLoading = false
        }
    }

    SwimDarkLuxuryBackground {
        if (isInitialLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SwimDesignTokens.Color.PurpleActive)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = SwimDesignTokens.Servers.HorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = SwimDesignTokens.Servers.TopPadding,
                    bottom = 44.dp,
                ),
            ) {
                item {
                    ImportHeader(onBack = onBack)
                }

                item {
                    ImportHeroCard(importedCount = importedProfiles.size)
                }

                item {
                    Text(
                        text = "IMPORT METHOD",
                        color = SwimDesignTokens.Color.PurpleActive,
                        fontSize = importFixedSp(12),
                        fontWeight = FontWeight.Black,
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ImportMethodPill(
                            title = "Manual Input",
                            subtitle = "Enter access text",
                            icon = Icons.Default.Edit,
                            modifier = Modifier.weight(1f),
                            onClick = { showImportDialog = true },
                        )
                        ImportMethodPill(
                            title = "QR Code",
                            subtitle = "Scan access",
                            icon = Icons.Default.QrCode,
                            modifier = Modifier.weight(1f),
                            onClick = { showQrScanner = true },
                        )
                    }
                }

                item {
                    Text(
                        text = "IMPORTED CONFIGURATIONS",
                        color = SwimDesignTokens.Color.PurpleActive,
                        fontSize = importFixedSp(12),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (importedProfiles.isEmpty()) {
                    item {
                        EmptyImportState()
                    }
                } else {
                    items(importedProfiles, key = { it.id }) { profile ->
                        ImportedProfileRow(
                            profile = profile,
                            selected = profile.id == activeProfileId,
                            onSelect = {
                                scope.launch {
                                    configRepository.setActiveProfile(profile)
                                    activeProfileId = profile.id
                                    onProfileSelected(profile)
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    configRepository.deleteProfile(profile)
                                    importedProfiles = configRepository.getAllProfiles()
                                    activeProfileId = configRepository.getActiveProfile()?.id
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportConfigDialog(
            onDismiss = { showImportDialog = false },
            onImport = { text ->
                scope.launch {
                    when (val result = configRepository.importConfig(text, SourceType.MANUAL_ENTRY)) {
                        is com.swimvpn.app.config.ImportResult.Success -> {
                            importedProfiles = configRepository.getAllProfiles()
                            activeProfileId = result.profile.id
                            showImportDialog = false
                            onProfilesImported(result.importedProfiles)
                            showToast(formatImportSuccessMessage(result.importedCount))
                        }
                        is com.swimvpn.app.config.ImportResult.Error -> {
                            showToast("Import error: ${result.errors.firstOrNull() ?: "Unknown error"}")
                        }
                        is com.swimvpn.app.config.ImportResult.Duplicate -> {
                            showToast("This configuration is already imported")
                        }
                    }
                }
            },
            onTextChange = { text -> importText = text },
            initialText = importText,
            preview = importPreview,
            canImport = configRepository.canAttemptImport(importText),
        )
    }

    if (showQrScanner) {
        val onCloseQrScanner = { showQrScanner = false }
        Dialog(onDismissRequest = onCloseQrScanner) {
            QrScannerView(
                onCodeScanned = { qrText ->
                    scope.launch {
                        when (val result = configRepository.importConfig(qrText, SourceType.QR_CODE)) {
                            is com.swimvpn.app.config.ImportResult.Success -> {
                                importedProfiles = configRepository.getAllProfiles()
                                activeProfileId = result.profile.id
                                onProfilesImported(result.importedProfiles)
                                onCloseQrScanner()
                                showToast(formatImportSuccessMessage(result.importedCount, "from QR code"))
                            }
                            is com.swimvpn.app.config.ImportResult.Error -> {
                                showToast("QR import error: ${result.errors.firstOrNull() ?: "Unknown error"}")
                            }
                            is com.swimvpn.app.config.ImportResult.Duplicate -> {
                                showToast("This configuration is already imported")
                            }
                        }
                    }
                },
                onClose = onCloseQrScanner,
            )
        }
    }

    LaunchedEffect(importText) {
        importPreview = if (importText.isNotBlank()) {
            configRepository.previewConfig(importText)
        } else {
            null
        }
    }
}

@Composable
private fun ImportHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HardwareIconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = SwimDesignTokens.Color.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = "Import access",
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = importFixedSp(27),
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = "Add managed configurations to your servers",
                color = SwimDesignTokens.Color.TextSecondary,
                fontSize = importFixedSp(12),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImportHeroCard(importedCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp)
            .shadow(SwimDesignTokens.Shadow.HardwareSurface, SwimDesignTokens.Shape.LargeHardwareCard, clip = false)
            .clip(SwimDesignTokens.Shape.LargeHardwareCard)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.LargeHardwareCard)
            .drawBehind {
                drawRect(SwimDesignTokens.Highlight.InnerTop, size = Size(size.width, 1.dp.toPx()))
            }
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
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
                    val center = Offset(size.width / 2f, size.height / 2f)
                    repeat(4) { index ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.08f + index * 0.018f),
                            radius = size.minDimension * (0.20f + index * 0.07f),
                            center = center,
                            style = Stroke(width = 0.8.dp.toPx()),
                        )
                    }
                }
                Text("AI", color = Color.White, fontSize = importFixedSp(14), fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Access vault",
                    color = SwimDesignTokens.Color.TextPrimary,
                    fontSize = importFixedSp(20),
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Manual input and QR code only",
                    color = SwimDesignTokens.Color.TextSecondary,
                    fontSize = importFixedSp(12),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusChip(text = "$importedCount saved")
        }
    }
}

@Composable
private fun ImportMethodPill(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(76.dp)
            .shadow(10.dp, SwimDesignTokens.Shape.HardwareCard, clip = false)
            .clip(SwimDesignTokens.Shape.HardwareCard)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.HardwareCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(SwimDesignTokens.Material.BowlBottom)
                .border(1.dp, SwimDesignTokens.Highlight.BowlRim.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SwimDesignTokens.Color.PurpleActive,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = importFixedSp(12),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = SwimDesignTokens.Color.TextMuted,
                fontSize = importFixedSp(10),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImportedProfileRow(
    profile: SwimVpnProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val outline = if (selected) SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.74f) else SwimDesignTokens.Color.StrokeSubtle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .shadow(8.dp, SwimDesignTokens.Shape.HardwareCard, clip = false)
            .clip(SwimDesignTokens.Shape.HardwareCard)
            .background(SwimDesignTokens.Material.ShellMid)
            .border(1.dp, outline, SwimDesignTokens.Shape.HardwareCard)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (selected) purpleGradient() else Brush.radialGradient(listOf(SwimDesignTokens.Material.BowlTop, SwimDesignTokens.Material.BowlBottom)))
                .border(1.dp, SwimDesignTokens.Highlight.BowlRim.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.Check else Icons.Default.Edit,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName.ifBlank { profile.address },
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = importFixedSp(14),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${profile.protocol.name} · ${profile.address}:${profile.port}",
                color = SwimDesignTokens.Color.TextSecondary,
                fontSize = importFixedSp(11),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${profile.transport.name} · ${profile.securityMode.name}",
                color = SwimDesignTokens.Color.PurpleActive,
                fontSize = importFixedSp(10),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HardwareIconButton(onClick = onDelete, size = 42.dp) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete configuration",
                tint = SwimDesignTokens.Color.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyImportState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(134.dp)
            .clip(SwimDesignTokens.Shape.LargeHardwareCard)
            .background(SwimDesignTokens.Material.ShellMid)
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.LargeHardwareCard)
            .padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No access imported",
            color = SwimDesignTokens.Color.TextPrimary,
            fontSize = importFixedSp(16),
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "Use manual input or scan a QR code to add selectable servers.",
            color = SwimDesignTokens.Color.TextSecondary,
            fontSize = importFixedSp(12),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .clip(SwimDesignTokens.Shape.Pill)
            .background(SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.14f))
            .border(1.dp, SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.30f), SwimDesignTokens.Shape.Pill)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = SwimDesignTokens.Color.PurpleActive,
            fontSize = importFixedSp(10),
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun HardwareIconButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(10.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        SwimDesignTokens.Material.BowlTop,
                        SwimDesignTokens.Material.BowlMid,
                        SwimDesignTokens.Material.BowlBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun purpleGradient(): Brush =
    Brush.radialGradient(
        listOf(
            SwimDesignTokens.Material.PurpleCoreTop,
            SwimDesignTokens.Material.PurpleCoreMid,
            SwimDesignTokens.Material.PurpleCoreBottom,
        )
    )

@Composable
private fun importFixedSp(value: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { value.dp.toSp() }
}

private fun formatImportSuccessMessage(importedCount: Int, sourceLabel: String? = null): String {
    val itemLabel = if (importedCount == 1) "server" else "servers"
    val sourceSuffix = sourceLabel?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    return "Imported $importedCount $itemLabel$sourceSuffix successfully"
}
