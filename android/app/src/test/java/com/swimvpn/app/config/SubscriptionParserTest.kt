package com.swimvpn.app.config

import com.swimvpn.app.config.subscriptionparser.SubscriptionParser
import com.swimvpn.app.config.subscriptionparser.SubscriptionHeaderMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.util.Base64

class SubscriptionParserTest {

    @Test
    fun `parses multiline base64 subscription payload`() {
        val payload = listOf(
            "Provider Demo 🇳🇱",
            "15.3GB/1000.0GB",
            "Expires: 15.05.2026",
            "Autoupdate - 1 h.",
            "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=ws&host=cdn.example.com&path=%2Fsocket&sni=edge.example.com#NL%20Node",
            "trojan://secret@example.net:443?security=tls&type=tcp&sni=discord.com#Trojan%20Node"
        ).joinToString("\n")

        val encoded = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        val multiline = encoded.chunked(24).joinToString("\n")

        val parsed = SubscriptionParser.parse(multiline, sourceType = SourceType.SUBSCRIPTION_URL)

        assertEquals(2, parsed.profiles.size)
        assertEquals("Provider Demo 🇳🇱", parsed.providerName)
        assertEquals(15.3 * 1024 * 1024 * 1024, parsed.trafficUsedBytes!!.toDouble(), 1024.0)
        assertEquals(1000.0 * 1024 * 1024 * 1024, parsed.trafficTotalBytes!!.toDouble(), 1024.0)
        assertEquals("2026-05-15T00:00:00Z", parsed.expiresAt)
        assertEquals(1, parsed.autoUpdateIntervalHours)
    }

    @Test
    fun `parses vless tls profile metadata`() {
        val input = "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=ws&host=cdn.example.com&path=%2Fsocket&sni=edge.example.com&fp=chrome&flow=xtls-rprx-vision#🇳🇱%20NL%20TLS"

        val parsed = SubscriptionParser.parse(input)
        val profile = parsed.profiles.single()

        assertEquals("vless", profile.protocol)
        assertEquals("ws", profile.transport)
        assertEquals("tls", profile.security)
        assertEquals("example.com", profile.serverHost)
        assertEquals(443, profile.serverPort)
        assertEquals("edge.example.com", profile.sni)
        assertEquals("chrome", profile.fingerprint)
        assertEquals("/socket", profile.path)
        assertEquals("xtls-rprx-vision", profile.flow)
        assertEquals("🇳🇱", profile.countryEmoji)
    }

    @Test
    fun `parses vless reality profile metadata`() {
        val input = "vless://11111111-1111-1111-1111-111111111111@reality.example.com:8443?security=reality&type=tcp&sni=www.google.com&fp=chrome&pbk=PUBLICKEY123&sid=ab12#Reality%20Node"

        val parsed = SubscriptionParser.parse(input)
        val profile = parsed.profiles.single()

        assertEquals("vless", profile.protocol)
        assertEquals("tcp", profile.transport)
        assertEquals("reality", profile.security)
        assertEquals("PUBLICKEY123", profile.publicKey)
        assertEquals("ab12", profile.shortId)
        assertEquals("www.google.com", profile.sni)
    }

    @Test
    fun `uses subscription userinfo header metadata for remote subscriptions`() {
        val payload = "vless://11111111-1111-1111-1111-111111111111@run.cloudrt.ru:443?security=reality&type=tcp&sni=run.cloudrt.ru&fp=chrome&pbk=PUBLICKEY123&sid=ab12#🇷🇺%20Россия%20%7C%20YouTube"
        val headerMetadata = SubscriptionHeaderMetadata(
            providerName = "wb.routerwb.ru",
            trafficUsedBytes = 59_322_736_383L,
            trafficTotalBytes = 1_073_741_824_000L,
            expiresAt = "2026-05-21T23:04:09Z",
            autoUpdateIntervalHours = 1,
        )

        val parsed = SubscriptionParser.parse(
            input = payload,
            sourceType = SourceType.SUBSCRIPTION_URL,
            sourceUrl = "https://wb.routerwb.ru/jtz5386jCHkztYRZ",
            headerMetadata = headerMetadata,
        )

        assertEquals(1, parsed.profiles.size)
        assertEquals("wb.routerwb.ru", parsed.providerName)
        assertEquals(59_322_736_383L, parsed.trafficUsedBytes)
        assertEquals(1_073_741_824_000L, parsed.trafficTotalBytes)
        assertEquals("2026-05-21T23:04:09Z", parsed.expiresAt)
        assertEquals(1, parsed.autoUpdateIntervalHours)
        assertEquals("🇷🇺 Россия | YouTube", parsed.profiles.single().displayName)
    }

