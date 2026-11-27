package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeColumnLabel
import com.nendo.argosy.domain.model.HomeColumnTheme
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeMediaColumnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SLOTS = 5

class HomeColumnsStateTest {

    private fun game(id: Long) = HomeGameUi(
        id = id,
        title = "Game $id",
        platformId = 1L,
        platformSlug = "snes",
        platformDisplayName = "Super Nintendo",
        platformShortName = "SNES",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = true
    )

    private fun media(id: String) = HomeMediaUi(itemId = id, title = "Show $id", subtitle = null, posterUrl = "")

    private fun theme(id: String, vararg gameIds: Long) = HomeColumnUi(
        id = id,
        label = HomeColumnLabel.Theme(HomeColumnTheme.CONTINUE),
        items = gameIds.map { HomeRowItem.Game(game(it)) }
    )

    private fun platform(id: String, vararg gameIds: Long) = HomeColumnUi(
        id = id,
        label = HomeColumnLabel.Platform(id, id),
        items = gameIds.map { HomeRowItem.Game(game(it)) }
    )

    private fun mediaColumn(id: String, vararg itemIds: String) = HomeColumnUi(
        id = id,
        label = HomeColumnLabel.Media(HomeMediaColumnKind.NEXT_UP),
        items = itemIds.map { HomeRowItem.Media(media(it)) }
    )

    private val loop = listOf(
        theme("t1", 1, 2, 3),
        theme("t2", 4),
        theme("t3", 5),
        platform("p1", 6, 7),
        platform("p2", 8),
        mediaColumn("m1", "a", "b")
    )

    private fun state(columns: List<HomeColumnUi> = loop, position: Int = 0) =
        ColumnsState(columns = columns, position = position, slotCount = SLOTS, isLoaded = true)

    private val progress = ColumnsGameProgress(
        gameId = 1L,
        weeks = List(14) { 0f },
        lastPlayedAt = null,
        lastPlayedDevice = null
    )

    @Test
    fun `progress loaded for one game is not shown for another`() {
        val withProgress = state().copy(progress = progress)

        assertEquals(progress, withProgress.progressFor(1L))
        assertNull(withProgress.progressFor(2L))
        assertNull(withProgress.progressFor(null))
    }

    @Test
    fun `a rebuild keeps the loaded progress`() {
        val rebuilt = state().copy(progress = progress).rebuiltWith(loop)

        assertEquals(progress, rebuilt.progress)
    }

    @Test
    fun `right past the last column wraps to the first`() {
        val last = state(position = loop.lastIndex)

        val moved = last.movedBy(1)!!

        assertEquals(0, moved.focusIndex)
        assertEquals(loop.size, moved.position)
    }

    @Test
    fun `left past the first column wraps to the last`() {
        val moved = state().movedBy(-1)!!

        assertEquals(loop.lastIndex, moved.focusIndex)
        assertEquals(-1, moved.position)
    }

    @Test
    fun `fewer columns than slots do not wrap at either end`() {
        val short = state(columns = loop.take(SLOTS - 1))

        assertFalse(short.wraps)
        assertNull(short.movedBy(-1))
        assertNull(short.copy(position = SLOTS - 2).movedBy(1))
        assertEquals(1, short.movedBy(1)!!.focusIndex)
    }

    @Test
    fun `up and down step games inside the focused column and stop at its ends`() {
        val start = state()

        val down = start.steppedBy(1)!!
        assertEquals(2L, (down.focusedItem as HomeRowItem.Game).game.id)
        val bottom = down.steppedBy(1)!!
        assertEquals(3L, (bottom.focusedItem as HomeRowItem.Game).game.id)

        assertNull(bottom.steppedBy(1))
        assertNull(start.steppedBy(-1))
    }

    @Test
    fun `each column keeps its own game index`() {
        val stepped = state().steppedBy(1)!!.movedBy(1)!!.movedBy(-1)!!

        assertEquals(2L, (stepped.focusedItem as HomeRowItem.Game).game.id)
    }

    @Test
    fun `the next trigger jumps to the first column of the next run`() {
        val fromThemes = state(position = 1).jumpedRun(1)!!
        assertEquals(3, fromThemes.focusIndex)

        val fromPlatforms = fromThemes.jumpedRun(1)!!
        assertEquals(5, fromPlatforms.focusIndex)
    }

    @Test
    fun `the next trigger on the media run wraps to the themes`() {
        val wrapped = state(position = 5).jumpedRun(1)!!

        assertEquals(0, wrapped.focusIndex)
        assertEquals(6, wrapped.position)
    }

    @Test
    fun `the previous trigger jumps to the first column of the previous run`() {
        val fromMedia = state(position = 5).jumpedRun(-1)!!
        assertEquals(3, fromMedia.focusIndex)

        val fromPlatforms = state(position = 4).jumpedRun(-1)!!
        assertEquals(0, fromPlatforms.focusIndex)
    }

    @Test
    fun `the previous trigger on the themes wraps to the media run`() {
        val wrapped = state(position = 2).jumpedRun(-1)!!

        assertEquals(5, wrapped.focusIndex)
        assertEquals(-1, wrapped.position)
    }

    @Test
    fun `a rebuild keeps focus on the same column id and the same game`() {
        val focused = state(position = 3).steppedBy(1)!!
        val reordered = listOf(platform("p1", 9, 7, 6)) + loop.filterNot { it.id == "p1" }

        val rebuilt = focused.rebuiltWith(reordered)

        assertEquals("p1", rebuilt.focusedColumn?.id)
        assertEquals(7L, (rebuilt.focusedItem as HomeRowItem.Game).game.id)
    }

    @Test
    fun `focusedGame answers from the Columns cursor`() {
        val ui = HomeUiState(
            layoutKind = HomeLayoutKind.COLUMNS,
            columns = state(position = 3).steppedBy(1)!!
        )

        assertEquals(7L, ui.focusedGame?.id)
        assertNull(ui.focusedMedia)
        assertFalse(ui.isMediaRow)
    }

    @Test
    fun `a media column answers through focusedMedia`() {
        val ui = HomeUiState(layoutKind = HomeLayoutKind.COLUMNS, columns = state(position = 5))

        assertEquals("a", ui.focusedMedia?.itemId)
        assertNull(ui.focusedGame)
        assertTrue(ui.isMediaRow)
    }
}
