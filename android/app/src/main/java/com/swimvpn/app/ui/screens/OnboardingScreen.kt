package com.swimvpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.swimvpn.app.R
import com.swimvpn.app.ui.theme.ElectricBlue

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var currentStep by remember { mutableStateOf(0) }

    val steps = listOf(
        Triple(stringResource(R.string.ob_step1_title), stringResource(R.string.ob_step1_desc), Icons.Default.Lock),
        Triple(stringResource(R.string.ob_step2_title), stringResource(R.string.ob_step2_desc), Icons.Default.PlayArrow),
        Triple(stringResource(R.string.ob_step3_title), stringResource(R.string.ob_step3_desc), Icons.Rounded.CheckCircle)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(ElectricBlue.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = steps[currentStep].third,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = ElectricBlue
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Title
        Text(
            text = steps[currentStep].first,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = steps[currentStep].second,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // Pagination Dots
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in steps.indices) {
                Box(
                    modifier = Modifier
                        .size(if (i == currentStep) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == currentStep) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Next / Start Button
        Button(
            onClick = {
                if (currentStep < steps.size - 1) {
                    currentStep++
                } else {
                    onFinish()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = if (currentStep < steps.size - 1) stringResource(R.string.onboarding_next) else stringResource(R.string.onboarding_start),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
        }
    }
}