    @Test
    fun `parses vmess base64 json profile`() {
        val vmessJson = """
            {
              "v": "2",
              "ps": "RU VMess 🚀",
              "add": "vmess.example.com",
              "port": 443,
              "id": "22222222-2222-2222-2222-222222222222",
              "aid": 0,
              "net": "grpc",
              "type": "none",
              "host": "",
              "path": "gun-service",
              "tls": "tls",
              "sni": "grpc.example.com",
              "fp": "chrome"
            }
        """.trimIndent()
        val input = "vmess://${Base64.getEncoder().encodeToString(vmessJson.toByteArray(Charsets.UTF_8))}"

        val parsed = SubscriptionParser.parse(input)
        val profile = parsed.profiles.single()

        assertEquals("vmess", profile.protocol)
        assertEquals("grpc", profile.transport)
        assertEquals("tls", profile.security)
        assertEquals("vmess.example.com", profile.serverHost)
        assertEquals(443, profile.serverPort)
        assertEquals("gun-service", profile.path)
        assertEquals("grpc.example.com", profile.sni)
    }

    @Test
    fun `parses trojan profile`() {
        val input = "trojan://supersecret@trojan.example.com:443?security=tls&type=tcp&sni=discord.com#Trojan%20TLS"

        val parsed = SubscriptionParser.parse(input)
        val profile = parsed.profiles.single()

        assertEquals("trojan", profile.protocol)
        assertEquals("tcp", profile.transport)
        assertEquals("tls", profile.security)
        assertEquals("trojan.example.com", profile.serverHost)
        assertEquals(443, profile.serverPort)
        assertEquals("supersecret", profile.password)
    }

    @Test
    fun `parses shadowsocks profile`() {
        val userInfo = Base64.getEncoder().encodeToString("aes-256-gcm:password123".toByteArray(Charsets.UTF_8))
        val input = "ss://$userInfo@ss.example.com:8388#SS%20Node"

        val parsed = SubscriptionParser.parse(input)
        val profile = parsed.profiles.single()

        assertEquals("shadowsocks", profile.protocol)
        assertEquals("tcp", profile.transport)
        assertEquals("none", profile.security)
        assertEquals("ss.example.com", profile.serverHost)
        assertEquals(8388, profile.serverPort)
        assertEquals("aes-256-gcm", profile.method)
        assertEquals("password123", profile.password)
    }

    @Test
    fun `parses exact traffic ratio values`() {
        val parsed = SubscriptionParser.parse("300.0GB/300.0GB\nvless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#Node")

        assertEquals(300.0 * 1024 * 1024 * 1024, parsed.trafficUsedBytes!!.toDouble(), 1024.0)
        assertEquals(300.0 * 1024 * 1024 * 1024, parsed.trafficTotalBytes!!.toDouble(), 1024.0)
    }

    @Test
    fun `parses unlimited traffic as null total`() {
        val parsed = SubscriptionParser.parse("178.7GB/∞\nvless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#Node")

        assertEquals(178.7 * 1024 * 1024 * 1024, parsed.trafficUsedBytes!!.toDouble(), 1024.0)
        assertNull(parsed.trafficTotalBytes)
    }

    @Test
    fun `parses european expiry date`() {
        val parsed = SubscriptionParser.parse("Expires: 15.05.2026\nvless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#Node")

        assertEquals("2026-05-15T00:00:00Z", parsed.expiresAt)
    }

    @Test
    fun `preserves russian metadata and emoji`() {
        val input = """
            🇷🇺 Поставщик VPN
            Закончился трафик
            vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#🇷🇺 Москва
        """.trimIndent()

        val parsed = SubscriptionParser.parse(input)
        val profile = parsed.profiles.single()

        assertEquals("Поставщик VPN", parsed.providerName)
        assertEquals("🇷🇺", profile.countryEmoji)
        assertTrue(parsed.warnings.any { it.contains("Закончился трафик") })
    }

