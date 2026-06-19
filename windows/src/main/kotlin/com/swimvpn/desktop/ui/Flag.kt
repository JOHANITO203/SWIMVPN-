package com.swimvpn.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.swimvpn.desktop.theme.SwimDesignTokens

/**
 * Circular country flag rendered from the country DECODED out of the config name's flag emoji
 * (FlagUtil). Real image when bundled; otherwise the ISO code as a badge, or a globe for entries
 * with no country (auto-select / game nodes).
 */
@Composable
fun Flag(name: String, size: Dp = 30.dp) {
    val tokens = SwimDesignTokens.Current
    val code = remember(name) { FlagUtil.countryCode(name)?.lowercase() }
    val available = remember(code) {
        code != null && FlagUtil::class.java.classLoader.getResource("flags/$code.png") != null
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(tokens.color.homeSurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        when {
            available -> Image(
                painter = painterResource("flags/$code.png"),
                contentDescription = code,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            code != null -> Text(
                code.uppercase(), color = tokens.color.homeTextSecondary,
                fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Bold,
            )
            else -> Icon(
                Icons.Filled.Public, null, tint = tokens.color.homeTextSecondary,
                modifier = Modifier.size(size * 0.62f),
            )
        }
    }
}
