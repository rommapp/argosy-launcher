package com.nendo.argosy.ui.components

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.PageAudioKind
import com.nendo.argosy.data.local.entity.PageBackgroundKind
import com.nendo.argosy.data.repository.HomeTileRepository
import com.nendo.argosy.domain.model.CustomGridLayout
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeGridKind
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.SCROLL_AXIS_BOUND
import com.nendo.argosy.domain.model.TileCoverScale
import com.nendo.argosy.domain.model.TilePlacement
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.domain.model.gridKindFor
import com.nendo.argosy.domain.model.placeScrollTiles
import com.nendo.argosy.domain.model.placeTiles

/**
 * Whether a tile of this target draws a game's cover, and so can choose how that cover sits in
 * its cell. A random or continue tile shows whichever game it currently resolves to.
 */
private fun HomeTileTargetRef.showsGameCover(): Boolean = when (this) {
    is HomeTileTargetRef.Game -> true
    is HomeTileTargetRef.Feature -> kind != FeatureTileKind.RA_SUMMARY
    else -> false
}

enum class CustomTileMenuAction {
    ARRANGE,
    RECURATE,
    FIT_COVER,
    CROP_COVER,
    EDIT_FILTERS,
    EDIT_TILE,
    BROWSE_ACHIEVEMENTS,
    REMOVE,
    START_GAME_QUEUE,
    SET_FOCUS_GAME,
    ADVANCE_FOCUS_GAME,
    HIDDEN_TILES,
    PAGE_BACKDROP,
    PAGE_MUSIC,
    DELETE_PAGE;

    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            ARRANGE -> R.string.custom_tile_menu_action_arrange
            RECURATE -> R.string.custom_tile_menu_action_recurate
            FIT_COVER -> R.string.custom_tile_menu_action_fit_cover
            CROP_COVER -> R.string.custom_tile_menu_action_crop_cover
            EDIT_FILTERS -> R.string.custom_tile_menu_action_edit_filters
            EDIT_TILE -> R.string.custom_tile_menu_action_edit_tile
            BROWSE_ACHIEVEMENTS -> R.string.custom_tile_menu_action_browse_achievements
            REMOVE -> R.string.custom_tile_menu_action_remove
            START_GAME_QUEUE -> R.string.custom_tile_menu_action_start_game_queue
            SET_FOCUS_GAME -> R.string.custom_tile_menu_action_set_focus_game
            ADVANCE_FOCUS_GAME -> R.string.custom_tile_menu_action_advance_focus_game
            HIDDEN_TILES -> R.string.custom_tile_menu_action_hidden_tiles
            PAGE_BACKDROP -> R.string.custom_tile_menu_action_page_backdrop
            PAGE_MUSIC -> R.string.custom_tile_menu_action_page_music
            DELETE_PAGE -> R.string.custom_tile_menu_action_delete_page
        }
}

/**
 * Which of a page's two decorations is being chosen. Both are picked the same way, so they share a
 * chooser rather than each growing their own.
 */
enum class PageChooserKind { BACKDROP, MUSIC, FOCUS_GAME }

/**
 * What confirming a row in the page chooser does. Rows that lead somewhere are separate from rows
 * that settle the choice, so the chooser never has to guess which it is looking at.
 */
sealed interface PageChooserAction {
    data object OpenFileBrowser : PageChooserAction
    data object BrowseGameArt : PageChooserAction
    data class OpenGameArt(val gameId: Long, val title: String) : PageChooserAction
    data class UseArt(val path: String) : PageChooserAction
    data object UseGameBackground : PageChooserAction
    data class UseTrack(val path: String) : PageChooserAction
    data class UseFocusGame(val gameId: Long) : PageChooserAction
    data object UseTileAudio : PageChooserAction
    data object UseLauncherMusic : PageChooserAction
    data object ClearBackdrop : PageChooserAction
}

/**
 * A row in the page chooser. A header labels the group beneath it and is skipped by the cursor, so
 * a long list reads as sections rather than one run of rows.
 */
data class PageChooserEntry(
    val label: String,
    val subtitle: String? = null,
    val previewPath: String? = null,
    val isHeader: Boolean = false,
    val action: PageChooserAction? = null
)

/**
 * Choosing what a page shows or plays. [gameId] is set once a game has been opened, which is what
 * tells Back whether to leave the chooser or step back to the list of games.
 */
