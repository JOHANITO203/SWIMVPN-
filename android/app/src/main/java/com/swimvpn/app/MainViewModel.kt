package com.swimvpn.app

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.data.network.ActivateCodeRequest
import com.swimvpn.app.data.network.ImportSubscriptionRequest
import com.swimvpn.app.data.network.RetrofitClient
import com.swimvpn.app.data.network.ServerNode
import com.swimvpn.app.data.network.StartTrialRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class AppState {
    object Loading : AppState()
    data class Success(
        val profile: AccessProfileResponse,
        val servers: List<ServerNode>,
        val plans: List<com.swimvpn.app.data.model.Plan>,
        val isOnboardingDone: Boolean,
        val routingMode: String,
        val autoConnect: Boolean,
        val language: String,
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

    init {
        initApp()
    }

    private fun initApp() {
        viewModelScope.launch {
            try {
                _state.value = AppState.Loading
                
                val isOnboardingDone = prefs.onboardingDoneFlow.first()
                val routingMode = prefs.routingModeFlow.first()
                val autoConnect = prefs.autoConnectFlow.first()
                val language = prefs.languageFlow.first()

                // Try to get profile from server
                val userNumber = prefs.userNumberFlow.first() ?: "NEW_USER"
                
                var profile: AccessProfileResponse
                try {
                    profile = if (userNumber == "NEW_USER") {
                        // For a real app, we might wait for the user to click "Start Trial"
                        // But for this init, let's assume we need to fetch or start trial
                        api.startTrial(StartTrialRequest(getDeviceId()))
                    } else {
                        api.getAccessProfile(userNumber)
                    }
                    // Save user number if it was new
                    if (userNumber == "NEW_USER") {
                        prefs.saveUserNumber(profile.userNumber)
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "API Error, using mock profile", e)
                    profile = AccessProfileResponse(
                        userNumber = "MOCK_USER",
                        email = "mock@swimvpn.com",
                        planType = "FREE",
                        status = "ACTIVE",
                        trialStartedAt = "",
                        trialExpiresAt = "",
                        subscriptionExpiresAt = "",
                        subscriptionUrl = null,
                        devicesAllowed = 1,
                        dataLimitGB = 1,
                        dataUsedBytes = "0"
                    )
                }

                val servers = try {
                    api.getServers(profile.userNumber)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "API Error fetching servers", e)
                    emptyList()
                }

                val plans = try {
                    api.getPlans()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "API Error fetching plans", e)
                    emptyList()
                }

                _state.value = AppState.Success(profile, servers, plans, isOnboardingDone, routingMode, autoConnect, language)

            } catch (e: Exception) {
                Log.e("MainViewModel", "Error initApp", e)
                _state.value = AppState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    fun setRoutingMode(mode: String) {
        viewModelScope.launch {
            prefs.setRoutingMode(mode)
            val currentState = _state.value as? AppState.Success ?: return@launch
            _state.value = currentState.copy(routingMode = mode)
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAutoConnect(enabled)
            val currentState = _state.value as? AppState.Success ?: return@launch
            _state.value = currentState.copy(autoConnect = enabled)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            prefs.setLanguage(lang)
            val currentState = _state.value as? AppState.Success ?: return@launch
            _state.value = currentState.copy(language = lang)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            prefs.setOnboardingDone()
            val currentState = _state.value as? AppState.Success ?: return@launch
            _state.value = currentState.copy(isOnboardingDone = true)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            prefs.saveUserNumber("NEW_USER")
            prefs.setOnboardingDone(false)
            initApp()
        }
    }

    fun importUrl(url: String) {
        viewModelScope.launch {
            try {
                _state.value = AppState.Loading
                val updatedProfile = api.importSubscription(ImportSubscriptionRequest(getDeviceId(), url))
                val currentState = _state.value as? AppState.Success ?: return@launch
                _state.value = currentState.copy(profile = updatedProfile)
            } catch (e: Exception) {
                _state.value = AppState.Error("Import failed: ${e.localizedMessage}")
            }
        }
    }

    fun activateCode(code: String) {
        viewModelScope.launch {
            try {
                _state.value = AppState.Loading
                val updatedProfile = api.activateCode(ActivateCodeRequest(getDeviceId(), code))
                val currentState = _state.value as? AppState.Success ?: return@launch
                _state.value = currentState.copy(profile = updatedProfile)
            } catch (e: Exception) {
                _state.value = AppState.Error("Activation failed: ${e.localizedMessage}")
            }
        }
    }

    fun createOrder(planId: String, amount: Double) {
        viewModelScope.launch {
            try {
                _state.value = AppState.Loading
                val request = com.swimvpn.app.data.model.CreateOrderRequest(
                    email = null,
                    phone = null,
                    planId = planId,
                    amountRub = amount
                )
                val response = api.createOrder(request)
                
                // If the response has a payment URL (simulated here for Stripe/YooKassa)
                // For now, we construct a mock URL or use one from the backend if available
                val paymentUrl = "https://checkout.stripe.com/pay/${response.orderRef}"
                _effect.emit(AppSideEffect.OpenUrl(paymentUrl))
                
                // After emission, we can revert to Success state but maybe show "Pending Payment"
                initApp() 
            } catch (e: Exception) {
                _state.value = AppState.Error("Order creation failed: ${e.localizedMessage}")
            }
        }
    }

    fun selectServer(server: ServerNode) {
        val current = _state.value
        if (current is AppState.Success) {
            _state.value = current.copy(activeServer = server)
        }
    }

    fun retry() {
        initApp()
    }

    fun toggleVpn(context: android.content.Context, server: ServerNode?, profile: AccessProfileResponse?) {
        val currentState = com.swimvpn.app.vpn.VpnManager.state.value

        if (currentState == com.swimvpn.app.vpn.VpnState.CONNECTED || currentState == com.swimvpn.app.vpn.VpnState.CONNECTING) {
            // STOP VPN
            val intent = android.content.Intent(context, SwimVpnService::class.java).apply {
                action = SwimVpnService.ACTION_STOP
            }
            context.startService(intent)
        } else {
            // START VPN
            if (server == null || profile == null) {
                _state.value = AppState.Error("No server or profile available.")
                return
            }

            // Check if plan is expired before starting
            if (profile.status == "EXPIRED") {
                _state.value = AppState.Error("Your subscription has expired. Please upgrade.")
                return
            }
            
            val intent = android.content.Intent(context, SwimVpnService::class.java).apply {
                action = SwimVpnService.ACTION_START
                putExtra(SwimVpnService.EXTRA_SERVER_HOST, server.host)
                putExtra(SwimVpnService.EXTRA_SERVER_PORT, server.port)
                putExtra(SwimVpnService.EXTRA_PROTOCOL, server.protocol)
                putExtra(SwimVpnService.EXTRA_URL, profile.subscriptionUrl)
                
                // Pass data limits
                val limitBytes = if (profile.planType == "TRIAL") -1L else profile.dataLimitGB.toLong() * 1024 * 1024 * 1024
                val usedBytes = profile.dataUsedBytes.toLongOrNull() ?: 0L
                
                putExtra("DATA_LIMIT_BYTES", limitBytes)
                putExtra("DATA_USED_BYTES", usedBytes)
            }
            context.startService(intent)
        }
    }

    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device_id"
    }
}
