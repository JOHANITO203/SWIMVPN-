package com.swimvpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.swimvpn.desktop.vpn.VpnState

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
                Text(
                    text = app.selected?.let { "${it.displayName} · ${it.protocol}" }
                        ?: "Aucun serveur — appuie pour importer",
                    color = tokens.color.homeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = if (vpn.fullTunnel) "Mode : tout le trafic (TUN)" else "Mode : proxy système",
                color = tokens.color.homeTextMuted, fontSize = 12.sp,
            )
        }
    }
}
