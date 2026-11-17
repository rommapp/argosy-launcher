package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.ui.quickmenu.FavoritesSort
import com.nendo.argosy.ui.quickmenu.MostPlayedRange
import com.nendo.argosy.ui.quickmenu.QuickMenuFormFactor
import com.nendo.argosy.ui.quickmenu.QuickMenuOrb
import com.nendo.argosy.ui.quickmenu.QuickMenuUiState
import com.nendo.argosy.ui.quickmenu.RandomStop
import com.nendo.argosy.ui.quickmenu.SaveSyncState
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

private val QuickMenuTokens = ComponentDefaults.QuickMenu

/**
 * What the content of the quick menu reports back. Every tap acts at once.
 */
data class QuickMenuContentActions(
    val onItemTap: (Int) -> Unit,
    val onItemLongPress: (Int) -> Unit,
    val onHeaderCycle: (Int) -> Unit,
    val onHeaderOpen: () -> Unit,
    val onPrimary: () -> Unit,
    val onRandomStop: (RandomStop) -> Unit,
    val onRandomHistoryTap: (Int) -> Unit,
    val onRandomCyclePlatform: (Int) -> Unit,
    val onRandomCoverTap: () -> Unit,
    val onSearchResultTap: (Long) -> Unit
)

/**
 * The selected section's content. On [QuickMenuFormFactor.LANDSCAPE] list sections split into a
 * list and a preview pane; the other form factors give the list the full width.
 */
