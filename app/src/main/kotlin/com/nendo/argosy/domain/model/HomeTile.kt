package com.nendo.argosy.domain.model

import com.nendo.argosy.data.local.entity.MediaTilePlayMode

/**
 * The smallest a media tile is allowed to be. A moving picture in a one-cell square reads as an
 * artefact rather than as something playing, so the kind carries a floor the placement and resize
 * paths both obey.
 */
const val MEDIA_TILE_MIN_SPAN = 2

/**
 * A tile's rectangle on a page, anchored at its top-left cell. Growth runs down and right from the
 * anchor, so extending a tile never moves it.
 */
data class TileRect(
    val columnIndex: Int,
    val rowIndex: Int,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1
) {
    val lastColumn: Int get() = columnIndex + columnSpan - 1
    val lastRow: Int get() = rowIndex + rowSpan - 1

    fun covers(column: Int, row: Int): Boolean =
        column in columnIndex..lastColumn && row in rowIndex..lastRow

    fun overlaps(other: TileRect): Boolean =
        columnIndex <= other.lastColumn && other.columnIndex <= lastColumn &&
            rowIndex <= other.lastRow && other.rowIndex <= lastRow

    fun withinBounds(columns: Int, rows: Int): Boolean =
        columnIndex >= 0 && rowIndex >= 0 && lastColumn < columns && lastRow < rows

    /**
     * The same rectangle grown to at least [span] in both directions. Growth runs down and right, so
     * the anchor is untouched and a tile raised to its floor stays where its owner put it.
     */
    fun atLeast(span: Int): TileRect =
        copy(columnSpan = maxOf(columnSpan, span), rowSpan = maxOf(rowSpan, span))

    /**
     * The part of this rectangle on a page of [columns] by [rows], anchor unchanged. Spans never drop
     * below one, so an anchor already off the page stays off it.
     */
    fun trimmedTo(columns: Int, rows: Int): TileRect = copy(
        columnSpan = columnSpan.coerceAtMost(columns - columnIndex).coerceAtLeast(1),
        rowSpan = rowSpan.coerceAtMost(rows - rowIndex).coerceAtLeast(1)
    )
}

/**
 * What a tile points at, resolved. A tile outlives its target, so the unresolvable case is a value
 * rather than an absence: the grid can then show the gap it leaves instead of quietly reflowing a
 * page the user arranged by hand.
 */
sealed interface HomeTileTargetRef {
    data class Game(val gameId: Long) : HomeTileTargetRef
    /**
     * [focusGameId] turns the tile into a queue: the game being played now, launched directly, with
     * the collection behind it as the list to advance through. Null is a plain collection tile.
     * [coverSource] is a chosen picture drawn in place of the tile's own art.
     */
    data class Collection(
        val collectionId: Long,
        val focusGameId: Long? = null,
        val coverSource: TileImageSource? = null
    ) : HomeTileTargetRef
    data class VirtualCollection(val type: String, val name: String) : HomeTileTargetRef
    data class App(val packageName: String) : HomeTileTargetRef

    /**
     * A show or a film on the media server. It is the item's own id rather than an episode's, because
     * a pinned show stands for the show: which episode a press starts is worked out at the moment of
     * the press, the way it is on a media row, and baking one in would leave the tile pointing at an
     * episode that has since been watched.
     */
    data class Media(
        val itemId: String,
        val playMode: MediaTilePlayMode = MediaTilePlayMode.SINGLE,
        val scopeId: String? = null
    ) : HomeTileTargetRef

    /**
     * A video or animation already on this device, named by its path rather than by a library id.
     * A sibling of [App]: it stands for something outside the media library entirely, so nothing has
     * to be fetched before it plays and nothing on a server can take it away.
     */
    data class LocalMedia(val filePath: String) : HomeTileTargetRef

    /**
     * A tile that performs an action. Each kind reads the one field it owns: [filters] on
     * [FeatureTileKind.RANDOM_GAME], [libraryLink] on [FeatureTileKind.LIBRARY_LINK], and
     * [pickedGameId] on [FeatureTileKind.RA_SUMMARY], null there meaning the account overview.
     * [coverSource] is read on [FeatureTileKind.LIBRARY_LINK] only: a chosen picture drawn in place
     * of the cover of the first matching game.
     */
    data class Feature(
        val kind: FeatureTileKind,
        val filters: RandomTileFilters = RandomTileFilters(),
        val pickedGameId: Long? = null,
        val libraryLink: LibraryLinkFilters? = null,
        val coverSource: TileImageSource? = null
    ) : HomeTileTargetRef

