package com.swimvpn.app.config

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.swimvpn.app.config.subscriptionparser.SubscriptionHeaderMetadata
import com.swimvpn.app.config.subscriptionparser.SubscriptionMetadataParser
import com.swimvpn.app.config.subscriptionparser.SubscriptionParser
import com.swimvpn.app.data.local.DeviceIdentityProvider
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.data.network.ResolveCryptSubscriptionRequest
import com.swimvpn.app.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.UUID

/**
 * Repository for managing imported VPN configurations
 * 
 * Responsibilities:
 * - Store and retrieve normalized SwimVpnProfile instances
 * - Manage import history and recently used configs
 * - Handle deduplication of imported configurations
 * - Provide access to active/assigned profiles
 */
class ConfigRepository(private val context: Context) {
    
    private val TAG = "ConfigRepository"
    private val gson = Gson()
    private val api = RetrofitClient.apiService
    private val prefs = PreferencesManager(context)
    private val subscriptionClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    companion object {
        private const val IMPORTED_SERVER_ID_PREFIX = "imported:"
        private const val DATA_STORE_NAME = "vpn_configs"
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)
        
        // Preference keys
        private val IMPORTED_PROFILES_KEY = stringPreferencesKey("imported_profiles")
        private val ACTIVE_PROFILE_ID_KEY = stringPreferencesKey("active_profile_id")
        private val LAST_USED_PROFILE_ID_KEY = stringPreferencesKey("last_used_profile_id")

        internal fun activeConfigSourceFor(sourceType: SourceType): ActiveConfigSource {
            return when (sourceType) {
                SourceType.CLIPBOARD,
                SourceType.FILE_IMPORT,
                SourceType.QR_CODE,
                SourceType.MANUAL_ENTRY,
                SourceType.SUBSCRIPTION_URL -> ActiveConfigSource.IMPORTED_CONFIG
                SourceType.BACKEND_API -> ActiveConfigSource.SWIMVPN_MANAGED
            }
        }

        internal fun importedServerIdFor(profileId: String): String = "$IMPORTED_SERVER_ID_PREFIX$profileId"

