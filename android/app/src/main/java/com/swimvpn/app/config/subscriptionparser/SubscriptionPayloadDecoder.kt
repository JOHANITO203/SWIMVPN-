package com.swimvpn.app.config.subscriptionparser

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.swimvpn.app.config.VpnConfigLinkExtractor
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

internal object SubscriptionPayloadDecoder {
    private const val MAX_CARRIER_DECODE_PASSES = 4
    private val percentEncodedPattern = Regex("%[0-9A-Fa-f]{2}")

    fun decode(input: String): DecodedPayload {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return DecodedPayload(payload = "")
        }

        val original = trimmed.removePrefix("\uFEFF").trim()
        var current = original
        val warnings = mutableListOf<String>()

        repeat(MAX_CARRIER_DECODE_PASSES) {
            if (isRemoteUrl(current) || containsImportableContent(current)) {
                return DecodedPayload(payload = current, warnings = warnings.distinct())
            }

            val unwrapped = unwrapHappAdd(current)
            if (unwrapped != null && unwrapped != current) {
                current = unwrapped
                warnings += "Unwrapped Happ add subscription payload"
                return@repeat
            }

            val urlDecoded = decodePercentEncoded(current)
            if (urlDecoded != null && urlDecoded != current) {
                current = urlDecoded
                warnings += "Decoded URL-encoded subscription payload"
                return@repeat
            }

            val base64Decoded = decodeBase64(current)?.trim()
            if (!base64Decoded.isNullOrBlank() && base64Decoded != current) {
                current = base64Decoded
                warnings += "Decoded Base64 subscription payload"
                return@repeat
            }

            return DecodedPayload(payload = original)
        }

