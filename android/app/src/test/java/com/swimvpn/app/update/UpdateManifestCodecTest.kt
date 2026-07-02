package com.swimvpn.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateManifestCodecTest {

    private val valid = """
        {
          "latestVersionCode": 14,
          "versionName": "1.0.13",
          "apkUrl": "https://app.swimvpn.pro/downloads/swimvpn.apk",
          "sha256": "abc123def456",
          "minSupportedCode": 1,
          "changelog": { "ru": "быстрее", "fr": "plus rapide", "en": "faster" }
        }
    """.trimIndent()

    @Test
    fun `decodes a valid manifest`() {
        val m = UpdateManifestCodec.decode(valid)
        assertNotNull(m)
        assertEquals(14, m!!.latestVersionCode)
        assertEquals("1.0.13", m.versionName)
        assertEquals("https://app.swimvpn.pro/downloads/swimvpn.apk", m.apkUrl)
        assertEquals("abc123def456", m.sha256)
        assertEquals(1, m.minSupportedCode)
        assertEquals("plus rapide", m.changelogFor("fr"))
    }

    @Test
    fun `null and blank input decode to null`() {
        assertNull(UpdateManifestCodec.decode(null))
        assertNull(UpdateManifestCodec.decode(""))
        assertNull(UpdateManifestCodec.decode("   "))
    }

    @Test
    fun `garbage input decodes to null and never throws`() {
        assertNull(UpdateManifestCodec.decode("not json at all"))
        assertNull(UpdateManifestCodec.decode("{\"latestVersionCode\": \"NaN\"}"))
        assertNull(UpdateManifestCodec.decode("[]"))
        assertNull(UpdateManifestCodec.decode("42"))
    }

    @Test
    fun `manifest missing required fields decodes to null`() {
        assertNull(UpdateManifestCodec.decode("{}"))
        assertNull(UpdateManifestCodec.decode("""{"latestVersionCode": 14}"""))
        assertNull(
            UpdateManifestCodec.decode(
                """{"latestVersionCode": 14, "versionName": "1.0.13", "apkUrl": "https://x/y.apk"}"""
            )
        )
    }

    @Test
    fun `non positive version code decodes to null`() {
        val zero = valid.replace("\"latestVersionCode\": 14", "\"latestVersionCode\": 0")
        assertNull(UpdateManifestCodec.decode(zero))
    }

    @Test
    fun `non https apk url is refused`() {
        val http = valid.replace("https://app.swimvpn.pro", "http://app.swimvpn.pro")
        assertNull(UpdateManifestCodec.decode(http))
    }

    @Test
    fun `missing changelog is tolerated`() {
        val noLog = """
            {
              "latestVersionCode": 14,
              "versionName": "1.0.13",
              "apkUrl": "https://app.swimvpn.pro/downloads/swimvpn.apk",
              "sha256": "abc",
              "minSupportedCode": 1
            }
        """.trimIndent()
        val m = UpdateManifestCodec.decode(noLog)
        assertNotNull(m)
        assertEquals("", m!!.changelogFor("fr"))
    }

    @Test
    fun `changelog falls back en then fr then ru then any`() {
        fun manifest(log: String) = UpdateManifestCodec.decode(
            """
            {
              "latestVersionCode": 14, "versionName": "1.0.13",
              "apkUrl": "https://x/y.apk", "sha256": "abc", "minSupportedCode": 1,
              "changelog": $log
            }
            """.trimIndent()
        )!!
        assertEquals("EN", manifest("""{"en": "EN", "ru": "RU"}""").changelogFor("fr"))
        assertEquals("RU", manifest("""{"ru": "RU"}""").changelogFor("fr"))
        assertEquals("DE", manifest("""{"de": "DE"}""").changelogFor("fr"))
    }

    @Test
    fun `unknown extra fields are ignored`() {
        val extra = valid.replace("\"minSupportedCode\": 1,", "\"minSupportedCode\": 1, \"futureField\": {\"x\": 1},")
        assertNotNull(UpdateManifestCodec.decode(extra))
    }
}