@Composable
fun QuickMenuContent(
    uiState: QuickMenuUiState,
    downloadIndicators: Map<Long, GameDownloadIndicator>,
    actions: QuickMenuContentActions,
    modifier: Modifier = Modifier
) {
    val showCursor = uiState.showsCursor
    val listFocused = showCursor && !uiState.isHeaderFocused
    val focusIndex = uiState.focusIndex
    val formFactor = uiState.formFactor
    Box(modifier = modifier) {
        when (uiState.selectedOrb) {
            QuickMenuOrb.SEARCH -> QuickMenuSearchContent(
                search = uiState.search,
                results = uiState.searchResults,
                formFactor = formFactor,
                focusIndex = focusIndex,
                showCursor = showCursor,
                gridSpacingDp = uiState.gridSpacingDp,
                downloadIndicators = downloadIndicators,
                onStopTap = actions.onItemTap,
                onGameLongPress = actions.onItemLongPress,
                onResultTap = actions.onSearchResultTap
            )
            QuickMenuOrb.RANDOM -> QuickMenuRandomContent(
                random = uiState.random,
                formFactor = formFactor,
                showCursor = showCursor,
                showGlyphs = showCursor,
                onStop = actions.onRandomStop,
                onHistoryTap = actions.onRandomHistoryTap,
                onCyclePlatform = actions.onRandomCyclePlatform,
                onCoverTap = actions.onRandomCoverTap
            )
            QuickMenuOrb.MOST_PLAYED -> SplitPane(uiState, actions.onPrimary, showPrimaryButton = false) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val range = uiState.mostPlayed.range
                    QuickMenuSectionHeader(
                        label = stringResource(R.string.ui_quick_menu_most_played_header),
                        value = stringResource(range.labelRes),
                        isFocused = showCursor && uiState.isHeaderFocused,
                        showGlyph = showCursor,
                        selectedIndex = range.ordinal,
                        optionCount = MostPlayedRange.entries.size,
                        onPrev = { actions.onHeaderCycle(-1) },
                        onNext = { actions.onHeaderCycle(1) },
                        onOpen = actions.onHeaderOpen
                    )
                    when {
                        !uiState.mostPlayed.isLoaded -> Unit
                        uiState.mostPlayed.rows.isEmpty() -> SectionEmpty(
                            stringResource(
                                if (range == MostPlayedRange.LAST_30_DAYS) R.string.ui_quick_menu_empty_most_played_last_30
                                else R.string.ui_quick_menu_empty_most_played
                            )
                        )
                        else -> MostPlayedList(
                            rows = uiState.mostPlayed.rows,
                            focusedIndex = focusIndex,
                            isFocused = listFocused,
                            downloadIndicators = downloadIndicators,
                            onRowTap = actions.onItemTap,
                            onRowLongPress = actions.onItemLongPress,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }
                }
            }
            QuickMenuOrb.RECENT -> SplitPane(uiState, actions.onPrimary, showPrimaryButton = true) {
                when {
                    !uiState.recent.isLoaded -> Unit
                    uiState.recent.rows.isEmpty() -> SectionEmpty(stringResource(R.string.ui_quick_menu_empty_recent))
                    else -> {
                        val rows = uiState.recent.rows
                        val heroExtras = uiState.extras?.takeIf { it.gameId == rows.first().game.id }
                        RecentList(
                            rows = rows,
                            focusedIndex = focusIndex,
                            isFocused = listFocused,
                            downloadIndicators = downloadIndicators,
                            onRowTap = actions.onItemTap,
                            onRowLongPress = actions.onItemLongPress,
                            showHero = formFactor == QuickMenuFormFactor.PORTRAIT,
                            heroScreenshot = heroExtras?.screenshotPath,
                            heroSaveLine = heroExtras?.saveStatus?.let { saveShortLine(it.state) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            QuickMenuOrb.TOP_UNPLAYED -> GridSection(
                isLoaded = uiState.topNew.isLoaded,
                isEmpty = uiState.topNew.tiles.isEmpty(),
                emptyMessage = stringResource(R.string.ui_quick_menu_empty_top_unplayed),
                header = { if (formFactor == QuickMenuFormFactor.LANDSCAPE) TopNewHeader(uiState) }
            ) {
                val tiles = uiState.topNew.tiles
                QuickMenuCoverGrid(
                    games = remember(tiles) { tiles.map { it.game } },
                    focusedIndex = focusIndex,
                    isFocused = listFocused,
                    columns = uiState.gridColumns,
                    spacingDp = uiState.gridSpacingDp,
                    dimmedIds = uiState.topNew.dismissedIds,
                    dimmedLabel = stringResource(R.string.ui_quick_menu_top_new_dismissed),
                    downloadIndicators = downloadIndicators,
                    onTileTap = actions.onItemTap,
                    onTileLongPress = actions.onItemLongPress,
                    modifier = Modifier.fillMaxSize(),
                    tileCaption = if (formFactor == QuickMenuFormFactor.PORTRAIT) {
                        { index -> tiles.getOrNull(index)?.let { TileCaption(it.game.title, topNewReasonText(it.reason)) } }
                    } else {
                        null
                    }
                )
            }
            QuickMenuOrb.FAVORITES -> GridSection(
                isLoaded = uiState.favorites.isLoaded,
                isEmpty = uiState.favorites.games.isEmpty(),
                emptyMessage = stringResource(R.string.ui_quick_menu_empty_favorites),
                header = header@{
                    if (uiState.favorites.games.isEmpty()) return@header
                    val sort = uiState.favorites.sort
                    val count = uiState.favorites.count
                    QuickMenuSectionHeader(
                        label = pluralStringResource(R.plurals.ui_quick_menu_favorites_header_count, count, count),
                        value = stringResource(sort.labelRes),
                        isFocused = showCursor && uiState.isHeaderFocused,
                        showGlyph = showCursor,
                        selectedIndex = sort.ordinal,
                        optionCount = FavoritesSort.entries.size,
                        onPrev = { actions.onHeaderCycle(-1) },
                        onNext = { actions.onHeaderCycle(1) },
                        onOpen = actions.onHeaderOpen
                    )
                }
            ) {
                QuickMenuCoverGrid(
                    games = uiState.favorites.games,
                    focusedIndex = focusIndex,
                    isFocused = listFocused,
                    columns = uiState.gridColumns,
                    spacingDp = uiState.gridSpacingDp,
                    dimmedIds = uiState.favorites.removedIds,
                    dimmedLabel = stringResource(R.string.ui_quick_menu_favorites_removed),
                    downloadIndicators = downloadIndicators,
                    onTileTap = actions.onItemTap,
                    onTileLongPress = actions.onItemLongPress,
                    hideFavoriteMark = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun saveShortLine(state: SaveSyncState): String = stringResource(
    when (state) {
        SaveSyncState.SYNCED -> R.string.ui_quick_menu_recent_hero_saved_synced
        SaveSyncState.PENDING -> R.string.ui_quick_menu_recent_hero_saved_pending
        SaveSyncState.LOCAL_ONLY -> R.string.ui_quick_menu_recent_hero_saved_local
    }
)

@Composable
private fun TileCaption(title: String, reason: String) {
    val theme = LocalArgosyTheme.current
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = reason,
            style = MaterialTheme.typography.labelSmall,
            color = theme.focusAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SplitPane(
    uiState: QuickMenuUiState,
    onPrimary: () -> Unit,
    showPrimaryButton: Boolean,
    list: @Composable () -> Unit
) {
    if (uiState.formFactor != QuickMenuFormFactor.LANDSCAPE) {
        Box(modifier = Modifier.fillMaxSize()) { list() }
        return
    }
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Box(modifier = Modifier.weight(QuickMenuTokens.listPaneRatio).fillMaxHeight()) { list() }
        Box(modifier = Modifier.weight(1f - QuickMenuTokens.listPaneRatio).fillMaxHeight()) {
            uiState.preview?.let { preview ->
                QuickMenuPreviewPane(
                    preview = preview,
                    large = false,
                    onPrimary = onPrimary,
                    showPrimaryButton = showPrimaryButton,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun GridSection(
    isLoaded: Boolean,
    isEmpty: Boolean,
    emptyMessage: String,
    header: @Composable () -> Unit,
    grid: @Composable () -> Unit
) {
    if (!isLoaded) return
    Column(modifier = Modifier.fillMaxSize()) {
        header()
        if (isEmpty) {
            SectionEmpty(emptyMessage)
            return@Column
        }
        Box(modifier = Modifier.weight(1f)) { grid() }
    }
}

@Composable
private fun TopNewHeader(uiState: QuickMenuUiState) {
    val preview = uiState.preview ?: return
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = preview.game.title,
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        QuickMenuChip(text = preview.game.platformName ?: stringResource(R.string.ui_quick_menu_row_platform_unknown))
        QuickMenuChip(text = topNewReasonText(preview.reason), tone = ChipTone.ACCENT)
    }
}
