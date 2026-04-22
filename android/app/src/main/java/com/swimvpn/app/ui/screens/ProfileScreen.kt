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
import androidx.compose.material.icons.rounded.Bolt
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.ui.formatBytes
import com.swimvpn.app.ui.theme.SwimBlueMain
import com.swimvpn.app.ui.theme.SwimNavyMouth
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun ProfileScreen(
    profile: AccessProfileResponse, 
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
                        color = Color(0xFFFFF7ED)
                    ) {
                        Text(
                            text = profile.planType,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color(0xFFC2410C),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(stringResource(R.string.label_user_id), fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = 1.sp)
                Text(profile.userNumber, fontWeight = FontWeight.Black, color = SwimNavyMouth, fontSize = 28.sp)
                Text(profile.email ?: "-", color = Color(0xFF64748B), fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Text(stringResource(R.string.label_analytics), fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(modifier = Modifier.height(12.dp))

        // Analytics Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Data Usage Section
                val isTrial = profile.accessType == "TRIAL"
                val limitGB = profile.dataLimitGB.toDouble()
                val sessionBytes = bytesIn + bytesOut
                val totalUsedBytes = (profile.dataUsedBytes.filter { it.isDigit() }.toLongOrNull() ?: 0L) + sessionBytes
                val limitBytes = (limitGB * 1024.0 * 1024.0 * 1024.0).toLong()
                val remainingBytes = (limitBytes - totalUsedBytes).coerceAtLeast(0L)
                val hasMeasuredLimit = limitBytes > 0L
                val progress = if (!hasMeasuredLimit) 0f else (totalUsedBytes.toFloat() / limitBytes.toFloat()).coerceIn(0f, 1f)
                
                val statusColor = when {
                    isTrial -> SwimBlueMain
                    progress > 0.9f -> Color(0xFFEF4444) // Red
                    progress > 0.7f -> Color(0xFFF59E0B) // Orange
                    else -> Color(0xFF22C55E) // Green
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.label_data_traffic), fontWeight = FontWeight.Bold, color = Color(0xFF475569), fontSize = 12.sp, letterSpacing = 0.5.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = SwimBlueMain, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (!hasMeasuredLimit) stringResource(R.string.label_unlimited) else "${formatBytes(remainingBytes)} ${stringResource(R.string.label_left)}",
                                fontWeight = FontWeight.Black,
                                color = SwimNavyMouth,
                                fontSize = 18.sp
                            )
                        }
                    }
                    if (hasMeasuredLimit) {
                        Text(
                            String.format(Locale.US, "%.0f%%", progress * 100),
                            fontWeight = FontWeight.Black,
                            color = statusColor,
                            fontSize = 18.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LinearProgressIndicator(
                    progress = { if (hasMeasuredLimit) progress else 0f },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                    color = statusColor,
                    trackColor = Color(0xFFF1F5F9)
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val expiryString = if (profile.accessType == "TRIAL") profile.trialExpiresAt else profile.subscriptionExpiresAt
                    val remainingText = calculateRemainingTime(expiryString)

                    Text(stringResource(R.string.label_expires_at), fontWeight = FontWeight.Bold, color = Color(0xFF475569), fontSize = 12.sp, letterSpacing = 0.5.sp)
                    Text(
                        text = remainingText,
                        fontWeight = FontWeight.Bold, 
                        color = if (remainingText.contains("ago")) Color(0xFFEF4444) else Color(0xFF22C55E), 
                        fontSize = 12.sp, 
                        letterSpacing = 0.5.sp
                    )
                }
            }
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
                ManagementRow(icon = Icons.Outlined.LocalOffer, title = stringResource(R.string.btn_activate_coupon), onClick = onNavigateToImport)
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
                        text = "TRIAL 3 DAYS",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color(0xFFC2410C),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("Your SWIMVPN+ ID", fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), fontSize = 10.sp, letterSpacing = 1.sp)
                Text(userNumber, fontWeight = FontWeight.Black, color = SwimNavyMouth, fontSize = 28.sp)

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "Complete your profile to unlock the 3-day trial. We use your device, email, and phone to prevent abuse.",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Phone number") },
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
                    Text("Activate 3-day trial", color = Color.White, fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (trialEligible) {
                        "Your trial activates once after profile completion."
                    } else {
                        "Trial already used for this device or these contact details."
                    },
                    color = if (trialEligible) Color(0xFF64748B) else Color(0xFFDC2626),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun calculateRemainingTime(expiryDateStr: String?): String {
    if (expiryDateStr.isNullOrEmpty()) return "Unknown"
    
    return try {
        // Parse ISO 8601 date string
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        val expiryDate = dateFormat.parse(expiryDateStr)
        val now = Date()
        
        val diffMillis = (expiryDate?.time ?: 0L) - now.time
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis) % 24
        
        when {
            diffMillis < 0 -> "Expired ${-days}d ago"
            days > 0 -> "${days} days left"
            hours > 0 -> "${hours} hours left"
            else -> "Expiring soon"
        }
    } catch (_: Exception) {
        // Fallback: just show the date part
        expiryDateStr.take(10)
    }
}

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
