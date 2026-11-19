package com.nendo.argosy.ui.screens.buildinglibrary

import com.nendo.argosy.data.remote.romm.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildingLibraryPhaseTest {

    private val running = SyncProgress(
        isSyncing = true,
        currentPlatform = "PlayStation 2",
        platformsTotal = 12,
        platformsDone = 3,
        gamesTotal = 4_200,
        gamesDone = 900
    )

    @Test
    fun `a running sync moves a waiting screen to syncing with its counts`() {
        val state = BuildingLibraryUiState().withProgress(running)

        assertEquals(BuildingLibraryPhase.SYNCING, state.phase)
        assertEquals("PlayStation 2", state.currentPlatform)
        assertEquals(900, state.gamesDone)
        assertEquals(4_200, state.gamesTotal)
        assertEquals(3, state.platformsDone)
    }

    @Test
    fun `the sync stopping finishes the screen and keeps the last counts`() {
        val state = BuildingLibraryUiState().withProgress(running).withProgress(SyncProgress(isSyncing = false))

        assertEquals(BuildingLibraryPhase.DONE, state.phase)
        assertEquals(900, state.gamesDone)
        assertEquals(4_200, state.gamesTotal)
    }

    @Test
    fun `an idle progress before the queued sync starts keeps the screen waiting`() {
        val state = BuildingLibraryUiState().withProgress(SyncProgress(isSyncing = false))

        assertEquals(BuildingLibraryPhase.WAITING, state.phase)
    }

    @Test
    fun `a finished screen stays finished when another idle progress arrives`() {
        val done = BuildingLibraryUiState().withProgress(running).withProgress(SyncProgress(isSyncing = false))

        assertEquals(BuildingLibraryPhase.DONE, done.withProgress(SyncProgress(isSyncing = false)).phase)
    }

    @Test
    fun `synced covers append in arrival order after the ones already shown`() {
        val shown = listOf(LibraryWallTile(1, "a"), LibraryWallTile(2, "b"))

        val wall = appendWallTiles(shown, listOf(LibraryWallTile(7, "g"), LibraryWallTile(3, "c")), limit = 10)

        assertEquals(listOf(1L, 2L, 7L, 3L), wall.map { it.gameId })
    }

    @Test
    fun `a game synced again does not add a second tile`() {
        val shown = listOf(LibraryWallTile(1, "a"), LibraryWallTile(2, "b"))

        val wall = appendWallTiles(shown, listOf(LibraryWallTile(2, "b"), LibraryWallTile(2, "b"), LibraryWallTile(4, "d")), limit = 10)

        assertEquals(listOf(1L, 2L, 4L), wall.map { it.gameId })
    }

    @Test
    fun `the wall keeps only the newest covers past its limit`() {
        val shown = (1L..4L).map { LibraryWallTile(it, "$it") }

        val wall = appendWallTiles(shown, listOf(LibraryWallTile(5, "5"), LibraryWallTile(6, "6")), limit = 4)

        assertEquals(listOf(3L, 4L, 5L, 6L), wall.map { it.gameId })
    }

    @Test
    fun `no game total means no fraction for the bar`() {
        assertNull(BuildingLibraryUiState().gamesFraction)
        assertEquals(0.25f, BuildingLibraryUiState(gamesDone = 1, gamesTotal = 4).gamesFraction)
    }
}
