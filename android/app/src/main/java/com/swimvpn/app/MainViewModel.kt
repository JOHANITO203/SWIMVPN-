package com.swimvpn.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.data.local.AutoConnectPayload
import com.swimvpn.app.data.local.DeviceIdentityProvider
import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.data.network.ActivateCodeRequest
import com.swimvpn.app.data.network.ActivateTrialRequest
import com.swimvpn.app.data.network.BootstrapAccessRequest
import com.swimvpn.app.data.network.CompleteProfileRequest
import com.swimvpn.app.data.model.CheckoutRequest
import com.swimvpn.app.data.network.ReportUsageRequest
import com.swimvpn.app.data.network.RetrofitClient
import com.swimvpn.app.data.network.ServerGroup
import com.swimvpn.app.data.network.ServerLatencyEvaluator
import com.swimvpn.app.data.network.ServerNode
import com.swimvpn.app.config.ConfigRepository
import com.swimvpn.app.config.ActiveConfigMetadata
import com.swimvpn.app.config.ImportedProfileGroup
import com.swimvpn.app.config.SecurityMode
import com.swimvpn.app.config.SwimVpnProfile
import com.swimvpn.app.vpn.RuntimeMode
import com.swimvpn.app.vpn.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class AppState {
    object Loading : AppState()
    data class TrialSetup(
        val userNumber: String,
        val email: String?,
        val phone: String?,
        val trialEligible: Boolean,
        val isOnboardingDone: Boolean,
        val routingMode: RuntimeMode,
        val autoConnect: Boolean,
        val language: String,
        val themeMode: ThemeMode,
    ) : AppState()

    data class Success(
        val profile: AccessProfileResponse,
        val activeConfigMetadata: ActiveConfigMetadata? = null,
        val servers: List<ServerNode>,
        val serverGroups: List<ServerGroup>,
        val plans: List<com.swimvpn.app.data.model.Plan>,
        val isOnboardingDone: Boolean,
        val routingMode: RuntimeMode,
        val autoConnect: Boolean,
        val language: String,
        val themeMode: ThemeMode,
        val activeServer: ServerNode? = null
    ) : AppState()

    data class Error(val message: String) : AppState()
}

sealed class AppSideEffect {
    data class OpenUrl(val url: String) : AppSideEffect()
    data class ShowToast(val message: String) : AppSideEffect()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val prefs = PreferencesManager(application)
    private val api = RetrofitClient.apiService
    private val configRepository = ConfigRepository(application)

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<AppSideEffect>()
    val effect: SharedFlow<AppSideEffect> = _effect.asSharedFlow()

    private var lastAutoConnectSignature: String? = null

    init {
        initApp()
    }

    private fun s(resId: Int, vararg args: Any): String = app.getString(resId, *args)

