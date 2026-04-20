package com.swimvpn.app.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.swimvpn.app.ui.theme.ElectricBlue
import java.util.Locale

@Composable
fun TechnicalSettingsScreen(
    routingMode: String,
    autoConnect: Boolean,
    language: String,
    onRoutingModeChange: (String) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showLanguageMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(stringResource(R.string.title_technical), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = Color(0xFF0F172A)))
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { /* Reset */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(stringResource(R.string.btn_reset), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Application
        SectionTitle(icon = Icons.Outlined.Language, title = stringResource(R.string.section_app))
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
        ) {
            Column {
                Box {
                    SettingsRowWithChip(
                        icon = Icons.Outlined.Language, 
                        title = stringResource(R.string.label_language), 
                        subtitle = stringResource(R.string.desc_language), 
                        chipText = when(language) {
                            "fr" -> stringResource(R.string.lang_fr)
                            "ru" -> stringResource(R.string.lang_ru)
                            else -> stringResource(R.string.lang_en)
                        }, 
                        onClick = { showLanguageMenu = true }
                    )
                    
                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("English", fontWeight = FontWeight.Bold) },
                            onClick = {
                                onLanguageChange("en")
                                showLanguageMenu = false
                                updateLocale(context, "en")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Français", fontWeight = FontWeight.Bold) },
                            onClick = {
                                onLanguageChange("fr")
                                showLanguageMenu = false
                                updateLocale(context, "fr")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Русский", fontWeight = FontWeight.Bold) },
                            onClick = {
                                onLanguageChange("ru")
                                showLanguageMenu = false
                                updateLocale(context, "ru")
                            }
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
                SettingsRowWithChip(icon = Icons.Outlined.Palette, title = stringResource(R.string.label_theme), subtitle = stringResource(R.string.desc_theme), chipText = stringResource(R.string.theme_light), onClick = {})
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Connectivity Features
        SectionTitle(icon = Icons.Outlined.Speed, title = stringResource(R.string.section_connectivity))
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
        ) {
            Column {
                SettingsRowWithChip(icon = Icons.Outlined.AccountTree, title = stringResource(R.string.label_routing), subtitle = stringResource(R.string.section_routing), chipText = routingMode, onClick = {
                    onRoutingModeChange(if (routingMode == "TUNNEL") "PROXY" else "TUNNEL")
                })
                HorizontalDivider(color = Color(0xFFF1F5F9))
                SettingsRowWithSwitch(icon = Icons.Outlined.PowerSettingsNew, title = stringResource(R.string.auto_connect), subtitle = stringResource(R.string.desc_boot), checked = autoConnect, onCheckedChange = onAutoConnectChange)
                HorizontalDivider(color = Color(0xFFF1F5F9))
                SettingsRowWithSwitch(
                    icon = Icons.Outlined.Security, 
                    title = stringResource(R.string.kill_switch), 
                    subtitle = stringResource(R.string.kill_switch_desc),
                    checked = false, 
                    onCheckedChange = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val intent = Intent(Settings.ACTION_VPN_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onBack() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
        ) {
            Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

fun updateLocale(context: android.content.Context, langCode: String) {
    val locale = Locale(langCode)
    Locale.setDefault(locale)
    val config = context.resources.configuration
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
    
    // Pour une application moderne, il est souvent préférable de recréer l'activité 
    // ou d'utiliser AppCompatDelegate.setApplicationLocales si possible.
    if (context is android.app.Activity) {
        context.recreate()
    }
}

@Composable
fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        Icon(icon, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun SettingsRowWithChip(icon: ImageVector, title: String, subtitle: String, chipText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(Color(0xFFF1F5F9), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFF0F172A))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 14.sp)
                Text(subtitle, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = 0.5.sp)
            }
        }
        Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(12.dp)) {
            Text(chipText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontSize = 10.sp)
        }
    }
}

@Composable
fun SettingsRowWithSwitch(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(Color(0xFFF1F5F9), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFF0F172A))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 14.sp)
                Text(subtitle, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = 0.5.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ElectricBlue)
        )
    }
}