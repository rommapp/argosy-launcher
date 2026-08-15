package com.nendo.argosy.domain.usecase.state

import com.nendo.argosy.data.local.entity.StateCacheEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReconcileDeadServerLinksTest {

    private fun state(
        id: Long,
        slot: Int,
        rommSaveId: Long?,
        syncStatus: String? = StateCacheEntity.STATUS_SYNCED
    ) = StateCacheEntity(
        id = id,
        gameId = 1L,
        platformSlug = "snes",
        emulatorId = "retroarch",
        slotNumber = slot,
        cachedAt = Instant.EPOCH,
        stateSize = 100L,
        cachePath = "states/slot$slot.state",
        rommSaveId = rommSaveId,
        syncStatus = syncStatus,
        serverUpdatedAt = Instant.EPOCH,
        lastUploadedHash = "hash$id"
    )

    @Test
    fun `a link the server still lists is left alone`() {
        val result = reconcileDeadServerLinks(listOf(state(1, 1, 55L)), setOf(55L, 77L))

        assertFalse(result.single().linkWasDropped)
        assertEquals(55L, result.single().state.rommSaveId)
        assertEquals(StateCacheEntity.STATUS_SYNCED, result.single().state.syncStatus)
    }

    @Test
    fun `a link the server no longer lists is dropped and queued for upload`() {
        val result = reconcileDeadServerLinks(listOf(state(1, 1, 55L)), setOf(77L))

        val reconciled = result.single()
        assertTrue(reconciled.linkWasDropped)
        assertNull(reconciled.state.rommSaveId)
        assertEquals(StateCacheEntity.STATUS_PENDING_UPLOAD, reconciled.state.syncStatus)
        assertNull(reconciled.state.serverUpdatedAt)
    }

    @Test
    fun `dropping a link keeps the cached file so local progress is never discarded`() {
        val original = state(1, 1, 55L)
        val reconciled = reconcileDeadServerLinks(listOf(original), emptySet()).single().state

        assertEquals(original.cachePath, reconciled.cachePath)
        assertEquals(original.stateSize, reconciled.stateSize)
        assertEquals(original.id, reconciled.id)
    }

    @Test
    fun `an unlinked row is not treated as dead`() {
        val result = reconcileDeadServerLinks(
            listOf(state(1, 1, null, StateCacheEntity.STATUS_PENDING_UPLOAD)),
            setOf(77L)
        )

        assertFalse(result.single().linkWasDropped)
        assertEquals(StateCacheEntity.STATUS_PENDING_UPLOAD, result.single().state.syncStatus)
    }

    @Test
    fun `only the rows whose links died are touched`() {
        val result = reconcileDeadServerLinks(
            listOf(state(1, 1, 55L), state(2, 2, 66L), state(3, 3, null)),
            setOf(55L)
        )

        assertFalse(result[0].linkWasDropped)
        assertTrue(result[1].linkWasDropped)
        assertFalse(result[2].linkWasDropped)
        assertEquals(55L, result[0].state.rommSaveId)
        assertNull(result[1].state.rommSaveId)
    }

    @Test
    fun `the lastUploadedHash is cleared so the re-created upload is not skipped as unchanged`() {
        val reconciled = reconcileDeadServerLinks(listOf(state(1, 1, 55L)), emptySet()).single().state

        assertNull(reconciled.lastUploadedHash)
    }
}
