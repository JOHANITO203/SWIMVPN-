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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
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
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun SubscriptionScreen(
    plans: List<Plan>,
    paymentEmail: String?,
    onCheckoutClick: (planId: String, paymentMethod: String) -> Unit,
    onBack: () -> Unit = {}
) {
    val visiblePlans = remember(plans) {
        plans.filter { plan ->
            plan.priceRub.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 1
        }
    }
    var selectedPlanId by remember(visiblePlans) { mutableStateOf(visiblePlans.firstOrNull()?.id ?: "") }
    var selectedPaymentMethod by remember { mutableStateOf("CARD_MANUAL") }
    var showEmailConfirmation by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val normalizedPaymentEmail = paymentEmail?.trim().orEmpty()

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
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(R.string.title_pricing),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.sub_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 40.sp,
            )
        )
        Text(
            text = stringResource(R.string.sub_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        visiblePlans.forEach { plan ->
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
                deviceAllowance = stringResource(R.string.plan_devices_up_to_two),
                isSelected = selectedPlanId == plan.id,
                badge = localizedPlanCode(plan.code),
                badgeColor = badgeColor,
                onClick = { selectedPlanId = plan.id }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (visiblePlans.isEmpty()) {
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

        Text(
            text = stringResource(R.string.payment_method_title),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PaymentMethodCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.payment_method_card),
                isSelected = selectedPaymentMethod == "CARD_MANUAL",
                onClick = { selectedPaymentMethod = "CARD_MANUAL" }
            )
            PaymentMethodCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.payment_method_swimpay),
                isSelected = selectedPaymentMethod == "SWIMPAY",
                onClick = { selectedPaymentMethod = "SWIMPAY" }
            )
            PaymentMethodCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.payment_method_crypto),
                isSelected = selectedPaymentMethod == "CRYPTO",
                onClick = { selectedPaymentMethod = "CRYPTO" }
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { if (selectedPlanId.isNotEmpty()) showEmailConfirmation = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            enabled = selectedPlanId.isNotEmpty()
        ) {
            Text(
                stringResource(R.string.btn_create_order),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                letterSpacing = 1.sp,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (showEmailConfirmation) {
            AlertDialog(
                onDismissRequest = { showEmailConfirmation = false },
                title = {
                    Text(
                        text = stringResource(R.string.payment_confirm_email_title),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.payment_confirm_email_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = normalizedPaymentEmail.ifBlank { stringResource(R.string.payment_confirm_email_missing) },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showEmailConfirmation = false
                            onCheckoutClick(selectedPlanId, selectedPaymentMethod)
                        },
                        enabled = selectedPlanId.isNotEmpty() && normalizedPaymentEmail.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text(stringResource(R.string.payment_confirm_email_confirm), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showEmailConfirmation = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Text(stringResource(R.string.payment_confirm_email_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }

    }
}

@Composable
private fun PaymentMethodCard(
    modifier: Modifier = Modifier,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun PlanCard(
    title: String,
    price: String,
    quota: String,
    duration: String,
    deviceAllowance: String,
    isSelected: Boolean,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
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
                        color = MaterialTheme.colorScheme.onSurface,
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
                Text(duration, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(deviceAllowance, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(price, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(quota, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                Text(stringResource(R.string.label_traffic), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
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
