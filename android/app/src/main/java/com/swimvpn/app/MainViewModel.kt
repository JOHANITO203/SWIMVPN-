package com.swimvpn.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swimvpn.app.adaptive.AdaptiveDecisionAgent
import com.swimvpn.app.adaptive.AdaptiveEventLogger
import com.swimvpn.app.adaptive.DecisionActionType
import com.swimvpn.app.adaptive.ServerDecisionCandidate
import com.swimvpn.app.adaptive.ServerScoreStore
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.data.local.AutoConnectPayload
import com.swimvpn.app.data.local.DeviceIdentityProvider
import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.data.network.ActivateCodeRequest
import com.swimvpn.app.data.network.ActivateTrialRequest
import com.swimvpn.app.data.network.BootstrapAccessRequest
import com.swimvpn.app.data.network.CancelCurrentSubscriptionRequest
import com.swimvpn.app.data.network.CompleteProfileRequest
import com.swimvpn.app.data.model.CheckoutRefreshPolicy
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
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.SwimVpnProfile
import com.swimvpn.app.vpn.RuntimeMode
import com.swimvpn.app.vpn.RuntimeModePreference
import com.swimvpn.app.vpn.RuntimeStatus
import com.swimvpn.app.vpn.ThemeMode
import com.swimvpn.app.vpn.VpnManager
import com.swimvpn.app.vpn.VpnNotificationLanguage
import com.swimvpn.app.vpn.VpnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val serverScoreStore = ServerScoreStore(application)

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<AppSideEffect>()
    val effect: SharedFlow<AppSideEffect> = _effect.asSharedFlow()

    private var lastAutoConnectSignature: String? = null
    private var adaptiveReconnectAttempt = 0
    private var adaptiveActiveServerId: String? = null
    private var manualStopRequested = false
    private var handlingAdaptiveFailure = false
    private var premiumUsageReportJob: Job? = null
    private var premiumUsageSessionBaselineBytes = 0L
    private var externalCheckoutRefreshUntilMs = 0L
    private var externalCheckoutRefreshInFlight = false

    private companion object {
        private const val PREMIUM_USAGE_REPORT_INTERVAL_MS = 30_000L
    }

    init {
        observeAdaptiveRuntime()
        initApp()
    }

    private fun s(resId: Int, vararg args: Any): String = app.getString(resId, *args)

    private fun observeAdaptiveRuntime() {
        viewModelScope.launch {
            VpnManager.runtimeStatus
                .collect { status ->
                    when (status) {
                        RuntimeStatus.RUNNING -> onAdaptiveRuntimeRunning()
                        RuntimeStatus.DEGRADED -> Unit
                        RuntimeStatus.RECONNECTING -> Unit
                        RuntimeStatus.FAILED -> {
                            stopPremiumUsageReporting()
                            handleAdaptiveRuntimeFailure()
                        }
                        RuntimeStatus.IDLE -> {
                            stopPremiumUsageReporting()
                            if (manualStopRequested) {
                                AdaptiveEventLogger.log("manual_disconnect_completed")
                            }
                            manualStopRequested = false
                        }
                        RuntimeStatus.STARTING,
                        RuntimeStatus.STOPPING,
                        RuntimeStatus.STOPPED_BY_USER -> Unit
                    }
                }
        }
    }

    private fun onAdaptiveRuntimeRunning() {
        val serverId = adaptiveActiveServerId ?: (_state.value as? AppState.Success)?.activeServer?.id
        if (serverId != null) {
            serverScoreStore.recordSuccess(serverId)
            AdaptiveEventLogger.log(
                event = if (adaptiveReconnectAttempt > 0) "reconnect_success" else "handshake_success",
                details = mapOf(
                    "serverId" to serverId,
                    "attempt" to adaptiveReconnectAttempt,
                ),
            )
        }
        adaptiveReconnectAttempt = 0
        handlingAdaptiveFailure = false
    }

    private suspend fun handleAdaptiveRuntimeFailure() {
        if (handlingAdaptiveFailure) return
        handlingAdaptiveFailure = true

        try {
            val current = _state.value as? AppState.Success ?: return
            if (!current.autoConnect) return
            if (manualStopRequested) return

            val activeServer = current.activeServer ?: return
            val runtimeConfig = activeServer.rawConfig ?: current.profile.subscriptionUrl
            if ((!current.profile.isPremiumAllowed && activeServer.source == "backend") || runtimeConfig.isNullOrBlank()) {
                return
            }

            val now = System.currentTimeMillis()
            serverScoreStore.recordFailure(activeServer.id, now)
            val scores = serverScoreStore.loadScores()
            val action = AdaptiveDecisionAgent.planAfterFailure(
                currentServerId = activeServer.id,
                candidates = current.servers.map { it.toDecisionCandidate(current.profile) },
                scores = scores,
                reconnectAttempt = adaptiveReconnectAttempt,
                nowMs = now,
            )

            AdaptiveEventLogger.log(
                event = "decision_agent_action_taken",
                details = mapOf(
                    "action" to action.type,
                    "targetServerId" to action.targetServerId,
                    "attempt" to adaptiveReconnectAttempt,
                    "delayMs" to action.delayMs,
                    "reason" to action.reason,
                ),
            )

            adaptiveReconnectAttempt += 1

            when (action.type) {
                DecisionActionType.GIVE_UP -> {
                    _effect.emit(AppSideEffect.ShowToast(s(R.string.adaptive_reconnect_give_up)))
                }
                DecisionActionType.RECONNECT_SAME,
                DecisionActionType.SWITCH_SERVER -> {
                    val targetServer = current.servers.firstOrNull { it.id == action.targetServerId } ?: return
                    if (action.type == DecisionActionType.SWITCH_SERVER) {
                        prefs.setSelectedServerId(targetServer.id)
                        _state.value = current.copy(
                            activeServer = targetServer,
                            activeConfigMetadata = resolveActiveConfigMetadata(targetServer, current.profile),
                        )
                        _effect.emit(AppSideEffect.ShowToast(s(R.string.adaptive_switching_route)))
                    } else {
                        _effect.emit(AppSideEffect.ShowToast(s(R.string.adaptive_reconnecting)))
                    }

                    delay(action.delayMs)

                    val vpnState = VpnManager.state.value
                    if (vpnState == VpnState.DISCONNECTED || vpnState == VpnState.ERROR) {
                        lastAutoConnectSignature = null
                        maybeAutoConnect(app, _state.value as? AppState.Success ?: current)
                    }
                }
            }
        } finally {
            handlingAdaptiveFailure = false
        }
    }

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
            val runtimeMode = RuntimeModePreference.fromUserSelectable(mode)
            val previousMode = when (val currentState = _state.value) {
                is AppState.Success -> currentState.routingMode
                is AppState.TrialSetup -> currentState.routingMode
                else -> null
            }
            if (previousMode == runtimeMode) {
                prefs.setRuntimeMode(runtimeMode)
                return@launch
            }

            prefs.setRuntimeMode(runtimeMode)
            when (val currentState = _state.value) {
                is AppState.Success -> {
                    val nextState = currentState.copy(routingMode = runtimeMode)
                    _state.value = nextState
                    restartRuntimeForModeChangeIfNeeded(nextState)
                }
                is AppState.TrialSetup -> _state.value = currentState.copy(routingMode = runtimeMode)
                else -> {}
            }
        }
    }

    private suspend fun restartRuntimeForModeChangeIfNeeded(nextState: AppState.Success) {
        val vpnState = VpnManager.state.value
        if (vpnState != VpnState.CONNECTED && vpnState != VpnState.CONNECTING) {
            return
        }

        val server = nextState.activeServer ?: return
        val profile = nextState.profile
        val runtimeConfig = server.rawConfig ?: profile.subscriptionUrl
        if ((!profile.isPremiumAllowed && server.source == "backend") || runtimeConfig.isNullOrBlank()) {
            return
        }

        stopPremiumUsageReporting()
        manualStopRequested = false
        adaptiveReconnectAttempt = 0
        lastAutoConnectSignature = null
        AdaptiveEventLogger.log(
            event = "routing_mode_restart_requested",
            details = mapOf("mode" to nextState.routingMode.name),
        )
        prefs.saveAutoConnectPayload(
            AutoConnectPayload(
                host = server.host,
                port = server.port,
                protocol = server.protocol,
                runtimeConfig = runtimeConfig,
                runtimeMode = nextState.routingMode,
            )
        )

        val context = app.applicationContext
        val restartIntent = Intent(context, SwimVpnService::class.java).apply {
            action = SwimVpnService.ACTION_RESTART
            putExtra(SwimVpnService.EXTRA_SERVER_HOST, server.host)
            putExtra(SwimVpnService.EXTRA_SERVER_PORT, server.port)
            putExtra(SwimVpnService.EXTRA_PROTOCOL, server.protocol)
            putExtra(SwimVpnService.EXTRA_URL, runtimeConfig)
            putExtra(SwimVpnService.EXTRA_RUNTIME_MODE, nextState.routingMode.name)
        }
        context.startService(restartIntent)
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val validationError = autoConnectValidationError()
                if (validationError != null) {
                    prefs.setAutoConnect(false)
                    updateAutoConnectState(false)
                    _effect.emit(AppSideEffect.ShowToast(validationError))
                    Log.w("MainViewModel", "Auto-connect not enabled: $validationError")
                    return@launch
                }
            }

            prefs.setAutoConnect(enabled)
            updateAutoConnectState(enabled)
        }
    }

    private fun updateAutoConnectState(enabled: Boolean) {
        when (val currentState = _state.value) {
            is AppState.Success -> _state.value = currentState.copy(autoConnect = enabled)
            is AppState.TrialSetup -> _state.value = currentState.copy(autoConnect = enabled)
            else -> {}
        }
    }

    private fun autoConnectValidationError(): String? {
        val currentState = _state.value as? AppState.Success
            ?: return s(R.string.err_no_server_profile)
        val server = currentState.activeServer
            ?: return s(R.string.err_no_server_profile)
        val profile = currentState.profile

        if (!profile.isPremiumAllowed && server.source == "backend") {
            return s(R.string.err_subscription_expired)
        }

        val runtimeConfig = server.rawConfig ?: profile.subscriptionUrl
        if (runtimeConfig.isNullOrBlank()) {
            return s(R.string.err_no_runtime_config)
        }

        return null
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            val normalizedLanguage = VpnNotificationLanguage.normalize(lang)
            prefs.setLanguage(normalizedLanguage)
            when (val currentState = _state.value) {
                is AppState.Success -> _state.value = currentState.copy(language = normalizedLanguage)
                is AppState.TrialSetup -> _state.value = currentState.copy(language = normalizedLanguage)
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

    fun cancelCurrentSubscription(context: Context) {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.Success ?: return@launch
            val deviceId = getDeviceId()
            if (deviceId == null) {
                Log.e("MainViewModel", "Device identity unavailable during subscription cancellation")
                _effect.emit(AppSideEffect.ShowToast(s(R.string.err_subscription_cancel_failed)))
                return@launch
            }

            try {
                val profile = api.cancelCurrentSubscription(
                    CancelCurrentSubscriptionRequest(
                        userNumber = currentState.profile.userNumber,
                        deviceId = deviceId,
                        reason = "CUSTOMER_CANCELLED_FROM_APP",
                    )
                )

                val shouldClearBackendAccess = currentState.activeServer?.source != "imported"
                val nextAutoConnect = if (shouldClearBackendAccess) false else currentState.autoConnect
                val vpnStateBeforeCancellation = VpnManager.state.value

                if (shouldClearBackendAccess) {
                    manualStopRequested = true
                    adaptiveReconnectAttempt = 0
                    adaptiveActiveServerId = null
                    lastAutoConnectSignature = null
                    prefs.setAutoConnect(false)
                    prefs.clearAutoConnectPayload()
                }

                if (currentState.activeServer?.source == "backend") {
                    prefs.setSelectedServerId(null)
                    configRepository.clearActiveProfile()
                }

                val shouldStopBackendVpn =
                    currentState.activeServer?.source == "backend" &&
                        (vpnStateBeforeCancellation == VpnState.CONNECTED ||
                            vpnStateBeforeCancellation == VpnState.CONNECTING)
                if (shouldStopBackendVpn) {
                    val intent = Intent(context, SwimVpnService::class.java).apply {
                        action = SwimVpnService.ACTION_STOP
                    }
                    context.startService(intent)
                }
                if (shouldClearBackendAccess) {
                    VpnManager.clearError()
                    VpnManager.updateState(VpnState.DISCONNECTED)
                }

                val successState = buildSuccessState(
                    profile = profile,
                    isOnboardingDone = currentState.isOnboardingDone,
                    routingMode = currentState.routingMode,
                    autoConnect = nextAutoConnect,
                    language = currentState.language,
                    themeMode = currentState.themeMode,
                )

                if (successState != null) {
                    _state.value = successState
                    refreshServerLatency()
                }

                _effect.emit(AppSideEffect.ShowToast(s(R.string.subscription_cancelled_success)))
            } catch (e: Exception) {
                Log.e("MainViewModel", "Subscription cancellation failed", e)
                _effect.emit(AppSideEffect.ShowToast(s(R.string.err_subscription_cancel_failed)))
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
                    userNumber = currentState.profile.userNumber,
                    deviceId = getDeviceId(),
                    email = currentState.profile.email,
                    phone = currentState.profile.phone,
                    planId = planId,
                    paymentMethod = paymentMethod,
                    cryptoAsset = cryptoAsset,
                )
                val response = api.createCheckout(request)
                response.redirectUrl?.let { redirectUrl ->
                    externalCheckoutRefreshUntilMs =
                        CheckoutRefreshPolicy.refreshUntil(System.currentTimeMillis())
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

    fun refreshAfterExternalCheckoutIfNeeded() {
        val now = System.currentTimeMillis()
        if (!CheckoutRefreshPolicy.shouldRefreshAfterReturn(now, externalCheckoutRefreshUntilMs)) {
            return
        }
        if (externalCheckoutRefreshInFlight) {
            return
        }

        viewModelScope.launch {
            val currentState = _state.value as? AppState.Success ?: return@launch
            externalCheckoutRefreshInFlight = true
            try {
                val refreshedProfile = refreshPremiumEntitlement(currentState.profile.userNumber)
                    ?: return@launch
                val profileForState = refreshedProfile.preserveRuntimeAccessFrom(currentState.profile)
                _state.value = refreshSuccessState(currentState.copy(profile = profileForState))
                refreshServerLatency()

                if (refreshedProfile.isPremiumAllowed || refreshedProfile.isPendingFulfillment) {
                    externalCheckoutRefreshUntilMs = 0L
                }
            } finally {
                externalCheckoutRefreshInFlight = false
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
            resumePremiumSupervisionIfRunning(context, successState)
            return
        }

        val signature = listOf(profile.userNumber, server.id, runtimeConfig).joinToString(":")
        if (lastAutoConnectSignature == signature) return

        lastAutoConnectSignature = signature
        AdaptiveEventLogger.log(
            event = "reconnect_started",
            details = mapOf(
                "serverId" to server.id,
                "attempt" to adaptiveReconnectAttempt,
                "mode" to successState.routingMode,
            ),
        )
        toggleVpn(context, server, profile)
    }

    fun maybeRestoreAutoConnectFromBoot(context: Context) {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.Success ?: return@launch

            val vpnState = com.swimvpn.app.vpn.VpnManager.state.value
            if (vpnState != com.swimvpn.app.vpn.VpnState.DISCONNECTED && vpnState != com.swimvpn.app.vpn.VpnState.ERROR) {
                resumePremiumSupervisionIfRunning(context, currentState)
                return@launch
            }

            if (!currentState.autoConnect) return@launch

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

    private fun resumePremiumSupervisionIfRunning(context: Context, successState: AppState.Success) {
        val vpnState = VpnManager.state.value
        if (vpnState != VpnState.CONNECTED && vpnState != VpnState.CONNECTING) {
            return
        }

        val activeServer = successState.activeServer ?: return
        if (!successState.profile.shouldSuperviseBackendPremiumConnection(activeServer.source)) {
            return
        }

        adaptiveActiveServerId = activeServer.id
        startPremiumUsageReporting(context.applicationContext, successState.profile, activeServer)
    }


    private fun startPremiumUsageReporting(
        context: Context,
        profile: AccessProfileResponse,
        server: ServerNode,
    ) {
        stopPremiumUsageReporting()

        if (!profile.shouldSuperviseBackendPremiumConnection(server.source)) {
            return
        }

        val userNumber = profile.userNumber
        premiumUsageSessionBaselineBytes = profile.parsedDataUsedBytes
        premiumUsageReportJob = viewModelScope.launch {
            while (true) {
                delay(PREMIUM_USAGE_REPORT_INTERVAL_MS)

                val vpnState = VpnManager.state.value
                if (vpnState != VpnState.CONNECTED && vpnState != VpnState.CONNECTING) {
                    break
                }

                val currentState = _state.value as? AppState.Success ?: break
                val activeServer = currentState.activeServer ?: break
                if (activeServer.source != "backend") {
                    break
                }

                val refreshedProfile = reportMeasuredUsage(
                    userNumber = userNumber,
                    baselineUsedBytes = premiumUsageSessionBaselineBytes,
                ) ?: refreshPremiumEntitlement(userNumber) ?: continue
                val profileForState = refreshedProfile.preserveRuntimeAccessFrom(currentState.profile)

                if (!profileForState.isPremiumAllowed) {
                    handlePremiumAccessEnded(context, currentState, profileForState)
                    break
                }

                _state.value = currentState.copy(
                    profile = profileForState,
                    activeConfigMetadata = resolveActiveConfigMetadata(activeServer, profileForState),
                )
            }
        }
    }

    private fun stopPremiumUsageReporting() {
        premiumUsageReportJob?.cancel()
        premiumUsageReportJob = null
        premiumUsageSessionBaselineBytes = 0L
    }

    private suspend fun handlePremiumAccessEnded(
        context: Context,
        currentState: AppState.Success,
        refreshedProfile: AccessProfileResponse,
    ) {
        stopPremiumUsageReporting()
        manualStopRequested = true
        adaptiveReconnectAttempt = 0
        lastAutoConnectSignature = null
        prefs.setAutoConnect(false)
        prefs.clearAutoConnectPayload()
        clearBackendSelectionIfNeeded(currentState)

        val standardState = refreshSuccessState(
            currentState.copy(
                profile = refreshedProfile,
                autoConnect = false,
            ),
        )
        _state.value = standardState
        _effect.emit(AppSideEffect.ShowToast(s(R.string.premium_access_finished_standard_mode)))

        val intent = Intent(context, SwimVpnService::class.java).apply {
            action = SwimVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    private suspend fun applyProfileRefresh(
        currentState: AppState.Success,
        refreshedProfile: AccessProfileResponse,
    ): AppState.Success {
        val profileForState = refreshedProfile.preserveRuntimeAccessFrom(currentState.profile)

        if (!profileForState.isPremiumAllowed) {
            clearBackendSelectionIfNeeded(currentState)
            return refreshSuccessState(currentState.copy(profile = profileForState))
        }

        return currentState.copy(
            profile = profileForState,
            activeConfigMetadata = resolveActiveConfigMetadata(currentState.activeServer, profileForState),
        )
    }

    private suspend fun clearBackendSelectionIfNeeded(currentState: AppState.Success) {
        val savedServerId = prefs.selectedServerIdFlow.first()
        val selectedBackend = currentState.activeServer?.source == "backend" ||
            savedServerId?.startsWith("backend") == true
        if (!selectedBackend) {
            return
        }

        prefs.setSelectedServerId(null)
        configRepository.clearActiveProfile()
        adaptiveActiveServerId = null
        lastAutoConnectSignature = null
    }

    private suspend fun reportMeasuredUsage(
        userNumber: String,
        baselineUsedBytes: Long,
    ): AccessProfileResponse? {
        val sessionBytes = (VpnManager.bytesIn.value + VpnManager.bytesOut.value)
            .coerceAtLeast(0L)
        if (sessionBytes <= 0L) {
            return null
        }

        val measuredBytes = (baselineUsedBytes + sessionBytes).coerceAtLeast(baselineUsedBytes)
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

    private suspend fun refreshPremiumEntitlement(userNumber: String): AccessProfileResponse? {
        return try {
            api.getAccessProfile(userNumber)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Premium entitlement refresh failed", e)
            null
        }
    }

    fun toggleVpn(context: android.content.Context, server: ServerNode?, profile: AccessProfileResponse?) {
        val currentState = com.swimvpn.app.vpn.VpnManager.state.value

        if (currentState == com.swimvpn.app.vpn.VpnState.CONNECTED || currentState == com.swimvpn.app.vpn.VpnState.CONNECTING) {
            val successState = _state.value as? AppState.Success
            val usageBaseline = premiumUsageSessionBaselineBytes
                .takeIf { it > 0L }
                ?: successState?.profile?.parsedDataUsedBytes
                ?: 0L
            stopPremiumUsageReporting()
            manualStopRequested = true
            adaptiveReconnectAttempt = 0
            AdaptiveEventLogger.log("manual_disconnect_requested")
            viewModelScope.launch {
                val refreshedProfile = successState?.profile?.userNumber?.let {
                    reportMeasuredUsage(
                        userNumber = it,
                        baselineUsedBytes = usageBaseline,
                    )
                }
                if (successState != null && refreshedProfile != null) {
                    _state.value = applyProfileRefresh(successState, refreshedProfile)
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
                manualStopRequested = false
                adaptiveActiveServerId = server.id
                val resolvedRuntimeConfig = configRepository.resolveRuntimeConfigForConnection(
                    input = runtimeConfig,
                    sourceType = if (server.source == "backend") SourceType.BACKEND_API else SourceType.MANUAL_ENTRY,
                ).getOrElse { error ->
                    val message = "Connection failed: ${error.localizedMessage ?: "Unsupported configuration format"}"
                    Log.e("MainViewModel", "Runtime config resolution failed for ${server.id}", error)
                    _effect.emit(AppSideEffect.ShowToast(message))
                    VpnManager.setError(message)
                    return@launch
                }.rawConfig

                AdaptiveEventLogger.log(
                    event = "connection_started",
                    details = mapOf(
                        "serverId" to server.id,
                        "source" to server.source,
                        "mode" to currentStateRoutingModeName(),
                    ),
                )
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
                    putExtra(SwimVpnService.EXTRA_URL, resolvedRuntimeConfig)
                    putExtra(SwimVpnService.EXTRA_RUNTIME_MODE, currentStateRoutingModeName())

                    val limitBytes = if (profile.hasMeasuredLimit) profile.dataLimitBytes else -1L
                    val usedBytes = profile.totalConsumedBytes()

                    putExtra(SwimVpnService.EXTRA_DATA_LIMIT, limitBytes)
                    putExtra(SwimVpnService.EXTRA_DATA_USED, usedBytes)
                }
                ContextCompat.startForegroundService(context, intent)
                startPremiumUsageReporting(context.applicationContext, profile, server)
            }
        }
    }

    private fun currentStateRoutingModeName(): String =
        when (val currentState = _state.value) {
            is AppState.Success -> currentState.routingMode.name
            is AppState.TrialSetup -> currentState.routingMode.name
            else -> RuntimeMode.FULL_TUNNEL.name
        }

    private fun ServerNode.toDecisionCandidate(profile: AccessProfileResponse): ServerDecisionCandidate {
        val runtimeConfig = rawConfig ?: profile.subscriptionUrl
        return ServerDecisionCandidate(
            serverId = id,
            pingMs = ping,
            isPinned = isPinned,
            hasRuntimeConfig = !runtimeConfig.isNullOrBlank(),
            premiumBlocked = source == "backend" && !profile.isPremiumAllowed,
        )
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
            Log.e("MainViewModel", "API Error fetching plans; continuing app shell without store plans", e)
            emptyList()
        }

        val resolvedBackendServers = resolveBackendServers(profile, backendServers, pinnedIds)
        val serverGroups = buildServerGroups(profile, resolvedBackendServers, importedGroups, pinnedIds)
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
        val backendServers = if (!currentState.profile.isPremiumAllowed) {
            emptyList()
        } else {
            backendGroup?.servers?.map { server ->
                server.copy(
                    isPinned = server.id in pinnedIds,
                    groupId = "backend:${currentState.profile.userNumber}",
                    groupName = s(R.string.server_group_access),
                    source = "backend",
                )
            } ?: currentState.servers.filter { it.source != "imported" }.map { it.copy(isPinned = it.id in pinnedIds) }
        }

        val resolvedBackendServers = resolveBackendServers(currentState.profile, backendServers, pinnedIds)
        val serverGroups = buildServerGroups(currentState.profile, resolvedBackendServers, importedGroups, pinnedIds)
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

        if (profile.isPremiumAllowed && backendServers.isNotEmpty()) {
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

    private suspend fun resolveBackendServers(
        profile: AccessProfileResponse,
        backendServers: List<ServerNode>,
        pinnedIds: Set<String>,
    ): List<ServerNode> {
        val subscriptionUrl = profile.subscriptionUrl?.trim()
        val isRemoteSubscription = subscriptionUrl?.startsWith("http://", ignoreCase = true) == true ||
            subscriptionUrl?.startsWith("https://", ignoreCase = true) == true

        if (!profile.isPremiumAllowed || !isRemoteSubscription || subscriptionUrl.isNullOrBlank()) {
            return backendServers.map { server ->
                server.copy(
                    isPinned = server.id in pinnedIds,
                    groupId = "backend:${profile.userNumber}",
                    groupName = s(R.string.server_group_access),
                    source = "backend",
                )
            }
        }

        // Keep the live Android subscription resolver only behind backend entitlement truth
        // until a backend-side resolver replaces this path.
        val resolvedProfiles = configRepository.resolveRuntimeProfilesForConnection(
            input = subscriptionUrl,
            sourceType = SourceType.BACKEND_API,
        ).getOrElse { error ->
            Log.w("MainViewModel", "Could not expand assigned premium subscription URL", error)
            return backendServers.map { server ->
                server.copy(
                    isPinned = server.id in pinnedIds,
                    groupId = "backend:${profile.userNumber}",
                    groupName = s(R.string.server_group_access),
                    source = "backend",
                )
            }
        }

        if (resolvedProfiles.isEmpty()) {
            return backendServers
        }

        return resolvedProfiles.map { premiumProfile ->
            premiumProfile.toPremiumServerNode(
                profile = profile,
                isPinned = premiumServerIdFor(premiumProfile) in pinnedIds,
            )
        }
    }

    private fun premiumServerIdFor(profile: SwimVpnProfile): String {
        val stableHash = profile.rawConfig.hashCode().toLong() and 0xffffffffL
        return "backend:${stableHash.toString(16)}"
    }

    private fun SwimVpnProfile.toPremiumServerNode(
        profile: AccessProfileResponse,
        isPinned: Boolean,
    ): ServerNode {
        val groupName = sourceBundleName ?: s(R.string.server_group_access)
        return ServerNode(
            id = premiumServerIdFor(this),
            country = groupName,
            city = displayName,
            host = address,
            port = port,
            protocol = protocol.name.lowercase(),
            tags = listOfNotNull(
                "premium",
                transport.name.lowercase(),
                securityMode.name.lowercase().takeIf { it != SecurityMode.NONE.name.lowercase() },
            ),
            planScope = profile.offerCode ?: "PREMIUM",
            countryCode = null,
            load = 0,
            ping = 0,
            groupId = "backend:${profile.userNumber}:${sourceBundleId ?: id}",
            groupName = groupName,
            rawConfig = rawConfig,
            source = "backend",
            isPinned = isPinned,
            providerName = subscriptionProviderName,
            trafficUsedBytes = subscriptionTrafficUsedBytes,
            trafficTotalBytes = subscriptionTrafficTotalBytes,
            expiresAt = subscriptionExpiresAt,
        )
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
