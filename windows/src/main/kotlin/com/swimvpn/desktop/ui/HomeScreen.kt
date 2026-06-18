package com.swimvpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.desktop.theme.SwimDesignTokens
import com.swimvpn.desktop.vpn.VpnController
import com.swimvpn.desktop.vpn.VpnState

@Composable
fun HomeScreen(controller: VpnController) {
    val tokens = SwimDesignTokens.Current
    var config by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to tokens.color.homeBackgroundDeep,
                    1f to tokens.color.homeBackgroundBase,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
        ) {
            Text(
                text = when (controller.state) {
                    VpnState.CONNECTED -> "PROTÉGÉ"
                    VpnState.CONNECTING -> "CONNEXION…"
                    VpnState.ERROR -> "ERREUR"
                    VpnState.DISCONNECTED -> "NON PROTÉGÉ"
                },
                color = when (controller.state) {
                    VpnState.CONNECTED -> tokens.color.homeSuccessGreen
                    VpnState.ERROR -> tokens.color.homeDanger
                    else -> tokens.color.homeTextSecondary
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )

            Spacer(Modifier.height(40.dp))

            ConnectButton(state = controller.state) {
                when (controller.state) {
                    VpnState.CONNECTED, VpnState.CONNECTING -> controller.disconnect()
                    else -> {
                        if (config.isBlank()) showImport = true
                        else controller.connect(config)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            if (controller.statusDetail.isNotBlank()) {
                Text(
                    text = controller.statusDetail,
                    color = tokens.color.homeTextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
            }

            // Server pill — the active endpoint (or an invite to import one).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(tokens.color.homeSurfaceBase),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = controller.activeConfig?.label
                        ?: if (config.isBlank()) "Importer une configuration" else "Configuration prête",
                    color = tokens.color.homeTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(14.dp))
            TextButton(onClick = { showImport = true }) {
                Text(if (config.isBlank()) "Coller une config VLESS" else "Changer de config",
                    color = tokens.color.homePurplePrimary)
            }

            if (!controller.isBinaryAvailable()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠ xray.exe non embarqué — UI seule (voir M2 du plan).",
                    color = tokens.color.homeWarning, fontSize = 11.sp, textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showImport) {
        var draft by remember { mutableStateOf(config) }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Importer une configuration VLESS") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("vless://…") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { config = draft.trim(); showImport = false }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Annuler") } },
        )
    }
}
