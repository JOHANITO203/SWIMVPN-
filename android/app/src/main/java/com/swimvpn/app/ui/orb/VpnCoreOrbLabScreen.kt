package com.swimvpn.app.ui.orb

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VpnCoreOrbLabScreen(
    modifier: Modifier = Modifier,
    isReducedMotionEnabled: Boolean = false,
) {
    var state by remember { mutableStateOf(VpnOrbState.CONNECTED) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VpnCoreOrbTokens.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            VpnCoreOrbTokens.PurpleDeep.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(900f, 340f),
                        radius = 760f,
                    )
                )
        )

        VpnCoreOrb(
            state = state,
            isReducedMotionEnabled = isReducedMotionEnabled,
            onClick = {
                state = when (state) {
                    VpnOrbState.DISCONNECTED -> VpnOrbState.CONNECTING
                    VpnOrbState.CONNECTING -> VpnOrbState.CONNECTED
                    VpnOrbState.CONNECTED -> VpnOrbState.DISCONNECTED
                    VpnOrbState.UNSTABLE -> VpnOrbState.CONNECTING
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
                .size(VpnCoreOrbDimens.OrbSize),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbStateButton("Disconnected", state == VpnOrbState.DISCONNECTED) { state = VpnOrbState.DISCONNECTED }
            OrbStateButton("Connecting", state == VpnOrbState.CONNECTING) { state = VpnOrbState.CONNECTING }
            OrbStateButton("Connected", state == VpnOrbState.CONNECTED) { state = VpnOrbState.CONNECTED }
            OrbStateButton("Unstable", state == VpnOrbState.UNSTABLE) { state = VpnOrbState.UNSTABLE }
        }
    }
}

@Composable
private fun OrbStateButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) VpnCoreOrbTokens.PurplePrimary else Color(0xFF17171C))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.18f else 0.08f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) VpnCoreOrbTokens.TextWhite else VpnCoreOrbTokens.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun VpnCoreOrbLabScreenPreview() {
    VpnCoreOrbLabScreen()
}
