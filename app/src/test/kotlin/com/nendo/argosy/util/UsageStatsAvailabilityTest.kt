package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatsAvailabilityTest {

    @Test
    fun `granted access is available whether or not the settings screen exists`() {
        assertEquals(
            UsageStatsAvailability.AVAILABLE,
            PermissionHelper.availabilityOf(granted = true, settingsScreenExists = true)
        )
        assertEquals(
            UsageStatsAvailability.AVAILABLE,
            PermissionHelper.availabilityOf(granted = true, settingsScreenExists = false)
        )
    }

    @Test
    fun `no grant and no settings screen is unavailable on the device`() {
        assertEquals(
            UsageStatsAvailability.UNAVAILABLE_ON_DEVICE,
            PermissionHelper.availabilityOf(granted = false, settingsScreenExists = false)
        )
    }

    @Test
    fun `no grant with a settings screen is denied`() {
        assertEquals(
            UsageStatsAvailability.DENIED,
            PermissionHelper.availabilityOf(granted = false, settingsScreenExists = true)
        )
    }
}