        internal fun importedProfileIdFromServerId(serverId: String): String? {
            val profileId = serverId.removePrefix(IMPORTED_SERVER_ID_PREFIX)
            return profileId.takeIf { it.isNotBlank() && it != serverId }
        }
    }
    
    /**
     * Import a configuration string and store it as a normalized profile
     */
    suspend fun importConfig(input: String, sourceType: SourceType = SourceType.MANUAL_ENTRY): ImportResult {
        return try {
            val resolution = resolveImportInput(input)
            when (resolution) {
                is ResolvedImportInput.SubscriptionUrl -> {
                    val fetched = fetchSubscriptionPayload(resolution.url)
                    if (fetched.payload.isBlank()) {
                        return ImportResult.Error(
                            errors = listOf("Subscription URL returned no importable content"),
                            warnings = resolution.warnings + fetched.warnings,
                        )
                    }

                    processResolvedImport(
                        resolution = ResolvedImportInput.DirectConfig(
                            payload = fetched.payload,
                            warnings = resolution.warnings + fetched.warnings,
                            sourceUrl = resolution.url,
                            headerMetadata = fetched.headerMetadata,
                        ),
                        sourceType = SourceType.SUBSCRIPTION_URL,
                    )
                }
                is ResolvedImportInput.HappEncryptedSubscription -> {
                    return ImportResult.Error(
                        errors = listOf(buildHappEncryptedSubscriptionMessage(resolution.version)),
                        warnings = resolution.warnings,
                    )
                }
                is ResolvedImportInput.HappRoutingDeepLink -> {
                    return ImportResult.Error(
                        errors = listOf("Recognized Happ routing deep link, but Happ routing profile import is not implemented yet"),
                        warnings = resolution.warnings,
                    )
                }
                is ResolvedImportInput.SwimEncryptedPayload -> {
                    val userNumber = prefs.userNumberFlow.first()
                        ?: return ImportResult.Error(
                            errors = listOf("Recognized SWIMVPN crypt1 payload, but no local user profile is available for backend resolution"),
                            warnings = resolution.warnings,
                        )
                    val deviceId = getDeviceId()
                        ?: return ImportResult.Error(
                            errors = listOf("Recognized SWIMVPN crypt1 payload, but device identity is unavailable for backend resolution"),
                            warnings = resolution.warnings,
                        )
                    val resolved = api.resolveCryptSubscription(
                        ResolveCryptSubscriptionRequest(
                            userNumber = userNumber,
                            deviceId = deviceId,
                            encryptedLink = resolution.encryptedLink,
                        ),
                    )
                    processResolvedImport(
                        resolution = ResolvedImportInput.DirectConfig(
                            payload = resolved.rawConfig,
                            warnings = resolution.warnings + "Resolved SWIMVPN crypt1 payload via backend",
                        ),
                        sourceType = sourceType,
                    )
                }
                is ResolvedImportInput.DirectConfig -> processResolvedImport(resolution, sourceType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing configuration", e)
            ImportResult.Error(
                errors = listOf("Import failed: ${e.localizedMessage}"),
                warnings = emptyList()
            )
        }
    }

    suspend fun resolveRuntimeConfigForConnection(
        input: String,
        sourceType: SourceType = SourceType.BACKEND_API,
    ): Result<RuntimeConfigResolution> {
        return try {
            val resolution = resolveImportInput(input)
            val resolved = when (resolution) {
                is ResolvedImportInput.SubscriptionUrl -> {
                    val fetched = fetchSubscriptionPayload(resolution.url)
                    selectFirstRuntimeConfig(
                        payload = fetched.payload,
                        sourceType = SourceType.SUBSCRIPTION_URL,
                        warnings = resolution.warnings + fetched.warnings,
                        sourceUrl = resolution.url,
                        headerMetadata = fetched.headerMetadata,
                    )
                }
                is ResolvedImportInput.SwimEncryptedPayload -> {
                    val userNumber = prefs.userNumberFlow.first()
                        ?: throw IllegalStateException("No local user profile is available for backend runtime resolution")
                    val deviceId = getDeviceId()
                        ?: throw IllegalStateException("Device identity is unavailable for backend runtime resolution")
                    val resolvedPayload = api.resolveCryptSubscription(
                        ResolveCryptSubscriptionRequest(
                            userNumber = userNumber,
                            deviceId = deviceId,
                            encryptedLink = resolution.encryptedLink,
                        ),
                    )
                    selectFirstRuntimeConfig(
                        payload = resolvedPayload.rawConfig,
                        sourceType = sourceType,
                        warnings = resolution.warnings + "Resolved SWIMVPN crypt1 payload via backend",
                    )
                }
                is ResolvedImportInput.DirectConfig -> selectFirstRuntimeConfig(
                    payload = resolution.payload,
                    sourceType = sourceType,
                    warnings = resolution.warnings,
                    sourceUrl = resolution.sourceUrl,
                    headerMetadata = resolution.headerMetadata,
                )
                is ResolvedImportInput.HappEncryptedSubscription -> {
                    throw IllegalArgumentException(buildHappEncryptedSubscriptionMessage(resolution.version))
                }
                is ResolvedImportInput.HappRoutingDeepLink -> {
                    throw IllegalArgumentException("Recognized Happ routing deep link, but Happ routing profile import is not implemented yet")
                }
            }
            Result.success(resolved)
        } catch (error: Exception) {
            Log.e(TAG, "Runtime config resolution failed", error)
            Result.failure(error)
        }
    }
    
    /**
     * Get all imported profiles
     */
    suspend fun getAllProfiles(): List<SwimVpnProfile> {
        return try {
            val jsonString = context.dataStore.data.first()[IMPORTED_PROFILES_KEY]
            
            if (jsonString.isNullOrEmpty()) {
                emptyList()
            } else {
                val typeToken = object : TypeToken<List<SwimVpnProfile>>() {}.type
                gson.fromJson(jsonString, typeToken) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading profiles", e)
            emptyList()
        }
    }

    suspend fun getImportedProfileGroups(): List<ImportedProfileGroup> {
        val profiles = getAllProfiles()
        if (profiles.isEmpty()) return emptyList()

        return profiles
            .groupBy { it.sourceBundleId ?: it.id }
            .values
            .map { groupedProfiles ->
                val sorted = groupedProfiles.sortedBy { it.sourceBundleOrder }
                val first = sorted.first()
                ImportedProfileGroup(
                    id = first.sourceBundleId ?: first.id,
                    name = first.sourceBundleName ?: first.displayName,
                    profiles = sorted,
                )
            }
            .sortedByDescending { group -> group.profiles.maxOfOrNull { it.importedAt } ?: 0L }
    }
    

    
    /**
     * Get active profile (currently selected for VPN connection)
     */
    suspend fun getActiveProfile(): SwimVpnProfile? {
        return try {
            val activeId = context.dataStore.data.first()[ACTIVE_PROFILE_ID_KEY]
            
            if (activeId.isNullOrEmpty()) {
                null
            } else {
                getAllProfiles().find { it.id == activeId }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active profile", e)
            null
        }
    }

    suspend fun getActiveConfigMetadata(): ActiveConfigMetadata? {
        val profile = getActiveProfile() ?: return null
        return ActiveConfigMetadata.fromImportedProfile(profile)
    }

    suspend fun getImportedConfigMetadata(serverId: String): ActiveConfigMetadata? {
        val profile = getImportedProfileForServerId(serverId) ?: return null
        return ActiveConfigMetadata.fromImportedProfile(profile)
    }

    suspend fun getImportedProfileForServerId(serverId: String): SwimVpnProfile? {
        val profileId = importedProfileIdFromServerId(serverId) ?: return null
        return getAllProfiles().find { it.id == profileId }
    }

    /**
     * Set active profile
     */
    suspend fun setActiveProfile(profile: SwimVpnProfile) {
        try {
            val profileId = profile.id
            
            context.dataStore.edit { preferences ->
                preferences[ACTIVE_PROFILE_ID_KEY] = profileId
                preferences[LAST_USED_PROFILE_ID_KEY] = profileId
            }
            
            Log.i(TAG, "Set active profile: ${profile.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting active profile", e)
        }
    }
    
    /**
     * Delete a profile
     */
    suspend fun deleteProfile(profile: SwimVpnProfile) {
        try {
            val allProfiles = getAllProfiles()
            val updatedProfiles = allProfiles.filter { it.id != profile.id }
            saveProfiles(updatedProfiles)
            
            Log.i(TAG, "Deleted profile: ${profile.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting profile", e)
        }
    }
    

    
    /**
     * Generate a preview for a configuration string without importing it
     */
    fun previewConfig(input: String): ConfigPreview? {
        return try {
            val resolution = resolveImportInput(input)
            if (resolution !is ResolvedImportInput.DirectConfig) {
                return null
            }

            val entries = splitConfigEntries(resolution.payload)
            if (entries.isEmpty()) {
                return null
            }

            val parseResult = ConfigParserEngine.parseConfig(entries.first())
            if (!parseResult.isValid) {
                return null
            }
            
            val profile = parseResult.profile!!
            val preview = ConfigNormalizationEngine.generatePreview(profile)
            if (entries.size == 1) {
                preview
            } else {
                preview.copy(
                    warnings = preview.warnings + "${entries.size - 1} additional server(s) detected in this import group",
                    summary = "${preview.summary} • group size ${entries.size}",
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating preview", e)
            null
        }
    }

    suspend fun clearActiveProfile() {
        try {
            context.dataStore.edit { preferences ->
                preferences.remove(ACTIVE_PROFILE_ID_KEY)
            }
            Log.i(TAG, "Cleared active imported profile selection")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing active profile", e)
        }
    }
    
    /**
     * Check if a string is likely a VPN configuration
     */
    fun isLikelyVpnConfig(input: String): Boolean {
        return ConfigNormalizationEngine.isLikelyVpnConfig(input)
    }

    fun canAttemptImport(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return false
        }

        return when (val resolution = resolveImportInput(trimmed)) {
            is ResolvedImportInput.SubscriptionUrl,
            is ResolvedImportInput.SwimEncryptedPayload -> true
            is ResolvedImportInput.DirectConfig -> previewConfig(resolution.payload)?.let { preview ->
                preview.validationStatus == ValidationStatus.VALID || preview.validationStatus == ValidationStatus.WARNING
            } ?: false
            is ResolvedImportInput.HappEncryptedSubscription,
            is ResolvedImportInput.HappRoutingDeepLink -> false
        }
    }
    
    /**
     * Check clipboard content for potential VPN configurations
     */
    fun checkClipboardForConfig(clipboardContent: String?): ClipboardCheckResult {
        if (clipboardContent.isNullOrBlank()) {
            return ClipboardCheckResult.Empty
        }
        
        val trimmed = clipboardContent.trim()
        
        if (!isLikelyVpnConfig(trimmed)) {
            return ClipboardCheckResult.NotVpnConfig
        }

        if (isUnsupportedEncryptedHappDeepLink(trimmed)) {
            return ClipboardCheckResult.InvalidConfig
        }
        
        val preview = previewConfig(trimmed)
        if (preview == null) {
            return ClipboardCheckResult.InvalidConfig
        }
        
        return ClipboardCheckResult.ValidConfig(
            preview = preview,
            rawConfig = trimmed
        )
    }
    
    // Private helper methods
    
    private suspend fun saveProfiles(profiles: List<SwimVpnProfile>) {
        try {
            val jsonString = gson.toJson(profiles)
            context.dataStore.edit { preferences ->
                preferences[IMPORTED_PROFILES_KEY] = jsonString
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profiles", e)
        }
    }
    
    private fun checkDuplicate(newProfile: SwimVpnProfile, existingProfiles: List<SwimVpnProfile>): Boolean {
        // Simple duplicate check based on address, port, and protocol
        return existingProfiles.any { existing ->
            existing.address == newProfile.address &&
            existing.port == newProfile.port &&
            existing.protocol == newProfile.protocol &&
            existing.userId == newProfile.userId
        }
    }
    
    private fun splitConfigEntries(input: String): List<String> {
        val trimmed = normalizeImportPayloadForSplitting(input).trim()
        if (trimmed.isBlank()) return emptyList()

        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && !containsSupportedEntry(trimmed)) {
            return listOf(trimmed)
        }

        return VpnConfigLinkExtractor.extractEntries(trimmed)
    }

    private fun normalizeImportPayloadForSplitting(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank() || containsSupportedEntry(trimmed) || trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }

        val decoded = decodeSubscriptionBase64(trimmed)?.trim()
        return decoded ?: trimmed
    }

    private fun deriveBundleName(profiles: List<SwimVpnProfile>): String {
        if (profiles.size == 1) {
            return profiles.first().displayName
        }

        val firstName = profiles.first().displayName.takeIf { it.isNotBlank() }
        return if (firstName != null) {
            "$firstName +${profiles.size - 1}"
        } else {
            "Imported group (${profiles.size})"
        }
    }

    private suspend fun processResolvedImport(
        resolution: ResolvedImportInput.DirectConfig,
        sourceType: SourceType,
    ): ImportResult {
        val parsedSubscription = SubscriptionParser.parse(
            input = resolution.payload,
            sourceType = sourceType,
            sourceUrl = resolution.sourceUrl,
            headerMetadata = resolution.headerMetadata,
        )
        val existingProfiles = getAllProfiles()
        val warnings = (resolution.warnings + parsedSubscription.warnings).toMutableList()
        val silentWarnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val importedProfiles = mutableListOf<SwimVpnProfile>()
        val duplicateEntries = mutableListOf<String>()
        var skippedUnsupportedEntries = 0
        val bundleId = if (parsedSubscription.profiles.size > 1) UUID.randomUUID().toString() else null

        parsedSubscription.profiles.forEachIndexed { index, parsedProfile ->
            val parseResult = ConfigParserEngine.parseConfig(parsedProfile.raw, sourceType)
            if (!parseResult.isValid) {
                if (parseResult.isRecognizedUnsupportedRuntimeFormat()) {
                    skippedUnsupportedEntries += 1
                    silentWarnings += parseResult.errors
                    silentWarnings += parseResult.warnings
                } else {
                    errors += parseResult.errors
                    warnings += parseResult.warnings
                }
                return@forEachIndexed
            }

            val normalizedProfile = ConfigNormalizationEngine.normalizeProfile(parseResult)
            if (normalizedProfile == null) {
                errors += "Failed to normalize configuration ${index + 1}"
                warnings += parseResult.warnings
                return@forEachIndexed
            }

            if (checkDuplicate(normalizedProfile, existingProfiles + importedProfiles)) {
                duplicateEntries += normalizedProfile.displayName
                return@forEachIndexed
            }

            importedProfiles += normalizedProfile.copy(
                subscriptionProviderName = parsedProfile.providerName ?: parsedSubscription.providerName,
                subscriptionTrafficUsedBytes = parsedProfile.trafficUsedBytes ?: parsedSubscription.trafficUsedBytes,
                subscriptionTrafficTotalBytes = parsedProfile.trafficTotalBytes ?: parsedSubscription.trafficTotalBytes,
                subscriptionExpiresAt = parsedProfile.expiresAt ?: parsedSubscription.expiresAt,
                subscriptionAutoUpdateIntervalHours = parsedProfile.autoUpdateIntervalHours
                    ?: parsedSubscription.autoUpdateIntervalHours,
            )
            warnings += (parseResult.warnings + parsedProfile.warnings)
        }

        if (importedProfiles.isEmpty()) {
            return if (duplicateEntries.isNotEmpty() && errors.isEmpty()) {
                ImportResult.Duplicate(
                    warnings = listOf("This configuration appears to be a duplicate")
                )
            } else {
                ImportResult.Error(
                    errors = errors.ifEmpty {
                        if (skippedUnsupportedEntries > 0) {
                            listOf("No supported server could be imported yet")
                        } else {
                            listOf("No supported server could be imported")
                        }
                    },
                    warnings = warnings + silentWarnings,
                )
            }
        }

        val bundleName = deriveBundleName(importedProfiles)
        val finalizedProfiles = importedProfiles.mapIndexed { index, profile ->
            profile.copy(
                sourceBundleId = bundleId ?: profile.id,
                sourceBundleName = if (importedProfiles.size > 1) bundleName else profile.displayName,
                sourceBundleOrder = index,
            )
        }

        if (errors.isNotEmpty()) {
            warnings += "Imported ${finalizedProfiles.size} server(s), but ${errors.size} entrie(s) could not be parsed"
        }
        if (duplicateEntries.isNotEmpty()) {
            warnings += "${duplicateEntries.size} duplicate entrie(s) were ignored"
        }

        val updatedProfiles = existingProfiles + finalizedProfiles
        saveProfiles(updatedProfiles)

        Log.i(TAG, "Successfully imported ${finalizedProfiles.size} configuration(s)")

        return ImportResult.Success(
            profile = finalizedProfiles.first(),
            importedProfiles = finalizedProfiles,
            importedCount = finalizedProfiles.size,
            warnings = warnings,
            silentWarnings = silentWarnings,
            skippedUnsupportedCount = skippedUnsupportedEntries,
        )
    }

    private fun ParseResult.isRecognizedUnsupportedRuntimeFormat(): Boolean {
        return errors.any { error ->
            error.contains("recognized but not supported", ignoreCase = true)
        }
    }

    private fun selectFirstRuntimeConfig(
        payload: String,
        sourceType: SourceType,
        warnings: List<String> = emptyList(),
        sourceUrl: String? = null,
        headerMetadata: SubscriptionHeaderMetadata? = null,
    ): RuntimeConfigResolution {
        val parsedSubscription = SubscriptionParser.parse(
            input = payload,
            sourceType = sourceType,
            sourceUrl = sourceUrl,
            headerMetadata = headerMetadata,
        )
        val errors = mutableListOf<String>()

        parsedSubscription.profiles.forEachIndexed { index, parsedProfile ->
            val parseResult = ConfigParserEngine.parseConfig(parsedProfile.raw, sourceType)
            if (parseResult.isValid) {
                return RuntimeConfigResolution(
                    rawConfig = parsedProfile.raw,
                    displayName = parsedProfile.displayName,
                    warnings = warnings + parsedSubscription.warnings + parsedProfile.warnings + parseResult.warnings,
                )
            }
            errors += parseResult.errors.ifEmpty { listOf("Profile ${index + 1} could not be parsed") }
        }

        throw IllegalArgumentException(
            errors.ifEmpty { listOf("No supported runtime config found in subscription") }
                .joinToString("; "),
        )
    }

    private suspend fun fetchSubscriptionPayload(url: String): SubscriptionFetchResult = withContext(Dispatchers.IO) {
        val fetchAttempts = listOf(
            SubscriptionFetchAttempt("SWIMVPN-Android/1.0", "SWIMVPN default subscription client"),
            SubscriptionFetchAttempt("v2rayNG/1.9.0", "v2rayNG-compatible subscription client"),
            SubscriptionFetchAttempt("Happ/1.0", "Happ-compatible subscription client"),
        )
        val failures = mutableListOf<String>()
        var fallbackResult: SubscriptionFetchResult? = null
        var bestResult: SubscriptionFetchResult? = null
        var bestScore = 0

        fetchAttempts.forEach { attempt ->
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", attempt.userAgent)
                .build()

            try {
                subscriptionClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        failures += "${attempt.label}: HTTP ${response.code}"
                        return@use
                    }

                    val body = response.body ?: run {
                        failures += "${attempt.label}: empty response body"
                        return@use
                    }
                    val raw = body.string().take(MAX_SUBSCRIPTION_CHARS)
                    val normalized = normalizeSubscriptionPayload(raw)
                    val headerMetadata = SubscriptionMetadataParser.parseHttpHeaders(
                        subscriptionUserInfo = response.header("subscription-userinfo"),
                        profileUpdateInterval = response.header("profile-update-interval"),
                        sourceUrl = url,
                    )
                    val result = SubscriptionFetchResult(
                        payload = normalized.payload,
                        headerMetadata = headerMetadata.takeIf { it.hasValues },
                        warnings = buildList {
                            add("Fetched remote subscription URL")
                            add("Subscription fetched with ${attempt.label}")
                            addAll(normalized.warnings)
                            addAll(headerMetadata.warnings)
                            if (raw.length >= MAX_SUBSCRIPTION_CHARS) {
                                add("Subscription response was truncated to $MAX_SUBSCRIPTION_CHARS characters")
                            }
                        },
                    )

                    val score = subscriptionPayloadScore(normalized.payload)
                    if (score > bestScore) {
                        bestResult = result
                        bestScore = score
                    }

                    fallbackResult = fallbackResult ?: result.copy(
                        warnings = result.warnings + "Subscription response did not contain directly importable supported entries",
                    )
                }
            } catch (error: Exception) {
                failures += "${attempt.label}: ${error.localizedMessage}"
            }
        }

        bestResult ?: fallbackResult ?: throw IOException("Subscription fetch failed: ${failures.joinToString("; ")}")
    }

    private fun normalizeSubscriptionPayload(raw: String): SubscriptionPayload {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return SubscriptionPayload("")
        }

        if (containsSupportedEntry(trimmed) || trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return SubscriptionPayload(trimmed)
        }

        val decoded = decodeSubscriptionBase64(trimmed)
        return if (decoded != null && (containsSupportedEntry(decoded) || decoded.trim().startsWith("{") || decoded.trim().startsWith("["))) {
            SubscriptionPayload(
                payload = decoded.trim(),
                warnings = listOf("Decoded Base64 subscription payload"),
            )
        } else {
            SubscriptionPayload(trimmed)
        }
    }

    private fun containsSupportedEntry(input: String): Boolean {
        return VpnConfigLinkExtractor.containsRecognizedLink(input)
    }

    private fun subscriptionPayloadScore(input: String): Int {
        val trimmed = input.trim()
        return when {
            containsSupportedEntry(trimmed) -> 3
            trimmed.startsWith("{") -> 2
            trimmed.startsWith("[") -> 1
            else -> 0
        }
    }

    private fun decodeSubscriptionBase64(input: String): String? {
        val compact = input
            .removePrefix("\uFEFF")
            .lines()
            .joinToString("") { it.trim() }
            .filter { char -> char.isLetterOrDigit() || char == '+' || char == '/' || char == '-' || char == '_' || char == '=' }
            .trim()

        if (compact.isBlank()) {
            return null
        }

        fun padBase64(value: String): String {
            return value.let {
                val padding = (4 - value.length % 4) % 4
                value + "=".repeat(padding)
            }
        }

        val candidates = listOf(
            padBase64(compact),
            padBase64(compact.replace('-', '+').replace('_', '/')),
        ).distinct()

        val flags = listOf(
            android.util.Base64.DEFAULT,
            android.util.Base64.NO_WRAP,
            android.util.Base64.URL_SAFE,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
        )

        return candidates.asSequence()
            .flatMap { candidate -> flags.asSequence().map { flag -> candidate to flag } }
            .mapNotNull { (candidate, flag) ->
                runCatching {
                    String(android.util.Base64.decode(candidate, flag), Charsets.UTF_8)
                }.getOrNull()
            }
            .firstOrNull { decoded ->
                val trimmed = decoded.trim()
                containsSupportedEntry(trimmed) || trimmed.startsWith("{") || trimmed.startsWith("[")
            }
    }

    private fun resolveImportInput(input: String): ResolvedImportInput {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return ResolvedImportInput.DirectConfig(payload = trimmed)
        }

        val lowercase = trimmed.lowercase()

        if (lowercase.startsWith("swimvpn://crypt1/")) {
            return ResolvedImportInput.SwimEncryptedPayload(
                encryptedLink = trimmed,
                version = "crypt1",
                warnings = listOf("Recognized SWIMVPN crypt1 encrypted import payload"),
            )
        }

        if (lowercase.startsWith("happ://add/")) {
            val wrapped = URLDecoder.decode(trimmed.removePrefix("happ://add/"), "UTF-8").trim()
            val warnings = mutableListOf("Recognized Happ add deep link")
            return when {
                wrapped.startsWith("http://", ignoreCase = true) || wrapped.startsWith("https://", ignoreCase = true) -> {
                    ResolvedImportInput.SubscriptionUrl(
                        url = wrapped,
                        warnings = warnings + "Happ deep link wraps a subscription URL",
                    )
                }
                else -> ResolvedImportInput.DirectConfig(
                    payload = wrapped,
                    warnings = warnings,
                )
            }
        }

        if (
            lowercase.startsWith("happ://crypt3/") ||
            lowercase.startsWith("happ://crypt4/") ||
            lowercase.startsWith("happ://crypt5/")
        ) {
            val version = when {
                lowercase.startsWith("happ://crypt3/") -> "crypt3"
                lowercase.startsWith("happ://crypt4/") -> "crypt4"
                else -> "crypt5"
            }
            return ResolvedImportInput.HappEncryptedSubscription(
                payload = trimmed,
                version = version,
                warnings = listOf(happEncryptedSubscriptionWarning(version)),
            )
        }

        if (
            lowercase.startsWith("happ://routing/add/") ||
            lowercase.startsWith("happ://routing/onadd/") ||
            lowercase == "happ://routing/off"
        ) {
            return ResolvedImportInput.HappRoutingDeepLink(
                payload = trimmed,
                warnings = listOf("Recognized Happ routing deep link"),
            )
        }

        if (lowercase.startsWith("http://") || lowercase.startsWith("https://")) {
            return ResolvedImportInput.SubscriptionUrl(
                url = trimmed,
                warnings = listOf("Recognized remote subscription URL"),
            )
        }

        return ResolvedImportInput.DirectConfig(payload = trimmed)
    }

    private fun isUnsupportedEncryptedHappDeepLink(input: String): Boolean {
        val lowercase = input.trim().lowercase()
        return lowercase.startsWith("happ://crypt3/") ||
            lowercase.startsWith("happ://crypt4/") ||
            lowercase.startsWith("happ://crypt5/")
    }

    private fun getDeviceId(): String? = DeviceIdentityProvider.getDeviceId(context)

    private fun happEncryptedSubscriptionWarning(version: String): String {
        return when (version) {
            "crypt5" -> "Recognized Happ crypt5 encrypted subscription deep link (current/preferred Happ format)"
            "crypt4" -> "Recognized Happ crypt4 encrypted subscription deep link (legacy Happ format)"
            "crypt3" -> "Recognized Happ crypt3 encrypted subscription deep link (legacy Happ format)"
            else -> "Recognized Happ encrypted subscription deep link"
        }
    }

    private fun buildHappEncryptedSubscriptionMessage(version: String): String {
        val label = when (version) {
            "crypt5" -> "Happ crypt5 encrypted subscription"
            "crypt4" -> "Happ crypt4 encrypted subscription"
            "crypt3" -> "Happ crypt3 encrypted subscription"
            else -> "Happ encrypted subscription"
        }
        return "$label is recognized, but SWIMVPN cannot import Happ-protected encrypted subscription links without an authorized provider key/format. Import the original https subscription URL, a happ://add/https://... wrapper, or unencrypted node links such as vless://, vmess://, trojan://, ss://, or JSON Xray/V2Ray."
    }
}

