package com.swimvpn.app.config.subscriptionparser

import com.google.gson.JsonParser
import com.swimvpn.app.config.ConfigParserEngine
import com.swimvpn.app.config.Protocol
import com.swimvpn.app.config.SecurityMode
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.SwimVpnProfile
import com.swimvpn.app.config.Transport

object SubscriptionParser {
    fun parse(
        input: String,
        sourceType: SourceType = SourceType.MANUAL_ENTRY,
        sourceUrl: String? = null,
        headerMetadata: SubscriptionHeaderMetadata? = null,
    ): ParsedSubscription {
        val decoded = SubscriptionPayloadDecoder.decode(input)
        val metadata = SubscriptionMetadataParser.parse(decoded.payload, sourceUrl, headerMetadata)
        val warnings = (decoded.warnings + metadata.warnings).toMutableList()
        val entries = SubscriptionPayloadDecoder.extractEntries(decoded.payload)

        if (entries.isEmpty() && sourceUrl != null) {
            warnings += "Subscription URL requires remote fetch before profile parsing"
        }

        val profiles = entries.mapNotNull { entry ->
            parseEntry(entry, sourceType, warnings, metadata)
        }

        return ParsedSubscription(
            sourceUrl = sourceUrl,
            providerName = metadata.providerName,
            profiles = profiles,
            trafficUsedBytes = metadata.trafficUsedBytes,
            trafficTotalBytes = metadata.trafficTotalBytes,
            expiresAt = metadata.expiresAt,
            autoUpdateIntervalHours = metadata.autoUpdateIntervalHours,
            warnings = warnings.distinct(),
            raw = decoded.payload,
        )
    }

    fun parseUnknownProviderSubscription(raw: String): ParsedSubscription {
        val parsed = parse(raw, sourceType = SourceType.SUBSCRIPTION_URL)
        if (parsed.profiles.isNotEmpty()) {
            return parsed
        }

        val detectedScheme = raw.trim()
            .substringBefore("://", "")
            .takeIf { it.isNotBlank() && it.length <= 24 }

        return parsed.copy(
            warnings = (
                parsed.warnings +
                    "Unsupported subscription format" +
                    listOfNotNull(detectedScheme?.let { "Detected unsupported scheme: $it" })
                ).distinct(),
        )
    }

    private fun parseEntry(
        entry: String,
        sourceType: SourceType,
        warnings: MutableList<String>,
        metadata: SubscriptionMetadataEnvelope,
    ): ParsedVpnProfile? {
        return when {
            entry.startsWith("vmess://", ignoreCase = true) -> {
                parseVmessFallback(entry, metadata) ?: run {
                    warnings += "Skipped entry: Invalid VMess URL format"
                    null
                }
            }
            entry.startsWith("ss://", ignoreCase = true) -> {
                parseShadowsocksFallback(entry, metadata) ?: run {
                    warnings += "Skipped entry: Invalid Shadowsocks URL format"
                    null
                }
            }
            else -> {
                val parseResult = runCatching {
                    ConfigParserEngine.parseConfig(entry, sourceType)
                }.getOrElse { error ->
                    warnings += "Skipped entry: ${error.message ?: "Unexpected parser failure"}"
                    return null
                }

                val profile = parseResult.profile
                if (profile == null) {
                    warnings += (parseResult.errors + parseResult.warnings).map { message ->
                        "Skipped entry: $message"
                    }
                    null
                } else {
                    mapProfile(profile, metadata)
                }
            }
        }
    }

    private fun mapProfile(
        profile: SwimVpnProfile,
        metadata: SubscriptionMetadataEnvelope,
    ): ParsedVpnProfile {
        return ParsedVpnProfile(
            providerName = metadata.providerName,
            displayName = profile.displayName,
            protocol = normalizeProtocol(profile.protocol),
            transport = normalizeTransport(profile),
            security = normalizeSecurity(profile.securityMode),
            serverHost = profile.address,
            serverPort = profile.port,
            userId = profile.userId,
            password = profile.password,
            encryption = profile.advancedSettings["encryption"] ?: "none",
            method = profile.method,
            sni = profile.tlsSettings?.sni,
            alpn = profile.tlsSettings?.alpn ?: emptyList(),
            fingerprint = profile.tlsSettings?.fingerprint,
            path = profile.websocketSettings?.path ?: profile.grpcSettings?.serviceName,
            hostHeader = profile.websocketSettings?.host ?: profile.tcpSettings?.host,
            serviceName = profile.grpcSettings?.serviceName,
            flow = profile.flow,
            publicKey = profile.realitySettings?.publicKey,
            shortId = profile.realitySettings?.shortId,
            spiderX = profile.realitySettings?.spiderX,
            allowInsecure = profile.tlsSettings?.allowInsecure,
            remark = profile.displayName,
            countryEmoji = SubscriptionMetadataParser.extractCountryEmoji(profile.displayName),
            trafficUsedBytes = metadata.trafficUsedBytes,
            trafficTotalBytes = metadata.trafficTotalBytes,
            expiresAt = metadata.expiresAt,
            autoUpdateIntervalHours = metadata.autoUpdateIntervalHours,
            raw = profile.rawConfig,
            warnings = profile.parseWarnings,
            isImportable = true,
        )
    }

