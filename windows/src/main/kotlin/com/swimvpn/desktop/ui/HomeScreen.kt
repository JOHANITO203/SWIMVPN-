package com.swimvpn.desktop.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.desktop.i18n.LocalStrings
import com.swimvpn.desktop.state.AppController
import com.swimvpn.desktop.state.NavTab
import com.swimvpn.desktop.theme.SwimDesignTokens
import com.swimvpn.desktop.vpn.TrafficStats
import com.swimvpn.desktop.vpn.VpnState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(app: AppController) {
    val tokens = SwimDesignTokens.Current
    val s = LocalStrings.current
    val vpn = app.vpn

    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        ) {
            Text(
                text = when (vpn.state) {
                    VpnState.CONNECTED -> s.statusProtected
                    VpnState.CONNECTING -> s.statusConnecting
                    VpnState.ERROR -> s.statusError
                    VpnState.DISCONNECTED -> s.statusUnprotected
                },
                color = when (vpn.state) {
                    VpnState.CONNECTED -> tokens.color.homeSuccessGreen
                    VpnState.ERROR -> tokens.color.homeDanger
                    else -> tokens.color.homeTextSecondary
                },
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp,
            )

            Spacer(Modifier.height(36.dp))
            ConnectButton(state = vpn.state) {
                if (app.selected == null) app.tab = NavTab.SERVERS else app.toggleConnect()
            }
            Spacer(Modifier.height(20.dp))

            if (vpn.statusDetail.isNotBlank()) {
                Text(vpn.statusDetail, color = tokens.color.homeTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }

            // Active server card → tap to pick a server.
            SwimCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 16,
                onClick = { app.tab = NavTab.SERVERS },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flag(app.selected?.displayName ?: "", size = 34.dp)
                    Spacer(Modifier.height(0.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            app.selected?.let { FlagUtil.cleanName(it.displayName) } ?: s.noServer,
                            color = tokens.color.homeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        )
                        Text(
                            app.selected?.let { "${it.protocol} · ${it.address}" } ?: s.tapToImport,
                            color = tokens.color.homeTextMuted, fontSize = 11.sp,
                        )
                    }
                    Text(if (vpn.fullTunnel) "TUN" else "PROXY", color = tokens.color.homePurplePrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Active camouflage profile (honest: the mimicked fingerprint, + AI tag when adaptive).
            Spacer(Modifier.height(8.dp))
            Text(
                s.camActiveFmt.format(app.activeProfile.label) + if (app.aiEnabled) " · ${s.aiTag}" else "",
                color = tokens.color.homeTextMuted, fontSize = 11.sp,
            )

            // Live data card (connected).
            if (vpn.state == VpnState.CONNECTED) {
                Spacer(Modifier.height(12.dp))
                var now by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
                val secs = ((now - vpn.connectedSinceMs).coerceAtLeast(0L) / 1000)
                val uptime = "%02d:%02d:%02d".format(secs / 3600, (secs % 3600) / 60, secs % 60)
                SwimCard(modifier = Modifier.fillMaxWidth(), padding = 16) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DataStat(s.dataDuration, uptime, tokens.color.homeTextPrimary)
                        DataStat(s.dataDown, "${TrafficStats.human(vpn.bytesIn)}\n${TrafficStats.human(vpn.downBps)}/s", tokens.color.homeSuccessGreen)
                        DataStat(s.dataUp, "${TrafficStats.human(vpn.bytesOut)}\n${TrafficStats.human(vpn.upBps)}/s", tokens.color.homePurplePrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DataStat(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    val tokens = SwimDesignTokens.Current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = tokens.color.homeTextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