    private fun initApp() {
        viewModelScope.launch {
            try {
                _state.value = AppState.Loading

                val isOnboardingDone = prefs.onboardingDoneFlow.first()
                val routingMode = prefs.runtimeModeFlow.first()
                val autoConnect = prefs.autoConnectFlow.first()
                val deviceId = getDeviceId()
                val language = prefs.languageFlow.first()
                val themeMode = prefs.themeModeFlow.first()

                if (deviceId == null) {
                    Log.e("MainViewModel", "Device identity unavailable during bootstrap")
                    _state.value = AppState.Error(s(R.string.err_bootstrap_failed))
                    return@launch
                }

                Log.d("MainViewModel", "Bootstrapping access for locale: $language")

                val bootstrap = try {
                    api.bootstrapAccess(
                        BootstrapAccessRequest(
                            deviceId = deviceId,
                            locale = language,
                        )
                    )
                } catch (e: retrofit2.HttpException) {
                    if (BuildConfig.DEBUG) {
                        val errorBody = e.response()?.errorBody()?.string()
                        Log.e("MainViewModel", "HTTP ${e.code()} bootstrapping access: $errorBody", e)
                    } else {
                        Log.e("MainViewModel", "HTTP ${e.code()} bootstrapping access", e)
                    }
                    val msg = if (e.code() >= 500) {
                        s(R.string.err_server_maintenance, e.code())
                    } else {
                        s(R.string.err_bootstrap_failed)
                    }
                    _state.value = AppState.Error(msg)
                    return@launch
                } catch (e: java.net.SocketTimeoutException) {
                    Log.e("MainViewModel", "Timeout bootstrapping access", e)
                    _state.value = AppState.Error(s(R.string.err_network_timeout))
                    return@launch
                } catch (e: Exception) {
                    Log.e("MainViewModel", "API Error bootstrapping access", e)
                    _state.value = AppState.Error(s(R.string.err_bootstrap_failed))
                    return@launch
                }

                prefs.saveUserNumber(bootstrap.userNumber)

                val shouldGoToHome = bootstrap.profile != null &&
                    !bootstrap.profile.requiresProfileCompletion

                if (shouldGoToHome) {
                    val successState = buildSuccessState(
                        profile = bootstrap.profile!!,
                        isOnboardingDone = isOnboardingDone,
                        routingMode = routingMode,
                        autoConnect = autoConnect,
                        language = language,
                        themeMode = themeMode,
                    )

                    if (successState == null) {
                        return@launch
                    }

                    _state.value = successState
                    refreshServerLatency()
                } else {
                    _state.value = AppState.TrialSetup(
                        userNumber = bootstrap.userNumber,
                        email = bootstrap.email,
                        phone = bootstrap.phone,
                        trialEligible = bootstrap.trialEligible,
                        isOnboardingDone = isOnboardingDone,
                        routingMode = routingMode,
                        autoConnect = autoConnect,
                        language = language,
                        themeMode = themeMode,
                    )
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error initApp", e)
                _state.value = AppState.Error(s(R.string.err_generic, e.localizedMessage ?: "unknown"))
            }
        }
    }

    fun setRoutingMode(mode: String) {
        viewModelScope.launch {
            val runtimeMode = when (mode) {
                "TUNNEL" -> RuntimeMode.FULL_TUNNEL
                "PROXY" -> RuntimeMode.LOCAL_PROXY
                else -> RuntimeMode.fromPersisted(mode)
            }
            prefs.setRuntimeMode(runtimeMode)
            when (val currentState = _state.value) {
                is AppState.Success -> _state.value = currentState.copy(routingMode = runtimeMode)
                is AppState.TrialSetup -> _state.value = currentState.copy(routingMode = runtimeMode)
                else -> {}
            }
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAutoConnect(enabled)
            when (val currentState = _state.value) {
                is AppState.Success -> _state.value = currentState.copy(autoConnect = enabled)
                is AppState.TrialSetup -> _state.value = currentState.copy(autoConnect = enabled)
                else -> {}
            }
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            prefs.setLanguage(lang)
            when (val currentState = _state.value) {
                is AppState.Success -> _state.value = currentState.copy(language = lang)
                is AppState.TrialSetup -> _state.value = currentState.copy(language = lang)
                else -> {}
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
            when (val currentState = _state.value) {
                is AppState.Success -> _state.value = currentState.copy(themeMode = mode)
                is AppState.TrialSetup -> _state.value = currentState.copy(themeMode = mode)
                else -> {}
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            prefs.setOnboardingDone()
            when (val currentState = _state.value) {
                is AppState.Success -> _state.value = currentState.copy(isOnboardingDone = true)
                is AppState.TrialSetup -> _state.value = currentState.copy(isOnboardingDone = true)
                else -> {}
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.value = AppState.Loading
            lastAutoConnectSignature = null
            prefs.saveUserNumber("NEW_USER")
            prefs.setOnboardingDone(false)
            prefs.clearAutoConnectPayload()
            initApp()
        }
    }

    fun activateTrial(email: String, phone: String) {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.TrialSetup ?: return@launch

            try {
                _state.value = AppState.Loading
                val deviceId = getDeviceId()
                if (deviceId == null) {
                    Log.e("MainViewModel", "Device identity unavailable during trial activation")
                    _state.value = currentState
                    _effect.emit(AppSideEffect.ShowToast(s(R.string.err_trial_activation)))
                    return@launch
                }

                val profile = api.activateTrial(
                    ActivateTrialRequest(
                        userNumber = currentState.userNumber,
                        deviceId = deviceId,
                        email = email,
                        phone = phone,
                    )
                )

                val successState = buildSuccessState(
                    profile = profile,
                    isOnboardingDone = currentState.isOnboardingDone,
                    routingMode = currentState.routingMode,
                    autoConnect = currentState.autoConnect,
                    language = currentState.language,
                    themeMode = currentState.themeMode,
                )

                if (successState == null) {
                    return@launch
                }

                _state.value = successState
                refreshServerLatency()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Trial activation failed", e)
                _state.value = currentState
                val message = extractApiErrorMessage(e)?.takeIf { it.isNotBlank() }
                    ?.let { s(R.string.err_activation_failed, it) }
                    ?: s(R.string.err_trial_activation)
                _effect.emit(AppSideEffect.ShowToast(message))
            }
        }
    }

    fun continueFreemiumFromTrialSetup(email: String? = null, phone: String? = null) {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.TrialSetup ?: return@launch

            try {
                _state.value = AppState.Loading
                val deviceId = getDeviceId()
                if (deviceId == null) {
                    Log.e("MainViewModel", "Device identity unavailable while continuing freemium")
                    _state.value = currentState
                    _effect.emit(AppSideEffect.ShowToast(s(R.string.err_bootstrap_failed)))
                    return@launch
                }

                val profile = api.completeProfile(
                    CompleteProfileRequest(
                        userNumber = currentState.userNumber,
                        deviceId = deviceId,
                        email = email?.trim()?.takeIf { it.isNotBlank() },
                        phone = phone?.trim()?.takeIf { it.isNotBlank() },
                    )
                )
                val successState = buildSuccessState(
                    profile = profile,
                    isOnboardingDone = currentState.isOnboardingDone,
                    routingMode = currentState.routingMode,
                    autoConnect = currentState.autoConnect,
                    language = currentState.language,
                    themeMode = currentState.themeMode,
                )

                if (successState == null) {
                    _state.value = currentState
                    return@launch
                }

                _state.value = successState
                refreshServerLatency()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Unable to continue in freemium mode", e)
                _state.value = currentState
                _effect.emit(AppSideEffect.ShowToast(s(R.string.err_bootstrap_failed)))
            }
        }
    }

    fun importVless(url: String) {
        refreshImportedServers()
    }

    fun refreshImportedServers() {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.Success ?: return@launch
            Log.i("MainViewModel", "Local imported config refreshed without mutating backend inventory")
            _state.value = refreshSuccessState(currentState)
            refreshServerLatency()
        }
    }

    fun activateTrialFromProfile() {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.Success ?: return@launch
            val email = currentState.profile.email?.trim().orEmpty()
            val phone = currentState.profile.phone?.trim().orEmpty()
            if (email.isBlank() || phone.isBlank()) {
                _effect.emit(AppSideEffect.ShowToast(s(R.string.err_trial_activation)))
                return@launch
            }

            try {
                _state.value = AppState.Loading
                val deviceId = getDeviceId()
                if (deviceId == null) {
                    Log.e("MainViewModel", "Device identity unavailable during profile trial activation")
                    _state.value = currentState
                    _effect.emit(AppSideEffect.ShowToast(s(R.string.err_trial_activation)))
                    return@launch
                }

                val profile = api.activateTrial(
                    ActivateTrialRequest(
                        userNumber = currentState.profile.userNumber,
                        deviceId = deviceId,
                        email = email,
                        phone = phone,
                    )
                )

                val successState = buildSuccessState(
                    profile = profile,
                    isOnboardingDone = currentState.isOnboardingDone,
                    routingMode = currentState.routingMode,
                    autoConnect = currentState.autoConnect,
                    language = currentState.language,
                    themeMode = currentState.themeMode,
                )

                if (successState == null) {
                    return@launch
                }

                _state.value = successState
                refreshServerLatency()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Profile trial activation failed", e)
                _state.value = currentState
                val message = extractApiErrorMessage(e)?.takeIf { it.isNotBlank() }
                    ?.let { s(R.string.err_activation_failed, it) }
                    ?: s(R.string.err_trial_activation)
                _effect.emit(AppSideEffect.ShowToast(message))
            }
        }
    }


