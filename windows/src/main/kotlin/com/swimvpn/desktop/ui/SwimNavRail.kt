package com.swimvpn.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.desktop.state.NavTab
import com.swimvpn.desktop.theme.SwimDesignTokens

private data class RailDest(val tab: NavTab, val icon: ImageVector, val label: String)

private val dests = listOf(
    RailDest(NavTab.HOME, Icons.Filled.Home, "Accueil"),
    RailDest(NavTab.SERVERS, Icons.Filled.Dns, "Serveurs"),
    RailDest(NavTab.SUBSCRIPTION, Icons.Filled.CreditCard, "Abonnement"),
    RailDest(NavTab.ACCOUNT, Icons.Filled.Person, "Compte"),
)

/**
 * Desktop left navigation rail (replaces the mobile bottom dock — adaptive nav: ≥ wide screens
 * use a side rail). 4 top-level destinations + Settings pinned at the bottom. Active = purple
 * rounded well + white glyph (hardware grammar); inactive = muted glyph.
 */
@Composable
fun SwimNavRail(
    active: NavTab,
    settingsActive: Boolean,
    onSelect: (NavTab) -> Unit,
    onSettings: () -> Unit,
) {
    val tokens = SwimDesignTokens.Current
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(92.dp)
            .background(
                Brush.verticalGradient(
                    0f to tokens.material.shellMid,
                    1f to tokens.color.homeBackgroundDeep,
                )
            )
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Brand mark
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(Brush.verticalGradient(
                    0f to tokens.material.purpleCoreTop, 1f to tokens.material.purpleCoreBottom)),
        )
        Spacer(Modifier.height(24.dp))

        dests.forEach { d ->
            RailItem(d.icon, d.label, active = d.tab == active && !settingsActive) { onSelect(d.tab) }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.weight(1f))
        RailItem(Icons.Filled.Settings, "Réglages", active = settingsActive) { onSettings() }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val tokens = SwimDesignTokens.Current
    val bg by animateColorAsState(
        if (active) tokens.color.homePurplePrimary else androidx.compose.ui.graphics.Color.Transparent,
        label = "railbg",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, label,
                tint = if (active) androidx.compose.ui.graphics.Color.White else tokens.color.homeTextMuted,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) tokens.color.homeTextPrimary else tokens.color.homeTextMuted,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
