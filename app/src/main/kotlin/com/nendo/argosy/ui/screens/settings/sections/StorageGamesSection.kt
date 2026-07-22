package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.storage.PlatformUsage
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.data.storage.WalkState
import com.nendo.argosy.ui.components.SegmentedMeterBar
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.storageVolumeColors
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.StorageGamesSortMode
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.pressScale
import com.nendo.argosy.util.formatBytes

internal sealed class StorageGamesItem(
    val key: String,
    val section: String,
    open val isFocusable: Boolean = true
) {
    data object TotalsHeader : StorageGamesItem("totalsHeader", "overview") {
        override val isFocusable = false
    }

    data object IntegrityToggle : StorageGamesItem("integrityToggle", "overview")

    data object PlatformsSpacer : StorageGamesItem("platformsSpacer", "platforms") {
        override val isFocusable = false
    }

    data object PlatformsHeader : StorageGamesItem("platformsHeader", "platforms") {
        override val isFocusable = false
    }

    data object EmptyState : StorageGamesItem("emptyState", "platforms") {
        override val isFocusable = false
    }

    class PlatformRow(val usage: PlatformUsage) : StorageGamesItem(
        key = "platform_${usage.platformId}",
        section = "platforms"
    )

    companion object {
        fun buildItems(platforms: List<PlatformUsage>): List<StorageGamesItem> = buildList {
            add(TotalsHeader)
            add(IntegrityToggle)
            add(PlatformsSpacer)
            add(PlatformsHeader)
            if (platforms.isEmpty()) {
                add(EmptyState)
            } else {
                platforms.forEach { add(PlatformRow(it)) }
            }
        }
    }
}

internal fun storageGamesPlatforms(state: SettingsUiState): List<PlatformUsage> {
    val platforms = state.attribution.snapshot?.gamesPerPlatform
        ?.filter { it.bytes > 0L || it.downloadedCount > 0 }
        ?: emptyList()
    return when (state.attribution.gamesSortMode) {
        StorageGamesSortMode.PLATFORM -> platforms.sortedBy { it.sortOrder }
        StorageGamesSortMode.SIZE -> platforms.sortedByDescending { it.bytes }
    }
}

internal fun createStorageGamesLayout(items: List<StorageGamesItem>) =
    SettingsLayout<StorageGamesItem, Unit>(
        allItems = items,
        isFocusable = { it.isFocusable },
        visibleWhen = { _, _ -> true },
        sectionOf = { it.section },
        sectionTitle = {
            when (it) {
                "overview" -> "OVERVIEW"
                "platforms" -> "PLATFORMS"
                else -> null
            }
        }
    )

internal data class StorageGamesLayoutInfo(
    val layout: SettingsLayout<StorageGamesItem, Unit>
)

internal fun createStorageGamesLayoutInfo(state: SettingsUiState): StorageGamesLayoutInfo =
    StorageGamesLayoutInfo(createStorageGamesLayout(StorageGamesItem.buildItems(storageGamesPlatforms(state))))

internal fun storageGamesItemAtFocusIndex(index: Int, info: StorageGamesLayoutInfo): StorageGamesItem? =
    info.layout.itemAtFocusIndex(index, Unit)

internal fun storageGamesFocusIndexOfPlatform(platformId: Long?, info: StorageGamesLayoutInfo): Int =
    info.layout.focusableItems(Unit)
        .indexOfFirst { it is StorageGamesItem.PlatformRow && it.usage.platformId == platformId }
        .coerceAtLeast(0)

internal fun storageGamesSections(info: StorageGamesLayoutInfo) = info.layout.buildSections(Unit)

internal fun storageGamesMaxFocusIndex(info: StorageGamesLayoutInfo): Int =
    info.layout.maxFocusIndex(Unit)

