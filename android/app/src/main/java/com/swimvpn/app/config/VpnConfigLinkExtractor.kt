package com.swimvpn.app.config

/**
 * Extracts VPN config entries from provider payloads without confusing nested scheme text.
 *
 * Example: `vless://` contains `ss://` as characters 4..7. Scheme detection must therefore
 * only match at a real entry boundary, not anywhere inside a token.
 */
object VpnConfigLinkExtractor {
    private val schemes = listOf(
        "hysteria2",
        "wireguard",
        "trojan",
        "vmess",
        "vless",
        "socks5",
        "hysteria",
        "tuic",
        "hy2",
        "socks",
        "ss",
        "wg",
    )

    private val schemeStartRegex = Regex(
        pattern = "(?i)(?<![A-Za-z0-9+.-])(?=(?:${schemes.joinToString("|")})://)",
    )

    fun containsRecognizedLink(input: String): Boolean {
        return schemeStartRegex.containsMatchIn(input)
    }

    fun extractEntries(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return emptyList()
        }

        val normalized = trimmed.replace("\r", "\n")
        val matches = schemeStartRegex.findAll(normalized).toList()
        if (matches.isEmpty()) {
            // Common provider format: a Base64-encoded subscription blob (a list of links with no
            // visible scheme). Decode once and re-extract if it reveals recognized links.
            val decoded = decodeBase64Blob(normalized)
            if (decoded != null && decoded != normalized && containsRecognizedLink(decoded)) {
                return extractEntries(decoded)
            }
            return listOf(trimmed)
        }

        return matches.mapIndexedNotNull { index, match ->
            val start = match.range.first
            val end = if (index + 1 < matches.size) {
                matches[index + 1].range.first
            } else {
                normalized.length
            }

            normalized
                .substring(start, end)
                .trimConfigDelimiters()
                .takeIf { it.isNotBlank() }
        }
    }

    /** Tolerant Base64 decode of a (possibly multi-line, URL-safe, unpadded) subscription blob. */
    private fun decodeBase64Blob(input: String): String? {
        val compact = input.filterNot { it.isWhitespace() }
        if (compact.length < 16) return null
        val normalized = compact.replace('-', '+').replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        return runCatching {
            String(java.util.Base64.getMimeDecoder().decode(normalized), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun String.trimConfigDelimiters(): String {
        return trim { char ->
            char.isWhitespace() ||
                char == '"' ||
                char == '\'' ||
                char == '`' ||
                char == ',' ||
                char == ';' ||
                char == '[' ||
                char == ']' ||
                char == '{' ||
                char == '}' ||
                char == '<' ||
                char == '>' ||
                char == '(' ||
                char == ')'
        }
    }
}
