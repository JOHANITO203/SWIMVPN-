@file:Suppress("SpellCheckingInspection")
package com.swimvpn.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.swimvpn.app.config.ConfigRepository
import com.swimvpn.app.ui.screens.ConfigImportScreen
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.data.network.ServerNode
import com.swimvpn.app.ui.formatBytes
import com.swimvpn.app.ui.screens.*
import com.swimvpn.app.ui.theme.*
import com.swimvpn.app.vpn.RuntimeMode
import com.swimvpn.app.vpn.RuntimeStatus
import com.swimvpn.app.vpn.ThemeMode
import com.swimvpn.app.vpn.RuntimeStateStore
import com.swimvpn.app.vpn.VpnManager
import com.swimvpn.app.vpn.VpnNotificationLanguage
import com.swimvpn.app.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferencesManager(this)
        val persistedLanguage = runBlocking {
            prefs.languageFlow.first()
        }
        applyLocale(persistedLanguage)
        super.onCreate(savedInstanceState)
        
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
                    }
                }
            }
        }
        setContent {
            val themeMode by prefs.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            SwimVpnTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        viewModel = viewModel,
                        onApplyLocale = ::applyLocale,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAfterExternalCheckoutIfNeeded()
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
        val destination = bootstrapDestination ?: return@LaunchedEffect
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

    NavHost(navController = navController, startDestination = "loading") {
        composable("loading") { SplashScreen() }
        composable("onboarding") { 
            OnboardingScreen(onFinish = { 
                viewModel.completeOnboarding()
            }) 
        }
        composable("home") { 
            val data = state as? AppState.Success ?: return@composable
            HomeScreen(
                viewModel = viewModel, 
                data = data,
                onNavigateProfile = { navController.navigate("profile") },
                onNavigateServers = { navController.navigate("servers") },
                onNavigateSubscription = { navController.navigate("subscription") }
            ) 
        }
        composable("servers") { 
            val data = state as? AppState.Success ?: return@composable
            ServersScreen(
                serverGroups = data.serverGroups,
                activeServerId = data.activeServer?.id,
                activeConfigMetadata = data.activeConfigMetadata,
                recommendedServerId = data.recommendedServerId,
                isRecommendedServerValidated = data.isRecommendedServerValidated,
                onSelectServer = { server ->
                    viewModel.selectServer(server)
                },
                onImportAccessClick = { navController.navigate("import") },
                onSubscribeClick = { navController.navigate("subscription") },
                onProfileClick = { navController.navigate("profile") },
                onHomeClick = { navController.navigate("home") },
                onSettingsClick = { navController.navigate("profile") },
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
                        onNavigateToSubscription = { navController.navigate("subscription") },
                        onNavigateToTechnical = { navController.navigate("technical") },
                        onNavigateToImport = { navController.navigate("import") },
                        onNavigateToSupport = { navController.navigate("support") },
                        onActivateTrial = { viewModel.activateTrialFromProfile() },
                        onCancelSubscription = { viewModel.cancelCurrentSubscription(context) },
                        onSignOut = {
                            viewModel.signOut()
                            navController.navigate("loading") { popUpTo(0) }
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
                onNavigateToSubscription = { navController.navigate("subscription") },
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
                paymentEmail = data.profile.email,
                activeOfferCode = data.profile.offerCode,
                onCheckoutClick = { planId, paymentMethod ->
                    viewModel.createCheckout(
                        planId = planId,
                        paymentMethod = paymentMethod,
                    )
                },
                onBack = { navController.popBackStack() },
                onProfileClick = { navController.navigate("profile") },
                onNavigateHome = { navController.navigate("home") },
                onNavigateServers = { navController.navigate("servers") },
                onNavigateSettings = { navController.navigate("profile") },
            )
        }
        composable("technical") {
            val metrics by VpnManager.metrics.collectAsState()
            val runtimeStatus by VpnManager.runtimeStatus.collectAsState()
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
                },
                autoConnect = autoConnect,
                language = language,
                onRoutingModeChange = { viewModel.setRoutingMode(it) },
                onAutoConnectChange = { viewModel.setAutoConnect(it) },
                onLanguageChange = {
                    viewModel.setLanguage(it)
                    onApplyLocale(it)
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
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    // 1. Animation de respiration légère du logo
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    // 2. Onde Radar 1 (Scale & Alpha)
    val radarScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarScale1"
    )
    val radarAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAlpha1"
    )

    // 3. Onde Radar 2 (Décalée)
    val radarScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing, delayMillis = 1250),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarScale2"
    )
    val radarAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing, delayMillis = 1250),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAlpha2"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            
            Box(contentAlignment = Alignment.Center) {
                // Onde 1
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(radarScale1)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = radarAlpha1), CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = radarAlpha1 * 0.15f), CircleShape)
                )

                // Onde 2
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(radarScale2)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = radarAlpha2), CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = radarAlpha2 * 0.15f), CircleShape)
                )

                // Anneau Premium qui tourne (Proposition 2)
                CircularProgressIndicator(
                    modifier = Modifier.size(200.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )

                // Logo Central (qui flotte/respire)
                Image(
                    painter = painterResource(id = R.drawable.swimvpn_logo),
                    contentDescription = stringResource(R.string.content_desc_logo),
                    modifier = Modifier
                        .size(180.dp)
                        .scale(logoScale)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(72.dp))

            // Texte Corporate espacé
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp // Espacement premium
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.content_desc_error), tint = Color.Red, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.btn_retry)) }
        }
    }
}