@Composable
fun StorageGamesSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val attribution = uiState.attribution
    val snapshot = attribution.snapshot
    val sortMode = attribution.gamesSortMode
    val gamesWalk = attribution.walkProgress[StorageCategory.GAMES]

    val platforms = remember(snapshot, sortMode) { storageGamesPlatforms(uiState) }
    val allItems = remember(platforms) { StorageGamesItem.buildItems(platforms) }
    val layout = remember(allItems) { createStorageGamesLayout(allItems) }
    val visibleItems = remember(layout) { layout.visibleItems(Unit) }
    val sections = remember(layout) { layout.buildSections(Unit) }

    val walkingBytes = (gamesWalk as? WalkState.Walking)?.bytes ?: 0L
    val totalGamesBytes = remember(snapshot, platforms, walkingBytes) {
        val snapshotBytes = snapshot?.categories?.get(StorageCategory.GAMES)?.bytes
            ?: platforms.sumOf { it.bytes }
        maxOf(snapshotBytes, platforms.sumOf { it.bytes }, walkingBytes)
    }
    val downloadedCount = remember(platforms, uiState.storage.downloadedGamesCount) {
        platforms.takeIf { it.isNotEmpty() }?.sumOf { it.downloadedCount }
            ?: uiState.storage.downloadedGamesCount
    }

    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val volumeColors = remember(attribution.volumes, neutral, primary, secondary) {
        storageVolumeColors(attribution.volumes, neutral, primary, secondary)
    }
    val volumeOrder = remember(attribution.volumes) { attribution.volumes.map { it.key } }

    fun isFocused(item: StorageGamesItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, Unit)

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, Unit) },
        itemKey = { it.key },
        isNavItem = { it is StorageGamesItem.PlatformsSpacer },
        isHeader = { it is StorageGamesItem.PlatformsHeader },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            StorageGamesItem.TotalsHeader -> GamesTotalsHeader(
                downloadedCount = downloadedCount,
                totalBytes = totalGamesBytes,
                isWorking = gamesWalk is WalkState.Walking || gamesWalk is WalkState.Pending
            )

            StorageGamesItem.IntegrityToggle -> SwitchPreference(
                title = "Weekly ROM Integrity Check",
                subtitle = "Verifies downloaded game files at startup once a week",
                isEnabled = uiState.storage.weeklyIntegrityCheckEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleWeeklyIntegrityCheck(it) }
            )

            StorageGamesItem.PlatformsSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            StorageGamesItem.PlatformsHeader -> PlatformsHeaderRow(
                sortMode = sortMode,
                onToggleSort = { viewModel.toggleGamesSortMode() }
            )

            StorageGamesItem.EmptyState -> GamesEmptyState(
                isComputing = snapshot == null ||
                    gamesWalk is WalkState.Walking || gamesWalk is WalkState.Pending
            )

            is StorageGamesItem.PlatformRow -> PlatformUsageRow(
                usage = item.usage,
                totalGamesBytes = totalGamesBytes,
                volumeColors = volumeColors,
                volumeOrder = volumeOrder,
                neutralColor = neutral,
                isFocused = isFocused(item),
                onClick = { viewModel.openStoragePlatformGames(item.usage.platformId) }
            )
        }
    }
}

@Composable
private fun GamesTotalsHeader(
    downloadedCount: Int,
    totalBytes: Long,
    isWorking: Boolean
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$downloadedCount downloaded",
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary
            )
            Text(
                text = "${formatBytes(totalBytes)} used",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim
            )
        }
        if (isWorking) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.iconSm),
                strokeWidth = Dimens.borderMedium,
                color = theme.textMute
            )
        }
    }
}

@Composable
private fun PlatformsHeaderRow(
    sortMode: StorageGamesSortMode,
    onToggleSort: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionHeader("PLATFORMS")
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .clickableNoFocus(onClick = onToggleSort)
                .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = "Toggle sort order",
                tint = theme.textDim,
                modifier = Modifier.size(Dimens.iconXs)
            )
            Text(
                text = when (sortMode) {
                    StorageGamesSortMode.PLATFORM -> "Platform order"
                    StorageGamesSortMode.SIZE -> "Largest first"
                },
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim
            )
        }
    }
}

@Composable
private fun GamesEmptyState(isComputing: Boolean) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Text(
            text = if (isComputing) "Computing storage usage..." else "No downloaded games",
            style = MaterialTheme.typography.titleSmall,
            color = theme.textPrimary
        )
        Text(
            text = if (isComputing) {
                "Per-platform game sizes will appear here once the scan finishes."
            } else {
                "Download games from your library and per-platform usage will show up here."
            },
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
    }
}

@Composable
private fun PlatformUsageRow(
    usage: PlatformUsage,
    totalGamesBytes: Long,
    volumeColors: Map<String, Color>,
    volumeOrder: List<String>,
    neutralColor: Color,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val segments = remember(usage, volumeColors, volumeOrder, neutralColor) {
        buildList {
            volumeOrder.forEach { key ->
                val bytes = usage.perVolume[key] ?: 0L
                if (bytes > 0L) add((volumeColors[key] ?: neutralColor) to bytes)
            }
            usage.perVolume.forEach { (key, bytes) ->
                if (key !in volumeOrder && bytes > 0L) add(neutralColor to bytes)
            }
            if (isEmpty() && usage.bytes > 0L) add(neutralColor to usage.bytes)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators(fill = true, ring = true),
                tint = theme.focusAccent,
                shape = shape
            )
            .clip(shape)
            .clickableNoFocus(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = usage.name,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${usage.downloadedCount} downloaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textPrimary
                )
                Text(
                    text = formatBytes(usage.bytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
            }
        }
        SegmentedMeterBar(
            totalBytes = totalGamesBytes,
            segments = segments,
            trackColor = trackColor
        )
    }
}
