package com.swimvpn.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.swimvpn.app.R
import com.swimvpn.app.config.CamouflageProfileRepository
import com.swimvpn.app.config.XrayRoutingBuilder
import com.swimvpn.app.ui.components.FeatureGlyphs
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.components.drawSwimDarkMaterialSkin
import com.swimvpn.app.ui.components.drawSwimLightCardTexture
import com.swimvpn.app.ui.theme.AppThemePreference
import com.swimvpn.app.ui.theme.LocalSwimVisualTokens
import com.swimvpn.app.ui.theme.SwimDesignTokens
import kotlinx.coroutines.delay

private const val LEGACY_PROXY_MODE = "PROXY"
private const val LEGACY_TUNNEL_MODE = "TUNNEL"
private const val FULL_TUNNEL_MODE = "FULL_TUNNEL"
private const val LOCAL_PROXY_MODE = "LOCAL_PROXY"
private const val ALWAYS_ON_VPN_APP_KEY = "always_on_vpn_app"
private const val ALWAYS_ON_VPN_LOCKDOWN_KEY = "always_on_vpn_lockdown"

private enum class KillSwitchStatus {
    SYSTEM,
    ALWAYS_ON,
    LOCKDOWN,
}

@Composable
fun TechnicalSettingsScreen(
    routingMode: String,
    autoConnect: Boolean,
    language: String,
    onRoutingModeChange: (String) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onBack: () -> Unit,
    themeMode: String = AppThemePreference.SYSTEM,
    onThemeModeChange: (String) -> Unit = {},
    runtimeStatus: String = "IDLE",
    activeRuntimeMode: String? = null,
    agentEnabled: Boolean = true,
    onAgentEnabledChange: (Boolean) -> Unit = {},
    bypassGeoEnabled: Boolean = false,
    onBypassGeoEnabledChange: (Boolean) -> Unit = {},
    bypassGeoEntries: Set<String> = emptySet(),
    onBypassGeoEntriesChange: (Set<String>) -> Unit = {},
    camouflageProfileId: String = "chrome",
    activeCamouflageProfileId: String = "chrome",
    onCamouflageProfileChange: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var selectedThemeMode by rememberSaveable(themeMode) { mutableStateOf(normalizeThemeMode(themeMode)) }
    var externalActionsArmed by rememberSaveable { mutableStateOf(false) }
    var showBypassEditor by rememberSaveable { mutableStateOf(false) }
    var showCamouflagePicker by rememberSaveable { mutableStateOf(false) }
    // Battery-optimization and kill-switch status are read from system settings the user can
    // change outside the app (battery exemption dialog, VPN settings). Re-read them on every
    // ON_RESUME so the displayed status reflects reality after the user returns from those screens.
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val killSwitchStatus = remember(resumeTick) { readKillSwitchStatus(context) }
    val batteryOptimizationRequired = remember(resumeTick) { isBatteryOptimizationRequired(context) }
    val normalizedRoutingMode = normalizeRoutingMode(routingMode)

    LaunchedEffect(Unit) {
        externalActionsArmed = false
        delay(600)
        externalActionsArmed = true
    }

    SwimDarkLuxuryBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(PaddingValues(horizontal = 24.dp, vertical = 18.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsHeader(onBack = onBack)

            SettingsSectionTitle(stringResource(R.string.technical_section_application))
            SettingsCanvas {
                LanguagePill(
                    language = language,
                    onLanguageChange = onLanguageChange,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ThemeSwitchPill(
                    dark = selectedThemeMode == AppThemePreference.DARK,
                    onChange = { enabled: Boolean ->
                        val next = if (enabled) AppThemePreference.DARK else AppThemePreference.LIGHT
                        selectedThemeMode = next
                        onThemeModeChange(next)
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.technical_section_connection))
            SettingsCanvas {
                RoutingPill(
                    selectedMode = normalizedRoutingMode,
                    runtimeStatus = runtimeStatus,
                    activeRuntimeMode = activeRuntimeMode,
                    onRoutingModeChange = onRoutingModeChange,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsSwitchPill(
                    icon = FeatureGlyphs.AutoConnect,
                    title = stringResource(R.string.technical_auto_connect_title),
                    subtitle = if (autoConnect) stringResource(R.string.technical_auto_connect_on) else stringResource(R.string.technical_auto_connect_off),
                    checked = autoConnect,
                    onCheckedChange = onAutoConnectChange,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsActionPill(
                    icon = FeatureGlyphs.KillSwitch,
                    title = stringResource(R.string.technical_kill_switch_title),
                    subtitle = killSwitchStatusChip(killSwitchStatus),
                    enabled = externalActionsArmed,
                    onClick = {
                        val opened = runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_VPN_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        }.isSuccess

                        if (!opened) {
                            runCatching {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            }.onFailure {
                                Toast.makeText(context, R.string.err_open_settings_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsActionPill(
                    icon = FeatureGlyphs.Battery,
                    title = stringResource(R.string.technical_battery_title),
                    subtitle = if (batteryOptimizationRequired) stringResource(R.string.technical_battery_required) else stringResource(R.string.technical_battery_optimized),
                    enabled = externalActionsArmed,
                    onClick = {
                        if (!openBatteryOptimizationSettings(context)) {
                            Toast.makeText(context, R.string.err_open_settings_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsSwitchPill(
                    icon = FeatureGlyphs.AdaptiveAgent,
                    title = stringResource(R.string.technical_agent_title),
                    subtitle = if (agentEnabled) stringResource(R.string.technical_agent_on) else stringResource(R.string.technical_agent_off),
                    checked = agentEnabled,
                    onCheckedChange = onAgentEnabledChange,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsSwitchPill(
                    icon = FeatureGlyphs.GeoBypass,
                    title = stringResource(R.string.technical_bypass_geo_title),
                    subtitle = if (bypassGeoEnabled) stringResource(R.string.technical_bypass_geo_on) else stringResource(R.string.technical_bypass_geo_off),
                    checked = bypassGeoEnabled,
                    onCheckedChange = onBypassGeoEnabledChange,
                )
                if (bypassGeoEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsActionPill(
                        icon = FeatureGlyphs.GeoBypassList,
                        title = stringResource(R.string.technical_bypass_geo_list_title),
                        subtitle = stringResource(R.string.technical_bypass_geo_list_subtitle, bypassGeoEntries.size),
                        enabled = true,
                        onClick = { showBypassEditor = true },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                val activeCamouflageName =
                    stringResource(CamouflageProfileRepository.byId(activeCamouflageProfileId).displayNameRes)
                if (agentEnabled) {
                    // AI ON: the agent picks the profile per network — show the active one, read-only.
                    SettingsPillScaffold(
                        icon = FeatureGlyphs.Camouflage,
                        title = stringResource(R.string.technical_camouflage_title),
                        subtitle = stringResource(R.string.technical_camouflage_adaptive, activeCamouflageName),
                    ) {
                        StatusChip(stringResource(R.string.technical_camouflage_auto))
                    }
                } else {
                    // AI OFF: manual pick.
                    SettingsActionPill(
                        icon = FeatureGlyphs.Camouflage,
                        title = stringResource(R.string.technical_camouflage_title),
                        subtitle = stringResource(CamouflageProfileRepository.byId(camouflageProfileId).displayNameRes),
                        enabled = true,
                        onClick = { showCamouflagePicker = true },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showBypassEditor) {
        BypassGeoEditorDialog(
            entries = bypassGeoEntries,
            onDismiss = { showBypassEditor = false },
            onSave = {
                onBypassGeoEntriesChange(it)
                showBypassEditor = false
            },
        )
    }

    if (showCamouflagePicker) {
        CamouflageProfilePickerDialog(
            currentId = camouflageProfileId,
            onPick = {
                onCamouflageProfileChange(it)
                showCamouflagePicker = false
            },
            onDismiss = { showCamouflagePicker = false },
        )
    }
}

@Composable
private fun CamouflageProfilePickerDialog(
    currentId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SwimDesignTokens.Color.SurfaceElevated.copy(alpha = 0.98f),
                            SwimDesignTokens.Material.ShellBottom,
                        )
                    )
                )
                .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.camouflage_picker_title),
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CamouflageProfileRepository.all().forEach { profile ->
                val selected = profile.id == currentId
                val accent = if (selected) SwimDesignTokens.Highlight.PurpleEdge else SwimDesignTokens.Color.TextSecondary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(profile.id) }
                        .background(
                            if (selected) SwimDesignTokens.Highlight.PurpleEdge.copy(alpha = 0.12f)
                            else SwimDesignTokens.Material.ShellMid.copy(alpha = 0.5f)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(profile.displayNameRes),
                        color = if (selected) SwimDesignTokens.Color.TextPrimary else SwimDesignTokens.Color.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.camouflage_picker_note),
                color = SwimDesignTokens.Color.TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun BypassGeoEditorDialog(
    entries: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val working = remember { mutableStateListOf<String>().apply { addAll(entries) } }
    var input by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SwimDesignTokens.Color.SurfaceElevated.copy(alpha = 0.98f),
                            SwimDesignTokens.Material.ShellBottom,
                        )
                    )
                )
                .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.bypass_geo_editor_title),
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.bypass_geo_editor_hint), fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        XrayRoutingBuilder.sanitizeEntry(input)?.let { clean ->
                            if (clean !in working) working.add(clean)
                        }
                        input = ""
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.bypass_geo_editor_add),
                        tint = SwimDesignTokens.Color.TextPrimary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (working.isEmpty()) {
                Text(
                    text = stringResource(R.string.bypass_geo_editor_empty),
                    color = SwimDesignTokens.Color.TextSecondary,
                    fontSize = 12.sp,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    working.toList().forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SwimDesignTokens.Material.ShellMid.copy(alpha = 0.6f))
                                .padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry,
                                color = SwimDesignTokens.Color.TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { working.remove(entry) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.bypass_geo_editor_remove),
                                    tint = SwimDesignTokens.Color.TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.bypass_geo_editor_cancel), color = SwimDesignTokens.Color.TextSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onSave(working.toSet()) }) {
                    Text(stringResource(R.string.bypass_geo_editor_save), color = SwimDesignTokens.Highlight.PurpleEdge)
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HardwareCircleButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.content_desc_back),
                tint = SwimDesignTokens.Color.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = "Paramètres",
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.profile_menu_technical_subtitle),
                color = SwimDesignTokens.Color.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LanguagePill(language: String, onLanguageChange: (String) -> Unit) {
    SettingsPillScaffold(
        icon = FeatureGlyphs.Language,
        title = stringResource(R.string.technical_language_title),
        subtitle = languageDisplay(language),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LanguageChip("ru", "RU", language, onLanguageChange)
            LanguageChip("en", "EN", language, onLanguageChange)
            LanguageChip("fr", "FR", language, onLanguageChange)
        }
    }
}

@Composable
private fun ThemeSwitchPill(dark: Boolean, onChange: (Boolean) -> Unit) {
    SettingsPillScaffold(
        icon = FeatureGlyphs.Theme,
        title = stringResource(R.string.technical_theme_title),
        subtitle = if (dark) stringResource(R.string.technical_theme_dark_active) else stringResource(R.string.technical_theme_light_active),
    ) {
        SwimSwitch(checked = dark, onCheckedChange = onChange)
    }
}

@Composable
private fun RoutingPill(
    selectedMode: String,
    runtimeStatus: String,
    activeRuntimeMode: String?,
    onRoutingModeChange: (String) -> Unit,
) {
    val activeMode = activeRuntimeMode?.let { normalizeRoutingMode(it) }
    val running = runtimeStatus.equals("RUNNING", ignoreCase = true)
    SettingsPillScaffold(
        icon = FeatureGlyphs.Routing,
        title = stringResource(R.string.label_routing),
        subtitle = routingChipLabel(selectedMode),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RouteLight(
                label = "Tunnel",
                selected = selectedMode == FULL_TUNNEL_MODE,
                active = selectedMode == FULL_TUNNEL_MODE && (!running || activeMode == FULL_TUNNEL_MODE),
                onClick = { onRoutingModeChange(FULL_TUNNEL_MODE) },
            )
            // LOCAL_PROXY mode pill removed (B1/B2): it did not route device traffic. Only the
            // real FULL_TUNNEL is offered; legacy proxy preferences are coerced to it.
        }
    }
}

@Composable
private fun SettingsSwitchPill(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsPillScaffold(icon = icon, title = title, subtitle = subtitle) {
        SwimSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionPill(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingsPillScaffold(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        StatusChip(if (enabled) stringResource(R.string.technical_action_open) else stringResource(R.string.technical_action_pending))
    }
}

@Composable
private fun SettingsPillScaffold(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val tokens = LocalSwimVisualTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(SwimDesignTokens.Elevation.HardwareSurface, SwimDesignTokens.Shape.Pill, clip = false)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Color.SurfaceElevated.copy(alpha = 0.58f),
                        SwimDesignTokens.Material.ShellMid.copy(alpha = 0.98f),
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.Pill)
            .drawBehind {
                drawSwimDarkMaterialSkin(tokens)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(SwimDesignTokens.Highlight.PurpleEdge.copy(alpha = 0.075f), Color.Transparent),
                        startY = 0f,
                        endY = 10.dp.toPx(),
                    ),
                    size = Size(size.width, 10.dp.toPx()),
                )
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBowl(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                color = SwimDesignTokens.Color.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
private fun SettingsCanvas(content: @Composable ColumnScope.() -> Unit) {
    val tokens = LocalSwimVisualTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(SwimDesignTokens.Shadow.HardwareSurface, SwimDesignTokens.Shape.LargeHardwareCard, clip = false)
            .clip(SwimDesignTokens.Shape.LargeHardwareCard)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Color.SurfaceElevated,
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.LargeHardwareCard)
            .drawBehind {
                drawSwimDarkMaterialSkin(tokens)
                drawSwimLightCardTexture(tokens)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(SwimDesignTokens.Highlight.PurpleEdge.copy(alpha = 0.065f), Color.Transparent),
                        startY = 0f,
                        endY = 14.dp.toPx(),
                    ),
                    size = Size(size.width, 14.dp.toPx()),
                )
            }
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        color = SwimDesignTokens.Color.PurpleActive,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp),
    )
}

@Composable
private fun IconBowl(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        SwimDesignTokens.Material.BowlTop,
                        SwimDesignTokens.Material.BowlMid,
                        SwimDesignTokens.Material.BowlBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Highlight.BowlRim.copy(alpha = 0.72f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SwimDesignTokens.Color.PurpleActive,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HardwareCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .shadow(10.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(SwimDesignTokens.Material.BowlBottom)
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LanguageChip(
    value: String,
    label: String,
    current: String,
    onLanguageChange: (String) -> Unit,
) {
    val selected = current.equals(value, ignoreCase = true)
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(if (selected) purpleGradient() else Brush.verticalGradient(listOf(SwimDesignTokens.Material.ShellMid, SwimDesignTokens.Material.ShellBottom)))
            .border(1.dp, if (selected) SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.46f) else SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.Pill)
            .clickable { onLanguageChange(value) }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else SwimDesignTokens.Color.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun RouteLight(label: String, selected: Boolean, active: Boolean, onClick: () -> Unit) {
    val lightColor = if (active) SwimDesignTokens.Color.SuccessGreen else SwimDesignTokens.Color.TextMuted
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(if (selected) SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.12f) else SwimDesignTokens.Material.ShellBottom)
            .border(1.dp, if (selected) SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.34f) else SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(SwimDesignTokens.Material.BowlBottom)
                .border(1.dp, SwimDesignTokens.Highlight.BowlRim.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (active) 5.5.dp else 4.5.dp)
                    .clip(CircleShape)
                    .background(lightColor),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) SwimDesignTokens.Color.TextPrimary else SwimDesignTokens.Color.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

// Hardware-style toggle: a recessed track with a glossy sliding thumb. Replaces the Material3
// Switch so the control reads as part of the app's skeuomorphic surface language.
@Composable
private fun SwimSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "swimSwitchThumb",
    )
    val trackBrush = if (checked) {
        Brush.verticalGradient(
            listOf(SwimDesignTokens.Color.PurplePrimary, SwimDesignTokens.Material.PurpleCoreBottom)
        )
    } else {
        Brush.radialGradient(
            listOf(SwimDesignTokens.Material.BowlTop, SwimDesignTokens.Material.BowlMid)
        )
    }
    Box(
        modifier = Modifier
            .width(50.dp)
            .height(28.dp)
            .shadow(
                elevation = if (checked) 8.dp else 0.dp,
                shape = SwimDesignTokens.Shape.Pill,
                clip = false,
                spotColor = SwimDesignTokens.Color.PurplePrimary,
                ambientColor = Color.Transparent,
            )
            .clip(SwimDesignTokens.Shape.Pill)
            .background(trackBrush)
            .border(
                1.dp,
                if (checked) SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.55f) else SwimDesignTokens.Color.StrokeSubtle,
                SwimDesignTokens.Shape.Pill,
            )
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .shadow(3.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        if (checked) {
                            listOf(Color.White, Color(0xFFECE8F7), Color(0xFFD3CBE8))
                        } else {
                            listOf(Color(0xFF9B96A8), SwimDesignTokens.Color.TextMuted, Color(0xFF4A4651))
                        }
                    )
                ),
        )
    }
}