    private fun extractApiErrorMessage(error: Exception): String? {
        val httpException = error as? retrofit2.HttpException ?: return error.localizedMessage
        val body = httpException.response()?.errorBody()?.string().orEmpty()
        if (body.isBlank()) {
            return httpException.message()
        }

        val patterns = listOf(
            Regex(""""message"\s*:\s*"([^"]+)"""),
            Regex(""""error"\s*:\s*"([^"]+)"""),
        )
        patterns.forEach { pattern ->
            val match = pattern.find(body)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return body.take(160)
    }

    fun createCheckout(planId: String, paymentMethod: String, cryptoAsset: String? = null) {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.Success ?: return@launch
            try {
                val request = CheckoutRequest(
                    email = currentState.profile.email,
                    phone = currentState.profile.phone,
                    planId = planId,
                    paymentMethod = paymentMethod,
                    cryptoAsset = cryptoAsset,
                )
                val response = api.createCheckout(request)
                response.redirectUrl?.let { redirectUrl ->
                    _effect.emit(AppSideEffect.OpenUrl(redirectUrl))
                }
                _effect.emit(
                    AppSideEffect.ShowToast(
                        response.message?.takeIf { it.isNotBlank() }
                            ?: s(R.string.order_created_pending_payment, response.orderRef)
                    )
                )
            } catch (e: Exception) {
                _effect.emit(
                    AppSideEffect.ShowToast(
                        s(R.string.err_order_creation_failed, extractApiErrorMessage(e) ?: "unknown")
                    )
                )
            }
        }
    }

