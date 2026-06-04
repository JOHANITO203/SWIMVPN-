package com.swimvpn.app.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.AppState
import com.swimvpn.app.MainViewModel
import com.swimvpn.app.R
import com.swimvpn.app.data.network.ServerNode
import com.swimvpn.app.ui.components.HomeVpnCoreStage
import com.swimvpn.app.ui.components.SwimCircularIconButton
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.components.SwimDockDestination
import com.swimvpn.app.ui.components.SwimHardwareCard
import com.swimvpn.app.ui.components.SwimMetaballDock
import com.swimvpn.app.ui.components.SwimPillSurface
import com.swimvpn.app.ui.formatBytes
import com.swimvpn.app.ui.orb.VpnOrbState
import com.swimvpn.app.ui.theme.SwimDesignTokens
import com.swimvpn.app.vpn.RuntimeMode
import com.swimvpn.app.vpn.RuntimeStateStore
import com.swimvpn.app.vpn.RuntimeStatus
import com.swimvpn.app.vpn.VpnManager
import com.swimvpn.app.vpn.VpnState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    data: AppState.Success,
    onNavigateProfile: () -> Unit,
    onNavigateServers: () -> Unit,
    onNavigateSubscription: () -> Unit,
    onNavigateProxy: () -> Unit = {},
) {
    val profile = data.profile
    val activeServer = data.activeServer
    val selectedRuntimeMode = data.routingMode
    val inMemoryVpnState by VpnManager.state.collectAsState()
    var vpnState by remember { mutableStateOf(inMemoryVpnState) }
    val runtimeStatus by VpnManager.runtimeStatus.collectAsState()
    val bytesIn by VpnManager.bytesIn.collectAsState()
    val bytesOut by VpnManager.bytesOut.collectAsState()
    val errorMessage by VpnManager.errorMessage.collectAsState()
    val context = LocalContext.current
    // Honor OS battery-saver / "remove animations" so the Halo Pulse aurora doesn't burn GPU and
    // battery on low-end devices: the reduced-motion path freezes drift/spin and holds a mid bloom.
    val reducedMotion = remember(context) {
        val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        val animatorScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        powerSave || animatorScale == 0f
    }

    LaunchedEffect(inMemoryVpnState, activeServer?.id) {
        val staleErrorWithoutServer = inMemoryVpnState == VpnState.ERROR && activeServer == null
        if (staleErrorWithoutServer) {
            VpnManager.updateState(VpnState.DISCONNECTED)
            vpnState = VpnState.DISCONNECTED
        } else {
            vpnState = inMemoryVpnState
        }
    }

    LaunchedEffect(Unit) {
        // Cold-start reconciliation: feed the persisted snapshot into the single authority ONCE
        // (a stale active snapshot collapses to DISCONNECTED there). After this, the live StateFlows
        // collected above drive the UI — no 1Hz polling loop burning CPU/battery while idle.
        VpnManager.reconcileRuntimeSnapshot(RuntimeStateStore.read(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.w("MainActivity", "Notification permission denied. VPN status won't be shown.")
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.toggleVpn(context, activeServer, profile)
        }
    }

    val connectionSubtitle = when (vpnState) {
        VpnState.CONNECTED -> stringResource(R.string.home_connected)
        VpnState.CONNECTING -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            stringResource(R.string.home_starting_proxy)
        } else {
            stringResource(R.string.home_starting_tunnel)
        }
        // UNSTABLE / NO_NETWORK are honestly "not protected" states; surface the
        // diagnostic hint instead of the misleading "tap to connect" idle copy.
        VpnState.UNSTABLE -> errorMessage ?: stringResource(R.string.home_unstable)
        VpnState.NO_NETWORK -> stringResource(R.string.home_no_network)
        VpnState.DISCONNECTING -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            stringResource(R.string.home_stopping_proxy)
        } else {
            stringResource(R.string.home_stopping_tunnel)
        }
        VpnState.ERROR -> errorMessage ?: stringResource(R.string.home_check_server)
        else -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            stringResource(R.string.home_tap_start_proxy)
        } else if (activeServer != null) {
            stringResource(R.string.home_tap_connect_selected)
        } else {
            stringResource(R.string.home_select_server_first)
        }
    }

    fun toggleVpnFromHome() {
        if (!profile.isPremiumAllowed && activeServer?.source == "backend" && vpnState == VpnState.DISCONNECTED) {
            onNavigateSubscription()
            return
        }
        if (vpnState == VpnState.DISCONNECTED || vpnState == VpnState.ERROR) {
            if (selectedRuntimeMode == RuntimeMode.FULL_TUNNEL) {
                val intent = android.net.VpnService.prepare(context)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    viewModel.toggleVpn(context, activeServer, profile)
                }
            } else {
                viewModel.toggleVpn(context, activeServer, profile)
            }
        } else {
            viewModel.toggleVpn(context, activeServer, profile)
        }
    }

    val orbState = mapVpnConnectionStateToOrbState(vpnState, runtimeStatus)
    val statusText = when (vpnState) {
        VpnState.CONNECTED -> stringResource(R.string.status_connected)
        VpnState.CONNECTING -> stringResource(R.string.status_connecting)
        // UNSTABLE/DEGRADED must read distinctly and NEVER as "Connected". Until dedicated
        // localized strings (status_unstable / status_no_network) exist, fall back to the
        // honest non-connected "disconnected" label rather than faking connectivity.
        VpnState.UNSTABLE -> stringResource(R.string.status_unstable)
        VpnState.NO_NETWORK -> stringResource(R.string.status_no_network)
        VpnState.DISCONNECTING -> stringResource(R.string.status_disconnecting)
        VpnState.ERROR -> errorMessage ?: stringResource(R.string.status_error)
        else -> stringResource(R.string.status_disconnected)
    }

    SwimDarkLuxuryBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < 840.dp
            val horizontalPadding = if (compact) 28.dp else SwimDesignTokens.Spacing.ScreenHorizontal
            val profileSize = if (compact) 58.dp else SwimDesignTokens.Home.ProfileButtonSize
            val orbSize = if (compact) 292.dp else 320.dp
            val titleSize = when {
                compact -> 28.sp
                statusText.length > 10 -> 30.sp
                else -> 32.sp
            }
            val subtitleSize = if (compact) 13.sp else 14.sp
            val serverHeight = if (compact) 78.dp else 86.dp
            val statsHeight = if (compact) 108.dp else 136.dp
            val dockHeight = 89.dp
            val bottomDockPadding = 34.dp

            SwimCircularIconButton(
                icon = Icons.Default.Person,
                contentDescription = stringResource(R.string.content_desc_profile),
                onClick = onNavigateProfile,
                size = profileSize,
                iconSize = if (compact) 24.dp else SwimDesignTokens.Home.ProfileIconSize,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (compact) 48.dp else 54.dp, end = horizontalPadding),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .padding(top = if (compact) 76.dp else 96.dp)
                    .padding(bottom = dockHeight + bottomDockPadding + 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HomeVpnCoreStage(
                    state = orbState,
                    onClick = ::toggleVpnFromHome,
                    size = orbSize,
                    isReducedMotionEnabled = reducedMotion,
                    modifier = Modifier.size(orbSize),
                )

                Spacer(modifier = Modifier.height(if (compact) 4.dp else 10.dp))

                Text(
                    text = statusText,
                    color = SwimDesignTokens.Color.TextPrimary,
                    fontSize = titleSize,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = connectionSubtitle,
                    color = SwimDesignTokens.Color.TextSecondary,
                    fontSize = subtitleSize,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                // Deep-link: when the active BYO proxy is reported down, offer a one-tap jump to the
                // proxy screen to test/replace it (keyed off the proxy-specific runtime message).
                if (errorMessage == stringResource(R.string.proxy_session_down)) {
                    Text(
                        text = stringResource(R.string.proxy_open_cta),
                        color = SwimDesignTokens.Color.PurpleActive,
                        fontSize = subtitleSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = if (compact) 6.dp else 8.dp)
                            .clickable { onNavigateProxy() },
                    )
                }
                StatusChip(
                    connected = vpnState == VpnState.CONNECTED,
                    compact = compact,
                    modifier = Modifier.padding(top = if (compact) 10.dp else 14.dp),
                )

                Spacer(modifier = Modifier.height(if (compact) 16.dp else 24.dp))

                SwimServerPill(
                    server = activeServer,
                    showAiBadge = data.isRecommendedServerValidated && activeServer?.id == data.recommendedServerId,
                    onClick = onNavigateServers,
                    height = serverHeight,
                    compact = compact,
                )

                Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

                SwimStatsCard(
                    bytesIn = bytesIn,
                    bytesOut = bytesOut,
                    connected = vpnState == VpnState.CONNECTED,
                    height = statsHeight,
                    compact = compact,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SwimMetaballDock(
                active = SwimDockDestination.Home,
                onHome = {},
                onServers = onNavigateServers,
                onProxy = onNavigateProxy,
                onSubscription = onNavigateSubscription,
                onSettings = onNavigateProfile,
                height = dockHeight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomDockPadding),
            )
        }
    }
}

