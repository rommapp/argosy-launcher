package com.nendo.argosy.ui.screens.collections.dialogs

import com.nendo.argosy.ui.common.GameListDetails
import com.nendo.argosy.ui.common.RunningGameUi
import com.nendo.argosy.ui.screens.collections.CollectionGameUi
import com.nendo.argosy.ui.screens.collections.VirtualCategoryUiState
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

    private fun game(id: Long, isDownloaded: Boolean) = CollectionGameUi(
        id = id,
        title = "Game $id",
        platformId = 7,
        platformSlug = "psp",
        platformDisplayName = "PSP",
        details = GameListDetails(),
        isFavorite = false,
        isDownloaded = isDownloaded,
        rommId = id
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
        val state = VirtualCategoryUiState(games = listOf(game(1, isDownloaded = true)))

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
        val state = VirtualCategoryUiState(games = listOf(game(1, isDownloaded = true)))

        assertTrue(state.optionRowsFor(null, listOf(target)).isEmpty())
    }

    @Test
    fun `a virtual category with a download pending offers download all only`() {
        val state = VirtualCategoryUiState(games = listOf(game(1, isDownloaded = false)))

        assertEquals(
            listOf(CollectionOptionRow.Action(CollectionOption.DOWNLOAD_ALL)),
            state.optionRowsFor(null)
        )
    }
}
