package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGameActivationTest {

    private fun game(
        isDownloaded: Boolean = false,
        needsInstall: Boolean = false,
        isSteamGame: Boolean = false
    ) = HomeGameUi(
        id = 5,
        title = "Game",
        platformId = 7,
        platformSlug = "snes",
        platformDisplayName = "SNES",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = isDownloaded,
        isRommGame = true,
        isSteamGame = isSteamGame,
        needsInstall = needsInstall
    )

    @Test
    fun `a rail entry that is not downloaded offers the group choice`() {
        assertEquals(
            HomeGameActivation.DOWNLOAD_WITH_CHOICE,
            homeGameActivation(game(), GameDownloadIndicator.NONE, exactRow = false)
        )
    }

    @Test
    fun `a tile that is not downloaded fetches its exact rom`() {
        assertEquals(
            HomeGameActivation.DOWNLOAD_EXACT,
            homeGameActivation(game(), GameDownloadIndicator.NONE, exactRow = true)
        )
    }

    @Test
    fun `play, install and steam win over either download for both surfaces`() {
        listOf(true, false).forEach { exact ->
            assertEquals(
                HomeGameActivation.LAUNCH,
                homeGameActivation(game(isDownloaded = true), GameDownloadIndicator.NONE, exact)
            )
            assertEquals(
                HomeGameActivation.INSTALL,
                homeGameActivation(game(needsInstall = true), GameDownloadIndicator.NONE, exact)
            )
            assertEquals(
                HomeGameActivation.STEAM_LINK_LAUNCHER,
                homeGameActivation(game(isSteamGame = true), GameDownloadIndicator.NONE, exact)
            )
        }
    }

    @Test
    fun `a stream-first game streams from either surface`() {
        listOf(true, false).forEach { exact ->
            assertEquals(
                HomeGameActivation.STREAM,
                homeGameActivation(game(), GameDownloadIndicator.NONE, exact, leadsWithStream = true)
            )
        }
    }

    @Test
    fun `play, install and a paused download win over stream first`() {
        assertEquals(
            HomeGameActivation.LAUNCH,
            homeGameActivation(game(isDownloaded = true), GameDownloadIndicator.NONE, false, leadsWithStream = true)
        )
        assertEquals(
            HomeGameActivation.INSTALL,
            homeGameActivation(game(needsInstall = true), GameDownloadIndicator.NONE, false, leadsWithStream = true)
        )
        assertEquals(
            HomeGameActivation.RESUME_DOWNLOAD,
            homeGameActivation(game(), GameDownloadIndicator(isPaused = true), false, leadsWithStream = true)
        )
    }

    @Test
    fun `a partial download counts as on the device`() {
        assertEquals(true, game().isOnDevice(GameDownloadIndicator(isPaused = true)))
        assertEquals(true, game().isOnDevice(GameDownloadIndicator(isDownloading = true)))
        assertEquals(false, game().isOnDevice(GameDownloadIndicator.NONE))
    }

    @Test
    fun `confirming a curated grid tile activates its exact game, never the group choice`() {
        val tileGame = game()
        val state = HomeUiState(
            layoutKind = HomeLayoutKind.CUSTOM_GRID,
            customGrid = CustomGridState(
                tiles = listOf(HomeTile(1L, 0, TileRect(0, 0), HomeTileTargetRef.Game(tileGame.id)))
            ),
            tileGames = mapOf(tileGame.id to tileGame)
        )
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns MutableStateFlow(state)
        every { actions.engageFocusedTile() } returns false
        val handler = HomeInputHandler(actions, isDefaultView = true, onGameSelect = {}, onNavigateToDefault = {}, onDrawerToggle = {})

        handler.onConfirm()

        verify(exactly = 1) { actions.activateExactGame(tileGame) }
        verify(exactly = 0) { actions.activateGame(any()) }
        verify(exactly = 0) { actions.queueDownload(any()) }
    }
}
