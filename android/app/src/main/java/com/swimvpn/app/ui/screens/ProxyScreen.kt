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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.config.ConfigParserEngine
import com.swimvpn.app.config.ConfigRepository
import com.swimvpn.app.config.Protocol
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.SwimVpnProfile
import com.swimvpn.app.data.network.ResidentialProxyProbe
import com.swimvpn.app.ui.components.FeatureGlyphs
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.theme.SwimDesignTokens
import kotlinx.coroutines.launch

/**
 * One entry of the BYO residential proxy pool: an imported SOCKS5/HTTP profile plus its last
 * "works-here" probe outcome (null = not yet tested this session).
 */
private data class ProxyPoolEntry(
    val profile: SwimVpnProfile,
    val probe: ResidentialProxyProbe.Result?,
)

/**
 * Dedicated "Mon proxy" screen (reached from Settings). Paste one OR several BYO residential proxies
 * (one per line), test them all via the works-here probe (real exit country/IP + latency through
 * each proxy), and the best-latency one is auto-imported + activated so the FULL_TUNNEL routes the
 * device through it. The others form a manual-switch pool — tap "Basculer" to make another active.
 *
 * Probing is SEQUENTIAL on purpose: [ResidentialProxyProbe] authenticates via the JVM-global
 * [java.net.Authenticator], which cannot be set per-connection, so concurrent authenticated probes
 * would race on credentials. Results stream in as each probe completes.
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
    var pool by remember { mutableStateOf<List<ProxyPoolEntry>>(emptyList()) }
    var activeId by remember { mutableStateOf<String?>(null) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val activatedMsg = stringResource(R.string.proxy_screen_activated)

    fun isProxy(p: SwimVpnProfile) = p.protocol == Protocol.SOCKS5 || p.protocol == Protocol.HTTP

    // Hydrate the pool from already-imported residential proxies so it stays visible even when
    // arriving via the proxy-down deep-link (no fresh paste) — the user can then re-test / switch.
    LaunchedEffect(Unit) {
        pool = configRepository.getAllProfiles().filter(::isProxy).map { ProxyPoolEntry(it, null) }
        activeId = configRepository.getActiveProfile()?.id
    }

    val activate: (SwimVpnProfile) -> Unit = { profile ->
        scope.launch {
            configRepository.setActiveProfile(profile)
            onProxyReady(profile)
            activeId = profile.id
            showToast(activatedMsg)
        }
    }

    // Serialized through the same `probing` flag as the full sweep: ResidentialProxyProbe
    // authenticates via the JVM-global Authenticator, so only one probe may run at a time.
    val reprobe: (ProxyPoolEntry) -> Unit = { entry ->
        if (!probing) {
            scope.launch {
                probing = true
                pool = pool.map { if (it.profile.id == entry.profile.id) it.copy(probe = null) else it }
                val res = ResidentialProxyProbe.probe(
                    entry.profile.address, entry.profile.port, entry.profile.userId, entry.profile.password,
                    useHttp = entry.profile.protocol == Protocol.HTTP,
                )
                pool = pool.map { if (it.profile.id == entry.profile.id) it.copy(probe = res) else it }
                probing = false
            }
        }
    }

    val testAndConnect: () -> Unit = {
        if (!probing) {
            scope.launch {
                probing = true
                errorRes = null
                val text = pasted.trim()
                var parsedAny = false
                if (text.isNotBlank()) {
                    text.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
                        val p = ConfigParserEngine.parseConfig(line, SourceType.MANUAL_ENTRY).profile
                        if (p != null && isProxy(p)) {
                            configRepository.importConfig(line, SourceType.MANUAL_ENTRY)
                            parsedAny = true
                        }
                    }
                    if (parsedAny) pasted = ""
                }

                val proxies = configRepository.getAllProfiles().filter(::isProxy)
                if (proxies.isEmpty() || (text.isNotBlank() && !parsedAny)) {
                    errorRes = R.string.proxy_screen_error_format
                    probing = false
                    return@launch
                }

                // Show untested rows immediately, then stream each probe result as it lands.
                pool = proxies.map { ProxyPoolEntry(it, null) }
                for (p in proxies) {
                    val res = ResidentialProxyProbe.probe(
                        p.address, p.port, p.userId, p.password, useHttp = p.protocol == Protocol.HTTP,
                    )
                    pool = pool.map { if (it.profile.id == p.id) it.copy(probe = res) else it }
                }

                // Rank reachable proxies by latency; the best becomes the active connection.
                pool = pool.sortedWith(
                    compareByDescending<ProxyPoolEntry> { it.probe?.ok == true }
                        .thenBy { it.probe?.latencyMs ?: Int.MAX_VALUE },
                )
                pool.firstOrNull { it.probe?.ok == true }?.let { best ->
                    configRepository.setActiveProfile(best.profile)
                    onProxyReady(best.profile)
                    activeId = best.profile.id
                    showToast(activatedMsg)
                }
                probing = false
            }
        }
    }

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
                    Icon(FeatureGlyphs.ProxyHub, contentDescription = null, tint = c.PurpleActive, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel(stringResource(R.string.proxy_screen_paste_label))
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it; errorRes = null },
                placeholder = { Text(stringResource(R.string.proxy_screen_paste_placeholder), color = c.TextMuted, fontSize = 13.sp) },
                minLines = 3,
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
                onClick = testAndConnect,
                enabled = !probing && (pasted.isNotBlank() || pool.isNotEmpty()),
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

            errorRes?.let { res ->
                if (!probing) {
                    Spacer(Modifier.height(22.dp))
                    ProxyErrorCard(message = stringResource(res))
                }
            }

            val activeEntry = pool.firstOrNull { it.profile.id == activeId }
            val bestId = pool.firstOrNull { it.probe?.ok == true }?.profile?.id
            val others = pool.filter { it.profile.id != activeEntry?.profile?.id }

            if (activeEntry != null) {
                Spacer(Modifier.height(26.dp))
                SectionLabel(stringResource(R.string.proxy_screen_result_label))
                ProxyHeroCard(entry = activeEntry, isBest = activeEntry.profile.id == bestId)
            }

            if (others.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel(stringResource(R.string.proxy_screen_pool_label) + " · ${others.size}")
                others.forEach { entry ->
                    ProxyPoolRow(
                        entry = entry,
                        probing = probing,
                        onSwitch = { activate(entry.profile) },
                        onRetry = { reprobe(entry) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.proxy_screen_pool_footer),
                    color = c.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp),
                )
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
private fun ProxyHeroCard(entry: ProxyPoolEntry, isBest: Boolean) {
    val c = SwimDesignTokens.Color
    val probe = entry.probe
    val ok = probe?.ok == true
    val failed = probe != null && !probe.ok
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.SurfaceBase)
            .border(1.dp, if (ok) c.PurpleActive.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (ok) c.SuccessGreen else if (failed) c.Danger else c.TextMuted))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when {
                            ok -> stringResource(R.string.proxy_screen_works)
                            failed -> stringResource(R.string.proxy_screen_unreachable_short)
                            else -> stringResource(R.string.proxy_screen_active_badge)
                        },
                        color = c.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                }
                BadgePill(if (isBest) stringResource(R.string.proxy_screen_best_badge) else stringResource(R.string.proxy_screen_active_badge))
            }
            if (ok) {
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ResultStat(Icons.Default.Public, stringResource(R.string.proxy_screen_stat_exit), probe?.country ?: "—", Modifier.weight(1f))
                    StatDivider()
                    ResultStat(Icons.Default.Speed, stringResource(R.string.proxy_screen_stat_latency), probe?.latencyMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                    StatDivider()
                    ResultStat(Icons.Default.Shield, stringResource(R.string.proxy_screen_stat_type), entry.profile.protocol.name, Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    GuardItem(stringResource(R.string.proxy_screen_guard_dns))
                    Spacer(Modifier.width(22.dp))
                    GuardItem(stringResource(R.string.proxy_screen_guard_noleak))
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("${entry.profile.address}:${entry.profile.port}", color = c.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BadgePill(text: String) {
    val c = SwimDesignTokens.Color
    Text(
        text = text,
        color = c.PurpleActive,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(40.dp))
            .background(c.PurplePrimary.copy(alpha = 0.16f))
            .border(1.dp, c.PurpleActive.copy(alpha = 0.34f), RoundedCornerShape(40.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun ProxyPoolRow(
    entry: ProxyPoolEntry,
    probing: Boolean,
    onSwitch: () -> Unit,
    onRetry: () -> Unit,
) {
    val c = SwimDesignTokens.Color
    val probe = entry.probe
    val ok = probe?.ok == true
    val failed = probe != null && !probe.ok
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.SurfaceBase)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .alpha(if (failed) 0.6f else 1f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) c.SuccessGreen else if (failed) c.Danger else c.TextMuted))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("${entry.profile.address}:${entry.profile.port}", color = c.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                    Text(
                        entry.profile.protocol.name,
                        color = c.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.SurfaceHighlight).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            probing && probe == null -> "…"
                            ok -> listOfNotNull(probe?.country, probe?.latencyMs?.let { "$it ms" }).joinToString(" · ")
                            failed -> stringResource(R.string.proxy_screen_unreachable_short)
                            else -> stringResource(R.string.proxy_screen_untested)
                        },
                        color = if (failed) c.Danger else c.TextSecondary, fontSize = 12.sp,
                    )
                }
            }
            if (!probing) {
                Spacer(Modifier.width(10.dp))
                if (failed) {
                    PoolAction(stringResource(R.string.proxy_screen_retry), accent = false, onClick = onRetry)
                } else {
                    PoolAction(stringResource(R.string.proxy_screen_switch), accent = true, onClick = onSwitch)
                }
            }
        }
    }
}

@Composable
private fun PoolAction(text: String, accent: Boolean, onClick: () -> Unit) {
    val c = SwimDesignTokens.Color
    Text(
        text = text,
        color = if (accent) c.PurpleActive else c.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (accent) c.PurpleActive.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ResultStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val c = SwimDesignTokens.Color
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = c.PurpleActive, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(7.dp))
        Text(label.uppercase(), color = c.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(value, color = c.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
