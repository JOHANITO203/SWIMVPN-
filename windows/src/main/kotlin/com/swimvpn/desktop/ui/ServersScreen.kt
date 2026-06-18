package com.swimvpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.desktop.state.AppController
import com.swimvpn.desktop.theme.SwimDesignTokens

@Composable
fun ServersScreen(app: AppController) {
    val tokens = SwimDesignTokens.Current
    var showImport by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text("Serveurs", color = tokens.color.homeTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Tes configurations importées", color = tokens.color.homeTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        // Import action
        Box(
            modifier = Modifier
                .fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(tokens.color.homePurplePrimary)
                .clickable { showImport = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("＋ Importer une configuration", color = androidx.compose.ui.graphics.Color.White,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))

        if (app.configs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucune config. Colle un lien VLESS/VMess/Trojan/Shadowsocks\nou un lien d'abonnement.",
                    color = tokens.color.homeTextMuted, fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(app.configs, key = { it.id }) { cfg ->
                    val active = cfg.id == app.selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) tokens.color.homeSurfaceHighlight else tokens.color.homeSurfaceBase)
                            .clickable { app.select(cfg.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(if (active) tokens.color.homePurplePrimary else tokens.color.homeStrokeSubtle),
                            contentAlignment = Alignment.Center,
                        ) { if (active) Icon(Icons.Filled.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp)) }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cfg.displayName, color = tokens.color.homeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${cfg.protocol} · ${cfg.transport} · ${cfg.securityMode} · ${cfg.address}:${cfg.port}",
                                color = tokens.color.homeTextMuted, fontSize = 11.sp,
                            )
                        }
                        Icon(
                            Icons.Filled.Delete, "Supprimer",
                            tint = tokens.color.homeTextMuted,
                            modifier = Modifier.size(20.dp).clickable { app.remove(cfg.id) },
                        )
                    }
                }
            }
        }
    }

    if (showImport) {
        var draft by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Importer une configuration") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it; error = null },
                        label = { Text("vless:// · vmess:// · trojan:// · ss:// · abonnement") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = tokens.color.homeDanger, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draft.isNotBlank()) {
                        val result = app.importConfig(draft.trim())
                        if (result.added > 0) showImport = false else error = result.message
                    }
                }) { Text("Importer") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Annuler") } },
        )
    }
}
