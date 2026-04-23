package com.swimvpn.app.config

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
                isRecognizedUnsupportedModernScheme(trimmed) -> parseUnsupportedModernConfig(trimmed)
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
            val parsed = parseStructuredUrl(url, "vless://")
            val userId = parsed.userInfo
            val host = parsed.host ?: return ParseResult(null, listOf("Missing host in VLESS URL"), warnings)
            val port = parsed.port ?: 443
            val query = parsed.query?.let { parseQueryParams(it) } ?: emptyMap()
            val fragment = parsed.fragment
            val flow = query["flow"]?.takeIf { it.isNotBlank() }
            
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
                tlsSettings = if (security == SecurityMode.TLS || security == SecurityMode.REALITY) {
                    TlsSettings(
                        sni = query["sni"] ?: host,
                        allowInsecure = parseBooleanFlag(query, "allowInsecure", "insecure", "tlsInsecure", "skip-cert-verify"),
                        alpn = parseCsv(query["alpn"]),
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
                } else if (transport == Transport.HTTP2) {
                    WebsocketSettings(
                        path = query["path"] ?: "/",
                        host = query["host"] ?: query["authority"] ?: query["sni"] ?: host,
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
                flow = flow,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                advancedSettings = extractAdvancedSettings(query),
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
            val decoded = decodeBase64Flexible(base64Part)
            
            // Parse JSON
            val json = gson.fromJson(decoded, Map::class.java)
            
            val ps = json["ps"] as? String ?: ""
            val add = json["add"] as? String ?: return ParseResult(null, listOf("Missing address in VMess config"), warnings)
            val port = (json["port"] as? Number)?.toInt() ?: return ParseResult(null, listOf("Missing port in VMess config"), warnings)
            val id = json["id"] as? String ?: return ParseResult(null, listOf("Missing user ID in VMess config"), warnings)
            val net = json["net"] as? String ?: "tcp"
            val tls = json["tls"] as? String ?: "none"
            val hostHeader = extractHostHeader(json["host"])
            val path = (json["path"] as? String)?.takeIf { it.isNotBlank() }
            val serviceName = (json["serviceName"] as? String)?.takeIf { it.isNotBlank() }
                ?: path?.takeIf { net.equals("grpc", ignoreCase = true) }
            val sni = (json["sni"] as? String)?.takeIf { it.isNotBlank() }
                ?: (json["serverName"] as? String)?.takeIf { it.isNotBlank() }
            val fingerprint = (json["fp"] as? String)?.takeIf { it.isNotBlank() }
            val alpn = extractAlpn(json["alpn"])
            val flow = (json["flow"] as? String)?.takeIf { it.isNotBlank() }
            val headerType = (json["headerType"] as? String)?.takeIf { it.isNotBlank() }
                ?: (json["type"] as? String)?.takeIf { it.isNotBlank() && it.lowercase() !in setOf("tcp", "ws", "grpc", "kcp", "quic", "h2", "http") }
            
            // Map to our models
            val transport = when (net.lowercase()) {
                "tcp" -> Transport.TCP
                "ws" -> Transport.WEBSOCKET
                "grpc" -> Transport.GRPC
                "http", "h2", "httpupgrade", "xhttp", "splithttp" -> Transport.HTTP2
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
                realitySettings = if (security == SecurityMode.REALITY) {
                    RealitySettings(
                        publicKey = (json["pbk"] as? String) ?: "",
                        shortId = (json["sid"] as? String) ?: "",
                        spiderX = json["spx"] as? String,
                    )
                } else null,
                tlsSettings = if (security == SecurityMode.TLS || security == SecurityMode.REALITY) {
                    TlsSettings(
                        sni = sni ?: hostHeader ?: add,
                        allowInsecure = parseBooleanFlag(json, "allowInsecure", "insecure", "tlsInsecure", "skip-cert-verify"),
                        alpn = alpn,
                        fingerprint = fingerprint,
                    )
                } else null,
                websocketSettings = if (transport == Transport.WEBSOCKET) {
                    WebsocketSettings(
                        path = path ?: "/",
                        host = hostHeader,
                    )
                } else if (transport == Transport.HTTP2) {
                    WebsocketSettings(
                        path = path ?: "/",
                        host = hostHeader ?: sni ?: add,
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
                flow = flow,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                advancedSettings = extractAdvancedSettings(json),
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
            val parsed = parseStructuredUrl(url, "trojan://")
            val userInfo = parsed.userInfo ?: return ParseResult(null, listOf("Missing password in Trojan URL"), warnings)
            val host = parsed.host ?: return ParseResult(null, listOf("Missing host in Trojan URL"), warnings)
            val port = parsed.port ?: 443
            val query = parsed.query?.let { parseQueryParams(it) } ?: emptyMap()
            val fragment = parsed.fragment
            
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
                realitySettings = if (security == SecurityMode.REALITY) {
                    RealitySettings(
                        publicKey = query["pbk"] ?: "",
                        shortId = query["sid"] ?: "",
                        spiderX = query["spx"],
                    )
                } else null,
                tlsSettings = if (security == SecurityMode.TLS || security == SecurityMode.REALITY) {
                    TlsSettings(
                        sni = query["sni"] ?: host,
                        allowInsecure = parseBooleanFlag(query, "allowInsecure", "insecure", "tlsInsecure", "skip-cert-verify"),
                        alpn = parseCsv(query["alpn"]),
                        fingerprint = query["fp"],
                    )
                } else null,
                websocketSettings = if (transport == Transport.WEBSOCKET) {
                    WebsocketSettings(
                        path = query["path"] ?: "/",
                        host = query["host"] ?: host
                    )
                } else if (transport == Transport.HTTP2) {
                    WebsocketSettings(
                        path = query["path"] ?: "/",
                        host = query["host"] ?: query["authority"] ?: query["sni"] ?: host,
                    )
                } else null,
                tcpSettings = if (transport == Transport.TCP) {
                    TcpSettings(
                        headerType = query["headerType"] ?: "none",
                        host = query["host"],
                    )
                } else null,
                grpcSettings = if (transport == Transport.GRPC) {
                    GrpcSettings(
                        serviceName = query["serviceName"] ?: "",
                        mode = query["mode"] ?: "gun",
                    )
                } else null,
                password = userInfo,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                advancedSettings = extractAdvancedSettings(query),
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
            val raw = url.removePrefix("ss://")
            val fragment = raw.substringAfter('#', "")
                .takeIf { it.isNotBlank() }
                ?.let { URLDecoder.decode(it, "UTF-8") }
            val withoutFragment = raw.substringBefore('#')
            val query = withoutFragment.substringAfter('?', "")
                .takeIf { it.isNotBlank() }
                ?.let(::parseQueryParams)
                ?: emptyMap()
            val withoutQuery = withoutFragment.substringBefore('?')

            val decodedCore = if (withoutQuery.contains("@")) {
                withoutQuery
            } else {
                decodeBase64Flexible(withoutQuery)
            }

            val atIndex = decodedCore.lastIndexOf('@')
            if (atIndex == -1) {
                return ParseResult(null, listOf("Invalid Shadowsocks format"), warnings)
            }

            val methodPassword = decodedCore.substring(0, atIndex)
            val hostPort = decodedCore.substring(atIndex + 1)

            val colonIndex = methodPassword.indexOf(':')
            if (colonIndex == -1) {
                return ParseResult(null, listOf("Invalid method:password format"), warnings)
            }

            val method = methodPassword.substring(0, colonIndex)
            val password = methodPassword.substring(colonIndex + 1)
            val (host, port) = parseHostAndPort(hostPort)
                ?: return ParseResult(null, listOf("Invalid host:port format"), warnings)

            val displayName = fragment ?: "Shadowsocks: $host"
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
                shadowsocksPluginSettings = parseShadowsocksPluginSettings(query, warnings),
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
            val root = JsonParser.parseString(jsonString)
            val firstOutbound = extractPrimaryOutbound(root)
                ?: return ParseResult(null, listOf("No supported outbound found in JSON configuration"), warnings)

            val outbound = gson.fromJson(firstOutbound, Map::class.java) as? Map<*, *>
                ?: return ParseResult(null, listOf("Invalid outbound format"), warnings)

            val protocol = outbound["protocol"] as? String ?: return ParseResult(
                null,
                listOf("Missing protocol in outbound configuration"),
                warnings
            )

            val settings = outbound["settings"] as? Map<*, *>
            val streamSettings = outbound["streamSettings"] as? Map<*, *>
            
            // Parse based on protocol
            when (protocol.lowercase()) {
                "vless" -> parseVlessJsonConfig(outbound, settings, streamSettings, sourceType, warnings, jsonString)
                "vmess" -> parseVmessJsonConfig(outbound, settings, streamSettings, sourceType, warnings, jsonString)
                "trojan" -> parseTrojanJsonConfig(outbound, settings, streamSettings, sourceType, warnings, jsonString)
                "shadowsocks" -> parseShadowsocksJsonConfig(outbound, settings, streamSettings, sourceType, warnings, jsonString)
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
            val flow = extractString(users["flow"]) ?: extractString(outbound["flow"]) ?: extractString(settings?.get("flow"))
            
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
                Transport.HTTP2 -> {
                    val httpSettingsMap = streamSettings?.get("httpSettings") as? Map<*, *>
                    websocketSettings = WebsocketSettings(
                        path = httpSettingsMap?.get("path") as? String ?: "/",
                        host = extractHostHeader(httpSettingsMap?.get("host"))
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
                    val serviceName = grpcSettingsMap?.get("serviceName") as? String
                    val fallbackPath = streamSettings?.get("wsSettings")
                        ?.let { it as? Map<*, *> }
                        ?.get("path") as? String
                    grpcSettings = GrpcSettings(
                        serviceName = serviceName?.takeIf { it.isNotBlank() }
                            ?: fallbackPath?.takeIf { it.isNotBlank() }
                            ?: "",
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
                        allowInsecure = parseBooleanFlag(tlsSettingsMap, "allowInsecure", "insecure", "tlsInsecure", "skip-cert-verify"),
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
                flow = flow,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                advancedSettings = extractAdvancedSettings(streamSettings),
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
                Transport.HTTP2 -> {
                    val httpSettingsMap = streamSettings?.get("httpSettings") as? Map<*, *>
                    websocketSettings = WebsocketSettings(
                        path = httpSettingsMap?.get("path") as? String ?: "/",
                        host = extractHostHeader(httpSettingsMap?.get("host"))
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
                    val serviceName = grpcSettingsMap?.get("serviceName") as? String
                    val fallbackPath = streamSettings?.get("httpSettings")
                        ?.let { it as? Map<*, *> }
                        ?.get("path") as? String
                    grpcSettings = GrpcSettings(
                        serviceName = serviceName?.takeIf { it.isNotBlank() }
                            ?: fallbackPath?.takeIf { it.isNotBlank() }
                            ?: "",
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
                        allowInsecure = parseBooleanFlag(tlsSettingsMap, "allowInsecure", "insecure", "tlsInsecure", "skip-cert-verify"),
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
                advancedSettings = extractAdvancedSettings(streamSettings),
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
                "http", "h2", "httpupgrade", "xhttp", "splithttp" -> Transport.HTTP2
                else -> Transport.UNKNOWN
            }
            
            // Trojan always uses TLS
            val securityMode = when (securityStr.lowercase()) {
                "reality" -> SecurityMode.REALITY
                "xtls" -> SecurityMode.XTLS
                else -> SecurityMode.TLS
            }
            
            // Parse TLS settings
            var grpcSettings: GrpcSettings? = null
            var websocketSettings: WebsocketSettings? = null
            var tlsSettings: TlsSettings? = null
            if (securityMode == SecurityMode.TLS || securityMode == SecurityMode.REALITY) {
                val tlsSettingsMap = streamSettings?.get("tlsSettings") as? Map<*, *>
                tlsSettings = TlsSettings(
                    sni = tlsSettingsMap?.get("serverName") as? String ?: address,
                    allowInsecure = parseBooleanFlag(tlsSettingsMap, "allowInsecure", "insecure", "tlsInsecure", "skip-cert-verify"),
                    alpn = (tlsSettingsMap?.get("alpn") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    fingerprint = tlsSettingsMap?.get("fingerprint") as? String
                )
            }
            val realitySettings = if (securityMode == SecurityMode.REALITY) {
                val realitySettingsMap = streamSettings?.get("realitySettings") as? Map<*, *>
                RealitySettings(
                    publicKey = realitySettingsMap?.get("publicKey") as? String ?: "",
                    shortId = realitySettingsMap?.get("shortId") as? String ?: "",
                    spiderX = realitySettingsMap?.get("spiderX") as? String,
                )
            } else {
                null
            }
            if (transport == Transport.GRPC) {
                val grpcSettingsMap = streamSettings?.get("grpcSettings") as? Map<*, *>
                val httpSettingsMap = streamSettings?.get("httpSettings") as? Map<*, *>
                grpcSettings = GrpcSettings(
                    serviceName = (grpcSettingsMap?.get("serviceName") as? String)?.takeIf { it.isNotBlank() }
                        ?: (httpSettingsMap?.get("path") as? String)?.takeIf { it.isNotBlank() }
                        ?: "",
                    mode = if ((grpcSettingsMap?.get("multiMode") as? Boolean) == true) "multi" else "gun",
                )
            }
            if (transport == Transport.HTTP2) {
                val httpSettingsMap = streamSettings?.get("httpSettings") as? Map<*, *>
                websocketSettings = WebsocketSettings(
                    path = httpSettingsMap?.get("path") as? String ?: "/",
                    host = extractHostHeader(httpSettingsMap?.get("host"))
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
                realitySettings = realitySettings,
                tlsSettings = tlsSettings,
                websocketSettings = websocketSettings,
                grpcSettings = grpcSettings,
                password = password,
                displayName = displayName,
                displaySubtitle = displaySubtitle,
                advancedSettings = extractAdvancedSettings(streamSettings),
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
            val plugin = servers["plugin"] as? String
            val pluginOptions = servers["plugin_opts"] as? String
            
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
                shadowsocksPluginSettings = parseShadowsocksPluginSettings(
                    query = buildMap {
                        plugin?.takeIf { it.isNotBlank() }?.let { put("plugin", it) }
                        pluginOptions?.takeIf { it.isNotBlank() }?.let { put("plugin-opts", it) }
                    },
                    warnings = warnings
                ),
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

    private fun parseShadowsocksPluginSettings(
        query: Map<String, String>,
        warnings: MutableList<String>,
    ): ShadowsocksPluginSettings? {
        val plugin = query["plugin"]?.takeIf { it.isNotBlank() } ?: return null
        val options = query["plugin-opts"]?.takeIf { it.isNotBlank() }

        warnings += "Shadowsocks plugin metadata preserved, but runtime support may still be incomplete"
        return ShadowsocksPluginSettings(
            plugin = plugin,
            options = options,
        )
    }
    
    /**
     * Helper: Parse transport and security from query parameters
     */
    private fun parseTransportAndSecurity(query: Map<String, String>): Triple<Transport, SecurityMode, Map<String, String>> {
        val type = query["type"] ?: "tcp"
        val security = query["security"] ?: "none"
        
        val transport = when (type.lowercase()) {
            "tcp", "raw" -> Transport.TCP
            "ws", "websocket" -> Transport.WEBSOCKET
            "grpc" -> Transport.GRPC
            "http", "h2", "httpupgrade", "xhttp", "splithttp" -> Transport.HTTP2
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

    private data class ParsedStructuredUrl(
        val userInfo: String?,
        val host: String?,
        val port: Int?,
        val query: String?,
        val fragment: String?,
    )

    private fun parseStructuredUrl(rawUrl: String, prefix: String): ParsedStructuredUrl {
        val withoutScheme = rawUrl.removePrefix(prefix)
        val fragment = withoutScheme.substringAfter('#', "")
            .takeIf { it.isNotBlank() }
            ?.let { URLDecoder.decode(it, "UTF-8") }
        val withoutFragment = withoutScheme.substringBefore('#')
        val query = withoutFragment.substringAfter('?', "")
            .takeIf { it.isNotBlank() }
        val authority = withoutFragment.substringBefore('?')

        val atIndex = authority.lastIndexOf('@')
        val userInfo = authority.substringBeforeLast('@', "")
            .takeIf { atIndex != -1 }
            ?.takeIf { it.isNotBlank() }
        val hostPortPart = if (atIndex != -1) authority.substring(atIndex + 1) else authority
        val (host, port) = parseHostAndPortWithDefault(hostPortPart)

        return ParsedStructuredUrl(
            userInfo = userInfo,
            host = host,
            port = port,
            query = query,
            fragment = fragment,
        )
    }

    private fun decodeBase64Flexible(input: String): String {
        val normalized = input.trim()
            .replace('-', '+')
            .replace('_', '/')
            .let { value ->
                val padding = (4 - value.length % 4) % 4
                value + "=".repeat(padding)
            }

        val strategies = listOf(
            Base64.DEFAULT,
            Base64.NO_WRAP,
            Base64.NO_PADDING or Base64.NO_WRAP,
            Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE,
        )

        for (flags in strategies) {
            try {
                return String(Base64.decode(normalized, flags))
            } catch (_: IllegalArgumentException) {
                // try next strategy
            }
        }

        throw IllegalArgumentException("Invalid Base64 payload")
    }

    private fun parseHostAndPort(input: String): Pair<String, Int>? {
        val trimmed = input.trim()
        if (trimmed.startsWith("[")) {
            val endBracket = trimmed.indexOf(']')
            if (endBracket == -1) return null
            val host = trimmed.substring(1, endBracket)
            val portPart = trimmed.substring(endBracket + 1).removePrefix(":")
            val port = portPart.toIntOrNull() ?: return null
            return host to port
        }

        val colonIndex = trimmed.lastIndexOf(':')
        if (colonIndex == -1) return null
        val host = trimmed.substring(0, colonIndex)
        val port = trimmed.substring(colonIndex + 1).toIntOrNull() ?: return null
        return host to port
    }

    private fun parseHostAndPortWithDefault(input: String): Pair<String?, Int?> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return null to null
        }

        if (trimmed.startsWith("[")) {
            val endBracket = trimmed.indexOf(']')
            if (endBracket == -1) {
                return trimmed.removePrefix("[").ifBlank { null } to null
            }
            val host = trimmed.substring(1, endBracket).ifBlank { null }
            val remainder = trimmed.substring(endBracket + 1)
            val port = remainder.removePrefix(":").takeIf { it.isNotBlank() }?.toIntOrNull()
            return host to port
        }

        val colonIndex = trimmed.lastIndexOf(':')
        if (colonIndex == -1) {
            return trimmed to null
        }

        val host = trimmed.substring(0, colonIndex).ifBlank { null }
        val portPart = trimmed.substring(colonIndex + 1)
        val port = portPart.toIntOrNull()

        return if (port != null) {
            host to port
        } else {
            trimmed to null
        }
    }

    private fun parseCsv(input: String?): List<String> {
        return input
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    private fun parseBooleanFlag(values: Map<*, *>?, vararg keys: String): Boolean {
        if (values == null) return false
        for (key in keys) {
            val value = values[key] ?: values.entries.firstOrNull {
                it.key?.toString()?.equals(key, ignoreCase = true) == true
            }?.value ?: continue
            return when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.equals("true", ignoreCase = true) ||
                    value == "1" ||
                    value.equals("yes", ignoreCase = true) ||
                    value.equals("y", ignoreCase = true)
                else -> false
            }
        }
        return false
    }

    private fun extractString(value: Any?): String? {
        return when (value) {
            is String -> value.takeIf { it.isNotBlank() }
            is Number -> value.toString()
            is Boolean -> value.toString()
            else -> null
        }
    }

    private fun extractAdvancedSettings(values: Map<*, *>?): Map<String, String> {
        if (values == null) return emptyMap()
        val keys = setOf(
            "fragment",
            "noises",
            "noise",
            "serverDescription",
            "packetEncoding",
            "mux",
            "smux",
            "mport",
            "mportHopInt",
            "port",
            "obfs",
            "obfs-password",
            "auth",
        )
        return values.entries.mapNotNull { (rawKey, rawValue) ->
            val key = rawKey?.toString() ?: return@mapNotNull null
            val matched = keys.firstOrNull { it.equals(key, ignoreCase = true) } ?: return@mapNotNull null
            val value = when (rawValue) {
                is String -> rawValue
                is Number, is Boolean -> rawValue.toString()
                is List<*> -> rawValue.joinToString(",") { it.toString() }
                is Map<*, *> -> gson.toJson(rawValue)
                else -> rawValue?.toString()
            }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            matched to value
        }.toMap()
    }

    private fun parseUnsupportedModernConfig(input: String): ParseResult {
        val warnings = mutableListOf<String>()
        val label = modernSchemeLabel(input)
        val parsedQuery = runCatching {
            val query = input.substringAfter('?', "").substringBefore('#').takeIf { it.isNotBlank() }
            query?.let { parseQueryParams(it) } ?: emptyMap()
        }.getOrDefault(emptyMap())
        val metadata = extractAdvancedSettings(parsedQuery)
        if (metadata.isNotEmpty()) {
            warnings += "$label metadata preserved for diagnostics only: ${metadata.keys.sorted().joinToString(", ")}"
        }
        return ParseResult(
            null,
            listOf("$label configuration is recognized but not supported by the current runtime yet"),
            warnings,
        )
    }

    private fun isRecognizedUnsupportedModernScheme(input: String): Boolean {
        return unsupportedModernScheme(input) != null
    }

    private fun modernSchemeLabel(input: String): String {
        return when (unsupportedModernScheme(input)) {
            "hy2", "hysteria2", "hysteria" -> "Hysteria2"
            "tuic" -> "TUIC"
            "socks", "socks5" -> "SOCKS"
            "wg", "wireguard" -> "WireGuard"
            else -> "Modern VPN"
        }
    }

    private fun unsupportedModernScheme(input: String): String? {
        val scheme = input.substringBefore("://", "").lowercase()
        return scheme.takeIf {
            it in setOf("hy2", "hysteria2", "hysteria", "tuic", "socks", "socks5", "wg", "wireguard")
        }
    }

    private fun extractHostHeader(value: Any?): String? {
        return when (value) {
            is String -> value.takeIf { it.isNotBlank() }
            is List<*> -> value.filterIsInstance<String>().firstOrNull { it.isNotBlank() }
            else -> null
        }
    }

    private fun extractAlpn(value: Any?): List<String> {
        return when (value) {
            is String -> parseCsv(value)
            is List<*> -> value.filterIsInstance<String>().map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun extractPrimaryOutbound(root: JsonElement): JsonObject? {
        return when {
            root.isJsonObject -> extractPrimaryOutboundFromObject(root.asJsonObject)
            root.isJsonArray -> extractPrimaryOutboundFromArray(root.asJsonArray)
            else -> null
        }
    }

    private fun extractPrimaryOutboundFromObject(root: JsonObject): JsonObject? {
        if (root.has("protocol") && root.has("settings")) {
            return root
        }

        val outbounds = root.getAsJsonArray("outbounds") ?: return null
        return outbounds
            .mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
            .firstOrNull { outbound ->
                val protocol = outbound.get("protocol")?.asString?.lowercase()
                protocol in setOf("vless", "vmess", "trojan", "shadowsocks")
            }
    }

    private fun extractPrimaryOutboundFromArray(array: JsonArray): JsonObject? {
        return array
            .mapNotNull { element ->
                when {
                    element.isJsonObject -> extractPrimaryOutboundFromObject(element.asJsonObject)
                    else -> null
                }
            }
            .firstOrNull()
    }
}