    /**
     * A tile that holds other tiles. [rule] null is a hand-filled folder whose members are the tiles
     * whose [HomeTile.parentTileId] names it; a rule makes it an auto-folder whose members are the
     * games the rule matches, and it holds no tiles of its own.
     */
    data class Folder(
        val name: String,
        val rule: LibraryLinkFilters? = null,
        val appearance: FolderAppearance = FolderAppearance()
    ) : HomeTileTargetRef

    /**
     * A picture filling its cells, cropped like a cover. Never focused outside decoration editing.
     */
    data class Image(val source: TileImageSource) : HomeTileTargetRef

    /**
     * A picture with transparency drawn without tile chrome, fitted inside its cells, with an
     * optional outline traced around its opaque pixels. Never focused outside decoration editing.
     */
    data class Sticker(
        val source: TileImageSource,
        val outline: StickerOutline = StickerOutline()
    ) : HomeTileTargetRef

    data object Unresolvable : HomeTileTargetRef
}

/**
 * Where an image or sticker tile reads its picture: a file on this device, or art Argosy already
 * holds for a game.
 */
sealed interface TileImageSource {
    data class File(val path: String) : TileImageSource
    data class GameArt(val gameId: Long, val slot: TileArtSlot) : TileImageSource
}

enum class TileArtSlot {
    COVER,
    LOGO,
    BACKGROUND;

    companion object {
        fun fromStored(value: String?): TileArtSlot? = entries.find { it.name == value }
    }
}

enum class StickerOutlineWidth {
    THIN,
    MEDIUM,
    THICK;

    companion object {
        fun fromStored(value: String?): StickerOutlineWidth = entries.find { it.name == value } ?: MEDIUM
    }
}

enum class StickerOutlineColor {
    WHITE,
    BLACK,
    ACCENT;

    companion object {
        fun fromStored(value: String?): StickerOutlineColor = entries.find { it.name == value } ?: WHITE
    }
}

data class StickerOutline(
    val enabled: Boolean = false,
    val width: StickerOutlineWidth = StickerOutlineWidth.MEDIUM,
    val color: StickerOutlineColor = StickerOutlineColor.WHITE
)

enum class FolderCoverStyle {
    MOSAIC,
    IMAGE,
    STICKER;

    companion object {
        fun fromStored(value: String?): FolderCoverStyle = entries.find { it.name == value } ?: MOSAIC
    }
}

enum class FolderBackdropStyle {
    NONE,
    COVER_BLURRED,
    IMAGE;

    companion object {
        fun fromStored(value: String?): FolderBackdropStyle = entries.find { it.name == value } ?: NONE
    }
}

enum class FolderAccent {
    THEME,
    ROSE,
    AMBER,
    LIME,
    SKY,
    VIOLET;

    companion object {
        fun fromStored(value: String?): FolderAccent = entries.find { it.name == value } ?: THEME
    }
}

enum class FolderMemberSize {
    SMALL,
    MEDIUM,
    LARGE;

    companion object {
        fun fromStored(value: String?): FolderMemberSize = entries.find { it.name == value } ?: MEDIUM
    }
}

/**
 * How a folder looks on the grid and once opened. [cover] other than [FolderCoverStyle.MOSAIC] draws
 * [coverSource], falling back to the mosaic while no source is chosen. [FolderBackdropStyle.COVER_BLURRED]
 * blurs the chosen cover picture, or the first member's cover when the folder shows a mosaic.
 */
data class FolderAppearance(
    val cover: FolderCoverStyle = FolderCoverStyle.MOSAIC,
    val coverSource: TileImageSource? = null,
    val coverOutline: StickerOutline = StickerOutline(),
    val showName: Boolean = true,
    val backdrop: FolderBackdropStyle = FolderBackdropStyle.NONE,
    val backdropSource: TileImageSource? = null,
    val accent: FolderAccent = FolderAccent.THEME,
    val memberSize: FolderMemberSize = FolderMemberSize.MEDIUM
) {
    val pictureSources: List<TileImageSource>
        get() = listOfNotNull(coverSource, backdropSource)
}

/**
 * Whether a tile is decoration: drawn on the grid but skipped by the cursor unless decorations are
 * being edited.
 */
val HomeTileTargetRef.isDecor: Boolean
    get() = this is HomeTileTargetRef.Image || this is HomeTileTargetRef.Sticker