    @Test
    fun `parses russian supplier bundle traffic and textual expiry`() {
        val input = """
            🔑 Ваша подписка:

            https://wb.routerwb.ru/jtz5386jCHkztYRZ

            ⏳ Осталось: 28 дней, 19 часов, 20 минут
            Истекает: 21 мая 2026 года

            📦 Тариф подписки:📁 Группа: Базовый ⚡️ 8 стран
            📊 Трафик: 1000 ГБ
            📉 Израсходовано: 6.7 ГБ / 1000 ГБ

            VlessWB
            vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#Node
        """.trimIndent()

        val parsed = SubscriptionParser.parse(input)

        assertEquals("VlessWB", parsed.providerName)
        assertEquals(6.7 * 1024 * 1024 * 1024, parsed.trafficUsedBytes!!.toDouble(), 1024.0)
        assertEquals(1000.0 * 1024 * 1024 * 1024, parsed.trafficTotalBytes!!.toDouble(), 1024.0)
        assertEquals("2026-05-21T00:00:00Z", parsed.expiresAt)
    }

    @Test
    fun `keeps partial invalid configs as warnings without crashing`() {
        val input = """
            Provider Demo
            trojan://missing-host@:443?security=tls#Broken
            vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#Working
        """.trimIndent()

        val parsed = SubscriptionParser.parse(input)

        assertEquals(1, parsed.profiles.size)
        assertEquals("Working", parsed.profiles.single().displayName)
        assertTrue(parsed.warnings.isNotEmpty())
        assertNotNull(parsed.raw)
    }

    @Test
    fun `parses url encoded base64 subscription payload`() {
        val payload = "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=reality&type=tcp&sni=example.com&pbk=PUBLICKEY123&sid=ab12#Encoded%20Node"
        val encoded = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        val urlEncoded = URLEncoder.encode(encoded, "UTF-8")

        val parsed = SubscriptionParser.parse(urlEncoded, sourceType = SourceType.SUBSCRIPTION_URL)

        assertEquals(1, parsed.profiles.size)
        assertEquals("Encoded Node", parsed.profiles.single().displayName)
    }

    @Test
    fun `parses nested base64 subscription payload`() {
        val payload = "trojan://secret@example.net:443?security=tls&type=tcp&sni=example.net#Nested%20Node"
        val inner = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        val outer = Base64.getEncoder().encodeToString(inner.toByteArray(Charsets.UTF_8))

        val parsed = SubscriptionParser.parse(outer, sourceType = SourceType.SUBSCRIPTION_URL)

        assertEquals(1, parsed.profiles.size)
        assertEquals("Nested Node", parsed.profiles.single().displayName)
    }

    @Test
    fun `parses happ add wrapper carrying encoded subscription payload`() {
        val payload = "ss://${Base64.getEncoder().encodeToString("aes-256-gcm:password123".toByteArray(Charsets.UTF_8))}@ss.example.com:8388#Wrapped%20SS"
        val encoded = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        val wrapped = "happ://add/${URLEncoder.encode(encoded, "UTF-8")}"

        val parsed = SubscriptionParser.parse(wrapped, sourceType = SourceType.SUBSCRIPTION_URL)

        assertEquals(1, parsed.profiles.size)
        assertEquals("Wrapped SS", parsed.profiles.single().displayName)
        assertEquals("shadowsocks", parsed.profiles.single().protocol)
    }

