package com.nendo.argosy.ui.components

private const val SPECIALS_SEASON = 0
private const val SPECIALS_KEY = "specials"
private const val ACTION_COUNT = 2

/**
 * One line of the episode chooser: a season to fold, or an episode to tick.
 *
 * [isDownloaded] is a fact about the file; [isLocked] is what the chooser does with it. A download
 * chooser locks what is already on the device, a chooser that only arranges episodes does not.
 */
data class EpisodePickerRow(
    val isHeader: Boolean,
    val seasonKey: String,
    val label: String,
    val supporting: String? = null,
    val itemId: String? = null,
    val isDownloaded: Boolean = false,
    val isLocked: Boolean = false
)

/**
 * One episode as its source knows it, before the chooser groups it under a season.
 */
data class EpisodePickerEntry(
    val itemId: String,
    val seasonNumber: Int?,
    val label: String,
    val supporting: String? = null,
    val isDownloaded: Boolean = false
)

/**
 * Which episodes are ticked, and which seasons are folded away. Keyed by item id, not position.
 *
 * [selected] is a list rather than a set because the order episodes were ticked in is the order one
 * caller plays them in, and nothing else records it.
 */
data class EpisodeSelection(
    val rows: List<EpisodePickerRow> = emptyList(),
    val selected: List<String> = emptyList(),
    val collapsed: Set<String> = emptySet()
) {
    val visibleRows: List<EpisodePickerRow>
        get() = rows.filter { it.isHeader || it.seasonKey !in collapsed }

    fun seasonRows(seasonKey: String): List<EpisodePickerRow> =
        rows.filter { !it.isHeader && it.seasonKey == seasonKey && !it.isLocked }

    /**
     * Ticks or unticks one row. A header takes its whole season on unless the season is already all
     * on, which is the only reading that lets one press both select and clear.
     */
    fun toggle(row: EpisodePickerRow): EpisodeSelection {
        if (row.isHeader) {
            val season = seasonRows(row.seasonKey).mapNotNull { it.itemId }
            if (season.isEmpty()) return this
            val updated = if (season.all { it in selected }) {
                selected - season.toSet()
            } else {
                selected + season.filterNot { it in selected }
            }
            return copy(selected = updated)
        }
        if (row.isLocked) return this
        val itemId = row.itemId ?: return this
        val updated = if (itemId in selected) selected - itemId else selected + itemId
        return copy(selected = updated)
    }
}

/**
 * A shortcut that ticks a set the viewer would otherwise walk the list to pick.
 *
 * It carries the ids it stands for, resolved when the chooser was built, so pressing it shows what
 * it chose rather than acting on the list unseen.
 */
data class EpisodePickerQuickAction(
    val key: String,
    val label: String,
    val itemIds: List<String> = emptyList()
) {
    val enabled: Boolean get() = itemIds.isNotEmpty()
}

/**
 * The chooser as a whole: the rows, any shortcuts, what is ticked, and where focus sits.
 *
 * Shortcuts and the two actions are the last focus positions of the same run the rows occupy, so a
 * controller walks out of the list into them without a second index to keep in step.
 */
