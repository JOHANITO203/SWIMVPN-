package com.swimvpn.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import com.swimvpn.app.config.ActiveConfigMetadata
import com.swimvpn.app.config.ActiveConfigSource
import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.ui.formatBytes
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.components.drawSwimLightCardTexture
import com.swimvpn.app.ui.theme.LocalSwimVisualTokens
import com.swimvpn.app.ui.theme.SwimDesignTokens
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
@Suppress("UNUSED_PARAMETER")
fun ProfileScreen(
    profile: AccessProfileResponse,
    activeConfigMetadata: ActiveConfigMetadata? = null,
    bytesIn: Long = 0,
    bytesOut: Long = 0,
    onNavigateToSubscription: () -> Unit,
    onNavigateToTechnical: () -> Unit,
    onNavigateToImport: () -> Unit = {},
    onNavigateToSupport: () -> Unit = {},
    onActivateTrial: (() -> Unit)? = null,
    onCancelSubscription: (() -> Unit)? = null,
    onSignOut: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val badgeText = profileBadgeText(profile, context)
    val badgeColor = profileBadgeColor(profile)
    var accountExpanded by remember { mutableStateOf(false) }
    val displayIdentity = profile.email?.takeIf { it.isNotBlank() }
        ?: profile.phone?.takeIf { it.isNotBlank() }
        ?: profile.userNumber

    SwimDarkLuxuryBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountHeader(onBack = onBack)

            AccountSectionTitle(stringResource(R.string.title_account))
            AccountCanvas {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountIconBowl {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = SwimDesignTokens.Color.TextPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayIdentity,
                            color = SwimDesignTokens.Color.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = profile.userNumber,
                            color = SwimDesignTokens.Color.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AccountStatusChip(text = badgeText, color = badgeColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SwimDesignTokens.Material.BowlMid)
                            .border(1.dp, SwimDesignTokens.Highlight.BodyStroke, CircleShape)
                            .clickable { accountExpanded = !accountExpanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (accountExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = SwimDesignTokens.Color.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (accountExpanded) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AccountInfoPill(
                        label = stringResource(R.string.label_user_id),
                        value = profile.userNumber,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AccountInfoPill(
                        label = stringResource(R.string.profile_email_label),
                        value = profile.email?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.profile_contact_missing),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AccountInfoPill(
                        label = stringResource(R.string.profile_phone_label),
                        value = profile.phone?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.profile_contact_missing),
                    )
                    if (profile.isTrialAvailable && profile.trialEligible && onActivateTrial != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AccountActionPill(
                            icon = Icons.Outlined.LocalOffer,
                            title = stringResource(R.string.profile_activate_trial),
                            subtitle = stringResource(R.string.profile_status_trial_available),
                            highlighted = true,
                            onClick = onActivateTrial,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                AccountLogoutPill(onClick = onSignOut)
            }

            AccountSectionTitle(stringResource(R.string.label_management))
            AccountCanvas {
                AccountActionPill(
                    icon = Icons.Outlined.Settings,
                    title = stringResource(R.string.menu_technical),
                    subtitle = stringResource(R.string.profile_menu_technical_subtitle),
                    onClick = onNavigateToTechnical,
                )
                Spacer(modifier = Modifier.height(12.dp))
                AccountActionPill(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = stringResource(R.string.menu_support),
                    subtitle = stringResource(R.string.profile_menu_support_subtitle),
                    onClick = onNavigateToSupport,
                )
                Spacer(modifier = Modifier.height(12.dp))
                AccountActionPill(
                    icon = Icons.Outlined.CreditCard,
                    title = stringResource(R.string.menu_subscription),
                    subtitle = stringResource(R.string.profile_section_swimvpn_access),
                    onClick = onNavigateToSubscription,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AccountHeader(onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            HardwareAccountButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.content_desc_back),
                    tint = SwimDesignTokens.Color.TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.title_account),
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = "Identité et accès",
                color = SwimDesignTokens.Color.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HardwareAccountButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(12.dp, CircleShape, ambientColor = SwimDesignTokens.Material.ShadowSoft)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Material.ShellTop,
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Highlight.BodyStroke, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun AccountSectionTitle(title: String) {
    Text(
        text = title.uppercase(Locale.ROOT),
        color = SwimDesignTokens.Color.PurpleActive,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.3.sp,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
    )
}

@Composable
private fun AccountCanvas(content: @Composable ColumnScope.() -> Unit) {
    val tokens = LocalSwimVisualTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = SwimDesignTokens.Elevation.HardwareSurface,
                shape = SwimDesignTokens.Shape.LargeHardwareCard,
                ambientColor = SwimDesignTokens.Material.ShadowSoft,
                spotColor = SwimDesignTokens.Material.ShadowRaised,
            )
            .clip(SwimDesignTokens.Shape.LargeHardwareCard)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Color.SurfaceHighlight.copy(alpha = 0.88f),
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Highlight.BodyStroke, SwimDesignTokens.Shape.LargeHardwareCard)
            .drawBehind {
                drawSwimLightCardTexture(tokens)
            }
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun AccountIconBowl(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Material.BowlTop,
                        SwimDesignTokens.Material.BowlMid,
                        SwimDesignTokens.Material.BowlBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Highlight.BowlRim, CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun AccountStatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(SwimDesignTokens.Shape.Pill)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.28f), SwimDesignTokens.Shape.Pill)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccountInfoPill(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SwimDesignTokens.Shape.Pill)
            .background(SwimDesignTokens.Material.BowlMid.copy(alpha = 0.72f))
            .border(1.dp, SwimDesignTokens.Highlight.BowlRim, SwimDesignTokens.Shape.Pill)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = SwimDesignTokens.Color.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = SwimDesignTokens.Color.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountActionPill(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(
                Brush.verticalGradient(
                    if (highlighted) {
                        listOf(
                            SwimDesignTokens.Material.PurpleCoreTop.copy(alpha = 0.80f),
                            SwimDesignTokens.Material.PurpleCoreMid.copy(alpha = 0.82f),
                            SwimDesignTokens.Material.PurpleCoreBottom.copy(alpha = 0.92f),
                        )
                    } else {
                        listOf(
                            SwimDesignTokens.Color.SurfaceHighlight.copy(alpha = 0.56f),
                            SwimDesignTokens.Material.ShellMid,
                            SwimDesignTokens.Material.ShellBottom,
                        )
                    }
                )
            )
            .border(
                1.dp,
                if (highlighted) SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.38f)
                else SwimDesignTokens.Highlight.BodyStroke,
                SwimDesignTokens.Shape.Pill,
            )
            .clickable { onClick() }
            .padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIconBowl {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) Color.White else SwimDesignTokens.Color.PurpleActive,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = if (highlighted) Color.White.copy(alpha = 0.72f) else SwimDesignTokens.Color.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (highlighted) Color.White.copy(alpha = 0.82f) else SwimDesignTokens.Color.TextSecondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun AccountLogoutPill(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(SwimDesignTokens.Material.BowlMid.copy(alpha = 0.84f))
            .border(1.dp, SwimDesignTokens.Color.TextMuted.copy(alpha = 0.18f), SwimDesignTokens.Shape.Pill)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.menu_signout),
            color = SwimDesignTokens.Color.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun SwimVpnAccessCard(
    profile: AccessProfileResponse,
    badgeColor: Color,
) {
    val context = LocalContext.current
    val expiryString = profile.effectiveExpiryAt
    val statusText = profileStatusText(profile, context)
    val expirationText = calculateRemainingTime(
        expiryDateStr = expiryString,
        unknownLabel = context.getString(R.string.profile_unknown),
        expiredAgoFormat = context.getString(R.string.profile_expired_days_ago),
        daysLeftFormat = context.getString(R.string.profile_days_left),
        hoursLeftFormat = context.getString(R.string.profile_hours_left),
        expiringSoonLabel = context.getString(R.string.profile_expiring_soon)
    )
    val exactExpirationText = expiryString
        ?.takeIf { it.isNotBlank() }
        ?.let(::formatMetadataExpiry)
        ?: context.getString(R.string.profile_unknown)
    val isActiveTrial = profile.isActiveTrial
    val isActiveSubscription = profile.isActiveSubscription
    val hasMeasuredLimit = profile.hasMeasuredLimit
    val showMeasuredAnalytics = isActiveSubscription && hasMeasuredLimit
    val quotaValue = when {
        showMeasuredAnalytics -> formatBytes(profile.dataLimitBytes)
        isActiveTrial -> stringResource(R.string.profile_trial_access_active)
        isActiveSubscription -> stringResource(R.string.profile_quota_provider_managed)
        else -> stringResource(R.string.profile_quota_freemium)
    }
    val quotaNote: String? = when {
        isActiveTrial -> stringResource(R.string.profile_trial_unlimited_note)
        isActiveSubscription -> stringResource(R.string.profile_quota_provider_managed_note)
        else -> null
    }
    val measuredUsedBytes = profile.parsedDataUsedBytes
    val remainingBytes = if (showMeasuredAnalytics) {
        (profile.dataLimitBytes - measuredUsedBytes).coerceAtLeast(0L)
    } else {
        0L
    }
    val progress = if (showMeasuredAnalytics && profile.dataLimitBytes > 0) {
        (measuredUsedBytes.toFloat() / profile.dataLimitBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val statusColor = when {
        isActiveTrial -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary // Green/Success generally represented by positive theme color or primary
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(
                            if (isActiveTrial) R.string.profile_metric_trial_access
                            else R.string.profile_metric_plan_quota
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = quotaValue,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp
                    )
                }
                if (showMeasuredAnalytics) {
                    Text(
                        text = formatUsagePercentage(progress),
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showMeasuredAnalytics) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    UsageStat(
                        title = stringResource(R.string.profile_metric_used),
                        value = formatQuotaBytes(measuredUsedBytes)
                    )
                    UsageStat(
                        title = stringResource(R.string.label_left),
                        value = formatQuotaBytes(remainingBytes)
                    )
                }
            } else {
                quotaNote?.let { note ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = note,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.profile_status_label),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        fontWeight = FontWeight.Black,
                        color = badgeColor,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.label_expires_at),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = exactExpirationText,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    if (exactExpirationText != context.getString(R.string.profile_unknown)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = expirationText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveConfigCard(metadata: ActiveConfigMetadata) {
    val sourceLabel = when (metadata.source) {
        ActiveConfigSource.SWIMVPN_MANAGED -> stringResource(R.string.active_config_source_managed)
        ActiveConfigSource.IMPORTED_CONFIG -> stringResource(R.string.active_config_source_imported)
    }
    val trafficSummary = activeConfigTrafficSummary(metadata)
    val formattedExpiry = metadata.expiresAt
        ?.takeIf { it.isNotBlank() }
        ?.let(::formatMetadataExpiry)
    val sourceBadgeColor = when (metadata.source) {
        ActiveConfigSource.SWIMVPN_MANAGED -> MaterialTheme.colorScheme.primary
        ActiveConfigSource.IMPORTED_CONFIG -> MaterialTheme.colorScheme.secondary
    }
    val sourceBadgeBackground = when (metadata.source) {
        ActiveConfigSource.SWIMVPN_MANAGED -> MaterialTheme.colorScheme.primaryContainer
        ActiveConfigSource.IMPORTED_CONFIG -> MaterialTheme.colorScheme.secondaryContainer
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata.displayName.ifBlank { stringResource(R.string.profile_unknown) },
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 22.sp
                    )
                    metadata.serverHost?.takeIf { it.isNotBlank() }?.let { host ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = host,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = sourceBadgeBackground
                ) {
                    Text(
                        text = sourceLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = sourceBadgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (trafficSummary != null || formattedExpiry != null) {
                Spacer(modifier = Modifier.height(22.dp))
                ActiveConfigStatusGrid(
                    trafficSummary = trafficSummary,
                    formattedExpiry = formattedExpiry
                )
            }

            metadata.providerName
                ?.takeIf { it.isNotBlank() }
                ?.let { providerName ->
                Spacer(modifier = Modifier.height(20.dp))
                MetadataRow(
                    label = stringResource(R.string.active_config_provider),
                    value = providerName
                )
            }

            metadata.availabilityStatus
                ?.takeIf { it.isNotBlank() }
                ?.let { availabilityStatus ->
                    Spacer(modifier = Modifier.height(16.dp))
                    MetadataRow(
                        label = stringResource(R.string.active_config_availability),
                        value = localizedAvailabilityStatus(availabilityStatus)
                    )
                }

            metadata.loadPercent?.let { loadPercent ->
                Spacer(modifier = Modifier.height(16.dp))
                MetadataRow(
                    label = stringResource(R.string.active_config_load),
                    value = stringResource(R.string.active_config_load_percent, loadPercent)
                )
            }

            metadata.protocol?.takeIf { it.isNotBlank() }?.let { protocol ->
                Spacer(modifier = Modifier.height(16.dp))
                MetadataRow(
                    label = stringResource(R.string.active_config_protocol),
                    value = protocol.uppercase(Locale.getDefault())
                )
            }
        }
    }
}

@Composable
private fun localizedAvailabilityStatus(status: String): String {
    return when (normalizeAvailabilityStatus(status)) {
        "CONGESTED" -> stringResource(R.string.active_config_availability_congested)
        "AVAILABLE" -> stringResource(R.string.active_config_availability_available)
        else -> status
    }
}

internal fun normalizeAvailabilityStatus(status: String): String {
    return status.trim().uppercase(Locale.ROOT)
}

@Composable
private fun ActiveConfigStatusGrid(
    trafficSummary: ActiveConfigTrafficSummary?,
    formattedExpiry: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        trafficSummary?.let { summary ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(summary.labelRes),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    letterSpacing = 0.4.sp
                )
                summary.percentageLabel?.let { percentageLabel ->
                    Text(
                        text = percentageLabel,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary.primaryValue,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            )
            summary.progress?.let { progress ->
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }
            summary.remainingBytes?.let { remainingBytes ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${stringResource(R.string.label_left)}: ${formatQuotaBytes(remainingBytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (trafficSummary != null && formattedExpiry != null) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))
        }

        formattedExpiry?.let { expiry ->
            Text(
                text = stringResource(R.string.active_config_expiration),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = expiry,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun UsageStat(
    title: String,
    value: String,
) {
    Column {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        )
    }
}

@Composable
fun TrialActivationProfileScreen(
    userNumber: String,
    email: String?,
    phone: String?,
    trialEligible: Boolean,
    onActivateTrial: (String, String) -> Unit,
    onContinueFreemium: (String?, String?) -> Unit,
    onBack: () -> Unit,
) {
    var emailInput by remember { mutableStateOf(email.orEmpty()) }
    var phoneInput by remember { mutableStateOf(phone.orEmpty()) }
    val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput.trim()).matches()
    val isPhoneValid = phoneInput.filter { it.isDigit() }.length >= 8
    val canCompleteProfile = isEmailValid && isPhoneValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(R.string.title_account),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.profile_trial_badge),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(stringResource(R.string.profile_trial_id_label), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.sp)
                Text(userNumber, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp)

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(R.string.profile_trial_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text(stringResource(R.string.profile_email_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text(stringResource(R.string.profile_phone_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onActivateTrial(emailInput.trim(), phoneInput.trim()) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    enabled = trialEligible && isEmailValid && isPhoneValid,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.profile_activate_trial), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        onContinueFreemium(
                            emailInput.trim().takeIf { isEmailValid },
                            phoneInput.trim().takeIf { isPhoneValid },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    enabled = canCompleteProfile,
                ) {
                    Text(
                        stringResource(R.string.profile_continue_without_trial),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (trialEligible) {
                        stringResource(R.string.profile_trial_eligible_note)
                    } else {
                        stringResource(R.string.profile_trial_ineligible_note)
                    },
                    color = if (trialEligible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun calculateRemainingTime(
    expiryDateStr: String?,
    unknownLabel: String,
    expiredAgoFormat: String,
    daysLeftFormat: String,
    hoursLeftFormat: String,
    expiringSoonLabel: String
): String {
    if (expiryDateStr.isNullOrEmpty()) return unknownLabel
    
    return try {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        val expiryDate = dateFormat.parse(expiryDateStr)
        val now = Date()
        
        val diffMillis = (expiryDate?.time ?: 0L) - now.time
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis) % 24
        
        when {
            diffMillis < 0 -> String.format(Locale.getDefault(), expiredAgoFormat, -days)
            days > 0 -> String.format(Locale.getDefault(), daysLeftFormat, days)
            hours > 0 -> String.format(Locale.getDefault(), hoursLeftFormat, hours)
            else -> expiringSoonLabel
        }
    } catch (_: Exception) {
        expiryDateStr.take(10)
    }
}

private fun profileBadgeText(profile: AccessProfileResponse, context: android.content.Context): String =
    when (profile.normalizedEntitlementState) {
        "PROFILE_INCOMPLETE" -> context.getString(R.string.profile_status_complete_profile)
        "TRIAL_AVAILABLE" -> context.getString(R.string.profile_status_trial_available)
        "PENDING_FULFILLMENT" -> context.getString(R.string.profile_status_pending_fulfillment)
        "FREEMIUM", "EXPIRED_TRIAL", "EXPIRED_SUBSCRIPTION" -> context.getString(R.string.profile_status_standard)
        "ACTIVE_TRIAL" -> context.getString(R.string.profile_status_trial_active)
        "ACTIVE_SUBSCRIPTION" -> context.getString(R.string.profile_status_paid_active)
        else -> context.getString(R.string.profile_status_standard)
    }

private fun profileStatusText(profile: AccessProfileResponse, context: android.content.Context): String =
    when (profile.normalizedEntitlementState) {
        "PROFILE_INCOMPLETE" -> context.getString(R.string.profile_status_complete_profile)
        "TRIAL_AVAILABLE" -> context.getString(R.string.profile_status_trial_available)
        "PENDING_FULFILLMENT" -> context.getString(R.string.profile_status_pending_fulfillment)
        "FREEMIUM", "EXPIRED_TRIAL", "EXPIRED_SUBSCRIPTION" -> context.getString(R.string.profile_status_standard)
        "ACTIVE_TRIAL" -> context.getString(R.string.profile_status_trial_active)
        "ACTIVE_SUBSCRIPTION" -> profileLocalizedPlanName(profile, context)?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.profile_status_offer_active, it) }
            ?: context.getString(R.string.profile_status_paid_active)
        else -> context.getString(R.string.profile_status_standard)
    }

private fun profileLocalizedPlanName(
    profile: AccessProfileResponse,
    context: android.content.Context,
): String? =
    when (profile.publicPlanName ?: profile.offerCode) {
        "Basic", "WEEK" -> context.getString(R.string.plan_basic)
        "Premium", "MONTH" -> context.getString(R.string.plan_premium)
        "Platinum", "QUARTER" -> context.getString(R.string.plan_platinum)
        else -> null
    }

@Composable
private fun profileBadgeColor(profile: AccessProfileResponse): Color =
    when (profile.normalizedEntitlementState) {
        "PROFILE_INCOMPLETE" -> MaterialTheme.colorScheme.error
        "TRIAL_AVAILABLE" -> MaterialTheme.colorScheme.primary
        "PENDING_FULFILLMENT" -> MaterialTheme.colorScheme.primary
        "FREEMIUM", "EXPIRED_TRIAL", "EXPIRED_SUBSCRIPTION" -> MaterialTheme.colorScheme.primary
        "ACTIVE_TRIAL", "ACTIVE_SUBSCRIPTION" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun profileBadgeBackground(profile: AccessProfileResponse): Color =
    when (profile.normalizedEntitlementState) {
        "PROFILE_INCOMPLETE" -> MaterialTheme.colorScheme.errorContainer
        "TRIAL_AVAILABLE" -> MaterialTheme.colorScheme.primaryContainer
        "PENDING_FULFILLMENT" -> MaterialTheme.colorScheme.primaryContainer
        "FREEMIUM", "EXPIRED_TRIAL", "EXPIRED_SUBSCRIPTION" -> MaterialTheme.colorScheme.primaryContainer
        "ACTIVE_TRIAL", "ACTIVE_SUBSCRIPTION" -> MaterialTheme.colorScheme.primaryContainer
      else -> MaterialTheme.colorScheme.surfaceVariant
  }

private fun formatUsagePercentage(progress: Float): String {
    if (progress <= 0f) return "0%"
    val percentage = progress * 100f
    return when {
        percentage < 0.1f -> "<0.1%"
        percentage < 1f -> String.format(Locale.US, "%.1f%%", percentage)
        else -> String.format(Locale.US, "%.0f%%", percentage)
    }
}

private fun formatQuotaBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = 1024.0 * 1024.0 * 1024.0
    val mb = 1024.0 * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        else -> formatBytes(bytes)
    }
}

private data class ActiveConfigTrafficSummary(
    val labelRes: Int,
    val primaryValue: String,
    val remainingBytes: Long? = null,
    val progress: Float? = null,
    val percentageLabel: String? = null,
)

private fun activeConfigTrafficSummary(metadata: ActiveConfigMetadata): ActiveConfigTrafficSummary? {
    val usedBytes = metadata.trafficUsedBytes
    val totalBytes = metadata.trafficTotalBytes
    val used = usedBytes?.let(::formatQuotaBytes)
    val total = totalBytes?.let(::formatQuotaBytes)

    return when {
        usedBytes != null && totalBytes != null && totalBytes > 0L -> {
            val progress = (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            val remaining = (totalBytes - usedBytes).coerceAtLeast(0L)
            ActiveConfigTrafficSummary(
                labelRes = R.string.active_config_quota,
                primaryValue = "${formatQuotaBytes(usedBytes)} / ${formatQuotaBytes(totalBytes)}",
                remainingBytes = remaining,
                progress = progress,
                percentageLabel = formatUsagePercentage(progress)
            )
        }
        total != null -> ActiveConfigTrafficSummary(
            labelRes = R.string.active_config_quota,
            primaryValue = total
        )
        used != null -> ActiveConfigTrafficSummary(
            labelRes = R.string.active_config_usage,
            primaryValue = used
        )
        else -> null
    }
}

private fun formatMetadataExpiry(expiresAt: String): String {
    if (hasDateOnlySemantics(expiresAt)) {
        return expiresAt.take(DATE_ONLY_LENGTH)
    }
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    for (pattern in formats) {
        val formatted = runCatching {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsedDate = parser.parse(expiresAt) ?: return@runCatching null
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(parsedDate)
        }.getOrNull()
        if (formatted != null) {
            return formatted
        }
    }
    return expiresAt
}

private fun hasDateOnlySemantics(expiresAt: String): Boolean {
    return DATE_ONLY_PATTERN.matches(expiresAt) || MIDNIGHT_TIMESTAMP_PATTERN.matches(expiresAt)
}

private const val DATE_ONLY_LENGTH = 10
private val DATE_ONLY_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
private val MIDNIGHT_TIMESTAMP_PATTERN = Regex("""\d{4}-\d{2}-\d{2}T00:00:00(?:\.0{1,9})?(?:Z|[+-]\d{2}:\d{2})?""")

@Composable
fun ManagementRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
