package com.swimvpn.app.config.subscriptionparser

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.swimvpn.app.config.VpnConfigLinkExtractor
import java.net.URLDecoder
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
}
