package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.storage.MediaLibraryUsage
import com.nendo.argosy.data.storage.MediaLocationUsage
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.data.storage.WalkState
import com.nendo.argosy.ui.components.SegmentedMeterBar
import com.nendo.argosy.ui.components.storageComputedLabel
import com.nendo.argosy.ui.components.storageVolumeColors
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.util.formatBytes

private const val MEDIA_SECTION = "media"

internal sealed class StorageMediaItem(
    val key: String,
    open val isFocusable: Boolean = true
) {
    data object TotalsHeader : StorageMediaItem("mediaTotalsHeader") {
        override val isFocusable = false
    }

    data object RecomputeRow : StorageMediaItem("mediaRecompute")

    data object ArtworkCache : StorageMediaItem("mediaArtworkCache") {
        override val isFocusable = false
    }

    data object LibrariesHeader : StorageMediaItem("mediaLibrariesHeader") {
        override val isFocusable = false
    }

    data object LibrariesEmpty : StorageMediaItem("mediaLibrariesEmpty") {
        override val isFocusable = false
    }

    data object LocationsHeader : StorageMediaItem("mediaLocationsHeader") {
        override val isFocusable = false
    }

    class LibraryRow(val usage: MediaLibraryUsage) : StorageMediaItem(
        key = "mediaLibrary_${usage.libraryId}_${usage.name}"
    ) {
        override val isFocusable = false
    }

    class LocationRow(val usage: MediaLocationUsage) : StorageMediaItem(
        key = "mediaLocation_${usage.path}"
    ) {
        override val isFocusable = false
    }

    companion object {
        fun buildItems(
            libraries: List<MediaLibraryUsage>,
            locations: List<MediaLocationUsage>
        ): List<StorageMediaItem> = buildList {
            add(TotalsHeader)
            add(RecomputeRow)
            add(ArtworkCache)
            add(LibrariesHeader)
            if (libraries.isEmpty()) {
                add(LibrariesEmpty)
            } else {
                libraries.forEach { add(LibraryRow(it)) }
            }
            if (locations.isNotEmpty()) {
                add(LocationsHeader)
                locations.forEach { add(LocationRow(it)) }
            }
        }
    }
}

internal fun createStorageMediaLayout(items: List<StorageMediaItem>) =
    SettingsLayout<StorageMediaItem, Unit>(
        allItems = items,
        isFocusable = { it.isFocusable },
        visibleWhen = { _, _ -> true },
        sectionOf = { MEDIA_SECTION },
        sectionTitle = { null }
    )

internal data class StorageMediaLayoutInfo(
    val layout: SettingsLayout<StorageMediaItem, Unit>
)

internal fun createStorageMediaLayoutInfo(state: SettingsUiState): StorageMediaLayoutInfo =
    StorageMediaLayoutInfo(
        createStorageMediaLayout(
            StorageMediaItem.buildItems(
                libraries = state.attribution.snapshot?.mediaPerLibrary.orEmpty(),
                locations = state.attribution.snapshot?.mediaLocations.orEmpty()
            )
        )
    )

internal fun storageMediaItemAtFocusIndex(index: Int, info: StorageMediaLayoutInfo): StorageMediaItem? =
    info.layout.itemAtFocusIndex(index, Unit)

internal fun storageMediaMaxFocusIndex(info: StorageMediaLayoutInfo): Int =
    info.layout.maxFocusIndex(Unit)

internal fun storageMediaSections(info: StorageMediaLayoutInfo) = info.layout.buildSections(Unit)

