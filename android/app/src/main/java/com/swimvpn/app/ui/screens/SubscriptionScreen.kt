package com.swimvpn.app.ui.screens

import android.content.res.Resources

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swimvpn.app.R
import com.swimvpn.app.data.model.PaymentMethodPolicy
import com.swimvpn.app.data.model.Plan
import com.swimvpn.app.ui.components.NavDockItem
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.components.SwimDockDestination
import com.swimvpn.app.ui.components.SwimMetaballDock
import com.swimvpn.app.ui.components.drawSwimDarkMaterialSkin
import com.swimvpn.app.ui.components.drawSwimLightCardTexture
import com.swimvpn.app.ui.theme.LocalSwimVisualTokens
import com.swimvpn.app.ui.theme.SwimDesignTokens
import java.math.BigDecimal
import java.math.RoundingMode

enum class SubscriptionPlanTier {
    BASIC,
    PREMIUM,
    PLATINUM,
}

data class SubscriptionPlanUi(
    val id: String,
    val tier: SubscriptionPlanTier,
    val title: String,
    val subtitle: String,
    val price: String,
    val billingPeriod: String,
    val features: List<String>,
    val ctaLabel: String,
    val badgeLabel: String? = null,
    val isHighlighted: Boolean = false,
    val isCurrentPlan: Boolean = false,
)

data class SubscriptionScreenUiState(
    val plans: List<SubscriptionPlanUi>,
    val selectedPlan: SubscriptionPlanTier?,
    val activePlan: SubscriptionPlanTier?,
    val showMoneyBackGuarantee: Boolean = true,
)

