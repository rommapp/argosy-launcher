package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeLayoutKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRefreshFocusTest {

    private fun game(id: Long) = HomeGameUi(
        id = id,
        title = "Game $id",
        platformId = 7,
        platformSlug = "snes",
        platformDisplayName = "SNES",
        platformShortName = "SNES",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = true,
        isRommGame = true,
        isSteamGame = false,
        needsInstall = false
    )

    private fun grid(ids: List<Long>, focusedIndex: Int) = HomeUiState(
        layoutKind = HomeLayoutKind.AUTO_GRID,
        currentRow = HomeRow.Continue,
        recentGames = ids.map(::game),
        focusedGameIndex = focusedIndex
    )

    private fun HomeUiState.relisted(ids: List<Long>, focusedId: Long?) =
        copy(recentGames = ids.map(::game)).keepingFocusOn(focusedId)

    @Test
    fun `a download finishing elsewhere in the row leaves the cursor on the highlighted game`() {
        val state = grid(listOf(1L, 2L, 3L, 4L, 5L), focusedIndex = 2)

        val after = state.relisted(listOf(5L, 1L, 2L, 3L, 4L), state.focusedGame?.id)

        assertEquals(3L, after.focusedGame?.id)
    }

    @Test
    fun `a download finishing on the highlighted game takes the cursor to the front with it`() {
        val state = grid(listOf(1L, 2L, 3L, 4L, 5L), focusedIndex = 4)

        val after = state.relisted(listOf(5L, 1L, 2L, 3L, 4L), state.focusedGame?.id)

        assertEquals(0, after.focusedGameIndex)
        assertEquals(5L, after.focusedGame?.id)
    }

    @Test
    fun `a refresh landing after the user scrolled keeps the game they scrolled to`() {
        val scrolled = grid(listOf(1L, 2L, 3L, 4L, 5L), focusedIndex = 4)

        val after = scrolled.relisted(listOf(1L, 2L, 3L, 4L, 5L), scrolled.focusedGame?.id)

        assertEquals(4, after.focusedGameIndex)
        assertEquals(5L, after.focusedGame?.id)
    }

    @Test
    fun `a highlighted game that left the row keeps the position inside the row`() {
        val state = grid(listOf(1L, 2L, 3L, 4L, 5L), focusedIndex = 4)

        val after = state.relisted(listOf(1L, 2L, 3L), state.focusedGame?.id)

        assertEquals(2, after.focusedGameIndex)
    }

    @Test
    fun `games added in front during a library scan leave the cursor on the game it was on`() {
        val state = grid(listOf(1L, 2L, 3L), focusedIndex = 0)

        val firstBatch = state.relisted(listOf(4L, 1L, 2L, 3L), state.focusedGame?.id)
        val secondBatch = firstBatch.relisted(listOf(6L, 5L, 4L, 1L, 2L, 3L), firstBatch.focusedGame?.id)

        assertEquals(1L, secondBatch.focusedGame?.id)
        assertEquals(3, secondBatch.focusedGameIndex)
    }

    @Test
    fun `a row with nothing loaded yet leaves the cursor where it is`() {
        val state = grid(listOf(1L, 2L, 3L), focusedIndex = 2)

        val after = state.relisted(emptyList(), state.focusedGame?.id)

        assertEquals(2, after.focusedGameIndex)
    }
}