private const val MAX_SUBSCRIPTION_CHARS = 1_000_000

private data class SubscriptionFetchResult(
    val payload: String,
    val headerMetadata: SubscriptionHeaderMetadata? = null,
    val warnings: List<String> = emptyList(),
)

private data class SubscriptionFetchAttempt(
    val userAgent: String,
    val label: String,
)

private data class SubscriptionPayload(
    val payload: String,
    val warnings: List<String> = emptyList(),
)

data class RuntimeConfigResolution(
    val rawConfig: String,
    val displayName: String? = null,
    val warnings: List<String> = emptyList(),
)

private sealed class ResolvedImportInput {
    data class DirectConfig(
        val payload: String,
        val warnings: List<String> = emptyList(),
        val sourceUrl: String? = null,
        val headerMetadata: SubscriptionHeaderMetadata? = null,
    ) : ResolvedImportInput()

    data class SubscriptionUrl(
        val url: String,
        val warnings: List<String> = emptyList(),
    ) : ResolvedImportInput()

    data class SwimEncryptedPayload(
        val encryptedLink: String,
        val version: String,
        val warnings: List<String> = emptyList(),
    ) : ResolvedImportInput()

    data class HappEncryptedSubscription(
        val payload: String,
        val version: String,
        val warnings: List<String> = emptyList(),
    ) : ResolvedImportInput()

    data class HappRoutingDeepLink(
        val payload: String,
        val warnings: List<String> = emptyList(),
    ) : ResolvedImportInput()
}

/**
 * Result types for import operations
 */
sealed class ImportResult {
    data class Success(
        val profile: SwimVpnProfile,
        val importedProfiles: List<SwimVpnProfile> = listOf(profile),
        val importedCount: Int = importedProfiles.size,
        val warnings: List<String> = emptyList(),
        val silentWarnings: List<String> = emptyList(),
        val skippedUnsupportedCount: Int = 0,
    ) : ImportResult()
    
    data class Duplicate(
        val warnings: List<String> = emptyList()
    ) : ImportResult()
    
    data class Error(
        val errors: List<String>,
        val warnings: List<String> = emptyList()
    ) : ImportResult()
}

/**
 * Result types for clipboard checking
 */
sealed class ClipboardCheckResult {
    object Empty : ClipboardCheckResult()
    object NotVpnConfig : ClipboardCheckResult()
    object InvalidConfig : ClipboardCheckResult()
    data class ValidConfig(
        val preview: ConfigPreview,
        val rawConfig: String
    ) : ClipboardCheckResult()
}

