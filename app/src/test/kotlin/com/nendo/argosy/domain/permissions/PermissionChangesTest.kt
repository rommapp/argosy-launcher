package com.nendo.argosy.domain.permissions

import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.PermissionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionChangesTest {

    private val usage = ArgosyPermission.USAGE_ACCESS
    private val overlay = ArgosyPermission.DISPLAY_OVERLAY

    private fun snapshot(vararg missing: ArgosyPermission): PermissionSnapshot = PermissionSnapshot(
        availability = ArgosyPermission.entries.associateWith {
            if (it in missing) PermissionAvailability.MISSING else PermissionAvailability.GRANTED
        }
    )

    private val allGranted = snapshot()

    private fun recordedAllGranted() = PermissionChanges.acknowledge(allGranted)

    @Test
    fun `an install with nothing recorded records the current state silently`() {
        val review = PermissionChanges.review(PermissionRecord(), snapshot(usage), saveSyncEnabled = true)

        assertTrue(review.revoked.isEmpty())
        assertFalse(review.turnSaveSyncOff)
        assertEquals(allGranted.grantedTracked - usage, review.record.acknowledged)
    }

    @Test
    fun `granted to revoked shows the change`() {
        val review = PermissionChanges.review(recordedAllGranted(), snapshot(overlay), saveSyncEnabled = false)

        assertEquals(listOf(overlay), review.revoked)
        assertEquals(listOf(overlay), review.unnoticed)
    }

    @Test
    fun `revoked to granted is recorded silently`() {
        val record = PermissionChanges.acknowledge(snapshot(overlay))

        val review = PermissionChanges.review(record, allGranted, saveSyncEnabled = false)

        assertTrue(review.revoked.isEmpty())
        assertTrue(overlay in review.record.acknowledged.orEmpty())
    }

    @Test
    fun `an acknowledged revocation is not shown again`() {
        val acknowledged = PermissionChanges.acknowledge(snapshot(overlay))

        val review = PermissionChanges.review(acknowledged, snapshot(overlay), saveSyncEnabled = false)

        assertTrue(review.revoked.isEmpty())
    }

    @Test
    fun `revoking again after an acknowledged re-grant shows again`() {
        val acknowledged = PermissionChanges.acknowledge(snapshot(overlay))
        val regranted = PermissionChanges.review(acknowledged, allGranted, saveSyncEnabled = false).record

        val review = PermissionChanges.review(regranted, snapshot(overlay), saveSyncEnabled = false)

        assertEquals(listOf(overlay), review.revoked)
    }

    @Test
    fun `a noticed revocation posts its notice once but stays unacknowledged for the launch sheet`() {
        val first = PermissionChanges.review(recordedAllGranted(), snapshot(overlay), saveSyncEnabled = false)
        val second = PermissionChanges.review(first.noticedRecord, snapshot(overlay), saveSyncEnabled = false)

        assertEquals(listOf(overlay), second.revoked)
        assertTrue(second.unnoticed.isEmpty())
    }

    @Test
    fun `a notice comes back after the permission returns and is revoked again`() {
        val noticed = PermissionChanges.review(recordedAllGranted(), snapshot(overlay), saveSyncEnabled = false).noticedRecord
        val regranted = PermissionChanges.review(noticed, allGranted, saveSyncEnabled = false).record

        val review = PermissionChanges.review(regranted, snapshot(overlay), saveSyncEnabled = false)

        assertEquals(listOf(overlay), review.unnoticed)
    }

    @Test
    fun `revoking a sync-required permission turns save sync off`() {
        val review = PermissionChanges.review(recordedAllGranted(), snapshot(usage), saveSyncEnabled = true)

        assertTrue(review.turnSaveSyncOff)
    }

    @Test
    fun `revoking a recommended permission leaves save sync alone`() {
        val review = PermissionChanges.review(recordedAllGranted(), snapshot(overlay), saveSyncEnabled = true)

        assertFalse(review.turnSaveSyncOff)
    }

    @Test
    fun `granting the permission back never turns save sync on again`() {
        val revoked = PermissionChanges.review(recordedAllGranted(), snapshot(usage), saveSyncEnabled = true)
        val regranted = PermissionChanges.review(revoked.record, allGranted, saveSyncEnabled = false)

        assertFalse(regranted.turnSaveSyncOff)
        assertTrue(regranted.revoked.isEmpty())
    }

    @Test
    fun `an unavailable permission is never reported as revoked`() {
        val current = PermissionSnapshot(
            availability = allGranted.availability + (usage to PermissionAvailability.UNAVAILABLE)
        )

        val review = PermissionChanges.review(recordedAllGranted(), current, saveSyncEnabled = true)

        assertTrue(review.revoked.isEmpty())
        assertFalse(review.turnSaveSyncOff)
    }

    @Test
    fun `a change made from Argosy's own page is taken as acknowledged`() {
        val review = PermissionChanges.review(
            recordedAllGranted(),
            snapshot(overlay),
            saveSyncEnabled = false,
            expected = setOf(overlay)
        )

        assertTrue(review.revoked.isEmpty())
        assertFalse(overlay in review.record.acknowledged.orEmpty())
    }

    @Test
    fun `other tier permissions are never tracked`() {
        val review = PermissionChanges.review(
            recordedAllGranted(),
            snapshot(ArgosyPermission.SCREEN_CAPTURE),
            saveSyncEnabled = false
        )

        assertTrue(review.revoked.isEmpty())
    }
}
