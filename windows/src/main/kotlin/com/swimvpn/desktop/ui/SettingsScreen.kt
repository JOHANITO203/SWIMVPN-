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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.desktop.i18n.Lang
import com.swimvpn.desktop.i18n.LocalStrings
import com.swimvpn.desktop.state.AppController
import com.swimvpn.desktop.theme.SwimDesignTokens
import com.swimvpn.desktop.vpn.VpnState

@Composable
fun SettingsScreen(app: AppController) {
    val tokens = SwimDesignTokens.Current
    val s = LocalStrings.current
    val locked = app.vpn.state != VpnState.DISCONNECTED && app.vpn.state != VpnState.ERROR
    val elevated = remember0 { app.vpn.isElevated() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, s.back,
                tint = tokens.color.homeTextPrimary,
                modifier = Modifier.size(24.dp).clickable { app.showSettings = false },
            )
            Spacer(Modifier.size(12.dp))
            Text(s.settingsTitle, color = tokens.color.homeTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        // --- Language (in-place switch) ---
        SectionLabel(s.langLabel)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Lang.entries.forEach { l ->
                val active = app.lang == l
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (active) tokens.color.homePurplePrimary else tokens.color.homeSurfaceBase)
                        .clickable { app.selectLang(l) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        l.label,
                        color = if (active) Color.White else tokens.color.homeTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // --- Connection ---
        SectionLabel(s.groupConnection)
        Spacer(Modifier.height(10.dp))

        // TUN vs proxy
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tokens.color.homeSurfaceBase)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.tunLabel, color = tokens.color.homeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (app.vpn.fullTunnel) s.tunOnDesc else s.tunOffDesc,
                    color = tokens.color.homeTextMuted, fontSize = 11.sp,
                )
            }
            Switch(
                checked = app.vpn.fullTunnel,
                onCheckedChange = { if (!locked) app.setFullTunnel(it) },
                enabled = !locked,
                colors = SwitchDefaults.colors(checkedThumbColor = tokens.color.homePurplePrimary),
            )
        }
        Spacer(Modifier.height(10.dp))

        // Admin status
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tokens.color.homeSurfaceBase)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.adminLabel, color = tokens.color.homeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                if (elevated) s.adminOn else s.adminOff,
                color = if (elevated) tokens.color.homeSuccessGreen else tokens.color.homeWarning, fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(s.groupAbout)
        Spacer(Modifier.height(10.dp))
        Text("SWIMVPN Windows · ${s.accountSubtitle}", color = tokens.color.homeTextSecondary, fontSize = 13.sp)
        Text(s.aboutEngine, color = tokens.color.homeTextMuted, fontSize = 11.sp)
    }
}

/** remember without recomposition churn for a one-shot value. */
@Composable
private fun <T> remember0(calc: () -> T): T = androidx.compose.runtime.remember { calc() }