@Composable
fun StorageMediaSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val attribution = uiState.attribution
    val snapshot = attribution.snapshot
    val mediaWalk = attribution.walkProgress[StorageCategory.MEDIA]

    val libraries = snapshot?.mediaPerLibrary.orEmpty()
    val locations = snapshot?.mediaLocations.orEmpty()
    val allItems = remember(libraries, locations) { StorageMediaItem.buildItems(libraries, locations) }
    val layout = remember(allItems) { createStorageMediaLayout(allItems) }
    val visibleItems = remember(layout) { layout.visibleItems(Unit) }
    val sections = remember(layout) { layout.buildSections(Unit) }

    val walkingBytes = (mediaWalk as? WalkState.Walking)?.bytes ?: 0L
    val totalBytes = remember(snapshot, libraries, walkingBytes) {
        val snapshotBytes = snapshot?.categories?.get(StorageCategory.MEDIA)?.bytes ?: 0L
        maxOf(snapshotBytes, libraries.sumOf { it.bytes }, walkingBytes)
    }
    val downloadedCount = remember(libraries) { libraries.sumOf { it.downloadedCount } }
    val offlineBytes = remember(libraries) { libraries.sumOf { it.offlineBytes } }
    val artworkUsage = snapshot?.categories?.get(StorageCategory.REMOTE_IMAGE_CACHE)

    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val volumeColors = remember(attribution.volumes, neutral, primary, secondary) {
        storageVolumeColors(attribution.volumes, neutral, primary, secondary)
    }
    val volumeOrder = remember(attribution.volumes) { attribution.volumes.map { it.key } }

    fun isFocused(item: StorageMediaItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, Unit)

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, Unit) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is StorageMediaItem.LibrariesHeader },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            StorageMediaItem.TotalsHeader -> MediaTotalsHeader(
                downloadedCount = downloadedCount,
                totalBytes = totalBytes,
                offlineBytes = offlineBytes,
                isWorking = mediaWalk is WalkState.Walking || mediaWalk is WalkState.Pending
            )

            StorageMediaItem.RecomputeRow -> RecomputeRow(
                label = storageComputedLabel(snapshot?.computedAt, attribution.isRefreshing),
                isRefreshing = attribution.isRefreshing,
                isFocused = isFocused(item),
                onRefresh = { viewModel.refreshStorageAttribution() },
                onDeepRescan = { viewModel.refreshStorageAttribution(deep = true) }
            )

            StorageMediaItem.ArtworkCache -> {
                val theme = LocalArgosyTheme.current
                MediaDetailRow(
                    title = "Artwork cache",
                    subtitle = "Posters and backdrops fetched while browsing, shared with other online artwork",
                    trailingPrimary = formatBytes(artworkUsage?.bytes ?: 0L),
                    trailingSecondary = "${artworkUsage?.fileCount ?: 0} files",
                    titleColor = theme.textPrimary,
                    trailingColor = theme.textDim
                )
            }

            StorageMediaItem.LibrariesHeader -> SectionHeader("LIBRARIES")

            StorageMediaItem.LibrariesEmpty -> MediaEmptyState(
                isComputing = snapshot == null ||
                    mediaWalk is WalkState.Walking || mediaWalk is WalkState.Pending
            )

            StorageMediaItem.LocationsHeader -> {
                Column {
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    SectionHeader("LOCATIONS")
                }
            }

            is StorageMediaItem.LibraryRow -> MediaLibraryRow(
                usage = item.usage,
                totalMediaBytes = totalBytes,
                volumeColors = volumeColors,
                volumeOrder = volumeOrder,
                neutralColor = neutral
            )

            is StorageMediaItem.LocationRow -> MediaLocationRow(usage = item.usage)
        }
    }
}

@Composable
private fun MediaTotalsHeader(
    downloadedCount: Int,
    totalBytes: Long,
    offlineBytes: Long,
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
                text = if (downloadedCount == 1) "1 title downloaded" else "$downloadedCount titles downloaded",
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary
            )
            Text(
                text = "${formatBytes(totalBytes)} used",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim
            )
            if (offlineBytes > 0L) {
                Text(
                    text = "${formatBytes(offlineBytes)} on storage that is not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute
                )
            }
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
private fun MediaEmptyState(isComputing: Boolean) {
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
            text = if (isComputing) "Computing storage usage..." else "No media libraries yet",
            style = MaterialTheme.typography.titleSmall,
            color = theme.textPrimary
        )
        Text(
            text = if (isComputing) {
                "Per-library sizes will appear here once the scan finishes."
            } else {
                "Sign in to a media server and downloaded movies and episodes show up here per library."
            },
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
    }
}

@Composable
private fun MediaLibraryRow(
    usage: MediaLibraryUsage,
    totalMediaBytes: Long,
    volumeColors: Map<String, Color>,
    volumeOrder: List<String>,
    neutralColor: Color
) {
    val theme = LocalArgosyTheme.current
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
                    text = if (usage.downloadedCount == 1) "1 downloaded" else "${usage.downloadedCount} downloaded",
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
            totalBytes = totalMediaBytes,
            segments = segments,
            trackColor = trackColor
        )
        if (usage.offlineCount > 0) {
            Text(
                text = "${usage.offlineCount} waiting on storage that is not connected, " +
                    "${formatBytes(usage.offlineBytes)} when last measured",
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMute
            )
        }
        if (usage.missingCount > 0) {
            Text(
                text = "${usage.missingCount} no longer on disk",
                style = MaterialTheme.typography.bodySmall,
                color = theme.destructive
            )
        }
    }
}

@Composable
private fun MediaLocationRow(usage: MediaLocationUsage) {
    val theme = LocalArgosyTheme.current
    val subtitle = when {
        !usage.isAvailable -> "Not connected, ${formatBytes(usage.bytes)} when last measured"
        usage.isCurrentTarget -> "New downloads land here, ${usage.fileCount} files"
        else -> "Left here by an earlier download folder, ${usage.fileCount} files"
    }
    MediaDetailRow(
        title = formatStoragePath(usage.path),
        subtitle = subtitle,
        trailingPrimary = if (usage.isAvailable) formatBytes(usage.bytes) else "Unavailable",
        trailingSecondary = null,
        titleColor = theme.textPrimary,
        trailingColor = if (usage.isAvailable) theme.textDim else theme.textMute
    )
}

@Composable
private fun MediaDetailRow(
    title: String,
    subtitle: String,
    trailingPrimary: String,
    trailingSecondary: String?,
    titleColor: Color,
    trailingColor: Color
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = trailingPrimary,
                style = MaterialTheme.typography.bodyMedium,
                color = trailingColor
            )
            if (trailingSecondary != null) {
                Text(
                    text = trailingSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute
                )
            }
        }
    }
}
