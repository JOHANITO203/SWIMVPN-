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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.config.ActiveConfigMetadata
import com.swimvpn.app.config.ActiveConfigSource
import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.ui.formatBytes
import com.swimvpn.app.ui.theme.SwimBlueMain
import com.swimvpn.app.ui.theme.SwimNavyMouth
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
    onSignOut: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val badgeText = profileBadgeText(profile, context)
    val badgeColor = profileBadgeColor(profile)
    val badgeBackground = profileBadgeBackground(profile)
    val localizedPlanName = profileLocalizedPlanName(profile, context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        // Top Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .clickable { onBack?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SwimNavyMouth)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(R.string.title_account),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = SwimNavyMouth)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // User Identification Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFF1F5F9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(32.dp))
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = badgeBackground
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(stringResource(R.string.label_user_id), fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = 1.sp)
                Text(profile.userNumber, fontWeight = FontWeight.Black, color = SwimNavyMouth, fontSize = 28.sp)
                val contactLines = buildList {
                    profile.email?.takeIf { it.isNotBlank() }?.let {
                        add(context.getString(R.string.profile_email_format, it))
                    }
                    profile.phone?.takeIf { it.isNotBlank() }?.let {
                        add(context.getString(R.string.profile_phone_format, it))
                    }
                }
                if (contactLines.isEmpty()) {
                    Text(
                        stringResource(R.string.profile_contact_missing),
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                } else {
                    contactLines.forEach { line ->
                        Text(line, color = Color(0xFF64748B), fontSize = 14.sp)
                    }
                }
                localizedPlanName?.takeIf { it.isNotBlank() && profile.accessType != "TRIAL" }?.let { planName ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.profile_offer_format, planName),
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        SectionLabel(title = stringResource(R.string.profile_section_swimvpn_access))
        Spacer(modifier = Modifier.height(12.dp))
        SwimVpnAccessCard(profile = profile, badgeColor = badgeColor)

        if (activeConfigMetadata != null) {
            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel(title = stringResource(R.string.profile_section_active_config))
            Spacer(modifier = Modifier.height(12.dp))
            ActiveConfigCard(metadata = activeConfigMetadata)
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Text(stringResource(R.string.label_management), fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(modifier = Modifier.height(12.dp))

        // Management List
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
        ) {
            Column {
                ManagementRow(icon = Icons.Outlined.CreditCard, title = stringResource(R.string.menu_subscription), onClick = onNavigateToSubscription)
                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 24.dp))
                ManagementRow(icon = Icons.Outlined.LocalOffer, title = stringResource(R.string.menu_import_access), onClick = onNavigateToImport)
                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 24.dp))
                ManagementRow(icon = Icons.Outlined.Settings, title = stringResource(R.string.menu_technical), onClick = onNavigateToTechnical)
                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 24.dp))
                ManagementRow(icon = Icons.AutoMirrored.Outlined.HelpOutline, title = stringResource(R.string.menu_support), onClick = onNavigateToSupport)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Sign Out Button
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = SwimNavyMouth),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Text(stringResource(R.string.menu_signout), fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF94A3B8),
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
    val isTrial = profile.accessType == "TRIAL"
    val hasMeasuredLimit = profile.hasMeasuredLimit
    val quotaValue = if (hasMeasuredLimit) formatBytes(profile.dataLimitBytes) else stringResource(R.string.label_unlimited)
    val measuredUsedBytes = profile.parsedDataUsedBytes
    val remainingBytes = if (hasMeasuredLimit) {
        (profile.dataLimitBytes - measuredUsedBytes).coerceAtLeast(0L)
    } else {
        0L
    }
    val progress = if (hasMeasuredLimit && profile.dataLimitBytes > 0) {
        (measuredUsedBytes.toFloat() / profile.dataLimitBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val statusColor = when {
        isTrial -> SwimBlueMain
        profile.isExpired -> Color(0xFFEF4444)
        else -> Color(0xFF22C55E)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.profile_metric_plan_quota),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569),
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = quotaValue,
                        fontWeight = FontWeight.Black,
                        color = SwimNavyMouth,
                        fontSize = 24.sp
                    )
                }
                if (hasMeasuredLimit) {
                    Text(
                        text = formatUsagePercentage(progress),
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = statusColor,
                trackColor = Color(0xFFF1F5F9)
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
                    value = if (hasMeasuredLimit) formatQuotaBytes(remainingBytes) else stringResource(R.string.label_unlimited)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0))
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
                        color = Color(0xFF475569),
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
                        color = Color(0xFF475569),
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = exactExpirationText,
                        fontWeight = FontWeight.Bold,
                        color = if (profile.isExpired) Color(0xFFEF4444) else Color(0xFF22C55E),
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    if (exactExpirationText != context.getString(R.string.profile_unknown)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = expirationText,
                            color = Color(0xFF64748B),
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
        ActiveConfigSource.SWIMVPN_MANAGED -> SwimBlueMain
        ActiveConfigSource.IMPORTED_CONFIG -> Color(0xFF7C3AED)
    }
    val sourceBadgeBackground = when (metadata.source) {
        ActiveConfigSource.SWIMVPN_MANAGED -> Color(0xFFEFF6FF)
        ActiveConfigSource.IMPORTED_CONFIG -> Color(0xFFF5F3FF)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
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
                        color = SwimNavyMouth,
                        fontSize = 22.sp
                    )
                    metadata.serverHost?.takeIf { it.isNotBlank() }?.let { host ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = host,
                            color = Color(0xFF64748B),
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

            metadata.providerName?.takeIf { it.isNotBlank() }?.let { providerName ->
                Spacer(modifier = Modifier.height(20.dp))
                MetadataRow(
                    label = stringResource(R.string.active_config_provider),
                    value = providerName
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
private fun ActiveConfigStatusGrid(
    trafficSummary: ActiveConfigTrafficSummary?,
    formattedExpiry: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), RoundedCornerShape(18.dp))
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
                    color = Color(0xFF475569),
                    fontSize = 12.sp,
                    letterSpacing = 0.4.sp
                )
                summary.percentageLabel?.let { percentageLabel ->
                    Text(
                        text = percentageLabel,
                        fontWeight = FontWeight.Black,
                        color = SwimBlueMain,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary.primaryValue,
                fontWeight = FontWeight.Black,
                color = SwimNavyMouth,
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
                    color = SwimBlueMain,
                    trackColor = Color(0xFFE2E8F0)
                )
            }
            summary.remainingBytes?.let { remainingBytes ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${stringResource(R.string.label_left)}: ${formatQuotaBytes(remainingBytes)}",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (trafficSummary != null && formattedExpiry != null) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Spacer(modifier = Modifier.height(16.dp))
        }

        formattedExpiry?.let { expiry ->
            Text(
                text = stringResource(R.string.active_config_expiration),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569),
                fontSize = 12.sp,
                letterSpacing = 0.4.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = expiry,
                fontWeight = FontWeight.Black,
                color = SwimNavyMouth,
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
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            letterSpacing = 0.4.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            color = SwimNavyMouth,
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
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            letterSpacing = 0.4.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Black,
            color = SwimNavyMouth,
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
    onBack: () -> Unit,
) {
    var emailInput by remember { mutableStateOf(email.orEmpty()) }
    var phoneInput by remember { mutableStateOf(phone.orEmpty()) }
    val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput.trim()).matches()
    val isPhoneValid = phoneInput.filter { it.isDigit() }.length >= 8

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SwimNavyMouth)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(R.string.title_account),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = SwimNavyMouth)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFF7ED)
                ) {
                    Text(
                        text = stringResource(R.string.profile_trial_badge),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color(0xFFC2410C),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(stringResource(R.string.profile_trial_id_label), fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = 1.sp)
                Text(userNumber, fontWeight = FontWeight.Black, color = SwimNavyMouth, fontSize = 28.sp)

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(R.string.profile_trial_description),
                    color = Color(0xFF64748B),
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
                    colors = ButtonDefaults.buttonColors(containerColor = SwimBlueMain)
                ) {
                    Text(stringResource(R.string.profile_activate_trial), color = Color.White, fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (trialEligible) {
                        stringResource(R.string.profile_trial_eligible_note)
                    } else {
                        stringResource(R.string.profile_trial_ineligible_note)
                    },
                    color = if (trialEligible) Color(0xFF64748B) else Color(0xFFDC2626),
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
    when (profile.status) {
        "PROFILE_INCOMPLETE" -> context.getString(R.string.profile_status_complete_profile)
        "TRIAL_AVAILABLE" -> context.getString(R.string.profile_status_trial_available)
        "EXPIRED" -> context.getString(R.string.profile_status_expired)
        "ACTIVE" -> {
            if (profile.accessType == "TRIAL") {
                context.getString(R.string.profile_status_trial_active)
            } else {
                context.getString(R.string.profile_status_paid_active)
            }
        }
        else -> context.getString(R.string.profile_status_inactive)
    }

private fun profileStatusText(profile: AccessProfileResponse, context: android.content.Context): String =
    when (profile.status) {
        "PROFILE_INCOMPLETE" -> context.getString(R.string.profile_status_complete_profile)
        "TRIAL_AVAILABLE" -> context.getString(R.string.profile_status_trial_available)
        "EXPIRED" -> context.getString(R.string.profile_status_expired)
        "ACTIVE" -> {
            if (profile.accessType == "TRIAL") {
                context.getString(R.string.profile_status_trial_active)
            } else {
                profileLocalizedPlanName(profile, context)?.takeIf { it.isNotBlank() }
                    ?.let { context.getString(R.string.profile_status_offer_active, it) }
                    ?: context.getString(R.string.profile_status_paid_active)
            }
        }
        else -> context.getString(R.string.profile_status_inactive)
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

private fun profileBadgeColor(profile: AccessProfileResponse): Color =
    when (profile.status) {
        "PROFILE_INCOMPLETE" -> Color(0xFFB45309)
        "TRIAL_AVAILABLE" -> SwimBlueMain
        "EXPIRED" -> Color(0xFFDC2626)
        "ACTIVE" -> if (profile.accessType == "TRIAL") SwimBlueMain else Color(0xFF15803D)
        else -> Color(0xFF64748B)
    }

private fun profileBadgeBackground(profile: AccessProfileResponse): Color =
    when (profile.status) {
        "PROFILE_INCOMPLETE" -> Color(0xFFFFFBEB)
        "TRIAL_AVAILABLE" -> Color(0xFFEFF6FF)
        "EXPIRED" -> Color(0xFFFEF2F2)
        "ACTIVE" -> if (profile.accessType == "TRIAL") Color(0xFFEFF6FF) else Color(0xFFF0FDF4)
      else -> Color(0xFFF8FAFC)
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
        runCatching {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsedDate = parser.parse(expiresAt) ?: return@runCatching null
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(parsedDate)
        }.getOrNull()?.let { return it }
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
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF475569), fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF94A3B8))
    }
}
