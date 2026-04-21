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
import kotlinx.coroutines.flow.first
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
    
    companion object {
        private const val DATA_STORE_NAME = "vpn_configs"
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)
        
        // Preference keys
        private val IMPORTED_PROFILES_KEY = stringPreferencesKey("imported_profiles")
        private val ACTIVE_PROFILE_ID_KEY = stringPreferencesKey("active_profile_id")
        private val LAST_USED_PROFILE_ID_KEY = stringPreferencesKey("last_used_profile_id")
    }
    
    /**
     * Import a configuration string and store it as a normalized profile
     */
    suspend fun importConfig(input: String, sourceType: SourceType = SourceType.MANUAL_ENTRY): ImportResult {
        return try {
            // Parse the configuration
            val parseResult = ConfigParserEngine.parseConfig(input, sourceType)
            
            if (!parseResult.isValid) {
                return ImportResult.Error(
                    errors = parseResult.errors,
                    warnings = parseResult.warnings
                )
            }
            
            // Normalize the profile
            val normalizedProfile = ConfigNormalizationEngine.normalizeProfile(parseResult)
            
            if (normalizedProfile == null) {
                return ImportResult.Error(
                    errors = listOf("Failed to normalize configuration"),
                    warnings = parseResult.warnings
                )
            }
            
            // Check for duplicates
            val existingProfiles = getAllProfiles()
            val isDuplicate = checkDuplicate(normalizedProfile, existingProfiles)
            
            if (isDuplicate) {
                return ImportResult.Duplicate(
                    warnings = listOf("This configuration appears to be a duplicate")
                )
            }
            
            val updatedProfiles = existingProfiles + normalizedProfile
            saveProfiles(updatedProfiles)
            
            Log.i(TAG, "Successfully imported ${normalizedProfile.protocol} configuration")
            
            ImportResult.Success(
                profile = normalizedProfile,
                warnings = parseResult.warnings
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error importing configuration", e)
            ImportResult.Error(
                errors = listOf("Import failed: ${e.localizedMessage}"),
                warnings = emptyList()
            )
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
    
    /**
     * Set active profile
     */
    suspend fun setActiveProfile(profile: SwimVpnProfile) {
        try {
            // Update last used timestamp - would need to save updated profile
            val profileId = generateProfileId(profile)
            
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
            val parseResult = ConfigParserEngine.parseConfig(input)
            if (!parseResult.isValid) {
                return null
            }
            
            val profile = parseResult.profile!!
            ConfigNormalizationEngine.generatePreview(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating preview", e)
            null
        }
    }
    
    /**
     * Check if a string is likely a VPN configuration
     */
    fun isLikelyVpnConfig(input: String): Boolean {
        return ConfigNormalizationEngine.isLikelyVpnConfig(input)
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
    
    private fun generateProfileId(profile: SwimVpnProfile): String {
        // Generate a unique ID based on profile properties
        val base = "${profile.protocol}:${profile.address}:${profile.port}:${profile.userId ?: profile.password ?: ""}"
        return UUID.nameUUIDFromBytes(base.toByteArray()).toString()
    }
}

/**
 * Result types for import operations
 */
sealed class ImportResult {
    data class Success(
        val profile: SwimVpnProfile,
        val warnings: List<String> = emptyList()
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

