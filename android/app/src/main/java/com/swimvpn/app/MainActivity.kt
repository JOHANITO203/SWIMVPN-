@file:Suppress("SpellCheckingInspection")
package com.swimvpn.app

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.swimvpn.app.config.ConfigRepository
import com.swimvpn.app.ui.screens.ConfigImportScreen
import com.swimvpn.app.ui.screens.ProxyScreen
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.data.network.ServerNode
import com.swimvpn.app.ui.formatBytes
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground
import com.swimvpn.app.ui.screens.*
import com.swimvpn.app.ui.theme.*
import com.swimvpn.app.update.UpdateGate
import com.swimvpn.app.vpn.RuntimeMode
import com.swimvpn.app.vpn.RuntimeStatus
import com.swimvpn.app.vpn.ThemeMode
import com.swimvpn.app.vpn.RuntimeStateStore
import com.swimvpn.app.vpn.VpnManager
import com.swimvpn.app.vpn.VpnNotificationLanguage
import com.swimvpn.app.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        logStartup("Activity.onCreate start")
        val prefs = PreferencesManager(this)
        super.onCreate(savedInstanceState)
        logStartup("Activity.onCreate after super")
        
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effect.collect { effect ->
                    when (effect) {
                        is AppSideEffect.OpenUrl -> {
                            val intent = Intent(Intent.ACTION_VIEW, effect.url.toUri())
                            startActivity(intent)
                        }
                        is AppSideEffect.ShowToast -> {
                            android.widget.Toast.makeText(this@MainActivity, effect.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        is AppSideEffect.ShowSubscribePrompt -> {
                            // Rendered as a snackbar in AppNavigation (needs the navController + Compose host).
                        }
                    }
                }
            }
        }
        // Source of truth = persisted prefs. Sync the OS/non-Compose locale (Toasts, notifications)
        // to it once at startup; live in-app changes are applied in-place via a localized Context
        // below (no Activity recreate, so the user is never bounced out of their current screen).
        val initialLanguage = runBlocking { prefs.languageFlow.first() }
        applyLocale(initialLanguage)
        logStartup("Activity.onCreate before setContent")
        setContent {
            var firstCompositionLogged by remember { mutableStateOf(false) }
            val themeMode by prefs.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val language by prefs.languageFlow.collectAsState(initial = initialLanguage)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            SideEffect {
                if (!firstCompositionLogged) {
                    firstCompositionLogged = true
                    logStartup("Compose first composition")
                }
            }

            // Apply the selected language IN-PLACE: a Context configured with the chosen locale is
            // provided to the whole tree, so every stringResource recomposes in the new language
            // without recreating the Activity (the user stays on whatever screen they were on).
            val baseContext = LocalContext.current
            val localizedContext = remember(language) {
                // Wrap the ORIGINAL activity context (keep it as base, so LocalActivityResultRegistryOwner
                // and the other owners still resolve up the ContextWrapper chain) and only swap resources
                // to the chosen locale. Replacing LocalContext with createConfigurationContext() directly
                // breaks that owner chain and crashes the NavHost.
                LocalizedContextWrapper(baseContext, Locale(VpnNotificationLanguage.normalize(language)))
            }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
            ) {
                SwimVpnTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // UpdateGate : réel en flavor sideload (check/download/install en un tap),
                        // stub no-op en flavor play (mises à jour via le Play Store).
                        UpdateGate(prefs = prefs) {
                            AppNavigation(
                                viewModel = viewModel,
                                onApplyLocale = ::applyLocale,
                            )
                        }
                    }
                }
            }
        }
        logStartup("Activity.onCreate after setContent")
        handlePaymentReturnDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAfterExternalCheckoutIfNeeded()
        viewModel.refreshSubscriptionsOnForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePaymentReturnDeepLink(intent)
    }

    // swimvpn://pay/return (or /cancel) — the PSP redirects here after checkout. singleTask brings the
    // existing app instance to the foreground; we force an entitlement refresh so a freshly purchased
    // plan is reflected immediately (onResume also refreshes, this covers an expired refresh window).
    private fun handlePaymentReturnDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "swimvpn" && data.host == "pay") {
            viewModel.refreshAfterExternalCheckoutIfNeeded()
        }
    }

    private fun applyLocale(langCode: String) {
        val normalizedLanguage = VpnNotificationLanguage.normalize(langCode)
        val targetLocales: LocaleListCompat = LocaleListCompat.forLanguageTags(normalizedLanguage)
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentPrimaryLanguage = currentLocales[0]?.language?.lowercase(Locale.ROOT).orEmpty()
        val targetPrimaryLanguage = targetLocales[0]?.language?.lowercase(Locale.ROOT).orEmpty()

        if (currentPrimaryLanguage == targetPrimaryLanguage) {
            return
        }

        AppCompatDelegate.setApplicationLocales(targetLocales)
    }

    private fun logStartup(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("SwimStartup", "${System.currentTimeMillis()} $message")
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    onApplyLocale: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val navController = rememberNavController()
    val context = LocalContext.current
    val bootstrapDestination = when (val currentState = state) {
        is AppState.Success -> if (!currentState.isOnboardingDone) "onboarding" else "home"
        is AppState.TrialSetup -> if (!currentState.isOnboardingDone) "onboarding" else "profile"
        else -> null
    }

    LaunchedEffect(bootstrapDestination) {
        if (BuildConfig.DEBUG) {
            Log.d("SwimStartup", "${System.currentTimeMillis()} bootstrapDestination=$bootstrapDestination")
        }
    }

    if (bootstrapDestination == null) {
        StartupBridgeSurface()
        return
    }

    LaunchedEffect(bootstrapDestination) {
        val destination = bootstrapDestination
        when (state) {
            is AppState.Success -> {
                viewModel.maybeRestoreAutoConnectFromBoot(context)
                navController.navigate(destination) { popUpTo(0) }
            }
            is AppState.TrialSetup -> {
                navController.navigate(destination) { popUpTo(0) }
            }
            else -> {}
        }
    }

    val subscribePromptState = remember { SnackbarHostState() }
    val subscribePromptScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            if (effect is AppSideEffect.ShowSubscribePrompt) {
                // Launch in a separate scope so the (long-lived) snackbar never blocks the
                // shared effect flow / other effects.
                subscribePromptScope.launch {
                    val result = subscribePromptState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        navController.navigateProductRoot("subscription")
                    }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = bootstrapDestination) {
        composable("onboarding") {
            val obLanguage = (state as? AppState.Success)?.language
                ?: (state as? AppState.TrialSetup)?.language
                ?: VpnNotificationLanguage.DEFAULT_LANGUAGE
            OnboardingScreen(
                onFinish = { viewModel.completeOnboarding() },
                currentLanguage = obLanguage,
                onLanguageChange = { viewModel.setLanguage(it) },
            )
        }
        composable("home") { 
            val data = state as? AppState.Success ?: return@composable
            HomeScreen(
                viewModel = viewModel, 
                data = data,
                onNavigateProfile = { navController.navigateProductRoot("profile") },
                onNavigateServers = { navController.navigateProductRoot("servers") },
                onNavigateSubscription = { navController.navigateProductRoot("subscription") },
                onNavigateProxy = { navController.navigateProductRoot("proxy") },
                onNavigateTechnical = { navController.navigateOnce("technical") },
            )
        }
        composable("servers") { 
            val data = state as? AppState.Success ?: return@composable
            ServersScreen(
                serverGroups = data.serverGroups,
                activeServerId = data.activeServer?.id,
                activeConfigMetadata = data.activeConfigMetadata,
                profile = data.profile,
                recommendedServerId = data.recommendedServerId,
                isRecommendedServerValidated = data.isRecommendedServerValidated,
                preferredNetworkByServerId = data.preferredNetworkByServerId,
                currentNetworkType = data.currentNetworkType,
                onSelectServer = { server ->
                    viewModel.selectServer(server)
                },
                onImportAccessClick = { navController.navigateOnce("import") },
                onSubscribeClick = { navController.navigateProductRoot("subscription") },
                onProfileClick = { navController.navigateProductRoot("profile") },
                onHomeClick = { navController.navigateProductRoot("home") },
                onSettingsClick = { navController.navigateOnce("technical") },
                onProxyClick = { navController.navigateProductRoot("proxy") },
                onPeriodicRefresh = { viewModel.refreshServerLatency() },
            )
        }
        composable("profile") { 
            when (val currentState = state) {
                is AppState.Success -> {
                    val bytesIn by VpnManager.bytesIn.collectAsState()
                    val bytesOut by VpnManager.bytesOut.collectAsState()

                    ProfileScreen(
                        profile = currentState.profile,
                        activeConfigMetadata = currentState.activeConfigMetadata,
                        bytesIn = bytesIn,
                        bytesOut = bytesOut,
                        onNavigateToSubscription = { navController.navigateProductRoot("subscription") },
                        onNavigateToTechnical = { navController.navigateOnce("technical") },
                        onNavigateToImport = { navController.navigateOnce("import") },
                        onNavigateToSupport = { navController.navigateOnce("support") },
                        onNavigateToProxy = { navController.navigateOnce("proxy") },
                        onActivateTrial = { viewModel.activateTrialFromProfile() },
                        onCancelSubscription = { viewModel.cancelCurrentSubscription(context) },
                        onSignOut = {
                            viewModel.signOut()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                is AppState.TrialSetup -> {
                    TrialActivationProfileScreen(
                        userNumber = currentState.userNumber,
                        email = currentState.email,
                        phone = currentState.phone,
                        trialEligible = currentState.trialEligible,
                        onActivateTrial = { email, phone -> viewModel.activateTrial(email, phone) },
                        onContinueFreemium = { email, phone -> viewModel.continueFreemiumFromTrialSetup(email, phone) },
                        onBack = { navController.popBackStack() },
                    )
                }
                else -> return@composable
            }
        }
        composable("support") {
            SupportScreen(
                onNavigateToSubscription = { navController.navigateProductRoot("subscription") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("import") {
            val importContext = LocalContext.current
            ConfigImportScreen(
                configRepository = ConfigRepository(importContext),
                onBack = { navController.popBackStack() },
                onProfileSelected = { profile ->
                    viewModel.selectImportedProfile(profile)
                },
                onProfilesImported = { profiles ->
                    profiles.firstOrNull()?.let { profile ->
                        viewModel.selectImportedProfile(profile)
                    } ?: viewModel.refreshImportedServers()
                },
                showToast = { message ->
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
        composable("subscription") { 
            val data = state as? AppState.Success ?: return@composable
            SubscriptionScreen(
                plans = data.plans,
                supportedCurrencies = data.supportedCurrencies,
                paymentEmail = data.profile.email,
                activeOfferCode = data.profile.offerCode,
                onCancelSubscription = { viewModel.cancelCurrentSubscription(context) },
                onCheckoutClick = { planId, paymentMethod ->
                    viewModel.createCheckout(
                        planId = planId,
                        paymentMethod = paymentMethod,
                    )
                },
                onBack = { navController.popBackStack() },
                onProfileClick = { navController.navigateProductRoot("profile") },
                onNavigateHome = { navController.navigateProductRoot("home") },
                onNavigateServers = { navController.navigateProductRoot("servers") },
                onNavigateProxy = { navController.navigateProductRoot("proxy") },
                onNavigateSettings = { navController.navigateOnce("technical") },
            )
        }
        composable("proxy") {
            val proxyContext = LocalContext.current
            ProxyScreen(
                configRepository = ConfigRepository(proxyContext),
                onProxyReady = { profile -> viewModel.selectImportedProfile(profile) },
                onBack = { navController.popBackStack() },
                showToast = { message ->
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                },
            )
        }

        composable("technical") {
            val metrics by VpnManager.metrics.collectAsState()
            val runtimeStatus by VpnManager.runtimeStatus.collectAsState()
            val bypassGeoEnabled by viewModel.bypassGeoEnabled.collectAsState()
            val bypassGeoEntries by viewModel.bypassGeoEntries.collectAsState()
            val camouflageProfileId by viewModel.camouflageProfileId.collectAsState()
            val activeCamouflageProfileId by viewModel.activeCamouflageProfileId.collectAsState()
            val routingMode = when (val currentState = state) {
                is AppState.Success -> currentState.routingMode
                is AppState.TrialSetup -> currentState.routingMode
                else -> return@composable
            }
            val autoConnect = when (val currentState = state) {
                is AppState.Success -> currentState.autoConnect
                is AppState.TrialSetup -> currentState.autoConnect
                else -> return@composable
            }
            val agentEnabled = when (val currentState = state) {
                is AppState.Success -> currentState.agentEnabled
                is AppState.TrialSetup -> currentState.agentEnabled
                else -> return@composable
            }
            val language = when (val currentState = state) {
                is AppState.Success -> currentState.language
                is AppState.TrialSetup -> currentState.language
                else -> return@composable
            }
            val themeMode = when (val currentState = state) {
                is AppState.Success -> currentState.themeMode
                is AppState.TrialSetup -> currentState.themeMode
                else -> return@composable
            }
            TechnicalSettingsScreen(
                routingMode = when (routingMode) {
                    RuntimeMode.FULL_TUNNEL -> "FULL_TUNNEL"
                    RuntimeMode.LOCAL_PROXY -> "LOCAL_PROXY"
                    RuntimeMode.SPLIT_TUNNEL -> "SPLIT_TUNNEL"
                    RuntimeMode.TOR_TUNNEL -> "TOR_TUNNEL"
                },
                autoConnect = autoConnect,
                agentEnabled = agentEnabled,
                bypassGeoEnabled = bypassGeoEnabled,
                bypassGeoEntries = bypassGeoEntries,
                language = language,
                onRoutingModeChange = { viewModel.setRoutingMode(it) },
                onAutoConnectChange = { viewModel.setAutoConnect(it) },
                onAgentEnabledChange = { viewModel.setAiAgentEnabled(it) },
                onBypassGeoEnabledChange = { viewModel.setBypassGeoEnabled(it) },
                onBypassGeoEntriesChange = { viewModel.setBypassGeoEntries(it) },
                camouflageProfileId = camouflageProfileId,
                activeCamouflageProfileId = activeCamouflageProfileId,
                onCamouflageProfileChange = { viewModel.setCamouflageProfile(it) },
                onLanguageChange = {
                    // Persist + update state only. The localized Context above re-renders the UI in the
                    // new language in-place — no Activity recreate, so we stay on the current screen.
                    viewModel.setLanguage(it)
                },
                themeMode = themeMode.name,
                onThemeModeChange = { viewModel.setThemeMode(ThemeMode.fromPersisted(it)) },
                runtimeStatus = runtimeStatus.name,
                activeRuntimeMode = metrics.activeMode,
                onBack = { navController.popBackStack() },
            )
        }
    }

    if (state is AppState.Error) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ErrorScreen(
                message = (state as AppState.Error).message,
                onRetry = { viewModel.retry() }
            )
        }
    }

    // Transparent bottom overlay hosting the light subscribe nudge (snackbar). An empty Box
    // with no pointer handlers does not intercept touches to the content beneath it.
    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = subscribePromptState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) { data ->
            SubscribeNudgeContent(
                message = data.visuals.message,
                actionLabel = data.visuals.actionLabel.orEmpty(),
                onAction = { data.performAction() },
            )
        }
    }
}

/**
 * Design-aligned snackbar for the freemium subscribe nudge: the app's satin-glass card grammar
 * (SurfaceHighlight → Shell gradient, subtle stroke, rounded) with a purple "S'abonner" action —
 * unlike the old default toast which didn't match the design at all.
 */
@Composable
internal fun SubscribeNudgeContent(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SwimDesignTokens.Color.SurfaceHighlight.copy(alpha = 0.72f),
                        SwimDesignTokens.Material.ShellMid,
                        SwimDesignTokens.Material.ShellBottom,
                    )
                )
            )
            .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, RoundedCornerShape(18.dp))
            .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = SwimDesignTokens.Color.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = actionLabel,
                color = SwimDesignTokens.Color.PurpleActive,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(SwimDesignTokens.Shape.Pill)
                    .clickable { onAction() }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

/**
 * Context wrapper that keeps the wrapped (activity) context as its base — so LocalContext-based owner
 * lookups (ActivityResultRegistryOwner, OnBackPressedDispatcherOwner, …) still resolve — while serving
 * resources for the chosen locale. Used to switch the app language in-place without recreating the
 * Activity (which would bounce the user back to the home destination).
 */
private class LocalizedContextWrapper(base: Context, locale: Locale) : ContextWrapper(base) {
    private val localizedResources: Resources =
        base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) }
        ).resources

    override fun getResources(): Resources = localizedResources
}

