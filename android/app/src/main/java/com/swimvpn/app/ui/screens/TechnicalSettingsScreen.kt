package com.swimvpn.app.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.ui.theme.AppThemePreference
import com.swimvpn.app.ui.theme.ElectricBlue
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
    runtimeDiagnostics: String = "",
    runtimeStatus: String = "IDLE",
    activeRuntimeMode: String? = null,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showLanguageMenu by rememberSaveable { mutableStateOf(false) }
    var showThemeMenu by rememberSaveable { mutableStateOf(false) }
    var selectedThemeMode by rememberSaveable(themeMode) { mutableStateOf(normalizeThemeMode(themeMode)) }
    var externalActionsArmed by rememberSaveable { mutableStateOf(false) }
    val killSwitchStatus = readKillSwitchStatus(context)

    LaunchedEffect(Unit) {
        externalActionsArmed = false
        delay(600)
        externalActionsArmed = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.title_technical),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    selectedThemeMode = AppThemePreference.SYSTEM
                    onThemeModeChange(AppThemePreference.SYSTEM)
                    if (!routingMode.equals(FULL_TUNNEL_MODE, ignoreCase = true) &&
                        !routingMode.equals(LEGACY_TUNNEL_MODE, ignoreCase = true)
                    ) {
                        onRoutingModeChange(FULL_TUNNEL_MODE)
                    }
                    onAutoConnectChange(false)
                    onLanguageChange("en")
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_reset),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SectionTitle(icon = Icons.Outlined.Language, title = stringResource(R.string.section_app))
        Spacer(modifier = Modifier.height(12.dp))
        AppPreferencesPanel(
            language = language,
            selectedThemeMode = selectedThemeMode,
            showLanguageMenu = showLanguageMenu,
            showThemeMenu = showThemeMenu,
            onLanguageMenuChange = { showLanguageMenu = it },
            onThemeMenuChange = { showThemeMenu = it },
            onLanguageChange = onLanguageChange,
            onThemeSelected = {
                selectedThemeMode = it
                onThemeModeChange(it)
            },
        )

        Spacer(modifier = Modifier.height(32.dp))

        SectionTitle(icon = Icons.Outlined.Speed, title = stringResource(R.string.section_connectivity))
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Column {
                RoutingControlPanel(
                    selectedMode = routingMode,
                    runtimeStatus = runtimeStatus,
                    activeRuntimeMode = activeRuntimeMode,
                    onRoutingModeChange = onRoutingModeChange,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRowWithSwitch(
                    icon = Icons.Outlined.PowerSettingsNew,
                    title = stringResource(R.string.auto_connect),
                    checked = autoConnect,
                    onCheckedChange = onAutoConnectChange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRowWithChip(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.kill_switch),
                    chipText = killSwitchStatusChip(killSwitchStatus),
                    enabled = externalActionsArmed,
                    onClick = {
                        val opened = runCatching {
                            val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }.isSuccess

                        if (!opened) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            }.onFailure {
                                Toast.makeText(context, R.string.err_open_settings_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        if (runtimeDiagnostics.isNotBlank()) {
            Spacer(modifier = Modifier.height(32.dp))

            SectionTitle(icon = Icons.Outlined.Security, title = "RUNTIME DIAGNOSTICS")
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            ) {
                Text(
                    text = runtimeDiagnostics,
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onBack() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = stringResource(R.string.btn_save),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AppPreferencesPanel(
    language: String,
    selectedThemeMode: String,
    showLanguageMenu: Boolean,
    showThemeMenu: Boolean,
    onLanguageMenuChange: (Boolean) -> Unit,
    onThemeMenuChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeSelected: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.app_preferences_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Box {
                AppPreferenceTile(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.label_language),
                    chipText = languageChipLabel(language),
                    onClick = { onLanguageMenuChange(true) }
                )

                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = { onLanguageMenuChange(false) },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    LanguageMenuItem(stringResource(R.string.lang_en)) {
                        onLanguageChange("en")
                        onLanguageMenuChange(false)
                    }
                    LanguageMenuItem(stringResource(R.string.lang_fr)) {
                        onLanguageChange("fr")
                        onLanguageMenuChange(false)
                    }
                    LanguageMenuItem(stringResource(R.string.lang_ru)) {
                        onLanguageChange("ru")
                        onLanguageMenuChange(false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box {
                AppPreferenceTile(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.label_theme),
                    chipText = themeChipLabel(selectedThemeMode),
                    onClick = { onThemeMenuChange(true) }
                )

                DropdownMenu(
                    expanded = showThemeMenu,
                    onDismissRequest = { onThemeMenuChange(false) },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    ThemeMenuItem(stringResource(R.string.theme_system)) {
                        onThemeSelected(AppThemePreference.SYSTEM)
                        onThemeMenuChange(false)
                    }
                    ThemeMenuItem(stringResource(R.string.theme_light)) {
                        onThemeSelected(AppThemePreference.LIGHT)
                        onThemeMenuChange(false)
                    }
                    ThemeMenuItem(stringResource(R.string.theme_dark)) {
                        onThemeSelected(AppThemePreference.DARK)
                        onThemeMenuChange(false)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPreferenceTile(
    icon: ImageVector,
    title: String,
    chipText: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = chipText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun LanguageMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = label, fontWeight = FontWeight.Bold) },
        onClick = onClick
    )
}

@Composable
private fun ThemeMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = label, fontWeight = FontWeight.Bold) },
        onClick = onClick
    )
}

@Composable
private fun languageChipLabel(language: String): String =
    when (language.lowercase()) {
        "fr" -> stringResource(R.string.lang_fr)
        "ru" -> stringResource(R.string.lang_ru)
        else -> stringResource(R.string.lang_en)
    }

@Composable
private fun themeChipLabel(themeMode: String): String =
    when (normalizeThemeMode(themeMode)) {
        AppThemePreference.LIGHT -> stringResource(R.string.theme_light)
        AppThemePreference.DARK -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }

@Composable
private fun routingChipLabel(routingMode: String): String =
    when (routingMode.uppercase()) {
        LEGACY_PROXY_MODE -> stringResource(R.string.routing_mode_local_proxy)
        "LOCAL_PROXY" -> stringResource(R.string.routing_mode_local_proxy)
        "SPLIT_TUNNEL" -> stringResource(R.string.routing_mode_split_tunnel)
        else -> stringResource(R.string.routing_mode_full_tunnel)
    }

@Composable
private fun RoutingControlPanel(
    selectedMode: String,
    runtimeStatus: String,
    activeRuntimeMode: String?,
    onRoutingModeChange: (String) -> Unit,
) {
    val normalizedSelectedMode = normalizeRoutingMode(selectedMode)
    val normalizedActiveMode = activeRuntimeMode?.let { normalizeRoutingMode(it) }
    val isRunning = runtimeStatus.equals("RUNNING", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.label_routing),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoutingModeButton(
                label = stringResource(R.string.routing_mode_full_tunnel),
                badge = stringResource(R.string.routing_badge_recommended),
                selected = normalizedSelectedMode == FULL_TUNNEL_MODE,
                active = isRunning && normalizedActiveMode == FULL_TUNNEL_MODE,
                modifier = Modifier.weight(1f),
                onClick = { onRoutingModeChange(FULL_TUNNEL_MODE) }
            )
            RoutingModeButton(
                label = stringResource(R.string.routing_mode_local_proxy),
                badge = stringResource(R.string.routing_badge_advanced),
                selected = normalizedSelectedMode == LOCAL_PROXY_MODE,
                active = isRunning && normalizedActiveMode == LOCAL_PROXY_MODE,
                modifier = Modifier.weight(1f),
                onClick = { onRoutingModeChange(LOCAL_PROXY_MODE) }
            )
        }

        if (normalizedSelectedMode == LOCAL_PROXY_MODE) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.routing_mode_local_proxy_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RoutingModeButton(
    label: String,
    badge: String,
    selected: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor = when {
        active -> MaterialTheme.colorScheme.secondary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val backgroundColor = when {
        active -> MaterialTheme.colorScheme.secondaryContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val textColor = when {
        active -> MaterialTheme.colorScheme.onSecondaryContainer
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dotColor = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier
            .height(72.dp)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        color = backgroundColor,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(dotColor, CircleShape)
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 0.4.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = badge,
                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

private fun normalizeRoutingMode(mode: String): String =
    when (mode.uppercase()) {
        LEGACY_PROXY_MODE, LOCAL_PROXY_MODE -> LOCAL_PROXY_MODE
        else -> FULL_TUNNEL_MODE
    }

private fun normalizeThemeMode(themeMode: String): String =
    when (themeMode.uppercase()) {
        AppThemePreference.LIGHT -> AppThemePreference.LIGHT
        AppThemePreference.DARK -> AppThemePreference.DARK
        else -> AppThemePreference.SYSTEM
    }

private fun readKillSwitchStatus(context: android.content.Context): KillSwitchStatus {
    return runCatching {
        val alwaysOnPackage = Settings.Secure.getString(
            context.contentResolver,
            ALWAYS_ON_VPN_APP_KEY
        )
        val lockdownEnabled = Settings.Secure.getInt(
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

@Composable
private fun killSwitchStatusChip(status: KillSwitchStatus): String {
    return when (status) {
        KillSwitchStatus.SYSTEM -> stringResource(R.string.kill_switch_chip_system)
        KillSwitchStatus.ALWAYS_ON -> stringResource(R.string.kill_switch_chip_always_on)
        KillSwitchStatus.LOCKDOWN -> stringResource(R.string.kill_switch_chip_lockdown)
    }
}

@Composable
fun SectionTitle(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun SettingsRowWithChip(
    icon: ImageVector,
    title: String,
    chipText: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable { onClick() } else Modifier
            )
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
            Text(
                text = chipText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun SettingsRowWithSwitch(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
