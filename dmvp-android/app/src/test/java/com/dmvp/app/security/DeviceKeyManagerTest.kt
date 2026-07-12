package com.dmvp.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceKeyManagerTest {

    @Test
    fun resolveTrustTierForHardwareStatus_hardwareBacked_returnsTierA() {
        assertEquals("TIER_A", DeviceKeyManager.resolveTrustTierForHardwareStatus(true))
    }

    @Test
    fun resolveTrustTierForHardwareStatus_softwareBacked_returnsTierC() {
        assertEquals("TIER_C", DeviceKeyManager.resolveTrustTierForHardwareStatus(false))
    }

    @Test
    fun resolveTrustTierForHardwareStatus_missingOrError_returnsTierD() {
        assertEquals("TIER_D", DeviceKeyManager.resolveTrustTierForHardwareStatus(null))
    }
}
