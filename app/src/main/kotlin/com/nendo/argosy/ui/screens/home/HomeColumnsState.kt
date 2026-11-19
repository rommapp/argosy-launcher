package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeColumnKind
import com.nendo.argosy.domain.model.HomeColumnLabel
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * One column of the Columns loop. [count] is the number its reason line states.
 */
data class HomeColumnUi(
    val id: String,
    val label: HomeColumnLabel,
    val items: List<HomeRowItem>,
    val count: Int = items.size
) {
    val kind: HomeColumnKind get() = label.kind
}

/**
 * The focused game's recent play on the Columns band. [weeks] holds each week's share of the
 * busiest week, oldest first, ending this week; [lastPlayedAt] is epoch millis.
 */
data class ColumnsGameProgress(
    val gameId: Long,
    val weeks: List<Float>,
    val lastPlayedAt: Long?,
    val lastPlayedDevice: String?
) {
    val playedInWindow: Boolean get() = weeks.any { it > 0f }
}

/**
 * The Columns cursor. [position] is unbounded while the loop [wraps] and is the focused index when
 * it does not; [itemIndex] is the game index per column id.
 */
data class ColumnsState(
    val columns: List<HomeColumnUi> = emptyList(),
    val position: Int = 0,
    val itemIndex: Map<String, Int> = emptyMap(),
    val slotCount: Int = ComponentDefaults.HomeColumns.slotsWide,
    val isLoaded: Boolean = false,
    val progress: ColumnsGameProgress? = null
) {
    fun progressFor(gameId: Long?): ColumnsGameProgress? = progress?.takeIf { it.gameId == gameId }

    val wraps: Boolean get() = columns.size >= slotCount

    val focusIndex: Int get() = if (columns.isEmpty()) 0 else position.mod(columns.size)

    val focusedColumn: HomeColumnUi? get() = columns.getOrNull(focusIndex)

    val focusedItem: HomeRowItem?
        get() = focusedColumn?.let { it.items.getOrNull(itemIndexOf(it)) }

    val focusedColumnGameIds: List<Long>
        get() = focusedColumn?.items.orEmpty().mapNotNull { (it as? HomeRowItem.Game)?.game?.id }

    fun itemIndexOf(column: HomeColumnUi): Int =
        (itemIndex[column.id] ?: 0).coerceIn(0, (column.items.size - 1).coerceAtLeast(0))

    fun movedBy(delta: Int): ColumnsState? {
        if (columns.isEmpty() || delta == 0) return null
        if (wraps) return copy(position = position + delta)
        val target = focusIndex + delta
        return if (target in columns.indices) copy(position = target) else null
    }

    fun steppedBy(delta: Int): ColumnsState? {
        val column = focusedColumn ?: return null
        val target = itemIndexOf(column) + delta
        if (delta == 0 || target !in column.items.indices) return null
        return copy(itemIndex = itemIndex + (column.id to target))
    }

    /**
     * The first column of the next or previous run of one [HomeColumnKind], wrapping at both ends.
     * Null when that column is already focused.
     */
    fun jumpedRun(direction: Int): ColumnsState? {
        if (columns.isEmpty() || direction == 0) return null
        val starts = columns.indices.filter { it == 0 || columns[it].kind != columns[it - 1].kind }
        val current = starts.indexOfLast { it <= focusIndex }
        val target = starts[(current + if (direction > 0) 1 else -1).mod(starts.size)]
        if (target == focusIndex) return null
        if (!wraps) return copy(position = target)
        val delta = if (direction > 0) {
            (target - focusIndex).mod(columns.size)
        } else {
            -(focusIndex - target).mod(columns.size)
        }
        return copy(position = position + delta)
    }

    fun reset(): ColumnsState? {
        val first = columns.firstOrNull() ?: return null
        val onFirstGame = itemIndexOf(first) == 0
        if (focusIndex == 0 && onFirstGame) return null
        val delta = if (wraps) -focusIndex else 0
        return copy(
            position = if (wraps) position + delta else 0,
            itemIndex = itemIndex - first.id
        )
    }

    fun withSlotCount(slots: Int): ColumnsState {
        if (slots == slotCount) return this
        val next = copy(slotCount = slots)
        return if (next.wraps) next else next.copy(position = focusIndex)
    }

    /**
     * Replaces the columns, keeping the cursor on the same column id and on the same game within
     * each column.
     */
    fun rebuiltWith(next: List<HomeColumnUi>): ColumnsState {
        val nextFocus = focusedColumn
            ?.let { focused -> next.indexOfFirst { it.id == focused.id } }
            ?.takeIf { it >= 0 }
            ?: focusIndex.coerceAtMost((next.size - 1).coerceAtLeast(0))
        val nextIndex = next.mapNotNull { column ->
            val previous = columns.firstOrNull { it.id == column.id } ?: return@mapNotNull null
            val key = previous.items.getOrNull(itemIndexOf(previous))?.let { itemKey(it) }
            val kept = key?.let { k -> column.items.indexOfFirst { itemKey(it) == k } }?.takeIf { it >= 0 }
                ?: (itemIndex[column.id] ?: 0).coerceIn(0, (column.items.size - 1).coerceAtLeast(0))
            column.id to kept
        }.toMap()
        val rebuilt = copy(columns = next, itemIndex = nextIndex, isLoaded = true)
        val nextPosition = if (rebuilt.wraps) position - focusIndex + nextFocus else nextFocus
        return rebuilt.copy(position = nextPosition)
    }

    private fun itemKey(item: HomeRowItem): String? = when (item) {
        is HomeRowItem.Game -> "$KEY_GAME${item.game.id}"
        is HomeRowItem.Media -> "$KEY_MEDIA${item.media.itemId}"
        is HomeRowItem.ViewAll -> null
    }

    private companion object {
        const val KEY_GAME = "game:"
        const val KEY_MEDIA = "media:"
    }
}
