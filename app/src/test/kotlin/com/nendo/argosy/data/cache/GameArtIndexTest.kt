package com.nendo.argosy.data.cache

import com.nendo.argosy.data.local.entity.GameArtEntity
import com.nendo.argosy.data.model.ArtSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GameArtIndexTest {

    private fun row(gameId: Long, slot: ArtSlot, path: String?, seq: Long) =
        GameArtEntity(gameId = gameId, slot = slot.name, cachedPath = path, changeSeq = seq)

    private val initial = GameArtIndex.of(
        listOf(
            row(1, ArtSlot.COVER, "/c1", 1),
            row(1, ArtSlot.BACKGROUND, "/b1", 2),
            row(2, ArtSlot.COVER, "/c2", 3)
        )
    )

    @Test
    fun `initial load resolves every game and records the highest sequence`() {
        assertEquals("/c1", initial.byGame.getValue(1).coverPath)
        assertEquals("/b1", initial.byGame.getValue(1).backgroundPath)
        assertEquals("/c2", initial.byGame.getValue(2).coverPath)
        assertEquals(3, initial.rowCount)
        assertEquals(3L, initial.seq)
    }

    @Test
    fun `changed rows rebuild only their game and keep the others`() {
        val next = initial.merge(listOf(row(2, ArtSlot.COVER, "/c2b", 4)))
        assertEquals("/c2b", next.byGame.getValue(2).coverPath)
        assertSame(initial.byGame.getValue(1), next.byGame.getValue(1))
        assertEquals(4L, next.seq)
        assertEquals(3, next.rowCount)
    }

    @Test
    fun `a new game is added and counted`() {
        val next = initial.merge(listOf(row(3, ArtSlot.COVER, "/c3", 4)))
        assertEquals("/c3", next.byGame.getValue(3).coverPath)
        assertEquals(4, next.rowCount)
    }

    @Test
    fun `a game that lost a slot is rebuilt from its remaining slots`() {
        val next = initial.merge(listOf(row(1, ArtSlot.COVER, "/c1", 4)))
        assertNull(next.byGame.getValue(1).backgroundPath)
        assertEquals("/c1", next.byGame.getValue(1).coverPath)
        assertEquals(2, next.rowCount)
    }

    @Test
    fun `a game whose rows were all removed drops out`() {
        val next = initial.retainGames(setOf(1L))
        assertNull(next.byGame[2])
        assertEquals(2, next.rowCount)
        assertEquals(initial.seq, next.seq)
    }

    @Test
    fun `stamped rows with unchanged resolved art keep the same map`() {
        val next = initial.merge(listOf(row(2, ArtSlot.COVER, "/c2", 4)))
        assertSame(initial.byGame, next.byGame)
        assertEquals(4L, next.seq)
    }

    @Test
    fun `no changes and no removals return the same index`() {
        assertSame(initial, initial.merge(emptyList()))
        assertSame(initial, initial.retainGames(setOf(1L, 2L)))
    }
}
