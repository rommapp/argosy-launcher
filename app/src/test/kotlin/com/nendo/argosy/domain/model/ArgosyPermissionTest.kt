package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgosyPermissionTest {

    @Test
    fun `required, recommended and other tiers hold the permissions the spec lists`() {
        assertEquals(
            listOf(ArgosyPermission.ALL_FILES_ACCESS, ArgosyPermission.USAGE_ACCESS, ArgosyPermission.NOTIFICATIONS),
            ArgosyPermission.inTier(PermissionTier.REQUIRED)
        )
        assertEquals(
            listOf(
                ArgosyPermission.DISPLAY_OVERLAY,
                ArgosyPermission.ACCESSIBILITY,
                ArgosyPermission.BATTERY_UNRESTRICTED
            ),
            ArgosyPermission.inTier(PermissionTier.RECOMMENDED)
        )
        assertEquals(
            listOf(
                ArgosyPermission.WRITE_SETTINGS,
                ArgosyPermission.SCREEN_CAPTURE,
                ArgosyPermission.LIVE_WALLPAPER,
                ArgosyPermission.PRIVILEGED_ACCESS
            ),
            ArgosyPermission.inTier(PermissionTier.OTHER)
        )
    }

    @Test
    fun `save sync needs exactly the required tier and only those are tracked with the recommended`() {
        assertEquals(
            ArgosyPermission.inTier(PermissionTier.REQUIRED),
            ArgosyPermission.entries.filter { it.syncRequired }
        )
        assertTrue(ArgosyPermission.inTier(PermissionTier.RECOMMENDED).all { it.tracksChanges })
        assertTrue(ArgosyPermission.inTier(PermissionTier.OTHER).none { it.tracksChanges })
    }

    @Test
    fun `tokens are unique and read back to their permission`() {
        val tokens = ArgosyPermission.entries.map { it.token }
        assertEquals(tokens.size, tokens.toSet().size)
        ArgosyPermission.entries.forEach { assertEquals(it, ArgosyPermission.fromToken(it.token)) }
        assertEquals(null, ArgosyPermission.fromToken("camera"))
    }

    @Test
    fun `a permission without a grant screen or root route is unavailable, never missing`() {
        assertEquals(PermissionAvailability.GRANTED, PermissionAvailability.of(granted = true, grantable = false))
        assertEquals(PermissionAvailability.GRANTED, PermissionAvailability.of(granted = true, grantable = true))
        assertEquals(PermissionAvailability.MISSING, PermissionAvailability.of(granted = false, grantable = true))
        assertEquals(PermissionAvailability.UNAVAILABLE, PermissionAvailability.of(granted = false, grantable = false))
    }

    @Test
    fun `usage access with no screen on this device does not block save sync`() {
        val snapshot = PermissionSnapshot(
            availability = mapOf(
                ArgosyPermission.ALL_FILES_ACCESS to PermissionAvailability.GRANTED,
                ArgosyPermission.USAGE_ACCESS to PermissionAvailability.UNAVAILABLE,
                ArgosyPermission.NOTIFICATIONS to PermissionAvailability.GRANTED
            )
        )
        assertTrue(snapshot.missingForSaveSync.isEmpty())
        assertFalse(ArgosyPermission.USAGE_ACCESS in snapshot.grantedTracked)
    }

    @Test
    fun `a missing sync-required permission is listed for save sync`() {
        val snapshot = PermissionSnapshot(
            availability = mapOf(
                ArgosyPermission.ALL_FILES_ACCESS to PermissionAvailability.GRANTED,
                ArgosyPermission.USAGE_ACCESS to PermissionAvailability.GRANTED,
                ArgosyPermission.NOTIFICATIONS to PermissionAvailability.MISSING,
                ArgosyPermission.DISPLAY_OVERLAY to PermissionAvailability.MISSING
            )
        )
        assertEquals(listOf(ArgosyPermission.NOTIFICATIONS), snapshot.missingForSaveSync)
    }
}
