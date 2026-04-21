package com.swimvpn.app.config

import android.content.Intent
import android.util.Log
import com.swimvpn.app.SwimVpnService

/**
 * Tunnel Runtime Adapter Engine
 * 
 * Responsibilities:
 * - Convert normalized SwimVpnProfile to internal runtime format
 * - Prepare runtime-safe configuration for tunnel execution
 * - Separate parsing logic from execution logic
 * - Generate appropriate service intents based on profile
 */
object TunnelRuntimeAdapter {
    
    private val TAG = "TunnelRuntimeAdapter"
    
    /**
     * Prepare VPN service intent from a SwimVpnProfile
     */
    fun prepareVpnIntent(profile: SwimVpnProfile): Intent {
        return Intent().apply {
            action = SwimVpnService.ACTION_START
            
            // Basic connection parameters
            putExtra(SwimVpnService.EXTRA_SERVER_HOST, profile.address)
            putExtra(SwimVpnService.EXTRA_SERVER_PORT, profile.port)
            putExtra(SwimVpnService.EXTRA_PROTOCOL, profile.protocol.name)
            
            // Pass normalized runtime config if available
            profile.normalizedRuntimeConfig?.let {
                putExtra("NORMALIZED_CONFIG", it)
            }
            
            // Pass raw config for debugging/fallback
            putExtra("RAW_CONFIG", profile.rawConfig)
            
            // Protocol-specific parameters
            when (profile.protocol) {
                Protocol.VLESS -> {
                    putExtra("USER_ID", profile.userId)
                    putExtra("SECURITY_MODE", profile.securityMode.name)
                    putExtra("TRANSPORT", profile.transport.name)
                    
                    // Transport-specific settings
                    profile.tlsSettings?.let { tls ->
                        putExtra("TLS_SNI", tls.sni)
                        putExtra("TLS_ALLOW_INSECURE", tls.allowInsecure)
                        if (tls.alpn.isNotEmpty()) {
                            putExtra("TLS_ALPN", tls.alpn.joinToString(","))
                        }
                    }
                    
                    profile.websocketSettings?.let { ws ->
                        putExtra("WS_PATH", ws.path)
                        ws.host?.let { host ->
                            putExtra("WS_HOST", host)
                        }
                    }
                    
                    profile.realitySettings?.let { reality ->
                        putExtra("REALITY_PUBLIC_KEY", reality.publicKey)
                        putExtra("REALITY_SHORT_ID", reality.shortId)
                        reality.spiderX?.let { spiderX ->
                            putExtra("REALITY_SPIDER_X", spiderX)
                        }
                    }
                }
                
                Protocol.VMESS -> {
                    putExtra("USER_ID", profile.userId)
                    putExtra("SECURITY_MODE", profile.securityMode.name)
                    putExtra("TRANSPORT", profile.transport.name)
                }
                
                Protocol.TROJAN -> {
                    putExtra("PASSWORD", profile.password)
                    putExtra("SECURITY_MODE", profile.securityMode.name)
                    putExtra("TRANSPORT", profile.transport.name)
                    
                    profile.tlsSettings?.let { tls ->
                        putExtra("TLS_SNI", tls.sni)
                        putExtra("TLS_ALLOW_INSECURE", tls.allowInsecure)
                    }
                    
                    profile.websocketSettings?.let { ws ->
                        putExtra("WS_PATH", ws.path)
                        ws.host?.let { host ->
                            putExtra("WS_HOST", host)
                        }
                    }
                }
                
                Protocol.SHADOWSOCKS -> {
                    putExtra("PASSWORD", profile.password)
                    putExtra("METHOD", profile.method)
                    // Shadowsocks typically uses TCP without additional transport settings
                }
                
                Protocol.UNKNOWN -> {
                    Log.w(TAG, "Unknown protocol, using raw config fallback")
                }
            }
            
            // Connection metadata for logging
            putExtra("PROFILE_ID", profile.id)
            putExtra("DISPLAY_NAME", profile.displayName)
            putExtra("SOURCE_FORMAT", profile.sourceFormat.name)
        }
    }
    