        return if (isRemoteUrl(current) || containsImportableContent(current)) {
            DecodedPayload(payload = current, warnings = warnings.distinct())
        } else {
            DecodedPayload(payload = original)
        }
    }

    fun extractEntries(payload: String): List<String> {
        val trimmed = payload.trim()
        if (trimmed.isBlank()) {
            return emptyList()
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val extractedFromJson = extractJsonEntries(trimmed)
            if (extractedFromJson.isNotEmpty()) {
                return extractedFromJson
            }
            return listOf(trimmed)
        }

        val extractedFromClash = extractClashYamlEntries(trimmed)
        if (extractedFromClash.isNotEmpty()) {
            return extractedFromClash
        }

        val links = VpnConfigLinkExtractor.extractEntries(trimmed)
        return if (links.size == 1 && links.first() == trimmed && !VpnConfigLinkExtractor.containsRecognizedLink(trimmed)) {
            listOf(trimmed)
        } else {
            links
        }
    }

    private fun extractJsonEntries(input: String): List<String> {
        return runCatching {
            val root = JsonParser.parseString(input)
            when {
                root.isJsonArray -> extractFromArray(root.asJsonArray)
                root.isJsonObject -> extractFromObject(root.asJsonObject)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun extractFromArray(array: JsonArray): List<String> {
        return array.mapNotNull { element ->
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
                element.isJsonObject -> element.toString()
                else -> null
            }
        }.flatMap { extractEntries(it) }
    }

    private fun extractFromObject(obj: JsonObject): List<String> {
        val outbounds = obj.getAsJsonArray("outbounds")
        if (outbounds != null && outbounds.size() > 0) {
            return outbounds.mapNotNull { outbound ->
                if (!outbound.isJsonObject) return@mapNotNull null
                buildSingleOutboundPayload(outbound.asJsonObject)
            }
        }

        return if (containsImportableContent(obj.toString())) {
            VpnConfigLinkExtractor.extractEntries(obj.toString())
        } else {
            emptyList()
        }
    }

    private fun buildSingleOutboundPayload(outbound: JsonObject): String? {
        buildSingBoxLink(outbound)?.let { return it }

        val protocol = outbound.getAsJsonPrimitive("protocol")?.asString?.lowercase() ?: return null
        if (protocol !in setOf("vless", "vmess", "trojan", "shadowsocks")) {
            return null
        }

        return JsonObject().apply {
            add("outbounds", JsonArray().apply { add(outbound) })
            if (outbound.has("tag")) {
                add("remarks", JsonPrimitive(outbound.getAsJsonPrimitive("tag").asString))
            }
        }.toString()
    }

    private fun buildSingBoxLink(outbound: JsonObject): String? {
        val type = outbound.string("type")?.lowercase() ?: return null
        if (type !in setOf("vless", "vmess", "trojan", "shadowsocks")) {
            return null
        }

        val host = outbound.string("server") ?: return null
        val port = outbound.int("server_port") ?: return null
        val tag = outbound.string("tag") ?: "$type: $host"
        val tls = outbound.getAsJsonObject("tls")
        val transport = outbound.getAsJsonObject("transport")
        val query = linkedMapOf<String, String>()

        val transportType = transport?.string("type")
        if (!transportType.isNullOrBlank()) {
            query["type"] = when (transportType.lowercase()) {
                "websocket" -> "ws"
                else -> transportType
            }
        }
        transport?.string("path")?.let { query["path"] = it }
        transport?.string("host")?.let { query["host"] = it }
        transport?.string("service_name")?.let { query["serviceName"] = it }

        if (tls?.bool("enabled") == true) {
            val reality = tls.getAsJsonObject("reality")
            query["security"] = if (reality?.bool("enabled") == true) "reality" else "tls"
            tls.string("server_name")?.let { query["sni"] = it }
            tls.getAsJsonObject("utls")?.string("fingerprint")?.let { query["fp"] = it }
            reality?.string("public_key")?.let { query["pbk"] = it }
            reality?.string("short_id")?.let { query["sid"] = it }
            reality?.string("spider_x")?.let { query["spx"] = it }
        }

        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
        val fragment = "#${encode(tag)}"

        return when (type) {
            "vless" -> {
                val uuid = outbound.string("uuid") ?: return null
                outbound.string("flow")?.let { query["flow"] = it }
                val rebuiltQuery = query.entries.joinToString("&") { (key, value) ->
                    "${encode(key)}=${encode(value)}"
                }.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
                "vless://$uuid@$host:$port$rebuiltQuery$fragment"
            }
            "trojan" -> {
                val password = outbound.string("password") ?: return null
                "trojan://${encode(password)}@$host:$port$queryString$fragment"
            }
            "shadowsocks" -> {
                val method = outbound.string("method") ?: return null
                val password = outbound.string("password") ?: return null
                val credentials = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString("$method:$password".toByteArray(Charsets.UTF_8))
                "ss://$credentials@$host:$port$fragment"
            }
            "vmess" -> null
            else -> null
        }
    }

    private fun extractClashYamlEntries(input: String): List<String> {
        if (!input.lineSequence().any { it.trim() == "proxies:" }) {
            return emptyList()
        }

        val entries = mutableListOf<String>()
        val current = linkedMapOf<String, String>()

        fun flush() {
            buildClashLink(current)?.let { entries += it }
            current.clear()
        }

        input.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("- ")) {
                flush()
                parseYamlPair(line.removePrefix("- "))?.let { (key, value) -> current[key] = value }
                return@forEach
            }
            parseYamlPair(line)?.let { (key, value) ->
                current[key] = value
            }
        }
        flush()

        return entries
    }

    private fun buildClashLink(values: Map<String, String>): String? {
        val type = values["type"]?.lowercase() ?: return null
        if (type !in setOf("vless", "trojan", "ss", "shadowsocks")) {
            return null
        }
        val host = values["server"] ?: return null
        val port = values["port"]?.toIntOrNull() ?: return null
        val name = values["name"] ?: "$type: $host"
        val query = linkedMapOf<String, String>()
        values["network"]?.let { query["type"] = if (it == "websocket") "ws" else it }
        if (values["tls"] == "true") query["security"] = "tls"
        values["servername"]?.let { query["sni"] = it }
        values["sni"]?.let { query["sni"] = it }
        values["client-fingerprint"]?.let { query["fp"] = it }
        values["path"]?.let { query["path"] = it }
        values["Host"]?.let { query["host"] = it }
        values["host"]?.let { query["host"] = it }
        values["flow"]?.let { query["flow"] = it }

        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
        val fragment = "#${encode(name)}"

        return when (type) {
            "vless" -> {
                val uuid = values["uuid"] ?: return null
                "vless://$uuid@$host:$port$queryString$fragment"
            }
            "trojan" -> {
                val password = values["password"] ?: return null
                "trojan://${encode(password)}@$host:$port$queryString$fragment"
            }
            "ss", "shadowsocks" -> {
                val method = values["cipher"] ?: return null
                val password = values["password"] ?: return null
                val credentials = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString("$method:$password".toByteArray(Charsets.UTF_8))
                "ss://$credentials@$host:$port$fragment"
            }
            else -> null
        }
    }

    private fun parseYamlPair(line: String): Pair<String, String>? {
        val index = line.indexOf(':')
        if (index <= 0) return null
        val key = line.substring(0, index).trim()
        val rawValue = line.substring(index + 1).trim()
        if (key.isBlank() || rawValue.isBlank()) return null
        return key to rawValue.trim('"', '\'')
    }

    private fun containsImportableContent(input: String): Boolean {
        val trimmed = input.trim()
        return VpnConfigLinkExtractor.containsRecognizedLink(trimmed) ||
            trimmed.startsWith("{") ||
            trimmed.startsWith("[")
    }

    private fun isRemoteUrl(input: String): Boolean {
        return input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true)
    }

    private fun unwrapHappAdd(input: String): String? {
        if (!input.startsWith("happ://add/", ignoreCase = true)) {
            return null
        }

        val wrapped = input.substring("happ://add/".length)
            .takeIf { it.isNotBlank() }
            ?: return null

        return decodePercentEncoded(wrapped) ?: wrapped.trim()
    }

    private fun decodePercentEncoded(input: String): String? {
        if (!percentEncodedPattern.containsMatchIn(input)) {
            return null
        }

        return runCatching {
            URLDecoder.decode(input, "UTF-8").trim()
        }.getOrNull()?.takeIf { it.isNotBlank() && it != input }
    }

    private fun decodeBase64(input: String): String? {
        val compact = input
            .removePrefix("\uFEFF")
            .lines()
            .joinToString("") { it.trim() }
            .filter { char ->
                char.isLetterOrDigit() || char == '+' || char == '/' || char == '-' || char == '_' || char == '='
            }
            .trim()

        if (compact.isBlank()) {
            return null
        }

        fun pad(value: String): String {
            val padding = (4 - value.length % 4) % 4
            return value + "=".repeat(padding)
        }

        val candidates = listOf(
            pad(compact),
            pad(compact.replace('-', '+').replace('_', '/')),
        ).distinct()

        val flags = listOf(
            Base64.getDecoder(),
            Base64.getUrlDecoder(),
        )

        return candidates.asSequence()
            .flatMap { candidate -> flags.asSequence().map { decoder -> candidate to decoder } }
            .mapNotNull { (candidate, decoder) ->
                runCatching {
                    String(decoder.decode(candidate), Charsets.UTF_8)
                }.getOrNull()
            }
            .firstOrNull { decoded -> looksLikeTextCarrier(decoded.trim()) }
    }

    private fun looksLikeTextCarrier(input: String): Boolean {
        if (input.isBlank() || input.contains('\uFFFD')) {
            return false
        }

        val printable = input.count { char ->
            !char.isISOControl() || char == '\n' || char == '\r' || char == '\t'
        }
        return printable.toDouble() / input.length.toDouble() >= 0.9
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asInt

    private fun JsonObject.bool(name: String): Boolean =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asBoolean == true

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
