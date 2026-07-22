package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.nendo.argosy.ui.components.volumeMeterCategoryColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideogameAsset
import coil.compose.AsyncImage
import com.nendo.argosy.domain.usecase.storage.GameStorageBreakdown
import com.nendo.argosy.domain.usecase.storage.GameStorageBucket
import com.nendo.argosy.domain.usecase.storage.GameStorageBucketRow
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.StoragePlatformGamesState
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.pressScale
import com.nendo.argosy.util.formatBytes

internal fun bucketDisplayLabel(bucket: GameStorageBucket): String = when (bucket) {
    GameStorageBucket.BASE -> "Base"
    GameStorageBucket.UPDATES -> "Updates"
    GameStorageBucket.DLC -> "DLC"
    GameStorageBucket.HACKS -> "Hacks"
    GameStorageBucket.SOUNDTRACK -> "Soundtrack"
    GameStorageBucket.OTHER -> "Other"
}

internal sealed class StoragePlatformGamesItem(
    val key: String,
    val section: String,
    open val isFocusable: Boolean = true
) {
    data object EmptyState : StoragePlatformGamesItem("emptyState", "empty") {
        override val isFocusable = false
    }

    class GameCard(val gameId: Long) : StoragePlatformGamesItem(
        key = "game_$gameId",
        section = "games"
    )

    companion object {
        fun buildItems(games: List<GameStorageBreakdown>): List<StoragePlatformGamesItem> = buildList {
            if (games.isEmpty()) {
                add(EmptyState)
            } else {
                games.forEach { add(GameCard(it.gameId)) }
            }
        }
    }
}

internal data class StoragePlatformGamesLayoutInfo(
    val layout: SettingsLayout<StoragePlatformGamesItem, Unit>
)

internal fun createStoragePlatformGamesLayout(items: List<StoragePlatformGamesItem>) =
    SettingsLayout<StoragePlatformGamesItem, Unit>(
        allItems = items,
        isFocusable = { it.isFocusable },
        visibleWhen = { _, _ -> true },
        sectionOf = { it.section }
    )

internal fun createStoragePlatformGamesLayoutInfo(state: SettingsUiState): StoragePlatformGamesLayoutInfo =
    StoragePlatformGamesLayoutInfo(
        createStoragePlatformGamesLayout(
            StoragePlatformGamesItem.buildItems(state.storagePlatformGames.games)
        )
    )

internal fun storagePlatformGamesItemAtFocusIndex(index: Int, info: StoragePlatformGamesLayoutInfo): StoragePlatformGamesItem? =
    info.layout.itemAtFocusIndex(index, Unit)

internal fun storagePlatformGamesMaxFocusIndex(info: StoragePlatformGamesLayoutInfo): Int =
    info.layout.maxFocusIndex(Unit)

internal fun storagePlatformGamesSections(info: StoragePlatformGamesLayoutInfo) =
    info.layout.buildSections(Unit)