/**
 * Whether a tile of this target can be given a chosen cover picture: a collection or a library link.
 */
val HomeTileTargetRef.acceptsCustomCover: Boolean
    get() = this is HomeTileTargetRef.Collection ||
        (this is HomeTileTargetRef.Feature && kind == FeatureTileKind.LIBRARY_LINK)

/**
 * The chosen cover picture this target draws in place of its own art, or null for its own art.
 */
val HomeTileTargetRef.customCover: TileImageSource?
    get() = when (this) {
        is HomeTileTargetRef.Collection -> coverSource
        is HomeTileTargetRef.Feature -> coverSource.takeIf { kind == FeatureTileKind.LIBRARY_LINK }
        else -> null
    }

/**
 * This target with [source] as its chosen cover picture, null clearing it. Answers null for a target
 * that does not [acceptsCustomCover].
 */
fun HomeTileTargetRef.withCustomCover(source: TileImageSource?): HomeTileTargetRef? = when {
    this is HomeTileTargetRef.Collection -> copy(coverSource = source)
    this is HomeTileTargetRef.Feature && kind == FeatureTileKind.LIBRARY_LINK -> copy(coverSource = source)
    else -> null
}

enum class FeatureTileKind {
    RANDOM_GAME,
    CONTINUE,
    RA_SUMMARY,
    LIBRARY_LINK;

    companion object {
        fun fromStored(value: String?): FeatureTileKind? = entries.find { it.name == value }
    }
}

/**
 * What a random game tile may pick from. Platforms are ids and genres are the raw values the
 * server sent, never display names, so a rename on either side leaves the stored filter intact.
 */
data class RandomTileFilters(
    val downloadedOnly: Boolean = true,
    val neverPlayed: Boolean = false,
    val platformIds: Set<Long> = emptySet(),
    val genres: Set<String> = emptySet()
)

/**
 * The floor [target] imposes on a tile's span. Everything but media sits happily in one cell.
 */
fun minimumSpanFor(target: HomeTileTargetRef): Int = when (target) {
    is HomeTileTargetRef.Media, is HomeTileTargetRef.LocalMedia -> MEDIA_TILE_MIN_SPAN
    else -> 1
}

/**
 * How a tile draws its cover inside its cell. [CROP] fills the cell and loses the edges of art
 * whose shape differs from the cell; [FIT] keeps the whole cover and leaves the cell showing
 * around it. Chosen per tile, because the same page holds art of several shapes.
 */
enum class TileCoverScale {
    CROP,
    FIT;

    companion object {
        fun fromString(value: String?): TileCoverScale =
            entries.find { it.name == value } ?: CROP
    }
}

/**
 * [playlist] is the run a media tile was told to play, in the order it was chosen. It is empty for
 * every other kind and for every play mode that works out its own run.
 *
 * [parentTileId] names the folder holding this tile, or null for a tile on the grid itself. A
 * folder member is never placed on a page; its [rect] column is its position inside the folder.
 */
data class HomeTile(
    val id: Long,
    val pageIndex: Int,
    val rect: TileRect,
    val target: HomeTileTargetRef,
    val playlist: List<String> = emptyList(),
    val coverScale: TileCoverScale = TileCoverScale.CROP,
    val kind: HomeGridKind = HomeGridKind.PAGED,
    val parentTileId: Long? = null
) {
    val minSpan: Int get() = minimumSpanFor(target)

    val isInFolder: Boolean get() = parentTileId != null
}

/**
 * The page index a folder member is stored under, so no page query, page count or page delete ever
 * reads one as a tile on a page.
 */
const val FOLDER_MEMBER_PAGE_INDEX = -1

/**
 * The members of [folderId] among [tiles], in their order inside the folder.
 */
fun folderMembers(tiles: List<HomeTile>, folderId: Long): List<HomeTile> =
    tiles.filter { it.parentTileId == folderId }.sortedWith(compareBy({ it.rect.columnIndex }, { it.id }))

/**
 * The bound a scrolling grid gives its scroll axis wherever the placement rules take a page size.
 */
const val SCROLL_AXIS_BOUND = 100_000

/**
 * The first [columnSpan] by [rowSpan] rectangle clear of [taken] on a canvas with [lanes] cells
 * across and no end along [axis], scanning line by line along [axis] and across each line from its
 * start. The span across the lanes is cut to fit them.
 */
