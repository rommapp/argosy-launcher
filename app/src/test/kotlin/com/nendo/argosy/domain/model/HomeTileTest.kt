package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTileTest {

    private fun tile(
        id: Long,
        column: Int,
        row: Int,
        columnSpan: Int = 1,
        rowSpan: Int = 1
    ) = HomeTile(
        id = id,
        pageIndex = 0,
        rect = TileRect(column, row, columnSpan, rowSpan),
        target = HomeTileTargetRef.Game(id)
    )

    @Test
    fun `tiles that fit keep their span`() {
        val placement = placeTiles(
            listOf(tile(1, 0, 0, columnSpan = 2), tile(2, 2, 0)),
            columns = 3,
            rows = 3
        )

        assertEquals(2, placement.placed.size)
        assertTrue(placement.displaced.isEmpty())
        assertEquals(2, placement.placed.first().rect.columnSpan)
    }

    @Test
    fun `a span that no longer fits the page is trimmed rather than dropped`() {
        val placement = placeTiles(listOf(tile(1, 0, 0, columnSpan = 3)), columns = 2, rows = 2)

        assertEquals(1, placement.placed.size)
        assertEquals(2, placement.placed.single().rect.columnSpan)
        assertTrue(placement.displaced.isEmpty())
    }

    @Test
    fun `a span is trimmed to clear a tile already holding the cells`() {
        val placement = placeTiles(
            listOf(tile(1, 0, 0), tile(2, 1, 0, columnSpan = 3)),
            columns = 4,
            rows = 2
        )

        assertEquals(2, placement.placed.size)
        assertEquals(3, placement.placed.last().rect.columnSpan)
    }

    @Test
    fun `only a taken anchor displaces a tile`() {
        val placement = placeTiles(
            listOf(tile(1, 0, 0, columnSpan = 2), tile(2, 1, 0)),
            columns = 3,
            rows = 1
        )

        assertEquals(listOf(1L), placement.placed.map { it.id })
        assertEquals(listOf(2L), placement.displaced.map { it.id })
    }

    @Test
    fun `an anchor outside the page is displaced rather than moved`() {
        val placement = placeTiles(listOf(tile(1, 5, 0)), columns = 3, rows = 3)

        assertTrue(placement.placed.isEmpty())
        assertEquals(listOf(1L), placement.displaced.map { it.id })
    }

    @Test
    fun `covers reports every cell under a spanning tile`() {
        val rect = TileRect(1, 1, columnSpan = 2, rowSpan = 2)

        assertTrue(rect.covers(1, 1))
        assertTrue(rect.covers(2, 2))
        assertTrue(!rect.covers(0, 1))
        assertTrue(!rect.covers(3, 1))
    }
}