@Composable
fun StoragePlatformGamesSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val state = uiState.storagePlatformGames
    val allItems = remember(state.games) {
        StoragePlatformGamesItem.buildItems(state.games)
    }
    val layout = remember(allItems) { createStoragePlatformGamesLayout(allItems) }
    val visibleItems = remember(layout) { layout.visibleItems(Unit) }
    val sections = remember(layout) { layout.buildSections(Unit) }

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, Unit) },
        itemKey = { it.key },
        isNavItem = { false },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            StoragePlatformGamesItem.EmptyState -> PlatformGamesEmptyState(state)

            is StoragePlatformGamesItem.GameCard -> {
                val game = state.games.firstOrNull { it.gameId == item.gameId }
                if (game != null) {
                    val gameIndex = layout.focusIndexOf(item, Unit)
                    val isActive = uiState.focusedIndex == gameIndex
                    val highlighted = state.highlightedCategoryIndex
                        .coerceIn(0, (game.buckets.size - 1).coerceAtLeast(0))
                    GameStorageCard(
                        game = game,
                        coverPath = state.coverPaths[game.gameId],
                        isActive = isActive,
                        highlightedCategoryIndex = highlighted,
                        onCoverClick = { viewModel.onStoragePlatformCoverTap(gameIndex, game.gameId) },
                        onCategoryClick = { bucketIndex ->
                            game.buckets.getOrNull(bucketIndex)?.let { row ->
                                viewModel.onStoragePlatformCategoryTap(gameIndex, bucketIndex, game.gameId, row.bucket)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformGamesEmptyState(state: StoragePlatformGamesState) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(theme.surfaceRaised.copy(alpha = 0.3f))
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Text(
            text = if (state.isLoading) "Loading..." else "No downloaded games",
            style = MaterialTheme.typography.titleSmall,
            color = theme.textPrimary
        )
        Text(
            text = "Downloaded games for ${state.platformName} appear here with a per-category breakdown.",
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameStorageCard(
    game: GameStorageBreakdown,
    coverPath: String?,
    isActive: Boolean,
    highlightedCategoryIndex: Int,
    onCoverClick: () -> Unit,
    onCategoryClick: (Int) -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .argosyFocusIndicators(
                focused = isActive,
                indicators = FocusIndicators(ring = true),
                tint = theme.focusAccent,
                shape = shape
            )
            .clip(shape)
            .padding(Dimens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        GameCover(coverPath = coverPath, onClick = onCoverClick)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .clickableNoFocus(onClick = onCoverClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = formatBytes(game.totalBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textDim
                )
            }
            val bucketColors = rememberBucketColors()
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                game.buckets.forEachIndexed { index, row ->
                    CategoryPill(
                        row = row,
                        color = bucketColors[row.bucket] ?: theme.focusAccent,
                        highlighted = isActive && index == highlightedCategoryIndex,
                        onClick = { onCategoryClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameCover(coverPath: String?, onClick: () -> Unit) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusSm)
    val model = rememberFileImageModel(coverPath)
    val modifier = Modifier
        .size(width = Dimens.storageGameCoverWidth, height = Dimens.storageGameCoverHeight)
        .clip(shape)
        .clickableNoFocus(onClick = onClick)
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(theme.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.VideogameAsset,
                contentDescription = null,
                tint = theme.textMute,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
    }
}

@Composable
private fun CategoryPill(
    row: GameStorageBucketRow,
    color: Color,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val statsLabel = "${row.fileCount} - ${formatBytes(row.totalBytes)}"
    val onFill = if (color.luminance() > 0.5f) theme.surfaceBase else theme.textPrimary
    val fillColor = if (highlighted) color else Color.Transparent
    val borderColor = if (highlighted) color else color.copy(alpha = 0.5f)
    val labelColor = if (highlighted) onFill else color
    val statsColor = if (highlighted) onFill.copy(alpha = 0.85f) else theme.textDim
    Column(
        modifier = Modifier
            .pressScale(interaction)
            .clip(shape)
            .background(fillColor)
            .border(width = Dimens.borderThin, color = borderColor, shape = shape)
            .clickableNoFocus(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = bucketDisplayLabel(row.bucket),
            style = MaterialTheme.typography.labelMedium,
            color = labelColor
        )
        Text(
            text = statsLabel,
            style = MaterialTheme.typography.labelSmall,
            color = statsColor,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun rememberBucketColors(): Map<GameStorageBucket, Color> {
    val theme = LocalArgosyTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    return remember(theme.destructive, primary, secondary) {
        val nonBase = listOf(
            GameStorageBucket.UPDATES, GameStorageBucket.DLC, GameStorageBucket.HACKS,
            GameStorageBucket.SOUNDTRACK, GameStorageBucket.OTHER
        )
        val palette = volumeMeterCategoryColors(primary, secondary, nonBase.size)
        buildMap {
            put(GameStorageBucket.BASE, theme.destructive)
            nonBase.forEachIndexed { index, bucket -> put(bucket, palette[index]) }
        }
    }
}