fun firstFreeScrollRect(
    taken: List<TileRect>,
    lanes: Int,
    axis: HomeScrollAxis,
    columnSpan: Int,
    rowSpan: Int
): TileRect {
    val laneCount = lanes.coerceAtLeast(1)
    val crossSpan = (if (axis == HomeScrollAxis.VERTICAL) columnSpan else rowSpan).coerceIn(1, laneCount)
    val alongSpan = (if (axis == HomeScrollAxis.VERTICAL) rowSpan else columnSpan).coerceAtLeast(1)
    val lastLine = taken.maxOfOrNull { if (axis == HomeScrollAxis.VERTICAL) it.lastRow else it.lastColumn } ?: -1
    for (line in 0..lastLine + 1) {
        for (lane in 0..laneCount - crossSpan) {
            val candidate = if (axis == HomeScrollAxis.VERTICAL) {
                TileRect(lane, line, crossSpan, alongSpan)
            } else {
                TileRect(line, lane, alongSpan, crossSpan)
            }
            if (taken.none { it.overlaps(candidate) }) return candidate
        }
    }
    return if (axis == HomeScrollAxis.VERTICAL) {
        TileRect(0, lastLine + 1, crossSpan, alongSpan)
    } else {
        TileRect(lastLine + 1, 0, alongSpan, crossSpan)
    }
}

/**
 * How [tiles] show on a scrolling canvas with [lanes] cells across [axis], in stored order. A tile
 * keeps its stored rectangle, cut to the lanes, where that is clear; any other tile takes the first
 * free spot of its size. No tile is ever displaced, and nothing here is written back.
 */
fun placeScrollTiles(tiles: List<HomeTile>, lanes: Int, axis: HomeScrollAxis): List<HomeTile> {
    val placed = mutableListOf<HomeTile>()
    for (tile in tiles.filterNot { it.isInFolder }) {
        val sized = scrollSized(tile, lanes, axis)
        val taken = placed.map { it.rect }
        val kept = sized.takeIf { rect -> rect.withinLanes(lanes, axis) && taken.none { it.overlaps(rect) } }
        placed += tile.copy(
            rect = kept ?: firstFreeScrollRect(taken, lanes, axis, sized.columnSpan, sized.rowSpan)
        )
    }
    return placed
}

/**
 * [tiles] repacked onto the scrolling canvas [to] describes, read in [from]'s order: along its
 * scroll axis first, then across it. Each tile keeps its spans, the one across the lanes cut to
 * [to]'s lanes but never below the tile's minimum span unless the lanes themselves are narrower.
 * [tiles] come back unchanged when [from] and [to] are not both scrolling or share axis and lanes.
 */
fun reflowScrollTiles(
    tiles: List<HomeTile>,
    from: CustomGridLayout,
    to: CustomGridLayout
): List<HomeTile> {
    val fromAxis = from.scrollAxis ?: return tiles
    val toAxis = to.scrollAxis ?: return tiles
    if (!from.needsScrollReflowTo(to)) return tiles
    val readingOrder = if (fromAxis == HomeScrollAxis.VERTICAL) {
        compareBy<HomeTile>({ it.rect.rowIndex }, { it.rect.columnIndex }, { it.id })
    } else {
        compareBy<HomeTile>({ it.rect.columnIndex }, { it.rect.rowIndex }, { it.id })
    }
    val placed = mutableListOf<HomeTile>()
    for (tile in tiles.filterNot { it.isInFolder }.sortedWith(readingOrder)) {
        val sized = scrollSized(tile, to.lanes, toAxis)
        placed += tile.copy(
            rect = firstFreeScrollRect(placed.map { it.rect }, to.lanes, toAxis, sized.columnSpan, sized.rowSpan)
        )
    }
    return placed
}

private fun scrollSized(tile: HomeTile, lanes: Int, axis: HomeScrollAxis): TileRect {
    val laneCount = lanes.coerceAtLeast(1)
    val floor = tile.minSpan.coerceAtMost(laneCount)
    val rect = tile.rect.atLeast(tile.minSpan)
    return if (axis == HomeScrollAxis.VERTICAL) {
        rect.copy(columnSpan = rect.columnSpan.coerceIn(floor, laneCount))
    } else {
        rect.copy(rowSpan = rect.rowSpan.coerceIn(floor, laneCount))
    }
}

private fun TileRect.withinLanes(lanes: Int, axis: HomeScrollAxis): Boolean =
    columnIndex >= 0 && rowIndex >= 0 &&
        (if (axis == HomeScrollAxis.VERTICAL) lastColumn else lastRow) < lanes

