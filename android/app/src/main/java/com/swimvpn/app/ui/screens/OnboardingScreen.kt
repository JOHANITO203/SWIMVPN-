package com.swimvpn.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.ui.theme.SwimDesignTokens
import kotlinx.coroutines.launch

private data class OnboardingPageUi(
    @DrawableRes val image: Int,
    @StringRes val title: Int,
    @StringRes val description: Int,
)

private val OnboardingPages = listOf(
    OnboardingPageUi(R.drawable.onboarding_camouflage, R.string.ob_step1_title, R.string.ob_step1_desc),
    OnboardingPageUi(R.drawable.onboarding_ai, R.string.ob_step2_title, R.string.ob_step2_desc),
    OnboardingPageUi(R.drawable.onboarding_encryption, R.string.ob_step3_title, R.string.ob_step3_desc),
    OnboardingPageUi(R.drawable.onboarding_privacy, R.string.ob_step4_title, R.string.ob_step4_desc),
)

private val BackgroundDeep = Color(0xFF05070A)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
) {
    val pages = OnboardingPages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep),
    ) {
        // Full-bleed photography per page, swipeable. Each page carries its own image + bottom scrim
        // + title/description so the editorial copy travels with the image.
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = pages[page]
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(item.image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Scrim: keep the photo clean up top, fade to true black at the bottom for legible copy.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.42f to Color.Transparent,
                                0.66f to BackgroundDeep.copy(alpha = 0.78f),
                                1.0f to BackgroundDeep,
                            )
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 172.dp),
                ) {
                    Text(
                        text = stringResource(item.title),
                        color = SwimDesignTokens.Color.TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 34.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(item.description),
                        color = SwimDesignTokens.Color.TextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
        }

        // Top bar: the app launcher icon + a language selector (changing it re-renders the copy
        // in-place thanks to the localized context, without leaving onboarding).
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                // The launcher icon foreground (a VectorDrawable). NB: R.mipmap.ic_launcher is an
                // adaptive-icon XML which painterResource cannot load.
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ru" to "RU", "en" to "EN", "fr" to "FR").forEach { (code, label) ->
                    OnboardingLangPill(
                        label = label,
                        selected = currentLanguage.equals(code, ignoreCase = true),
                        onClick = { onLanguageChange(code) },
                    )
                }
            }
        }

        // Fixed bottom controls: progress dots + CTA.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { index ->
                    val selected = index == pagerState.currentPage
                    val dotWidth by animateDpAsState(if (selected) 26.dp else 8.dp, label = "ob-dot")
                    Box(
                        modifier = Modifier
                            .size(width = dotWidth, height = 8.dp)
                            .clip(SwimDesignTokens.Shape.Pill)
                            .background(
                                if (selected) SwimDesignTokens.Color.PurpleActive
                                else SwimDesignTokens.Color.TextMuted.copy(alpha = 0.34f)
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = {
                    if (isLast) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = SwimDesignTokens.Shape.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SwimDesignTokens.Color.PurplePrimary,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next),
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun OnboardingLangPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(SwimDesignTokens.Shape.Pill)
            .background(
                if (selected) SwimDesignTokens.Color.PurplePrimary
                else Color.White.copy(alpha = 0.10f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else SwimDesignTokens.Color.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