@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .clip(SwimDesignTokens.Shape.Pill)
            .background(SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.14f))
            .border(1.dp, SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.30f), SwimDesignTokens.Shape.Pill)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = SwimDesignTokens.Color.PurpleActive, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

private fun purpleGradient(): Brush =
    Brush.radialGradient(
        listOf(
            SwimDesignTokens.Material.PurpleCoreTop,
            SwimDesignTokens.Material.PurpleCoreMid,
            SwimDesignTokens.Material.PurpleCoreBottom,
        )
    )

private fun normalizeRoutingMode(mode: String): String =
    when (mode.uppercase()) {
        // LOCAL_PROXY retired (B1/B2): legacy/proxy preferences normalize to the real full tunnel.
        LEGACY_PROXY_MODE, LOCAL_PROXY_MODE -> FULL_TUNNEL_MODE
        LEGACY_TUNNEL_MODE, FULL_TUNNEL_MODE -> FULL_TUNNEL_MODE
        else -> FULL_TUNNEL_MODE
    }

private fun normalizeThemeMode(themeMode: String): String =
    when (themeMode.uppercase()) {
        AppThemePreference.LIGHT -> AppThemePreference.LIGHT
        AppThemePreference.DARK -> AppThemePreference.DARK
        else -> AppThemePreference.DARK
    }

