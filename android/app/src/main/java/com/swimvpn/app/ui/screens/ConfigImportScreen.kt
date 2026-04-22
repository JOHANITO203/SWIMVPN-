package com.swimvpn.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swimvpn.app.QrScannerView
import com.swimvpn.app.config.ConfigPreview
import com.swimvpn.app.config.ConfigRepository
import com.swimvpn.app.config.SwimVpnProfile
import com.swimvpn.app.ui.components.ClipboardImportSheet
import com.swimvpn.app.ui.components.ConfigPreviewCard
import com.swimvpn.app.ui.components.ImportConfigDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigImportScreen(
    configRepository: ConfigRepository,
    onBack: () -> Unit,
    onProfileSelected: (SwimVpnProfile) -> Unit = {},
    onImportToProfile: (String) -> Unit = {},
    showToast: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // State
    var showImportDialog by remember { mutableStateOf(false) }
    var showClipboardSheet by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importPreview by remember { mutableStateOf<ConfigPreview?>(null) }
    var importedProfiles by remember { mutableStateOf<List<SwimVpnProfile>>(emptyList()) }
    var activeProfileId by remember { mutableStateOf<String?>(null) }
    
    // Loading states
    var isImporting by remember { mutableStateOf(false) }
    var isDeletingProfile by remember { mutableStateOf(false) }
    var isCheckingClipboard by remember { mutableStateOf(false) }
    var isInitialLoading by remember { mutableStateOf(true) }
    
    // Clipboard state
    var clipboardContent by remember { mutableStateOf<String?>(null) }
    var clipboardPreview by remember { mutableStateOf<ConfigPreview?>(null) }
    var isClipboardConfigDetected by remember { mutableStateOf(false) }
    
    // Load profiles on composition
    LaunchedEffect(Unit) {
        isInitialLoading = true
        try {
            importedProfiles = configRepository.getAllProfiles()
            activeProfileId = configRepository.getActiveProfile()?.id
        } finally {
            isInitialLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Access Configurations",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (isInitialLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Empty state
                    if (importedProfiles.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(72.dp)
                                )
                                Text(
                                    text = "No configurations imported",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Choose one of the methods below to import a VPN configuration",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Import methods section
                    item {
                        Text(
                            text = "Import Methods",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Paste from clipboard
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                                    .clickable {
                                        scope.launch {
                                            // Check clipboard content
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = clipboard.primaryClip
                                            clipboardContent = clip?.getItemAt(0)?.text?.toString()
                                            
                                            if (clipboardContent != null) {
                                                val checkResult = configRepository.checkClipboardForConfig(clipboardContent)
                                                isClipboardConfigDetected = when (checkResult) {
                                                    is com.swimvpn.app.config.ClipboardCheckResult.ValidConfig -> {
                                                        clipboardPreview = checkResult.preview
                                                        true
                                                    }
                                                    else -> false
                                                }
                                                showClipboardSheet = true
                                            } else {
                                                // Clipboard is empty, show import dialog instead
                                                showImportDialog = true
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Paste",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                            
                            // QR code scanner
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                                    .clickable {
                                        showQrScanner = true
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "QR Code",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clickable {
                                    showImportDialog = true
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Manual Input",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    
                    // Imported configurations section
                    if (importedProfiles.isNotEmpty()) {
                        item {
                            Text(
                                text = "Imported Configurations (${importedProfiles.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                        
                        items(importedProfiles) { profile ->
                            val preview = remember(profile) {
                                ConfigPreview(
                                    protocol = profile.protocol.name,
                                    address = profile.address,
                                    port = profile.port,
                                    transport = profile.transport.name,
                                    security = profile.securityMode.name,
                                    displayName = profile.displayName,
                                    validationStatus = if (profile.parseErrors.isEmpty()) {
                                        if (profile.parseWarnings.isEmpty()) 
                                            com.swimvpn.app.config.ValidationStatus.VALID 
                                        else 
                                            com.swimvpn.app.config.ValidationStatus.WARNING
                                    } else {
                                        com.swimvpn.app.config.ValidationStatus.ERROR
                                    },
                                    warnings = profile.parseWarnings,
                                    summary = "${profile.protocol.name} to ${profile.address}:${profile.port} via ${profile.transport.name}"
                                )
                            }
                            
                            ConfigPreviewCard(
                                preview = preview,
                                isSelected = profile.id == activeProfileId,
                                onClick = {
                                    scope.launch {
                                        configRepository.setActiveProfile(profile)
                                        activeProfileId = profile.id
                                        onProfileSelected(profile)
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        configRepository.deleteProfile(profile)
                                        importedProfiles = configRepository.getAllProfiles()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Import dialog
    if (showImportDialog) {
        ImportConfigDialog(
            onDismiss = { 
                @Suppress("UNUSED_VALUE")
                showImportDialog = false 
            },
            onImport = { text ->
                scope.launch {
                    when (val result = configRepository.importConfig(text)) {
                        is com.swimvpn.app.config.ImportResult.Success -> {
                            importedProfiles = configRepository.getAllProfiles()
                            @Suppress("UNUSED_VALUE")
                            showImportDialog = false
                            onImportToProfile(text)
                            showToast("Configuration imported successfully")
                        }
                        is com.swimvpn.app.config.ImportResult.Error -> {
                            showToast("Import error: ${result.errors.firstOrNull() ?: "Unknown error"}")
                        }
                        is com.swimvpn.app.config.ImportResult.Duplicate -> {
                            showToast("This configuration is already imported")
                        }
                    }
                }
            },
            onTextChange = { text ->
                importText = text
            },
            initialText = importText,
            preview = importPreview
        )
    }
    
    // Clipboard import sheet
    if (showClipboardSheet) {
        @Suppress("UNUSED_VALUE")
        val onDismissClipboardSheet = { showClipboardSheet = false }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismissClipboardSheet
        ) {
            ClipboardImportSheet(
                onDismiss = onDismissClipboardSheet,
                onImport = { content ->
                    scope.launch {
                        when (val result = configRepository.importConfig(content)) {
                            is com.swimvpn.app.config.ImportResult.Success -> {
                                importedProfiles = configRepository.getAllProfiles()
                                onImportToProfile(content)
                                onDismissClipboardSheet()
                                showToast("Configuration imported from clipboard")
                            }
                            is com.swimvpn.app.config.ImportResult.Error -> {
                                showToast("Clipboard import error: ${result.errors.firstOrNull() ?: "Unknown error"}")
                            }
                            is com.swimvpn.app.config.ImportResult.Duplicate -> {
                                showToast("This configuration is already imported")
                            }
                        }
                    }
                },
                clipboardContent = clipboardContent,
                isConfigDetected = isClipboardConfigDetected,
                preview = clipboardPreview
            )
        }
    }
    
    // QR scanner
    if (showQrScanner) {
        @Suppress("UNUSED_VALUE")
        val onCloseQrScanner = { showQrScanner = false }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onCloseQrScanner
        ) {
            QrScannerView(
                onCodeScanned = { qrText ->
                    scope.launch {
                        when (val result = configRepository.importConfig(qrText)) {
                            is com.swimvpn.app.config.ImportResult.Success -> {
                                importedProfiles = configRepository.getAllProfiles()
                                onImportToProfile(qrText)
                                onCloseQrScanner()
                                showToast("Configuration imported from QR code")
                            }
                            is com.swimvpn.app.config.ImportResult.Error -> {
                                showToast("QR import error: ${result.errors.firstOrNull() ?: "Unknown error"}")
                            }
                            is com.swimvpn.app.config.ImportResult.Duplicate -> {
                                showToast("This configuration is already imported")
                            }
                        }
                    }
                },
                onClose = onCloseQrScanner
            )
        }
    }
    
    // Update preview when text changes
    LaunchedEffect(importText) {
        @Suppress("UNUSED_VALUE")
        importPreview = if (importText.isNotBlank()) {
            configRepository.previewConfig(importText)
        } else {
            null
        }
    }
}
