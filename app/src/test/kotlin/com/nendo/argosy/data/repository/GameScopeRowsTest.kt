package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameChangeDao
import com.nendo.argosy.data.local.dao.GameScopeTally
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GameScopeRowsTest {

    private fun item(id: Long, sortTitle: String, source: GameSource = GameSource.LOCAL_ONLY, playTime: Int = 0) =
        GameListItem(
            id = id, platformId = 1, platformSlug = "nes", title = sortTitle, sortTitle = sortTitle,
            localPath = null, source = source, isFavorite = false, isHidden = false, isMultiDisc = false,
            rommId = null, steamAppId = null, packageName = null, steamLauncher = null, playCount = 0,
            playTimeMinutes = playTime, lastPlayed = null, genre = null, players = null, rating = null,
            userRating = 0, userDifficulty = 0, releaseYear = null, addedAt = Instant.EPOCH,
            achievementCount = 0, earnedAchievementCount = 0, completion = 0, status = null,
            developer = null, igdbId = null, timeToBeatMainSec = null
        )

    private val alpha = item(1, "Alpha")
    private val bravo = item(2, "Bravo")
    private val charlie = item(3, "Charlie")
    private val initial = listOf(alpha, bravo, charlie)

    private fun patch(current: List<GameListItem>, vararg changes: GameRowChange<GameListItem>) =
        patchGameRows(current, changes.toList(), GameListItem::id, GAME_LIST_TITLE_ORDER)

    private fun ids(rows: List<GameListItem>) = rows.map { it.id }

    private fun tally(rows: List<GameListItem>) = tallyOf(rows, GameListItem::id)

    @Test
    fun `an updated row replaces its old copy in place`() {
        val played = bravo.copy(playTimeMinutes = 30)
        val next = patch(initial, GameRowChange(2, played))
        assertEquals(listOf(alpha, played, charlie), next)
    }

    @Test
    fun `an updated row whose title moved is placed by the scope order`() {
        val renamed = alpha.copy(sortTitle = "Delta")
        assertEquals(listOf(2L, 3L, 1L), ids(patch(initial, GameRowChange(1, renamed))))
    }

    @Test
    fun `a row entering the scope is inserted in order`() {
        val entering = item(9, "Bz")
        assertEquals(listOf(1L, 2L, 9L, 3L), ids(patch(initial, GameRowChange(9, entering))))
    }

    @Test
    fun `a row leaving the scope is removed`() {
        assertEquals(listOf(1L, 3L), ids(patch(initial, GameRowChange<GameListItem>(2, null))))
    }

    @Test
    fun `stamped rows that did not change and rows outside the scope keep the same list`() {
        assertSame(initial, patch(initial, GameRowChange(2, bravo), GameRowChange<GameListItem>(7, null)))
        assertSame(initial, patch(initial))
    }

    @Test
    fun `the latest change for an id wins`() {
        val first = bravo.copy(playTimeMinutes = 1)
        val second = bravo.copy(playTimeMinutes = 2)
        assertEquals(listOf(alpha, second, charlie), patch(initial, GameRowChange(2, first), GameRowChange(2, second)))
    }

    @Test
    fun `without an order changed rows move to the end`() {
        val played = alpha.copy(playTimeMinutes = 5)
        val next = patchGameRows(initial, listOf(GameRowChange(1, played)), GameListItem::id, null)
        assertEquals(listOf(bravo, charlie, played), next)
    }

    @Test
    fun `favorites order puts server-only rows last`() {
        val remote = item(5, "Aardvark", source = GameSource.ROMM_REMOTE)
        val next = patchGameRows(initial, listOf(GameRowChange(5, remote)), GameListItem::id, GAME_LIST_FAVORITES_ORDER)
        assertEquals(listOf(1L, 2L, 3L, 5L), ids(next))
    }

    @Test
    fun `a deleted row shows in the tally and retaining present ids removes it`() {
        val dbTally = tally(listOf(alpha, charlie))
        assertTrue(tally(initial) != dbTally)
        val retained = retainGameRows(initial, setOf(1L, 3L), GameListItem::id)
        assertEquals(dbTally, tally(retained))
        assertSame(initial, retainGameRows(initial, setOf(1L, 2L, 3L), GameListItem::id))
    }

    @Test
    fun `tally tells apart two sets of the same size and id sum`() {
        assertTrue(tally(listOf(item(1, "a"), item(4, "b"))) != tally(listOf(item(2, "a"), item(3, "b"))))
    }

    @Test
    fun `binary collation orders by code point, not by UTF-16 unit`() {
        val fullwidthTilde = Char(0xFF5E).toString()
        val emoji = String(Character.toChars(0x1F600))
        assertTrue(fullwidthTilde.compareTo(emoji) > 0)
        assertTrue(compareBinaryCollation(fullwidthTilde, emoji) < 0)
        assertTrue(compareBinaryCollation("Ab", "Abc") < 0)
        assertTrue(compareBinaryCollation("B", "a") < 0)
        assertEquals(0, compareBinaryCollation("same", "same"))
    }

    private class FakeSource(var full: List<GameListItem>) : GameScopeSource<GameListItem> {
        var delta: GameScopeDelta<GameListItem>? = null
        var loads = 0
        override val order: Comparator<in GameListItem> = GAME_LIST_TITLE_ORDER
        override fun idOf(row: GameListItem): Long = row.id
        override suspend fun load(): List<GameListItem> {
            loads++
            return full
        }
        override suspend fun changesSince(seq: Long): GameScopeDelta<GameListItem> = checkNotNull(delta)
    }

    private fun delta(tallyRows: List<GameListItem>, maxSeq: Long, vararg changes: GameRowChange<GameListItem>) =
        GameScopeDelta(changes.toList(), tally(tallyRows), maxSeq)

    @Test
    fun `the cursor loads once, then patches updates, scope exits and deletes`() = runTest {
        val dao = mockk<GameChangeDao>()
        coEvery { dao.maxChangeSeq() } returns 3
        val source = FakeSource(initial)
        val cursor = GameScopeCursor(source, dao)
        assertSame(initial, cursor.current())

        val renamed = bravo.copy(sortTitle = "Delta")
        source.delta = delta(listOf(alpha, charlie, renamed), 4, GameRowChange(2, renamed))
        assertEquals(listOf(1L, 3L, 2L), ids(cursor.current()))

        source.delta = delta(listOf(alpha, renamed), 5, GameRowChange<GameListItem>(3, null))
        assertEquals(listOf(1L, 2L), ids(cursor.current()))

        coEvery { dao.getAllIds() } returns listOf(2L, 3L)
        source.delta = delta(listOf(renamed), 6)
        assertEquals(listOf(2L), ids(cursor.current()))
        assertEquals(1, source.loads)
    }

    @Test
    fun `the cursor reloads when the tally still disagrees after dropping deleted ids`() = runTest {
        val dao = mockk<GameChangeDao>()
        coEvery { dao.maxChangeSeq() } returns 3
        coEvery { dao.getAllIds() } returns listOf(1L, 2L, 3L, 4L)
        val source = FakeSource(initial)
        val cursor = GameScopeCursor(source, dao)
        cursor.current()

        val missed = item(4, "Echo")
        source.full = initial + missed
        source.delta = delta(initial + missed, 4)
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(cursor.current()))
        assertEquals(2, source.loads)
    }

    @Test
    fun `the cursor reloads when the database sequence went backwards`() = runTest {
        val dao = mockk<GameChangeDao>()
        coEvery { dao.maxChangeSeq() } returns 10
        val source = FakeSource(initial)
        val cursor = GameScopeCursor(source, dao)
        cursor.current()

        source.delta = GameScopeDelta(emptyList(), GameScopeTally(3, 6, 14), maxSeq = 2)
        cursor.current()
        assertEquals(2, source.loads)
    }
}
