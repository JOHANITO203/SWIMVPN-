package com.swimvpn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.config.ConfigRepository
import com.swimvpn.app.config.ImportResult
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.SwimVpnProfile
import com.swimvpn.app.ui.components.NavDockItem
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.components.SwimDockDestination
import com.swimvpn.app.ui.components.SwimMetaballDock
import com.swimvpn.app.ui.theme.SwimDesignTokens
import kotlinx.coroutines.launch

/**
 * Dedicated "Mon proxy" screen (BYO residential proxy). V1: paste a proxy -> import + activate it
 * (routed via FULL_TUNNEL as the Xray outbound). The works-here egress/geo probe and AI rotation
 * are the next iteration (V2).
 */
@Composable
fun ProxyScreen(
    configRepository: ConfigRepository,
    onProxyReady: (SwimVpnProfile) -> Unit,
    onDockNavigate: (NavDockItem) -> Unit,
    showToast: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    SwimDarkLuxuryBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Mon proxy 🦈",
                    color = SwimDesignTokens.Color.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Colle ton proxy résidentiel (acheté ailleurs). On le route via le tunnel.",
                    color = SwimDesignTokens.Color.TextMuted,
                    fontSize = 14.sp,
                )
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = { Text("socks5://user:pass@host:port  ·  ou  host:port:user:pass") },
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
                Button(
                    onClick = {
                        val text = pasted.trim()
                        if (text.isEmpty() || busy) return@Button
                        busy = true
                        status = "Vérification…"
                        scope.launch {
                            when (val r = configRepository.importConfig(text, SourceType.MANUAL_ENTRY)) {
                                is ImportResult.Success -> {
                                    configRepository.setActiveProfile(r.profile)
                                    onProxyReady(r.profile)
                                    status = "✅ Proxy enregistré et activé. Connecte-toi depuis l'accueil."
                                    showToast("Proxy activé")
                                }
                                is ImportResult.Duplicate -> {
                                    status = "Ce proxy est déjà importé."
                                }
                                is ImportResult.Error -> {
                                    status = "❌ ${r.errors.firstOrNull() ?: "Proxy invalide"}"
                                }
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "…" else "Enregistrer & activer")
                }
                status?.let {
                    Text(text = it, color = SwimDesignTokens.Color.TextPrimary, fontSize = 14.sp)
                }
            }

            SwimMetaballDock(
                active = SwimDockDestination.Proxy,
                onHome = { onDockNavigate(NavDockItem.HOME) },
                onServers = { onDockNavigate(NavDockItem.SERVERS) },
                onProxy = { onDockNavigate(NavDockItem.PROXY) },
                onSubscription = { onDockNavigate(NavDockItem.SUBSCRIPTION) },
                onSettings = { onDockNavigate(NavDockItem.SETTINGS) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            )
        }
    }
}