private fun NavHostController.navigateProductRoot(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo("home") {
            saveState = true
            inclusive = false
        }
    }
}

private fun NavHostController.navigateOnce(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

@Composable
fun StartupBridgeSurface() {
    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG) {
            Log.d("SwimStartup", "${System.currentTimeMillis()} StartupBridgeSurface shown")
        }
    }
    StartupShell()
}

@Composable
fun QrScannerView(onCodeScanned: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val currentOnCodeScanned by rememberUpdatedState(onCodeScanned)
    val currentOnClose by rememberUpdatedState(onClose)
    var hasLaunched by remember { mutableStateOf(false) }
    var handledResult by remember { mutableStateOf(false) }
    val scanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .enableAutoZoom()
            .allowManualInput()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    LaunchedEffect(scanner) {
        if (hasLaunched) {
            return@LaunchedEffect
        }

        hasLaunched = true
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                if (!handledResult) {
                    handledResult = true
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        currentOnCodeScanned(rawValue)
                    } else {
                        android.widget.Toast
                            .makeText(context, context.getString(R.string.scanner_empty), android.widget.Toast.LENGTH_SHORT)
                            .show()
                        currentOnClose()
                    }
                }
            }
            .addOnCanceledListener {
                if (!handledResult) {
                    handledResult = true
                    currentOnClose()
                }
            }
            .addOnFailureListener { error ->
                if (!handledResult) {
                    handledResult = true
                    Log.e("QrScannerView", "Google Code Scanner failed", error)
                    android.widget.Toast
                        .makeText(context, context.getString(R.string.scanner_unavailable), android.widget.Toast.LENGTH_SHORT)
                        .show()
                    currentOnClose()
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = SwimBlueMain)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.scanner_opening), color = Color.White)
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_close), tint = Color.White)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwimDesignTokens.Color.BackgroundDeep)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        SwimDesignTokens.Color.PurpleDeep.copy(alpha = 0.24f),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(760f, 180f),
                    radius = 720f,
                )
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(SwimDesignTokens.Shadow.HardwareSurface, SwimDesignTokens.Shape.LargeHardwareCard, clip = false)
                .clip(SwimDesignTokens.Shape.LargeHardwareCard)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SwimDesignTokens.Color.SurfaceHighlight.copy(alpha = 0.78f),
                            SwimDesignTokens.Color.SurfaceBase.copy(alpha = 0.96f),
                            SwimDesignTokens.Color.BackgroundDeep.copy(alpha = 0.99f),
                        )
                    )
                )
                .border(1.dp, SwimDesignTokens.Color.StrokeSubtle, SwimDesignTokens.Shape.LargeHardwareCard)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(SwimDesignTokens.Color.PurpleDeep.copy(alpha = 0.52f))
                    .border(1.dp, SwimDesignTokens.Color.PurpleActive.copy(alpha = 0.32f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = stringResource(R.string.content_desc_error),
                    tint = SwimDesignTokens.Color.PurpleActive,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = message,
                color = SwimDesignTokens.Color.TextPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = SwimDesignTokens.Shape.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SwimDesignTokens.Color.PurplePrimary,
                    contentColor = Color.White,
                ),
            ) {
                Text(stringResource(R.string.btn_retry), fontWeight = FontWeight.Black)
            }
        }
    }
}
