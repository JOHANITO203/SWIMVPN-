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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Brush
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

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
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
            val prefs = remember { PreferencesManager(this@MainActivity) }
            val language by prefs.languageFlow.collectAsState(initial = "en")
            val themeMode by prefs.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            LaunchedEffect(language) {
                applyLocale(language)
            }

            SwimVpnTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel)
                }
            }
        }
    }

    private fun applyLocale(langCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val navController = rememberNavController()
    val context = LocalContext.current

    LaunchedEffect(state) {
        when (val currentState = state) {
            is AppState.Success -> {
                viewModel.maybeAutoConnect(context, currentState)
                if (!currentState.isOnboardingDone) {
                    navController.navigate("onboarding") { popUpTo(0) }
                } else {
                    navController.navigate("home") { popUpTo(0) }
                }
            }
            is AppState.TrialSetup -> {
                if (!currentState.isOnboardingDone) {
                    navController.navigate("onboarding") { popUpTo(0) }
                } else {
                    navController.navigate("profile") { popUpTo(0) }
                }
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
                        bytesIn = bytesIn,
                        bytesOut = bytesOut,
                        onNavigateToSubscription = { navController.navigate("subscription") },
                        onNavigateToTechnical = { navController.navigate("technical") },
                        onNavigateToImport = { navController.navigate("import") },
                        onNavigateToSupport = { navController.navigate("support") },
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
                onImportToProfile = { rawConfig ->
                    viewModel.importVless(rawConfig)
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
                onUpgradeClick = { planId ->
                    val plan = (state as? AppState.Success)?.plans?.find { it.id == planId }
                    plan?.let {
                        viewModel.createOrder(it.id, it.priceRub.toDoubleOrNull() ?: 0.0)
                    }
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
                onLanguageChange = { viewModel.setLanguage(it) },
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
    
    // Animation de respiration (taille)
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.swimvpn_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(240.dp)
                    .scale(scale)
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "SWIMVPN+",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = Color(0xFF0A3151)
            )
            Spacer(modifier = Modifier.height(32.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(140.dp).height(6.dp).clip(CircleShape),
                color = Color(0xFF4A9ED7),
                trackColor = Color(0xFFEBF5FF)
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

    LaunchedEffect(inMemoryVpnState) {
        vpnState = inMemoryVpnState
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

    val badgeText = when {
        profile.status == "EXPIRED" -> "EXPIRED"
        profile.accessType == "TRIAL" -> "TRIAL 3 DAYS"
        profile.offerCode == "MONTH" -> "MONTH ACTIVE"
        profile.offerCode == "QUARTER" -> "QUARTER ACTIVE"
        profile.offerCode == "WEEK" -> "WEEK ACTIVE"
        else -> "ACCESS ACTIVE"
    }
    val connectionSubtitle = when (vpnState) {
        VpnState.CONNECTED -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            "Local proxy ready on 127.0.0.1:10808"
        } else {
            activeServer?.let { "Connected via ${it.country}, ${it.city}" } ?: "Connected"
        }
        VpnState.CONNECTING -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            "Starting native local proxy..."
        } else {
            "Establishing secure tunnel..."
        }
        VpnState.DISCONNECTING -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            "Stopping local proxy..."
        } else {
            "Stopping secure tunnel..."
        }
        VpnState.ERROR -> errorMessage ?: "Check your server or imported config."
        else -> if (selectedRuntimeMode == RuntimeMode.LOCAL_PROXY) {
            "Tap to start the local Xray proxy"
        } else if (activeServer != null) {
            "Tap to connect using the selected server"
        } else {
            "Select a server or import a config first"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
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
                        contentDescription = "Logo",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "SWIMVPN+",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black, 
                            color = Color(0xFF0F172A),
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        .clickable { onNavigateProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person, 
                        contentDescription = "Profile", 
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status Badge
            val badgeColor = when {
                profile.status == "EXPIRED" -> Color(0xFFFFF1F2) // Red-ish
                profile.accessType == "TRIAL" -> Color(0xFFFFF7ED) // Orange
                else -> Color(0xFFF0FDF4) // Green
            }
            val badgeTextColor = when {
                profile.status == "EXPIRED" -> Color(0xFFE11D48)
                profile.accessType == "TRIAL" -> Color(0xFFC2410C)
                else -> Color(0xFF15803D)
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
                isActive -> SwimBlueMain.copy(alpha = 0.1f)
                isError -> Color(0xFFFFE4E6)
                else -> Color(0xFFF1F5F9)
            }
            
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .scale(pulseScale)
                    .shadow(
                        elevation = if (isActive) 32.dp else 0.dp,
                        shape = CircleShape,
                        spotColor = SwimBlueMain.copy(alpha = 0.4f)
                    )
                    .clip(CircleShape)
                    .background(circleOuterColor)
                    .clickable(
                        enabled = profile.status != "EXPIRED" && vpnState != VpnState.DISCONNECTING,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
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
                // Background Circle with Gradient Border
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = when {
                                    isActive -> listOf(SwimBlueMain, SwimBlueFace)
                                    isError -> listOf(Color(0xFFFB7185), Color(0xFFE11D48))
                                    else -> listOf(Color(0xFFE2E8F0), Color(0xFFF1F5F9))
                                }
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTransitioning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(120.dp),
                            color = SwimBlueMain.copy(alpha = 0.2f),
                            strokeWidth = 2.dp,
                            trackColor = Color.Transparent
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Connect",
                        modifier = Modifier.size(88.dp),
                        tint = when {
                            vpnState == VpnState.CONNECTED -> SwimBlueMain
                            isTransitioning -> SwimBlueMain.copy(alpha = 0.5f)
                            isError -> Color(0xFFE11D48)
                            else -> Color(0xFFCBD5E1)
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
                        color = SwimBlueMain
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
                        color = Color(0xFF0F172A),
                        letterSpacing = (-0.5).sp
                    )
                )
            }

            Text(
                text = connectionSubtitle,
                color = Color(0xFF64748B),
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
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(32.dp))
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = stringResource(R.string.label_download), 
                    value = formatBytes(bytesIn), 
                    icon = Icons.Default.ArrowDownward, 
                    color = Color(0xFF22C55E)
                )
                Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color(0xFFE2E8F0)))
                StatItem(
                    label = stringResource(R.string.label_upload), 
                    value = formatBytes(bytesOut),
                    icon = Icons.Default.ArrowUpward, 
                    color = SwimBlueMain
                )
            }

            Spacer(modifier = Modifier.height(96.dp))
        }

        FloatingActionButton(
            onClick = onNavigateImport,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            containerColor = SwimBlueMain,
            contentColor = Color.White,
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Quick actions", modifier = Modifier.size(30.dp))
        }
    }
}