data class EpisodePickerState(
    val selection: EpisodeSelection = EpisodeSelection(),
    val quickActions: List<EpisodePickerQuickAction> = emptyList(),
    val focusedIndex: Int = 0
) {
    val visibleRows: List<EpisodePickerRow> get() = selection.visibleRows

    val rowCount: Int get() = visibleRows.size

    val focusCount: Int get() = rowCount + quickActions.size + ACTION_COUNT

    val focusedRow: EpisodePickerRow? get() = visibleRows.getOrNull(focusedIndex)

    val focusedQuickAction: EpisodePickerQuickAction?
        get() = if (focusedIndex < rowCount) null
        else quickActions.getOrNull(focusedIndex - rowCount)

    val cancelIndex: Int get() = rowCount + quickActions.size

    val confirmIndex: Int get() = cancelIndex + 1

    val isCancelFocused: Boolean get() = focusedIndex == cancelIndex

    val isConfirmFocused: Boolean get() = focusedIndex == confirmIndex

    val selectedCount: Int get() = selection.selected.size

    val hasSelection: Boolean get() = selection.selected.isNotEmpty()

    val isEmpty: Boolean get() = selection.rows.isEmpty()

    fun move(delta: Int): EpisodePickerState =
        copy(focusedIndex = (focusedIndex + delta).mod(focusCount))

    fun focus(index: Int): EpisodePickerState =
        copy(focusedIndex = index.coerceIn(0, focusCount - 1))

    fun toggleFocused(): EpisodePickerState {
        val row = focusedRow ?: return this
        return copy(selection = selection.toggle(row))
    }

    /**
     * Ticks the focused shortcut's set, leaving anything already ticked alone.
     */
    fun applyFocusedQuickAction(): EpisodePickerState {
        val action = focusedQuickAction ?: return this
        val pickable = selection.rows
            .filter { !it.isHeader && !it.isLocked }
            .mapNotNull { it.itemId }
            .toSet()
        val added = action.itemIds
            .filter { it in pickable && it !in selection.selected }
        if (added.isEmpty()) return this
        return copy(selection = selection.copy(selected = selection.selected + added))
    }

    /**
     * On a season heading, folds or unfolds it. On anything else, moves to Cancel or Confirm.
     */
    fun moveSideways(towardsEnd: Boolean): EpisodePickerState {
        val row = focusedRow
        if (row == null || !row.isHeader) return copy(focusedIndex = actionIndex(towardsEnd))
        val isCollapsed = row.seasonKey in selection.collapsed
        if (towardsEnd == !isCollapsed) return this
        val updated = if (towardsEnd) {
            selection.collapsed - row.seasonKey
        } else {
            selection.collapsed + row.seasonKey
        }
        val next = selection.copy(collapsed = updated)
        val headerIndex = next.visibleRows
            .indexOfFirst { it.isHeader && it.seasonKey == row.seasonKey }
        return copy(
            selection = next,
            focusedIndex = if (headerIndex >= 0) headerIndex else focusedIndex
        )
    }

    /**
     * What is ticked, in broadcast order rather than in the order it was ticked. For a caller that
     * fetches files, the order rows were pressed in says nothing.
     */
    fun selectedIdsInRowOrder(): List<String> = selection.rows
        .filter { !it.isHeader && it.itemId in selection.selected }
        .mapNotNull { it.itemId }

    private fun actionIndex(towardsEnd: Boolean): Int = if (towardsEnd) confirmIndex else cancelIndex
}

/**
 * Groups episodes into a season-by-season chooser, seasons in broadcast order and specials last.
 *
 * [lockDownloaded] decides whether an episode already on the device can be ticked. A chooser that
 * fetches files locks it and lists it anyway, so a season reads as what it is rather than as what is
 * left of it; a chooser that only arranges episodes leaves it pickable.
 */
fun buildEpisodePickerRows(
    entries: List<EpisodePickerEntry>,
    lockDownloaded: Boolean = false
): List<EpisodePickerRow> {
    if (entries.isEmpty()) return emptyList()
    val grouped = entries.groupBy { entry ->
        entry.seasonNumber?.takeIf { it != SPECIALS_SEASON }
    }
    return buildList {
        grouped.entries
            .sortedBy { it.key ?: Int.MAX_VALUE }
            .forEach { (season, episodes) ->
                val seasonKey = season?.toString() ?: SPECIALS_KEY
                add(
                    EpisodePickerRow(
                        isHeader = true,
                        seasonKey = seasonKey,
                        label = season?.let { "Season $it" } ?: "Specials",
                        supporting = seasonSupporting(
                            onDevice = episodes.count { it.isDownloaded },
                            total = episodes.size
                        )
                    )
                )
                episodes.forEach { episode ->
                    add(
                        EpisodePickerRow(
                            isHeader = false,
                            seasonKey = seasonKey,
                            label = episode.label,
                            supporting = episode.supporting,
                            itemId = episode.itemId,
                            isDownloaded = episode.isDownloaded,
                            isLocked = lockDownloaded && episode.isDownloaded
                        )
                    )
                }
            }
    }
}

private fun seasonSupporting(onDevice: Int, total: Int): String? = when {
    total == 0 -> null
    onDevice == 0 -> "None on this device"
    onDevice == total -> "All $total on this device"
    else -> "$onDevice of $total on this device"
}
