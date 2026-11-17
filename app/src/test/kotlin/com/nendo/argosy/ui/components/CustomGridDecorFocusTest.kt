package com.nendo.argosy.ui.components

import com.nendo.argosy.domain.model.FOLDER_MEMBER_PAGE_INDEX
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileImageSource
import com.nendo.argosy.domain.model.TileRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomGridDecorFocusTest {

    private val sticker = HomeTile(
        id = 1L,
        pageIndex = 0,
        rect = TileRect(0, 0),
        target = HomeTileTargetRef.Sticker(TileImageSource.File("/s.png"))
    )

    private val folder = HomeTile(
        id = 2L,
        pageIndex = 0,
        rect = TileRect(1, 0),
        target = HomeTileTargetRef.Folder(name = "RPGs")
    )

    private val member = HomeTile(
        id = 3L,
        pageIndex = FOLDER_MEMBER_PAGE_INDEX,
        rect = TileRect(0, 0),
        target = HomeTileTargetRef.Game(9L),
        parentTileId = 2L
    )

    private fun state(decorEditing: Boolean = false, cell: GridCell = GridCell(0, 0)) = CustomGridState(
        tiles = listOf(sticker, folder, member),
        cell = cell,
        columns = 3,
        rows = 1,
        storedPages = 1,
        decorEditing = decorEditing
    )

    @Test
    fun `a sticker is not the focused tile outside decoration editing`() {
        val grid = state()

        assertNull(grid.focusedTile)
        assertTrue(grid.isCellOccupied)
        assertNull(grid.confirmLabelRes)
    }

    @Test
    fun `a sticker is the focused tile while decorations are edited`() {
        assertEquals(1L, state(decorEditing = true).focusedTile?.id)
    }

    @Test
    fun `a folder member is never on the page`() {
        assertEquals(listOf(1L, 2L), state().tilesOnPage(0).map { it.id })
    }

    @Test
    fun `an open folder lists its members`() {
        val grid = state(cell = GridCell(1, 0)).copy(folderOverlay = FolderOverlayState(folderId = 2L))

        assertEquals(listOf(3L), grid.openFolderMembers.map { it.id })
        assertEquals(3L, grid.focusedFolderMember?.id)
    }

    @Test
    fun `a folder is offered as a destination but never for itself`() {
        val grid = state(cell = GridCell(1, 0)).copy(folderChooser = FolderChooserState(tileId = 2L))

        assertTrue(grid.folderChooserTargets.isEmpty())
        assertEquals(listOf(2L), state().copy(folderChooser = FolderChooserState(tileId = 1L)).folderChooserTargets.map { it.id })
    }
}