    @Test
    fun `parses json array of vless outbound nodes`() {
        val input = """
            [
              {
                "tag": "Самый Быстрый АВТО",
                "protocol": "vless",
                "settings": {
                  "vnext": [
                    {
                      "address": "auto.example.com",
                      "port": 443,
                      "users": [
                        {
                          "id": "11111111-1111-1111-1111-111111111111",
                          "encryption": "none",
                          "flow": "xtls-rprx-vision"
                        }
                      ]
                    }
                  ]
                },
                "streamSettings": {
                  "network": "xhttp",
                  "security": "reality",
                  "realitySettings": {
                    "serverName": "auto.example.com",
                    "fingerprint": "chrome",
                    "publicKey": "PUBLICKEY123",
                    "shortId": "ab12"
                  },
                  "httpSettings": {
                    "path": "/auto",
                    "host": ["auto.example.com"]
                  }
                }
              },
              {
                "tag": "Германия",
                "protocol": "vless",
                "settings": {
                  "vnext": [
                    {
                      "address": "de.example.com",
                      "port": 443,
                      "users": [
                        {
                          "id": "22222222-2222-2222-2222-222222222222",
                          "encryption": "none"
                        }
                      ]
                    }
                  ]
                },
                "streamSettings": {
                  "network": "tcp",
                  "security": "tls",
                  "tlsSettings": {
                    "serverName": "de.example.com"
                  }
                }
              }
            ]
        """.trimIndent()

        val parsed = SubscriptionParser.parse(input, sourceType = SourceType.SUBSCRIPTION_URL)

        assertEquals(2, parsed.profiles.size)
        assertEquals("Самый Быстрый АВТО", parsed.profiles[0].displayName)
        assertEquals("vless", parsed.profiles[0].protocol)
        assertEquals("xhttp", parsed.profiles[0].transport)
        assertEquals("reality", parsed.profiles[0].security)
        assertEquals("PUBLICKEY123", parsed.profiles[0].publicKey)
        assertEquals("Германия", parsed.profiles[1].displayName)
    }

    @Test
    fun `parses base64 subscription with missing padding`() {
        val payload = "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#No%20Padding"
        val encodedWithoutPadding = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))

        val parsed = SubscriptionParser.parse(encodedWithoutPadding, sourceType = SourceType.SUBSCRIPTION_URL)

        assertEquals(1, parsed.profiles.size)
        assertEquals("No Padding", parsed.profiles.single().displayName)
    }

    @Test
    fun `parses clash yaml supported proxies`() {
        val input = """
            proxies:
              - name: "🇳🇱 Clash VLESS"
                type: vless
                server: clash.example.com
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                network: ws
                tls: true
                servername: edge.example.com
                client-fingerprint: chrome
                ws-opts:
                  path: /socket
                  headers:
                    Host: cdn.example.com
              - name: "Trojan Clash"
                type: trojan
                server: trojan-clash.example.com
                port: 443
                password: supersecret
                sni: discord.com
        """.trimIndent()

        val parsed = SubscriptionParser.parse(input, sourceType = SourceType.SUBSCRIPTION_URL)

        assertEquals(2, parsed.profiles.size)
        assertEquals("vless", parsed.profiles[0].protocol)
        assertEquals("ws", parsed.profiles[0].transport)
        assertEquals("tls", parsed.profiles[0].security)
        assertEquals("edge.example.com", parsed.profiles[0].sni)
        assertEquals("cdn.example.com", parsed.profiles[0].hostHeader)
        assertEquals("/socket", parsed.profiles[0].path)
        assertEquals("trojan", parsed.profiles[1].protocol)
    }

    @Test
    fun `parses sing box json supported outbounds`() {
        val input = """
            {
              "outbounds": [
                {
                  "type": "vless",
                  "tag": "Sing Reality",
                  "server": "sing.example.com",
                  "server_port": 8443,
                  "uuid": "11111111-1111-1111-1111-111111111111",
                  "flow": "xtls-rprx-vision",
                  "tls": {
                    "enabled": true,
                    "server_name": "www.google.com",
                    "utls": { "fingerprint": "chrome" },
                    "reality": {
                      "enabled": true,
                      "public_key": "PUBLICKEY123",
                      "short_id": "ab12"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = SubscriptionParser.parse(input, sourceType = SourceType.SUBSCRIPTION_URL)
        val profile = parsed.profiles.single()

        assertEquals("vless", profile.protocol)
        assertEquals("reality", profile.security)
        assertEquals("Sing Reality", profile.displayName)
        assertEquals("PUBLICKEY123", profile.publicKey)
        assertEquals("ab12", profile.shortId)
        assertEquals("www.google.com", profile.sni)
        assertEquals("chrome", profile.fingerprint)
    }

    @Test
    fun `unknown provider parser returns useful warnings without crashing`() {
        val parsed = SubscriptionParser.parseUnknownProviderSubscription(
            "hysteria2://password@example.com:443?sni=edge.example.com#HY2"
        )

        assertEquals(0, parsed.profiles.size)
        assertTrue(parsed.warnings.any { it.contains("Unsupported subscription format", ignoreCase = true) })
        assertTrue(parsed.warnings.any { it.contains("hysteria2", ignoreCase = true) })
    }
}
