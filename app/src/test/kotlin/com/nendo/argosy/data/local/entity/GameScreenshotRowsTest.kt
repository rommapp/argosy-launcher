package com.nendo.argosy.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameScreenshotRowsTest {

    private val gameId = 7L

    private fun cached(position: Int, url: String) =
        GameScreenshotEntity(gameId, position, url, "/cache/ss_g7_${position}_$url.jpg", url)

    @Test
    fun `an unchanged url keeps its cached file`() {
        val existing = listOf(cached(0, "a"), cached(1, "b"))

        val rows = planScreenshotRows(gameId, existing, listOf("a", "b"))

        assertEquals(existing, rows)
    }

    @Test
    fun `a changed url at a position starts uncached`() {
        val existing = listOf(cached(0, "a"), cached(1, "b"))

        val rows = planScreenshotRows(gameId, existing, listOf("a", "c"))

        assertEquals(listOf(cached(0, "a"), GameScreenshotEntity(gameId, 1, "c")), rows)
    }

    @Test
    fun `a shorter list drops the positions past its end`() {
        val existing = listOf(cached(0, "a"), cached(1, "b"), cached(2, "c"))

        val rows = planScreenshotRows(gameId, existing, listOf("a"))

        assertEquals(listOf(cached(0, "a")), rows)
    }

    @Test
    fun `a longer list adds uncached rows after the kept ones`() {
        val existing = listOf(cached(0, "a"))

        val rows = planScreenshotRows(gameId, existing, listOf("a", "b"))

        assertEquals(listOf(cached(0, "a"), GameScreenshotEntity(gameId, 1, "b")), rows)
    }

    @Test
    fun `a url moved to another position does not carry its cache with it`() {
        val existing = listOf(cached(0, "a"), cached(1, "b"))

        val rows = planScreenshotRows(gameId, existing, listOf("b", "a"))

        assertEquals(listOf(GameScreenshotEntity(gameId, 0, "b"), GameScreenshotEntity(gameId, 1, "a")), rows)
    }

    @Test
    fun `an empty, blank or absent list leaves no rows`() {
        val existing = listOf(cached(0, "a"))

        assertTrue(planScreenshotRows(gameId, existing, emptyList()).isEmpty())
        assertTrue(planScreenshotRows(gameId, existing, listOf("")).isEmpty())
        assertTrue(planScreenshotRows(gameId, existing, listOf(" ", "")).isEmpty())
        assertTrue(planScreenshotRows(gameId, existing, null).isEmpty())
    }

    @Test
    fun `blank entries are skipped so positions stay contiguous`() {
        val rows = planScreenshotRows(gameId, emptyList(), listOf("a", "", "b"))

        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(listOf("a", "b"), rows.map { it.sourceUrl })
    }

    @Test
    fun `a row is cached from its source only when the cached file came from that url`() {
        assertTrue(cached(0, "a").isCachedFromSource)
        assertTrue(!GameScreenshotEntity(gameId, 0, "a", "/cache/x.jpg", null).isCachedFromSource)
        assertTrue(!GameScreenshotEntity(gameId, 0, "a").isCachedFromSource)
    }
}
