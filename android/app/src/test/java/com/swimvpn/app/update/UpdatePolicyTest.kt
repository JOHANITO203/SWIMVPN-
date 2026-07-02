package com.swimvpn.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private fun manifest(latest: Int, minSupported: Int) = UpdateManifest(
        latestVersionCode = latest,
        versionName = "x.y.z",
        apkUrl = "https://app.swimvpn.pro/downloads/swimvpn.apk",
        sha256 = "abc",
        minSupportedCode = minSupported,
        changelog = null,
    )

    @Test
    fun `equal version is up to date`() {
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.decide(14, manifest(latest = 14, minSupported = 1)))
    }

    @Test
    fun `newer local build (dev) is up to date`() {
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.decide(99, manifest(latest = 14, minSupported = 1)))
    }

    @Test
    fun `older build gets an optional update`() {
        val d = UpdatePolicy.decide(13, manifest(latest = 14, minSupported = 1))
        assertTrue(d is UpdateDecision.Optional)
        assertEquals(14, (d as UpdateDecision.Optional).manifest.latestVersionCode)
    }

    @Test
    fun `build below the supported floor is mandatory`() {
        val d = UpdatePolicy.decide(5, manifest(latest = 14, minSupported = 10))
        assertTrue(d is UpdateDecision.Mandatory)
    }

    @Test
    fun `build exactly at the supported floor stays optional`() {
        val d = UpdatePolicy.decide(10, manifest(latest = 14, minSupported = 10))
        assertTrue(d is UpdateDecision.Optional)
    }

    @Test
    fun `floor above latest never blocks an up-to-date build`() {
        // Defensive: a mis-generated manifest (minSupported > latest) must not block current users.
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.decide(14, manifest(latest = 14, minSupported = 99)))
    }
}