@Composable
fun SubscriptionScreen(
    plans: List<Plan>,
    paymentEmail: String?,
    onCheckoutClick: (planId: String, paymentMethod: String) -> Unit,
    onBack: () -> Unit = {},
    activeOfferCode: String? = null,
    onProfileClick: () -> Unit = onBack,
    onNavigateHome: () -> Unit = {},
    onNavigateServers: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
) {
    val resources = LocalContext.current.resources
    val visiblePlans = remember(plans) {
        plans
            .filter { plan -> plan.priceRub.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 1 }
            .sortedWith(compareBy<Plan> { plan -> plan.code.toSubscriptionTier().order }.thenBy { it.displayOrder })
    }
    val uiPlans = remember(visiblePlans, activeOfferCode, resources) {
        visiblePlans.map { plan -> plan.toSubscriptionPlanUi(activeOfferCode, resources) }
    }
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethodPolicy.DEFAULT_METHOD) }
    var pendingPlan by remember { mutableStateOf<SubscriptionPlanUi?>(null) }
    val normalizedPaymentEmail = paymentEmail?.trim().orEmpty()

    SubscriptionScreen(
        uiState = SubscriptionScreenUiState(
            plans = uiPlans,
            selectedPlan = pendingPlan?.tier,
            activePlan = activeOfferCode?.toSubscriptionTierOrNull(),
        ),
        selectedPaymentMethod = selectedPaymentMethod,
        onPaymentMethodSelected = { selectedPaymentMethod = it },
        onPlanSelected = { tier -> pendingPlan = uiPlans.firstOrNull { it.tier == tier } },
        onProfileClick = onProfileClick,
        onDockNavigate = { item ->
            when (item) {
                NavDockItem.HOME -> onNavigateHome()
                NavDockItem.SERVERS -> onNavigateServers()
                NavDockItem.SUBSCRIPTION -> Unit
                NavDockItem.SETTINGS -> onNavigateSettings()
            }
        },
    )

    pendingPlan?.let { plan ->
        CheckoutEmailDialog(
            email = normalizedPaymentEmail,
            onDismiss = { pendingPlan = null },
            onConfirm = {
                pendingPlan = null
                onCheckoutClick(plan.id, selectedPaymentMethod)
            },
        )
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun SubscriptionScreen(
    uiState: SubscriptionScreenUiState,
    onPlanSelected: (SubscriptionPlanTier) -> Unit,
    onProfileClick: () -> Unit,
    onDockNavigate: (NavDockItem) -> Unit,
    modifier: Modifier = Modifier,
    selectedPaymentMethod: String = PaymentMethodPolicy.DEFAULT_METHOD,
    onPaymentMethodSelected: (String) -> Unit = {},
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    SwimDarkLuxuryBackground(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 390.dp || maxHeight < 760.dp
            val horizontalPadding = if (compact) {
                12.dp
            } else {
                16.dp
            }
            val cardGap = if (compact) 13.dp else SwimDesignTokens.Subscription.CardGap

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = SwimDesignTokens.Subscription.DockReservedHeight),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = if (compact) SwimDesignTokens.Subscription.TopPadding + 18.dp else SwimDesignTokens.Subscription.TopPadding + 28.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(cardGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    StaggeredEnter(visible = entered, delayMs = 30) {
                        SubscriptionHeader(compact = compact)
                    }
                }

                if (uiState.plans.isEmpty()) {
                    item {
                        StaggeredEnter(visible = entered, delayMs = 80) {
                            EmptyPlansCard()
                        }
                    }
                } else {
                    uiState.plans.forEachIndexed { index, plan ->
                        item(key = plan.id) {
                            StaggeredEnter(visible = entered, delayMs = 80 + index * 50) {
                                SubscriptionPlanCard(
                                    plan = plan,
                                    compact = compact,
                                    onClick = { onPlanSelected(plan.tier) },
                                )
                            }
                        }
                    }
                }

                item {
                    StaggeredEnter(visible = entered, delayMs = 215) {
                        PaymentMethodStrip(
                            selectedPaymentMethod = selectedPaymentMethod,
                            onPaymentMethodSelected = onPaymentMethodSelected,
                            compact = compact,
                        )
                    }
                }

                if (uiState.showMoneyBackGuarantee) {
                    item {
                        StaggeredEnter(visible = entered, delayMs = 245) {
                            GuaranteeRow(modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                        }
                    }
                }
            }

            SwimMetaballDock(
                active = SwimDockDestination.Subscription,
                onHome = { onDockNavigate(NavDockItem.HOME) },
                onServers = { onDockNavigate(NavDockItem.SERVERS) },
                onSubscription = { onDockNavigate(NavDockItem.SUBSCRIPTION) },
                onSettings = { onDockNavigate(NavDockItem.SETTINGS) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = SwimDesignTokens.Subscription.DockBottomPadding),
            )
        }
    }
}

@Composable
private fun SubscriptionHeader(compact: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.subscription_screen_title),
            color = SwimDesignTokens.Color.TextPrimary,
            fontSize = fixedSp(if (compact) 24 else 27),
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.subscription_screen_subtitle),
            color = SwimDesignTokens.Color.TextMuted,
            fontSize = fixedSp(if (compact) 11 else 13),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(SwimDesignTokens.Subscription.TitleBottomGap))
    }
}

