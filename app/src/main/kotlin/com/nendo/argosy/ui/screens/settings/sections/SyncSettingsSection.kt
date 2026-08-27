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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.cache.ImageCacheProgress
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.preferences.DownloadDefaults
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.ImageCachePreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.SYNC_REGION_MODE_PICKER_KEY
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
    val isFocusable: Boolean get() =
        this !is MediaHeader && this !is ImageCacheProgressIndicator && this !is DownloadDefaultsHeader

    data object PlatformFilters : SyncSettingsItem("platformFilters", "filters")
    data object MetadataFilters : SyncSettingsItem("metadataFilters", "filters")
    data object MediaHeader : SyncSettingsItem("mediaHeader", "media")
    data object CacheScreenshots : SyncSettingsItem("cacheScreenshots", "media")
    data object CacheBoxArt : SyncSettingsItem("cacheBoxArt", "media")
    data object UploadScreenshots : SyncSettingsItem("uploadScreenshots", "media")
    data object ImageCacheLocation : SyncSettingsItem("imageCacheLocation", "media")
    data object ImageCacheProgressIndicator : SyncSettingsItem("imageCacheProgress", "media")
    data object DownloadDefaultsHeader : SyncSettingsItem("downloadDefaultsHeader", "downloads")
    class CategoryDefault(val categoryKey: String) :
        SyncSettingsItem("dl_$categoryKey", "downloads")

    companion object {
        val CATEGORY_DEFAULTS: List<CategoryDefault> =
            DownloadDefaults.CONFIGURABLE_KEYS.map { CategoryDefault(it) }
    }
}

internal fun downloadCategoryLabel(context: android.content.Context, key: String): String =
    if (key == DownloadDefaults.OTHER_KEY) {
        context.getString(R.string.settings_sync_download_category_other)
    } else {
        context.getString(VariantCategory.fromKey(key).labelRes)
    }