// Protection trust badge: a single tinted chip (green dot + word) that lights up only when the
// tunnel is up. Replaces the old indicator row, which duplicated the status title's meaning.
@Composable
private fun StatusChip(connected: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val accent = if (connected) SwimDesignTokens.Color.SuccessGreen else SwimDesignTokens.Color.TextMuted
    Row(
        modifier = modifier
            .clip(SwimDesignTokens.Shape.Pill)
            .background(accent.copy(alpha = if (connected) 0.10f else 0.06f))
            .border(1.dp, accent.copy(alpha = if (connected) 0.30f else 0.20f), SwimDesignTokens.Shape.Pill)
            .padding(start = 12.dp, end = 15.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 8.dp else 9.dp)
                .drawBehind {
                    if (connected) {
                        drawCircle(color = accent.copy(alpha = 0.45f), radius = size.minDimension)
                    }
                }
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (connected) "Prot\u00E9g\u00E9" else "Non prot\u00E9g\u00E9",
            color = accent,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SwimServerPill(
    server: ServerNode?,
    showAiBadge: Boolean,
    onClick: () -> Unit,
    height: Dp,
    compact: Boolean,
) {
    val lightTheme = SwimDesignTokens.Current == SwimDesignTokens.Light
    SwimPillSurface(
        modifier = Modifier.fillMaxWidth().height(height),
        minHeight = height,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                SwimDesignTokens.Material.PurpleCoreTop.copy(alpha = if (lightTheme) 0.28f else 0.34f),
                                SwimDesignTokens.Color.PurpleDeep.copy(alpha = if (lightTheme) 0.72f else 0.58f),
                                SwimDesignTokens.Material.ShellBottom,
                            ),
                        ),
                    )
                    .border(1.dp, SwimDesignTokens.Color.StrokeActive.copy(alpha = if (lightTheme) 0.34f else 0.26f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = SwimDesignTokens.Color.PurpleActive,
                    modifier = Modifier.size(if (compact) 28.dp else 32.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server?.country ?: stringResource(R.string.selected_server_none),
                    color = SwimDesignTokens.Color.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 19.sp else 21.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = server?.let { "${it.city} \u00B7 ${stringResource(R.string.selected_server_auto_select)}" } ?: stringResource(R.string.selected_server_hint),
                    color = SwimDesignTokens.Color.TextSecondary,
                    fontSize = if (compact) 13.sp else 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showAiBadge) {
                Text(
                    text = stringResource(R.string.server_chip_ai),
                    color = SwimDesignTokens.Color.SuccessGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                SwimDesignTokens.Material.BowlTop.copy(alpha = if (lightTheme) 0.86f else 0.74f),
                                SwimDesignTokens.Material.BowlMid,
                                SwimDesignTokens.Material.BowlBottom,
                            ),
                        ),
                    )
                    .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.content_desc_open_server_list),
                    tint = SwimDesignTokens.Color.TextPrimary.copy(alpha = 0.78f),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun SwimStatsCard(
    bytesIn: Long,
    bytesOut: Long,
    connected: Boolean,
    height: Dp,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    SwimHardwareCard(
        modifier = modifier,
        height = height,
        shape = SwimDesignTokens.Shape.HardwareCard,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SwimStatsItem(
                icon = Icons.Default.ArrowDownward,
                label = stringResource(R.string.label_download).replaceFirstChar { it.uppercase() },
                value = formatBytes(bytesIn),
                compact = compact,
            )
            SwimStatsDivider()
            SwimStatsItem(
                icon = Icons.Default.ArrowUpward,
                label = stringResource(R.string.label_upload).replaceFirstChar { it.uppercase() },
                value = formatBytes(bytesOut),
                compact = compact,
            )
            SwimStatsDivider()
            SwimStatsItem(
                icon = Icons.Default.SettingsInputAntenna,
                label = stringResource(R.string.status_connected),
                value = if (connected) "Actif" else "Repos",
                compact = compact,
            )
        }
    }
}