data class PageChooserState(
    val kind: PageChooserKind,
    val page: Int,
    val entries: List<PageChooserEntry> = emptyList(),
    val focusIndex: Int = 0,
    val query: String = "",
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val gameId: Long? = null,
    val gameTitle: String? = null,
    val searchFocused: Boolean = false,
    val searchKeyboardOpen: Boolean = false
) {
    val offersSearch: Boolean
        get() = gameTitle != null && gameId == null

    /**
     * Null once a game has been opened, because the heading is then that game's own title, which
     * [gameTitle] already carries.
     */
    @get:StringRes
    val titleRes: Int?
        get() = when {
            kind == PageChooserKind.FOCUS_GAME -> R.string.ui_page_chooser_title_focus_game
            gameTitle != null -> null
            kind == PageChooserKind.BACKDROP -> R.string.ui_page_chooser_title_backdrop
            else -> R.string.ui_page_chooser_title_music
        }

    @get:StringRes
    val subtitleRes: Int
        get() = when {
            kind == PageChooserKind.FOCUS_GAME -> R.string.ui_page_chooser_subtitle_focus_game
            gameId != null -> R.string.ui_page_chooser_subtitle_game_art
            kind == PageChooserKind.BACKDROP -> R.string.ui_page_chooser_subtitle_backdrop
            else -> R.string.ui_page_chooser_subtitle_music
        }
}

/**
 * What a page shows behind its tiles and what it does about sound. Held per page rather than per
 * position, so an arrangement keeps its look when the pages around it move.
 */
data class GridPageSettings(
    val backgroundKind: PageBackgroundKind = PageBackgroundKind.NONE,
    val backgroundPath: String? = null,
    val backgroundGameId: Long? = null,
    val audioKind: PageAudioKind = PageAudioKind.GLOBAL,
    val audioPath: String? = null
) {
    val hasBackground: Boolean
        get() = backgroundKind != PageBackgroundKind.NONE && backgroundPath != null

    /**
     * Whether the launcher's own music should stand aside while this page is shown.
     */
    val silencesGlobalAudio: Boolean
        get() = audioKind != PageAudioKind.GLOBAL
}

/**
 * Editing a placed tile. Moving and resizing are the same activity with the d-pad meaning two
 * different things, so they are one mode you switch within rather than two you enter separately.
 */
enum class TileEditMode { NONE, MOVE, RESIZE }

/**
 * What choosing a picker row does. [PLACE] fills the focused cell; [TRACK_RA_GAME] names the game
 * the RetroAchievements tile being set up will follow, so the same modal serves both without the
 * grid guessing from context which one it is answering.
 */
enum class TilePickerPurpose { PLACE, TRACK_RA_GAME }

/**
 * The little the grid has to know about the RetroAchievements tile's content to route a press:
 * whether anyone is signed in, whether the tile follows one game, and how many badges a browse can
 * step through. The content itself stays on the surface's own state.
 */
