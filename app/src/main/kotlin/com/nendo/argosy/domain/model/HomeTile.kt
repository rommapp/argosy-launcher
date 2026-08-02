package com.nendo.argosy.domain.model

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
}

/**
 * What a tile points at, resolved. A tile outlives its target, so the unresolvable case is a value
 * rather than an absence: the grid can then show the gap it leaves instead of quietly reflowing a
 * page the user arranged by hand.
 */
sealed interface HomeTileTargetRef {
    data class Game(val gameId: Long) : HomeTileTargetRef
    data class Collection(val collectionId: Long) : HomeTileTargetRef
    data class VirtualCollection(val type: String, val name: String) : HomeTileTargetRef
    data class App(val packageName: String) : HomeTileTargetRef
    data object Unresolvable : HomeTileTargetRef
}

data class HomeTile(
    val id: Long,
    val pageIndex: Int,
    val rect: TileRect,
    val target: HomeTileTargetRef
)

/**
 * Places [tiles] onto a page of [columns] by [rows], keeping the ones that fit and reporting the
 * ones that cannot be placed rather than dropping them silently.
 *
 * Tiles are taken in stored order and the first claim on a cell wins. A tile that no longer fits,
 * because the lane count was lowered or two pages were merged by an edit, is trimmed to the largest
 * rectangle still free at its anchor; only when its own anchor is taken is it reported as displaced,
 * so a page survives a narrowing rather than being rebuilt from nothing.
 */
fun placeTiles(tiles: List<HomeTile>, columns: Int, rows: Int): TilePlacement {
    val placed = mutableListOf<HomeTile>()
    val displaced = mutableListOf<HomeTile>()
    for (tile in tiles) {
        val anchor = TileRect(tile.rect.columnIndex, tile.rect.rowIndex)
        if (!anchor.withinBounds(columns, rows) || placed.any { it.rect.overlaps(anchor) }) {
            displaced += tile
            continue
        }
        var fitted = tile.rect
        while (
            !fitted.withinBounds(columns, rows) ||
            placed.any { it.rect.overlaps(fitted) }
        ) {
            fitted = when {
                fitted.columnSpan > 1 -> fitted.copy(columnSpan = fitted.columnSpan - 1)
                fitted.rowSpan > 1 -> fitted.copy(rowSpan = fitted.rowSpan - 1)
                else -> break
            }
        }
        placed += tile.copy(rect = fitted)
    }
    return TilePlacement(placed, displaced)
}

data class TilePlacement(val placed: List<HomeTile>, val displaced: List<HomeTile>)

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
 * A step starts from the edge of whatever tile currently holds the cell, not from the cell itself,
 * so leaving a tile that spans three columns takes one press rather than three. Running off the
 * left or right edge turns the page, because a curated grid has no sections to switch between;
 * running off the top or bottom stops, since that is the direction a page does not continue in.
 */
fun customGridStep(
    cell: GridCell,
    tiles: List<HomeTile>,
    columns: Int,
    rows: Int,
    direction: GridDirection2D
): CustomGridMove {
    if (columns <= 0 || rows <= 0) return CustomGridMove.None
    val origin = tiles.firstOrNull { it.rect.covers(cell.columnIndex, cell.rowIndex) }?.rect
        ?: TileRect(cell.columnIndex, cell.rowIndex)
    val target = when (direction) {
        GridDirection2D.LEFT -> GridCell(origin.columnIndex - 1, cell.rowIndex)
        GridDirection2D.RIGHT -> GridCell(origin.lastColumn + 1, cell.rowIndex)
        GridDirection2D.UP -> GridCell(cell.columnIndex, origin.rowIndex - 1)
        GridDirection2D.DOWN -> GridCell(cell.columnIndex, origin.lastRow + 1)
    }
    val offPage = target.columnIndex !in 0 until columns || target.rowIndex !in 0 until rows
    if (!offPage) return CustomGridMove.Focus(target)
    return when (direction) {
        GridDirection2D.LEFT -> CustomGridMove.PreviousPage
        GridDirection2D.RIGHT -> CustomGridMove.NextPage
        else -> CustomGridMove.None
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
    val untouched = others.filterNot { it.rect.overlaps(editing.rect) }
    settled += untouched
    for (tile in others.filter { it.rect.overlaps(editing.rect) }) {
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
            settled += tile.copy(rect = TileRect(relocated.columnIndex, relocated.rowIndex))
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

private fun shrinkClear(
    tile: HomeTile,
    taken: List<HomeTile>,
    columns: Int,
    rows: Int
): TileRect? {
    var rect = tile.rect
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
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if (fits(TileRect(column, row), taken, columns, rows)) return GridCell(column, row)
        }
    }
    return null
}
