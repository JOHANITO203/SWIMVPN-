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
import com.swimvpn.app.vpn.RuntimeMetrics
import com.swimvpn.app.vpn.RuntimeStateStore
import com.swimvpn.app.vpn.VpnManager
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

    private fun applyLocale(langCode: String) {
        val normalizedLanguage = langCode.trim().ifEmpty { "en" }
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
        when (val currentState = state) {
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
                onNavigateImport = { navController.navigate("import") },
                onNavigateSubscription = { navController.navigate("subscription") }
            ) 
        }
        composable("servers") { 
            val data = state as? AppState.Success ?: return@composable
            ServersScreen(
                serverGroups = data.serverGroups,
                activeServerId = data.activeServer?.id,
                onBack = { navController.popBackStack() },
                onSelectServer = { server ->
                    viewModel.selectServer(server)
                    navController.popBackStack()
                },
                onTogglePinServer = { server ->
                    viewModel.toggleServerPin(server)
                }
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
            val context = LocalContext.current
            ConfigImportScreen(
                configRepository = ConfigRepository(context),
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
                onCheckoutClick = { planId, paymentMethod ->
                    viewModel.createCheckout(
                        planId = planId,
                        paymentMethod = paymentMethod,
                    )
                },
                onBack = { navController.popBackStack() }
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
                runtimeDiagnostics = buildRuntimeDiagnostics(metrics),
                runtimeStatus = runtimeStatus.name,
                activeRuntimeMode = metrics.activeMode,
                onBack = { navController.popBackStack() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    data: AppState.Success,
    onNavigateProfile: () -> Unit,
    onNavigateServers: () -> Unit,
    onNavigateImport: () -> Unit,
    onNavigateSubscription: () -> Unit,
) {
    val profile = data.profile
    val activeServer = data.activeServer
    val selectedRuntimeMode = data.routingMode

    // Lier l'UI au VRAI statut du service VPN Android
    val inMemoryVpnState by VpnManager.state.collectAsState()
    var vpnState by remember { mutableStateOf(inMemoryVpnState) }
    val bytesIn by VpnManager.bytesIn.collectAsState()
    val bytesOut by VpnManager.bytesOut.collectAsState()
    val errorMessage by VpnManager.errorMessage.collectAsState()

    LaunchedEffect(inMemoryVpnState, activeServer?.id) {
        val staleErrorWithoutServer = inMemoryVpnState == VpnState.ERROR && activeServer == null
        if (staleErrorWithoutServer) {
            VpnManager.updateState(VpnState.DISCONNECTED)
            vpnState = VpnState.DISCONNECTED
        } else {
            vpnState = inMemoryVpnState
        }
    }

    // Animation pour le bouton Power
    val infiniteTransition = rememberInfiniteTransition(label = "powerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING) 1.05f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            val snapshot = RuntimeStateStore.read(context)
            VpnManager.reconcileRuntimeSnapshot(snapshot)
            vpnState = if (snapshot.isFresh()) {
                vpnStateForRuntimeStatus(snapshot.status)
            } else {
                VpnState.DISCONNECTED
            }
            delay(1_000)
        }
    }

    // Notification Permission Request (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.w("MainActivity", "Notification permission denied. VPN status won't be shown.")
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Launcher pour demander la permission VPN système
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Permission accordée, on démarre le tunnel
            viewModel.toggleVpn(context, activeServer, profile)
        }
    }

    val publicPlanCode = when (profile.publicPlanName ?: profile.offerCode) {
        "Premium", "MONTH" -> "PREMIUM"
        "Platinum", "QUARTER" -> "PLATINUM"
        "Basic", "WEEK" -> "BASIC"
        else -> null
    }
    val badgeText = when {
        profile.isPendingFulfillment -> stringResource(R.string.home_badge_pending_fulfillment)
        profile.isActiveTrial -> stringResource(R.string.home_badge_trial)
        publicPlanCode == "PREMIUM" -> stringResource(R.string.home_badge_premium)
        publicPlanCode == "PLATINUM" -> stringResource(R.string.home_badge_platinum)
        publicPlanCode == "BASIC" -> stringResource(R.string.home_badge_basic)
        else -> stringResource(R.string.home_badge_standard)
    }
    val connectionSubtitle = when (vpnState) {
        VpnState.CONNECTED -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            stringResource(R.string.home_proxy_ready)
        } else {
            activeServer?.let {
                stringResource(R.string.home_connected_via, it.country, it.city)
            } ?: stringResource(R.string.home_connected)
        }
        VpnState.CONNECTING -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            stringResource(R.string.home_starting_proxy)
        } else {
            stringResource(R.string.home_starting_tunnel)
        }
        VpnState.DISCONNECTING -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            stringResource(R.string.home_stopping_proxy)
        } else {
            stringResource(R.string.home_stopping_tunnel)
        }
        VpnState.ERROR -> errorMessage ?: stringResource(R.string.home_check_server)
        else -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            stringResource(R.string.home_tap_start_proxy)
        } else if (activeServer != null) {
            stringResource(R.string.home_tap_connect_selected)
        } else {
            stringResource(R.string.home_select_server_first)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo Requin
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.swimvpn_logo),
                        contentDescription = stringResource(R.string.content_desc_logo),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black, 
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { onNavigateProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person, 
                        contentDescription = stringResource(R.string.content_desc_profile), 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status Badge
            val badgeColor = when {
                profile.isPendingFulfillment -> MaterialTheme.colorScheme.secondaryContainer
                profile.isActiveTrial -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
            val badgeTextColor = when {
                profile.isPendingFulfillment -> MaterialTheme.colorScheme.onSecondaryContainer
                profile.isActiveTrial -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = badgeColor
            ) {
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = badgeTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Big Power Circle
            val isActive = vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING
            val isTransitioning = vpnState == VpnState.CONNECTING || vpnState == VpnState.DISCONNECTING
            val isError = vpnState == VpnState.ERROR
            
            val circleOuterColor = when {
                isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else -> Color.Transparent
            }
            
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .scale(pulseScale)
                    .shadow(
                        elevation = if (isActive) 16.dp else 2.dp,
                        shape = CircleShape,
                        spotColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
                    )
                    .clip(CircleShape)
                    .background(circleOuterColor)
                    .clickable(
                        enabled = vpnState != VpnState.DISCONNECTING,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!profile.isPremiumAllowed && activeServer?.source == "backend" && vpnState == VpnState.DISCONNECTED) {
                            onNavigateSubscription()
                            return@clickable
                        }
                        if (vpnState == VpnState.DISCONNECTED || vpnState == VpnState.ERROR) {
                            if (selectedRuntimeMode == RuntimeMode.FULL_TUNNEL) {
                                val intent = android.net.VpnService.prepare(context)
                                if (intent != null) {
                                    vpnPermissionLauncher.launch(intent)
                                } else {
                                    viewModel.toggleVpn(context, activeServer, profile)
                                }
                            } else {
                                viewModel.toggleVpn(context, activeServer, profile)
                            }
                        } else {
                            viewModel.toggleVpn(context, activeServer, profile)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val innerCircleColor = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isError -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                
                // Background Circle with Solid Fill or Empty state
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(CircleShape)
                        .background(innerCircleColor)
                        .border(
                            width = 2.dp,
                            color = if (isActive) Color.Transparent else if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTransitioning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(120.dp),
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            strokeWidth = 3.dp,
                            trackColor = Color.Transparent
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = stringResource(R.string.content_desc_connect),
                        modifier = Modifier.size(88.dp),
                        tint = when {
                            vpnState == VpnState.CONNECTED -> MaterialTheme.colorScheme.onPrimary
                            isTransitioning -> if (isActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            isError -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Status Text
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vpnState == VpnState.CONNECTING || vpnState == VpnState.DISCONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = when (vpnState) {
                        VpnState.CONNECTED -> stringResource(R.string.status_connected)
                        VpnState.CONNECTING -> stringResource(R.string.status_connecting)
                        VpnState.DISCONNECTING -> stringResource(R.string.status_disconnecting)
                        VpnState.ERROR -> errorMessage ?: stringResource(R.string.status_error)
                        else -> stringResource(R.string.status_disconnected)
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                )
            }

            Text(
                text = connectionSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            ServerSelectionCard(
                server = activeServer,
                onClick = onNavigateServers,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(32.dp))
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = stringResource(R.string.label_download), 
                    value = formatBytes(bytesIn), 
                    icon = Icons.Default.ArrowDownward, 
                    color = MaterialTheme.colorScheme.secondary
                )
                Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                StatItem(
                    label = stringResource(R.string.label_upload), 
                    value = formatBytes(bytesOut),
                    icon = Icons.Default.ArrowUpward, 
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(96.dp))
        }

        FloatingActionButton(
            onClick = onNavigateImport,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.content_desc_quick_actions), modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun buildRuntimeDiagnostics(metrics: RuntimeMetrics): String {
    val lines = mutableListOf<String>()
    metrics.activeMode?.let { lines += stringResource(R.string.runtime_diag_mode, it) }
    metrics.xraySessionId?.let { lines += stringResource(R.string.runtime_diag_xray_session, it) }
    metrics.xrayLogPath?.let { lines += stringResource(R.string.runtime_diag_xray_log, it) }
    metrics.tun2SocksSessionId?.let { lines += stringResource(R.string.runtime_diag_tun2socks_session, it) }
    metrics.tun2SocksLogPath?.let { lines += stringResource(R.string.runtime_diag_tun2socks_log, it) }
    metrics.lastError?.let { lines += stringResource(R.string.runtime_diag_last_error, it) }
    return lines.joinToString("\n")
}

private fun vpnStateForRuntimeStatus(status: RuntimeStatus): VpnState {
    return when (status) {
        RuntimeStatus.IDLE -> VpnState.DISCONNECTED
        RuntimeStatus.STARTING -> VpnState.CONNECTING
        RuntimeStatus.RUNNING -> VpnState.CONNECTED
        RuntimeStatus.STOPPING -> VpnState.DISCONNECTING
        RuntimeStatus.FAILED -> VpnState.ERROR
    }
}

@Composable
fun ServerSelectionCard(server: ServerNode?, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val serverLabel = server?.countryCode ?: "--"
                Text(
                    text = serverLabel.uppercase(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.selected_server_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (server != null) "${server.country}, ${server.city}" else stringResource(R.string.selected_server_none),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (server != null) {
                        buildString {
                            append(server.protocol)
                            append(" - ")
                            append(server.host)
                            if (server.ping > 0) {
                                append(" - ")
                                append("${server.ping}ms")
                            }
                        }
                    } else {
                        stringResource(R.string.selected_server_hint)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.content_desc_open_server_list),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface))
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
