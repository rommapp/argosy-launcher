package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import com.nendo.argosy.data.cache.ImageCacheProgress
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.ImageCachePreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.PlatformFiltersModal
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SyncFiltersModal
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

// --- Item definitions ---

internal sealed class SyncSettingsItem(val key: String, val section: String) {
    val isFocusable: Boolean get() = this !is MediaHeader && this !is ImageCacheProgressIndicator

    data object PlatformFilters : SyncSettingsItem("platformFilters", "filters")
    data object MetadataFilters : SyncSettingsItem("metadataFilters", "filters")
    data object MediaHeader : SyncSettingsItem("mediaHeader", "media")
    data object CacheScreenshots : SyncSettingsItem("cacheScreenshots", "media")
    data object ImageCacheLocation : SyncSettingsItem("imageCacheLocation", "media")
    data object ImageCacheProgressIndicator : SyncSettingsItem("imageCacheProgress", "media")
}

private val syncSettingsLayout = SettingsLayout<SyncSettingsItem, Boolean>(
    allItems = listOf(
        SyncSettingsItem.PlatformFilters,
        SyncSettingsItem.MetadataFilters,
        SyncSettingsItem.MediaHeader,
        SyncSettingsItem.CacheScreenshots,
        SyncSettingsItem.ImageCacheLocation,
        SyncSettingsItem.ImageCacheProgressIndicator
    ),
    isFocusable = { it.isFocusable },
    visibleWhen = { item, isProcessing ->
        if (item is SyncSettingsItem.ImageCacheProgressIndicator) isProcessing else true
    },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "media" -> "MEDIA"
            else -> null
        }
    }
)

internal fun syncSettingsItemAtFocusIndex(index: Int, isProcessing: Boolean = false): SyncSettingsItem? =
    syncSettingsLayout.itemAtFocusIndex(index, isProcessing)

internal fun syncSettingsMaxFocusIndex(isProcessing: Boolean = false): Int =
    syncSettingsLayout.maxFocusIndex(isProcessing)