/**
 * How [tiles] show on a page of [columns] by [rows] on one screen, in stored order with the first
 * claim on a cell winning. A tile whose anchor is on the page and free is drawn trimmed to the edge
 * and to its neighbours; it is [TilePlacement.displaced] when its anchor is off the page or taken, or
 * when the trimmed rectangle falls below its minimum span. Nothing here is ever written back.
 */
fun placeTiles(tiles: List<HomeTile>, columns: Int, rows: Int): TilePlacement {
    val placed = mutableListOf<HomeTile>()
    val displaced = mutableListOf<HomeTile>()
    for (tile in tiles.filterNot { it.isInFolder }) {
        val anchor = TileRect(tile.rect.columnIndex, tile.rect.rowIndex)
        if (!anchor.withinBounds(columns, rows) || placed.any { it.rect.overlaps(anchor) }) {
            displaced += tile
            continue
        }
        var fitted = tile.rect.atLeast(tile.minSpan).trimmedTo(columns, rows)
        while (placed.any { it.rect.overlaps(fitted) }) {
            fitted = when {
                fitted.columnSpan > 1 -> fitted.copy(columnSpan = fitted.columnSpan - 1)
                fitted.rowSpan > 1 -> fitted.copy(rowSpan = fitted.rowSpan - 1)
                else -> break
            }
        }
        if (fitted.columnSpan < tile.minSpan || fitted.rowSpan < tile.minSpan) {
            displaced += tile
            continue
        }
        placed += tile.copy(rect = fitted)
    }
    return TilePlacement(placed, displaced)
}

data class TilePlacement(val placed: List<HomeTile>, val displaced: List<HomeTile>)

/**
 * A tile placed at [cell] carrying whatever of its own floor the page has room for. A media tile
 * dropped into the last free corner of a page cannot always be two cells wide, and clipping it off
 * the edge would be worse than showing it smaller than its kind prefers.
 */
private fun anchoredAt(cell: GridCell, tile: HomeTile, columns: Int, rows: Int): TileRect {
    val span = tile.minSpan
    return TileRect(
        columnIndex = cell.columnIndex,
        rowIndex = cell.rowIndex,
        columnSpan = span.coerceAtMost((columns - cell.columnIndex).coerceAtLeast(1)),
        rowSpan = span.coerceAtMost((rows - cell.rowIndex).coerceAtLeast(1))
    )
}

/**
 * A cell on a page. Focus lives on a cell rather than on a tile so an empty slot is somewhere the
 * cursor can be, which is what lets a tile be placed at a chosen spot rather than appended.
 */
data class GridCell(val columnIndex: Int, val rowIndex: Int)

sealed interface CustomGridMove {
    data class Focus(val cell: GridCell) : CustomGridMove
    data object PreviousPage : CustomGridMove
    data object NextPage : CustomGridMove
    data object None : CustomGridMove
}

/**
 * Where focus lands moving [direction] from [cell] across a page of [columns] by [rows].
 *
 * A step starts from the far edge of the tile holding [cell], so leaving a multi-cell tile takes
 * one press. Running off the left or right edge turns the page; running off the top or bottom
 * stops. Tiles [isFocusable] rejects are stepped over, and folder members are never on the page.
 */
fun customGridStep(
    cell: GridCell,
    tiles: List<HomeTile>,
    columns: Int,
    rows: Int,
    direction: GridDirection2D,
    isFocusable: (HomeTile) -> Boolean = { true }
): CustomGridMove {
    if (columns <= 0 || rows <= 0) return CustomGridMove.None
    val onPage = tiles.filterNot { it.isInFolder }
    var origin = onPage.firstOrNull { it.rect.covers(cell.columnIndex, cell.rowIndex) }?.rect
        ?: TileRect(cell.columnIndex, cell.rowIndex)
    while (true) {
        val target = when (direction) {
            GridDirection2D.LEFT -> GridCell(origin.columnIndex - 1, cell.rowIndex)
            GridDirection2D.RIGHT -> GridCell(origin.lastColumn + 1, cell.rowIndex)
            GridDirection2D.UP -> GridCell(cell.columnIndex, origin.rowIndex - 1)
            GridDirection2D.DOWN -> GridCell(cell.columnIndex, origin.lastRow + 1)
        }
        val offPage = target.columnIndex !in 0 until columns || target.rowIndex !in 0 until rows
        if (offPage) {
            return when (direction) {
                GridDirection2D.LEFT -> CustomGridMove.PreviousPage
                GridDirection2D.RIGHT -> CustomGridMove.NextPage
                else -> CustomGridMove.None
            }
        }
        val skipped = onPage.firstOrNull {
            it.rect.covers(target.columnIndex, target.rowIndex) && !isFocusable(it)
        } ?: return CustomGridMove.Focus(target)
        origin = skipped.rect
    }
}

