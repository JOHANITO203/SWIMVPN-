package com.swimvpn.app.config

import android.util.Log

/**
 * Compatibility and Normalization Engine
 * 
 * Responsibilities:
 * - Detect source format
 * - Classify protocol, transport, security mode
 * - Normalize all supported formats to canonical SwimVpnProfile
 * - Validate configurations
 * - Generate runtime-ready configurations
 */
object ConfigNormalizationEngine {

    private val TAG = "ConfigNormalizationEngine"

    private val gson = com.google.gson.GsonBuilder()
        .disableHtmlEscaping()
        .create()
    
    /**
     * Main entry point: normalize a parsed configuration
     */
    fun normalizeProfile(parseResult: ParseResult): SwimVpnProfile? {
        if (!parseResult.isValid) {
            Log.w(TAG, "Cannot normalize invalid parse result")
            return null
        }
        
        val profile = parseResult.profile ?: return null
        
        return try {
            // Apply normalization steps
            val validated = validateProfile(profile)
            val enriched = enrichProfile(validated)
            val runtimeReady = prepareRuntimeConfig(enriched)
            
            runtimeReady
        } catch (e: Exception) {
            Log.e(TAG, "Error normalizing profile", e)
            null
        }
    }
    
    /**
     * Validate profile for completeness and correctness
     */
    private fun validateProfile(profile: SwimVpnProfile): SwimVpnProfile {
        val validationWarnings = mutableListOf<String>()
        val validationErrors = mutableListOf<String>()
        
        // Basic validation
        if (profile.address.isBlank()) {
            validationErrors.add("Missing server address")
        }

        if (profile.advancedSettings.keys.any { it.equals("fragment", ignoreCase = true) || it.equals("noises", ignoreCase = true) || it.equals("noise", ignoreCase = true) }) {
            validationWarnings.add("Advanced DPI metadata is preserved, but fragment/noises runtime support is not verified yet")
        }
        
        if (profile.port <= 0 || profile.port > 65535) {
            validationErrors.add("Invalid port number: ${profile.port}")
        }
        
        // Protocol-specific validation
        when (profile.protocol) {
            Protocol.VLESS -> {
                if (profile.userId.isNullOrBlank()) {
                    validationErrors.add("VLESS requires user ID")
                }
                if (profile.userId?.length ?: 0 < 32) {
                    validationWarnings.add("VLESS user ID may be too short")
                }
            }
            Protocol.VMESS -> {
                if (profile.userId.isNullOrBlank()) {
                    validationErrors.add("VMess requires user ID")
                }
            }
            Protocol.TROJAN -> {
                if (profile.password.isNullOrBlank()) {
                    validationErrors.add("Trojan requires password")
                }
                if (profile.securityMode != SecurityMode.TLS) {
                    validationWarnings.add("Trojan should use TLS for security")
                }
            }
            Protocol.SHADOWSOCKS -> {
                if (profile.password.isNullOrBlank()) {
                    validationErrors.add("Shadowsocks requires password")
                }
                if (profile.method.isNullOrBlank()) {
                    validationErrors.add("Shadowsocks requires encryption method")
                }
                if (profile.shadowsocksPluginSettings != null) {
                    validationWarnings.add("Shadowsocks plugin metadata is preserved, but plugin runtime support is not fully verified yet")
                }
            }
            Protocol.SOCKS5, Protocol.HTTP -> {
                // BYO proxy: address + port already validated above; auth is optional.
            }
            Protocol.UNKNOWN -> {
                validationErrors.add("Unknown protocol")
            }
        }

        // Transport validation
        when (profile.transport) {
            Transport.WEBSOCKET -> {
                if (profile.websocketSettings == null) {
                    validationWarnings.add("WebSocket transport missing settings")
                }
            }
            Transport.GRPC -> {
                if (profile.grpcSettings == null) {
                    validationWarnings.add("gRPC transport missing settings")
                }
            }
            else -> {
                // Other transports may have optional settings
            }
        }
        
        // Security validation
        when (profile.securityMode) {
            SecurityMode.TLS -> {
                if (profile.tlsSettings == null) {
                    validationWarnings.add("TLS security missing settings")
                }
            }
            SecurityMode.REALITY -> {
                if (profile.realitySettings == null) {
                    validationErrors.add("Reality security missing settings")
                }
            }
            else -> {
                // None or XTLS
            }
        }
        
        val updatedWarnings = profile.parseWarnings + validationWarnings
        val updatedErrors = profile.parseErrors + validationErrors
        
        return profile.copy(
            parseWarnings = updatedWarnings,
            parseErrors = updatedErrors
        )
    }
    
    /**
     * Enrich profile with additional metadata and defaults
     */
    private fun enrichProfile(profile: SwimVpnProfile): SwimVpnProfile {
        var enriched = profile
        
        // Set default display names if missing
        if (enriched.displayName.isBlank()) {
            enriched = enriched.copy(
                displayName = generateDefaultDisplayName(enriched)
            )
        }
        
        if (enriched.displaySubtitle.isBlank()) {
            enriched = enriched.copy(
                displaySubtitle = generateDefaultSubtitle(enriched)
            )
        }
        
        // Apply protocol-specific enrichments
        enriched = when (enriched.protocol) {
            Protocol.VLESS -> enrichVlessProfile(enriched)
            Protocol.VMESS -> enrichVmessProfile(enriched)
            Protocol.TROJAN -> enrichTrojanProfile(enriched)
            Protocol.SHADOWSOCKS -> enrichShadowsocksProfile(enriched)
            else -> enriched
        }
        
        return enriched
    }
    
