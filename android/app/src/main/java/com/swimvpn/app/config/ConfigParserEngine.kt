package com.swimvpn.app.config

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.net.URI
import java.net.URLDecoder

/**
 * Core engine for parsing VPN configurations from various formats
 * 
 * Responsibilities:
 * - Accept configs from raw text, links, JSON payloads
 * - Parse VLESS, VMess, Trojan, Shadowsocks URLs
 * - Parse JSON Xray/V2Ray configurations
 * - Extract metadata while preserving raw input
 */
object ConfigParserEngine {
    
    private val TAG = "ConfigParserEngine"
    private val gson = Gson()
    
    /**
     * Main entry point: parse any configuration string
     */
    fun parseConfig(input: String, sourceType: SourceType = SourceType.MANUAL_ENTRY): ParseResult {
        return try {
            // Trim and normalize input
            val trimmed = input.trim()
            
            // Detect format and delegate to appropriate parser
            when {
                trimmed.startsWith("vless://") -> parseVlessUrl(trimmed, sourceType)
                trimmed.startsWith("vmess://") -> parseVmessUrl(trimmed, sourceType)
                trimmed.startsWith("trojan://") -> parseTrojanUrl(trimmed, sourceType)
                trimmed.startsWith("ss://") -> parseShadowsocksUrl(trimmed, sourceType)
                isJsonConfig(trimmed) -> parseJsonConfig(trimmed, sourceType)
                else -> ParseResult(null, listOf("Unsupported configuration format"), emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing configuration", e)
            ParseResult(null, listOf("Parsing failed: ${e.localizedMessage}"), emptyList())
        }
    }
    
    /**
     * Parse VLESS URL format: vless://uuid@host:port?params#remark
     */
    private fun parseVlessUrl(url: String, sourceType: SourceType): ParseResult {
        val warnings = mutableListOf<String>()
        
        try {
            // Parse URI components
            val uri = URI.create(url)
            
            // Extract user info (UUID)
            val userInfo = uri.userInfo
            val userId = if (userInfo != null) {
                if (userInfo.contains("?")) {
                    userInfo.substringBefore("?")
                } else {
                    userInfo
                }
            } else {
                ""
            }
            
            // Extract host and port
            val host = uri.host ?: return ParseResult(null, listOf("Missing host in VLESS URL"), warnings)
            val port = uri.port.takeIf { it != -1 } ?: 443
            
            // Parse query parameters
            val query = uri.query?.let { parseQueryParams(it) } ?: emptyMap()
            val fragment = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") }
            
            // Determine transport and security
            val (transport, security, _) = parseTransportAndSecurity(query)
            
            // Generate display names
            val displayName = fragment ?: "VLESS: ${host}"
            val displaySubtitle = buildSubtitle(transport, security, port)
            
            // Create profile
            val profile = SwimVpnProfile(
                sourceType = sourceType,
                rawConfig = url,
                sourceFormat = SourceFormat.VLESS_URL,
                protocol = Protocol.VLESS,
                transport = transport,
                securityMode = security,
                address = host,
                port = port,
                realitySettings = if (security == SecurityMode.REALITY) {
                    RealitySettings(
                        publicKey = query["pbk"] ?: "",
                        shortId = query["sid"] ?: "",
                        spiderX = query["spx"]
                    )
                } else null,
                tlsSettings = if (security == SecurityMode.TLS) {
                    TlsSettings(
                        sni = query["sni"] ?: host,
                        allowInsecure = query["allowInsecure"]?.toBoolean() ?: false,
                        alpn = query["alpn"]?.split(",") ?: emptyList(),
                        fingerprint = query["fp"]
                    )
                } else null,
                websocketSettings = if (transport == Transport.WEBSOCKET) {
                    WebsocketSettings(
                        path = query["path"] ?: "/",
                        host = query["host"] ?: host,
                        headers = query.filterKeys { it.startsWith("header:") }.mapKeys { 
                            it.key.removePrefix("header:")
                        }
                    )
                } else null,
                tcpSettings = if (transport == Transport.TCP) {
                    TcpSettings(
                        headerType = query["headerType"] ?: "none",
                        host = query["host"]
                    )
                } else null,
                grpcSettings = if (transport == Transport.GRPC) {
                    GrpcSettings(
                        serviceName = query["serviceName"] ?: "",
                        mode = query["mode"] ?: "gun",
                    )
                } else null,
                userId = userId,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                parseWarnings = warnings
            )
            
            return ParseResult(profile, emptyList(), warnings)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VLESS URL", e)
            return ParseResult(null, listOf("Invalid VLESS URL format: ${e.message}"), warnings)
        }
    }
    
    /**
     * Parse VMess URL format: vmess://base64(json)
     */
    private fun parseVmessUrl(url: String, sourceType: SourceType): ParseResult {
        val warnings = mutableListOf<String>()
        
        try {
            // Extract Base64 part
            val base64Part = url.removePrefix("vmess://")
            val decoded = String(Base64.decode(base64Part, Base64.DEFAULT))
            
            // Parse JSON
            val json = gson.fromJson(decoded, Map::class.java)
            
            val ps = json["ps"] as? String ?: ""
            val add = json["add"] as? String ?: return ParseResult(null, listOf("Missing address in VMess config"), warnings)
            val port = (json["port"] as? Number)?.toInt() ?: return ParseResult(null, listOf("Missing port in VMess config"), warnings)
            val id = json["id"] as? String ?: return ParseResult(null, listOf("Missing user ID in VMess config"), warnings)
            val net = json["net"] as? String ?: "tcp"
            val tls = json["tls"] as? String ?: "none"
            val hostHeader = (json["host"] as? String)?.takeIf { it.isNotBlank() }
            val path = (json["path"] as? String)?.takeIf { it.isNotBlank() }
            val serviceName = (json["serviceName"] as? String)?.takeIf { it.isNotBlank() }
            val sni = (json["sni"] as? String)?.takeIf { it.isNotBlank() }
                ?: (json["serverName"] as? String)?.takeIf { it.isNotBlank() }
            val fingerprint = (json["fp"] as? String)?.takeIf { it.isNotBlank() }
            val alpn = (json["alpn"] as? String)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val headerType = (json["type"] as? String)?.takeIf { it.isNotBlank() }
            
            // Map to our models
            val transport = when (net.lowercase()) {
                "tcp" -> Transport.TCP
                "ws" -> Transport.WEBSOCKET
                "grpc" -> Transport.GRPC
                "kcp" -> Transport.KCP
                "quic" -> Transport.QUIC
                else -> Transport.UNKNOWN
            }
            
            val security = when (tls.lowercase()) {
                "tls" -> SecurityMode.TLS
                "reality" -> SecurityMode.REALITY
                "xtls" -> SecurityMode.XTLS
                else -> SecurityMode.NONE
            }
            
            val displayName = ps.ifEmpty { "VMess: $add" }
            val displaySubtitle = buildSubtitle(transport, security, port)
            
            val profile = SwimVpnProfile(
                sourceType = sourceType,
                rawConfig = url,
                sourceFormat = SourceFormat.VMESS_URL,
                protocol = Protocol.VMESS,
                transport = transport,
                securityMode = security,
                address = add,
                port = port,
                realitySettings = null,
                tlsSettings = if (security == SecurityMode.TLS || security == SecurityMode.REALITY) {
                    TlsSettings(
                        sni = sni ?: hostHeader ?: add,
                        allowInsecure = false,
                        alpn = alpn,
                        fingerprint = fingerprint,
                    )
                } else null,
                websocketSettings = if (transport == Transport.WEBSOCKET) {
                    WebsocketSettings(
                        path = path ?: "/",
                        host = hostHeader,
                    )
                } else null,
                tcpSettings = if (transport == Transport.TCP && headerType != null) {
                    TcpSettings(
                        headerType = headerType,
                        host = hostHeader,
                    )
                } else null,
                grpcSettings = if (transport == Transport.GRPC) {
                    GrpcSettings(
                        serviceName = serviceName ?: "",
                        mode = "gun",
                    )
                } else null,
                userId = id,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                parseWarnings = warnings
            )
            
            return ParseResult(profile, emptyList(), warnings)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VMess URL", e)
            return ParseResult(null, listOf("Invalid VMess URL format: ${e.message}"), warnings)
        }
    }
    
    /**
     * Parse Trojan URL format: trojan://password@host:port?params#remark
     */
    private fun parseTrojanUrl(url: String, sourceType: SourceType): ParseResult {
        val warnings = mutableListOf<String>()
        
        try {
            val uri = URI.create(url)
            val userInfo = uri.userInfo ?: return ParseResult(null, listOf("Missing password in Trojan URL"), warnings)
            val host = uri.host ?: return ParseResult(null, listOf("Missing host in Trojan URL"), warnings)
            val port = uri.port.takeIf { it != -1 } ?: 443
            
            val query = uri.query?.let { parseQueryParams(it) } ?: emptyMap()
            val fragment = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") }
            
            val (transport, security, _) = parseTransportAndSecurity(query)
            
            val displayName = fragment ?: "Trojan: $host"
            val displaySubtitle = buildSubtitle(transport, security, port)
            
            val profile = SwimVpnProfile(
                sourceType = sourceType,
                rawConfig = url,
                sourceFormat = SourceFormat.TROJAN_URL,
                protocol = Protocol.TROJAN,
                transport = transport,
                securityMode = security,
                address = host,
                port = port,
                realitySettings = null,
                tlsSettings = TlsSettings(
                    sni = query["sni"] ?: host,
                    allowInsecure = query["allowInsecure"]?.toBoolean() ?: false
                ),
                websocketSettings = if (transport == Transport.WEBSOCKET) {
                    WebsocketSettings(
                        path = query["path"] ?: "/",
                        host = query["host"] ?: host
                    )
                } else null,
                tcpSettings = null,
                grpcSettings = if (transport == Transport.GRPC) {
                    GrpcSettings(
                        serviceName = query["serviceName"] ?: "",
                        mode = query["mode"] ?: "gun",
                    )
                } else null,
                password = userInfo,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                parseWarnings = warnings
            )
            
            return ParseResult(profile, emptyList(), warnings)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Trojan URL", e)
            return ParseResult(null, listOf("Invalid Trojan URL format: ${e.message}"), warnings)
        }
    }
    
    /**
     * Parse Shadowsocks URL format: ss://method:password@host:port#remark
     */
    private fun parseShadowsocksUrl(url: String, sourceType: SourceType): ParseResult {
        val warnings = mutableListOf<String>()
        
        try {
            // Handle base64 encoded format
            val raw = url.removePrefix("ss://")
            val decoded = if (raw.contains("@")) {
                raw // Plain format
            } else {
                // Base64 encoded format
                String(Base64.decode(raw, Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP))
            }
            
            val atIndex = decoded.indexOf('@')
            if (atIndex == -1) {
                return ParseResult(null, listOf("Invalid Shadowsocks format"), warnings)
            }
            
            val methodPassword = decoded.substring(0, atIndex)
            val hostPort = decoded.substring(atIndex + 1)
            
            val colonIndex = methodPassword.indexOf(':')
            if (colonIndex == -1) {
                return ParseResult(null, listOf("Invalid method:password format"), warnings)
            }
            
            val method = methodPassword.substring(0, colonIndex)
            val password = methodPassword.substring(colonIndex + 1)
            
            val hostPortParts = hostPort.split(':')
            if (hostPortParts.size != 2) {
                return ParseResult(null, listOf("Invalid host:port format"), warnings)
            }
            
            val host = hostPortParts[0]
            val port = hostPortParts[1].toIntOrNull() ?: return ParseResult(null, listOf("Invalid port"), warnings)
            
            val displayName = "Shadowsocks: $host"
            val displaySubtitle = "Port: $port, Method: $method"
            
            val profile = SwimVpnProfile(
                sourceType = sourceType,
                rawConfig = url,
                sourceFormat = SourceFormat.SHADOWSOCKS_URL,
                protocol = Protocol.SHADOWSOCKS,
                transport = Transport.TCP, // Shadowsocks typically uses TCP
                securityMode = SecurityMode.NONE,
                address = host,
                port = port,
                realitySettings = null,
                tlsSettings = null,
                websocketSettings = null,
                tcpSettings = null,
                grpcSettings = null,
                password = password,
                method = method,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                parseWarnings = warnings
            )
            
            return ParseResult(profile, emptyList(), warnings)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Shadowsocks URL", e)
            return ParseResult(null, listOf("Invalid Shadowsocks URL: ${e.message}"), warnings)
        }
    }
    
    /**
     * Parse JSON configuration (Xray/V2Ray)
     */
    private fun parseJsonConfig(jsonString: String, sourceType: SourceType): ParseResult {
        val warnings = mutableListOf<String>()
        
        return try {
            // Parse the JSON string into a map
            val json = gson.fromJson(jsonString, Map::class.java)
            
            // Check if this is an Xray/V2Ray configuration
            val outbounds = json["outbounds"] as? List<*>
            if (outbounds.isNullOrEmpty()) {
                return ParseResult(null, listOf("No outbounds found in JSON configuration"), warnings)
            }
            
            // Take the first outbound (typically the main VPN connection)
            val firstOutbound = outbounds[0] as? Map<*, *>
            if (firstOutbound == null) {
                return ParseResult(null, listOf("Invalid outbound format"), warnings)
            }
            
            val protocol = firstOutbound["protocol"] as? String ?: return ParseResult(
                null, 
                listOf("Missing protocol in outbound configuration"), 
                warnings
            )
            
            val settings = firstOutbound["settings"] as? Map<*, *>
            val streamSettings = firstOutbound["streamSettings"] as? Map<*, *>
            
            // Parse based on protocol
            when (protocol.lowercase()) {
                "vless" -> parseVlessJsonConfig(firstOutbound, settings, streamSettings, sourceType, warnings, jsonString)
                "vmess" -> parseVmessJsonConfig(firstOutbound, settings, streamSettings, sourceType, warnings, jsonString)
                "trojan" -> parseTrojanJsonConfig(firstOutbound, settings, streamSettings, sourceType, warnings, jsonString)
                "shadowsocks" -> parseShadowsocksJsonConfig(firstOutbound, settings, streamSettings, sourceType, warnings, jsonString)
                else -> ParseResult(
                    null, 
                    listOf("Unsupported protocol '$protocol' in JSON configuration"), 
                    warnings
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON configuration", e)
            ParseResult(null, listOf("JSON parsing failed: ${e.message}"), warnings)
        }
    }
    
    /**
     * Parse VLESS JSON configuration
     */
    private fun parseVlessJsonConfig(
        outbound: Map<*, *>,
        settings: Map<*, *>?,
        streamSettings: Map<*, *>?,
        sourceType: SourceType,
        warnings: MutableList<String>,
        rawJson: String
    ): ParseResult {
        try {
            // Extract vnext array
            val vnext = (settings?.get("vnext") as? List<*>)?.firstOrNull() as? Map<*, *>
            if (vnext == null) {
                return ParseResult(null, listOf("Missing vnext in VLESS configuration"), warnings)
            }
            
            val address = vnext["address"] as? String ?: return ParseResult(
                null, 
                listOf("Missing address in VLESS configuration"), 
                warnings
            )
            
            val port = (vnext["port"] as? Number)?.toInt() ?: return ParseResult(
                null, 
                listOf("Missing port in VLESS configuration"), 
                warnings
            )
            
            val users = (vnext["users"] as? List<*>)?.firstOrNull() as? Map<*, *>
            val userId = users?.get("id") as? String ?: return ParseResult(
                null, 
                listOf("Missing user ID in VLESS configuration"), 
                warnings
            )
            
            // Parse transport and security from streamSettings
            val transportStr = streamSettings?.get("network") as? String ?: "tcp"
            val securityStr = streamSettings?.get("security") as? String ?: "none"
            
            val transport = when (transportStr.lowercase()) {
                "tcp" -> Transport.TCP
                "ws", "websocket" -> Transport.WEBSOCKET
                "grpc" -> Transport.GRPC
                "http", "h2" -> Transport.HTTP2
                "kcp" -> Transport.KCP
                "quic" -> Transport.QUIC
                else -> Transport.UNKNOWN
            }
            
            val securityMode = when (securityStr.lowercase()) {
                "tls" -> SecurityMode.TLS
                "reality" -> SecurityMode.REALITY
                "xtls" -> SecurityMode.XTLS
                else -> SecurityMode.NONE
            }
            
            // Parse transport-specific settings
            var websocketSettings: WebsocketSettings? = null
            var tcpSettings: TcpSettings? = null
            var tlsSettings: TlsSettings? = null
            var realitySettings: RealitySettings? = null
            var grpcSettings: GrpcSettings? = null
            
            when (transport) {
                Transport.WEBSOCKET -> {
                    val wsSettings = streamSettings?.get("wsSettings") as? Map<*, *>
                    websocketSettings = WebsocketSettings(
                        path = wsSettings?.get("path") as? String ?: "/",
                        host = wsSettings?.get("host") as? String ?:
                               (wsSettings?.get("headers") as? Map<*, *>)?.get("Host") as? String
                    )
                }
                Transport.TCP -> {
                    val tcpSettingsMap = streamSettings?.get("tcpSettings") as? Map<*, *>
                    val header = tcpSettingsMap?.get("header") as? Map<*, *>
                    tcpSettings = TcpSettings(
                        headerType = header?.get("type") as? String ?: "none",
                        host = header?.get("host") as? String
                    )
                }
                Transport.GRPC -> {
                    val grpcSettingsMap = streamSettings?.get("grpcSettings") as? Map<*, *>
                    grpcSettings = GrpcSettings(
                        serviceName = grpcSettingsMap?.get("serviceName") as? String ?: "",
                        mode = if ((grpcSettingsMap?.get("multiMode") as? Boolean) == true) "multi" else "gun",
                    )
                }
                else -> { /* Other transports not yet fully implemented */ }
            }
            
            // Parse security settings
            when (securityMode) {
                SecurityMode.TLS -> {
                    val tlsSettingsMap = streamSettings?.get("tlsSettings") as? Map<*, *>
                    tlsSettings = TlsSettings(
                        sni = tlsSettingsMap?.get("serverName") as? String ?: address,
                        allowInsecure = tlsSettingsMap?.get("allowInsecure") as? Boolean ?: false,
                        alpn = (tlsSettingsMap?.get("alpn") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        fingerprint = tlsSettingsMap?.get("fingerprint") as? String
                    )
                }
                SecurityMode.REALITY -> {
                    val realitySettingsMap = streamSettings?.get("realitySettings") as? Map<*, *>
                    realitySettings = RealitySettings(
                        publicKey = realitySettingsMap?.get("publicKey") as? String ?: "",
                        shortId = realitySettingsMap?.get("shortId") as? String ?: "",
                        spiderX = realitySettingsMap?.get("spiderX") as? String
                    )
                }
                else -> { /* No security settings */ }
            }
            
            val displayName = outbound["tag"] as? String ?: "VLESS: $address"
            val displaySubtitle = buildSubtitle(transport, securityMode, port)
            
            val profile = SwimVpnProfile(
                sourceType = sourceType,
                rawConfig = rawJson,
                sourceFormat = SourceFormat.JSON_XRAY,
                protocol = Protocol.VLESS,
                transport = transport,
                securityMode = securityMode,
                address = address,
                port = port,
                realitySettings = realitySettings,
                tlsSettings = tlsSettings,
                websocketSettings = websocketSettings,
                tcpSettings = tcpSettings,
                grpcSettings = grpcSettings,
                userId = userId,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                parseWarnings = warnings
            )
            
            return ParseResult(profile, emptyList(), warnings)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VLESS JSON configuration", e)
            return ParseResult(null, listOf("VLESS JSON parsing failed: ${e.message}"), warnings)
        }
    }
    
    /**
     * Parse VMess JSON configuration
     */
    private fun parseVmessJsonConfig(
        outbound: Map<*, *>,
        settings: Map<*, *>?,
        streamSettings: Map<*, *>?,
        sourceType: SourceType,
        warnings: MutableList<String>,
        rawJson: String
    ): ParseResult {
        try {
            // Similar to VLESS but with VMess-specific settings
            val vnext = (settings?.get("vnext") as? List<*>)?.firstOrNull() as? Map<*, *>
            if (vnext == null) {
                return ParseResult(null, listOf("Missing vnext in VMess configuration"), warnings)
            }
            
            val address = vnext["address"] as? String ?: return ParseResult(
                null, 
                listOf("Missing address in VMess configuration"), 
                warnings
            )
            
            val port = (vnext["port"] as? Number)?.toInt() ?: return ParseResult(
                null, 
                listOf("Missing port in VMess configuration"), 
                warnings
            )
            
            val users = (vnext["users"] as? List<*>)?.firstOrNull() as? Map<*, *>
            val userId = users?.get("id") as? String ?: return ParseResult(
                null, 
                listOf("Missing user ID in VMess configuration"), 
                warnings
            )
            
            // Parse transport and security
            val transportStr = streamSettings?.get("network") as? String ?: "tcp"
            val securityStr = streamSettings?.get("security") as? String ?: "none"
            
            val transport = when (transportStr.lowercase()) {
                "tcp" -> Transport.TCP
                "ws", "websocket" -> Transport.WEBSOCKET
                "grpc" -> Transport.GRPC
                "http", "h2" -> Transport.HTTP2
                "kcp" -> Transport.KCP
                "quic" -> Transport.QUIC
                else -> Transport.UNKNOWN
            }
            
            val securityMode = when (securityStr.lowercase()) {
                "tls" -> SecurityMode.TLS
                "reality" -> SecurityMode.REALITY
                "xtls" -> SecurityMode.XTLS
                else -> SecurityMode.NONE
            }
            
            // Parse transport-specific settings (similar to VLESS)
            var websocketSettings: WebsocketSettings? = null
            var tcpSettings: TcpSettings? = null
            var tlsSettings: TlsSettings? = null
            var realitySettings: RealitySettings? = null
            var grpcSettings: GrpcSettings? = null
            
            when (transport) {
                Transport.WEBSOCKET -> {
                    val wsSettings = streamSettings?.get("wsSettings") as? Map<*, *>
                    websocketSettings = WebsocketSettings(
                        path = wsSettings?.get("path") as? String ?: "/",
                        host = wsSettings?.get("host") as? String ?:
                               (wsSettings?.get("headers") as? Map<*, *>)?.get("Host") as? String
                    )
                }
                Transport.TCP -> {
                    val tcpSettingsMap = streamSettings?.get("tcpSettings") as? Map<*, *>
                    val header = tcpSettingsMap?.get("header") as? Map<*, *>
                    tcpSettings = TcpSettings(
                        headerType = header?.get("type") as? String ?: "none",
                        host = header?.get("host") as? String
                    )
                }
                Transport.GRPC -> {
                    val grpcSettingsMap = streamSettings?.get("grpcSettings") as? Map<*, *>
                    grpcSettings = GrpcSettings(
                        serviceName = grpcSettingsMap?.get("serviceName") as? String ?: "",
                        mode = if ((grpcSettingsMap?.get("multiMode") as? Boolean) == true) "multi" else "gun",
                    )
                }
                else -> { /* Other transports */ }
            }
            
            when (securityMode) {
                SecurityMode.TLS -> {
                    val tlsSettingsMap = streamSettings?.get("tlsSettings") as? Map<*, *>
                    tlsSettings = TlsSettings(
                        sni = tlsSettingsMap?.get("serverName") as? String ?: address,
                        allowInsecure = tlsSettingsMap?.get("allowInsecure") as? Boolean ?: false,
                        alpn = (tlsSettingsMap?.get("alpn") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        fingerprint = tlsSettingsMap?.get("fingerprint") as? String
                    )
                }
                SecurityMode.REALITY -> {
                    val realitySettingsMap = streamSettings?.get("realitySettings") as? Map<*, *>
                    realitySettings = RealitySettings(
                        publicKey = realitySettingsMap?.get("publicKey") as? String ?: "",
                        shortId = realitySettingsMap?.get("shortId") as? String ?: "",
                        spiderX = realitySettingsMap?.get("spiderX") as? String
                    )
                }
                else -> { /* No security */ }
            }
            
            val displayName = outbound["tag"] as? String ?: "VMess: $address"
            val displaySubtitle = buildSubtitle(transport, securityMode, port)
            
            val profile = SwimVpnProfile(
                sourceType = sourceType,
                rawConfig = rawJson,
                sourceFormat = SourceFormat.JSON_XRAY,
                protocol = Protocol.VMESS,
                transport = transport,
                securityMode = securityMode,
                address = address,
                port = port,
                realitySettings = realitySettings,
                tlsSettings = tlsSettings,
                websocketSettings = websocketSettings,
                tcpSettings = tcpSettings,
                grpcSettings = grpcSettings,
                userId = userId,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                parseWarnings = warnings
            )
            
            return ParseResult(profile, emptyList(), warnings)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VMess JSON configuration", e)
            return ParseResult(null, listOf("VMess JSON parsing failed: ${e.message}"), warnings)
        }
    }
    
    /**
     * Parse Trojan JSON configuration
     */
    private fun parseTrojanJsonConfig(
        outbound: Map<*, *>,
        settings: Map<*, *>?,
        streamSettings: Map<*, *>?,
        sourceType: SourceType,
        warnings: MutableList<String>,
        rawJson: String
    ): ParseResult {
        try {
            val servers = (settings?.get("servers") as? List<*>)?.firstOrNull() as? Map<*, *>
            if (servers == null) {
                return ParseResult(null, listOf("Missing servers in Trojan configuration"), warnings)
            }
            
            val address = servers["address"] as? String ?: return ParseResult(
                null, 
                listOf("Missing address in Trojan configuration"), 
                warnings
            )
            
            val port = (servers["port"] as? Number)?.toInt() ?: return ParseResult(
                null, 
                listOf("Missing port in Trojan configuration"), 
                warnings
            )
            
            val password = servers["password"] as? String ?: return ParseResult(
                null, 
                listOf("Missing password in Trojan configuration"), 
                warnings
            )
            
            // Parse transport and security
            val transportStr = streamSettings?.get("network") as? String ?: "tcp"
            val securityStr = streamSettings?.get("security") as? String ?: "tls" // Trojan always uses TLS
            
            val transport = when (transportStr.lowercase()) {
                "tcp" -> Transport.TCP
                "ws", "websocket" -> Transport.WEBSOCKET
                "grpc" -> Transport.GRPC
                "http", "h2" -> Transport.HTTP2
                else -> Transport.UNKNOWN
            }
            
            // Trojan always uses TLS
            val securityMode = SecurityMode.TLS
            
            // Parse TLS settings
            var grpcSettings: GrpcSettings? = null
            var tlsSettings: TlsSettings? = null
            if (securityMode == SecurityMode.TLS) {
                val tlsSettingsMap = streamSettings?.get("tlsSettings") as? Map<*, *>
                tlsSettings = TlsSettings(
                    sni = tlsSettingsMap?.get("serverName") as? String ?: address,
                    allowInsecure = tlsSettingsMap?.get("allowInsecure") as? Boolean ?: false,
                    alpn = (tlsSettingsMap?.get("alpn") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    fingerprint = tlsSettingsMap?.get("fingerprint") as? String
                )
            }
            if (transport == Transport.GRPC) {
                val grpcSettingsMap = streamSettings?.get("grpcSettings") as? Map<*, *>
                grpcSettings = GrpcSettings(
                    serviceName = grpcSettingsMap?.get("serviceName") as? String ?: "",
                    mode = if ((grpcSettingsMap?.get("multiMode") as? Boolean) == true) "multi" else "gun",
                )
            }

            val displayName = outbound["tag"] as? String ?: "Trojan: $address"
            val displaySubtitle = buildSubtitle(transport, securityMode, port)
            
            val profile = SwimVpnProfile(
                sourceType = sourceType,
                rawConfig = rawJson,
                sourceFormat = SourceFormat.JSON_XRAY,
                protocol = Protocol.TROJAN,
                transport = transport,
                securityMode = securityMode,
                address = address,
                port = port,
                tlsSettings = tlsSettings,
                grpcSettings = grpcSettings,
                password = password,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                parseWarnings = warnings
            )
            
            return ParseResult(profile, emptyList(), warnings)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Trojan JSON configuration", e)
            return ParseResult(null, listOf("Trojan JSON parsing failed: ${e.message}"), warnings)
        }
    }
    
    /**
     * Parse Shadowsocks JSON configuration
     */
    private fun parseShadowsocksJsonConfig(
        outbound: Map<*, *>,
        settings: Map<*, *>?,
        streamSettings: Map<*, *>?,
        sourceType: SourceType,
        warnings: MutableList<String>,
        rawJson: String
    ): ParseResult {
        try {
            val servers = (settings?.get("servers") as? List<*>)?.firstOrNull() as? Map<*, *>
            if (servers == null) {
                return ParseResult(null, listOf("Missing servers in Shadowsocks configuration"), warnings)
            }
            
            val address = servers["address"] as? String ?: return ParseResult(
                null, 
                listOf("Missing address in Shadowsocks configuration"), 
                warnings
            )
            
            val port = (servers["port"] as? Number)?.toInt() ?: return ParseResult(
                null, 
                listOf("Missing port in Shadowsocks configuration"), 
                warnings
            )
            
            val password = servers["password"] as? String ?: return ParseResult(
                null, 
                listOf("Missing password in Shadowsocks configuration"), 
                warnings
            )
            
            val method = servers["method"] as? String ?: "aes-256-gcm"
            
            val displayName = outbound["tag"] as? String ?: "Shadowsocks: $address"
            val displaySubtitle = "Port: $port, Method: $method"
            
            val profile = SwimVpnProfile(
                sourceType = sourceType,
                rawConfig = rawJson,
                sourceFormat = SourceFormat.JSON_XRAY,
                protocol = Protocol.SHADOWSOCKS,
                transport = Transport.TCP, // Shadowsocks typically uses TCP
                securityMode = SecurityMode.NONE,
                address = address,
                port = port,
                password = password,
                method = method,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                parseWarnings = warnings
            )
            
            return ParseResult(profile, emptyList(), warnings)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Shadowsocks JSON configuration", e)
            return ParseResult(null, listOf("Shadowsocks JSON parsing failed: ${e.message}"), warnings)
        }
    }
    
    /**
     * Helper: Parse query parameters from URL
     */
    private fun parseQueryParams(query: String): Map<String, String> {
        return query.split('&').associate { param ->
            val parts = param.split('=', limit = 2)
            val key = parts[0]
            val value = if (parts.size == 2) URLDecoder.decode(parts[1], "UTF-8") else ""
            key to value
        }
    }
    
    /**
     * Helper: Parse transport and security from query parameters
     */
    private fun parseTransportAndSecurity(query: Map<String, String>): Triple<Transport, SecurityMode, Map<String, String>> {
        val type = query["type"] ?: "tcp"
        val security = query["security"] ?: "none"
        
        val transport = when (type.lowercase()) {
            "tcp" -> Transport.TCP
            "ws", "websocket" -> Transport.WEBSOCKET
            "grpc" -> Transport.GRPC
            "http", "h2" -> Transport.HTTP2
            "kcp" -> Transport.KCP
            "quic" -> Transport.QUIC
            else -> Transport.UNKNOWN
        }
        
        val securityMode = when (security.lowercase()) {
            "tls" -> SecurityMode.TLS
            "reality" -> SecurityMode.REALITY
            "xtls" -> SecurityMode.XTLS
            else -> SecurityMode.NONE
        }
        
        return Triple(transport, securityMode, query)
    }
    
    /**
     * Helper: Build subtitle from transport and security
     */
    private fun buildSubtitle(transport: Transport, security: SecurityMode, port: Int): String {
        val parts = mutableListOf<String>()
        if (transport != Transport.UNKNOWN) parts.add(transport.name)
        if (security != SecurityMode.NONE) parts.add(security.name)
        parts.add("Port: $port")
        return parts.joinToString(", ")
    }
    
    /**
     * Helper: Check if string is JSON configuration
     */
    private fun isJsonConfig(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.startsWith('{') && trimmed.endsWith('}') ||
               trimmed.startsWith('[') && trimmed.endsWith(']')
    }
}
