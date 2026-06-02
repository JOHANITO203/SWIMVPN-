package com.swimvpn.app.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAgentPreferenceDefaultTest {

    @Test
    fun unsetDefaultsToEnabled() {
        assertTrue(PreferencesManager.resolveAiAgentEnabled(null))
    }

    @Test
    fun defaultConstantIsEnabled() {
        assertTrue(PreferencesManager.DEFAULT_AI_AGENT_ENABLED)
    }

    @Test
    fun explicitlyDisabledIsRespected() {
        assertFalse(PreferencesManager.resolveAiAgentEnabled(false))
    }

    @Test
    fun explicitlyEnabledIsRespected() {
        assertTrue(PreferencesManager.resolveAiAgentEnabled(true))
    }
}
