package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridOrganizePlacementTest {

    private fun game(id: Long, column: Int, row: Int, parent: Long? = null) = HomeTile(
        id = id,
        pageIndex = if (parent == null) 0 else FOLDER_MEMBER_PAGE_INDEX,
        rect = TileRect(column, row),
        target = HomeTileTargetRef.Game(id),
        parentTileId = parent
    )

    private fun sticker(id: Long, column: Int, row: Int, columnSpan: Int = 1) = HomeTile(
        id = id,
        pageIndex = 0,
        rect = TileRect(column, row, columnSpan, 1),
        target = HomeTileTargetRef.Sticker(TileImageSource.File("/s.png"))
    )

    private val skipsDecor: (HomeTile) -> Boolean = { !it.target.isDecor }

    @Test
    fun `the cursor steps over a sticker to the cell beyond it`() {
        val move = customGridStep(
            cell = GridCell(0, 0),
            tiles = listOf(sticker(1, 1, 0, columnSpan = 2)),
            columns = 5,
            rows = 1,
            direction = GridDirection2D.RIGHT,
            isFocusable = skipsDecor
        )

        assertEquals(CustomGridMove.Focus(GridCell(3, 0)), move)
    }

    @Test
    fun `the cursor steps over decoration leftwards too`() {
        val move = customGridStep(
            cell = GridCell(3, 0),
            tiles = listOf(sticker(1, 1, 0, columnSpan = 2)),
            columns = 5,
            rows = 1,
            direction = GridDirection2D.LEFT,
            isFocusable = skipsDecor
        )

        assertEquals(CustomGridMove.Focus(GridCell(0, 0)), move)
    }

    @Test
    fun `decoration at the page edge turns the page instead of trapping the cursor`() {
        val move = customGridStep(
            cell = GridCell(0, 0),
            tiles = listOf(sticker(1, 1, 0)),
            columns = 2,
            rows = 1,
            direction = GridDirection2D.RIGHT,
            isFocusable = skipsDecor
        )

        assertEquals(CustomGridMove.NextPage, move)
    }

    @Test
    fun `decoration takes the cursor while it is focusable`() {
        val move = customGridStep(
            cell = GridCell(0, 0),
            tiles = listOf(sticker(1, 1, 0)),
            columns = 3,
            rows = 1,
            direction = GridDirection2D.RIGHT
        )

        assertEquals(CustomGridMove.Focus(GridCell(1, 0)), move)
    }

    @Test
    fun `folder members never claim a cell on the page`() {
        val placement = placeTiles(
            listOf(game(1, 0, 0, parent = 9L), game(2, 0, 0)),
            columns = 2,
            rows = 2
        )

        assertEquals(listOf(2L), placement.placed.map { it.id })
        assertTrue(placement.displaced.isEmpty())
    }

    @Test
    fun `folder members are left out of a scrolling canvas`() {
        val placed = placeScrollTiles(
            listOf(game(1, 0, 0, parent = 9L), game(2, 1, 0)),
            lanes = 2,
            axis = HomeScrollAxis.VERTICAL
        )

        assertEquals(listOf(2L), placed.map { it.id })
    }

    @Test
    fun `a folder member under the cursor is not the tile the step leaves from`() {
        val move = customGridStep(
            cell = GridCell(0, 0),
            tiles = listOf(game(1, 0, 0, parent = 9L).copy(rect = TileRect(0, 0, 3, 1))),
            columns = 3,
            rows = 1,
            direction = GridDirection2D.RIGHT
        )

        assertEquals(CustomGridMove.Focus(GridCell(1, 0)), move)
    }

    @Test
    fun `members come back in folder order`() {
        val members = folderMembers(
            listOf(game(3, 2, 0, parent = 9L), game(4, 0, 0, parent = 9L), game(5, 1, 0, parent = 8L)),
            folderId = 9L
        )

        assertEquals(listOf(4L, 3L), members.map { it.id })
    }
}
