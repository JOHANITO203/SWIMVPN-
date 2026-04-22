package com.swimvpn.app.ui.screens

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.data.model.Plan
import com.swimvpn.app.ui.theme.SwimBlueMain
import com.swimvpn.app.ui.theme.SwimNavyMouth
import java.math.RoundingMode

@Composable
fun SubscriptionScreen(
    plans: List<Plan>,
    onUpgradeClick: (planId: String) -> Unit,
    onBack: () -> Unit = {}
) {
    var selectedPlanId by remember { mutableStateOf(plans.firstOrNull()?.id ?: "") }
    val scrollState = rememberScrollState()

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
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SwimNavyMouth)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(R.string.title_pricing),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = SwimNavyMouth,
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.sub_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                color = SwimNavyMouth,
                lineHeight = 40.sp,
            )
        )
        Text(
            text = stringResource(R.string.sub_desc),
            color = Color(0xFF64748B),
            fontSize = 16.sp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        plans.forEach { plan ->
            val badgeColor = when (plan.code) {
                "WEEK" -> Color(0xFFCD7F32)
                "MONTH" -> Color(0xFF94A3B8)
                "QUARTER" -> Color(0xFFFFD700)
                else -> Color.Gray
            }

            PlanCard(
                title = plan.name,
                price = formatPlanPrice(plan.priceRub),
                quota = plan.quotaLabel,
                duration = plan.durationLabel,
                isSelected = selectedPlanId == plan.id,
                badge = localizedPlanCode(plan.code),
                badgeColor = badgeColor,
                onClick = { selectedPlanId = plan.id }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (plans.isEmpty()) {
            Text(
                text = stringResource(R.string.no_plans_available),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                textAlign = TextAlign.Center,
                color = Color.Gray,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FeatureItem(stringResource(R.string.feature_1))
        FeatureItem(stringResource(R.string.feature_2))
        FeatureItem(stringResource(R.string.feature_3))
        FeatureItem(stringResource(R.string.feature_4))

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { if (selectedPlanId.isNotEmpty()) onUpgradeClick(selectedPlanId) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SwimBlueMain),
            enabled = selectedPlanId.isNotEmpty()
        ) {
            Text(
                stringResource(R.string.btn_create_order),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                letterSpacing = 1.sp,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PlanCard(
    title: String,
    price: String,
    quota: String,
    duration: String,
    isSelected: Boolean,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit,
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
                    Text(
                        title,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = SwimNavyMouth,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(badge, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(duration, color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(price, fontWeight = FontWeight.Black, color = SwimNavyMouth, fontSize = 24.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(quota, fontWeight = FontWeight.Black, color = SwimBlueMain, fontSize = 18.sp)
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
            modifier = Modifier
                .size(24.dp)
                .background(SwimBlueMain.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = SwimBlueMain, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = SwimNavyMouth, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun localizedPlanCode(code: String): String = when (code) {
    "WEEK" -> stringResource(R.string.plan_code_week)
    "MONTH" -> stringResource(R.string.plan_code_month)
    "QUARTER" -> stringResource(R.string.plan_code_quarter)
    else -> code
}

private fun formatPlanPrice(priceRub: String): String {
    val normalized = priceRub.replace(',', '.')
    val amount = normalized.toBigDecimalOrNull() ?: return "$priceRub RUB"
    val stripped = amount.stripTrailingZeros()
    val display = if (stripped.scale() <= 0) {
        stripped.toPlainString()
    } else {
        stripped.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
    return "$display RUB"
}
