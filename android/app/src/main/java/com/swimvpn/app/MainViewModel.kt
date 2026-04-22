package com.swimvpn.app

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.data.network.ActivateCodeRequest
import com.swimvpn.app.data.network.ActivateTrialRequest
import com.swimvpn.app.data.network.BootstrapAccessRequest
import com.swimvpn.app.data.network.ImportSubscriptionRequest
import com.swimvpn.app.data.network.RetrofitClient
import com.swimvpn.app.data.network.ServerNode
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
        val servers: List<ServerNode>,
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

    private val prefs = PreferencesManager(application)
    private val api = RetrofitClient.apiService

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<AppSideEffect>()
    val effect: SharedFlow<AppSideEffect> = _effect.asSharedFlow()

    private var lastAutoConnectSignature: String? = null

    init {
        initApp()
    }

    private fun initApp() {
        viewModelScope.launch {
            try {
                _state.value = AppState.Loading

                val isOnboardingDone = prefs.onboardingDoneFlow.first()
                val routingMode = prefs.runtimeModeFlow.first()
                val autoConnect = prefs.autoConnectFlow.first()
                val language = prefs.languageFlow.first()
                val themeMode = prefs.themeModeFlow.first()

                val bootstrap = try {
                    api.bootstrapAccess(
                        BootstrapAccessRequest(
                            deviceId = getDeviceId(),
                            locale = language,
                        )
                    )
                } catch (e: Exception) {
                    Log.e("MainViewModel", "API Error bootstrapping access", e)
                    _state.value = AppState.Error("Impossible de charger votre profil. Verifiez votre connexion et reessayez.")
                    return@launch
                }

                prefs.saveUserNumber(bootstrap.userNumber)

                if (bootstrap.hasActiveAccess && bootstrap.profile != null) {
                    val successState = buildSuccessState(
                        profile = bootstrap.profile,
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
                _state.value = AppState.Error("Error: ${e.localizedMessage}")
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
            initApp()
        }
    }

    fun activateTrial(email: String, phone: String) {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.TrialSetup ?: return@launch

            try {
                _state.value = AppState.Loading
                val profile = api.activateTrial(
                    ActivateTrialRequest(
                        userNumber = currentState.userNumber,
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
            } catch (e: Exception) {
                Log.e("MainViewModel", "Trial activation failed", e)
                _state.value = currentState
                _effect.emit(AppSideEffect.ShowToast("Impossible d'activer l'essai pour le moment"))
            }
        }
    }

    fun activateCode(code: String) {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.Success ?: return@launch
            try {
                _state.value = AppState.Loading
                val updatedProfile = api.activateCode(ActivateCodeRequest(currentState.profile.userNumber, code))
                _state.value = currentState.copy(profile = updatedProfile)
            } catch (e: Exception) {
                _state.value = AppState.Error("Activation failed: ${e.localizedMessage}")
            }
        }
    }

    fun importVless(url: String) {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.Success ?: return@launch
            try {
                val updatedProfile = api.importSubscription(
                    ImportSubscriptionRequest(
                        userNumber = currentState.profile.userNumber,
                        subscriptionUrl = url
                    )
                )

                _state.value = currentState.copy(profile = updatedProfile)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Import failed", e)
                _state.value = currentState
                _effect.emit(AppSideEffect.ShowToast("Configuration imported locally, but profile sync failed"))
            }
        }
    }

    fun createOrder(planId: String, amount: Double) {
        viewModelScope.launch {
            val currentState = _state.value as? AppState.Success ?: return@launch
            try {
                _state.value = AppState.Loading
                val request = com.swimvpn.app.data.model.CreateOrderRequest(
                    email = currentState.profile.email,
                    phone = currentState.profile.phone,
                    planId = planId,
                    amountRub = amount
                )
                val response = api.createOrder(request)
                _effect.emit(
                    AppSideEffect.ShowToast(
                        getApplication<Application>().getString(
                            R.string.order_created_pending_payment,
                            response.orderRef
                        )
                    )
                )
                initApp()
            } catch (e: Exception) {
                _state.value = AppState.Error("Order creation failed: ${e.localizedMessage}")
            }
        }
    }

    fun selectServer(server: ServerNode) {
        viewModelScope.launch {
            prefs.setSelectedServerId(server.id)
            val current = _state.value
            if (current is AppState.Success) {
                _state.value = current.copy(activeServer = server)
            }
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
        if (profile.isExpired || profile.subscriptionUrl.isNullOrBlank()) return

        val vpnState = com.swimvpn.app.vpn.VpnManager.state.value
        if (vpnState != com.swimvpn.app.vpn.VpnState.DISCONNECTED && vpnState != com.swimvpn.app.vpn.VpnState.ERROR) {
            return
        }

        val signature = listOf(profile.userNumber, server.id, profile.subscriptionUrl).joinToString(":")
        if (lastAutoConnectSignature == signature) return

        lastAutoConnectSignature = signature
        toggleVpn(context, server, profile)
    }

    fun toggleVpn(context: android.content.Context, server: ServerNode?, profile: AccessProfileResponse?) {
        val currentState = com.swimvpn.app.vpn.VpnManager.state.value

        if (currentState == com.swimvpn.app.vpn.VpnState.CONNECTED || currentState == com.swimvpn.app.vpn.VpnState.CONNECTING) {
            val intent = android.content.Intent(context, SwimVpnService::class.java).apply {
                action = SwimVpnService.ACTION_STOP
            }
            context.startService(intent)
        } else {
            if (server == null || profile == null) {
                _state.value = AppState.Error("No server or profile available.")
                return
            }

            if (profile.isExpired) {
                _state.value = AppState.Error("Your subscription has expired. Please upgrade.")
                return
            }

            val intent = android.content.Intent(context, SwimVpnService::class.java).apply {
                action = SwimVpnService.ACTION_START
                putExtra(SwimVpnService.EXTRA_SERVER_HOST, server.host)
                putExtra(SwimVpnService.EXTRA_SERVER_PORT, server.port)
                putExtra(SwimVpnService.EXTRA_PROTOCOL, server.protocol)
                putExtra(SwimVpnService.EXTRA_URL, profile.subscriptionUrl)
                putExtra(SwimVpnService.EXTRA_RUNTIME_MODE, currentStateRoutingModeName())

                val limitBytes = if (profile.hasMeasuredLimit) profile.dataLimitBytes else -1L
                val usedBytes = profile.totalConsumedBytes()

                putExtra(SwimVpnService.EXTRA_DATA_LIMIT, limitBytes)
                putExtra(SwimVpnService.EXTRA_DATA_USED, usedBytes)
            }
            context.startService(intent)
        }
    }

    private fun currentStateRoutingModeName(): String =
        when (val currentState = _state.value) {
            is AppState.Success -> currentState.routingMode.name
            is AppState.TrialSetup -> currentState.routingMode.name
            else -> RuntimeMode.FULL_TUNNEL.name
        }

    @android.annotation.SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device_id"
    }

    private suspend fun buildSuccessState(
        profile: AccessProfileResponse,
        isOnboardingDone: Boolean,
        routingMode: RuntimeMode,
        autoConnect: Boolean,
        language: String,
        themeMode: ThemeMode,
    ): AppState.Success? {
        val servers = try {
            api.getServers(profile.userNumber)
        } catch (e: Exception) {
            Log.e("MainViewModel", "API Error fetching servers", e)
            _state.value = AppState.Error("Impossible de charger la liste des serveurs. Verifiez votre connexion et reessayez.")
            return null
        }

        val plans = try {
            api.getPlans()
        } catch (e: Exception) {
            Log.e("MainViewModel", "API Error fetching plans", e)
            _state.value = AppState.Error("Impossible de charger les offres d'abonnement. Verifiez votre connexion et reessayez.")
            return null
        }

        val savedServerId = prefs.selectedServerIdFlow.first()
        val activeServer = servers.find { it.id == savedServerId } ?: servers.firstOrNull()

        return AppState.Success(
            profile = profile,
            servers = servers,
            plans = plans,
            isOnboardingDone = isOnboardingDone,
            routingMode = routingMode,
            autoConnect = autoConnect,
            language = language,
            themeMode = themeMode,
            activeServer = activeServer,
        )
    }
}
