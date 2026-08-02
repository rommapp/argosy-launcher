package com.nendo.argosy.ui.components

import com.nendo.argosy.data.repository.HomeTileRepository
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.domain.model.fitTilesToPage

enum class CustomTileMenuAction(val label: String) {
    ARRANGE("Move or resize"),
    REMOVE("Remove from grid")
}

/**
 * Editing a placed tile. Moving and resizing are the same activity with the d-pad meaning two
 * different things, so they are one mode you switch within rather than two you enter separately.
 */
enum class TileEditMode { NONE, MOVE, RESIZE }

/**
 * Everything the custom grid needs to draw and edit itself, held once for both home surfaces.
 *
 * The page shape lives here rather than being derived at render time because the cursor, the bounds
 * a move is checked against and the tiles a page can hold all have to agree on it, and only the
 * composable that measures the screen knows what it is.
 */
data class CustomGridState(
    val tiles: List<HomeTile> = emptyList(),
    val page: Int = 0,
    val cell: GridCell = GridCell(0, 0),
    val columns: Int = 0,
    val rows: Int = 0,
    val editMode: TileEditMode = TileEditMode.NONE,
    val editingTileId: Long? = null,
    val editingRect: TileRect? = null,
    val editingPage: Int? = null,
    val pendingPage: Int? = null,
    val autoFit: Boolean = true,
    val storedPages: Int = 0,
    val showMenu: Boolean = false,
    val menuFocusIndex: Int = 0,
    val showPicker: Boolean = false,
    val pickerQuery: String = "",
    val pickerSearchActive: Boolean = false,
    val pickerCategory: TilePickerCategory = TilePickerCategory.GAMES,
    val pickerFocusIndex: Int = 0,
    val pickerEntries: List<TilePickerEntry> = emptyList(),
    val pendingAdd: TilePickerEntry? = null,
    val pendingAddFocusIndex: Int = 0
) {

    val storedPageCount: Int
        get() = maxOf(
            (tiles.maxOfOrNull { it.pageIndex } ?: -1) + 1,
            storedPages,
            HomeTileRepository.DEFAULT_PAGE_COUNT
        )

    /**
     * Pages the grid currently shows. A tile carried onto the trailing stub makes that page real for
     * as long as the edit lasts, so the tile is visible on it while being placed; abandoning the edit
     * takes the page away again because nothing was ever stored there.
     */
    val pageCount: Int
        get() = maxOf(
            storedPageCount,
            (editingPage ?: -1) + 1,
            (pendingPage ?: -1) + 1
        )

    val isOnAddPage: Boolean
        get() = page >= pageCount

    val isEditing: Boolean
        get() = editMode != TileEditMode.NONE

    val editLabel: String?
        get() = when (editMode) {
            TileEditMode.MOVE -> "Move"
            TileEditMode.RESIZE -> "Resize"
            TileEditMode.NONE -> null
        }

    /**
     * The page as it currently looks, with the tile being arranged shown at its draft rectangle.
     * The draft lives here rather than in the database so an interrupted edit leaves the stored
     * page exactly as it was.
     */
    fun tilesOnPage(pageIndex: Int): List<HomeTile> {
        val stored = tiles.filter { it.pageIndex == pageIndex }
        val fitted = fitTilesToPage(stored, columns, rows)
        val editingId = editingTileId
        val rect = editingRect
        if (editingId == null || rect == null) return fitted
        val withoutEditing = fitted.filter { it.id != editingId }
        if (editingPage != pageIndex) return withoutEditing
        val carried = tiles.firstOrNull { it.id == editingId } ?: return withoutEditing
        return withoutEditing + carried.copy(rect = rect, pageIndex = pageIndex)
    }

    /**
     * The tile being arranged. Held by id rather than found under the cursor, because once overlap
     * is allowed two tiles can cover the same cell and the one picked up has to stay the one that
     * moves.
     */
    val editingTile: HomeTile?
        get() = editingTileId?.let { id ->
            tiles.firstOrNull { it.id == id }
                ?.let { if (editingRect != null) it.copy(rect = editingRect) else it }
        }

    /**
     * The tile the next action applies to. While arranging that is the tile picked up, not whatever
     * happens to sit under the cursor: overlap is allowed there, so the cell can belong to two.
     */
    val focusedTile: HomeTile?
        get() = editingTile ?: tilesOnPage(page).firstOrNull {
            it.rect.covers(cell.columnIndex, cell.rowIndex)
        }

    fun tileAt(target: GridCell): HomeTile? =
        tilesOnPage(page).firstOrNull { it.rect.covers(target.columnIndex, target.rowIndex) }

    val focusedGameId: Long?
        get() = (focusedTile?.target as? HomeTileTargetRef.Game)?.gameId

    /**
     * Tiles the edited one is currently sitting on top of. They fade rather than refuse the move,
     * so the overlap is visible before it is committed and the arrangement stays possible.
     */
    val overlappedTileIds: Set<Long>
        get() {
            if (!isEditing) return emptySet()
            val editing = editingTile ?: return emptySet()
            return tilesOnPage(page)
                .filter { it.id != editing.id && it.rect.overlaps(editing.rect) }
                .map { it.id }
                .toSet()
        }

    val menuActions: List<CustomTileMenuAction>
        get() = if (focusedTile == null) emptyList() else CustomTileMenuAction.entries
}
