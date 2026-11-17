package com.nendo.argosy.ui.screens.search

internal enum class FocusMove { UP, DOWN, LEFT, RIGHT }

internal data class FocusRegion(val size: Int, val columns: Int)

internal sealed class FocusTarget {
    data class Stop(val index: Int) : FocusTarget()
    data object Field : FocusTarget()
    data object Boundary : FocusTarget()
}

internal val SearchUiState.coverCount: Int
    get() = when {
        dualScreen -> coverGames.size
        showKeyboard -> previewGames.size
        else -> 0
    }

internal val SearchUiState.isAboveKeyboard: Boolean get() = previewFocused || historyFocused

internal fun SearchUiState.enteringAboveKeyboard(): SearchUiState? = when {
    dualScreen && historyEntries.isNotEmpty() -> enteringHistory(historyEntries.lastIndex)
    coverCount > 0 -> enteringCovers()
    else -> null
}

private fun SearchUiState.enteringHistory(index: Int): SearchUiState =
    copy(historyFocused = true, historyIndex = index, previewFocused = false)

private fun SearchUiState.enteringCovers(): SearchUiState =
    copy(previewFocused = true, previewIndex = previewIndex.coerceIn(0, coverCount - 1), historyFocused = false)

internal fun SearchUiState.returningToKeyboard(): SearchUiState = if (dualScreen) {
    copy(
        previewFocused = false,
        historyFocused = false,
        showKeyboard = true,
        focusedIndex = SearchViewModel.SEARCH_FIELD_INDEX
    )
} else {
    copy(previewFocused = false)
}

internal fun SearchUiState.withAboveKeyboardClamped(): SearchUiState = when {
    historyFocused && historyEntries.isEmpty() -> returningToKeyboard()
    historyFocused -> copy(historyIndex = historyIndex.coerceIn(0, historyEntries.lastIndex))
    previewFocused && coverCount == 0 -> copy(previewFocused = false, previewIndex = 0)
    previewFocused -> copy(previewIndex = previewIndex.coerceIn(0, coverCount - 1))
    else -> this
}

internal fun SearchUiState.aboveKeyboardStep(move: FocusMove): SearchUiState? =
    if (historyFocused) historyStep(move) else coverStep(move)

private fun SearchUiState.historyStep(move: FocusMove): SearchUiState? = when (move) {
    FocusMove.UP -> when {
        historyIndex > 0 -> copy(historyIndex = historyIndex - 1)
        coverCount > 0 -> enteringCovers()
        else -> null
    }
    FocusMove.DOWN -> if (historyIndex < historyEntries.lastIndex) copy(historyIndex = historyIndex + 1) else returningToKeyboard()
    FocusMove.LEFT, FocusMove.RIGHT -> null
}

private fun SearchUiState.coverStep(move: FocusMove): SearchUiState? =
    when (val target = moveAcross(presentationRegions, previewIndex, move)) {
        is FocusTarget.Stop -> copy(previewIndex = target.index)
        FocusTarget.Field -> null
        FocusTarget.Boundary -> when {
            move != FocusMove.DOWN -> null
            historyEntries.isNotEmpty() -> enteringHistory(0)
            else -> returningToKeyboard()
        }
    }

private data class Located(val regionIndex: Int, val start: Int, val local: Int)

internal fun moveAcross(regions: List<FocusRegion>, index: Int, move: FocusMove): FocusTarget {
    val live = regions.filter { it.size > 0 }
    val at = locate(live, index) ?: return FocusTarget.Boundary
    val region = live[at.regionIndex]
    val columns = region.columns.coerceIn(1, region.size)
    val column = at.local % columns
    val local = when (move) {
        FocusMove.LEFT -> if (column == 0) null else at.local - 1
        FocusMove.RIGHT -> if (column == columns - 1 || at.local == region.size - 1) null else at.local + 1
        FocusMove.DOWN -> when {
            at.local + columns < region.size -> at.local + columns
            at.local / columns < (region.size - 1) / columns -> region.size - 1
            else -> return enterNext(live, at)
        }
        FocusMove.UP -> if (at.local >= columns) at.local - columns else return enterPrevious(live, at)
    }
    return local?.let { FocusTarget.Stop(at.start + it) } ?: FocusTarget.Boundary
}

private fun locate(regions: List<FocusRegion>, index: Int): Located? {
    if (index < 0) return null
    var start = 0
    regions.forEachIndexed { regionIndex, region ->
        if (index < start + region.size) return Located(regionIndex, start, index - start)
        start += region.size
    }
    return null
}

private fun enterNext(regions: List<FocusRegion>, at: Located): FocusTarget =
    if (at.regionIndex + 1 < regions.size) {
        FocusTarget.Stop(at.start + regions[at.regionIndex].size)
    } else {
        FocusTarget.Boundary
    }

private fun enterPrevious(regions: List<FocusRegion>, at: Located): FocusTarget {
    if (at.regionIndex == 0) return FocusTarget.Field
    val previous = regions[at.regionIndex - 1]
    val columns = previous.columns.coerceIn(1, previous.size)
    val lastRowStart = (previous.size - 1) / columns * columns
    return FocusTarget.Stop(at.start - previous.size + lastRowStart)
}
