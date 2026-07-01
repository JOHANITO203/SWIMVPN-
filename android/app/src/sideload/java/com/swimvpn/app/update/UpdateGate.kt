package com.swimvpn.app.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swimvpn.app.R
import com.swimvpn.app.data.local.PreferencesManager
import kotlinx.coroutines.launch

/**
 * Porte de mise à jour (flavor sideload) — enveloppe le contenu de l'app :
 * - check au launch (throttlé par [UpdateChecker]) ;
 * - update OPTIONNELLE → dialog refusable (versionCode mémorisé, pas de re-nag) ;
 * - update OBLIGATOIRE (< minSupportedCode) → écran bloquant, seule action = mettre à jour.
 * Un tap mène au dialog d'installation SYSTÈME (pas d'installation silencieuse en sideload).
 * Le flavor play remplace ce fichier par un stub no-op.
 */
@Composable
fun UpdateGate(prefs: PreferencesManager, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var decision by remember { mutableStateOf<UpdateDecision?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val language by prefs.languageFlow.collectAsState(initial = PreferencesManager.DEFAULT_LANGUAGE)

    LaunchedEffect(Unit) {
        decision = runCatching { UpdateChecker(prefs).checkIfDue() }.getOrNull()
    }

    fun startInstall(manifest: UpdateManifest) {
        if (busy) return
        busy = true
        errorRes = null
        progress = 0
        scope.launch {
            val result = runCatching {
                ApkInstaller(context.applicationContext).downloadAndInstall(manifest) { progress = it }
            }.getOrDefault(InstallResult.DownloadFailed)
            busy = false
            errorRes = when (result) {
                InstallResult.InstallerLaunched -> null
                InstallResult.NeedsInstallPermission -> R.string.update_permission_needed
                InstallResult.DownloadFailed -> R.string.update_download_failed
                InstallResult.VerifyFailed -> R.string.update_verify_failed
            }
        }
    }

    @Composable
    fun details(manifest: UpdateManifest) {
        val changelog = manifest.changelogFor(language)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.update_available_message, manifest.versionName))
            if (changelog.isNotBlank()) {
                Text(changelog, style = MaterialTheme.typography.bodyMedium)
            }
            if (busy) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
    }

    content()

    when (val current = decision) {
        is UpdateDecision.Optional -> AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    scope.launch { prefs.setUpdateDismissedCode(current.manifest.latestVersionCode) }
                    decision = null
                }
            },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { details(current.manifest) },
            confirmButton = {
                Button(onClick = { startInstall(current.manifest) }, enabled = !busy) {
                    Text(stringResource(R.string.update_action_now))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch { prefs.setUpdateDismissedCode(current.manifest.latestVersionCode) }
                        decision = null
                    },
                ) {
                    Text(stringResource(R.string.update_action_later))
                }
            },
        )

        is UpdateDecision.Mandatory -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(R.string.update_required_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.update_required_message),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    details(current.manifest)
                    Button(onClick = { startInstall(current.manifest) }, enabled = !busy) {
                        Text(stringResource(R.string.update_action_now))
                    }
                }
            }
        }

        else -> Unit
    }
}
