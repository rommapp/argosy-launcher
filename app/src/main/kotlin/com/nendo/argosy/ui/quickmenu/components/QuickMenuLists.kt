package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.EnumValueControl
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.quickmenu.QuickMenuGameUi
import com.nendo.argosy.ui.quickmenu.RankedGameUi
import com.nendo.argosy.ui.quickmenu.RecentRowUi
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatRelativeTimeShort

private const val FOCUSED_TILE_Z = 1f
private const val RESTING_TILE_Z = 0f
private const val HERO_ASPECT = 16f / 10f
private const val FLAT_TILE_SCALE = 1f

@Composable
fun MostPlayedList(
    rows: List<RankedGameUi>,
    focusedIndex: Int,
    isFocused: Boolean,
    downloadIndicators: Map<Long, GameDownloadIndicator>,
    onRowTap: (Int) -> Unit,
    onRowLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(focusedIndex, rows.size, isFocused) {
        if (isFocused && focusedIndex in rows.indices) listState.animateScrollToItemCentered(focusedIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        itemsIndexed(rows, key = { _, row -> row.game.id }) { index, row ->
            QuickMenuGameRow(
                game = row.game,
                isFocused = isFocused && index == focusedIndex,
                onClick = { onRowTap(index) },
                onLongClick = { onRowLongPress(index) },
                downloadIndicator = downloadIndicators[row.game.id] ?: GameDownloadIndicator.NONE,
                rank = row.rank,
                fraction = row.fraction,
                trailingText = row.minutes.takeIf { it > 0 }?.let { formatPlayTime(context, it.toInt()) }
            )
        }
    }
}

private sealed interface RecentEntry {
    val key: String

    data class Header(val row: RecentRowUi) : RecentEntry {
        override val key: String get() = "group:${row.group.name}"
    }

    data class Row(val row: RecentRowUi, val index: Int) : RecentEntry {
        override val key: String get() = "game:${row.game.id}"
    }

    data class Hero(val row: RecentRowUi) : RecentEntry {
        override val key: String get() = "hero:${row.game.id}"
    }
}

/**
 * The Recent list under its day group labels. With [showHero] the newest game leads as a card
 * carrying [heroScreenshot] and [heroSaveLine], and stays focus stop 0.
 */
@Composable
fun RecentList(
    rows: List<RecentRowUi>,
    focusedIndex: Int,
    isFocused: Boolean,
    downloadIndicators: Map<Long, GameDownloadIndicator>,
    onRowTap: (Int) -> Unit,
    onRowLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showHero: Boolean = false,
    heroScreenshot: String? = null,
    heroSaveLine: String? = null
) {
    val runningLabel = stringResource(R.string.ui_quick_menu_recent_running_chip)
    val entries = remember(rows, showHero) {
        buildList<RecentEntry> {
            rows.forEachIndexed { index, row ->
                if (showHero && index == 0) {
                    add(RecentEntry.Hero(row))
                    return@forEachIndexed
                }
                val previous = rows.getOrNull(index - 1)
                if (previous?.group != row.group || (showHero && index == 1)) add(RecentEntry.Header(row))
                add(RecentEntry.Row(row, index))
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(focusedIndex, entries, isFocused) {
        if (!isFocused) return@LaunchedEffect
        val target = entries.indexOfFirst {
            (it is RecentEntry.Row && it.index == focusedIndex) || (it is RecentEntry.Hero && focusedIndex == 0)
        }
        if (target >= 0) listState.animateScrollToItemCentered(target)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.key }) { _, entry ->
            when (entry) {
                is RecentEntry.Header -> GroupLabel(stringResource(entry.row.group.labelRes))
                is RecentEntry.Hero -> RecentHero(
                    row = entry.row,
                    isFocused = isFocused && focusedIndex == 0,
                    screenshot = heroScreenshot,
                    saveLine = heroSaveLine,
                    runningLabel = runningLabel,
                    onClick = { onRowTap(0) },
                    onLongClick = { onRowLongPress(0) }
                )
                is RecentEntry.Row -> QuickMenuGameRow(
                    game = entry.row.game,
                    isFocused = isFocused && entry.index == focusedIndex,
                    onClick = { onRowTap(entry.index) },
                    onLongClick = { onRowLongPress(entry.index) },
                    downloadIndicator = downloadIndicators[entry.row.game.id] ?: GameDownloadIndicator.NONE,
                    meta = recentMeta(entry.row),
                    runningChip = if (entry.row.isRunning) runningLabel else null
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComponentDefaults.QuickMenu.groupLabelHeightDp.dp)
            .padding(horizontal = Dimens.spacingMd),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = LocalArgosyTheme.current.textDim,
            maxLines = 1
        )
    }
}

/**
 * The Recent row's second line: how long ago the game was played and how long its last session
 * ran, each left out when unknown.
 */
@Composable
fun recentMeta(row: RecentRowUi): String? {
    val context = LocalContext.current
    val ago = row.game.lastPlayed?.let { formatRelativeTimeShort(context, it) }
    val session = row.lastSessionMinutes?.takeIf { it > 0 }?.let { formatPlayTime(context, it.toInt()) }
    return when {
        ago != null && session != null -> stringResource(R.string.ui_quick_menu_recent_row_meta, ago, session)
        else -> ago ?: session
    }
}

@Composable
private fun RecentHero(
    row: RecentRowUi,
    isFocused: Boolean,
    screenshot: String?,
    saveLine: String?,
    runningLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val game = row.game
    val image = screenshot ?: game.backdropPath
    val meta = recentMeta(row)
    val metaLine = when {
        meta != null && saveLine != null -> stringResource(R.string.ui_quick_menu_recent_row_meta, meta, saveLine)
        else -> meta ?: saveLine.orEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.spacingXs)
            .clip(shape)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators(ring = true), shape = shape)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (image != null) {
            AsyncImage(
                model = rememberFileImageModel(image),
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(HERO_ASPECT)
            )
        }
        Column(
            modifier = Modifier.padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (metaLine.isNotEmpty()) {
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (row.isRunning) QuickMenuChip(text = runningLabel, tone = ChipTone.RUNNING)
            }
            ActionButton(
                label = stringResource(
                    if (row.isRunning) R.string.ui_quick_menu_recent_action_resume else R.string.ui_quick_menu_recent_action_play
                ),
                onClick = onClick,
                focused = isFocused,
                primary = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A cover grid. [dimmedIds] draw dimmed under [dimmedLabel]. [hideFavoriteMark] drops the heart
 * where every tile is a favorite. [tileCaption] draws under each tile when set.
 */
@Composable
fun QuickMenuCoverGrid(
    games: List<QuickMenuGameUi>,
    focusedIndex: Int,
    isFocused: Boolean,
    columns: Int,
    spacingDp: Int,
    dimmedIds: Set<Long>,
    dimmedLabel: String,
    downloadIndicators: Map<Long, GameDownloadIndicator>,
    onTileTap: (Int) -> Unit,
    onTileLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hideFavoriteMark: Boolean = false,
    tileCaption: (@Composable (Int) -> Unit)? = null
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(focusedIndex, games.size, isFocused) {
        if (isFocused && focusedIndex in games.indices) gridState.animateScrollToItemCentered(focusedIndex)
    }
    val aspectRatio = LocalBoxArtStyle.current.aspectRatio
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(spacingDp.dp),
        verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
    ) {
        itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
            val tileFocused = isFocused && index == focusedIndex
            val dimmed = game.id in dimmedIds
            val indicator = downloadIndicators[game.id] ?: GameDownloadIndicator.NONE
            Column(
                modifier = Modifier
                    .zIndex(if (tileFocused) FOCUSED_TILE_Z else RESTING_TILE_Z)
                    .clickableNoFocus(onClick = { onTileTap(index) }, onLongClick = { onTileLongPress(index) }),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
                    contentAlignment = Alignment.Center
                ) {
                    GameCard(
                        game = game.toCard(hideFavoriteMark),
                        isFocused = tileFocused,
                        modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
                        focusScale = FLAT_TILE_SCALE,
                        downloadIndicator = indicator,
                        showDownloadedIndicator = false,
                        alphaOverride = if (dimmed) ComponentDefaults.QuickMenu.dimmedTileAlpha else null
                    )
                    TileNotDownloadedMark(game = game, indicator = indicator, modifier = Modifier.align(Alignment.BottomEnd))
                    if (dimmed) QuickMenuChip(text = dimmedLabel)
                }
                tileCaption?.invoke(index)
            }
        }
    }
}

/**
 * The 40 dp header over a section: [label] on the left and the section's enum on the right.
 * [showGlyph] draws the X glyph that cycles it.
 */
@Composable
fun QuickMenuSectionHeader(
    label: String,
    value: String,
    isFocused: Boolean,
    showGlyph: Boolean,
    selectedIndex: Int,
    optionCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.menuRowHeight)
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators(fill = true, ring = true), shape = shape)
                .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            EnumValueControl(
                value = value,
                focused = isFocused,
                onPrev = onPrev,
                onNext = onNext,
                onOpen = onOpen,
                selectedIndex = selectedIndex,
                optionCount = optionCount
            )
            if (showGlyph) InputGlyph(button = InputButton.X)
        }
    }
}

internal fun QuickMenuGameUi.toCard(hideFavoriteMark: Boolean = false): HomeGameUi = HomeGameUi(
    id = id,
    title = title,
    platformId = platformId,
    platformSlug = platformSlug,
    platformDisplayName = platformName.orEmpty(),
    coverPath = coverPath,
    backgroundPath = backdropPath,
    developer = developer,
    releaseYear = year,
    genre = genre,
    isFavorite = isFavorite && !hideFavoriteMark,
    isDownloaded = isDownloaded,
    rating = rating,
    needsInstall = needsInstall
)