    /**
     * Generate Xray-core compatible JSON configuration from profile
     */
    fun generateXrayConfig(profile: SwimVpnProfile): String? {
        return try {
            when (profile.protocol) {
                Protocol.VLESS -> generateVlessXrayConfig(profile)
                Protocol.VMESS -> generateVmessXrayConfig(profile)
                Protocol.TROJAN -> generateTrojanXrayConfig(profile)
                Protocol.SHADOWSOCKS -> generateShadowsocksXrayConfig(profile)
                Protocol.UNKNOWN -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Xray config", e)
            null
        }
    }
    
    /**
     * Check if profile is compatible with current runtime capabilities
     */
    fun isProfileSupported(profile: SwimVpnProfile): Pair<Boolean, String> {
        // Basic validation
        if (profile.address.isBlank()) {
            return Pair(false, "Missing server address")
        }
        
        if (profile.port <= 0 || profile.port > 65535) {
            return Pair(false, "Invalid port number")
        }
        
        // Protocol-specific validation
        return when (profile.protocol) {
            Protocol.VLESS -> {
                if (profile.userId.isNullOrBlank()) {
                    Pair(false, "VLESS requires user ID")
                } else {
                    Pair(true, "VLESS protocol supported")
                }
            }
            
            Protocol.VMESS -> {
                if (profile.userId.isNullOrBlank()) {
                    Pair(false, "VMess requires user ID")
                } else {
                    Pair(true, "VMess protocol supported")
                }
            }
            
            Protocol.TROJAN -> {
                if (profile.password.isNullOrBlank()) {
                    Pair(false, "Trojan requires password")
                } else {
                    Pair(true, "Trojan protocol supported")
                }
            }
            
            Protocol.SHADOWSOCKS -> {
                if (profile.password.isNullOrBlank()) {
                    Pair(false, "Shadowsocks requires password")
                } else if (profile.method.isNullOrBlank()) {
                    Pair(false, "Shadowsocks requires encryption method")
                } else {
                    Pair(true, "Shadowsocks protocol supported")
                }
            }
            
            Protocol.UNKNOWN -> {
                Pair(false, "Unknown protocol")
            }
        }
    }
    
    /**
     * Extract basic connection info for UI display
     */
    fun getConnectionSummary(profile: SwimVpnProfile): String {
        return buildString {
            append("${profile.protocol.name} to ${profile.address}:${profile.port}")
            
            if (profile.transport != Transport.UNKNOWN) {
                append(" via ${profile.transport.name}")
            }
            
            if (profile.securityMode != SecurityMode.NONE) {
                append(" with ${profile.securityMode.name} security")
            }
            
            profile.tlsSettings?.let {
                if (it.sni.isNotBlank() && it.sni != profile.address) {
                    append(" (SNI: ${it.sni})")
                }
            }
        }
    }
    
    // Private helper methods for Xray config generation
    
    private fun generateVlessXrayConfig(profile: SwimVpnProfile): String {
        val builder = StringBuilder()
        builder.appendLine("{")
        builder.appendLine("  \"outbounds\": [{")
        builder.appendLine("    \"protocol\": \"vless\",")
        builder.appendLine("    \"settings\": {")
        builder.appendLine("      \"vnext\": [{")
        builder.appendLine("        \"address\": \"${profile.address}\",")
        builder.appendLine("        \"port\": ${profile.port},")
        builder.appendLine("        \"users\": [{")
        builder.appendLine("          \"id\": \"${profile.userId}\",")
        builder.appendLine("          \"encryption\": \"none\",")
        builder.appendLine("          \"flow\": \"\"")
        builder.appendLine("        }]")
        builder.appendLine("      }]")
        builder.appendLine("    },")
        builder.appendLine("    \"streamSettings\": {")
        builder.appendLine("      \"network\": \"${profile.transport.name.lowercase()}\",")
        
        // Transport-specific settings
        when (profile.transport) {
            Transport.TCP -> {
                builder.appendLine("      \"tcpSettings\": {")
                profile.tcpSettings?.let { tcp ->
                    if (tcp.headerType != "none") {
                        builder.appendLine("        \"header\": {")
                        builder.appendLine("          \"type\": \"${tcp.headerType}\"")
                        tcp.host?.let { host ->
                            builder.appendLine("          \"host\": \"$host\"")
                        }
                        builder.appendLine("        }")
                    }
                }
                builder.appendLine("      }")
            }
            
            Transport.WEBSOCKET -> {
                builder.appendLine("      \"wsSettings\": {")
                profile.websocketSettings?.let { ws ->
                    builder.appendLine("        \"path\": \"${ws.path}\",")
                    ws.host?.let { host ->
                        builder.appendLine("        \"headers\": {")
                        builder.appendLine("          \"Host\": \"$host\"")
                        builder.appendLine("        }")
                    }
                }
                builder.appendLine("      }")
            }
            
            Transport.GRPC -> {
                builder.appendLine("      \"grpcSettings\": {")
                profile.grpcSettings?.let { grpc ->
                    builder.appendLine("        \"serviceName\": \"${grpc.serviceName}\",")
                    builder.appendLine("        \"multiMode\": ${grpc.mode == "multi"}")
                }
                builder.appendLine("      }")
            }
            
            else -> {
                // Other transports (HTTP2, KCP, QUIC, UNKNOWN)
            }
        }
        
        // Security settings
        builder.appendLine("      \"security\": \"${profile.securityMode.name.lowercase()}\",")
        
        when (profile.securityMode) {
            SecurityMode.TLS -> {
                builder.appendLine("      \"tlsSettings\": {")
                profile.tlsSettings?.let { tls ->
                    builder.appendLine("        \"serverName\": \"${tls.sni}\",")
                    builder.appendLine("        \"allowInsecure\": ${tls.allowInsecure}")
                    if (tls.alpn.isNotEmpty()) {
                        val alpnString = tls.alpn.joinToString(", ") { "\"$it\"" }
                        builder.appendLine("        \"alpn\": [$alpnString]")
                    }
                }
                builder.appendLine("      }")
            }
            
            SecurityMode.REALITY -> {
                builder.appendLine("      \"realitySettings\": {")
                profile.realitySettings?.let { reality ->
                    builder.appendLine("        \"publicKey\": \"${reality.publicKey}\",")
                    builder.appendLine("        \"shortId\": \"${reality.shortId}\",")
                    builder.appendLine("        \"serverName\": \"${profile.tlsSettings?.sni ?: profile.address}\",")
                    builder.appendLine("        \"spiderX\": \"${reality.spiderX ?: ""}\"")
                }
                builder.appendLine("      }")
            }
            
            else -> {
                // No security settings
            }
        }
        
        builder.appendLine("    }")
        builder.appendLine("  }]")
        builder.appendLine("}")
        
        return builder.toString()
    }
    
    private fun generateVmessXrayConfig(profile: SwimVpnProfile): String {
        val builder = StringBuilder()
        builder.appendLine("{")
        builder.appendLine("  \"outbounds\": [{")
        builder.appendLine("    \"protocol\": \"vmess\",")
        builder.appendLine("    \"settings\": {")
        builder.appendLine("      \"vnext\": [{")
        builder.appendLine("        \"address\": \"${profile.address}\",")
        builder.appendLine("        \"port\": ${profile.port},")
        builder.appendLine("        \"users\": [{")
        builder.appendLine("          \"id\": \"${profile.userId}\",")
        builder.appendLine("          \"alterId\": 0,")
        builder.appendLine("          \"security\": \"auto\"")
        builder.appendLine("        }]")
        builder.appendLine("      }]")
        builder.appendLine("    },")
        builder.appendLine("    \"streamSettings\": {")
        builder.appendLine("      \"network\": \"${profile.transport.name.lowercase()}\",")
        builder.appendLine("      \"security\": \"${profile.securityMode.name.lowercase()}\"")
        builder.appendLine("    }")
        builder.appendLine("  }]")
        builder.appendLine("}")
        
        return builder.toString()
    }
    
    private fun generateTrojanXrayConfig(profile: SwimVpnProfile): String {
        val builder = StringBuilder()
        builder.appendLine("{")
        builder.appendLine("  \"outbounds\": [{")
        builder.appendLine("    \"protocol\": \"trojan\",")
        builder.appendLine("    \"settings\": {")
        builder.appendLine("      \"servers\": [{")
        builder.appendLine("        \"address\": \"${profile.address}\",")
        builder.appendLine("        \"port\": ${profile.port},")
        builder.appendLine("        \"password\": \"${profile.password}\"")
        builder.appendLine("      }]")
        builder.appendLine("    },")
        builder.appendLine("    \"streamSettings\": {")
        builder.appendLine("      \"network\": \"${profile.transport.name.lowercase()}\",")
        builder.appendLine("      \"security\": \"${profile.securityMode.name.lowercase()}\",")
        
        profile.tlsSettings?.let { tls ->
            builder.appendLine("      \"tlsSettings\": {")
            builder.appendLine("        \"serverName\": \"${tls.sni}\",")
            builder.appendLine("        \"allowInsecure\": ${tls.allowInsecure}")
            builder.appendLine("      }")
        }
        
        builder.appendLine("    }")
        builder.appendLine("  }]")
        builder.appendLine("}")
        
        return builder.toString()
    }
    
    private fun generateShadowsocksXrayConfig(profile: SwimVpnProfile): String {
        val builder = StringBuilder()
        builder.appendLine("{")
        builder.appendLine("  \"outbounds\": [{")
        builder.appendLine("    \"protocol\": \"shadowsocks\",")
        builder.appendLine("    \"settings\": {")
        builder.appendLine("      \"servers\": [{")
        builder.appendLine("        \"address\": \"${profile.address}\",")
        builder.appendLine("        \"port\": ${profile.port},")
        builder.appendLine("        \"method\": \"${profile.method}\",")
        builder.appendLine("        \"password\": \"${profile.password}\"")
        builder.appendLine("      }]")
        builder.appendLine("    }")
        builder.appendLine("  }]")
        builder.appendLine("}")
        
        return builder.toString()
    }
}