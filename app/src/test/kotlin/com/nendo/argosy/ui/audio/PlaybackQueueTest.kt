package com.nendo.argosy.ui.audio

import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackQueueTest {

    private fun track(n: Int) = MusicQueueTrack(
        id = "t$n",
        source = MusicTrackSource.Local("/music/$n.ogg"),
        title = "Track $n"
    )

    private val source = (1..8).map { track(it) }

    private fun queue(): PlaybackQueue = PlaybackQueue(Random(42)).apply { load(source) }

    private fun PlaybackQueue.ids() = order.map { it.id }

    @Test
    fun `load keeps source order and starts at the first track`() {
        val q = queue()
        assertEquals(source.map { it.id }, q.ids())
        assertEquals("t1", q.current?.id)
    }

    @Test
    fun `append adds tracks after the existing ones and keeps the current track`() {
        val q = queue()
        q.advance()
        q.append(listOf(track(9), track(10)))
        assertEquals(source.map { it.id } + listOf("t9", "t10"), q.ids())
        assertEquals("t2", q.current?.id)
        assertEquals(10, q.sourceTracks.size)
    }

    @Test
    fun `append under shuffle mixes only into the unplayed part`() {
        val q = queue()
        q.setShuffle(true)
        q.advance()
        q.advance()
        val played = q.ids().take(3)
        q.append(listOf(track(9), track(10)))
        assertEquals(played, q.ids().take(3))
        assertEquals(q.ids()[2], q.current?.id)
        assertEquals((source.map { it.id } + listOf("t9", "t10")).sorted(), q.ids().sorted())
    }

    @Test
    fun `append of nothing leaves the queue alone`() {
        val q = queue()
        q.append(emptyList())
        assertEquals(source.map { it.id }, q.ids())
    }

    @Test
    fun `enabling shuffle keeps the current track playing and permutes the rest`() {
        val q = queue()
        q.advance()
        q.advance()
        assertTrue(q.setShuffle(true))
        assertEquals("t3", q.current?.id)
        assertEquals(source.map { it.id }.sorted(), q.ids().sorted())
    }

    @Test
    fun `repeating the same shuffle value is a no-op`() {
        val q = queue()
        q.setShuffle(true)
        q.advance()
        q.advance()
        val orderBefore = q.ids()
        val indexBefore = q.index

        assertFalse(q.setShuffle(true))
        assertFalse(q.setShuffle(true))

        assertEquals(orderBefore, q.ids())
        assertEquals(indexBefore, q.index)
    }

    @Test
    fun `repeating shuffle off is a no-op`() {
        val q = queue()
        q.advance()
        assertFalse(q.setShuffle(false))
        assertEquals(source.map { it.id }, q.ids())
        assertEquals(1, q.index)
    }

    @Test
    fun `turning shuffle off restores source order at the current track`() {
        val q = queue()
        q.setShuffle(true)
        q.advance()
        q.advance()
        val playing = q.current?.id

        assertTrue(q.setShuffle(false))

        assertEquals(source.map { it.id }, q.ids())
        assertEquals(playing, q.current?.id)
        assertEquals(source.indexOfFirst { it.id == playing }, q.index)
    }

    @Test
    fun `advance stops at the end and leaves the index there`() {
        val q = queue()
        repeat(source.size - 1) { assertTrue(q.advance()) }
        assertFalse(q.advance())
        assertEquals(source.lastIndex, q.index)
    }

    @Test
    fun `step back wraps from the first track to the last`() {
        val q = queue()
        q.stepBack()
        assertEquals(source.lastIndex, q.index)
        q.stepBack()
        assertEquals(source.lastIndex - 1, q.index)
    }

    @Test
    fun `previous past the first track restarts only when more than three seconds in`() {
        val q = queue()
        q.advance()
        assertEquals(PreviousAction.STEP_BACK, q.previousAction(0L))
        assertEquals(PreviousAction.STEP_BACK, q.previousAction(SKIP_PREVIOUS_RESTART_THRESHOLD_MS))
        assertEquals(PreviousAction.RESTART, q.previousAction(SKIP_PREVIOUS_RESTART_THRESHOLD_MS + 1))
    }

    @Test
    fun `previous on the first track always restarts it`() {
        val q = queue()
        assertEquals(PreviousAction.RESTART, q.previousAction(0L))
        assertEquals(PreviousAction.RESTART, q.previousAction(SKIP_PREVIOUS_RESTART_THRESHOLD_MS + 1))
    }

    @Test
    fun `only the last track in play order is at the end`() {
        val q = queue()
        assertFalse(q.isAtLast)
        repeat(source.size - 1) { q.advance() }
        assertTrue(q.isAtLast)
        val single = PlaybackQueue(Random(1)).apply { load(listOf(track(1))) }
        assertTrue(single.isAtLast)
    }

    @Test
    fun `replacing the source keeps a surviving current track`() {
        val q = queue()
        q.advance()
        q.advance()
        val updated = listOf(track(9)) + source.drop(1)

        assertTrue(q.replaceKeepingCurrent(updated))

        assertEquals("t3", q.current?.id)
        assertEquals(updated.map { it.id }, q.ids())
    }

    @Test
    fun `replacing the source without the current track restarts at the top`() {
        val q = queue()
        q.advance()
        val updated = source.filter { it.id != "t2" }

        assertFalse(q.replaceKeepingCurrent(updated))

        assertEquals(0, q.index)
        assertEquals("t1", q.current?.id)
    }

    @Test
    fun `replacing a shuffled source keeps the played order and appends new tracks`() {
        val q = queue()
        q.setShuffle(true)
        val shuffledBefore = q.ids()
        val updated = source + track(9)

        assertTrue(q.replaceKeepingCurrent(updated))

        assertEquals(shuffledBefore, q.ids().take(source.size))
        assertEquals("t9", q.ids().last())
    }

    @Test
    fun `an empty queue has no current track and ignores stepping`() {
        val q = PlaybackQueue(Random(1))
        q.stepBack()
        assertFalse(q.advance())
        assertTrue(q.isEmpty)
        assertEquals(null, q.current)
    }

    @Test
    fun `loadAt starts at the chosen track in source order`() {
        val q = PlaybackQueue(Random(42))
        q.loadAt(source, 4)
        assertEquals(source.map { it.id }, q.ids())
        assertEquals("t5", q.current?.id)
    }

    @Test
    fun `loadAt under shuffle plays the chosen track first and keeps every track`() {
        val q = PlaybackQueue(Random(42))
        q.setShuffle(true)
        q.loadAt(source, 4)
        assertEquals("t5", q.current?.id)
        assertEquals(0, q.index)
        assertEquals(source.map { it.id }.toSet(), q.ids().toSet())
        assertEquals(source.size, q.size)
    }

    @Test
    fun `loadAt with an out of range index falls back to the top`() {
        val q = PlaybackQueue(Random(42))
        q.loadAt(source, 99)
        assertEquals("t1", q.current?.id)
    }
}
