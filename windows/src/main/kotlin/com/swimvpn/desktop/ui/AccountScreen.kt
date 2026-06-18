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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.desktop.state.AppController
import com.swimvpn.desktop.theme.SwimDesignTokens

@Composable
fun AccountScreen(app: AppController) {
    val tokens = SwimDesignTokens.Current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text("Compte", color = tokens.color.homeTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        // Identity card
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(tokens.color.homeSurfaceBase).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(tokens.color.homeSurfaceElevated), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, tint = tokens.color.homePurplePrimary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text("SWIMVPN Windows", color = tokens.color.homeTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Édition desktop (instable) · v1.0.0", color = tokens.color.homeTextMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        AccountPill("Réglages techniques", Icons.Filled.Settings, tokens) { app.showSettings = true }
        Spacer(Modifier.height(10.dp))
        AccountPill("Support", Icons.AutoMirrored.Filled.HelpOutline, tokens) {
            runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://app.swimvpn.pro")) }
        }
    }
}

@Composable
private fun AccountPill(label: String, icon: ImageVector, tokens: com.swimvpn.desktop.theme.SwimVisualTokens, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(20.dp))
            .background(tokens.color.homeSurfaceBase).clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tokens.color.homeTextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Text(label, color = tokens.color.homeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
