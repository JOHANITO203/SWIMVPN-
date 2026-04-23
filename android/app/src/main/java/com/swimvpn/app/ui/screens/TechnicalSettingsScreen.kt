package com.swimvpn.app.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.ui.theme.AppThemePreference
import com.swimvpn.app.ui.theme.ElectricBlue

private const val LEGACY_PROXY_MODE = "PROXY"
private const val LEGACY_TUNNEL_MODE = "TUNNEL"
private const val FULL_TUNNEL_MODE = "FULL_TUNNEL"
private const val LOCAL_PROXY_MODE = "LOCAL_PROXY"

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
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showRoutingMenu by rememberSaveable { mutableStateOf(false) }
    var showLanguageMenu by rememberSaveable { mutableStateOf(false) }
    var showThemeMenu by rememberSaveable { mutableStateOf(false) }
    var selectedThemeMode by rememberSaveable(themeMode) { mutableStateOf(normalizeThemeMode(themeMode)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color(0xFF0F172A)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.title_technical),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
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
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
        ) {
            Column {
                Box {
                    SettingsRowWithChip(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.label_language),
                        subtitle = stringResource(R.string.desc_language),
                        chipText = languageChipLabel(language),
                        onClick = { showLanguageMenu = true }
                    )

                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        LanguageMenuItem(stringResource(R.string.lang_en)) {
                            onLanguageChange("en")
                            showLanguageMenu = false
                        }
                        LanguageMenuItem(stringResource(R.string.lang_fr)) {
                            onLanguageChange("fr")
                            showLanguageMenu = false
                        }
                        LanguageMenuItem(stringResource(R.string.lang_ru)) {
                            onLanguageChange("ru")
                            showLanguageMenu = false
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Box {
                    SettingsRowWithChip(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.label_theme),
                        subtitle = stringResource(R.string.desc_theme),
                        chipText = themeChipLabel(selectedThemeMode),
                        onClick = { showThemeMenu = true }
                    )

                    DropdownMenu(
                        expanded = showThemeMenu,
                        onDismissRequest = { showThemeMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        ThemeMenuItem(stringResource(R.string.theme_system)) {
                            selectedThemeMode = AppThemePreference.SYSTEM
                            onThemeModeChange(AppThemePreference.SYSTEM)
                            showThemeMenu = false
                        }
                        ThemeMenuItem(stringResource(R.string.theme_light)) {
                            selectedThemeMode = AppThemePreference.LIGHT
                            onThemeModeChange(AppThemePreference.LIGHT)
                            showThemeMenu = false
                        }
                        ThemeMenuItem(stringResource(R.string.theme_dark)) {
                            selectedThemeMode = AppThemePreference.DARK
                            onThemeModeChange(AppThemePreference.DARK)
                            showThemeMenu = false
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SectionTitle(icon = Icons.Outlined.Speed, title = stringResource(R.string.section_connectivity))
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
        ) {
            Column {
                Box {
                    SettingsRowWithChip(
                        icon = Icons.Outlined.AccountTree,
                        title = stringResource(R.string.label_routing),
                        subtitle = stringResource(R.string.routing_runtime_desc),
                        chipText = routingChipLabel(routingMode),
                        onClick = { showRoutingMenu = true },
                    )

                    DropdownMenu(
                        expanded = showRoutingMenu,
                        onDismissRequest = { showRoutingMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        RoutingMenuItem(stringResource(R.string.routing_mode_full_tunnel)) {
                            onRoutingModeChange(FULL_TUNNEL_MODE)
                            showRoutingMenu = false
                        }
                        RoutingMenuItem(stringResource(R.string.routing_mode_local_proxy)) {
                            onRoutingModeChange(LOCAL_PROXY_MODE)
                            showRoutingMenu = false
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
                SettingsRowWithSwitch(
                    icon = Icons.Outlined.PowerSettingsNew,
                    title = stringResource(R.string.auto_connect),
                    subtitle = stringResource(R.string.desc_boot),
                    checked = autoConnect,
                    onCheckedChange = onAutoConnectChange
                )
                HorizontalDivider(color = Color(0xFFF1F5F9))
                SettingsRowWithChip(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.kill_switch),
                    subtitle = stringResource(R.string.kill_switch_desc),
                    chipText = stringResource(R.string.kill_switch_system_chip),
                    onClick = {
                        val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
            ) {
                Text(
                    text = runtimeDiagnostics,
                    modifier = Modifier.padding(24.dp),
                    color = Color(0xFF0F172A),
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
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
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
private fun RoutingMenuItem(label: String, onClick: () -> Unit) {
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

private fun normalizeThemeMode(themeMode: String): String =
    when (themeMode.uppercase()) {
        AppThemePreference.LIGHT -> AppThemePreference.LIGHT
        AppThemePreference.DARK -> AppThemePreference.DARK
        else -> AppThemePreference.SYSTEM
    }

@Composable
fun SectionTitle(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun SettingsRowWithChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
                    .background(Color(0xFFF1F5F9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF0F172A))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A),
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
        Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(12.dp)) {
            Text(
                text = chipText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun SettingsRowWithSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
                    .background(Color(0xFFF1F5F9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF0F172A))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A),
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ElectricBlue
            )
        )
    }
}