private val syncSettingsLayout = SettingsLayout<SyncSettingsItem, Boolean>(
    allItems = listOf(
        SyncSettingsItem.PlatformFilters,
        SyncSettingsItem.MetadataFilters,
        SyncSettingsItem.MediaHeader,
        SyncSettingsItem.CacheScreenshots,
        SyncSettingsItem.CacheBoxArt,
        SyncSettingsItem.UploadScreenshots,
        SyncSettingsItem.ImageCacheLocation,
        SyncSettingsItem.ImageCacheProgressIndicator,
        SyncSettingsItem.DownloadDefaultsHeader
    ) + SyncSettingsItem.CATEGORY_DEFAULTS,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, isProcessing ->
        if (item is SyncSettingsItem.ImageCacheProgressIndicator) isProcessing else true
    },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "media" -> R.string.settings_sync_section_media
            "downloads" -> R.string.settings_sync_section_download_defaults
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
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshUsageStatsPermission()
        }
    }

    fun isFocused(item: SyncSettingsItem): Boolean =
        uiState.focusedIndex == syncSettingsLayout.focusIndexOf(item, isProcessing)
    val sections = syncSettingsLayout.buildSections(isProcessing, context)
    val visibleItems = syncSettingsLayout.visibleItems(isProcessing)

    Box(modifier = Modifier.fillMaxSize()) {
        SectionPaneLayout(
            items = visibleItems,
            sections = sections,
            focusedIndex = uiState.focusedIndex,
            focusToListIndex = { syncSettingsLayout.focusToListIndex(it, isProcessing) },
            itemKey = { it.key },
            isNavItem = { false },
            isHeader = { it is SyncSettingsItem.MediaHeader },
            onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd).blur(modalBlur),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { item ->
                when (item) {
                    SyncSettingsItem.PlatformFilters -> {
                        val enabledCount = uiState.syncSettings.enabledPlatformCount
                        val totalCount = uiState.syncSettings.totalPlatforms
                        val subtitle = if (totalCount > 0) {
                            stringResource(
                                R.string.settings_sync_platform_filters_subtitle_count,
                                enabledCount,
                                totalCount
                            )
                        } else {
                            stringResource(R.string.settings_sync_platform_filters_subtitle_empty)
                        }
                        ActionPreference(
                            icon = Icons.Default.FilterList,
                            title = stringResource(R.string.settings_sync_platform_filters_title),
                            subtitle = subtitle,
                            isFocused = isFocused(item),
                            onClick = { viewModel.showPlatformFiltersModal() }
                        )
                    }
                    SyncSettingsItem.MetadataFilters -> {
                        val filtersSubtitle = buildFiltersSubtitle(context, uiState.syncSettings.syncFilters)
                        ActionPreference(
                            icon = Icons.Default.Tune,
                            title = stringResource(R.string.settings_sync_metadata_filters_title),
                            subtitle = filtersSubtitle,
                            isFocused = isFocused(item),
                            onClick = { viewModel.showSyncFiltersModal() }
                        )
                    }
                    SyncSettingsItem.MediaHeader -> {
                        Spacer(modifier = Modifier.height(Dimens.spacingMd))
                        SectionHeader(stringResource(R.string.settings_sync_section_media))
                    }
                    SyncSettingsItem.CacheScreenshots -> {
                        SwitchPreference(
                            title = stringResource(R.string.settings_sync_cache_screenshots_title),
                            subtitle = stringResource(R.string.settings_sync_cache_screenshots_subtitle),
                            isEnabled = uiState.server.syncScreenshotsEnabled,
                            isFocused = isFocused(item),
                            onToggle = { viewModel.toggleSyncScreenshots() }
                        )
                    }
                    SyncSettingsItem.CacheBoxArt -> {
                        SwitchPreference(
                            title = stringResource(R.string.settings_sync_cache_box_art_title),
                            subtitle = stringResource(R.string.settings_sync_cache_box_art_subtitle),
                            isEnabled = uiState.server.boxArtCacheEnabled,
                            isFocused = isFocused(item),
                            onToggle = { viewModel.toggleBoxArtCache() }
                        )
                    }
                    SyncSettingsItem.UploadScreenshots -> {
                        val supported = uiState.server.screenshotUploadSupported
                        val hasUsageAccess = uiState.controls.hasUsageStatsPermission
                        val subtitle = when {
                            !supported -> stringResource(R.string.settings_sync_upload_screenshots_subtitle_unsupported)
                            uiState.server.uploadScreenshotsEnabled && !hasUsageAccess ->
                                stringResource(R.string.settings_sync_upload_screenshots_subtitle_needs_usage)
                            else -> stringResource(R.string.settings_sync_upload_screenshots_subtitle)
                        }
                        Box(modifier = Modifier.alpha(if (supported) 1f else 0.45f)) {
                            SwitchPreference(
                                title = stringResource(R.string.settings_sync_upload_screenshots_title),
                                subtitle = subtitle,
                                isEnabled = supported && uiState.server.uploadScreenshotsEnabled,
                                isFocused = isFocused(item),
                                onToggle = { if (supported) viewModel.toggleUploadScreenshots() }
                            )
                        }
                    }
                    SyncSettingsItem.ImageCacheLocation -> {
                        val cachePath = uiState.syncSettings.imageCachePath
                        val displayPath = if (cachePath != null) {
                            "${cachePath.substringAfterLast("/")}/argosy_images"
                        } else {
                            stringResource(R.string.settings_sync_image_cache_path_internal)
                        }
                        ImageCachePreference(
                            title = stringResource(R.string.settings_sync_image_cache_title),
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
                    SyncSettingsItem.DownloadDefaultsHeader -> {
                        Spacer(modifier = Modifier.height(Dimens.spacingMd))
                        SectionHeader(stringResource(R.string.settings_sync_section_download_defaults))
                    }
                    is SyncSettingsItem.CategoryDefault -> {
                        val included = uiState.syncSettings.downloadDefaults[item.categoryKey]
                            ?: (DownloadDefaults.FACTORY[item.categoryKey] ?: false)
                        SwitchPreference(
                            title = downloadCategoryLabel(context, item.categoryKey),
                            subtitle = stringResource(
                                R.string.settings_sync_download_category_subtitle,
                                downloadCategoryLabel(context, item.categoryKey).lowercase()
                            ),
                            isEnabled = included,
                            isFocused = isFocused(item),
                            onToggle = { viewModel.setDownloadCategoryDefault(item.categoryKey, !included) }
                        )
                    }
                }
        }

        if (uiState.syncSettings.showPlatformFiltersModal) {
            PlatformFiltersModal(
                platforms = uiState.syncSettings.platformFiltersList,
                filterMode = uiState.syncSettings.platformFilterMode,
                searchQuery = uiState.syncSettings.platformFilterSearchQuery,
                focusIndex = uiState.syncSettings.platformFiltersModalFocusIndex,
                isLoading = uiState.syncSettings.isLoadingPlatforms,
                headerFocused = uiState.syncSettings.platformFiltersHeaderFocused,
                headerIndex = uiState.syncSettings.platformFiltersHeaderIndex,
                searchActive = uiState.syncSettings.platformFiltersSearchActive,
                sortMenuOpen = uiState.syncSettings.platformFiltersSortMenuOpen,
                sortMenuIndex = uiState.syncSettings.platformFiltersSortMenuIndex,
                onTogglePlatform = { viewModel.togglePlatformSyncEnabled(it) },
                onSearchQueryChange = { viewModel.setPlatformFilterSearchQuery(it) },
                onSortModeChange = {
                    viewModel.setPlatformFilterSortMode(it)
                    viewModel.closePlatformSortMenu()
                },
                onFilterModeChange = { viewModel.cyclePlatformFilterMode() },
                onOpenSearch = { viewModel.openPlatformSearch() },
                onCloseSearch = { viewModel.closePlatformSearch() },
                onOpenSortMenu = { viewModel.openPlatformSortMenu() },
                onCloseSortMenu = { viewModel.closePlatformSortMenu() },
                onDismiss = { viewModel.dismissPlatformFiltersModal() }
            )
        }

        if (uiState.syncSettings.showSyncFiltersModal) {
            SyncFiltersModal(
                syncFilters = uiState.syncSettings.syncFilters,
                focusIndex = uiState.syncSettings.syncFiltersModalFocusIndex,
                showRegionPicker = uiState.syncSettings.showRegionPicker,
                regionPickerFocusIndex = uiState.syncSettings.regionPickerFocusIndex,
                regionPickerRegions = uiState.syncSettings.syncFilters.pickerDisplayOrder,
                regionPickerHeldRegion = uiState.syncSettings.regionPickerHeldRegion,
                onToggleRegion = { viewModel.toggleRegion(it) },
                onLiftRegion = { viewModel.liftRegionAt(it) },
                onMoveRegionTo = { region, index -> viewModel.moveRegionTo(region, index) },
                onDropRegion = { viewModel.dropHeldRegion() },
                onToggleRegionMode = { viewModel.toggleRegionMode() },
                onToggleExcludeBeta = { viewModel.setExcludeBeta(it) },
                onToggleExcludePrototype = { viewModel.setExcludePrototype(it) },
                onToggleExcludeDemo = { viewModel.setExcludeDemo(it) },
                onToggleExcludeHack = { viewModel.setExcludeHack(it) },
                onToggleDeleteOrphans = { viewModel.setDeleteOrphans(it) },
                onShowRegionPicker = { viewModel.showRegionPicker() },
                onDismissRegionPicker = { viewModel.dismissRegionPicker() },
                onDismiss = { viewModel.dismissSyncFiltersModal() },
                regionModePickerToken = if (uiState.enumPickerKey == SYNC_REGION_MODE_PICKER_KEY) uiState.enumPickerToken else 0
            )
        }
    }
}

private fun buildFiltersSubtitle(
    context: android.content.Context,
    filters: SyncFilterPreferences
): String {
    val parts = mutableListOf<String>()
    if (filters.enabledRegions.isNotEmpty()) {
        val mode = context.getString(
            if (filters.regionMode == RegionFilterMode.EXCLUDE) {
                R.string.settings_sync_metadata_filters_region_mode_exclude
            } else {
                R.string.settings_sync_metadata_filters_region_mode_include
            }
        )
        parts.add(
            context.resources.getQuantityString(
                R.plurals.settings_sync_metadata_filters_regions,
                filters.enabledRegions.size,
                filters.enabledRegions.size,
                mode
            )
        )
    }
    val excludes = listOfNotNull(
        if (filters.excludeBeta) context.getString(R.string.settings_sync_metadata_filters_beta) else null,
        if (filters.excludePrototype) context.getString(R.string.settings_sync_metadata_filters_prototype) else null,
        if (filters.excludeDemo) context.getString(R.string.settings_sync_metadata_filters_demo) else null,
        if (filters.excludeHack) context.getString(R.string.settings_sync_metadata_filters_hack) else null
    )
    if (excludes.isNotEmpty()) {
        parts.add(
            context.getString(
                R.string.settings_sync_metadata_filters_excluded,
                excludes.joinToString("/")
            )
        )
    }
    return if (parts.isEmpty()) {
        context.getString(R.string.settings_sync_metadata_filters_subtitle_none)
    } else {
        parts.joinToString(", ")
    }
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
                text = stringResource(R.string.settings_sync_image_cache_progress_title),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
            )
            Text(
                text = stringResource(
                    R.string.settings_sync_image_cache_progress_percent,
                    progress.progressPercent
                ),
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
