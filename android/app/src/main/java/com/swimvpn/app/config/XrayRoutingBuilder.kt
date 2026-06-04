package com.swimvpn.app.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Options driving Xray `routing.rules` generation for the optional geo-bypass / basic split.
 *
 * Defaults are a deliberate **no-op** ([bypassGeo] = false, no domain/block lists): with the
 * defaults, [XrayRoutingBuilder.buildRules] returns an empty array, so the full-tunnel runtime is
 * byte-for-byte unchanged. Bypass only engages when the user toggles it ON.
 */
data class RoutingOptions(
    val bypassGeo: Boolean = false,
    val directDomains: List<String> = emptyList(),
    val directIps: List<String> = DEFAULT_DIRECT_IPS,
    val blockDomains: List<String> = emptyList(),
) {
    companion object {
        /** LAN / private address space, sent direct (outside the tunnel) when bypass is ON. */
        val DEFAULT_DIRECT_IPS: List<String> = listOf("geoip:private")

        /** Private/local domains (e.g. *.lan), sent direct when bypass is ON. */
        val DEFAULT_DIRECT_DOMAINS: List<String> = listOf("geosite:private")
    }
}

/**
 * Pure builder for the `routing.rules` array. No Android deps — unit-testable directly.
 *
 * Rule order (Xray evaluates first-match): direct IPs → direct domains → block domains. Anything
 * unmatched falls through to the default route (the first outbound, tag `proxy`).
 */
object XrayRoutingBuilder {

    /** True when any rule would be emitted (drives both rule emission and inbound sniffing). */
    fun hasActiveRules(options: RoutingOptions): Boolean =
        options.bypassGeo ||
            options.directDomains.isNotEmpty() ||
            options.blockDomains.isNotEmpty()

    fun buildRules(options: RoutingOptions): JsonArray {
        val rules = JsonArray()
        if (!hasActiveRules(options)) return rules // OFF + no lists ⇒ empty ⇒ full tunnel preserved

        addIpRule(rules, options.directIps, "direct")
        addDomainRule(rules, RoutingOptions.DEFAULT_DIRECT_DOMAINS + options.directDomains, "direct")
        addDomainRule(rules, options.blockDomains, "block")
        return rules
    }

    private fun addIpRule(rules: JsonArray, ips: List<String>, tag: String) {
        val clean = ips.filter { it.isNotBlank() }
        if (clean.isEmpty()) return
        rules.add(JsonObject().apply {
            addProperty("type", "field")
            add("ip", JsonArray().apply { clean.forEach { add(it) } })
            addProperty("outboundTag", tag)
        })
    }

    private fun addDomainRule(rules: JsonArray, domains: List<String>, tag: String) {
        val clean = domains.filter { it.isNotBlank() }
        if (clean.isEmpty()) return
        rules.add(JsonObject().apply {
            addProperty("type", "field")
            add("domain", JsonArray().apply { clean.forEach { add(it) } })
            addProperty("outboundTag", tag)
        })
    }

    private val IPV4_CIDR = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(/\\d{1,2})?$")
    private val IPV6_CIDR = Regex("^[0-9a-fA-F:]+(/\\d{1,3})?$")

    /**
     * Normalizes a raw user-entered direct-routing entry. Returns the trimmed entry, or null if it
     * is unusable (blank or contains whitespace) — guards the generated Xray config against
     * malformed rules. Accepts plain hosts, `domain:`/`full:`/`keyword:`/`regexp:`/`geosite:` rules,
     * IPv4/IPv6 (with optional CIDR), and `geoip:` rules; semantic validity beyond this is left to
     * Xray (we never want to silently drop a valid-but-unusual rule).
     */
    fun sanitizeEntry(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        return trimmed
    }

    /** True when a sanitized entry belongs in Xray's `ip` rule field rather than `domain`. */
    fun isIpEntry(entry: String): Boolean {
        if (entry.startsWith("geoip:", ignoreCase = true)) return true
        if (IPV4_CIDR.matches(entry)) return true
        // IPv6 must contain a colon and only hex/colon (+ optional CIDR); excludes "domain:x" forms.
        if (entry.contains(":") && !entry.contains(".") && IPV6_CIDR.matches(entry)) return true
        return false
    }

    /**
     * Partitions raw user entries into (domains, ips) for the `domain`/`ip` routing fields,
     * dropping invalid ones and de-duplicating while preserving order.
     */
    fun partitionDirectEntries(entries: Collection<String>): Pair<List<String>, List<String>> {
        val domains = LinkedHashSet<String>()
        val ips = LinkedHashSet<String>()
        entries.forEach { raw ->
            val entry = sanitizeEntry(raw) ?: return@forEach
            if (isIpEntry(entry)) ips.add(entry) else domains.add(entry)
        }
        return domains.toList() to ips.toList()
    }
}
