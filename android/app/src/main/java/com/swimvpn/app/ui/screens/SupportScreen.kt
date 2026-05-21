package com.swimvpn.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.theme.SwimDesignTokens

@Composable
fun SupportScreen(
    onNavigateToSubscription: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    SwimDarkLuxuryBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(PaddingValues(horizontal = 24.dp, vertical = 18.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SupportHeader(onBack = onBack)

            SupportHero()

            SupportSectionTitle(stringResource(R.string.support_faq_title))
            SupportCanvas {
                SupportFaqItem(
                    title = stringResource(R.string.faq_connect_title),
                    description = stringResource(R.string.faq_connect_desc),
                )
                Spacer(modifier = Modifier.height(10.dp))
                SupportFaqItem(
                    title = stringResource(R.string.faq_import_title),
                    description = stringResource(R.string.faq_import_desc),
                )
            }

            SupportSectionTitle(stringResource(R.string.support_contact_title))
            SupportCanvas {
                SupportActionRow(
                    icon = Icons.Outlined.Email,
                    title = stringResource(R.string.support_email),
                    subtitle = stringResource(R.string.support_email_value),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mailto:${context.getString(R.string.support_email_value)}"))
                        )
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                SupportActionRow(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.support_telegram_bot),
                    subtitle = stringResource(R.string.support_telegram_handle),
                    onClick = {
                        openTelegramSupport(context.getString(R.string.support_telegram_username), context)
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(SwimDesignTokens.Shape.Pill)
                    .background(purpleGradient())
                    .border(1.dp, SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.48f), SwimDesignTokens.Shape.Pill)
                    .clickable(onClick = onNavigateToSubscription),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_renew),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SupportHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HardwareCircleButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.content_desc_back),
                tint = SwimDesignTokens.Color.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = stringResource(R.string.support_title),
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = "FAQ, email et Telegram",
                color = SwimDesignTokens.Color.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SupportHero() {
    SupportCanvas {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(purpleGradient())
                    .border(1.dp, SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.40f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Support SwimVPN",
                    color = SwimDesignTokens.Color.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    text = "Trouver une réponse ou contacter l’équipe.",
                    color = SwimDesignTokens.Color.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SupportFaqItem(title: String, description: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SwimDesignTokens.Shape.HardwareCard)
            .background(SwimDesignTokens.Material.BowlTop.copy(alpha = 0.72f))
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.HardwareCard)
            .clickable { expanded = !expanded }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBowl(Icons.AutoMirrored.Outlined.HelpOutline)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = SwimDesignTokens.Color.TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SwimDesignTokens.Color.TextSecondary,
                modifier = Modifier.graphicsLayer(rotationZ = if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                color = SwimDesignTokens.Color.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 56.dp),
            )
        }
    }
}

@Composable
private fun SupportActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(SwimDesignTokens.Shape.Pill)
            .background(SwimDesignTokens.Material.BowlTop.copy(alpha = 0.72f))
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBowl(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SwimDesignTokens.Color.TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = SwimDesignTokens.Color.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SwimDesignTokens.Color.TextSecondary,
        )
    }
}

@Composable
private fun SupportCanvas(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(SwimDesignTokens.Shadow.HardwareSurface, SwimDesignTokens.Shape.LargeHardwareCard, clip = false)
            .clip(SwimDesignTokens.Shape.LargeHardwareCard)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.LargeHardwareCard)
            .drawBehind {
                drawRect(SwimDesignTokens.Highlight.InnerTop, size = Size(size.width, 1.dp.toPx()))
            }
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun SupportSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        color = SwimDesignTokens.Color.PurpleActive,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp),
    )
}

@Composable
private fun IconBowl(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(SwimDesignTokens.Material.BowlBottom)
            .border(1.dp, SwimDesignTokens.Highlight.BowlRim.copy(alpha = 0.72f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SwimDesignTokens.Color.PurpleActive,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HardwareCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .shadow(10.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(SwimDesignTokens.Material.BowlBottom)
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun purpleGradient(): Brush =
    Brush.radialGradient(
        listOf(
            SwimDesignTokens.Material.PurpleCoreTop,
            SwimDesignTokens.Material.PurpleCoreMid,
            SwimDesignTokens.Material.PurpleCoreBottom,
        )
    )

private fun openTelegramSupport(username: String, context: android.content.Context) {
    val cleanUsername = username.removePrefix("@")
    val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$cleanUsername"))
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$cleanUsername"))

    try {
        context.startActivity(telegramIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(webIntent)
    }
}
