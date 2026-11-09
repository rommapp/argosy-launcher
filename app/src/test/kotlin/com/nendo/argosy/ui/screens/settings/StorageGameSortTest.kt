package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.usecase.storage.GameStorageBreakdown
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class StorageGameSortTest {

    private fun game(id: Long, bytes: Long, lastPlayed: Instant?) = GameStorageBreakdown(
        gameId = id,
        title = "game $id",
        source = GameSource.ROMM_SYNCED,
        buckets = emptyList(),
        totalBytes = bytes,
        unsyncedSaves = 0,
        lastPlayed = lastPlayed
    )

    private val recentSmall = game(1, 10L, Instant.parse("2026-11-01T00:00:00Z"))
    private val neverBig = game(2, 500L, null)
    private val oldMid = game(3, 200L, Instant.parse("2025-01-01T00:00:00Z"))
    private val neverSmall = game(4, 5L, null)
    private val loadOrder = listOf(recentSmall, neverBig, oldMid, neverSmall)

    private fun ids(sort: StorageGameSort) = sort.order(loadOrder).map { it.gameId }

    @Test
    fun `size puts the largest game first`() {
        assertEquals(listOf(2L, 3L, 1L, 4L), ids(StorageGameSort.SIZE))
    }

    @Test
    fun `title keeps the title order the list loaded in`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(StorageGameSort.TITLE))
    }

    @Test
    fun `least recently played puts never played games first, larger ones ahead, then the oldest play`() {
        assertEquals(listOf(2L, 4L, 3L, 1L), ids(StorageGameSort.LEAST_RECENTLY_PLAYED))
    }

    @Test
    fun `cycling the sort wraps back to size`() {
        assertEquals(StorageGameSort.TITLE, StorageGameSort.SIZE.next())
        assertEquals(StorageGameSort.LEAST_RECENTLY_PLAYED, StorageGameSort.TITLE.next())
        assertEquals(StorageGameSort.SIZE, StorageGameSort.LEAST_RECENTLY_PLAYED.next())
    }
}