    fun selectServer(server: ServerNode) {
        viewModelScope.launch {
            prefs.setSelectedServerId(server.id)
            when (server.source) {
                "imported" -> {
                    configRepository.getImportedProfileForServerId(server.id)?.let { profile ->
                        configRepository.setActiveProfile(profile)
                    }
                }
                "backend" -> configRepository.clearActiveProfile()
            }
            val current = _state.value
            if (current is AppState.Success) {
                val activeConfigMetadata = resolveActiveConfigMetadata(server, current.profile)
                _state.value = current.copy(
                    activeServer = server,
                    activeConfigMetadata = activeConfigMetadata,
                    servers = current.servers.map { it.copy(isPinned = it.isPinned) },
                )
            }
        }
    }

    fun selectImportedProfile(profile: SwimVpnProfile) {
        viewModelScope.launch {
            val current = _state.value as? AppState.Success ?: return@launch
            val targetId = ConfigRepository.importedServerIdFor(profile.id)
            configRepository.setActiveProfile(profile)
            prefs.setSelectedServerId(targetId)
            _state.value = refreshSuccessState(current)
            refreshServerLatency()
        }
    }

    fun toggleServerPin(server: ServerNode) {
        viewModelScope.launch {
            val current = _state.value as? AppState.Success ?: return@launch
            prefs.togglePinnedServerId(server.id)
            _state.value = refreshSuccessState(current)
        }
    }

    fun refreshServerLatency() {
        viewModelScope.launch {
            val current = _state.value as? AppState.Success ?: return@launch
            val measuredServers = ServerLatencyEvaluator.enrichWithLatency(current.servers)
            val byId = measuredServers.associateBy { it.id }
            val measuredGroups = current.serverGroups.map { group ->
                group.copy(
                    servers = group.servers.map { server -> byId[server.id] ?: server }
                )
            }
            val activeServer = current.activeServer?.id?.let(byId::get) ?: current.activeServer
            _state.value = current.copy(
                servers = measuredServers,
                serverGroups = measuredGroups,
                activeServer = activeServer,
            )
        }
    }

    fun retry() {
        initApp()
    }