private fun routingChipLabel(routingMode: String): String =
    when (normalizeRoutingMode(routingMode)) {
        LOCAL_PROXY_MODE -> "Proxy local"
        else -> "Tunnel complet"
    }

private fun languageDisplay(language: String): String =
    when (language.lowercase()) {
        "fr" -> "Français"
        "ru" -> "Russe"
        else -> "Anglais"
    }

private fun readKillSwitchStatus(context: android.content.Context): KillSwitchStatus {
    return runCatching {
        val alwaysOnPackage = AndroidSettings.Secure.getString(
            context.contentResolver,
            ALWAYS_ON_VPN_APP_KEY
        )
        val lockdownEnabled = AndroidSettings.Secure.getInt(
            context.contentResolver,
            ALWAYS_ON_VPN_LOCKDOWN_KEY,
            0
        ) == 1

        when {
            alwaysOnPackage == context.packageName && lockdownEnabled -> KillSwitchStatus.LOCKDOWN
            alwaysOnPackage == context.packageName -> KillSwitchStatus.ALWAYS_ON
            else -> KillSwitchStatus.SYSTEM
        }
    }.getOrDefault(KillSwitchStatus.SYSTEM)
}

private fun isBatteryOptimizationRequired(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openBatteryOptimizationSettings(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    val packageUri = Uri.parse("package:${context.packageName}")
    val requestOpened = runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }.isSuccess

    if (requestOpened) return true

    return runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }.isSuccess
}

@Composable
private fun killSwitchStatusChip(status: KillSwitchStatus): String {
    return when (status) {
        KillSwitchStatus.SYSTEM -> stringResource(R.string.kill_switch_chip_system)
        KillSwitchStatus.ALWAYS_ON -> stringResource(R.string.kill_switch_chip_always_on)
        KillSwitchStatus.LOCKDOWN -> stringResource(R.string.kill_switch_chip_lockdown)
    }
}
