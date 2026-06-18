package com.swimvpn.desktop.vpn

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Runtime post-processing of the Xray config, ported verbatim (Gson-pure) from Android's
 * TunnelRuntimeAdapter — the part that makes REALITY/censored networks actually work:
 *  - dial the server by a pre-resolved IPv4 (keep the hostname as SNI) so xray doesn't rely on its
 *    own DNS (the "connected but no internet" failure on DNS-censored networks),
 *  - FakeDNS + sniffing so DNS is answered locally instead of storming the server (TUN mode),
 *  - block literal IPv6 so it fast-fails to IPv4 through the tunnel (TUN mode).
 */
object RuntimeDoc {

    /** Replace each outbound dial host with a resolved IPv4, preserving the hostname as TLS/REALITY
     *  SNI. Best-effort: unresolved hosts are left as-is. */
    fun resolveOutboundServerAddresses(document: JsonObject) {
        val outbounds = document.getAsJsonArray("outbounds") ?: return
        outbounds.forEach { element ->
            val outbound = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val settings = outbound.getAsJsonObject("settings") ?: return@forEach
            val stream = outbound.getAsJsonObject("streamSettings")
            listOf("vnext", "servers").forEach { key ->
                settings.getAsJsonArray(key)?.forEach { node ->
                    val obj = node.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                    val addr = obj.get("address")?.takeIf { it.isJsonPrimitive }?.asString ?: return@forEach
                    if (addr.isBlank() || isIpLiteral(addr)) return@forEach
                    val ip = resolveIpv4(addr) ?: return@forEach
                    stream?.getAsJsonObject("tlsSettings")?.let { t ->
                        if (t.get("serverName")?.asString.isNullOrBlank()) t.addProperty("serverName", addr)
                    }
                    stream?.getAsJsonObject("realitySettings")?.let { r ->
                        if (r.get("serverName")?.asString.isNullOrBlank()) r.addProperty("serverName", addr)
                    }
                    obj.addProperty("address", ip)
                }
            }
        }
    }

    /** FakeDNS + sniffing + dns-out routing (TUN mode): answer DNS locally, route by domain. */
    fun applyFakeDnsInterception(document: JsonObject) {
        if (!document.has("fakedns")) {
            document.add("fakedns", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("ipPool", "198.18.0.0/15")
                    addProperty("poolSize", 65535)
                })
            })
        }
        val dns = document.getAsJsonObject("dns") ?: JsonObject().also { document.add("dns", it) }
        if (dns.get("queryStrategy")?.asString.isNullOrBlank()) dns.addProperty("queryStrategy", "UseIPv4")
        val servers = dns.getAsJsonArray("servers") ?: JsonArray().also { dns.add("servers", it) }
        if (!servers.any { it.isJsonPrimitive && it.asString == "fakedns" }) {
            dns.add("servers", JsonArray().apply { add("fakedns"); servers.forEach { add(it) } })
        }
        document.getAsJsonArray("inbounds")?.forEach { el ->
            val inbound = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val sniff = inbound.getAsJsonObject("sniffing") ?: JsonObject().also { inbound.add("sniffing", it) }
            sniff.addProperty("enabled", true)
            val want = linkedSetOf("fakedns", "http", "tls", "quic")
            inbound.getAsJsonObject("sniffing")?.getAsJsonArray("destOverride")
                ?.forEach { if (it.isJsonPrimitive) want.add(it.asString) }
            sniff.add("destOverride", JsonArray().apply { want.forEach { add(it) } })
        }
        val outbounds = document.getAsJsonArray("outbounds") ?: return
        if (!outbounds.any { it.isJsonObject && it.asJsonObject.get("tag")?.asString == "dns-out" }) {
            outbounds.add(JsonObject().apply { addProperty("tag", "dns-out"); addProperty("protocol", "dns") })
        }
        val routing = document.getAsJsonObject("routing") ?: JsonObject().also { document.add("routing", it) }
        val rules = routing.getAsJsonArray("rules") ?: JsonArray().also { routing.add("rules", it) }
        if (!rules.any { it.isJsonObject && it.asJsonObject.get("outboundTag")?.asString == "dns-out" }) {
            val dnsRule = JsonObject().apply {
                addProperty("type", "field"); addProperty("port", 53); addProperty("outboundTag", "dns-out")
            }
            routing.add("rules", JsonArray().apply { add(dnsRule); rules.forEach { add(it) } })
        }
    }

    /** Block literal IPv6 → fast-fail → Happy Eyeballs retries over IPv4 through the tunnel. */
    fun appendIpv6BlockRule(document: JsonObject) {
        val routing = document.getAsJsonObject("routing") ?: JsonObject().also { document.add("routing", it) }
        val rules = routing.getAsJsonArray("rules") ?: JsonArray().also { routing.add("rules", it) }
        val present = rules.any {
            it.isJsonObject && it.asJsonObject.getAsJsonArray("ip")
                ?.any { ip -> ip.isJsonPrimitive && ip.asString == "::/0" } == true
        }
        if (present) return
        rules.add(JsonObject().apply {
            addProperty("type", "field")
            add("ip", JsonArray().apply { add("::/0") })
            addProperty("outboundTag", "block")
        })
    }

    private fun isIpLiteral(host: String): Boolean =
        host.contains(":") || host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))

    private fun resolveIpv4(host: String): String? = try {
        java.net.InetAddress.getAllByName(host).firstOrNull { it is java.net.Inet4Address }?.hostAddress
    } catch (e: Exception) {
        null
    }
}
