package com.swimvpn.app.config.subscriptionparser

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.swimvpn.app.config.VpnConfigLinkExtractor
import java.util.Base64

internal object SubscriptionPayloadDecoder {
    fun decode(input: String): DecodedPayload {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return DecodedPayload(payload = "")
        }

        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return DecodedPayload(payload = trimmed)
        }

        if (containsImportableContent(trimmed)) {
            return DecodedPayload(payload = trimmed)
        }

        val decoded = decodeBase64(trimmed)
        return if (decoded != null) {
            DecodedPayload(
                payload = decoded.trim(),
                warnings = listOf("Decoded Base64 subscription payload"),
            )
        } else {
            DecodedPayload(payload = trimmed)
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
            .firstOrNull { decoded -> containsImportableContent(decoded.trim()) }
    }
}
