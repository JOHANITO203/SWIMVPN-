@file:Suppress("SpellCheckingInspection")
package com.swimvpn.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.material.icons.filled.*
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
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.ui.formatBytes
import com.swimvpn.app.ui.screens.*
import com.swimvpn.app.ui.theme.*
import com.swimvpn.app.vpn.VpnManager
import com.swimvpn.app.vpn.VpnState
import kotlinx.coroutines.flow.first
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

        // Apply language on startup
        val prefs = PreferencesManager(this)
        lifecycleScope.launch { 
            val lang = prefs.languageFlow.first()
            applyLocale(lang)
        }

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
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
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
            ServersScreen(servers = data.servers, onBack = { navController.popBackStack() }, onSelectServer = { server ->
                viewModel.selectServer(server)
                navController.popBackStack()
            }) 
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
                onSignOut = { 
                    viewModel.signOut()
                    navController.navigate("loading") { popUpTo(0) }
                },
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
fun HomeScreen(viewModel: MainViewModel, data: AppState.Success, onNavigateProfile: () -> Unit, onNavigateServers: () -> Unit) {
    val profile = data.profile
    val activeServer = data.activeServer

    // Lier l'UI au VRAI statut du service VPN Android
    val vpnState by VpnManager.state.collectAsState()
    val bytesIn by VpnManager.bytesIn.collectAsState()
    val bytesOut by VpnManager.bytesOut.collectAsState()

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
                profile.planType == "TRIAL" -> Color(0xFFFFF7ED) // Orange
                else -> Color(0xFFF0FDF4) // Green
            }
            val badgeTextColor = when {
                profile.status == "EXPIRED" -> Color(0xFFE11D48)
                profile.planType == "TRIAL" -> Color(0xFFC2410C)
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
            val circleOuterColor = if (isActive) SwimBlueMain.copy(alpha = 0.1f) else Color(0xFFF1F5F9)
            
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
                        .size(210.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = if (isActive) 
                                    listOf(SwimBlueMain, SwimBlueFace) else listOf(Color(0xFFE2E8F0), Color(0xFFF1F5F9))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Connect",
                        modifier = Modifier.size(88.dp),
                        tint = if (isActive) SwimBlueMain else Color(0xFFCBD5E1)
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
                        VpnState.ERROR -> stringResource(R.string.status_error)
                        else -> stringResource(R.string.status_disconnected)
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black, 
                        color = Color(0xFF0F172A),
                        letterSpacing = (-0.5).sp
                    )
                )
            }
            
            if (activeServer != null) {
                Text(
                    text = "${activeServer.country}, ${activeServer.city} (${activeServer.host})",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp).clickable { onNavigateServers() }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportMenuSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var showActivateCode by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        if (showActivateCode) {
            ActivateCodeDialog(
                onDismiss = { showActivateCode = false },
                onActivate = { code ->
                    viewModel.activateCode(code)
                    showActivateCode = false
                    onDismiss()
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 8.dp)
            ) {
                Text(
                    stringResource(R.string.title_import_method),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    ImportMethodCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.QrCodeScanner,
                        title = stringResource(R.string.method_qr),
                        onClick = { /* OCR/QR later */ }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    ImportMethodCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Link,
                        title = stringResource(R.string.method_url),
                        onClick = { /* Clipboard later */ }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                ImportMethodCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.ConfirmationNumber,
                    title = stringResource(R.string.method_code),
                    onClick = { showActivateCode = true }
                )
            }
        }
    }
}

@Composable
fun ActivateCodeDialog(onDismiss: () -> Unit, onActivate: (String) -> Unit) {
    var inputText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) { }
                .background(Color.White, RoundedCornerShape(32.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.btn_activate_coupon), fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("XXXX-XXXX-XXXX") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onActivate(inputText) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SwimBlueMain),
                enabled = inputText.isNotBlank()
            ) {
                Text(stringResource(R.string.btn_activate), color = Color.White, fontWeight = FontWeight.Black)
            }
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    }
}

@Composable
fun ImportMethodCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        modifier = modifier.height(100.dp).clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = SwimBlueMain)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF475569), fontSize = 12.sp)
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
