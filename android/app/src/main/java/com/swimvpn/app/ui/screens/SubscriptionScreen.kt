package com.swimvpn.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.swimvpn.app.R
import com.swimvpn.app.ui.theme.SwimBlueMain
import com.swimvpn.app.ui.theme.SwimNavyMouth

@Composable
fun SubscriptionScreen(onUpgradeClick: (planId: String) -> Unit, onBack: () -> Unit = {}) {
    var selectedPlan by remember { mutableStateOf("monthly") }
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
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SwimNavyMouth)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(stringResource(R.string.title_pricing), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = SwimNavyMouth))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.sub_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                color = SwimNavyMouth,
                lineHeight = 40.sp
            )
        )
        Text(
            text = stringResource(R.string.sub_desc),
            color = Color(0xFF64748B),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Plans
        PlanCard(
            title = stringResource(R.string.plan_weekly),
            price = "299 ₽",
            data = "50 GB",
            isSelected = selectedPlan == "weekly",
            badge = "BRONZE",
            badgeColor = Color(0xFFCD7F32),
            onClick = { selectedPlan = "weekly" }
        )
        Spacer(modifier = Modifier.height(16.dp))
        PlanCard(
            title = stringResource(R.string.plan_monthly),
            price = "699 ₽",
            data = "150 GB",
            isSelected = selectedPlan == "monthly",
            badge = "SILVER",
            badgeColor = Color(0xFF94A3B8),
            onClick = { selectedPlan = "monthly" }
        )
        Spacer(modifier = Modifier.height(16.dp))
        PlanCard(
            title = stringResource(R.string.plan_annual),
            price = "1899 ₽",
            data = "500 GB",
            isSelected = selectedPlan == "quarterly",
            badge = "GOLD",
            badgeColor = Color(0xFFFFD700),
            onClick = { selectedPlan = "quarterly" }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Features
        FeatureItem(stringResource(R.string.feature_1))
        FeatureItem(stringResource(R.string.feature_2))
        FeatureItem(stringResource(R.string.feature_3))
        FeatureItem(stringResource(R.string.feature_4))

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onUpgradeClick(selectedPlan) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SwimBlueMain)
        ) {
            Text(stringResource(R.string.btn_upgrade), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PlanCard(
    title: String,
    price: String,
    data: String,
    isSelected: Boolean,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color.White else Color(0xFFF1F5F9)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) SwimBlueMain else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp, color = SwimNavyMouth, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Star, contentDescription = null, tint = badgeColor, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(badge, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(price, fontWeight = FontWeight.Black, color = SwimNavyMouth, fontSize = 24.sp)
                    Text(stringResource(R.string.label_period), color = Color(0xFF64748B), fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(data, fontWeight = FontWeight.Black, color = SwimBlueMain, fontSize = 18.sp)
                Text(stringResource(R.string.label_traffic), color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FeatureItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp).background(SwimBlueMain.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = SwimBlueMain, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = SwimNavyMouth, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
