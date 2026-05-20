package com.swimvpn.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.ui.theme.AppThemePreference
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
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var selectedThemeMode by rememberSaveable(themeMode) { mutableStateOf(normalizeThemeMode(themeMode)) }
    var externalActionsArmed by rememberSaveable { mutableStateOf(false) }
    var agentEnabled by rememberSaveable { mutableStateOf(true) }
    val killSwitchStatus = readKillSwitchStatus(context)
    val batteryOptimizationRequired = isBatteryOptimizationRequired(context)
    val normalizedRoutingMode = normalizeRoutingMode(routingMode)

    LaunchedEffect(Unit) {
        externalActionsArmed = false
        delay(600)
        externalActionsArmed = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(settingsBackground())
    ) {
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

            SettingsSectionTitle("Application")
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

            SettingsSectionTitle("Connexion")
            SettingsCanvas {
                RoutingPill(
                    selectedMode = normalizedRoutingMode,
                    runtimeStatus = runtimeStatus,
                    activeRuntimeMode = activeRuntimeMode,
                    onRoutingModeChange = onRoutingModeChange,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsSwitchPill(
                    icon = Icons.Outlined.PowerSettingsNew,
                    title = "Connexion auto",
                    subtitle = if (autoConnect) "Active au prochain lancement" else "Desactivee",
                    checked = autoConnect,
                    onCheckedChange = onAutoConnectChange,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsActionPill(
                    icon = Icons.Outlined.Security,
                    title = "Kill switch",
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
                    icon = Icons.Outlined.PowerSettingsNew,
                    title = "Optimisation batterie",
                    subtitle = if (batteryOptimizationRequired) "Exemption recommandee" else "Deja optimisee",
                    enabled = externalActionsArmed,
                    onClick = {
                        if (!openBatteryOptimizationSettings(context)) {
                            Toast.makeText(context, R.string.err_open_settings_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsSwitchPill(
                    icon = Icons.Outlined.Speed,
                    title = "Agent IA",
                    subtitle = if (agentEnabled) "Selection intelligente active" else "Selection manuelle",
                    checked = agentEnabled,
                    onCheckedChange = { agentEnabled = it },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
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
                contentDescription = "Back",
                tint = SwimDesignTokens.Color.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = "Parametres",
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = "Application et connexion",
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
        icon = Icons.Outlined.Language,
        title = "Langue",
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
        icon = Icons.Outlined.Palette,
        title = "Theme",
        subtitle = if (dark) "Dark active" else "Light active",
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
        icon = Icons.Outlined.AccountTree,
        title = "Routage",
        subtitle = routingChipLabel(selectedMode),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RouteLight(
                label = "Tunnel",
                selected = selectedMode == FULL_TUNNEL_MODE,
                active = selectedMode == FULL_TUNNEL_MODE && (!running || activeMode == FULL_TUNNEL_MODE),
                onClick = { onRoutingModeChange(FULL_TUNNEL_MODE) },
            )
            RouteLight(
                label = "Proxy",
                selected = selectedMode == LOCAL_PROXY_MODE,
                active = selectedMode == LOCAL_PROXY_MODE && (!running || activeMode == LOCAL_PROXY_MODE),
                onClick = { onRoutingModeChange(LOCAL_PROXY_MODE) },
            )
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
        StatusChip(if (enabled) "OPEN" else "WAIT")
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(SwimDesignTokens.Material.BowlTop.copy(alpha = 0.72f))
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.Pill)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(SwimDesignTokens.Shadow.HardwareSurface, SwimDesignTokens.Shape.LargeHardwareCard, clip = false)
            .clip(SwimDesignTokens.Shape.LargeHardwareCard)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.LargeHardwareCard)
            .drawBehind {
                drawRect(SwimDesignTokens.Highlight.InnerTop, size = Size(size.width, 1.dp.toPx()))
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
            .background(SwimDesignTokens.Material.BowlBottom)
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

@Composable
private fun SwimSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = SwimDesignTokens.Color.PurplePrimary,
            checkedBorderColor = SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.4f),
            uncheckedThumbColor = SwimDesignTokens.Color.TextSecondary,
            uncheckedTrackColor = SwimDesignTokens.Material.BowlBottom,
            uncheckedBorderColor = SwimDesignTokens.Color.StrokeSubtle,
        ),
    )
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

private fun settingsBackground(): Brush =
    Brush.radialGradient(
        colors = listOf(
            SwimDesignTokens.Color.PurpleDeep.copy(alpha = 0.34f),
            SwimDesignTokens.Color.BackgroundBase.copy(alpha = 0.96f),
            SwimDesignTokens.Color.BackgroundDeep,
        ),
        center = Offset(Float.POSITIVE_INFINITY, 0f),
        radius = 980f,
    )

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
        LEGACY_PROXY_MODE, LOCAL_PROXY_MODE -> LOCAL_PROXY_MODE
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
        "fr" -> "Francais"
        "ru" -> "Russkiy"
        else -> "English"
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
