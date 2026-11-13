package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.model.GameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryQuickMenuRowsTest {

    private fun game(isDownloaded: Boolean = false, canRemoveFromLibrary: Boolean = false) = LibraryGameUi(
        id = 1,
        title = "Game",
        sortTitle = "game",
        platformId = 7,
        platformSlug = "snes",
        platformDisplayName = "SNES",
        coverPath = null,
        source = GameSource.ROMM_REMOTE,
        isFavorite = false,
        isDownloaded = isDownloaded,
        isRommGame = true,
        canRemoveFromLibrary = canRemoveFromLibrary,
        isAndroidApp = false,
        emulatorName = null,
        listDetails = com.nendo.argosy.ui.common.GameListDetails()
    )

    @Test
    fun `the active variant row is hidden for a single-member group`() {
        val rows = libraryQuickMenuRows(game(), isCustomGridHome = false, hasSiblingGroup = false)

        assertFalse(rows.contains(LibraryQuickMenuRow.ACTIVE_VARIANT))
    }

    @Test
    fun `the active variant row follows the collection rows and precedes refresh`() {
        val rows = libraryQuickMenuRows(game(), isCustomGridHome = true, hasSiblingGroup = true)

        assertEquals(
            listOf(
                LibraryQuickMenuRow.PRIMARY,
                LibraryQuickMenuRow.FAVORITE,
                LibraryQuickMenuRow.DETAILS,
                LibraryQuickMenuRow.ADD_TO_COLLECTION,
                LibraryQuickMenuRow.ADD_TO_GRID,
                LibraryQuickMenuRow.ACTIVE_VARIANT,
                LibraryQuickMenuRow.REFRESH,
                LibraryQuickMenuRow.RESYNC_PLATFORM,
                LibraryQuickMenuRow.HIDE
            ),
            rows
        )
    }

    @Test
    fun `the row list keeps the order the menu had before the active variant row`() {
        val rows = libraryQuickMenuRows(game(isDownloaded = true), isCustomGridHome = false, hasSiblingGroup = false)

        assertEquals(
            listOf(
                LibraryQuickMenuRow.PRIMARY,
                LibraryQuickMenuRow.FAVORITE,
                LibraryQuickMenuRow.DETAILS,
                LibraryQuickMenuRow.ADD_TO_COLLECTION,
                LibraryQuickMenuRow.REFRESH,
                LibraryQuickMenuRow.RESYNC_PLATFORM,
                LibraryQuickMenuRow.DELETE,
                LibraryQuickMenuRow.HIDE
            ),
            rows
        )
    }

    @Test
    fun `a removable game offers remove from library just before hide`() {
        val rows = libraryQuickMenuRows(game(canRemoveFromLibrary = true), isCustomGridHome = false, hasSiblingGroup = false)

        assertEquals(LibraryQuickMenuRow.REMOVE_FROM_LIBRARY, rows[rows.lastIndex - 1])
        assertEquals(LibraryQuickMenuRow.HIDE, rows.last())
    }

    @Test
    fun `a streamable game offers the stream row right after download`() {
        val rows = libraryQuickMenuRows(game(), isCustomGridHome = false, hasSiblingGroup = false, streamable = true)

        assertEquals(listOf(LibraryQuickMenuRow.PRIMARY, LibraryQuickMenuRow.STREAM), rows.take(2))
        assertFalse(libraryQuickMenuRows(game(), isCustomGridHome = false, hasSiblingGroup = false).contains(LibraryQuickMenuRow.STREAM))
    }

    @Test
    fun `stream mode puts the stream row on top and download second`() {
        val rows = libraryQuickMenuRows(
            game(),
            isCustomGridHome = false,
            hasSiblingGroup = false,
            streamable = true,
            streamLeads = true
        )

        assertEquals(listOf(LibraryQuickMenuRow.STREAM, LibraryQuickMenuRow.PRIMARY), rows.take(2))
        assertEquals(1, rows.count { it == LibraryQuickMenuRow.STREAM })
    }

    @Test
    fun `stream mode without a container keeps download alone on top`() {
        val rows = libraryQuickMenuRows(
            game(),
            isCustomGridHome = false,
            hasSiblingGroup = false,
            streamable = false,
            streamLeads = true
        )

        assertEquals(LibraryQuickMenuRow.PRIMARY, rows.first())
        assertFalse(rows.contains(LibraryQuickMenuRow.STREAM))
    }

    @Test
    fun `a game that is not removable never offers remove from library`() {
        val rows = libraryQuickMenuRows(game(), isCustomGridHome = false, hasSiblingGroup = false)

        assertFalse(rows.contains(LibraryQuickMenuRow.REMOVE_FROM_LIBRARY))
    }
}
