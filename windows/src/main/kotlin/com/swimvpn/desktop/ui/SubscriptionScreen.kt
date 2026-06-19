package com.swimvpn.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.desktop.i18n.LocalStrings
import com.swimvpn.desktop.i18n.Strings
import com.swimvpn.desktop.theme.SwimDesignTokens

private data class PlanUi(
    val name: String, val price: String, val days: Int,
    val features: List<(Strings) -> String>, val best: Boolean,
)

private val plans = listOf(
    PlanUi("Basic", "$3.49", 7, listOf({ it.feat50gb }, { it.feat1dev }, { it.aiAgent }), false),
    PlanUi("Premium", "$7.99", 30, listOf({ it.feat150gb }, { it.feat2dev }, { it.aiAgentRt }), true),
    PlanUi("Platinum", "$21.99", 90, listOf({ it.feat500gb }, { it.feat3dev }, { it.aiAgentRt }), false),
)

private fun openWeb(url: String) = runCatching {
    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
}

@Composable
fun SubscriptionScreen() {
    val tokens = SwimDesignTokens.Current
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text(s.subTitle, color = tokens.color.homeTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(s.subPaymentLine, color = tokens.color.homeTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .focusable()
                .onPreviewKeyEvent { handleScrollKeys(it, scroll, scope) },
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                plans.forEach { p ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (p.best) tokens.color.homeSurfaceHighlight else tokens.color.homeSurfaceBase)
                            .then(if (p.best) Modifier.border(1.5.dp, tokens.color.homeStrokeActive, RoundedCornerShape(24.dp)) else Modifier)
                            .padding(18.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, color = tokens.color.homeTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (p.best) Text(s.planRecommended, color = tokens.color.homePurplePrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(p.price, color = tokens.color.homeTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("  / ${s.periodDaysFmt.format(p.days)}", color = tokens.color.homeTextMuted, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        p.features.forEach { feat ->
                            Text("✓  ${feat(s)}", color = tokens.color.homeTextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        val subSrc = remember { MutableInteractionSource() }
                        Box(
                            Modifier.fillMaxWidth().height(46.dp)
                                .interactive(subSrc, pressScale = 0.98f, hoverScale = 1.012f)
                                .clip(RoundedCornerShape(23.dp))
                                .background(if (p.best) tokens.color.homePurplePrimary else tokens.color.homeSurfaceElevated)
                                .clickable(interactionSource = subSrc, indication = null) { openWeb("https://app.swimvpn.pro/#offres") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(s.planSubscribe, color = if (p.best) Color.White else tokens.color.homeTextPrimary,
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    s.subFootnote,
                    color = tokens.color.homeTextMuted, fontSize = 11.sp,
                )
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                style = swimScrollbarStyle(),
            )
        }
    }
}
