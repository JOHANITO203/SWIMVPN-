package com.swimvpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.components.drawSwimLightCardTexture
import com.swimvpn.app.ui.theme.LocalSwimVisualTokens
import com.swimvpn.app.ui.theme.SwimDesignTokens
import java.util.*

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

internal fun normalizeAvailabilityStatus(status: String): String {
    return status.trim().uppercase(Locale.ROOT)
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

    val scrollState = rememberScrollState()
    val canActivate = trialEligible && isEmailValid && isPhoneValid
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SwimDesignTokens.Color.PurpleActive,
        unfocusedBorderColor = SwimDesignTokens.Color.StrokeSubtle,
        focusedTextColor = SwimDesignTokens.Color.TextPrimary,
        unfocusedTextColor = SwimDesignTokens.Color.TextPrimary,
        cursorColor = SwimDesignTokens.Color.PurpleActive,
        focusedLabelColor = SwimDesignTokens.Color.PurpleActive,
        unfocusedLabelColor = SwimDesignTokens.Color.TextMuted,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                HardwareAccountButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_desc_back),
                        tint = SwimDesignTokens.Color.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.title_account),
                    color = SwimDesignTokens.Color.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }

            AccountCanvas {
                AccountStatusChip(
                    text = stringResource(R.string.profile_trial_badge),
                    color = SwimDesignTokens.Color.SuccessGreen,
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.profile_trial_id_label),
                    color = SwimDesignTokens.Color.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = userNumber,
                    color = SwimDesignTokens.Color.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.profile_trial_description),
                    color = SwimDesignTokens.Color.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )

                Spacer(modifier = Modifier.height(22.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text(stringResource(R.string.profile_email_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = SwimDesignTokens.Shape.Control,
                    colors = fieldColors,
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text(stringResource(R.string.profile_phone_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = SwimDesignTokens.Shape.Control,
                    colors = fieldColors,
                )

                Spacer(modifier = Modifier.height(22.dp))

                TrialPrimaryCta(
                    label = stringResource(R.string.profile_activate_trial),
                    enabled = canActivate,
                    onClick = { onActivateTrial(emailInput.trim(), phoneInput.trim()) },
                )

                Spacer(modifier = Modifier.height(12.dp))

                TrialSecondaryCta(
                    label = stringResource(R.string.profile_continue_without_trial),
                    enabled = canCompleteProfile,
                    onClick = {
                        onContinueFreemium(
                            emailInput.trim().takeIf { isEmailValid },
                            phoneInput.trim().takeIf { isPhoneValid },
                        )
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (trialEligible) {
                        stringResource(R.string.profile_trial_eligible_note)
                    } else {
                        stringResource(R.string.profile_trial_ineligible_note)
                    },
                    color = if (trialEligible) SwimDesignTokens.Color.TextSecondary else SwimDesignTokens.Color.Danger,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun TrialPrimaryCta(label: String, enabled: Boolean, onClick: () -> Unit) {
    val background = if (enabled) {
        Brush.verticalGradient(
            listOf(
                SwimDesignTokens.Material.PurpleCoreTop,
                SwimDesignTokens.Material.PurpleCoreMid,
                SwimDesignTokens.Material.PurpleCoreBottom,
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(SwimDesignTokens.Material.ShellMid, SwimDesignTokens.Material.ShellBottom)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .then(
                if (enabled) {
                    Modifier.shadow(
                        SwimDesignTokens.Shadow.HardwareButton,
                        SwimDesignTokens.Shape.Pill,
                        clip = false,
                        spotColor = SwimDesignTokens.Color.PurplePrimary,
                    )
                } else {
                    Modifier
                }
            )
            .clip(SwimDesignTokens.Shape.Pill)
            .background(background)
            .border(
                1.dp,
                if (enabled) SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.5f) else SwimDesignTokens.Color.StrokeSubtle,
                SwimDesignTokens.Shape.Pill,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else SwimDesignTokens.Color.TextMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun TrialSecondaryCta(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(SwimDesignTokens.Material.BowlMid.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (enabled) SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.34f) else SwimDesignTokens.Color.StrokeSubtle,
                SwimDesignTokens.Shape.Pill,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) SwimDesignTokens.Color.TextPrimary else SwimDesignTokens.Color.TextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun profileBadgeText(profile: AccessProfileResponse, context: android.content.Context): String =
    when (profile.normalizedEntitlementState) {
        "PROFILE_INCOMPLETE" -> context.getString(R.string.profile_status_complete_profile)
        "TRIAL_AVAILABLE" -> context.getString(R.string.profile_status_trial_available)
        "PENDING_FULFILLMENT" -> context.getString(R.string.profile_status_pending_fulfillment)
        "FREEMIUM", "EXPIRED_TRIAL", "EXPIRED_SUBSCRIPTION" -> context.getString(R.string.profile_status_standard)
        "ACTIVE_TRIAL" -> context.getString(R.string.profile_status_trial_active)
        "ACTIVE_SUBSCRIPTION" -> profileLocalizedPlanName(profile, context)?.takeIf { it.isNotBlank() }
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

