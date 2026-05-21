package com.swimvpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.theme.SwimDesignTokens

private data class OnboardingStepUi(
    val title: String,
    val description: String,
    val icon: ImageVector,
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var currentStep by remember { mutableStateOf(0) }
    val steps = listOf(
        OnboardingStepUi(stringResource(R.string.ob_step1_title), stringResource(R.string.ob_step1_desc), Icons.Default.Lock),
        OnboardingStepUi(stringResource(R.string.ob_step2_title), stringResource(R.string.ob_step2_desc), Icons.Default.PlayArrow),
        OnboardingStepUi(stringResource(R.string.ob_step3_title), stringResource(R.string.ob_step3_desc), Icons.Rounded.CheckCircle),
    )
    val step = steps[currentStep]

    SwimDarkLuxuryBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(SwimDesignTokens.Shadow.HardwareSurface, SwimDesignTokens.Shape.LargeHardwareCard, clip = false)
                    .clip(SwimDesignTokens.Shape.LargeHardwareCard)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                SwimDesignTokens.Color.SurfaceHighlight.copy(alpha = 0.82f),
                                SwimDesignTokens.Color.SurfaceBase.copy(alpha = 0.96f),
                                SwimDesignTokens.Color.BackgroundDeep.copy(alpha = 0.99f),
                            )
                        )
                    )
                    .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.LargeHardwareCard)
                    .padding(horizontal = 26.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardingIcon(icon = step.icon)

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = step.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = SwimDesignTokens.Color.TextPrimary,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SwimDesignTokens.Color.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }

            Spacer(modifier = Modifier.height(34.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                steps.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentStep) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentStep) {
                                    SwimDesignTokens.Color.PurpleActive
                                } else {
                                    SwimDesignTokens.Color.TextMuted.copy(alpha = 0.34f)
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (currentStep < steps.lastIndex) {
                        currentStep++
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = SwimDesignTokens.Shape.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SwimDesignTokens.Color.PurplePrimary,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = if (currentStep < steps.lastIndex) {
                        stringResource(R.string.onboarding_next)
                    } else {
                        stringResource(R.string.onboarding_start)
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OnboardingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.34f),
                        SwimDesignTokens.Color.SurfaceElevated.copy(alpha = 0.92f),
                        SwimDesignTokens.Color.BackgroundDeep.copy(alpha = 0.98f),
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.30f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(50.dp),
            tint = SwimDesignTokens.Color.PurpleActive,
        )
    }
}
