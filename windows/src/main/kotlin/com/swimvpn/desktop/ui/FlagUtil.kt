package com.swimvpn.desktop.ui

/**
 * Decodes the country carried in a config name. The flag is metadata embedded in the name as a
 * regional-indicator emoji (e.g. "🇷🇺🇺🇦 Россия | YouTube" → "RU"); we extract the ISO-3166-1
 * alpha-2 code from the first such pair, falling back to a few common name keywords (RU/EU/etc.).
 * The UI then renders a real flag image from this code — same principle as Happ.
 */
object FlagUtil {
    private const val RI_BASE = 0x1F1E6 // 🇦
    private const val RI_LAST = 0x1F1FF // 🇿

    /** ISO-2 country code, or null when the name carries no country (auto-select, game nodes, …). */
    fun countryCode(name: String): String? {
        // 1) Regional-indicator flag emoji (the canonical metadata).
        val letters = StringBuilder()
        var i = 0
        while (i < name.length && letters.length < 2) {
            val cp = name.codePointAt(i)
            if (cp in RI_BASE..RI_LAST) {
                letters.append(('A' + (cp - RI_BASE)))
            } else if (letters.isNotEmpty()) {
                break // stop at the end of the first flag run
            }
            i += Character.charCount(cp)
        }
        if (letters.length == 2) return letters.toString()

        // 2) Fallback: keyword in the (cyrillic/latin) name → ISO-2.
        val n = name.lowercase()
        return KEYWORDS.entries.firstOrNull { n.contains(it.key) }?.value
    }

    /** The name without its leading flag emoji (rendered separately as a real flag image). */
    fun cleanName(name: String): String {
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            if (cp in RI_BASE..RI_LAST || cp == 0x200D || cp == 0xFE0F || Character.isWhitespace(cp)) {
                i += Character.charCount(cp)
            } else break
        }
        return name.substring(i).trim().ifBlank { name.trim() }
    }

    private val KEYWORDS = linkedMapOf(
        "росси" to "RU", "russia" to "RU",
        "германи" to "DE", "germany" to "DE",
        "нидерланд" to "NL", "netherlan" to "NL",
        "финлянди" to "FI", "finland" to "FI",
        "швеци" to "SE", "sweden" to "SE",
        "латви" to "LV", "latvia" to "LV",
        "литв" to "LT", "lithuania" to "LT",
        "эстони" to "EE", "estonia" to "EE",
        "польш" to "PL", "poland" to "PL",
        "франци" to "FR", "france" to "FR",
        "британи" to "GB", "британия" to "GB", "uk" to "GB",
        "армени" to "AM", "armenia" to "AM",
        "турци" to "TR", "turkey" to "TR",
        "инди" to "IN", "india" to "IN",
        "сша" to "US", "usa" to "US",
    )
}