    fun maybeAutoConnect(context: Context, successState: AppState.Success) {
        if (!successState.autoConnect) return
        if (successState.routingMode == RuntimeMode.FULL_TUNNEL && VpnService.prepare(context) != null) return
        val server = successState.activeServer ?: return
        val profile = successState.profile
        val runtimeConfig = server.rawConfig ?: profile.subscriptionUrl
        if ((!profile.isPremiumAllowed && server.source == "backend") || runtimeConfig.isNullOrBlank()) return

        val vpnState = com.swimvpn.app.vpn.VpnManager.state.value
        if (vpnState != com.swimvpn.app.vpn.VpnState.DISCONNECTED && vpnState != com.swimvpn.app.vpn.VpnState.ERROR) {
            return
        }

        val signature = listOf(profile.userNumber, server.id, runtimeConfig).joinToString(":")
        if (lastAutoConnectSignature == signature) return

        lastAutoConnectSignature = signature
        toggleVpn(context, server, profile)
    }

    fun maybeRestoreAutoConnectFromBoot(context: Context) {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.Success ?: return@launch
            if (!currentState.autoConnect) return@launch

            val vpnState = com.swimvpn.app.vpn.VpnManager.state.value
            if (vpnState != com.swimvpn.app.vpn.VpnState.DISCONNECTED && vpnState != com.swimvpn.app.vpn.VpnState.ERROR) {
                return@launch
            }

            val payload = prefs.getAutoConnectPayload() ?: return@launch
            if (payload.runtimeMode == RuntimeMode.FULL_TUNNEL && VpnService.prepare(context) != null) {
                return@launch
            }

            val currentServer = currentState.activeServer
            val currentConfig = currentServer?.rawConfig ?: currentState.profile.subscriptionUrl
            if (currentServer != null &&
                currentServer.host == payload.host &&
                currentServer.port == payload.port &&
                currentConfig == payload.runtimeConfig &&
                currentState.routingMode == payload.runtimeMode
            ) {
                maybeAutoConnect(context, currentState)
                return@launch
            }

            Log.i("MainViewModel", "Skipping stale auto-connect payload until bootstrap selects the active server again")
        }
    }


    private suspend fun reportMeasuredUsage(userNumber: String): AccessProfileResponse? {
        val measuredBytes = (com.swimvpn.app.vpn.VpnManager.bytesIn.value + com.swimvpn.app.vpn.VpnManager.bytesOut.value)
            .coerceAtLeast(0L)
        if (measuredBytes <= 0L) {
            return null
        }

        return try {
            api.reportUsage(
                ReportUsageRequest(
                    userNumber = userNumber,
                    measuredUsedBytes = measuredBytes.toString(),
                    deviceId = getDeviceId(),
                )
            )
        } catch (e: Exception) {
            Log.e("MainViewModel", "Usage report failed", e)
            null
        }
    }

    fun toggleVpn(context: android.content.Context, server: ServerNode?, profile: AccessProfileResponse?) {
        val currentState = com.swimvpn.app.vpn.VpnManager.state.value

        if (currentState == com.swimvpn.app.vpn.VpnState.CONNECTED || currentState == com.swimvpn.app.vpn.VpnState.CONNECTING) {
            val successState = _state.value as? AppState.Success
            viewModelScope.launch {
                val refreshedProfile = successState?.profile?.userNumber?.let { reportMeasuredUsage(it) }
                if (successState != null && refreshedProfile != null) {
                    _state.value = refreshSuccessState(successState.copy(profile = refreshedProfile))
                }
                val intent = Intent(context, SwimVpnService::class.java).apply {
                    action = SwimVpnService.ACTION_STOP
                }
                context.startService(intent)
            }
        } else {
            if (server == null || profile == null) {
                viewModelScope.launch { _effect.emit(AppSideEffect.ShowToast(s(R.string.err_no_server_profile))) }
                return
            }

            if (!profile.isPremiumAllowed && server.source == "backend") {
                viewModelScope.launch { _effect.emit(AppSideEffect.ShowToast(s(R.string.err_subscription_expired))) }
                return
            }

            val runtimeConfig = server.rawConfig ?: profile.subscriptionUrl
            if (runtimeConfig.isNullOrBlank()) {
                viewModelScope.launch { _effect.emit(AppSideEffect.ShowToast(s(R.string.err_no_runtime_config))) }
                return
            }

            viewModelScope.launch {
                prefs.saveAutoConnectPayload(
                    AutoConnectPayload(
                        host = server.host,
                        port = server.port,
                        protocol = server.protocol,
                        runtimeConfig = runtimeConfig,
                        runtimeMode = RuntimeMode.fromPersisted(currentStateRoutingModeName()),
                    )
                )

                val intent = Intent(context, SwimVpnService::class.java).apply {
                    action = SwimVpnService.ACTION_START
                    putExtra(SwimVpnService.EXTRA_SERVER_HOST, server.host)
                    putExtra(SwimVpnService.EXTRA_SERVER_PORT, server.port)
                    putExtra(SwimVpnService.EXTRA_PROTOCOL, server.protocol)
                    putExtra(SwimVpnService.EXTRA_URL, runtimeConfig)
                    putExtra(SwimVpnService.EXTRA_RUNTIME_MODE, currentStateRoutingModeName())

                    val limitBytes = if (profile.hasMeasuredLimit) profile.dataLimitBytes else -1L
                    val usedBytes = profile.totalConsumedBytes()

                    putExtra(SwimVpnService.EXTRA_DATA_LIMIT, limitBytes)
                    putExtra(SwimVpnService.EXTRA_DATA_USED, usedBytes)
                }
                context.startService(intent)
            }
        }
    }

    private fun currentStateRoutingModeName(): String =
        when (val currentState = _state.value) {
            is AppState.Success -> currentState.routingMode.name
            is AppState.TrialSetup -> currentState.routingMode.name
            else -> RuntimeMode.FULL_TUNNEL.name
        }

    private fun getDeviceId(): String? = DeviceIdentityProvider.getDeviceId(app)

    private suspend fun buildSuccessState(
        profile: AccessProfileResponse,
        isOnboardingDone: Boolean,
        routingMode: RuntimeMode,
        autoConnect: Boolean,
        language: String,
        themeMode: ThemeMode,
    ): AppState.Success? {
        val pinnedIds = prefs.pinnedServerIdsFlow.first()
        val importedGroups = configRepository.getImportedProfileGroups()
        val deviceId = getDeviceId()
        val backendServers = try {
            if (deviceId == null) {
                if (profile.isPremiumAllowed) {
                    Log.e("MainViewModel", "Device identity unavailable while loading premium servers")
                    _state.value = AppState.Error(s(R.string.err_fetch_servers_failed))
                    return null
                }
                emptyList()
            } else {
                api.getServers(profile.userNumber, deviceId)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "API Error fetching servers", e)
            if (profile.isPremiumAllowed) {
                _state.value = AppState.Error(s(R.string.err_fetch_servers_failed))
                return null
            }
            emptyList()
        }

        val plans = try {
            api.getPlans()
        } catch (e: Exception) {
            Log.e("MainViewModel", "API Error fetching plans", e)
            _state.value = AppState.Error(s(R.string.err_fetch_plans_failed))
            return null
        }

        val serverGroups = buildServerGroups(profile, backendServers, importedGroups, pinnedIds)
        val servers = serverGroups.flatMap { it.servers }
        val savedServerId = prefs.selectedServerIdFlow.first()
        val activeServer = servers.find { it.id == savedServerId } ?: servers.firstOrNull()
        val activeConfigMetadata = resolveActiveConfigMetadata(activeServer, profile)

        return AppState.Success(
            profile = profile,
            activeConfigMetadata = activeConfigMetadata,
            servers = servers,
            serverGroups = serverGroups,
            plans = plans,
            isOnboardingDone = isOnboardingDone,
            routingMode = routingMode,
            autoConnect = autoConnect,
            language = language,
            themeMode = themeMode,
            activeServer = activeServer,
        )
    }

    private suspend fun resolveActiveConfigMetadata(
        activeServer: ServerNode?,
        profile: AccessProfileResponse,
    ): ActiveConfigMetadata? {
        if (activeServer == null) {
            return null
        }

        return when (activeServer.source) {
            "imported" -> runCatching { configRepository.getImportedConfigMetadata(activeServer.id) }.getOrNull()
            "backend" -> ActiveConfigMetadata.fromManagedProfile(profile, activeServer)
            else -> activeServer.rawConfig?.let { rawConfig ->
                ActiveConfigMetadata.fromRawConfig(
                    rawConfig = rawConfig,
                    source = com.swimvpn.app.config.ActiveConfigSource.IMPORTED_CONFIG,
                    displayNameFallback = activeServer.city.ifBlank { activeServer.host },
                    isActive = true,
                )
            }
        }
    }

    private suspend fun refreshSuccessState(currentState: AppState.Success): AppState.Success {
        val pinnedIds = prefs.pinnedServerIdsFlow.first()
        val importedGroups = configRepository.getImportedProfileGroups()
        val backendGroup = currentState.serverGroups.firstOrNull { it.source == "backend" }
        val backendServers = backendGroup?.servers?.map { server ->
            server.copy(
                isPinned = server.id in pinnedIds,
                groupId = "backend:${currentState.profile.userNumber}",
                groupName = s(R.string.server_group_access),
                source = "backend",
                rawConfig = null,
            )
        } ?: currentState.servers.filter { it.source != "imported" }.map { it.copy(isPinned = it.id in pinnedIds) }

        val serverGroups = buildServerGroups(currentState.profile, backendServers, importedGroups, pinnedIds)
        val servers = serverGroups.flatMap { it.servers }
        val savedServerId = prefs.selectedServerIdFlow.first()
        val activeServer = servers.find { it.id == savedServerId }
            ?: servers.find { it.id == currentState.activeServer?.id }
            ?: servers.firstOrNull()
        val activeConfigMetadata = resolveActiveConfigMetadata(activeServer, currentState.profile)

        return currentState.copy(
            servers = servers,
            serverGroups = serverGroups,
            activeServer = activeServer,
            activeConfigMetadata = activeConfigMetadata,
        )
    }

    private fun buildServerGroups(
        profile: AccessProfileResponse,
        backendServers: List<ServerNode>,
        importedGroups: List<ImportedProfileGroup>,
        pinnedIds: Set<String>,
    ): List<ServerGroup> {
        val groups = mutableListOf<ServerGroup>()

        if (backendServers.isNotEmpty()) {
            groups += ServerGroup(
                id = "backend:${profile.userNumber}",
                title = s(R.string.server_group_access),
                subtitle = s(R.string.server_group_backend_count, backendServers.size),
                source = "backend",
                servers = backendServers
                    .map { server ->
                        server.copy(
                            groupId = "backend:${profile.userNumber}",
                            groupName = s(R.string.server_group_access),
                            source = "backend",
                            isPinned = server.id in pinnedIds,
                            rawConfig = null,
                        )
                    }
                    .sortedWith(compareByDescending<ServerNode> { it.isPinned }.thenBy { it.country }.thenBy { it.city }),
            )
        }

        importedGroups.forEach { group ->
            val importedServers = group.profiles.map { importedProfile ->
                importedProfile.toImportedServerNode(
                    groupName = group.name,
                    isPinned = ConfigRepository.importedServerIdFor(importedProfile.id) in pinnedIds,
                )
            }.sortedWith(compareByDescending<ServerNode> { it.isPinned }.thenBy { it.city })

            if (importedServers.isNotEmpty()) {
                groups += ServerGroup(
                    id = group.id,
                    title = group.name,
                    subtitle = s(R.string.server_group_imported_count, importedServers.size),
                    source = "imported",
                    servers = importedServers,
                )
            }
        }

        return groups
    }

    private fun SwimVpnProfile.toImportedServerNode(
        groupName: String,
        isPinned: Boolean,
    ): ServerNode {
        return ServerNode(
            id = ConfigRepository.importedServerIdFor(id),
            country = groupName,
            city = displayName,
            host = address,
            port = port,
            protocol = protocol.name.lowercase(),
            tags = listOfNotNull(
                "imported",
                transport.name.lowercase(),
                securityMode.name.lowercase().takeIf { it != SecurityMode.NONE.name.lowercase() },
            ),
            planScope = "imported",
            countryCode = null,
            load = 0,
            ping = 0,
            groupId = sourceBundleId ?: id,
            groupName = groupName,
            rawConfig = rawConfig,
            source = "imported",
            isPinned = isPinned,
        )
    }
}