@Composable
private fun SubscriptionPlanCard(
    plan: SubscriptionPlanUi,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSwimVisualTokens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 110, easing = PremiumEase),
        label = "subscription-plan-press",
    )
    val shape = SwimDesignTokens.Shape.LargeHardwareCard
    val cardHeight = plan.cardHeight(compact)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .scale(pressScale)
            .shadow(
                if (plan.isHighlighted) 22.dp else 14.dp,
                shape,
                clip = false,
                ambientColor = if (plan.isHighlighted) SwimDesignTokens.Material.ShadowActive else SwimDesignTokens.Material.ShadowSoft,
                spotColor = if (plan.isHighlighted) SwimDesignTokens.Material.ShadowActive else SwimDesignTokens.Material.ShadowRaised,
            )
            .clip(shape)
            .background(planSurfaceBrush(plan.isHighlighted))
            .border(
                width = if (plan.isHighlighted) 1.4.dp else 1.dp,
                color = if (plan.isHighlighted) {
                    SwimDesignTokens.Color.StrokeActive
                } else {
                    SwimDesignTokens.Color.StrokeSubtle
                },
                shape = shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .drawBehind {
                drawSwimDarkMaterialSkin(tokens)
                drawSwimLightCardTexture(tokens)
                if (tokens == SwimDesignTokens.Light) {
                    drawRect(
                        color = SwimDesignTokens.Highlight.InnerTop.copy(alpha = if (plan.isHighlighted) 0.42f else 0.30f),
                        size = Size(size.width, 0.9.dp.toPx()),
                    )
                }
                if (plan.isHighlighted) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.12f, size.height * 0.24f),
                            radius = size.width * 0.42f,
                        ),
                    )
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SwimDesignTokens.Material.BowlInnerShadow.copy(alpha = SwimDesignTokens.Shadow.InnerBottomAlpha),
                        ),
                        startY = size.height * 0.58f,
                        endY = size.height,
                    ),
                )
            }
            .padding(if (compact) 18.dp else 22.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            PlanIconBadge(
                tier = plan.tier,
                highlighted = plan.isHighlighted,
                size = if (compact) SwimDesignTokens.Subscription.CompactPlanIconSize else SwimDesignTokens.Subscription.PlanIconSize,
            )
                Spacer(modifier = Modifier.width(if (compact) 13.dp else 16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = plan.title,
                            color = SwimDesignTokens.Color.TextPrimary,
                        fontSize = fixedSp(if (compact) 18 else 20),
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = plan.subtitle,
                            color = SwimDesignTokens.Color.TextSecondary,
                        fontSize = fixedSp(if (compact) 10 else 11),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PriceBlock(plan = plan, compact = compact)
                }

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)) {
                    plan.features.forEach { feature ->
                        FeatureRow(text = feature, compact = compact)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                PlanCtaButton(
                    text = plan.ctaLabel,
                    highlighted = plan.isHighlighted,
                    onClick = onClick,
                )
            }
    }
}

