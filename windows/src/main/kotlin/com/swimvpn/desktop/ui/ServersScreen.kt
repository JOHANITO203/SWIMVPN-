package com.swimvpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    // No auto-ping: probing every server on each list change flooded the active tunnel and
    // destabilized things. Latency is on-demand (like Happ's "Test ping"), so delete stays local.
    // Deletion via a pending flag + LaunchedEffect: the click only sets the id; the actual list
    // mutation runs on the composition coroutine AFTER the event, so the clicked row is never
    // disposed mid-click (the freeze/crash on delete).
    var pendingRemoval by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingRemoval) {
        val id = pendingRemoval ?: return@LaunchedEffect
        app.remove(id)
        pendingRemoval = null
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text("Serveurs", color = tokens.color.homeTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Tes configurations importées", color = tokens.color.homeTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        // Subscription metadata (parsed from the response headers, like Android).
        app.subscription?.let { sub ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(tokens.color.homeSurfaceBase).padding(16.dp),
            ) {
                Text(sub.title ?: "Abonnement", color = tokens.color.homeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val total = sub.totalBytes
                if (total != null && total > 0) {
                    val used = sub.usedBytes ?: 0L
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (used.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = tokens.color.homePurplePrimary,
                        trackColor = tokens.color.homeStrokeSubtle,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${gb(used)} / ${gb(total)} Go utilisés", color = tokens.color.homeTextSecondary, fontSize = 12.sp)
                }
                sub.expiresAt?.let {
                    Text("Expire le ${fmtDate(it)}", color = tokens.color.homeTextMuted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
        }

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
        Spacer(Modifier.height(8.dp))
        if (app.configs.isNotEmpty()) {
            TextButton(onClick = { app.refreshLatencies() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Tester le ping", color = tokens.color.homePurplePrimary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (app.configs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucune config. Colle un lien VLESS/VMess/Trojan/Shadowsocks\nou un lien d'abonnement.",
                    color = tokens.color.homeTextMuted, fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                app.configs.toList().forEach { cfg ->
                    val active = cfg.id == app.selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) tokens.color.homeSurfaceHighlight else tokens.color.homeSurfaceBase)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { app.select(cfg.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Flag(cfg.displayName, size = 32.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(FlagUtil.cleanName(cfg.displayName), color = tokens.color.homeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${cfg.protocol} · ${cfg.transport} · ${cfg.securityMode} · ${cfg.address}:${cfg.port}",
                                color = tokens.color.homeTextMuted, fontSize = 11.sp,
                            )
                        }
                        val hasPing = app.latency.containsKey(cfg.id)
                        val ping = app.latency[cfg.id]
                        Text(
                            text = when { !hasPing -> ""; ping == null -> "✕"; else -> "$ping ms" },
                            color = when {
                                ping == null -> tokens.color.homeDanger
                                hasPing && ping < 150 -> tokens.color.homeSuccessGreen
                                hasPing && ping < 400 -> tokens.color.homeWarning
                                else -> tokens.color.homeTextSecondary
                            },
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.size(12.dp))
                        Icon(
                            Icons.Filled.Delete, "Supprimer",
                            tint = tokens.color.homeTextMuted,
                            modifier = Modifier.size(20.dp)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { pendingRemoval = cfg.id },
                        )
                    }
                }
            }
        }
    }

    if (showImport) {
        var draft by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(app.importResult) {
            val r = app.importResult ?: return@LaunchedEffect
            if (r.added > 0) { app.importResult = null; showImport = false } else error = r.message
        }
        AlertDialog(
            onDismissRequest = { if (!app.importBusy) showImport = false },
            title = { Text("Importer une configuration") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it; error = null },
                        label = { Text("vless:// · vmess:// · trojan:// · ss:// · abonnement (https://…)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (app.importBusy) {
                        Spacer(Modifier.height(8.dp))
                        Text("Téléchargement de l'abonnement…", color = tokens.color.homeTextMuted, fontSize = 12.sp)
                    }
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = tokens.color.homeDanger, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !app.importBusy, onClick = {
                    if (draft.isNotBlank()) { error = null; app.importConfig(draft.trim()) }
                }) { Text(if (app.importBusy) "…" else "Importer") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Annuler") } },
        )
    }
}

private fun gb(bytes: Long): String = String.format("%.1f", bytes / 1_000_000_000.0)

private fun fmtDate(iso: String): String = runCatching {
    java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
}.getOrDefault(iso)
