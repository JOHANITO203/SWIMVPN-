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
