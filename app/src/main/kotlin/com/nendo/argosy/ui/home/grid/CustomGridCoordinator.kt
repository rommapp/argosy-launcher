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
import com.nendo.argosy.ui.components.TilePickerAction
import com.nendo.argosy.ui.components.TilePickerCategory
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
    private val pickerEntries: suspend (TilePickerCategory, String) -> List<TilePickerEntry>,
    private val onPageAdded: ((Int) -> Unit)? = null,
    private val onPageRemoved: ((Int) -> Unit)? = null,
    private val mediaCatalog: MediaTileCatalog? = null,
    private val read: () -> CustomGridState,
    private val write: ((CustomGridState) -> CustomGridState) -> Unit
) {

    val state: CustomGridState get() = read()

    private val mediaSetupController = MediaTileSetupController(
        scope = scope,
        catalog = mediaCatalog,
        read = read,
        write = write,
        onPlace = { target, playlist ->
            val editing = recuratingTileId
            if (editing == null) {
                placeOnFocusedCell(target, playlist)
            } else {
                retargetTile(editing, target, playlist)
            }
            recuratingTileId = null
            closePicker()
        }
    )

    /**
     * Tile being re-curated, or null when the setup flow is placing a new one.
     */
    private var recuratingTileId: Long? = null

    fun setTiles(tiles: List<HomeTile>) = write { it.copy(tiles = tiles) }

    fun setTilePlayback(playback: Map<Long, String>) = write { it.copy(tilePlayback = playback) }

    /**
     * Hands the d-pad to the focused tile. Answers false when it has nothing to play, so the caller
     * can fall back to whatever a press on that tile otherwise means.
     */
    fun engageFocusedTile(): Boolean {
        val current = read()
        val tile = current.focusedTile ?: return false
        if (tile.id !in current.tilePlayback) return false
        write { it.copy(engagedTileId = tile.id, showMenu = false) }
        return true
    }

    fun disengageTile(): Boolean {
        if (read().engagedTileId == null) return false
        write { it.copy(engagedTileId = null) }
        return true
    }

    /**
     * Applies the parts of the layout config the grid itself has to obey. Kept on the state rather
     * than read from preferences at each decision point, so a placement and the page it lands on
     * cannot disagree about the rules mid-edit.
     */
    fun applyConfig(autoFit: Boolean, storedPages: Int) =
        write { it.copy(autoFit = autoFit, storedPages = storedPages) }

    /**
     * Says whether media can be pinned at all. Signing out closes the Media tab and moves off it, so
     * the picker is never left listing a kind that has stopped existing underneath it.
     */
    fun setMediaAvailable(available: Boolean) {
        if (read().mediaAvailable == available) return
        write { state ->
            val category = state.pickerCategory
            state.copy(
                mediaAvailable = available,
                pickerCategory = if (
                    !available && category == TilePickerCategory.MEDIA
                ) {
                    TilePickerCategory.GAMES
                } else {
                    category
                }
            )
        }
        if (read().showPicker) refreshPicker()
    }

    /**
     * Says whether this surface can play a file already on the device. It is what keeps the Media tab
     * on offer for a reader with no media server: a video on their own storage is something they
     * have rather than a feature being advertised at them.
     */
    fun setLocalVideoSupported(supported: Boolean) {
        if (read().supportsLocalVideo == supported) return
        write { it.copy(supportsLocalVideo = supported) }
    }

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
                engagedTileId = null,
                cell = GridCell(
                    entryColumn,
                    it.cell.rowIndex.coerceIn(0, (it.rows - 1).coerceAtLeast(0))
                )
            )
        }
        return true
    }

    fun openMenu() {
        if (read().menuActions.isEmpty()) return
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
            CustomTileMenuAction.RECURATE -> recurateFocusedTile()
            CustomTileMenuAction.REMOVE -> removeFocusedTile()
            CustomTileMenuAction.DELETE_PAGE -> deleteCurrentPage()
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
                engagedTileId = null,
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
     * Drops the tile being arranged on a cell chosen by touch. The anchor goes where the finger
     * went, clamped so the whole tile stays on the page: a touch has no direction to step in, so it
     * names a destination rather than a move.
     */
    fun moveEditingTileTo(target: GridCell): Boolean {
        val current = read()
        val tile = current.editingTile ?: return false
        val column = target.columnIndex.coerceIn(
            0,
            (current.columns - tile.rect.columnSpan).coerceAtLeast(0)
        )
        val row = target.rowIndex.coerceIn(0, (current.rows - tile.rect.rowSpan).coerceAtLeast(0))
        write {
            it.copy(
                editingRect = tile.rect.copy(columnIndex = column, rowIndex = row),
                cell = GridCell(column, row)
            )
        }
        return true
    }

    /**
     * Reshapes the tile being arranged so its far corner reaches the dragged cell. The anchor never
     * moves, which is what keeps resizing and moving distinguishable: one changes where a tile is,
     * the other only how much it covers.
     */
    fun resizeEditingTileTo(target: GridCell): Boolean {
        val current = read()
        val tile = current.editingTile ?: return false
        val floor = tile.minSpan
        val columnCeiling = (current.columns - tile.rect.columnIndex).coerceAtLeast(1)
        val rowCeiling = (current.rows - tile.rect.rowIndex).coerceAtLeast(1)
        val columnSpan = (target.columnIndex - tile.rect.columnIndex + 1)
            .coerceIn(floor.coerceAtMost(columnCeiling), columnCeiling)
        val rowSpan = (target.rowIndex - tile.rect.rowIndex + 1)
            .coerceIn(floor.coerceAtMost(rowCeiling), rowCeiling)
        write {
            it.copy(editingRect = tile.rect.copy(columnSpan = columnSpan, rowSpan = rowSpan))
        }
        return true
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
        val floor = tile.minSpan
        if (resized.columnSpan < floor || resized.rowSpan < floor) return false
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
        if (!current.autoFit && others.any { it.rect.overlaps(edited.rect) }) {
            cancelEdit()
            return
        }
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
     * Adds a page and moves onto it. Nothing is stored yet - a page exists because tiles reference
     * it - so the new page is held here until something is placed on it, and the stub moves along
     * to sit after it. Confirming here used to open the picker, which answered a different question
     * than the one the stub asks.
     */
    fun confirmAddPage() {
        val current = read()
        if (!current.isOnAddPage) return
        write { it.copy(pendingPage = current.page, cell = GridCell(0, 0)) }
        onPageAdded?.invoke(current.page + 1)
    }

    /**
     * Removes the page under the cursor and closes the gap behind it. The tiles go with the page;
     * nothing they pointed at is touched, so the games themselves stay on the device.
     *
     * The cursor lands on the page that took the deleted one's place, or on the new last page when
     * the deleted one was last - never past the end, where it would be sitting on the trailing stub
     * with nothing to go back to. A page that was only ever pending has nothing stored to delete, so
     * it is simply forgotten.
     */
    fun deleteCurrentPage() {
        val current = read()
        if (!current.canDeletePage) return
        val target = current.page
        val remaining = (current.realPageCount - 1).coerceAtLeast(1)
        val landing = target.coerceAtMost(remaining - 1).coerceAtLeast(0)
        val wasPending = current.pendingPage == target
        val pending = when {
            wasPending -> null
            current.pendingPage != null && current.pendingPage > target -> current.pendingPage - 1
            else -> current.pendingPage
        }
        write {
            it.copy(
                page = landing,
                cell = GridCell(0, 0),
                pendingPage = pending,
                storedPages = it.storedPages.coerceAtMost(remaining),
                showMenu = false,
                menuFocusIndex = 0,
                showPicker = false,
                pickerQuery = "",
                pickerSearchActive = false,
                pickerFocusIndex = 0
            )
        }
        if (!wasPending) {
            repository?.let { tiles ->
                scope.launch { tiles.removePage(ownerUserId(), target) }
            }
        }
        onPageRemoved?.invoke(remaining)
    }

    fun openPicker() {
        if (read().focusedTile != null) return
        write {
            it.copy(
                showPicker = true,
                pickerQuery = "",
                pickerFocusIndex = 0,
                pickerSearchActive = false,
                pickerCategory = TilePickerCategory.GAMES
            )
        }
        refreshPicker()
    }

    fun closePicker() = write {
        it.copy(
            showPicker = false,
            pickerQuery = "",
            pickerSearchActive = false,
            mediaSetup = null,
            showFileBrowser = false
        )
    }

    /**
     * Opens the search field, which is the one place the grid hands focus to Compose: a soft
     * keyboard has to reach a text field, and there is nothing else on the modal competing for it.
     * Closing it clears the query so the list a dismissed search leaves behind is the full one.
     */
    fun togglePickerSearch() {
        val active = !read().pickerSearchActive
        write { it.copy(pickerSearchActive = active, pickerQuery = if (active) it.pickerQuery else "") }
        if (!active) refreshPicker()
    }

    fun movePickerFocus(delta: Int) = write {
        val maxIndex = (it.pickerFocusCount - 1).coerceAtLeast(0)
        it.copy(pickerFocusIndex = (it.pickerFocusIndex + delta).coerceIn(0, maxIndex))
    }

    /**
     * Jumps the picker to the next or previous initial letter, so a long library is crossed in
     * presses rather than in rows.
     */
    fun jumpPickerLetter(forward: Boolean) = write { state ->
        val entries = state.pickerEntries
        if (entries.isEmpty()) return@write state
        val from = state.pickerFocusIndex.coerceIn(0, entries.lastIndex)
        val current = entries[from].title.firstOrNull()?.uppercaseChar()
        val target = if (forward) {
            entries.withIndex().firstOrNull { (index, entry) ->
                index > from && entry.title.firstOrNull()?.uppercaseChar() != current
            }?.index
        } else {
            val start = entries.withIndex().lastOrNull { (index, entry) ->
                index < from && entry.title.firstOrNull()?.uppercaseChar() != current
            }?.index
            start?.let { end ->
                val letter = entries[end].title.firstOrNull()?.uppercaseChar()
                entries.withIndex().first { (index, entry) ->
                    index <= end && entry.title.firstOrNull()?.uppercaseChar() == letter
                }.index
            }
        }
        state.copy(pickerFocusIndex = target ?: if (forward) entries.lastIndex else 0)
    }

    fun setPickerQuery(query: String) {
        write { it.copy(pickerQuery = query, pickerFocusIndex = 0) }
        refreshPicker()
    }

    /**
     * Focus is clamped as the entries land, because a list that arrives shorter than the one it
     * replaces would otherwise leave the cursor past its end - and past the end is where the
     * destructive footer sits.
     */
    private fun refreshPicker() {
        scope.launch {
            val current = read()
            val entries = withLocalVideoRow(
                current,
                pickerEntries(current.pickerCategory, current.pickerQuery.trim().lowercase())
            )
            write { state ->
                val updated = state.copy(pickerEntries = entries)
                updated.copy(
                    pickerFocusIndex = updated.pickerFocusIndex
                        .coerceIn(0, (updated.pickerFocusCount - 1).coerceAtLeast(0))
                )
            }
        }
    }

    /**
     * Moves between what the picker lists. The focus index resets because the lists have nothing to
     * do with each other, so keeping a position from one in another lands somewhere arbitrary.
     */
    fun setPickerCategory(category: TilePickerCategory) {
        val current = read()
        if (current.pickerCategory == category) return
        if (category !in current.pickerCategories) return
        write { it.copy(pickerCategory = category, pickerFocusIndex = 0) }
        refreshPicker()
    }

    /**
     * Steps between the kinds actually on offer, not between every kind that exists. Cycling through
     * the enum would stop on a Media tab that is not drawn when nobody is signed in.
     */
    fun cyclePickerCategory(delta: Int) {
        val current = read()
        val entries = current.pickerCategories
        if (entries.isEmpty()) return
        val position = entries.indexOf(current.pickerCategory).coerceAtLeast(0)
        val next = entries[(position + delta).mod(entries.size)]
        write { it.copy(pickerCategory = next, pickerFocusIndex = 0) }
        refreshPicker()
    }

    /**
     * The video and animation files a tile can be pointed at, offered at the head of the Media tab.
     *
     * It leads the list rather than trailing it because it is the one row that always works: the
     * titles under it depend on a media server having been reached, while a file on this device is
     * there whether anything has been synced or not.
     */
    private fun withLocalVideoRow(
        current: CustomGridState,
        entries: List<TilePickerEntry>
    ): List<TilePickerEntry> {
        if (current.pickerCategory != TilePickerCategory.MEDIA) return entries
        if (!current.supportsLocalVideo) return entries
        return listOf(
            TilePickerEntry(
                target = HomeTileTargetRef.Unresolvable,
                title = "Choose a video on this device",
                subtitle = "Plays straight from your storage",
                action = TilePickerAction.BROWSE_LOCAL_FILE,
                isLocal = true
            )
        ) + entries
    }

    fun confirmPickerSelection() {
        val current = read()
        if (current.isPickerDeletePageFocused) {
            deleteCurrentPage()
            return
        }
        val entry = current.pickerEntries.getOrNull(current.pickerFocusIndex) ?: return
        selectPickerEntry(entry)
    }

    /**
     * Acts on a chosen row. Three of the kinds go straight onto the page; the two media kinds do not,
     * because one needs a file naming and the other needs to be asked what part of a show it stands
     * for. Both a tap and a press of confirm arrive here, so neither can behave differently.
     */
    fun selectPickerEntry(entry: TilePickerEntry) {
        if (entry.action == TilePickerAction.BROWSE_LOCAL_FILE) {
            openFileBrowser()
            return
        }
        if (entry.target is HomeTileTargetRef.Media) {
            if (entry.isSeries) {
                mediaSetupController.begin(entry)
            } else {
                mediaSetupController.offerSingle(entry)
            }
            return
        }
        placeOnFocusedCell(entry.target)
        closePicker()
    }

    fun openFileBrowser() = write { it.copy(showFileBrowser = true) }

    fun closeFileBrowser() = write { it.copy(showFileBrowser = false) }

    /**
     * Places a file from this device. Nothing is fetched and nothing is asked: the file is already
     * where it needs to be, which is the whole point of offering it.
     */
    fun placeLocalVideo(path: String) {
        write { it.copy(showFileBrowser = false) }
        if (path.isBlank()) return
        placeOnFocusedCell(HomeTileTargetRef.LocalMedia(path))
        closePicker()
    }

    fun moveMediaSetupFocus(delta: Int) = mediaSetupController.moveFocus(delta)

    fun moveMediaSetupSideways(towardsEnd: Boolean) =
        mediaSetupController.moveSideways(towardsEnd)

    fun confirmMediaSetup(index: Int? = null) = mediaSetupController.confirm(index)

    /**
     * Steps back through the media questions, closing the whole run once there is nothing behind the
     * first one. Answers whether anything was handled, so the caller can fall through to closing the
     * picker itself.
     */
    fun backFromMediaSetup(): Boolean {
        if (read().mediaSetup == null) return false
        if (mediaSetupController.back()) return true
        mediaSetupController.close()
        return true
    }

    fun confirmMediaTileNotice() = mediaSetupController.confirmNotice()

    fun dismissMediaTileNotice() = mediaSetupController.dismissNotice()

    fun moveMediaTileNoticeFocus(delta: Int) = mediaSetupController.moveNoticeFocus(delta)

    /**
     * Raises the offer a finished download left behind. Only one is shown at a time: a batch of
     * downloads would otherwise stack modals over the grid, and the rest stay queued until this one
     * is answered.
     */
    fun showPendingAdd(entry: TilePickerEntry) {
        if (read().pendingAdd != null) return
        write { it.copy(pendingAdd = entry, pendingAddFocusIndex = 0) }
    }

    fun movePendingAddFocus(delta: Int) = write {
        it.copy(pendingAddFocusIndex = (it.pendingAddFocusIndex + delta).coerceIn(0, 1))
    }

    /**
     * Accepts the offer by appending to the last page, the same placement the automatic mode uses,
     * so the two modes differ only in whether they ask.
     */
    fun confirmPendingAdd(onResolved: (Long) -> Unit) {
        val entry = read().pendingAdd ?: return
        val tiles = repository
        write { it.copy(pendingAdd = null) }
        entry.gameId?.let(onResolved)
        if (tiles == null) return
        scope.launch {
            tiles.appendToLastPage(
                ownerUserId = ownerUserId(),
                target = entry.target,
                columns = read().columns.coerceAtLeast(1)
            )
        }
    }

    fun dismissPendingAdd(onResolved: (Long) -> Unit) {
        val entry = read().pendingAdd ?: return
        write { it.copy(pendingAdd = null) }
        entry.gameId?.let(onResolved)
    }

    private fun retargetTile(tileId: Long, target: HomeTileTargetRef, playlist: List<String>) {
        val tiles = repository ?: return
        val tile = read().tiles.firstOrNull { it.id == tileId } ?: return
        scope.launch { tiles.retarget(tile, ownerUserId(), target, playlist) }
    }

    /**
     * Reopens the media setup flow for a tile already on the grid.
     */
    fun recurateFocusedTile() {
        val tile = read().focusedTile ?: return
        val target = tile.target as? HomeTileTargetRef.Media ?: return
        val catalog = mediaCatalog ?: return
        scope.launch {
            val entry = catalog.entryFor(target.itemId) ?: return@launch
            recuratingTileId = tile.id
            mediaSetupController.begin(entry)
        }
    }

    fun placeOnFocusedCell(target: HomeTileTargetRef, playlist: List<String> = emptyList()) {
        val current = read()
        val tiles = repository ?: return
        scope.launch {
            tiles.place(
                ownerUserId = ownerUserId(),
                pageIndex = current.page,
                rect = TileRect(current.cell.columnIndex, current.cell.rowIndex),
                target = target,
                playlist = playlist
            )
        }
    }
}