@Composable
private fun SwimStatsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    compact: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SwimDesignTokens.Color.PurpleActive,
            modifier = Modifier.size(if (compact) 22.dp else 26.dp),
        )
        Spacer(modifier = Modifier.height(if (compact) 5.dp else 8.dp))
        Text(
            text = label.lowercase().replaceFirstChar { it.uppercase() },
            color = SwimDesignTokens.Color.TextPrimary,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = SwimDesignTokens.Color.TextMuted,
            fontSize = if (compact) 10.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwimStatsDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(46.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        SwimDesignTokens.Color.StrokeSubtle,
                        Color.Transparent,
                    ),
                ),
            )
    )
}

private fun vpnStateForRuntimeStatus(status: RuntimeStatus): VpnState {
    return when (status) {
        RuntimeStatus.IDLE -> VpnState.DISCONNECTED
        RuntimeStatus.STARTING -> VpnState.CONNECTING
        RuntimeStatus.RUNNING -> VpnState.CONNECTED
        RuntimeStatus.RECONNECTING -> VpnState.CONNECTING
        RuntimeStatus.DEGRADED -> VpnState.UNSTABLE
        RuntimeStatus.NO_NETWORK -> VpnState.NO_NETWORK
        RuntimeStatus.STOPPING -> VpnState.DISCONNECTING
        RuntimeStatus.FAILED -> VpnState.ERROR
        RuntimeStatus.STOPPED_BY_USER -> VpnState.DISCONNECTED
    }
}

private fun mapVpnConnectionStateToOrbState(vpnState: VpnState, runtimeStatus: RuntimeStatus): VpnOrbState {
    return when {
        runtimeStatus == RuntimeStatus.RECONNECTING || runtimeStatus == RuntimeStatus.DEGRADED -> VpnOrbState.UNSTABLE
        vpnState == VpnState.UNSTABLE -> VpnOrbState.UNSTABLE
        vpnState == VpnState.CONNECTED -> VpnOrbState.CONNECTED
        vpnState == VpnState.CONNECTING || vpnState == VpnState.DISCONNECTING -> VpnOrbState.CONNECTING
        vpnState == VpnState.NO_NETWORK -> VpnOrbState.DISCONNECTED
        vpnState == VpnState.ERROR -> VpnOrbState.UNSTABLE
        else -> VpnOrbState.DISCONNECTED
    }
}
