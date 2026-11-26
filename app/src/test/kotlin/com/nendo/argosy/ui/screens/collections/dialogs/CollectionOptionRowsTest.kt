package com.nendo.argosy.ui.screens.collections.dialogs

import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.ui.common.GameListDetails
import com.nendo.argosy.ui.common.RunningGameUi
import com.nendo.argosy.ui.screens.collections.GameSetListing
import com.nendo.argosy.ui.screens.collections.VirtualCategoryUiState
import com.nendo.argosy.ui.screens.library.LibraryGameUi
import com.nendo.argosy.ui.screens.library.LibraryGridItem
import com.nendo.argosy.ui.screens.library.LibraryListing
import com.nendo.argosy.ui.screens.running.RunningMoveTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionOptionRowsTest {

    private val target = RunningMoveTarget(displayId = 2, screenNumber = 2, swapsRoles = false)

    private val running = RunningGameUi(
        gameId = 1,
        variantFileId = null,
        taskId = 40,
        slotIndex = null,
        displayId = 0,
        emulatorPackage = "org.ppsspp.ppsspp",
        playedThisRunMs = 0,
        isClosing = false,
        order = 0
    )

    private fun game(id: Long, isDownloaded: Boolean) = LibraryGameUi(
        id = id,
        title = "Game $id",
        sortTitle = "game $id",
        platformId = 7,
        platformSlug = "psp",
        platformDisplayName = "PSP",
        coverPath = null,
        source = GameSource.ROMM_SYNCED,
        isFavorite = false,
        isDownloaded = isDownloaded,
        isRommGame = true,
        isAndroidApp = false,
        emulatorName = null,
        listDetails = GameListDetails()
    )

    private fun categoryWith(vararg games: LibraryGameUi) = VirtualCategoryUiState(
        listing = GameSetListing(
            listing = LibraryListing(
                gridItems = games.mapIndexed { index, game -> LibraryGridItem.Game(game, index) }
            ),
            totalCount = games.size,
            rommGameIds = games.map { it.id }.toSet(),
            isLoaded = true
        )
    )

    @Test
    fun `an editable collection offers rename and delete after download all`() {
        val rows = collectionOptionRows(showDownloadAll = true, downloadableCount = 3, showRemoveGame = true)

        assertEquals(
            listOf(
                CollectionOptionRow.Action(CollectionOption.DOWNLOAD_ALL),
                CollectionOptionRow.Action(CollectionOption.RENAME),
                CollectionOptionRow.Action(CollectionOption.DELETE),
                CollectionOptionRow.Action(CollectionOption.REMOVE_GAME)
            ),
            rows
        )
    }

    @Test
    fun `a non-editable list never offers rename or delete`() {
        val rows = collectionOptionRows(
            showDownloadAll = true,
            downloadableCount = 3,
            editable = false,
            moveTargets = listOf(target),
            showCloseGame = true
        )

        assertEquals(
            listOf(
                CollectionOptionRow.MoveTo(target),
                CollectionOptionRow.Action(CollectionOption.DOWNLOAD_ALL),
                CollectionOptionRow.Action(CollectionOption.CLOSE_GAME)
            ),
            rows
        )
    }

    @Test
    fun `a non-editable list with nothing to download and no running game is empty`() {
        assertTrue(collectionOptionRows(showDownloadAll = true, downloadableCount = 0, editable = false).isEmpty())
    }

    @Test
    fun `a virtual category with everything downloaded offers close for a running game`() {
        val state = categoryWith(game(1, isDownloaded = true))

        assertEquals(
            listOf(
                CollectionOptionRow.MoveTo(target),
                CollectionOptionRow.Action(CollectionOption.CLOSE_GAME)
            ),
            state.optionRowsFor(running, listOf(target))
        )
    }

    @Test
    fun `a virtual category with everything downloaded and no running game has no rows`() {
        val state = categoryWith(game(1, isDownloaded = true))

        assertTrue(state.optionRowsFor(null, listOf(target)).isEmpty())
    }

    @Test
    fun `a virtual category with a download pending offers download all only`() {
        val state = categoryWith(game(1, isDownloaded = false))

        assertEquals(
            listOf(CollectionOptionRow.Action(CollectionOption.DOWNLOAD_ALL)),
            state.optionRowsFor(null)
        )
    }
}
