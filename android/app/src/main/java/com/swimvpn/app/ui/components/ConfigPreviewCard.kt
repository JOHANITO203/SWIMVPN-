package com.swimvpn.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swimvpn.app.config.ConfigPreview
import com.swimvpn.app.config.ValidationStatus
import com.swimvpn.app.ui.theme.SwimDesignTokens

@Composable
fun ConfigPreviewCard(
    preview: ConfigPreview,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onDelete: (() -> Unit)? = null
) {
    val borderColor = when (preview.validationStatus) {
        ValidationStatus.VALID -> if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF22C55E)
        ValidationStatus.WARNING -> Color(0xFFF59E0B)
        ValidationStatus.ERROR -> MaterialTheme.colorScheme.error
        ValidationStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row with protocol and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Protocol badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = getProtocolColor(preview.protocol).copy(alpha = 0.1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = getProtocolIcon(preview.protocol),
                            contentDescription = null,
                            tint = getProtocolColor(preview.protocol),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = preview.protocol,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = getProtocolColor(preview.protocol)
                        )
                    }
                }
                
                // Status indicator and actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(borderColor)
                    )
                    
                    // Delete button if provided
                    onDelete?.let {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = it,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            
            // Display name
            Text(
                text = preview.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Connection details
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${preview.address}:${preview.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${preview.transport} • ${preview.security}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Warnings if any
            if (preview.warnings.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Alertes",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        preview.warnings.forEach { warning ->
                            Text(
                                text = "• $warning",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(start = 18.dp, top = 2.dp)
                            )
                        }
                    }
                }
            }
            
            // Summary
            Text(
                text = preview.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun getProtocolColor(protocol: String): Color {
    return when (protocol.uppercase()) {
        "VLESS" -> Color(0xFF3B82F6)
        "VMESS" -> Color(0xFF8B5CF6)
        "TROJAN" -> Color(0xFF10B981)
        "SHADOWSOCKS" -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.outline
    }
}

@Composable
private fun getProtocolIcon(protocol: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (protocol.uppercase()) {
        "VLESS" -> Icons.Default.Security
        "VMESS" -> Icons.Default.Cloud
        "TROJAN" -> Icons.Default.Password
        "SHADOWSOCKS" -> Icons.Default.VpnKey
        else -> Icons.Default.QuestionMark
    }
}

@Composable
fun ImportConfigDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onTextChange: (String) -> Unit,
    initialText: String = "",
    preview: ConfigPreview? = null,
    canImport: Boolean = preview?.validationStatus == ValidationStatus.VALID || preview?.validationStatus == ValidationStatus.WARNING,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
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
                        .border(1.dp, SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.36f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saisie manuelle",
                        color = SwimDesignTokens.Color.TextPrimary,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        maxLines = 1
                    )
                    Text(
                        text = "Entrez une configuration d’accès VPN",
                        color = SwimDesignTokens.Color.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedTextField(
                value = initialText,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = "vless://... ou vmess://... ou trojan://...",
                        color = SwimDesignTokens.Color.TextMuted
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                label = {
                    Text(
                        text = "URL ou code de configuration",
                        color = SwimDesignTokens.Color.PurpleActive
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = SwimDesignTokens.Color.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                ),
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(34.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SwimDesignTokens.Color.TextPrimary,
                    unfocusedTextColor = SwimDesignTokens.Color.TextPrimary,
                    focusedContainerColor = SwimDesignTokens.Material.BowlMid,
                    unfocusedContainerColor = SwimDesignTokens.Material.BowlMid,
                    focusedBorderColor = SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.62f),
                    unfocusedBorderColor = SwimDesignTokens.Color.StrokeSubtle,
                    cursorColor = SwimDesignTokens.Color.PurpleActive
                )
            )

            preview?.let {
                CompactImportPreview(preview = it)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DialogPillButton(
                    text = "Annuler",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    active = false,
                    enabled = true
                )
                DialogPillButton(
                    text = "Importer",
                    onClick = { onImport(initialText) },
                    modifier = Modifier.weight(1f),
                    active = true,
                    enabled = initialText.isNotBlank() && canImport
                )
            }
        }
    }
}

@Composable
private fun CompactImportPreview(preview: ConfigPreview) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(SwimDesignTokens.Material.BowlTop.copy(alpha = 0.78f))
            .border(1.dp, preview.validationStatus.toSwimPreviewColor().copy(alpha = 0.42f), RoundedCornerShape(32.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(preview.validationStatus.toSwimPreviewColor().copy(alpha = 0.16f))
                .border(1.dp, preview.validationStatus.toSwimPreviewColor().copy(alpha = 0.44f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (preview.validationStatus == ValidationStatus.ERROR) Icons.Default.Warning else getProtocolIcon(preview.protocol),
                contentDescription = null,
                tint = preview.validationStatus.toSwimPreviewColor(),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preview.displayName.ifBlank { preview.protocol },
                color = SwimDesignTokens.Color.TextPrimary,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preview.summary,
                color = SwimDesignTokens.Color.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DialogPillButton(
    text: String,
    onClick: () -> Unit,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val background = if (active && enabled) {
        Brush.verticalGradient(
            listOf(
                SwimDesignTokens.Material.PurpleCoreTop,
                SwimDesignTokens.Material.PurpleCoreMid,
                SwimDesignTokens.Material.PurpleCoreBottom
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                SwimDesignTokens.Material.ShellMid,
                SwimDesignTokens.Material.ShellBottom
            )
        )
    }
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(background)
            .border(
                1.dp,
                if (active && enabled) SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.52f) else SwimDesignTokens.Color.StrokeSubtle,
                SwimDesignTokens.Shape.Pill
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) SwimDesignTokens.Color.TextPrimary else SwimDesignTokens.Color.TextMuted,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black)
        )
    }
}

private fun ValidationStatus.toSwimPreviewColor(): Color =
    when (this) {
        ValidationStatus.VALID -> SwimDesignTokens.Color.SuccessGreen
        ValidationStatus.WARNING -> Color(0xFFFFC266)
        ValidationStatus.ERROR -> Color(0xFFFF7A7A)
        ValidationStatus.UNKNOWN -> SwimDesignTokens.Color.TextMuted
    }

@Composable
fun ClipboardImportSheet(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    clipboardContent: String?,
    isConfigDetected: Boolean,
    preview: ConfigPreview?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Title
            Text(
                text = "Importer depuis le presse-papiers",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            
            if (clipboardContent == null) {
                // Empty clipboard
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Le presse-papiers est vide",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!isConfigDetected) {
                // Not a VPN config
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Le contenu du presse-papiers ne semble pas être une configuration VPN",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = clipboardContent.take(100) + if (clipboardContent.length > 100) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            } else {
                // Valid config preview
                preview?.let {
                    ConfigPreviewCard(preview = it)
                    
                    Button(
                        onClick = { onImport(clipboardContent) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Importer cette configuration")
                    }
                } ?: run {
                    // Invalid config
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Format de configuration invalide",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Dismiss button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Fermer")
            }
        }
    }
}