@Composable
fun SyncSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    imageCacheProgress: ImageCacheProgress
) {
    val hasAnyModal = uiState.syncSettings.showSyncFiltersModal || uiState.syncSettings.showPlatformFiltersModal
    val modalBlur by animateDpAsState(
        targetValue = if (hasAnyModal) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "syncFiltersModalBlur"
    )

    val isProcessing = imageCacheProgress.isProcessing

    fun isFocused(item: SyncSettingsItem): Boolean =
        uiState.focusedIndex == syncSettingsLayout.focusIndexOf(item, isProcessing)
    val sections = syncSettingsLayout.buildSections(isProcessing)
    val visibleItems = syncSettingsLayout.visibleItems(isProcessing)

    Box(modifier = Modifier.fillMaxSize()) {
        SectionPaneLayout(
            items = visibleItems,
            sections = sections,
            focusedIndex = uiState.focusedIndex,
            focusToListIndex = { syncSettingsLayout.focusToListIndex(it, isProcessing) },
            itemKey = { it.key },
            isNavItem = { false },
            onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd).blur(modalBlur),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { item ->
                when (item) {
                    SyncSettingsItem.PlatformFilters -> {
                        val enabledCount = uiState.syncSettings.enabledPlatformCount
                        val totalCount = uiState.syncSettings.totalPlatforms
                        val subtitle = if (totalCount > 0) "$enabledCount/$totalCount platforms" else "Select platforms to sync"
                        ActionPreference(
                            icon = Icons.Default.FilterList,
                            title = "Platform Filters",
                            subtitle = subtitle,
                            isFocused = isFocused(item),
                            onClick = { viewModel.showPlatformFiltersModal() }
                        )
                    }
                    SyncSettingsItem.MetadataFilters -> {
                        val filtersSubtitle = buildFiltersSubtitle(uiState.syncSettings.syncFilters)
                        ActionPreference(
                            icon = Icons.Default.Tune,
                            title = "Metadata Filters",
                            subtitle = filtersSubtitle,
                            isFocused = isFocused(item),
                            onClick = { viewModel.showSyncFiltersModal() }
                        )
                    }
                    SyncSettingsItem.MediaHeader -> {
                        Spacer(modifier = Modifier.height(Dimens.spacingMd))
                        SectionHeader("MEDIA")
                    }
                    SyncSettingsItem.CacheScreenshots -> {
                        SwitchPreference(
                            title = "Cache Screenshots",
                            subtitle = "Boxart and backgrounds are always cached",
                            isEnabled = uiState.server.syncScreenshotsEnabled,
                            isFocused = isFocused(item),
                            onToggle = { viewModel.toggleSyncScreenshots() }
                        )
                    }
                    SyncSettingsItem.ImageCacheLocation -> {
                        val cachePath = uiState.syncSettings.imageCachePath
                        val displayPath = if (cachePath != null) {
                            "${cachePath.substringAfterLast("/")}/argosy_images"
                        } else {
                            "Internal (default)"
                        }
                        ImageCachePreference(
                            title = "Image Cache Location",
                            displayPath = displayPath,
                            hasCustomPath = cachePath != null,
                            isFocused = isFocused(item),
                            actionIndex = uiState.syncSettings.imageCacheActionIndex,
                            isMigrating = uiState.syncSettings.isImageCacheMigrating,
                            onChange = { viewModel.openImageCachePicker() },
                            onReset = { viewModel.resetImageCacheToDefault() }
                        )
                    }
                    SyncSettingsItem.ImageCacheProgressIndicator -> {
                        Spacer(modifier = Modifier.height(Dimens.spacingMd))
                        ImageCacheProgressItem(imageCacheProgress)
                    }
                }
        }

        if (uiState.syncSettings.showPlatformFiltersModal) {
            PlatformFiltersModal(
                platforms = uiState.syncSettings.platformFiltersList,
                filterMode = uiState.syncSettings.platformFilterMode,
                searchQuery = uiState.syncSettings.platformFilterSearchQuery,
                sortMode = uiState.syncSettings.platformFilterSortMode,
                focusIndex = uiState.syncSettings.platformFiltersModalFocusIndex,
                isLoading = uiState.syncSettings.isLoadingPlatforms,
                onTogglePlatform = { viewModel.togglePlatformSyncEnabled(it) },
                onSortModeChange = { viewModel.setPlatformFilterSortMode(it) },
                onFilterModeChange = { viewModel.cyclePlatformFilterMode() },
                onSearchQueryChange = { viewModel.setPlatformFilterSearchQuery(it) },
                onDismiss = { viewModel.dismissPlatformFiltersModal() }
            )
        }

        if (uiState.syncSettings.showSyncFiltersModal) {
            SyncFiltersModal(
                syncFilters = uiState.syncSettings.syncFilters,
                focusIndex = uiState.syncSettings.syncFiltersModalFocusIndex,
                showRegionPicker = uiState.syncSettings.showRegionPicker,
                regionPickerFocusIndex = uiState.syncSettings.regionPickerFocusIndex,
                onToggleRegion = { viewModel.toggleRegion(it) },
                onToggleRegionMode = { viewModel.toggleRegionMode() },
                onToggleExcludeBeta = { viewModel.setExcludeBeta(it) },
                onToggleExcludePrototype = { viewModel.setExcludePrototype(it) },
                onToggleExcludeDemo = { viewModel.setExcludeDemo(it) },
                onToggleExcludeHack = { viewModel.setExcludeHack(it) },
                onToggleDeleteOrphans = { viewModel.setDeleteOrphans(it) },
                onShowRegionPicker = { viewModel.showRegionPicker() },
                onDismissRegionPicker = { viewModel.dismissRegionPicker() },
                onDismiss = { viewModel.dismissSyncFiltersModal() }
            )
        }
    }
}

private fun buildFiltersSubtitle(filters: SyncFilterPreferences): String {
    val parts = mutableListOf<String>()
    if (filters.enabledRegions.isNotEmpty()) {
        val mode = if (filters.regionMode == RegionFilterMode.EXCLUDE) "excl" else "incl"
        parts.add("${filters.enabledRegions.size} regions ($mode)")
    }
    val excludes = listOfNotNull(
        if (filters.excludeBeta) "beta" else null,
        if (filters.excludePrototype) "proto" else null,
        if (filters.excludeDemo) "demo" else null,
        if (filters.excludeHack) "hacks" else null
    )
    if (excludes.isNotEmpty()) {
        parts.add("no ${excludes.joinToString("/")}")
    }
    return if (parts.isEmpty()) "No filters applied" else parts.joinToString(", ")
}

@Composable
private fun ImageCacheProgressItem(progress: ImageCacheProgress) {
    val disabledAlpha = 0.45f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(Dimens.radiusMd)
            )
            .padding(Dimens.spacingMd)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Caching images",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
            )
            Text(
                text = "${progress.progressPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = progress.currentGameTitle.take(30) + if (progress.currentGameTitle.length > 30) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha * 0.7f)
            )
            Text(
                text = progress.currentType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha * 0.7f)
            )
        }
    }
}
