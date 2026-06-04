package com.swimvpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.config.ConfigParserEngine
import com.swimvpn.app.config.ConfigRepository
import com.swimvpn.app.config.ImportResult
import com.swimvpn.app.config.Protocol
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.SwimVpnProfile
import com.swimvpn.app.data.network.ResidentialProxyProbe
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.theme.SwimDesignTokens
import kotlinx.coroutines.launch

/**
 * Dedicated "Mon proxy" screen (reached from Settings). Paste a BYO residential proxy, test it
 * end-to-end via the works-here probe (real exit country/IP + latency through the proxy), then it
 * is imported + activated so the FULL_TUNNEL routes the device through it.
 */
@Composable
fun ProxyScreen(
    configRepository: ConfigRepository,
    onProxyReady: (SwimVpnProfile) -> Unit,
    onBack: () -> Unit,
    showToast: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val c = SwimDesignTokens.Color
    var pasted by remember { mutableStateOf("") }
    var probing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ResidentialProxyProbe.Result?>(null) }
    val activatedMsg = stringResource(R.string.proxy_screen_activated)
    val duplicateMsg = stringResource(R.string.proxy_screen_duplicate)
    val importFailedMsg = stringResource(R.string.proxy_screen_import_failed)

    SwimDarkLuxuryBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 18.dp, bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(c.SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back), tint = c.TextSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.proxy_screen_title), color = c.TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.proxy_screen_subtitle), color = c.TextMuted, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.PurplePrimary.copy(alpha = 0.16f))
                        .border(1.dp, c.PurpleActive.copy(alpha = 0.30f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = c.PurpleActive, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel(stringResource(R.string.proxy_screen_paste_label))
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it; result = null },
                placeholder = { Text(stringResource(R.string.proxy_screen_paste_placeholder), color = c.TextMuted, fontSize = 13.sp) },
                minLines = 2,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = c.SurfaceBase,
                    unfocusedContainerColor = c.SurfaceBase,
                    focusedBorderColor = c.StrokeActive,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedTextColor = c.PurpleActive,
                    unfocusedTextColor = c.PurpleActive,
                    cursorColor = c.PurpleActive,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.proxy_screen_paste_hint),
                color = c.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (probing || pasted.isBlank()) return@Button
                    val text = pasted.trim()
                    scope.launch {
                        probing = true
                        result = null
                        val parsed = ConfigParserEngine.parseConfig(text, SourceType.MANUAL_ENTRY).profile
                        if (parsed == null || (parsed.protocol != Protocol.SOCKS5 && parsed.protocol != Protocol.HTTP)) {
                            result = ResidentialProxyProbe.Result(ok = false, errorRes = R.string.proxy_screen_error_format)
                            probing = false
                            return@launch
                        }
                        val probe = ResidentialProxyProbe.probe(parsed.address, parsed.port, parsed.userId, parsed.password)
                        result = probe
                        if (probe.ok) {
                            when (val imp = configRepository.importConfig(text, SourceType.MANUAL_ENTRY)) {
                                is ImportResult.Success -> {
                                    configRepository.setActiveProfile(imp.profile)
                                    onProxyReady(imp.profile)
                                    showToast(activatedMsg)
                                }
                                is ImportResult.Duplicate -> showToast(duplicateMsg)
                                is ImportResult.Error -> showToast(imp.errors.firstOrNull() ?: importFailedMsg)
                            }
                        }
                        probing = false
                    }
                },
                enabled = !probing && pasted.isNotBlank(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.PurplePrimary, contentColor = Color.White, disabledContainerColor = c.SurfaceElevated, disabledContentColor = c.TextMuted),
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) {
                if (probing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.proxy_screen_cta), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            val r = result
            if (r != null && !probing) {
                Spacer(Modifier.height(26.dp))
                if (r.ok) {
                    SectionLabel(stringResource(R.string.proxy_screen_result_label))
                    ProxyResultCard(country = r.country ?: "—", latencyMs = r.latencyMs)
                } else {
                    ProxyErrorCard(message = stringResource(r.errorRes ?: R.string.proxy_error_unreachable))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = SwimDesignTokens.Color.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 11.dp),
    )
}

@Composable
private fun ProxyResultCard(country: String, latencyMs: Int?) {
    val c = SwimDesignTokens.Color
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.SurfaceBase)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(c.SuccessGreen))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.proxy_screen_works), color = c.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ResultStat(Icons.Default.Public, stringResource(R.string.proxy_screen_stat_exit), country, Modifier.weight(1f))
                StatDivider()
                ResultStat(Icons.Default.Speed, stringResource(R.string.proxy_screen_stat_latency), latencyMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                StatDivider()
                ResultStat(Icons.Default.Shield, stringResource(R.string.proxy_screen_stat_type), "SOCKS5", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Row {
                GuardItem(stringResource(R.string.proxy_screen_guard_dns))
                Spacer(Modifier.width(22.dp))
                GuardItem(stringResource(R.string.proxy_screen_guard_noleak))
            }
        }
    }
}

@Composable
private fun ResultStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val c = SwimDesignTokens.Color
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = c.PurpleActive, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(7.dp))
        Text(label.uppercase(), color = c.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(value, color = c.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.07f)))
}

@Composable
private fun GuardItem(text: String) {
    val c = SwimDesignTokens.Color
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, contentDescription = null, tint = c.SuccessGreen, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = c.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ProxyErrorCard(message: String) {
    val c = SwimDesignTokens.Color
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.SurfaceBase)
            .border(1.dp, c.Warning.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Text("⚠︎  $message", color = c.TextPrimary, fontSize = 14.sp)
    }
}
