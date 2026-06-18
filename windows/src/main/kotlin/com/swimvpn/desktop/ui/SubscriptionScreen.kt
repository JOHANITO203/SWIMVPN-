package com.swimvpn.desktop.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.desktop.theme.SwimDesignTokens

private data class PlanUi(
    val name: String, val price: String, val period: String,
    val features: List<String>, val best: Boolean,
)

private val plans = listOf(
    PlanUi("Basic", "$3.49", "7 jours", listOf("50 Go inclus", "1 appareil", "Agent IA"), false),
    PlanUi("Premium", "$7.99", "30 jours", listOf("150 Go inclus", "2 appareils", "Agent IA temps réel"), true),
    PlanUi("Platinum", "$21.99", "90 jours", listOf("500 Go inclus", "3 appareils", "Agent IA temps réel"), false),
)

private fun openWeb(url: String) = runCatching {
    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
}

@Composable
fun SubscriptionScreen() {
    val tokens = SwimDesignTokens.Current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text("Abonnement", color = tokens.color.homeTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Paiement via SwimPay (RUB · USD · XOF) ou crypto", color = tokens.color.homeTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(plans.size) { i ->
                val p = plans[i]
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (p.best) tokens.color.homeSurfaceHighlight else tokens.color.homeSurfaceBase)
                        .then(if (p.best) Modifier.border(1.5.dp, tokens.color.homeStrokeActive, RoundedCornerShape(24.dp)) else Modifier)
                        .padding(18.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, color = tokens.color.homeTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (p.best) Text("Recommandé", color = tokens.color.homePurplePrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(p.price, color = tokens.color.homeTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(0.dp))
                        Text("  / ${p.period}", color = tokens.color.homeTextMuted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    p.features.forEach {
                        Text("✓  $it", color = tokens.color.homeTextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(if (p.best) tokens.color.homePurplePrimary else tokens.color.homeSurfaceElevated)
                            .clickable { openWeb("https://app.swimvpn.pro/#offres") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("S'abonner", color = if (p.best) Color.White else tokens.color.homeTextPrimary,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Le paiement s'ouvre sur app.swimvpn.pro. Après paiement, importe la config reçue dans « Serveurs ».",
                    color = tokens.color.homeTextMuted, fontSize = 11.sp,
                )
            }
        }
    }
}
