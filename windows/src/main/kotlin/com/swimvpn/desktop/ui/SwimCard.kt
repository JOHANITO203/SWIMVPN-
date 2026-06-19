package com.swimvpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.desktop.theme.SwimDesignTokens

/**
 * Canonical content card — a rounded matte surface with a hairline stroke, used to group and
 * visually separate sections (hardware grammar). Consistent radius + elevation across screens.
 */
@Composable
fun SwimCard(
    modifier: Modifier = Modifier,
    padding: Int = 18,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = SwimDesignTokens.Current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (highlighted) tokens.color.homeSurfaceHighlight else tokens.color.homeSurfaceBase)
            .border(1.dp, if (highlighted) tokens.color.homeStrokeActive else tokens.color.homeStrokeSubtle, shape)
            .padding(padding.dp),
        content = content,
    )
}

/** A small uppercase section label above a group of cards. */
@Composable
fun SectionLabel(text: String) {
    val tokens = SwimDesignTokens.Current
    Text(
        text = text,
        color = tokens.color.homeTextMuted,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold,
    )
}