enum class GridDirection2D { LEFT, RIGHT, UP, DOWN }

/**
 * Settles a page after a tile has been dropped somewhere it overlaps others.
 *
 * Editing is deliberately unconstrained: refusing a move mid-drag makes arranging a full page a
 * puzzle, so the collision is allowed to happen and resolved once. Displaced tiles are pushed clear
 * first, then shrunk, then relocated to the first free cell, in that order, because moving a tile
 * keeps it the size its owner chose while shrinking changes it, and relocating loses its place.
 *
 * [placed] holds the settled tiles including [editing]; [dropped] holds any that could not be fitted
 * anywhere and are reported rather than silently deleted.
 */
fun settleAfterEdit(
    editing: HomeTile,
    others: List<HomeTile>,
    columns: Int,
    rows: Int
): TilePlacement {
    val settled = mutableListOf(editing)
    val dropped = mutableListOf<HomeTile>()
    val onPage = others.filterNot { it.isInFolder }
    val untouched = onPage.filterNot { it.rect.overlaps(editing.rect) }
    settled += untouched
    for (tile in onPage.filter { it.rect.overlaps(editing.rect) }) {
        val pushed = pushClear(tile, editing.rect, settled, columns, rows)
        if (pushed != null) {
            settled += tile.copy(rect = pushed)
            continue
        }
        val shrunk = shrinkClear(tile, settled, columns, rows)
        if (shrunk != null) {
            settled += tile.copy(rect = shrunk)
            continue
        }
        val relocated = firstFreeCell(settled, columns, rows)
        if (relocated != null) {
            settled += tile.copy(rect = anchoredAt(relocated, tile, columns, rows))
            continue
        }
        dropped += tile
    }
    return TilePlacement(settled, dropped)
}

private fun fits(rect: TileRect, taken: List<HomeTile>, columns: Int, rows: Int): Boolean =
    rect.withinBounds(columns, rows) && taken.none { it.rect.overlaps(rect) }

private fun pushClear(
    tile: HomeTile,
    against: TileRect,
    taken: List<HomeTile>,
    columns: Int,
    rows: Int
): TileRect? {
    val candidates = listOf(
        tile.rect.copy(columnIndex = against.lastColumn + 1),
        tile.rect.copy(columnIndex = against.columnIndex - tile.rect.columnSpan),
        tile.rect.copy(rowIndex = against.lastRow + 1),
        tile.rect.copy(rowIndex = against.rowIndex - tile.rect.rowSpan)
    )
    return candidates.firstOrNull { fits(it, taken, columns, rows) }
}

/**
 * Shrinks [tile] until it clears everything already settled. The tile's own floor is tried first and
 * only abandoned when nothing at that size fits, so a media tile gives up its minimum only to avoid
 * being dropped from the page entirely.
 */
private fun shrinkClear(
    tile: HomeTile,
    taken: List<HomeTile>,
    columns: Int,
    rows: Int
): TileRect? {
    var rect = tile.rect.atLeast(tile.minSpan)
    if (fits(rect, taken, columns, rows)) return rect
    while (rect.columnSpan > 1 || rect.rowSpan > 1) {
        rect = if (rect.columnSpan >= rect.rowSpan) {
            rect.copy(columnSpan = rect.columnSpan - 1)
        } else {
            rect.copy(rowSpan = rect.rowSpan - 1)
        }
        if (fits(rect, taken, columns, rows)) return rect
    }
    return null
}

private fun firstFreeCell(taken: List<HomeTile>, columns: Int, rows: Int): GridCell? {
    if (columns >= SCROLL_AXIS_BOUND) {
        for (column in 0 until columns) {
            for (row in 0 until rows) {
                if (fits(TileRect(column, row), taken, columns, rows)) return GridCell(column, row)
            }
        }
        return null
    }
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if (fits(TileRect(column, row), taken, columns, rows)) return GridCell(column, row)
        }
    }
    return null
}
