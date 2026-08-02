package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.data.repository.HomeTileRepository
import com.nendo.argosy.domain.model.CustomGridMove
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.GridDirection2D
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.domain.model.customGridStep
import com.nendo.argosy.domain.model.settleAfterEdit
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.CustomTileMenuAction
import com.nendo.argosy.ui.components.TileEditMode
import com.nendo.argosy.ui.components.TilePickerEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Every action the custom grid supports, held once for both home surfaces.
 *
 * The grid is the same feature on a phone and on a companion display, so the behaviour lives here
 * and the view models keep only the lens onto their own state. Two copies of this drifted the last
 * time it was written twice, and a divergence between them is silent: the surface not being tested
 * simply behaves differently.
 */
class CustomGridCoordinator(
    private val scope: CoroutineScope,
    private val repository: HomeTileRepository?,
    private val ownerUserId: suspend () -> Long?,
    private val pickerEntries: suspend (String) -> List<TilePickerEntry>,
    private val read: () -> CustomGridState,
    private val write: ((CustomGridState) -> CustomGridState) -> Unit
) {

    val state: CustomGridState get() = read()

    fun setTiles(tiles: List<HomeTile>) = write { it.copy(tiles = tiles) }

    fun setShape(columns: Int, rows: Int) = write { it.copy(columns = columns, rows = rows) }

    fun setCell(cell: GridCell) = write { it.copy(cell = cell) }

    fun focusedTile(): HomeTile? = read().focusedTile

    fun focusedGameId(): Long? = read().focusedGameId

    fun moveFocus(direction: GridDirection2D): Boolean {
        val current = read()
        val move = customGridStep(
            cell = current.cell,
            tiles = current.tilesOnPage(current.page),
            columns = current.columns,
            rows = current.rows,
            direction = direction
        )
        return when (move) {
            is CustomGridMove.Focus -> {
                write { it.copy(cell = move.cell) }
                true
            }
            CustomGridMove.PreviousPage -> turnPage(-1)
            CustomGridMove.NextPage -> turnPage(1)
            CustomGridMove.None -> false
        }
    }

    /**
     * Turns to an adjacent page, entering from the edge the move came from so the cursor keeps its
     * line rather than jumping to a corner. While a tile is being arranged it comes along, since
     * leaving it behind would put the cursor and the tile on different pages.
     */
    fun turnPage(delta: Int): Boolean {
        val current = read()
        val editing = current.editingTile
        if (editing != null) return carryToPage(editing, delta)
        val target = current.page + delta
        if (target < 0 || target > current.pageCount) return false
        val entryColumn = if (delta > 0) 0 else (current.columns - 1).coerceAtLeast(0)
        write {
            it.copy(
                page = target,
                cell = GridCell(
                    entryColumn,
                    it.cell.rowIndex.coerceIn(0, (it.rows - 1).coerceAtLeast(0))
                )
            )
        }
        return true
    }

    fun openMenu() {
        if (read().focusedTile == null) return
        write { it.copy(showMenu = true, menuFocusIndex = 0) }
    }

    fun closeMenu() = write { it.copy(showMenu = false) }

    fun moveMenuFocus(delta: Int) = write {
        val maxIndex = (it.menuActions.size - 1).coerceAtLeast(0)
        it.copy(menuFocusIndex = (it.menuFocusIndex + delta).coerceIn(0, maxIndex))
    }

    fun confirmMenu() {
        val current = read()
        val action = current.menuActions.getOrNull(current.menuFocusIndex) ?: return
        closeMenu()
        when (action) {
            CustomTileMenuAction.ARRANGE -> enterMoveMode()
            CustomTileMenuAction.REMOVE -> removeFocusedTile()
        }
    }

    fun enterMoveMode() {
        val tile = read().focusedTile ?: return
        write {
            it.copy(
                editMode = TileEditMode.MOVE,
                editingTileId = tile.id,
                editingRect = tile.rect,
                editingPage = it.page,
                showMenu = false
            )
        }
    }

    fun toggleEditMode() = write {
        it.copy(
            editMode = when (it.editMode) {
                TileEditMode.MOVE -> TileEditMode.RESIZE
                TileEditMode.RESIZE -> TileEditMode.MOVE
                TileEditMode.NONE -> TileEditMode.NONE
            }
        )
    }

    fun moveFocusedTile(direction: GridDirection2D): Boolean {
        val current = read()
        val tile = current.focusedTile ?: return false
        val moved = when (direction) {
            GridDirection2D.LEFT -> tile.rect.copy(columnIndex = tile.rect.columnIndex - 1)
            GridDirection2D.RIGHT -> tile.rect.copy(columnIndex = tile.rect.columnIndex + 1)
            GridDirection2D.UP -> tile.rect.copy(rowIndex = tile.rect.rowIndex - 1)
            GridDirection2D.DOWN -> tile.rect.copy(rowIndex = tile.rect.rowIndex + 1)
        }
        if (moved.withinBounds(current.columns, current.rows)) {
            write {
                it.copy(
                    editingRect = moved,
                    cell = GridCell(moved.columnIndex, moved.rowIndex)
                )
            }
            return true
        }
        return when (direction) {
            GridDirection2D.LEFT -> carryToPage(tile, -1)
            GridDirection2D.RIGHT -> carryToPage(tile, 1)
            else -> false
        }
    }

    /**
     * Takes the tile being arranged onto the neighbouring page. Left and right already turn the page
     * when only the cursor moves, so a tile pushed past the same edge follows it rather than stopping
     * dead; without this a tile can only change page by being removed and added again.
     *
     * The trailing stub counts as a destination: a tile carried there is what makes the page exist,
     * which is the same way a page is created by placing on it.
     */
    private fun carryToPage(tile: HomeTile, delta: Int): Boolean {
        val current = read()
        val target = current.page + delta
        if (target < 0 || target > current.storedPageCount) return false
        val column = if (delta > 0) {
            0
        } else {
            (current.columns - tile.rect.columnSpan).coerceAtLeast(0)
        }
        val landed = tile.rect.copy(columnIndex = column)
        write {
            it.copy(
                page = target,
                editingPage = target,
                editingRect = landed,
                cell = GridCell(landed.columnIndex, landed.rowIndex)
            )
        }
        return true
    }

    /**
     * Resize driven by the d-pad: a press away from the anchor grows that edge, a press back toward
     * it shrinks. Since growth already runs down and right, right and down extend while left and up
     * pull in.
     */
    fun resizeFocusedTile(direction: GridDirection2D): Boolean = when (direction) {
        GridDirection2D.RIGHT -> resizeFocusedTile(horizontal = true, grow = true)
        GridDirection2D.LEFT -> resizeFocusedTile(horizontal = true, grow = false)
        GridDirection2D.DOWN -> resizeFocusedTile(horizontal = false, grow = true)
        GridDirection2D.UP -> resizeFocusedTile(horizontal = false, grow = false)
    }

    fun resizeFocusedTile(horizontal: Boolean, grow: Boolean): Boolean {
        val current = read()
        val tile = current.focusedTile ?: return false
        val step = if (grow) 1 else -1
        val resized = if (horizontal) {
            tile.rect.copy(columnSpan = tile.rect.columnSpan + step)
        } else {
            tile.rect.copy(rowSpan = tile.rect.rowSpan + step)
        }
        if (resized.columnSpan < 1 || resized.rowSpan < 1) return false
        if (!resized.withinBounds(current.columns, current.rows)) return false
        write { it.copy(editingRect = resized) }
        return true
    }

    /**
     * Commits the arrangement, settling anything the edited tile came to rest on top of. Overlap is
     * allowed while dragging so a full page can be rearranged at all; this is where the page is made
     * consistent again.
     *
     * A tile the settle cannot find room for is left exactly where it is rather than deleted. The
     * page tolerates an overlap - the schema no longer forbids one - and a curated tile is the
     * user's work: losing one to a full page would be a far worse outcome than a page that still
     * needs tidying.
     */
    fun commitEdit() {
        val current = read()
        val draft = current.editingRect
        val stored = current.editingTileId?.let { id -> current.tiles.firstOrNull { it.id == id } }
        val tiles = repository
        if (draft == null || stored == null || tiles == null) {
            clearEdit()
            return
        }
        val page = current.editingPage ?: stored.pageIndex
        val edited = stored.copy(rect = draft, pageIndex = page)
        val others = current.tiles.filter { it.pageIndex == page && it.id != stored.id }
        val settled = settleAfterEdit(
            editing = edited,
            others = others,
            columns = current.columns,
            rows = current.rows
        )
        scope.launch {
            val owner = ownerUserId()
            if (stored.rect != draft || stored.pageIndex != page) {
                tiles.move(stored, owner, draft, page)
            }
            settled.placed.filter { it.id != stored.id }.forEach { moved ->
                val was = others.firstOrNull { it.id == moved.id }
                if (was != null && was.rect != moved.rect) tiles.move(was, owner, moved.rect)
            }
        }
        clearEdit()
    }

    /**
     * Abandons the arrangement. The draft never reached the database, so dropping it is the whole
     * undo and the stored page was never disturbed.
     */
    fun cancelEdit() {
        val current = read()
        val stored = current.editingTileId?.let { id -> current.tiles.firstOrNull { it.id == id } }
        if (stored != null) {
            write {
                it.copy(
                    page = stored.pageIndex,
                    cell = GridCell(stored.rect.columnIndex, stored.rect.rowIndex)
                )
            }
        }
        clearEdit()
    }

    private fun clearEdit() = write {
        it.copy(
            editMode = TileEditMode.NONE,
            editingTileId = null,
            editingRect = null,
            editingPage = null
        )
    }

    fun removeFocusedTile() {
        val tile = read().focusedTile ?: return
        val tiles = repository ?: return
        scope.launch { tiles.remove(tile.id) }
    }

    /**
     * Turns the stub into a real page by placing the first tile on it. A page exists because tiles
     * reference it, so there is nothing to create until something is put there; opening the picker
     * is the whole action.
     */
    fun confirmAddPage() {
        if (!read().isOnAddPage) return
        write { it.copy(cell = GridCell(0, 0)) }
        openPicker()
    }

    fun openPicker() {
        if (read().focusedTile != null) return
        write { it.copy(showPicker = true, pickerQuery = "", pickerFocusIndex = 0) }
        refreshPicker()
    }

    fun closePicker() = write { it.copy(showPicker = false, pickerQuery = "") }

    fun movePickerFocus(delta: Int) = write {
        val maxIndex = (it.pickerEntries.size - 1).coerceAtLeast(0)
        it.copy(pickerFocusIndex = (it.pickerFocusIndex + delta).coerceIn(0, maxIndex))
    }

    fun setPickerQuery(query: String) {
        write { it.copy(pickerQuery = query, pickerFocusIndex = 0) }
        refreshPicker()
    }

    private fun refreshPicker() {
        scope.launch {
            val entries = pickerEntries(read().pickerQuery.trim().lowercase())
            write { it.copy(pickerEntries = entries) }
        }
    }

    fun confirmPickerSelection() {
        val current = read()
        val entry = current.pickerEntries.getOrNull(current.pickerFocusIndex) ?: return
        placeOnFocusedCell(HomeTileTargetRef.Game(entry.gameId))
        closePicker()
    }

    fun placeOnFocusedCell(target: HomeTileTargetRef) {
        val current = read()
        val tiles = repository ?: return
        scope.launch {
            tiles.place(
                ownerUserId = ownerUserId(),
                pageIndex = current.page,
                rect = TileRect(current.cell.columnIndex, current.cell.rowIndex),
                target = target
            )
        }
    }
}
