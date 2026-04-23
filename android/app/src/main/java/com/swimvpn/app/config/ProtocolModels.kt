package com.swimvpn.app.config

import java.util.UUID

/**
 * Protocol-specific data models for VPN configuration parsing
 */

/**
 * Canonical VPN profile model - the normalized representation of any VPN config
 */
data class SwimVpnProfile(
    // Unique identifier for storage
    val id: String = UUID.randomUUID().toString(),
    
    // Source and Metadata
    val sourceType: SourceType,
    val rawConfig: String,                    // Original config preserved intact
    val sourceFormat: SourceFormat,
    val sourceBundleId: String? = null,
    val sourceBundleName: String? = null,
    val sourceBundleOrder: Int = 0,
    
    // Protocol Classification
    val protocol: Protocol,
    val transport: Transport,
    val securityMode: SecurityMode,
    
    // Connection Details
    val address: String,
    val port: Int,
    
    // Transport-specific settings
    val realitySettings: RealitySettings? = null,
    val tlsSettings: TlsSettings? = null,
    val websocketSettings: WebsocketSettings? = null,
    val tcpSettings: TcpSettings? = null,
    val grpcSettings: GrpcSettings? = null,
    
    // Authentication
    val userId: String? = null,               // VLESS/VMess user ID
    val password: String? = null,             // Trojan/Shadowsocks password
    val method: String? = null,               // Shadowsocks method
    
    // Display
    val displayName: String,
    val displaySubtitle: String,
    
    // Validation
    val parseWarnings: List<String> = emptyList(),
    val parseErrors: List<String> = emptyList(),
    
    // Runtime
    val normalizedRuntimeConfig: String? = null,
    
    // Metadata
    val importedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)

/**
 * Source types for configuration
 */
enum class SourceType {
    CLIPBOARD,
    FILE_IMPORT,
    QR_CODE,
    MANUAL_ENTRY,
    BACKEND_API,
    SUBSCRIPTION_URL
}

/**
 * Original format of the configuration
 */
enum class SourceFormat {
    VLESS_URL,
    VMESS_URL,
    TROJAN_URL,
    SHADOWSOCKS_URL,
    JSON_XRAY,
    JSON_V2RAY,
    RAW_SUBSCRIPTION,
    UNKNOWN
}

/**
 * VPN protocols
 */
enum class Protocol {
    VLESS,
    VMESS,
    TROJAN,
    SHADOWSOCKS,
    UNKNOWN
}

/**
 * Transport layers
 */
enum class Transport {
    TCP,
    WEBSOCKET,
    GRPC,
    HTTP2,
    KCP,
    QUIC,
    UNKNOWN
}

/**
 * Security modes
 */
enum class SecurityMode {
    NONE,
    TLS,
    REALITY,
    XTLS,
    UNKNOWN
}

/**
 * Transport-specific settings
 */
data class RealitySettings(
    val publicKey: String,
    val shortId: String,
    val spiderX: String? = null
)

data class TlsSettings(
    val sni: String,
    val allowInsecure: Boolean = false,
    val alpn: List<String> = emptyList(),
    val fingerprint: String? = null
)

data class WebsocketSettings(
    val path: String,
    val host: String? = null,
    val headers: Map<String, String> = emptyMap()
)

data class TcpSettings(
    val headerType: String = "none",
    val host: String? = null
)

data class GrpcSettings(
    val serviceName: String,
    val mode: String = "gun"
)

/**
 * Parsing result container
 */
data class ParseResult(
    val profile: SwimVpnProfile?,
    val errors: List<String>,
    val warnings: List<String>
) {
    val isValid: Boolean get() = profile != null && errors.isEmpty()
}

/**
 * Configuration preview for UI display
 */
data class ConfigPreview(
    val protocol: String,
    val address: String,
    val port: Int,
    val transport: String,
    val security: String,
    val displayName: String,
    val validationStatus: ValidationStatus,
    val warnings: List<String>,
    val summary: String
)

enum class ValidationStatus {
    VALID,
    WARNING,
    ERROR,
    UNKNOWN
}

data class ImportedProfileGroup(
    val id: String,
    val name: String,
    val profiles: List<SwimVpnProfile>,
)
