package com.nendo.argosy.ui.common

enum class GridDirection { UP, DOWN, LEFT, RIGHT }

class GridFocusNavigator {

    var stickyCol: Int = -1
        private set

    fun resetStickyColumn() { stickyCol = -1 }

    fun navigate(
        direction: GridDirection,
        currentIndex: Int,
        rows: List<List<Int>>
    ): Int? {
        var row = -1
        var col = -1
        for (r in rows.indices) {
            val c = rows[r].indexOf(currentIndex)
            if (c >= 0) { row = r; col = c; break }
        }
        if (row < 0) return null
        if (stickyCol < 0) stickyCol = col

        return when (direction) {
            GridDirection.LEFT -> {
                if (col > 0) { stickyCol = col - 1; rows[row][col - 1] } else null
            }
            GridDirection.RIGHT -> {
                if (col + 1 < rows[row].size) { stickyCol = col + 1; rows[row][col + 1] } else null
            }
            GridDirection.UP -> {
                if (row > 0) {
                    val prev = rows[row - 1]
                    prev[stickyCol.coerceAtMost(prev.size - 1)]
                } else null
            }
            GridDirection.DOWN -> {
                if (row + 1 < rows.size) {
                    val next = rows[row + 1]
                    next[stickyCol.coerceAtMost(next.size - 1)]
                } else null
            }
        }
    }

    companion object {
        fun <T> buildGridRows(
            items: List<T>,
            columns: Int,
            isHeader: (T) -> Boolean,
            gameIndex: (T) -> Int
        ): List<List<Int>> {
            val rows = mutableListOf<List<Int>>()
            var current = mutableListOf<Int>()
            for (item in items) {
                if (isHeader(item)) {
                    if (current.isNotEmpty()) { rows.add(current); current = mutableListOf() }
                } else {
                    current.add(gameIndex(item))
                    if (current.size == columns) { rows.add(current); current = mutableListOf() }
                }
            }
            if (current.isNotEmpty()) rows.add(current)
            return rows
        }
    }
}
