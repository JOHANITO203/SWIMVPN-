package com.swimvpn.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swimvpn.app.config.ConfigPreview
import com.swimvpn.app.config.ValidationStatus

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
                                contentDescription = "Delete",
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
                                text = "Warnings",
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Import VPN Configuration",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                
                // Input field
                OutlinedTextField(
                    value = initialText,
                    onValueChange = onTextChange,
                    placeholder = { Text("vless://... or vmess://... or trojan://...") },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Configuration URL or Code") },
                    singleLine = false,
                    maxLines = 3
                )
                
                // Preview if available
                preview?.let {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    ConfigPreviewCard(preview = it)
                }
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onImport(initialText) },
                        enabled = initialText.isNotBlank() && canImport
                    ) {
                        Text("Import")
                    }
                }
            }
        }
    }
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
                text = "Import from Clipboard",
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
                        text = "Clipboard is empty",
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
                        text = "Clipboard content doesn't appear to be a VPN configuration",
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
                        Text("Import this configuration")
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
                            text = "Invalid configuration format",
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
                Text("Close")
            }
        }
    }
}
