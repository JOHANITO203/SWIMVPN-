package com.swimvpn.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R

@Composable
fun SupportScreen(
    onNavigateToSubscription: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        // Top Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(R.string.support_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // FAQ Section
        Text(
            stringResource(R.string.support_faq_title),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Column {
                FaqItem(
                    title = stringResource(R.string.faq_connect_title),
                    description = stringResource(R.string.faq_connect_desc)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                FaqItem(
                    title = stringResource(R.string.faq_import_title),
                    description = stringResource(R.string.faq_import_desc)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Contact Section
        Text(
            stringResource(R.string.support_contact_title),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Column {
                SupportRow(
                    icon = Icons.Outlined.Email,
                    title = stringResource(R.string.support_email),
                    subtitle = stringResource(R.string.support_email_value),
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:${context.getString(R.string.support_email_value)}")
                        }
                        context.startActivity(intent)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                SupportRow(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.support_telegram_bot),
                    subtitle = stringResource(R.string.support_telegram_handle),
                    onClick = {
                        openTelegramSupport(context.getString(R.string.support_telegram_username), context)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Renew Section
        Button(
            onClick = onNavigateToSubscription,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.btn_renew),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun FaqItem(title: String, description: String) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer(rotationZ = if (expanded) 90f else 0f)
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(start = 36.dp)
            )
        }
    }
}

@Composable
fun SupportRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun openTelegramSupport(username: String, context: android.content.Context) {
    val cleanUsername = username.removePrefix("@")
    val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$cleanUsername"))
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$cleanUsername"))

    try {
        context.startActivity(telegramIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(webIntent)
    }
}