private fun buildRuntimeDiagnostics(metrics: RuntimeMetrics): String {
    val lines = mutableListOf<String>()
    metrics.activeMode?.let { lines += "Mode: $it" }
    metrics.xraySessionId?.let { lines += "Xray session: $it" }
    metrics.xrayLogPath?.let { lines += "Xray log: $it" }
    metrics.tun2SocksSessionId?.let { lines += "tun2socks session: $it" }
    metrics.tun2SocksLogPath?.let { lines += "tun2socks log: $it" }
    metrics.lastError?.let { lines += "Last error: $it" }
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(28.dp))
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
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                val serverLabel = server?.countryCode ?: "--"
                Text(
                    text = serverLabel.uppercase(),
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SELECTED SERVER",
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (server != null) "${server.country}, ${server.city}" else "No server selected",
                    color = Color(0xFF0F172A),
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
                        "Choose an active route before connecting"
                    },
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Open server list",
                tint = Color(0xFF0F172A)
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
            Text(label, color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = Color(0xFF0F172A)))
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
                            .makeText(context, "QR code is empty", android.widget.Toast.LENGTH_SHORT)
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
                        .makeText(context, "Scanner unavailable on this device", android.widget.Toast.LENGTH_SHORT)
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
                Text("Opening scanner...", color = Color.White)
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.btn_retry)) }
        }
    }
}
