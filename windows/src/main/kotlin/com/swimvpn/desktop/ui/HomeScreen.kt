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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.desktop.state.AppController
import com.swimvpn.desktop.state.NavTab
import com.swimvpn.desktop.theme.SwimDesignTokens
import com.swimvpn.desktop.vpn.TrafficStats
import com.swimvpn.desktop.vpn.VpnState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(app: AppController) {
    val tokens = SwimDesignTokens.Current
    val vpn = app.vpn

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
        ) {
            Text(
                text = when (vpn.state) {
                    VpnState.CONNECTED -> "PROTÉGÉ"
                    VpnState.CONNECTING -> "CONNEXION…"
                    VpnState.ERROR -> "ERREUR"
                    VpnState.DISCONNECTED -> "NON PROTÉGÉ"
                },
                color = when (vpn.state) {
                    VpnState.CONNECTED -> tokens.color.homeSuccessGreen
                    VpnState.ERROR -> tokens.color.homeDanger
                    else -> tokens.color.homeTextSecondary
                },
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp,
            )

            Spacer(Modifier.height(40.dp))
            ConnectButton(state = vpn.state) {
                if (app.selected == null) app.tab = NavTab.SERVERS else app.toggleConnect()
            }
            Spacer(Modifier.height(28.dp))

            if (vpn.statusDetail.isNotBlank()) {
                Text(vpn.statusDetail, color = tokens.color.homeTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
            }

            // Active server pill → tap goes to Servers.
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(64.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(tokens.color.homeSurfaceBase)
                    .clickable { app.tab = NavTab.SERVERS },
                contentAlignment = Alignment.Center,
            ) {
                val sel = app.selected
                if (sel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Flag(sel.displayName, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${FlagUtil.cleanName(sel.displayName)} · ${sel.protocol}",
                            color = tokens.color.homeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                } else {
                    Text(
                        "Aucun serveur — appuie pour importer",
                        color = tokens.color.homeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = if (vpn.fullTunnel) "Mode : tout le trafic (TUN)" else "Mode : proxy système",
                color = tokens.color.homeTextMuted, fontSize = 12.sp,
            )

            // Data consumption (live session): uptime + ↓/↑ totals & speed (TUN mode).
            if (vpn.state == VpnState.CONNECTED) {
                var now by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
                val secs = ((now - vpn.connectedSinceMs).coerceAtLeast(0L) / 1000)
                val uptime = "%02d:%02d:%02d".format(secs / 3600, (secs % 3600) / 60, secs % 60)
                Spacer(Modifier.height(14.dp))
                Text(uptime, color = tokens.color.homeTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
                        Text("↓ ${TrafficStats.human(vpn.bytesIn)}", color = tokens.color.homeSuccessGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${TrafficStats.human(vpn.downBps)}/s", color = tokens.color.homeTextMuted, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
                        Text("↑ ${TrafficStats.human(vpn.bytesOut)}", color = tokens.color.homePurplePrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${TrafficStats.human(vpn.upBps)}/s", color = tokens.color.homeTextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
