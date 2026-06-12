package com.nendo.argosy.data.sync

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class SyncQueueManagerTest {

    private fun info(gameId: Long) = ConflictInfo(
        gameId = gameId,
        gameName = "Test",
        channelName = null,
        localTimestamp = Instant.parse("2026-05-16T12:00:00Z"),
        serverTimestamp = Instant.parse("2026-05-16T13:00:00Z"),
        isHashConflict = false
    )

    @Test
    fun `awaitResolution consumes the resolution so the next call blocks`() = runTest {
        val manager = SyncQueueManager()

        manager.addConflict(info(1L))
        manager.resolveConflict(1L, ConflictResolution.KEEP_SERVER)
        assertEquals(ConflictResolution.KEEP_SERVER, manager.awaitResolution(1L))

        manager.addConflict(info(1L))
        try {
            withTimeout(50) { manager.awaitResolution(1L) }
            fail("Second awaitResolution should block until a fresh resolution is provided")
        } catch (_: Exception) {
        }

        manager.resolveConflict(1L, ConflictResolution.KEEP_LOCAL)
        assertEquals(ConflictResolution.KEEP_LOCAL, manager.awaitResolution(1L))
    }

    @Test
    fun `previous SKIP does not leak into a later conflict for the same game`() = runTest {
        val manager = SyncQueueManager()

        manager.addConflict(info(7L))
        manager.resolveConflict(7L, ConflictResolution.SKIP)
        assertEquals(ConflictResolution.SKIP, manager.awaitResolution(7L))

        manager.addConflict(info(7L))
        val deferred = async { manager.awaitResolution(7L) }
        manager.resolveConflict(7L, ConflictResolution.KEEP_SERVER)
        assertEquals(ConflictResolution.KEEP_SERVER, deferred.await())
    }

    @Test
    fun `resolutions for different games are independent`() = runTest {
        val manager = SyncQueueManager()

        manager.addConflict(info(1L))
        manager.addConflict(info(2L))
        manager.resolveConflict(1L, ConflictResolution.KEEP_LOCAL)
        manager.resolveConflict(2L, ConflictResolution.KEEP_SERVER)

        assertEquals(ConflictResolution.KEEP_LOCAL, manager.awaitResolution(1L))
        assertEquals(ConflictResolution.KEEP_SERVER, manager.awaitResolution(2L))
    }
}
