package com.nendo.argosy.ui.screens.search

import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.hardware.CompanionInGameState
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.screens.home.HomeGameUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPresentationFocusTest {

    private fun game(id: Long, title: String = "Game $id") = SearchResultUi.Game(
        key = "game:$id",
        title = title,
        gameId = id,
        card = HomeGameUi(
            id = id,
            title = title,
            platformId = 1,
            platformSlug = "snes",
            platformDisplayName = "SNES",
            platformShortName = "SNES",
            coverPath = null,
            backgroundPath = null,
            developer = null,
            releaseYear = null,
            genre = null,
            isFavorite = false,
            isDownloaded = false
        )
    )

    private fun state(
        games: Int = 0,
        history: List<String> = emptyList(),
        query: String = "zelda",
        dualScreen: Boolean = true,
        showKeyboard: Boolean = true
    ) = SearchUiState(
        query = query,
        gameResults = (1..games).map { game(it.toLong()) },
        recentSearches = history,
        gridDensity = GridDensity.NORMAL,
        showKeyboard = showKeyboard,
        dualScreen = dualScreen
    )

    private fun onCovers(games: Int, index: Int, history: List<String> = emptyList()) =
        state(games = games, history = history).copy(previewFocused = true, previewIndex = index)

    private fun onHistory(history: List<String>, index: Int, games: Int = 0) =
        state(games = games, history = history).copy(historyFocused = true, historyIndex = index)

    private fun SearchUiState.assertOnKeyboard() {
        assertFalse(previewFocused)
        assertFalse(historyFocused)
        assertTrue(showKeyboard)
        assertTrue(isFieldFocused)
    }

    @Test
    fun `up from the keyboard enters the history list at its bottom entry`() {
        val entered = state(games = 4, history = listOf("zelda", "zelda 2", "zelda 3")).enteringAboveKeyboard()!!

        assertTrue(entered.historyFocused)
        assertFalse(entered.previewFocused)
        assertEquals(2, entered.historyIndex)
    }

    @Test
    fun `up from the keyboard goes straight to the covers when no history matches`() {
        val entered = state(games = 4, history = listOf("metroid")).enteringAboveKeyboard()!!

        assertTrue(entered.previewFocused)
        assertFalse(entered.historyFocused)
    }

    @Test
    fun `up from the keyboard with nothing above is left to the keyboard`() {
        assertNull(state(games = 0).enteringAboveKeyboard())
    }

    @Test
    fun `the region above is reachable with the keyboard closed`() {
        assertTrue(state(games = 2, showKeyboard = false).enteringAboveKeyboard()!!.previewFocused)
    }

    @Test
    fun `up inside the history list moves one entry`() {
        val next = onHistory(listOf("zelda", "zelda 2"), index = 1).aboveKeyboardStep(FocusMove.UP)!!

        assertTrue(next.historyFocused)
        assertEquals(0, next.historyIndex)
    }

    @Test
    fun `up from the top history entry enters the covers`() {
        val next = onHistory(listOf("zelda"), index = 0, games = 3).aboveKeyboardStep(FocusMove.UP)!!

        assertTrue(next.previewFocused)
        assertFalse(next.historyFocused)
    }

    @Test
    fun `up from the top history entry with no covers is a boundary`() {
        assertNull(onHistory(listOf("zelda"), index = 0).aboveKeyboardStep(FocusMove.UP))
    }

    @Test
    fun `down from the bottom history entry returns to the keyboard`() {
        onHistory(listOf("zelda", "zelda 2"), index = 1).aboveKeyboardStep(FocusMove.DOWN)!!.assertOnKeyboard()
    }

    @Test
    fun `down inside the history list moves one entry`() {
        assertEquals(1, onHistory(listOf("zelda", "zelda 2"), index = 0).aboveKeyboardStep(FocusMove.DOWN)!!.historyIndex)
    }

    @Test
    fun `left and right on the history list are boundaries`() {
        val history = onHistory(listOf("zelda"), index = 0)

        assertNull(history.aboveKeyboardStep(FocusMove.LEFT))
        assertNull(history.aboveKeyboardStep(FocusMove.RIGHT))
    }

    @Test
    fun `right and left move one cover inside a row`() {
        val covers = onCovers(games = 12, index = 1)

        assertEquals(2, covers.aboveKeyboardStep(FocusMove.RIGHT)!!.previewIndex)
        assertEquals(0, covers.aboveKeyboardStep(FocusMove.LEFT)!!.previewIndex)
    }

    @Test
    fun `left at the first column and right at the last column are boundaries`() {
        assertNull(onCovers(games = 12, index = 6).aboveKeyboardStep(FocusMove.LEFT))
        assertNull(onCovers(games = 12, index = 5).aboveKeyboardStep(FocusMove.RIGHT))
    }

    @Test
    fun `up from the top cover row is a boundary`() {
        assertNull(onCovers(games = 12, index = 3).aboveKeyboardStep(FocusMove.UP))
    }

    @Test
    fun `down moves a cover row while one is below`() {
        assertEquals(9, onCovers(games = 12, index = 3).aboveKeyboardStep(FocusMove.DOWN)!!.previewIndex)
    }

    @Test
    fun `down from the bottom cover row enters the top history entry`() {
        val next = onCovers(games = 12, index = 8, history = listOf("zelda", "zelda 2")).aboveKeyboardStep(FocusMove.DOWN)!!

        assertTrue(next.historyFocused)
        assertFalse(next.previewFocused)
        assertEquals(0, next.historyIndex)
    }

    @Test
    fun `down from the bottom cover row with no history returns to the keyboard`() {
        onCovers(games = 12, index = 8).aboveKeyboardStep(FocusMove.DOWN)!!.assertOnKeyboard()
    }

    @Test
    fun `back from either region reopens the keyboard on the field`() {
        onCovers(games = 4, index = 2).copy(showKeyboard = false).returningToKeyboard().assertOnKeyboard()
        onHistory(listOf("zelda"), index = 0).returningToKeyboard().assertOnKeyboard()
    }

    @Test
    fun `history narrows to entries containing what is typed`() {
        val typed = state(history = listOf("Zelda", "Metroid", "zelda 2"), query = "zel")

        assertEquals(listOf("Zelda", "zelda 2"), typed.historyEntries)
        assertEquals(3, typed.copy(query = "").historyEntries.size)
    }

    @Test
    fun `an empty query shows recently played covers and typing narrows them`() {
        val landing = SearchUiState(
            query = "",
            recentGames = listOf(game(1, "Mario"), game(2, "Metroid"), game(3, "Zelda")),
            dualScreen = true
        )

        assertEquals(3, landing.coverGames.size)
        assertEquals(listOf("Metroid"), landing.copy(query = "t").coverGames.map { it.title })
    }

    @Test
    fun `a full query shows the game results as covers`() {
        assertEquals(5, state(games = 5).coverGames.size)
    }

    @Test
    fun `a narrowed history clamps its focus and an emptied one returns to the keyboard`() {
        val focused = onHistory(listOf("zelda", "zelda 2", "zelda 3"), index = 2)

        assertEquals(0, focused.copy(recentSearches = listOf("zelda")).withAboveKeyboardClamped().historyIndex)
        focused.copy(recentSearches = emptyList()).withAboveKeyboardClamped().assertOnKeyboard()
    }

    @Test
    fun `fewer covers clamp the grid focus and none drop it`() {
        val focused = onCovers(games = 10, index = 9)

        assertEquals(1, focused.copy(gameResults = focused.gameResults.take(2)).withAboveKeyboardClamped().previewIndex)
        assertFalse(focused.copy(gameResults = emptyList()).withAboveKeyboardClamped().previewFocused)
    }

    @Test
    fun `one screen enters the preview row only while the keyboard is open and never the history`() {
        val single = state(games = 3, history = listOf("zelda"), dualScreen = false)

        assertNull(single.copy(showKeyboard = false).enteringAboveKeyboard())
        val entered = single.enteringAboveKeyboard()!!
        assertTrue(entered.previewFocused)
        assertFalse(entered.historyFocused)
    }

    @Test
    fun `one screen caps the preview row at the preview limit`() {
        val entered = state(games = 30, dualScreen = false).copy(previewIndex = 25).enteringAboveKeyboard()!!

        assertEquals(SearchViewModel.PREVIEW_LIMIT - 1, entered.previewIndex)
    }

    @Test
    fun `one screen leaves the keyboard as it was when the row lets go`() {
        val back = state(games = 3, dualScreen = false).copy(previewFocused = true).returningToKeyboard()

        assertFalse(back.previewFocused)
        assertTrue(back.showKeyboard)
    }

    @Test
    fun `search uses the presentation screen while it is up`() {
        val slot = PresentationSlot.Search(SlotOwner("search"), state(games = 1).toPresentationState())

        assertTrue(searchUsesPresentation(true, slot))
        assertTrue(searchUsesPresentation(true, PresentationSlot.Fallback))
    }

    @Test
    fun `search keeps one screen without a presentation screen`() {
        assertFalse(searchUsesPresentation(false, PresentationSlot.Fallback))
    }

    @Test
    fun `a live game or media playback on the presentation screen keeps search on one screen`() {
        assertFalse(searchUsesPresentation(true, PresentationSlot.InGame(CompanionInGameState(), emptyList())))
        assertFalse(searchUsesPresentation(true, PresentationSlot.MediaPlayback("item")))
    }

    @Test
    fun `the presentation focus ring follows the covers only`() {
        val covers = onCovers(games = 3, index = 1)

        assertTrue(covers.toPresentationState().focusOnTop)
        assertEquals("Game 2", covers.toPresentationState().focusedCover?.title)
        assertFalse(onHistory(listOf("zelda"), index = 0, games = 3).toPresentationState().focusOnTop)
        assertFalse(covers.copy(dualScreen = false).toPresentationState().focusOnTop)
    }
}