@Composable
private fun PriceBlock(plan: SubscriptionPlanUi, compact: Boolean) {
    Column(horizontalAlignment = Alignment.End) {
        plan.badgeLabel?.let { badge ->
            PlanBadge(text = badge)
            Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
        }
        if (plan.isCurrentPlan) {
            Text(
                text = "Actuel",
                color = SwimDesignTokens.Color.PurpleActive,
                fontSize = fixedSp(10),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = plan.price,
            color = SwimDesignTokens.Color.TextPrimary,
            fontSize = fixedSp(if (compact) 16 else 18),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = plan.billingPeriod,
            color = SwimDesignTokens.Color.TextSecondary,
            fontSize = fixedSp(if (compact) 9 else 10),
            maxLines = 1,
        )
    }
}

@Composable
private fun PlanIconBadge(
    tier: SubscriptionPlanTier,
    highlighted: Boolean,
    size: Dp,
) {
    val iconRes = when (tier) {
        SubscriptionPlanTier.BASIC -> R.drawable.ic_plan_basic_medal
        SubscriptionPlanTier.PREMIUM -> R.drawable.ic_plan_premium_sparkles
        SubscriptionPlanTier.PLATINUM -> R.drawable.ic_plan_platinum_diamond
    }
    val tint = if (highlighted) SwimDesignTokens.Color.PurpleActive else SwimDesignTokens.Color.TextPrimary

    Box(
        modifier = Modifier
            .size(size)
            .drawBehind {
                val radius = this.size.minDimension / 2f
                if (highlighted) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SwimDesignTokens.Color.PurplePrimary.copy(alpha = 0.28f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = radius * 1.32f,
                        ),
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SwimDesignTokens.Material.ShellTop,
                            SwimDesignTokens.Material.ShellMid,
                            SwimDesignTokens.Material.ShellBottom,
                        ),
                        center = Offset(center.x - radius * 0.20f, center.y - radius * 0.28f),
                        radius = radius * 1.15f,
                    ),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = SwimDesignTokens.Material.OuterDarkVeil,
                    radius = radius * 0.82f,
                    center = Offset(center.x, center.y + radius * 0.10f),
                )
                drawCircle(
                    color = SwimDesignTokens.Highlight.BowlRim,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(tier.contentDescriptionRes),
            tint = tint,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun PlanBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(SwimDesignTokens.Shape.Pill)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.34f),
                        SwimDesignTokens.Color.PurpleDeep.copy(alpha = 0.28f),
                    ),
                ),
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.Pill)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = SwimDesignTokens.Color.TextPrimary,
            fontSize = fixedSp(10),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun FeatureRow(text: String, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = SwimDesignTokens.Color.PurpleActive,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = text,
            color = SwimDesignTokens.Color.TextPrimary.copy(alpha = 0.92f),
            fontSize = fixedSp(if (compact) 10 else 11),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlanCtaButton(
    text: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    PressablePill(
        text = text,
        highlighted = highlighted,
        contentDescription = text,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PaymentMethodStrip(
    selectedPaymentMethod: String,
    onPaymentMethodSelected: (String) -> Unit,
    compact: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.payment_method_title),
            color = SwimDesignTokens.Color.PurpleActive,
            fontSize = fixedSp(11),
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            PaymentMethodPolicy.VISIBLE_METHODS.forEach { method ->
                PaymentMethodPill(
                    method = method,
                    selected = method == selectedPaymentMethod,
                    onClick = { onPaymentMethodSelected(method) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodPill(
    method: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = SwimDesignTokens.Shape.Pill
    Box(
        modifier = modifier
            .height(SwimDesignTokens.Subscription.PaymentMethodHeight)
            .clip(shape)
            .background(if (selected) purpleCtaBrush(soft = true) else secondaryCtaBrush())
            .border(
                1.dp,
                if (selected) SwimDesignTokens.Color.StrokeActive else SwimDesignTokens.Color.StrokeSubtle,
                shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            val iconRes = method.paymentIconRes
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .size(18.dp)
                        .alpha(if (selected) 1f else 0.72f),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = method.paymentLabel,
                color = if (selected) Color.White else SwimDesignTokens.Color.TextPrimary,
                fontSize = fixedSp(12),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GuaranteeRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(SwimDesignTokens.Subscription.GuaranteeIconSize)
                .clip(CircleShape)
                .background(SwimDesignTokens.Color.PurpleDeep.copy(alpha = 0.58f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = SwimDesignTokens.Color.PurpleActive,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = "Garantie satisfait ou remboursé 30 jours",
                color = SwimDesignTokens.Color.TextPrimary,
                fontSize = fixedSp(13),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Annulation possible à tout moment, sans justification.",
                color = SwimDesignTokens.Color.TextMuted,
                fontSize = fixedSp(12),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PressablePill(
    text: String,
    highlighted: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100, easing = PremiumEase),
        label = "subscription-pill-press",
    )
    val shape = SwimDesignTokens.Shape.Pill
    Box(
        modifier = modifier
            .height(SwimDesignTokens.Subscription.CtaHeight)
            .scale(scale)
            .shadow(
                if (highlighted) 16.dp else 8.dp,
                shape,
                clip = false,
                ambientColor = if (highlighted) SwimDesignTokens.Material.ShadowActive else SwimDesignTokens.Material.ShadowSoft,
                spotColor = if (highlighted) SwimDesignTokens.Material.ShadowActive else SwimDesignTokens.Material.ShadowSoft,
            )
            .clip(shape)
            .background(if (highlighted) purpleCtaBrush() else secondaryCtaBrush())
            .border(1.dp, if (highlighted) SwimDesignTokens.Color.StrokeActive else SwimDesignTokens.Color.StrokeSubtle, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .drawBehind {
                val tokens = SwimDesignTokens.Current
                val topGlow = if (tokens == SwimDesignTokens.Dark) {
                    SwimDesignTokens.Highlight.PurpleEdge.copy(alpha = if (highlighted) 0.08f else 0.035f)
                } else {
                    SwimDesignTokens.Highlight.InnerTop.copy(alpha = if (highlighted) 0.34f else 0.26f)
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(topGlow, Color.Transparent),
                        startY = 0f,
                        endY = 8.dp.toPx(),
                    ),
                    size = Size(size.width, 8.dp.toPx()),
                )
            }
            .semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (highlighted) Color.White else SwimDesignTokens.Color.TextPrimary,
            fontSize = fixedSp(12),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyPlansCard() {
    HardwareBox(
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp),
        shape = SwimDesignTokens.Shape.HardwareCard,
    ) {
        Text(
            text = stringResource(R.string.no_plans_available),
            color = SwimDesignTokens.Color.TextSecondary,
            fontSize = fixedSp(16),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
        )
    }
}

@Composable
private fun HardwareBox(
    modifier: Modifier,
    shape: Shape,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val tokens = LocalSwimVisualTokens.current
    Box(
        modifier = modifier
            .shadow(18.dp, shape, clip = false)
            .clip(shape)
            .background(planSurfaceBrush(highlighted = false))
            .border(1.dp, SwimDesignTokens.Color.DividerSubtle, shape)
            .drawBehind {
                drawSwimDarkMaterialSkin(tokens)
                drawSwimLightCardTexture(tokens)
                if (tokens == SwimDesignTokens.Light) {
                    drawRect(
                        color = SwimDesignTokens.Highlight.InnerTop.copy(alpha = 0.28f),
                        size = Size(size.width, 0.9.dp.toPx()),
                    )
                }
            },
        content = content,
    )
}

@Composable
private fun CheckoutEmailDialog(
    email: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.payment_confirm_email_title),
                color = SwimDesignTokens.Color.TextPrimary,
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.payment_confirm_email_body),
                    color = SwimDesignTokens.Color.TextSecondary,
                    fontSize = fixedSp(14),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SwimDesignTokens.Shape.Control)
                        .background(SwimDesignTokens.Color.SurfaceElevated)
                        .border(1.dp, SwimDesignTokens.Color.DividerSubtle, SwimDesignTokens.Shape.Control)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Text(
                        text = email.ifBlank { stringResource(R.string.payment_confirm_email_missing) },
                        color = SwimDesignTokens.Color.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = fixedSp(14),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = email.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SwimDesignTokens.Color.PurplePrimary),
            ) {
                Text(
                    text = stringResource(R.string.payment_confirm_email_confirm),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SwimDesignTokens.Color.SurfaceElevated),
            ) {
                Text(
                    text = stringResource(R.string.payment_confirm_email_cancel),
                    color = SwimDesignTokens.Color.TextSecondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        shape = SwimDesignTokens.Shape.HardwareCard,
        containerColor = SwimDesignTokens.Color.SurfaceBase,
    )
}

@Composable
private fun StaggeredEnter(
    visible: Boolean,
    delayMs: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = SwimDesignTokens.Motion.ScreenEnterMs, delayMillis = delayMs, easing = PremiumEase)) +
            slideInVertically(
                animationSpec = tween(durationMillis = SwimDesignTokens.Motion.ScreenEnterMs, delayMillis = delayMs, easing = PremiumEase),
                initialOffsetY = { it / 8 },
            ),
    ) {
        content()
    }
}

private fun Plan.toSubscriptionPlanUi(activeOfferCode: String?, resources: Resources): SubscriptionPlanUi {
    val tier = code.toSubscriptionTier()
    val planTitle = name.ifBlank { tier.displayName(resources) }.toLocalizedPlanTitle(tier, resources)
    return SubscriptionPlanUi(
        id = id,
        tier = tier,
        title = planTitle,
        subtitle = durationLabel.toPlanSubtitle(tier, resources),
        price = formatPlanPrice(priceRub),
        billingPeriod = durationLabel.toBillingPeriod(resources),
        features = buildPlanFeatureBullets(tier = tier, quotaLabel = quotaLabel, resources = resources),
        ctaLabel = resources.getString(R.string.subscription_choose_plan, planTitle),
        badgeLabel = if (tier == SubscriptionPlanTier.PREMIUM) resources.getString(R.string.subscription_most_chosen) else null,
        isHighlighted = tier == SubscriptionPlanTier.PREMIUM,
        isCurrentPlan = activeOfferCode?.toSubscriptionTierOrNull() == tier,
    )
}

private fun SubscriptionPlanUi.cardHeight(compact: Boolean): Dp =
    when {
        compact && isHighlighted -> SwimDesignTokens.Subscription.CompactPremiumCardHeight
        compact && tier == SubscriptionPlanTier.BASIC -> SwimDesignTokens.Subscription.CompactBasicCardHeight
        compact -> SwimDesignTokens.Subscription.CompactPlatinumCardHeight
        isHighlighted -> SwimDesignTokens.Subscription.PremiumCardHeight
        tier == SubscriptionPlanTier.BASIC -> SwimDesignTokens.Subscription.BasicCardHeight
        else -> SwimDesignTokens.Subscription.PlatinumCardHeight
    }

private fun String.toSubscriptionTier(): SubscriptionPlanTier =
    toSubscriptionTierOrNull() ?: SubscriptionPlanTier.PREMIUM

private fun String.toSubscriptionTierOrNull(): SubscriptionPlanTier? =
    when (uppercase()) {
        "WEEK", "BASIC" -> SubscriptionPlanTier.BASIC
        "MONTH", "PREMIUM" -> SubscriptionPlanTier.PREMIUM
        "QUARTER", "PLATINUM" -> SubscriptionPlanTier.PLATINUM
        else -> null
    }

private val SubscriptionPlanTier.order: Int
    get() = when (this) {
        SubscriptionPlanTier.BASIC -> 0
        SubscriptionPlanTier.PREMIUM -> 1
        SubscriptionPlanTier.PLATINUM -> 2
    }

private fun SubscriptionPlanTier.displayName(resources: Resources): String =
    resources.getString(planTitleRes)

private fun String.toLocalizedPlanTitle(tier: SubscriptionPlanTier, resources: Resources): String =
    when {
        equals("Basic", ignoreCase = true) || equals("WEEK", ignoreCase = true) -> resources.getString(R.string.subscription_plan_basic)
        equals("Platinum", ignoreCase = true) || equals("QUARTER", ignoreCase = true) -> resources.getString(R.string.subscription_plan_platinum)
        equals("Premium", ignoreCase = true) || equals("MONTH", ignoreCase = true) -> resources.getString(R.string.subscription_plan_premium)
        isBlank() -> tier.displayName(resources)
        else -> this
    }

private val SubscriptionPlanTier.deviceAllowance: Int
    get() = when (this) {
        SubscriptionPlanTier.BASIC -> 2
        SubscriptionPlanTier.PREMIUM -> 3
        SubscriptionPlanTier.PLATINUM -> 4
    }

private fun String.toPlanSubtitle(tier: SubscriptionPlanTier, resources: Resources): String =
    when {
        isNotBlank() -> resources.getString(R.string.subscription_subtitle_with_duration, this)
        else -> when (tier) {
            SubscriptionPlanTier.BASIC -> resources.getString(R.string.subscription_subtitle_basic)
            SubscriptionPlanTier.PREMIUM -> resources.getString(R.string.subscription_subtitle_premium)
            SubscriptionPlanTier.PLATINUM -> resources.getString(R.string.subscription_subtitle_platinum)
        }
    }

private fun buildPlanFeatureBullets(
    tier: SubscriptionPlanTier,
    quotaLabel: String,
    resources: Resources,
): List<String> {
    val dataLabel = quotaLabel.ifBlank { resources.getString(R.string.subscription_data_fallback) }
    return listOf(
        resources.getString(R.string.subscription_feature_data, dataLabel),
        resources.getString(R.string.subscription_feature_devices, tier.deviceAllowance),
        resources.getString(R.string.subscription_feature_ai),
    )
}

private val SubscriptionPlanTier.planTitleRes: Int
    get() = when (this) {
        SubscriptionPlanTier.BASIC -> R.string.subscription_plan_basic
        SubscriptionPlanTier.PREMIUM -> R.string.subscription_plan_premium
        SubscriptionPlanTier.PLATINUM -> R.string.subscription_plan_platinum
    }

private val SubscriptionPlanTier.contentDescriptionRes: Int
    get() = when (this) {
        SubscriptionPlanTier.BASIC -> R.string.subscription_content_basic
        SubscriptionPlanTier.PREMIUM -> R.string.subscription_content_premium
        SubscriptionPlanTier.PLATINUM -> R.string.subscription_content_platinum
    }

private val String.paymentLabel: String
    get() = when (this) {
        PaymentMethodPolicy.SWIMPAY -> "SwimPay"
        PaymentMethodPolicy.CRYPTO -> "Crypto"
        else -> this
    }

private val String.paymentIconRes: Int?
    get() = when (this) {
        PaymentMethodPolicy.SWIMPAY -> R.drawable.ic_swimpay_mark
        PaymentMethodPolicy.CRYPTO -> R.drawable.ic_crypto_pay_mark
        else -> null
    }

private fun String.toBillingPeriod(resources: Resources): String {
    val lower = lowercase()
    return when {
        "week" in lower -> resources.getString(R.string.subscription_period_week)
        "month" in lower -> resources.getString(R.string.subscription_period_month)
        "quarter" in lower -> resources.getString(R.string.subscription_period_quarter)
        "year" in lower -> resources.getString(R.string.subscription_period_year)
        else -> resources.getString(R.string.label_period).trim()
    }
}

private fun planSurfaceBrush(highlighted: Boolean): Brush =
    Brush.verticalGradient(
        colors = if (highlighted) {
            listOf(
                SwimDesignTokens.Color.SurfaceHighlight.copy(alpha = 0.98f),
                SwimDesignTokens.Color.SurfaceElevated.copy(alpha = 0.96f),
                SwimDesignTokens.Color.BackgroundDeep.copy(alpha = 0.98f),
            )
        } else {
            listOf(
                SwimDesignTokens.Color.SurfaceHighlight.copy(alpha = 0.78f),
                SwimDesignTokens.Color.SurfaceBase.copy(alpha = 0.96f),
                SwimDesignTokens.Color.BackgroundDeep.copy(alpha = 0.99f),
            )
        },
    )

private fun purpleCtaBrush(soft: Boolean = false): Brush =
    Brush.verticalGradient(
        colors = if (soft) {
            listOf(
                SwimDesignTokens.Material.PurpleCoreTop.copy(alpha = 0.70f),
                SwimDesignTokens.Material.PurpleCoreMid.copy(alpha = 0.76f),
                SwimDesignTokens.Material.PurpleCoreBottom.copy(alpha = 0.80f),
            )
        } else {
            listOf(
                SwimDesignTokens.Material.PurpleCoreTop,
                SwimDesignTokens.Material.PurpleCoreMid,
                SwimDesignTokens.Material.PurpleCoreBottom,
            )
        },
    )

private fun darkCtaBrush(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            SwimDesignTokens.Color.SurfaceHighlight.copy(alpha = 0.72f),
            SwimDesignTokens.Color.SurfaceElevated.copy(alpha = 0.92f),
            SwimDesignTokens.Color.SurfaceBase.copy(alpha = 0.96f),
        ),
    )

private fun secondaryCtaBrush(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            SwimDesignTokens.Material.ShellTop,
            SwimDesignTokens.Color.SurfaceElevated,
            SwimDesignTokens.Material.ShellBottom,
        ),
    )

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

private val PremiumEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
private fun fixedSp(value: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { value.dp.toSp() }
}