data class RaTileStatus(
    val signedIn: Boolean = false,
    val tracksGame: Boolean = false,
    val browseCount: Int = 0
)

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
    val menuListsHidden: Boolean = false,
    /**
     * Screens the focused tile can be launched on, listed above the tile's own actions while the
     * menu is open. Empty unless two or more screens are attached and the tile is an app or a
     * downloaded game.
     */
    val menuScreens: List<AppMenuRow.OpenOnScreen> = emptyList(),
    val showPicker: Boolean = false,
    val pickerQuery: String = "",
    val pickerSearchFocused: Boolean = false,
    val pickerKeyboardOpen: Boolean = false,
    val pickerCategory: TilePickerCategory = TilePickerCategory.GAMES,
    /**
     * The media library the picker has drilled into, or null while it is offering the libraries
     * themselves. Back steps out of a library before it closes the picker.
     */
    val pickerLibraryId: String? = null,
    val pickerFocusIndex: Int = 0,
    val pickerEntries: List<TilePickerEntry> = emptyList(),
    val pickerPurpose: TilePickerPurpose = TilePickerPurpose.PLACE,
    val mediaAvailable: Boolean = false,
    val supportsLocalVideo: Boolean = false,
    /**
     * Whether this surface can run the RetroAchievements tile's setup. It needs a picker source for
     * the games the tile can follow, which the companion does not have.
     */
    val supportsRaTileSetup: Boolean = false,
    val raTile: RaTileStatus = RaTileStatus(),
    /**
     * The tile currently holding the d-pad, or null. An engaged tile plays with sound and takes
     * the directional keys for its own transport; Menu and system Back are never taken, so there
     * is always a way out.
     */
    val engagedTileId: Long? = null,
    val engagedPaused: Boolean = false,
    /**
     * Counts seek presses since the tile was engaged, signed by direction. The player owns the
     * clock, so a press is sent as a step to take rather than a position to move to.
     */
    val engagedSeekTicks: Int = 0,
    /**
     * Which badge an engaged RetroAchievements tile has under the cursor, as a position in its
     * browse list. Meaningless for a playing tile, which has a clock rather than a list.
     */
    val engagedIndex: Int = 0,
    /**
     * Local files the media tiles on this page resolve to, keyed by tile id. Only tiles with an
     * entry here can preview; everything else draws its poster.
     */
    val tilePlayback: Map<Long, String> = emptyMap(),
    /**
     * Where playback had reached when a tile was last torn down, keyed by the file itself. A page
     * turn releases the decoders that page was using, so without this a tile restarts every time it
     * is returned to. Keyed by file rather than by tile so a tile that moves on to another episode
     * starts that one from the beginning.
     */
    val playbackPositions: Map<String, Long> = emptyMap(),
    /**
     * Per-page look and sound, keyed by the page's position. Absent means the page has never been
     * given either and draws the launcher's own background and music.
     */
    val pageSettings: Map<Int, GridPageSettings> = emptyMap(),
    val pendingBackgroundPage: Int? = null,
    val pageChooser: PageChooserState? = null,
    val mediaSetup: MediaTileSetup? = null,
    val featureSetup: FeatureTileSetup? = null,
    val showFileBrowser: Boolean = false,
    val pendingAdd: TilePickerEntry? = null,
    val pendingAddFocusIndex: Int = 0,
    val scrollAxis: HomeScrollAxis? = null
) {

    val isScrolling: Boolean
        get() = scrollAxis != null

    val kind: HomeGridKind
        get() = gridKindFor(scrollAxis)

    /**
     * The bounds tiles are placed and edited within: the page shape, with the scroll axis of a
     * scrolling grid raised to [SCROLL_AXIS_BOUND].
     */
    val layoutColumns: Int
        get() = if (scrollAxis == HomeScrollAxis.HORIZONTAL) SCROLL_AXIS_BOUND else columns

    val layoutRows: Int
        get() = if (scrollAxis == HomeScrollAxis.VERTICAL) SCROLL_AXIS_BOUND else rows

    /**
     * Lines a scrolling grid draws along its scroll axis: the tiles plus one empty line after the
     * furthest, never fewer than the screen shows or than the cursor needs. Zero for a paged grid.
     */
    val scrollExtent: Int
        get() {
            val axis = scrollAxis ?: return 0
            val visible = if (axis == HomeScrollAxis.VERTICAL) rows else columns
            val furthest = tilesOnPage(page).maxOfOrNull {
                if (axis == HomeScrollAxis.VERTICAL) it.rect.lastRow else it.rect.lastColumn
            } ?: -1
            return maxOf(visible, furthest + 2, cell.lineOn(axis) + 1)
        }

    /**
     * The bounds the cursor moves within: the page shape, or the fixed axis by [scrollExtent].
     */
    val canvasColumns: Int
        get() = if (scrollAxis == HomeScrollAxis.HORIZONTAL) scrollExtent else columns

    val canvasRows: Int
        get() = if (scrollAxis == HomeScrollAxis.VERTICAL) scrollExtent else rows

    val scrollBands: List<IntRange>
        get() {
            val axis = scrollAxis ?: return emptyList()
            val extent = scrollExtent
            val reach = IntArray(extent) { it }
            tilesOnPage(page).forEach { tile ->
                val start = if (axis == HomeScrollAxis.VERTICAL) tile.rect.rowIndex else tile.rect.columnIndex
                val end = if (axis == HomeScrollAxis.VERTICAL) tile.rect.lastRow else tile.rect.lastColumn
                if (start in 0 until extent) reach[start] = maxOf(reach[start], end.coerceAtMost(extent - 1))
            }
            val bands = mutableListOf<IntRange>()
            var bandStart = 0
            var bandEnd = -1
            for (line in 0 until extent) {
                bandEnd = maxOf(bandEnd, reach[line])
                if (line == bandEnd) {
                    bands += bandStart..bandEnd
                    bandStart = line + 1
                }
            }
            return bands
        }

    val focusedBandIndex: Int
        get() {
            val axis = scrollAxis ?: return 0
            val line = cell.lineOn(axis)
            return scrollBands.indexOfFirst { line in it }.coerceAtLeast(0)
        }

    private fun GridCell.lineOn(axis: HomeScrollAxis): Int =
        if (axis == HomeScrollAxis.VERTICAL) rowIndex else columnIndex

    /**
     * The kinds this grid can currently be filled from.
     *
     * Media stands on either of two feet. A signed-in account gives it library titles, and a surface
     * that can play a file already on the device gives it that file - so a reader with no media
     * server still meets the tab, because a video on their own storage is something they have rather
     * than a feature being advertised at them. A surface with neither sees no tab at all.
     */
    val pickerCategories: List<TilePickerCategory>
        get() = if (pickerPurpose == TilePickerPurpose.TRACK_RA_GAME) {
            listOf(TilePickerCategory.GAMES)
        } else {
            TilePickerCategory.entries.filter {
                it != TilePickerCategory.MEDIA || mediaAvailable || supportsLocalVideo
            }
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
        get() = !isScrolling && !isEditing && realPageCount > 1 && page in 0 until realPageCount

    val isOnAddPage: Boolean
        get() = !isScrolling && page >= pageCount

    val isEditing: Boolean
        get() = editMode != TileEditMode.NONE

    @get:StringRes
    val editLabelRes: Int?
        get() = when (editMode) {
            TileEditMode.MOVE -> R.string.ui_custom_grid_edit_move
            TileEditMode.RESIZE -> R.string.ui_custom_grid_edit_resize
            TileEditMode.NONE -> null
        }

    /**
     * The page as it currently looks, with the tile being arranged shown at its draft rectangle.
     * The draft lives here rather than in the database so an interrupted edit leaves the stored
     * page exactly as it was.
     */
    fun tilesOnPage(pageIndex: Int): List<HomeTile> {
        val shown = placementOnPage(pageIndex).placed
        val editingId = editingTileId
        val rect = editingRect
        if (editingId == null || rect == null) return shown
        val withoutEditing = shown.filter { it.id != editingId }
        if (editingPage != pageIndex) return withoutEditing
        val carried = tiles.firstOrNull { it.id == editingId } ?: return withoutEditing
        return withoutEditing + carried.copy(rect = onScreen(rect), pageIndex = pageIndex)
    }

    /**
     * Stored tiles on [pageIndex] this screen cannot draw, in stored order. The tile being arranged
     * is never among them.
     */
    fun hiddenTilesOnPage(pageIndex: Int): List<HomeTile> =
        placementOnPage(pageIndex).displaced.filter { it.id != editingTileId }

    val hiddenTiles: List<HomeTile>
        get() = if (isOnAddPage) emptyList() else hiddenTilesOnPage(page)

    private fun placementOnPage(pageIndex: Int): TilePlacement {
        val stored = tiles.filter { it.pageIndex == pageIndex && it.kind == kind }
        if (columns <= 0 || rows <= 0) return TilePlacement(stored, emptyList())
        val axis = scrollAxis ?: return placeTiles(stored, layoutColumns, layoutRows)
        val lanes = CustomGridLayout(CustomGridShape(columns, rows), axis).lanes
        return TilePlacement(placeScrollTiles(stored, lanes, axis), emptyList())
    }

    private fun onScreen(rect: TileRect): TileRect =
        if (columns <= 0 || rows <= 0) rect else rect.trimmedTo(layoutColumns, layoutRows)

    /**
     * The tile being arranged, at its draft rectangle trimmed to this screen. Held by id so the tile
     * picked up stays the one that moves when two tiles cover the same cell.
     */
    val editingTile: HomeTile?
        get() = editingTileId?.let { id ->
            tiles.firstOrNull { it.id == id }
                ?.let { if (editingRect != null) it.copy(rect = onScreen(editingRect)) else it }
        }

    /**
     * The tile the next action applies to. While arranging that is the tile picked up, not whatever
     * happens to sit under the cursor: overlap is allowed there, so the cell can belong to two.
     */
    val focusedTile: HomeTile?
        get() = editingTile ?: tilesOnPage(page).firstOrNull {
            it.rect.covers(cell.columnIndex, cell.rowIndex)
        }

    val currentPageSettings: GridPageSettings
        get() = if (isScrolling) GridPageSettings() else pageSettings[page] ?: GridPageSettings()

    val engagedTile: HomeTile?
        get() = engagedTileId?.let { id -> tiles.firstOrNull { it.id == id } }

    /**
     * The file the engaged tile is playing, if it has one.
     */
    val engagedPlaybackPath: String?
        get() = engagedTileId?.let { tilePlayback[it] }

    /**
     * Whether the engaged tile is the RetroAchievements one, whose d-pad steps through badges
     * rather than scrubbing a clock.
     */
    val engagedRaTile: Boolean
        get() = engagedTile?.target.isRaSummary()

    val isFocusedRaTile: Boolean
        get() = focusedTile?.target.isRaSummary()

    private fun HomeTileTargetRef?.isRaSummary(): Boolean =
        (this as? HomeTileTargetRef.Feature)?.kind == FeatureTileKind.RA_SUMMARY

    /**
     * Whether the focused tile carries a play mode, and so has curation worth reopening.
     */
    val isFocusedTileCurated: Boolean
        get() = (focusedTile?.target as? HomeTileTargetRef.Media)?.playMode != null

    /**
     * The collection tile under the cursor, if it is one. A collection can be played through rather
     * than opened, so it is the one target that answers to more than a single action.
     */
    val focusedCollection: HomeTileTargetRef.Collection?
        get() = focusedTile?.target as? HomeTileTargetRef.Collection

    fun tileAt(target: GridCell): HomeTile? =
        tilesOnPage(page).firstOrNull { it.rect.covers(target.columnIndex, target.rowIndex) }

    /**
     * The game under the cursor, which for a collection running as a queue is the game it would
     * launch. Everything acting on "the game here" then agrees, rather than a queue tile reading as
     * no game at all.
     */
    val focusedGameId: Long?
        get() = when (val target = focusedTile?.target) {
            is HomeTileTargetRef.Game -> target.gameId
            is HomeTileTargetRef.Collection -> target.focusGameId
            is HomeTileTargetRef.Feature ->
                target.pickedGameId.takeIf { target.kind == FeatureTileKind.RANDOM_GAME }
            else -> null
        }

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
    @get:StringRes
    val confirmLabelRes: Int?
        get() = when (val target = focusedTile?.target) {
            is HomeTileTargetRef.Game -> R.string.ui_custom_grid_confirm_play
            is HomeTileTargetRef.Media -> R.string.ui_custom_grid_confirm_play
            is HomeTileTargetRef.LocalMedia -> R.string.ui_custom_grid_confirm_play
            is HomeTileTargetRef.App -> R.string.ui_custom_grid_confirm_open
            is HomeTileTargetRef.Collection -> if (target.focusGameId != null) {
                R.string.ui_custom_grid_confirm_play
            } else {
                R.string.ui_custom_grid_confirm_open
            }
            is HomeTileTargetRef.VirtualCollection -> R.string.ui_custom_grid_confirm_open
            is HomeTileTargetRef.Feature -> when {
                target.kind == FeatureTileKind.LIBRARY_LINK -> R.string.ui_custom_grid_confirm_open
                target.kind != FeatureTileKind.RA_SUMMARY -> R.string.ui_custom_grid_confirm_play
                !raTile.signedIn -> R.string.ui_custom_grid_confirm_sign_in
                raTile.tracksGame -> R.string.ui_custom_grid_confirm_play
                raTile.browseCount > 0 -> R.string.ui_custom_grid_confirm_browse
                else -> R.string.ui_custom_grid_confirm_library
            }
            HomeTileTargetRef.Unresolvable -> null
            null -> R.string.ui_custom_grid_confirm_add
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
     * What the tile menu offers, in three zones: what this tile is, then this page, then the rows
     * that destroy something. Within the tile's own zone the rows that act now come before the ones
     * that reconfigure it. Deleting the page is listed even on an empty cell, because it acts on the
     * page rather than on whatever the cursor happens to be sitting over.
     */
    val menuActions: List<CustomTileMenuAction>
        get() = buildList {
            val focused = focusedTile
            if (focused != null) {
                val feature = focused.target as? HomeTileTargetRef.Feature
                if (feature?.kind == FeatureTileKind.RA_SUMMARY &&
                    raTile.tracksGame && raTile.browseCount > 0
                ) {
                    add(CustomTileMenuAction.BROWSE_ACHIEVEMENTS)
                }
                focusedCollection?.let { collection ->
                    if (collection.focusGameId == null) {
                        add(CustomTileMenuAction.START_GAME_QUEUE)
                    } else {
                        add(CustomTileMenuAction.ADVANCE_FOCUS_GAME)
                        add(CustomTileMenuAction.SET_FOCUS_GAME)
                    }
                }
                if (isFocusedTileCurated) add(CustomTileMenuAction.RECURATE)
                if (feature?.kind == FeatureTileKind.RANDOM_GAME ||
                    feature?.kind == FeatureTileKind.LIBRARY_LINK
                ) {
                    add(CustomTileMenuAction.EDIT_FILTERS)
                }
                if (feature?.kind == FeatureTileKind.RA_SUMMARY && supportsRaTileSetup) {
                    add(CustomTileMenuAction.EDIT_TILE)
                }
                if (focused.target.showsGameCover()) {
                    add(
                        if (focused.coverScale == TileCoverScale.FIT) {
                            CustomTileMenuAction.CROP_COVER
                        } else {
                            CustomTileMenuAction.FIT_COVER
                        }
                    )
                }
                add(CustomTileMenuAction.ARRANGE)
            }
            if (!isOnAddPage) {
                if (!isEditing && hiddenTiles.isNotEmpty()) add(CustomTileMenuAction.HIDDEN_TILES)
                if (!isScrolling) {
                    add(CustomTileMenuAction.PAGE_BACKDROP)
                    add(CustomTileMenuAction.PAGE_MUSIC)
                }
            }
            if (focused != null) add(CustomTileMenuAction.REMOVE)
            if (canDeletePage) add(CustomTileMenuAction.DELETE_PAGE)
        }

    /**
     * Where the trailing run of destructive rows begins. Removing a tile is as destructive as
     * deleting the page and now sits beside it, so the fence opens at whichever of the two the menu
     * reaches first rather than at the page row alone.
     */
    val menuEntryCount: Int
        get() = if (menuListsHidden) hiddenTiles.size else menuScreens.size + menuActions.size

    val menuDangerFromIndex: Int?
        get() = if (menuListsHidden) null else menuActions
            .indexOfFirst {
                it == CustomTileMenuAction.REMOVE || it == CustomTileMenuAction.DELETE_PAGE
            }
            .takeIf { it >= 0 }
            ?.plus(menuScreens.size)

    @get:StringRes
    val menuScreenLabelRes: Int
        get() = if (focusedTile?.target is HomeTileTargetRef.App) {
            R.string.custom_tile_menu_action_open_on_screen
        } else {
            R.string.custom_tile_menu_action_play_on_screen
        }

    fun menuScreenAt(index: Int): AppMenuRow.OpenOnScreen? =
        if (menuListsHidden) null else menuScreens.getOrNull(index)

    fun menuActionAt(index: Int): CustomTileMenuAction? =
        if (menuListsHidden) null else menuActions.getOrNull(index - menuScreens.size)

    /**
     * Whether the picker ends in the row that deletes the page. Naming a game for the
     * RetroAchievements tile is a question about one tile, so that row stays out of it.
     */
    val pickerOffersDeletePage: Boolean
        get() = canDeletePage && pickerPurpose == TilePickerPurpose.PLACE

    /**
     * Rows the picker can focus. Deleting the page sits after the entries rather than among them,
     * so a search that empties the list still leaves it reachable.
     */
    val pickerFocusCount: Int
        get() = pickerEntries.size + if (pickerOffersDeletePage) 1 else 0

    val isPickerDeletePageFocused: Boolean
        get() = pickerOffersDeletePage && pickerFocusIndex >= pickerEntries.size

    /**
     * Whether the media setup is the thing input should be reaching. The download notice is drawn as
     * its own confirmation over the setup, so it answers separately: the two are never both the
     * target of a press.
     */
    val isMediaSetupOpen: Boolean
        get() = mediaSetup != null && mediaSetup.notice == null

    /**
     * Whether the feature setup is the thing input should be reaching. While the setup has handed
     * the screen to the picker to name a game, the setup is still there to come back to but is not
     * what a press lands on.
     */
    val isFeatureSetupOpen: Boolean
        get() = featureSetup != null && pickerPurpose == TilePickerPurpose.PLACE

    val mediaTileNotice: MediaTileNotice?
        get() = mediaSetup?.notice
}