    private fun normalizeProtocol(protocol: Protocol): String {
        return when (protocol) {
            Protocol.VLESS -> "vless"
            Protocol.VMESS -> "vmess"
            Protocol.TROJAN -> "trojan"
            Protocol.SHADOWSOCKS -> "shadowsocks"
            Protocol.UNKNOWN -> "unknown"
        }
    }

    private fun normalizeTransport(profile: SwimVpnProfile): String {
        val raw = profile.rawConfig.lowercase()
        return when (profile.transport) {
            Transport.TCP -> "tcp"
            Transport.WEBSOCKET -> "ws"
            Transport.GRPC -> "grpc"
            Transport.HTTP2 -> when {
                raw.contains("type=xhttp") || raw.contains("\"network\":\"xhttp\"") -> "xhttp"
                else -> "httpupgrade"
            }
            else -> "unknown"
        }
    }

    private fun normalizeSecurity(securityMode: SecurityMode): String {
        return when (securityMode) {
            SecurityMode.TLS -> "tls"
            SecurityMode.REALITY -> "reality"
            SecurityMode.NONE -> "none"
            else -> "unknown"
        }
    }

    private fun parseVmessFallback(
        entry: String,
        metadata: SubscriptionMetadataEnvelope,
    ): ParsedVpnProfile? {
        val base64Payload = entry.removePrefix("vmess://")
        val decoded = SubscriptionPayloadDecoder.decode(base64Payload).payload
        val root = runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull() ?: return null

        val host = root.get("add")?.asString ?: return null
        val port = root.get("port")?.asInt ?: return null
        val security = when (root.get("tls")?.asString?.lowercase()) {
            "tls" -> "tls"
            "reality" -> "reality"
            else -> "none"
        }
        val network = when (root.get("net")?.asString?.lowercase()) {
            "ws" -> "ws"
            "grpc" -> "grpc"
            "httpupgrade", "xhttp", "h2", "http" -> "httpupgrade"
            else -> "tcp"
        }
        val displayName = root.get("ps")?.asString?.takeIf { it.isNotBlank() } ?: "VMess: $host"
        val path = root.get("path")?.asString?.takeIf { it.isNotBlank() }

        return ParsedVpnProfile(
            providerName = metadata.providerName,
            displayName = displayName,
            protocol = "vmess",
            transport = network,
            security = security,
            serverHost = host,
            serverPort = port,
            userId = root.get("id")?.asString,
            encryption = root.get("scy")?.asString ?: root.get("security")?.asString,
            sni = root.get("sni")?.asString ?: root.get("serverName")?.asString,
            alpn = root.get("alpn")?.asString
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            fingerprint = root.get("fp")?.asString,
            path = path,
            hostHeader = root.get("host")?.asString?.takeIf { it.isNotBlank() },
            serviceName = root.get("serviceName")?.asString?.takeIf { it.isNotBlank() }
                ?: path?.takeIf { network == "grpc" },
            remark = displayName,
            countryEmoji = SubscriptionMetadataParser.extractCountryEmoji(displayName),
            trafficUsedBytes = metadata.trafficUsedBytes,
            trafficTotalBytes = metadata.trafficTotalBytes,
            expiresAt = metadata.expiresAt,
            autoUpdateIntervalHours = metadata.autoUpdateIntervalHours,
            raw = entry,
        )
    }

    private fun parseShadowsocksFallback(
        entry: String,
        metadata: SubscriptionMetadataEnvelope,
    ): ParsedVpnProfile? {
        val withoutScheme = entry.removePrefix("ss://")
        val hashIndex = withoutScheme.indexOf('#')
        val mainPart = if (hashIndex >= 0) withoutScheme.substring(0, hashIndex) else withoutScheme
        val displayName = if (hashIndex >= 0) java.net.URLDecoder.decode(withoutScheme.substring(hashIndex + 1), "UTF-8") else "Shadowsocks"
        val atIndex = mainPart.lastIndexOf('@')
        if (atIndex <= 0) return null

        val userInfoEncoded = mainPart.substring(0, atIndex)
        val hostPart = mainPart.substring(atIndex + 1)
        val credentials = runCatching {
            val padded = userInfoEncoded + "=".repeat((4 - userInfoEncoded.length % 4) % 4)
            String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_8)
        }.getOrNull() ?: return null

        val credentialParts = credentials.split(':', limit = 2)
        if (credentialParts.size != 2) return null

        val hostParts = hostPart.split(':', limit = 2)
        if (hostParts.size != 2) return null

        return ParsedVpnProfile(
            providerName = metadata.providerName,
            displayName = displayName,
            protocol = "shadowsocks",
            transport = "tcp",
            security = "none",
            serverHost = hostParts[0],
            serverPort = hostParts[1].toIntOrNull(),
            password = credentialParts[1],
            method = credentialParts[0],
            remark = displayName,
            countryEmoji = SubscriptionMetadataParser.extractCountryEmoji(displayName),
            trafficUsedBytes = metadata.trafficUsedBytes,
            trafficTotalBytes = metadata.trafficTotalBytes,
            expiresAt = metadata.expiresAt,
            autoUpdateIntervalHours = metadata.autoUpdateIntervalHours,
            raw = entry,
        )
    }
}
