package com.nendo.argosy.ui.components

import com.nendo.argosy.data.repository.HomeTileRepository
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.domain.model.fitTilesToPage

enum class CustomTileMenuAction(val label: String) {
    ARRANGE("Move or resize"),
    RECURATE("Change what it plays"),
    REMOVE("Remove from grid"),
    DELETE_PAGE("Delete Page")
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
    val mediaAvailable: Boolean = false,
    val supportsLocalVideo: Boolean = false,
    /**
     * The tile currently holding the d-pad, or null. An engaged tile plays with sound and takes
     * the directional keys for its own transport; Menu and system Back are never taken, so there
     * is always a way out.
     */
    val engagedTileId: Long? = null,
    /**
     * Local files the media tiles on this page resolve to, keyed by tile id. Only tiles with an
     * entry here can preview; everything else draws its poster.
     */
    val tilePlayback: Map<Long, String> = emptyMap(),
    val mediaSetup: MediaTileSetup? = null,
    val showFileBrowser: Boolean = false,
    val pendingAdd: TilePickerEntry? = null,
    val pendingAddFocusIndex: Int = 0
) {

    /**
     * The kinds this grid can currently be filled from.
     *
     * Media stands on either of two feet. A signed-in account gives it library titles, and a surface
     * that can play a file already on the device gives it that file - so a reader with no media
     * server still meets the tab, because a video on their own storage is something they have rather
     * than a feature being advertised at them. A surface with neither sees no tab at all.
     */
    val pickerCategories: List<TilePickerCategory>
        get() = TilePickerCategory.entries.filter {
            it != TilePickerCategory.MEDIA || mediaAvailable || supportsLocalVideo
        }

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

    /**
     * Pages that actually exist, as opposed to pages the grid shows. The trailing stub and the
     * two-page display floor are conveniences of the view, so neither is something a delete can act
     * on: deleting one would remove nothing and leave the count where it was.
     */
    val realPageCount: Int
        get() = maxOf(
            (tiles.maxOfOrNull { it.pageIndex } ?: -1) + 1,
            storedPages,
            (pendingPage ?: -1) + 1
        )

    /**
     * Whether the current page can be deleted. The last remaining page is not offered: a grid with
     * no page at all is not a state the rest of the surface can render.
     */
    val canDeletePage: Boolean
        get() = !isEditing && realPageCount > 1 && page in 0 until realPageCount

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

    val engagedTile: HomeTile?
        get() = engagedTileId?.let { id -> tiles.firstOrNull { it.id == id } }

    /**
     * The file the engaged tile is playing, if it has one.
     */
    val engagedPlaybackPath: String?
        get() = engagedTileId?.let { tilePlayback[it] }

    /**
     * Whether the focused tile carries a play mode, and so has curation worth reopening.
     */
    val isFocusedTileCurated: Boolean
        get() = (focusedTile?.target as? HomeTileTargetRef.Media)?.playMode != null

    fun tileAt(target: GridCell): HomeTile? =
        tilesOnPage(page).firstOrNull { it.rect.covers(target.columnIndex, target.rowIndex) }

    val focusedGameId: Long?
        get() = (focusedTile?.target as? HomeTileTargetRef.Game)?.gameId

    val focusedMediaItemId: String?
        get() = (focusedTile?.target as? HomeTileTargetRef.Media)?.itemId

    /**
     * What confirm will do to the cell under the cursor, as a word. Read off the target rather than
     * off whether a game is there, so an app tile no longer offers to add something to a cell that
     * is already full.
     *
     * A tile whose target this build cannot read answers null: confirm does nothing on one, and a
     * hint promising otherwise is worse than no hint.
     */
    val confirmLabel: String?
        get() = when (focusedTile?.target) {
            is HomeTileTargetRef.Game -> "Play"
            is HomeTileTargetRef.Media -> "Play"
            is HomeTileTargetRef.LocalMedia -> "Play"
            is HomeTileTargetRef.App -> "Open"
            is HomeTileTargetRef.Collection -> "Open"
            is HomeTileTargetRef.VirtualCollection -> "Open"
            HomeTileTargetRef.Unresolvable -> null
            null -> "Add"
        }

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

    /**
     * What the tile menu offers. Deleting the page is listed even on an empty cell, because it acts
     * on the page rather than on whatever the cursor happens to be sitting over.
     */
    val menuActions: List<CustomTileMenuAction>
        get() = buildList {
            if (focusedTile != null) {
                add(CustomTileMenuAction.ARRANGE)
                if (isFocusedTileCurated) add(CustomTileMenuAction.RECURATE)
                add(CustomTileMenuAction.REMOVE)
            }
            if (canDeletePage) add(CustomTileMenuAction.DELETE_PAGE)
        }

    val menuDangerFromIndex: Int?
        get() = menuActions.indexOf(CustomTileMenuAction.DELETE_PAGE).takeIf { it >= 0 }

    /**
     * Rows the picker can focus. Deleting the page sits after the entries rather than among them,
     * so a search that empties the list still leaves it reachable.
     */
    val pickerFocusCount: Int
        get() = pickerEntries.size + if (canDeletePage) 1 else 0

    val isPickerDeletePageFocused: Boolean
        get() = canDeletePage && pickerFocusIndex >= pickerEntries.size

    /**
     * Whether the media setup is the thing input should be reaching. The download notice is drawn as
     * its own confirmation over the setup, so it answers separately: the two are never both the
     * target of a press.
     */
    val isMediaSetupOpen: Boolean
        get() = mediaSetup != null && mediaSetup.notice == null

    val mediaTileNotice: MediaTileNotice?
        get() = mediaSetup?.notice
}
