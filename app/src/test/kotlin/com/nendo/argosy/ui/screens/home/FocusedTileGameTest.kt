package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.CustomGridState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val QUEUE_GAME_ID = 11L

/**
 * [CustomGridState.focusedGameId] and [HomeUiState.focusedTileGame] both answer what game sits
 * under the cursor, and everything downstream of either one - the presentation screen, the ambient
 * LED, the footer - assumes they agree. A tile kind handled by one and not the other reads as
 * having no game at all.
 */
class FocusedTileGameTest {

    private fun stateWith(target: HomeTileTargetRef, games: Map<Long, HomeGameUi>) = HomeUiState(
        layoutKind = HomeLayoutKind.CUSTOM_GRID,
        tileGames = games,
        customGrid = CustomGridState(
            tiles = listOf(HomeTile(id = 1L, pageIndex = 0, rect = TileRect(0, 0), target = target)),
            page = 0,
            cell = GridCell(0, 0),
            columns = 4,
            rows = 4
        )
    )

    @Test
    fun `a collection playing through answers with the game it would launch`() {
        val game = game(QUEUE_GAME_ID)
        val target = HomeTileTargetRef.Collection(collectionId = 3L, focusGameId = QUEUE_GAME_ID)
        val state = stateWith(target, mapOf(QUEUE_GAME_ID to game))

        assertEquals(QUEUE_GAME_ID, state.customGrid.focusedGameId)
        assertEquals(game, state.focusedTileGame)
    }

    @Test
    fun `a plain collection answers with no game on either accessor`() {
        val state = stateWith(HomeTileTargetRef.Collection(collectionId = 3L), emptyMap())

        assertNull(state.customGrid.focusedGameId)
        assertNull(state.focusedTileGame)
    }

    @Test
    fun `a game tile answers the same game on either accessor`() {
        val game = game(QUEUE_GAME_ID)
        val state = stateWith(HomeTileTargetRef.Game(QUEUE_GAME_ID), mapOf(QUEUE_GAME_ID to game))

        assertEquals(QUEUE_GAME_ID, state.customGrid.focusedGameId)
        assertEquals(game, state.focusedTileGame)
    }

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
}
