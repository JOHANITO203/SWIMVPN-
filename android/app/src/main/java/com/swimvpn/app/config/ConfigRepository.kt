package com.swimvpn.app.config

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.swimvpn.app.config.subscriptionparser.SubscriptionHeaderMetadata
import com.swimvpn.app.config.subscriptionparser.SubscriptionPayloadDecoder
import com.swimvpn.app.config.subscriptionparser.SubscriptionParser
import com.swimvpn.app.data.local.DeviceIdentityProvider
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.data.local.SecureCrypto
import com.swimvpn.app.data.network.ResolveCryptSubscriptionRequest
import com.swimvpn.app.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.UUID

/**
 * Gson factory that maps unknown enum constants to a safe fallback (a member named UNKNOWN if
 * present, else the first declared constant) instead of throwing. Without this, a single enum
 * value added/renamed in a newer build would make Gson throw while deserializing stored profiles,
 * silently wiping the user's entire imported-profile list.
 */
private object EnumFallbackTypeAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val rawType = type.rawType
        if (!rawType.isEnum) return null
        val constants = rawType.enumConstants ?: return null
        val byName = HashMap<String, Any>()
        for (c in constants) byName[(c as Enum<*>).name] = c
        val fallback: Any? = byName["UNKNOWN"] ?: constants.firstOrNull()
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T) {
                if (value == null) out.nullValue() else out.value((value as Enum<*>).name)
            }

            @Suppress("UNCHECKED_CAST")
            override fun read(reader: JsonReader): T {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    return null as T
                }
                val name = reader.nextString()
                return (byName[name] ?: fallback) as T
            }
        }
    }
}

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
    // Tolerant Gson: unknown enum constants (e.g. after a schema change) map to UNKNOWN / the first
    // constant instead of throwing — prevents a single drift from wiping ALL stored profiles.
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapterFactory(EnumFallbackTypeAdapterFactory)
        .create()
    private val api = RetrofitClient.apiService
    private val prefs = PreferencesManager(context)
    private val subscriptionFetcher = SubscriptionFetcher()

    // Guards the whole due-subscription refresh pass so concurrent triggers
    // (app foreground + Servers screen open) cannot run overlapping refreshes.
    private val refreshMutex = Mutex()

    companion object {
        private const val IMPORTED_SERVER_ID_PREFIX = "imported:"
        private const val DATA_STORE_NAME = "vpn_configs"
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)
        
        // Preference keys
        private val IMPORTED_PROFILES_KEY = stringPreferencesKey("imported_profiles")
        // Holds the raw blob when it fails to parse, so corrupt data is recoverable instead of
        // being silently overwritten by the next save.
        private val CORRUPT_PROFILES_BACKUP_KEY = stringPreferencesKey("imported_profiles_corrupt_backup")
        private val ACTIVE_PROFILE_ID_KEY = stringPreferencesKey("active_profile_id")
        private val LAST_USED_PROFILE_ID_KEY = stringPreferencesKey("last_used_profile_id")

        // Per-subscription auto-refresh registry. Stores, keyed by the original
        // subscription URL, the provider-declared interval and the last successful
        // refresh timestamp. Kept in this DataStore so it lives next to the
        // imported profiles it describes (no extra store, no Gradle deps).
        private val SUBSCRIPTION_REFRESH_REGISTRY_KEY = stringPreferencesKey("subscription_refresh_registry")

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

                    val result = processResolvedImport(
                        resolution = ResolvedImportInput.DirectConfig(
                            payload = fetched.payload,
                            warnings = resolution.warnings + fetched.warnings,
                            sourceUrl = resolution.url,
                            headerMetadata = fetched.headerMetadata,
                        ),
                        sourceType = SourceType.SUBSCRIPTION_URL,
                    )
                    // Track this subscription URL so it can be auto-refreshed later.
                    // Only meaningful when the provider declared an update interval.
                    if (result is ImportResult.Success) {
                        val intervalHours = result.importedProfiles
                            .firstNotNullOfOrNull { it.subscriptionAutoUpdateIntervalHours }
                            ?: 0
                        if (intervalHours > 0) {
                            trackSubscriptionForRefresh(
                                url = resolution.url,
                                intervalHours = intervalHours,
                                lastRefreshedAtMs = System.currentTimeMillis(),
                            )
                        }
                    }
                    result
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

    suspend fun resolveRuntimeProfilesForConnection(
        input: String,
        sourceType: SourceType = SourceType.BACKEND_API,
    ): Result<List<SwimVpnProfile>> {
        return try {
            val resolution = resolveImportInput(input)
            val profiles = when (resolution) {
                is ResolvedImportInput.SubscriptionUrl -> {
                    val fetched = fetchSubscriptionPayload(resolution.url)
                    selectRuntimeProfiles(
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
                    selectRuntimeProfiles(
                        payload = resolvedPayload.rawConfig,
                        sourceType = sourceType,
                        warnings = resolution.warnings + "Resolved SWIMVPN crypt1 payload via backend",
                    )
                }
                is ResolvedImportInput.DirectConfig -> selectRuntimeProfiles(
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
            Result.success(profiles)
        } catch (error: Exception) {
            Log.e(TAG, "Runtime profile resolution failed", error)
            Result.failure(error)
        }
    }
    
    /**
     * Get all imported profiles
     */
    suspend fun getAllProfiles(): List<SwimVpnProfile> {
        val jsonString = try {
            context.dataStore.data.first()[IMPORTED_PROFILES_KEY]
        } catch (e: Exception) {
            Log.e(TAG, "Error reading profiles store", e)
            null
        }
        if (jsonString.isNullOrEmpty()) return emptyList()

        return try {
            // Decrypt at-rest (legacy plaintext blobs pass through unchanged — transparent migration).
            val decoded = SecureCrypto.decrypt(jsonString)
            val typeToken = object : TypeToken<List<SwimVpnProfile>>() {}.type
            gson.fromJson<List<SwimVpnProfile>>(decoded, typeToken) ?: emptyList()
        } catch (e: Exception) {
            // A NON-empty blob failed to parse. Do NOT silently treat it as "no profiles": the next
            // save would overwrite recoverable data. Back up the raw blob first (best effort).
            Log.e(TAG, "Error parsing profiles — backing up raw blob before fallback", e)
            backupCorruptProfiles(jsonString)
            emptyList()
        }
    }

    private suspend fun backupCorruptProfiles(raw: String) {
        try {
            context.dataStore.edit { prefs ->
                // Encrypt at rest: the corrupt blob can carry credentials (esp. a legacy plaintext
                // blob that failed to parse), so it must not sit in cleartext like the live store.
                prefs[CORRUPT_PROFILES_BACKUP_KEY] =
                    SecureCrypto.encrypt("${System.currentTimeMillis()}\n$raw")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to back up corrupt profiles blob", e)
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
                preferences[IMPORTED_PROFILES_KEY] = SecureCrypto.encrypt(jsonString)
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

        val decoded = SubscriptionPayloadDecoder.decode(trimmed).payload.trim()
        return decoded.takeIf {
            containsSupportedEntry(it) || it.startsWith("{") || it.startsWith("[")
        } ?: trimmed
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

    private fun selectRuntimeProfiles(
        payload: String,
        sourceType: SourceType,
        warnings: List<String> = emptyList(),
        sourceUrl: String? = null,
        headerMetadata: SubscriptionHeaderMetadata? = null,
    ): List<SwimVpnProfile> {
        val parsedSubscription = SubscriptionParser.parse(
            input = payload,
            sourceType = sourceType,
            sourceUrl = sourceUrl,
            headerMetadata = headerMetadata,
        )
        val errors = mutableListOf<String>()
        val profiles = mutableListOf<SwimVpnProfile>()
        val bundleId = if (parsedSubscription.profiles.size > 1) UUID.randomUUID().toString() else null

        parsedSubscription.profiles.forEachIndexed { index, parsedProfile ->
            val parseResult = ConfigParserEngine.parseConfig(parsedProfile.raw, sourceType)
            if (!parseResult.isValid) {
                errors += parseResult.errors.ifEmpty { listOf("Profile ${index + 1} could not be parsed") }
                return@forEachIndexed
            }

            val normalizedProfile = ConfigNormalizationEngine.normalizeProfile(parseResult)
            if (normalizedProfile == null) {
                errors += "Failed to normalize profile ${index + 1}"
                return@forEachIndexed
            }

            profiles += normalizedProfile.copy(
                subscriptionProviderName = parsedProfile.providerName ?: parsedSubscription.providerName,
                subscriptionTrafficUsedBytes = parsedProfile.trafficUsedBytes ?: parsedSubscription.trafficUsedBytes,
                subscriptionTrafficTotalBytes = parsedProfile.trafficTotalBytes ?: parsedSubscription.trafficTotalBytes,
                subscriptionExpiresAt = parsedProfile.expiresAt ?: parsedSubscription.expiresAt,
                subscriptionAutoUpdateIntervalHours = parsedProfile.autoUpdateIntervalHours
                    ?: parsedSubscription.autoUpdateIntervalHours,
                parseWarnings = (normalizedProfile.parseWarnings + warnings + parsedProfile.warnings).distinct(),
            )
        }

        if (profiles.isEmpty()) {
            throw IllegalArgumentException(
                errors.ifEmpty { listOf("No supported runtime config found in subscription") }
                    .joinToString("; "),
            )
        }

        val bundleName = deriveBundleName(profiles)
        return profiles.mapIndexed { index, profile ->
            profile.copy(
                sourceBundleId = bundleId ?: profile.id,
                sourceBundleName = if (profiles.size > 1) bundleName else profile.displayName,
                sourceBundleOrder = index,
            )
        }
    }

    private suspend fun fetchSubscriptionPayload(url: String): SubscriptionFetchResult = subscriptionFetcher.fetch(url)

    // region Subscription auto-refresh

    /**
     * Conservatively re-fetch + re-import every subscription whose declared
     * auto-update interval has elapsed (per [SubscriptionRefreshPolicy]).
     *
     * Non-destructive guarantees:
     *  - Existing imported profiles are NEVER wiped. Refresh reuses the normal
     *    [importConfig] path, which only APPENDS de-duplicated new profiles and
     *    leaves existing entries (including the active server's config) untouched.
     *  - If a fetch fails / returns empty / yields no importable server, the
     *    existing configs stay exactly as they were and the per-subscription
     *    timestamp is NOT advanced (so it retries on the next trigger).
     *  - The active selection is never cleared here; selection lives in separate
     *    keys and is not mutated by import.
     *
     * Idempotent + race-safe: the whole pass is guarded by [refreshMutex]; a
     * concurrent trigger returns [RefreshOutcome.Skipped] instead of racing.
     *
     * @param nowMs current epoch millis (injectable for testing/determinism).
     */
    suspend fun refreshSubscriptionsIfDue(nowMs: Long = System.currentTimeMillis()): RefreshOutcome {
        if (!refreshMutex.tryLock()) {
            return RefreshOutcome.Skipped
        }
        try {
            val registry = readRefreshRegistry()
            if (registry.isEmpty()) {
                return RefreshOutcome.Completed(emptyList())
            }

            val due = registry.values.filter { entry ->
                SubscriptionRefreshPolicy.shouldRefresh(
                    intervalHours = entry.intervalHours,
                    lastRefreshedAtMs = entry.lastRefreshedAtMs,
                    nowMs = nowMs,
                )
            }
            if (due.isEmpty()) {
                return RefreshOutcome.Completed(emptyList())
            }

            val results = mutableListOf<SubscriptionRefreshResult>()
            for (entry in due) {
                val result = runCatching { importConfig(entry.url, SourceType.SUBSCRIPTION_URL) }
                    .getOrElse { error ->
                        ImportResult.Error(errors = listOf(error.localizedMessage ?: "refresh failed"))
                    }

                when (result) {
                    is ImportResult.Success -> {
                        // Success path advances the timestamp. Note importConfig already
                        // re-registered the URL with a fresh timestamp; we still write here
                        // to cover the (rare) case where the interval was dropped to 0.
                        updateRefreshTimestamp(entry.url, nowMs)
                        results += SubscriptionRefreshResult(
                            url = entry.url,
                            succeeded = true,
                            importedCount = result.importedCount,
                        )
                    }
                    is ImportResult.Duplicate -> {
                        // Nothing new, but the fetch succeeded => advance timestamp so we
                        // do not hammer the provider every trigger. Existing configs intact.
                        updateRefreshTimestamp(entry.url, nowMs)
                        results += SubscriptionRefreshResult(
                            url = entry.url,
                            succeeded = true,
                            importedCount = 0,
                        )
                    }
                    is ImportResult.Error -> {
                        // Non-destructive: keep configs AND keep the old timestamp so the
                        // subscription stays "due" and retries on the next trigger.
                        results += SubscriptionRefreshResult(
                            url = entry.url,
                            succeeded = false,
                            importedCount = 0,
                        )
                    }
                }
            }
            return RefreshOutcome.Completed(results)
        } finally {
            refreshMutex.unlock()
        }
    }

    private suspend fun trackSubscriptionForRefresh(url: String, intervalHours: Int, lastRefreshedAtMs: Long) {
        val registry = readRefreshRegistry().toMutableMap()
        registry[url] = SubscriptionRefreshEntry(url, intervalHours, lastRefreshedAtMs)
        writeRefreshRegistry(registry)
    }

    private suspend fun updateRefreshTimestamp(url: String, nowMs: Long) {
        val registry = readRefreshRegistry().toMutableMap()
        val existing = registry[url] ?: return
        registry[url] = existing.copy(lastRefreshedAtMs = nowMs)
        writeRefreshRegistry(registry)
    }

    private suspend fun readRefreshRegistry(): Map<String, SubscriptionRefreshEntry> {
        return try {
            val stored = context.dataStore.data.first()[SUBSCRIPTION_REFRESH_REGISTRY_KEY]
            // Decrypt at rest (legacy plaintext registries pass through unchanged — transparent migration).
            val json = stored?.let { SecureCrypto.decrypt(it) }
            if (json.isNullOrEmpty()) {
                emptyMap()
            } else {
                val typeToken = object : TypeToken<List<SubscriptionRefreshEntry>>() {}.type
                val entries: List<SubscriptionRefreshEntry> = gson.fromJson(json, typeToken) ?: emptyList()
                entries.filter { it.url.isNotBlank() }.associateBy { it.url }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading subscription refresh registry", e)
            emptyMap()
        }
    }

    private suspend fun writeRefreshRegistry(registry: Map<String, SubscriptionRefreshEntry>) {
        try {
            val json = gson.toJson(registry.values.toList())
            context.dataStore.edit { preferences ->
                // Encrypt at rest: subscription URLs can embed access tokens.
                preferences[SUBSCRIPTION_REFRESH_REGISTRY_KEY] = SecureCrypto.encrypt(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing subscription refresh registry", e)
        }
    }

    // endregion

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

data class RuntimeConfigResolution(
    val rawConfig: String,
    val displayName: String? = null,
    val warnings: List<String> = emptyList(),
)

/**
 * Persisted per-subscription refresh tracking record.
 * Keyed by [url] inside the refresh registry.
 */
data class SubscriptionRefreshEntry(
    val url: String,
    val intervalHours: Int,
    val lastRefreshedAtMs: Long,
)

/** Per-subscription outcome of a single refresh attempt. */
data class SubscriptionRefreshResult(
    val url: String,
    val succeeded: Boolean,
    val importedCount: Int,
)

/** Summary returned by [ConfigRepository.refreshSubscriptionsIfDue]. */
sealed class RefreshOutcome {
    /** A concurrent refresh pass was already running; nothing was done. */
    object Skipped : RefreshOutcome()

    /** The pass ran. [results] is empty when nothing was due. */
    data class Completed(val results: List<SubscriptionRefreshResult>) : RefreshOutcome() {
        val attempted: Int get() = results.size
        val succeeded: Int get() = results.count { it.succeeded }
        val failed: Int get() = results.count { !it.succeeded }
        val newlyImported: Int get() = results.sumOf { it.importedCount }
    }
}

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

