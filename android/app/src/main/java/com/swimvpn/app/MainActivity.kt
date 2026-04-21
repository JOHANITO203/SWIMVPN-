@file:Suppress("SpellCheckingInspection")
package com.swimvpn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.ui.screens.*
import com.swimvpn.app.ui.theme.*
import com.swimvpn.app.vpn.VpnManager
import com.swimvpn.app.vpn.VpnState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effect.collect { effect ->
                    when (effect) {
                        is AppSideEffect.OpenUrl -> {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(effect.url))
                            startActivity(intent)
                        }
                        is AppSideEffect.ShowToast -> {
                            android.widget.Toast.makeText(this@MainActivity, effect.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        // Persist language on startup
        val prefs = PreferencesManager(this)
        val lang = runBlocking { prefs.languageFlow.first() }
        applyLocale(lang)

        setContent {
            SwimVpnTheme {
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
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val navController = rememberNavController()

    // Navigation trigger based on State
    LaunchedEffect(state) {
        if (state is AppState.Success) {
            val successState = state as AppState.Success
            if (!successState.isOnboardingDone) {
                navController.navigate("onboarding") { popUpTo(0) }
            } else if (navController.currentDestination?.route == "loading") {
                navController.navigate("home") { popUpTo(0) }
            }
        }
    }

    NavHost(navController = navController, startDestination = "loading") {
        composable("loading") { SplashScreen() }
        composable("onboarding") { 
            OnboardingScreen(onFinish = { 
                viewModel.completeOnboarding()
                navController.navigate("home") { popUpTo(0) }
            }) 
        }
        composable("home") { 
            val data = state as? AppState.Success ?: return@composable
            HomeScreen(
                viewModel = viewModel, 
                data = data,
                onNavigateProfile = { navController.navigate("profile") },
                onNavigateServers = { navController.navigate("servers") }
            ) 
        }
        composable("servers") { 
            val data = state as? AppState.Success ?: return@composable
            ServersScreen(servers = data.servers, onBack = { navController.popBackStack() }) 
        }
        composable("profile") { 
            val data = state as? AppState.Success ?: return@composable
            val bytesIn by VpnManager.bytesIn.collectAsState()
            val bytesOut by VpnManager.bytesOut.collectAsState()
            
            ProfileScreen(
                profile = data.profile,
                bytesIn = bytesIn,
                bytesOut = bytesOut,
                onNavigateToSubscription = { navController.navigate("subscription") },
                onNavigateToTechnical = { navController.navigate("technical") },
                onNavigateToImport = { navController.navigate("import") },
                onNavigateToSupport = { navController.navigate("support") },
                onBack = { navController.popBackStack() }
            ) 
        }
        composable("support") {
            SupportScreen(
                onNavigateToSubscription = { navController.navigate("subscription") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("import") {
            ImportMenuSheet(viewModel = viewModel, onDismiss = { navController.popBackStack() })
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
            val data = state as? AppState.Success ?: return@composable
            TechnicalSettingsScreen(
                routingMode = data.routingMode,
                autoConnect = data.autoConnect,
                language = data.language,
                onRoutingModeChange = { viewModel.setRoutingMode(it) },
                onAutoConnectChange = { viewModel.setAutoConnect(it) },
                onLanguageChange = { viewModel.setLanguage(it) },
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
fun HomeScreen(viewModel: MainViewModel, data: AppState.Success, onNavigateProfile: () -> Unit, onNavigateServers: () -> Unit = {}) {
    val (showImportSheet, setShowImportSheet) = remember { mutableStateOf(false) }

    val profile = data.profile
    val activeServer = data.servers.firstOrNull()

    // Lier l'UI au VRAI statut du service VPN Android
    val vpnState by VpnManager.state.collectAsState()
    val vpnError by VpnManager.errorMessage.collectAsState()
    val bytesIn by VpnManager.bytesIn.collectAsState()
    val bytesOut by VpnManager.bytesOut.collectAsState()

    // Animation pour le bouton Power
    val infiniteTransition = rememberInfiniteTransition(label = "powerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (vpnState == VpnState.CONNECTED) 1.05f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val context = LocalContext.current

    // Launcher pour demander la permission VPN système
    val vpnPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Permission accordée, on démarre le tunnel
            viewModel.toggleVpn(context, activeServer, profile)
        }
    }

    val badgeText = when {
        profile.status == "EXPIRED" -> stringResource(R.string.status_expired)
        profile.planType == "TRIAL" -> stringResource(R.string.status_trial)
        else -> stringResource(R.string.status_pro)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo Requin
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.swimvpn_logo),
                        contentDescription = "Logo",
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SWIMVPN+",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        .clickable { onNavigateProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color(0xFF0F172A))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Trial Banner (Orange)
            if (profile.planType == "TRIAL" || profile.status == "EXPIRED") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFFFF7ED)
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        color = Color(0xFFC2410C),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFF0FDF4)
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        color = Color(0xFF15803D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Big Power Circle
            val circleOuterColor = if (vpnState == VpnState.CONNECTED) 
                SwimBlueMain.copy(alpha = 0.1f) else Color(0xFFF1F5F9)
            
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(pulseScale)
                    .shadow(
                        elevation = if (vpnState == VpnState.CONNECTED) 30.dp else 0.dp,
                        shape = CircleShape,
                        spotColor = SwimBlueMain.copy(alpha = 0.5f)
                    )
                    .clip(CircleShape)
                    .background(circleOuterColor)
                    .clickable(enabled = profile.status != "EXPIRED") {
                        if (vpnState == VpnState.DISCONNECTED || vpnState == VpnState.ERROR) {
                            val intent = android.net.VpnService.prepare(context)
                            if (intent != null) {
                                vpnPermissionLauncher.launch(intent)
                            } else {
                                viewModel.toggleVpn(context, activeServer, profile)
                            }
                        } else {
                            viewModel.toggleVpn(context, activeServer, profile)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = if (vpnState == VpnState.CONNECTED) 
                                    listOf(SwimBlueMain, SwimBlueFace) else listOf(Color(0xFFE2E8F0), Color(0xFFF1F5F9))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "Power",
                            modifier = Modifier.size(72.dp),
                            tint = if (vpnState == VpnState.CONNECTED) SwimBlueMain else Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (vpnState == VpnState.CONNECTED) stringResource(R.string.btn_connect_on) else stringResource(R.string.btn_connect),
                            color = if (vpnState == VpnState.CONNECTED) SwimBlueMain else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = if (vpnState == VpnState.CONNECTED) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
            )
            
            if (vpnState == VpnState.CONNECTED) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Text(formatBytes(bytesIn), color = Color(0xFF64748B), fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                    Text(formatBytes(bytesOut), color = Color(0xFF64748B), fontSize = 14.sp)
                }
            } else {
                Text(
                    text = stringResource(R.string.msg_tap_to_connect),
                    color = Color(0xFF64748B),
                    fontSize = 16.sp
                )
            }

            if (vpnError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = vpnError!!, color = RedAlert, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Server Selector Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
                    .clickable { onNavigateServers() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Country Code Box
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("GE", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.label_selected_server),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = activeServer?.city?.let { "${activeServer.country}, $it" } ?: "Germany, Frankfurt",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 16.sp
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.label_stable), color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("24ms", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Change Server",
                        tint = Color(0xFF0F172A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
        }

        // Action Flottante (Bouton + Massif)
        FloatingActionButton(
            onClick = { setShowImportSheet(true) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(72.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = ElectricBlue,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Import", modifier = Modifier.size(32.dp))
        }
    }

    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { setShowImportSheet(false) },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
        ) {
            ImportMenuSheet(viewModel) { setShowImportSheet(false) }
        }
    }
}

@Composable
fun ImportMenuSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp)
    ) {
        // Top Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.import_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF94A3B8))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.label_quick_actions),
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Grid of 4
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ImportMethodCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ContentPaste,
                title = stringResource(R.string.btn_paste),
                onClick = {
                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    if (clipboardManager.hasPrimaryClip() && (clipboardManager.primaryClip?.itemCount ?: 0) > 0) {
                        val pasteData = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!pasteData.isNullOrEmpty()) {
                            inputText = pasteData
                        }
                    }
                }
            )
            ImportMethodCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.QrCode,
                title = stringResource(R.string.btn_scan_qr),
                onClick = {
                    android.widget.Toast.makeText(context, "QR Scanner coming soon", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ImportMethodCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ConfirmationNumber,
                title = stringResource(R.string.btn_coupon),
                onClick = {
                    inputText = "SWIM-"
                }
            )
            ImportMethodCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Add,
                title = stringResource(R.string.btn_manual),
                onClick = {
                    inputText = ""
                }
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.label_direct_input),
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text(stringResource(R.string.input_placeholder), color = Color(0xFF94A3B8)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = ElectricBlue,
                    focusedTextColor = Color(0xFF0F172A),
                    unfocusedTextColor = Color(0xFF0F172A)
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (inputText.isNotBlank()) {
                    if (inputText.startsWith("SWIM-", ignoreCase = true)) {
                        viewModel.activateCode(inputText.uppercase())
                    } else {
                        viewModel.importUrl(inputText)
                    }
                    onDismiss()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
            enabled = inputText.isNotBlank()
        ) {
            Text(
                text = stringResource(R.string.btn_activate),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    }
}


@Composable
fun ImportMethodCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier.height(120.dp).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp)).clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = ElectricBlue)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontSize = 12.sp, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, contentDescription = "Error", tint = RedAlert, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.btn_retry)) }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