    /**
     * Prepare runtime configuration for tunnel engine
     */
    private fun prepareRuntimeConfig(profile: SwimVpnProfile): SwimVpnProfile {
        // Single producer for the proxy outbound (shared with TunnelRuntimeAdapter via
        // XrayOutboundBuilder), so VLESS/VMESS get full transports + complete Reality/XTLS, and
        // every protocol emits the same correct streamSettings. SOCKS5/HTTP BYO proxies return null
        // here and keep their dedicated outbound builders in the adapter.
        val outbound = XrayOutboundBuilder.forProfile(profile)
        return profile.copy(
            normalizedRuntimeConfig = outbound?.let { gson.toJson(it) },
        )
    }
    
    /**
     * Generate configuration preview for UI display
     */
    fun generatePreview(profile: SwimVpnProfile): ConfigPreview {
        val validationStatus = when {
            profile.parseErrors.isNotEmpty() -> ValidationStatus.ERROR
            profile.parseWarnings.isNotEmpty() -> ValidationStatus.WARNING
            else -> ValidationStatus.VALID
        }
        
        val summary = buildString {
            append("${profile.protocol.name} connection to ${profile.address}:${profile.port}")
            if (profile.transport != Transport.UNKNOWN) {
                append(" via ${profile.transport.name}")
            }
            if (profile.securityMode != SecurityMode.NONE) {
                append(" with ${profile.securityMode.name} security")
            }
        }
        
        return ConfigPreview(
            protocol = profile.protocol.name,
            address = profile.address,
            port = profile.port,
            transport = profile.transport.name,
            security = profile.securityMode.name,
            displayName = profile.displayName,
            validationStatus = validationStatus,
            warnings = profile.parseWarnings,
            summary = summary
        )
    }
    
    /**
     * Helper: Generate default display name
     */
    private fun generateDefaultDisplayName(profile: SwimVpnProfile): String {
        return "${profile.protocol.name}: ${profile.address}"
    }
    
    /**
     * Helper: Generate default subtitle
     */
    private fun generateDefaultSubtitle(profile: SwimVpnProfile): String {
        val parts = mutableListOf<String>()
        
        if (profile.transport != Transport.UNKNOWN) {
            parts.add(profile.transport.name)
        }
        
        if (profile.securityMode != SecurityMode.NONE) {
            parts.add(profile.securityMode.name)
        }
        
        parts.add("Port: ${profile.port}")
        
        return parts.joinToString(", ")
    }
    
    /**
     * Protocol-specific enrichment
     */
    private fun enrichVlessProfile(profile: SwimVpnProfile): SwimVpnProfile {
        var enriched = profile
        
        // Ensure TLS settings for VLESS with TLS security
        if (enriched.securityMode == SecurityMode.TLS && enriched.tlsSettings == null) {
            enriched = enriched.copy(
                tlsSettings = TlsSettings(
                    sni = enriched.address,
                    allowInsecure = false,
                    alpn = listOf("h2", "http/1.1")
                )
            )
        }
        
        return enriched
    }
    
    private fun enrichVmessProfile(profile: SwimVpnProfile): SwimVpnProfile {
        // VMess-specific enrichment
        return profile
    }
    
    private fun enrichTrojanProfile(profile: SwimVpnProfile): SwimVpnProfile {
        var enriched = profile
        
        // Trojan always uses TLS
        if (enriched.securityMode == SecurityMode.NONE) {
            enriched = enriched.copy(
                securityMode = SecurityMode.TLS
            )
        }
        
        // Ensure TLS settings
        if (enriched.securityMode == SecurityMode.TLS && enriched.tlsSettings == null) {
            enriched = enriched.copy(
                tlsSettings = TlsSettings(
                    sni = enriched.address,
                    allowInsecure = false
                )
            )
        }
        
        return enriched
    }
    
    private fun enrichShadowsocksProfile(profile: SwimVpnProfile): SwimVpnProfile {
        // Shadowsocks-specific enrichment
        return profile
    }
    
    /**
     * Detect if a raw string is likely a VPN configuration
     */
    fun isLikelyVpnConfig(input: String): Boolean {
        val trimmed = input.trim()
        
        return when {
            trimmed.startsWith("vless://") -> true
            trimmed.startsWith("vmess://") -> true
            trimmed.startsWith("trojan://") -> true
            trimmed.startsWith("ss://") -> true
            trimmed.startsWith("hy2://", ignoreCase = true) -> true
            trimmed.startsWith("hysteria2://", ignoreCase = true) -> true
            trimmed.startsWith("hysteria://", ignoreCase = true) -> true
            trimmed.startsWith("tuic://", ignoreCase = true) -> true
            trimmed.startsWith("socks://", ignoreCase = true) -> true
            trimmed.startsWith("socks5://", ignoreCase = true) -> true
            trimmed.startsWith("wg://", ignoreCase = true) -> true
            trimmed.startsWith("wireguard://", ignoreCase = true) -> true
            trimmed.startsWith("swimvpn://crypt1/", ignoreCase = true) -> true
            trimmed.startsWith("happ://", ignoreCase = true) -> true
            trimmed.startsWith("https://", ignoreCase = true) -> true
            trimmed.startsWith("http://", ignoreCase = true) -> true
            isJsonConfig(trimmed) -> true
            else -> false
        }
    }
    
    private fun isJsonConfig(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.startsWith('{') && trimmed.endsWith('}') ||
               trimmed.startsWith('[') && trimmed.endsWith(']')
    }
}
