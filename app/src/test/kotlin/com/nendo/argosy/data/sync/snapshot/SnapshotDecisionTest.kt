package com.nendo.argosy.data.sync.snapshot

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotDecisionTest {

    private val a = SaveHashes("content-a", "identity-a")
    private val b = SaveHashes("content-b", "identity-b")
    private val aClockTicked = SaveHashes("content-a2", "identity-a")

    private fun point(id: Long, save: SaveHashes?) = SnapshotPoint(id, save)

    private fun chosen(id: Long, save: SaveHashes?) = SnapshotPoint(id, save, byChoice = true)

    @Test
    fun `a restored older snapshot with clean files is kept, not replaced by current`() {
        assertEquals(SnapshotAction.Nothing, SnapshotDecision.decide(chosen(39, a), point(42, b), a))
    }

    @Test
    fun `a restored older snapshot played on pushes over current with it as parent`() {
        val c = SaveHashes("content-c", "identity-c")
        assertEquals(
            SnapshotAction.Push(expectedCurrentId = 42, parentSnapshotId = 39),
            SnapshotDecision.decide(chosen(39, a), point(42, b), c)
        )
    }

    @Test
    fun `a restored snapshot newer than a stale current listing still pushes onto current with it as parent`() {
        val c = SaveHashes("content-c", "identity-c")
        assertEquals(
            SnapshotAction.Push(expectedCurrentId = 39, parentSnapshotId = 42),
            SnapshotDecision.decide(chosen(42, b), point(39, a), c)
        )
    }

    @Test
    fun `an older snapshot held without a choice still downloads current`() {
        assertEquals(SnapshotAction.Download(42), SnapshotDecision.decide(point(39, a), point(42, b), a))
    }

    @Test
    fun `holding current with clean files does nothing`() {
        assertEquals(SnapshotAction.Nothing, SnapshotDecision.decide(point(41, a), point(41, a), a))
    }

    @Test
    fun `a clock-only change is not dirty`() {
        assertEquals(SnapshotAction.Nothing, SnapshotDecision.decide(point(41, a), point(41, a), aClockTicked))
    }

    @Test
    fun `holding current with dirty files pushes expecting current with it as parent`() {
        assertEquals(SnapshotAction.Push(41, parentSnapshotId = 41), SnapshotDecision.decide(point(41, a), point(41, a), b))
    }

    @Test
    fun `holding an older snapshot with clean files downloads current`() {
        assertEquals(SnapshotAction.Download(42), SnapshotDecision.decide(point(41, a), point(42, b), a))
    }

    @Test
    fun `holding an older snapshot with dirty files is a conflict`() {
        assertEquals(SnapshotAction.Conflict(42), SnapshotDecision.decide(point(41, a), point(42, b), aClockTicked.copy(identityHash = "identity-c")))
    }

    @Test
    fun `holding nothing with no files downloads current`() {
        assertEquals(SnapshotAction.Download(42), SnapshotDecision.decide(null, point(42, b), null))
    }

    @Test
    fun `holding nothing with files equal to current adopts it`() {
        assertEquals(SnapshotAction.Adopt(42), SnapshotDecision.decide(null, point(42, b), b))
    }

    @Test
    fun `holding nothing with files different from current is a conflict`() {
        assertEquals(SnapshotAction.Conflict(42), SnapshotDecision.decide(null, point(42, b), a))
    }

    @Test
    fun `holding nothing with files against a current that has no save is a conflict`() {
        assertEquals(SnapshotAction.Conflict(42), SnapshotDecision.decide(null, point(42, null), a))
    }

    @Test
    fun `a channel with no current and files on disk pushes expecting null`() {
        assertEquals(SnapshotAction.Push(null), SnapshotDecision.decide(null, null, a))
    }

    @Test
    fun `no current and no files does nothing`() {
        assertEquals(SnapshotAction.Nothing, SnapshotDecision.decide(null, null, null))
    }

    @Test
    fun `a save missing locally is refilled from current, never pushed`() {
        assertEquals(SnapshotAction.Download(41), SnapshotDecision.decide(point(41, a), point(41, a), null))
        assertEquals(SnapshotAction.Download(42), SnapshotDecision.decide(point(41, a), point(42, b), null))
    }

    @Test
    fun `a save missing locally while holding a chosen restore refills that restore`() {
        assertEquals(SnapshotAction.Download(39), SnapshotDecision.decide(chosen(39, a), point(42, b), null))
        assertEquals(SnapshotAction.Download(42), SnapshotDecision.decide(chosen(42, b), point(42, b), null))
    }

    @Test
    fun `a missing local save against a snapshot that carries none does nothing`() {
        assertEquals(SnapshotAction.Nothing, SnapshotDecision.decide(point(41, null), point(41, null), null))
        assertEquals(SnapshotAction.Nothing, SnapshotDecision.decide(chosen(39, null), point(42, b), null))
    }

    @Test
    fun `a first save on top of a snapshot without one is dirty`() {
        assertEquals(SnapshotAction.Push(41, parentSnapshotId = 41), SnapshotDecision.decide(point(41, null), point(41, null), a))
    }
}
