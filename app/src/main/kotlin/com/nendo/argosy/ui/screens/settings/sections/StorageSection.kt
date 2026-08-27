package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.steam.SteamConnectionState
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.data.storage.StorageSnapshot
import com.nendo.argosy.data.storage.WalkState
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CategoryTile
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import androidx.compose.material3.MaterialTheme
import com.nendo.argosy.ui.components.VolumeMeterCategory
import com.nendo.argosy.ui.components.VolumeMeterHero
import com.nendo.argosy.ui.components.storageComputedLabel
import com.nendo.argosy.ui.components.volumeMeterCategoryColors
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.pressScale
import com.nendo.argosy.util.formatBytes

internal data class StorageLayoutState(val steamVisible: Boolean, val mediaVisible: Boolean) {
    companion object {
        fun from(state: SettingsUiState) = StorageLayoutState(
            steamVisible = storageSteamVisible(state),
            mediaVisible = storageMediaVisible(state)
        )
    }
}

internal fun storageSteamVisible(state: SettingsUiState): Boolean =
    state.attribution.steamTileLatched

internal fun storageSteamVisibleLive(state: SettingsUiState): Boolean =
    state.steam.connectionState == SteamConnectionState.LOGGED_IN ||
        (state.attribution.snapshot?.categories?.get(StorageCategory.STEAM)?.bytes ?: 0L) > 0L

internal fun storageMediaVisible(state: SettingsUiState): Boolean =
    state.attribution.mediaTileLatched

internal fun storageMediaVisibleLive(state: SettingsUiState): Boolean =
    state.jellyfin.isSignedIn ||
        (state.attribution.snapshot?.categories?.get(StorageCategory.MEDIA)?.bytes ?: 0L) > 0L ||
        state.attribution.snapshot?.mediaPerLibrary?.isNotEmpty() == true

internal sealed class StorageItem(
    val key: String,
    val section: String,
    val visibleWhen: (StorageLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer, VolumeHero -> false
        else -> true
    }

    class Header(key: String, section: String, val titleRes: Int) : StorageItem(key, section)
    class SectionSpacer(key: String, section: String) : StorageItem(key, section)

    data object VolumeHero : StorageItem("volumeHero", "overview")
    data object RecomputeRow : StorageItem("recomputeRow", "overview")
    data object GamesTile : StorageItem("gamesTile", "overview")
    data object MusicTile : StorageItem("musicTile", "overview")
    data object MediaTile : StorageItem("mediaTile", "overview", { it.mediaVisible })
    data object CachesTile : StorageItem("cachesTile", "overview")
    data object SteamTile : StorageItem("steamTile", "overview", { it.steamVisible })

    data object GlobalRomPath : StorageItem("globalRomPath", "locations")
    data object ImageCache : StorageItem("imageCache", "locations")
    data object MusicLocation : StorageItem("musicLocation", "locations")
    data object BiosFolder : StorageItem("biosFolder", "locations")
    data object BuiltinSavePath : StorageItem("builtinSavePath", "locations")
    data object BuiltinStatePath : StorageItem("builtinStatePath", "locations")

    data object MaxDownloads : StorageItem("maxDownloads", "downloads")
    data object Threshold : StorageItem("threshold", "downloads")
    data object InternalStaging : StorageItem("internalStaging", "downloads")

    data object ResetLibrary : StorageItem("resetLibrary", "danger")
    data object HardReset : StorageItem("hardReset", "danger")

    companion object {
        private val LocationsSpacer = SectionSpacer("locationsSpacer", "locations")
        private val LocationsHeader =
            Header("locationsHeader", "locations", R.string.settings_storage_section_locations)
        private val DownloadsSpacer = SectionSpacer("downloadsSpacer", "downloads")
        private val DownloadsHeader =
            Header("downloadsHeader", "downloads", R.string.settings_storage_section_downloads)
        private val DangerSpacer = SectionSpacer("dangerSpacer", "danger")
        private val DangerHeader = Header("dangerHeader", "danger", R.string.settings_storage_section_danger)

        val ALL: List<StorageItem>
            get() = listOf(
                VolumeHero, RecomputeRow, GamesTile, MediaTile, MusicTile, CachesTile, SteamTile,
                LocationsSpacer, LocationsHeader,
                GlobalRomPath, ImageCache, MusicLocation, BiosFolder, BuiltinSavePath, BuiltinStatePath,
                DownloadsSpacer, DownloadsHeader, MaxDownloads, Threshold, InternalStaging,
                DangerSpacer, DangerHeader, ResetLibrary, HardReset
            )
    }
}

private val storageLayout = SettingsLayout<StorageItem, StorageLayoutState>(
    allItems = StorageItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "overview" -> R.string.settings_storage_section_overview
            "locations" -> R.string.settings_storage_section_locations
            "downloads" -> R.string.settings_storage_section_downloads
            "danger" -> R.string.settings_storage_section_danger
            else -> null
        }
    }
)

internal data class StorageLayoutInfo(
    val layout: SettingsLayout<StorageItem, StorageLayoutState>,
    val state: StorageLayoutState
)

internal fun createStorageLayoutInfo(state: SettingsUiState): StorageLayoutInfo =
    StorageLayoutInfo(storageLayout, StorageLayoutState.from(state))

internal fun storageItemAtFocusIndex(index: Int, info: StorageLayoutInfo): StorageItem? =
    info.layout.itemAtFocusIndex(index, info.state)

internal fun storageFocusIndexOf(item: StorageItem, info: StorageLayoutInfo): Int =
    info.layout.focusIndexOf(item, info.state)

internal fun storageSections(info: StorageLayoutInfo): List<ListSection> =
    info.layout.buildSections(info.state)

private val CACHES_CATEGORIES: Set<StorageCategory> = StorageCategory.entries.toSet() -
    setOf(
        StorageCategory.GAMES,
        StorageCategory.MUSIC,
        StorageCategory.MEDIA,
        StorageCategory.STEAM,
        StorageCategory.ANDROID_APPS
    )

private fun groupBytes(snapshot: StorageSnapshot?, group: Set<StorageCategory>): Long =
    group.sumOf { snapshot?.categories?.get(it)?.bytes ?: 0L }

private fun groupFileCount(snapshot: StorageSnapshot?, group: Set<StorageCategory>): Int =
    group.sumOf { snapshot?.categories?.get(it)?.fileCount ?: 0 }

private fun groupPerVolume(snapshot: StorageSnapshot?, group: Set<StorageCategory>): Map<String, Long> {
    val merged = HashMap<String, Long>()
    group.forEach { category ->
        snapshot?.categories?.get(category)?.perVolume?.forEach { (key, bytes) ->
            merged.merge(key, bytes, Long::plus)
        }
    }
    return merged
}

@Composable
internal fun RecomputeRow(
    label: String,
    isRefreshing: Boolean,
    isFocused: Boolean,
    onRefresh: () -> Unit,
    onDeepRescan: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
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
            .clickableNoFocus(
                interactionSource = interaction,
                onClick = { if (!isRefreshing) onRefresh() },
                onLongClick = { if (!isRefreshing) onDeepRescan() }
            )
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.iconSm),
                strokeWidth = Dimens.borderMedium,
                color = theme.textMute
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = theme.textDim,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = stringResource(R.string.settings_storage_recompute_title),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textMute
        )
    }
}

private fun displayBytes(snapshot: StorageSnapshot?, walk: WalkState?, group: Set<StorageCategory>): Long {
    val walking = (walk as? WalkState.Walking)?.bytes ?: 0L
    if (snapshot != null && group.any { snapshot.categories.containsKey(it) }) {
        return maxOf(groupBytes(snapshot, group), walking)
    }
    return walking
}

private fun WalkState?.isActiveWalk(): Boolean =
    this is WalkState.Walking || this is WalkState.Pending

@Composable
fun StorageSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val storage = uiState.storage
    val syncSettings = uiState.syncSettings
    val attribution = uiState.attribution
    val snapshot = attribution.snapshot
    val walkProgress = attribution.walkProgress

    val context = LocalContext.current
    val steamVisible = storageSteamVisible(uiState)
    val mediaVisible = storageMediaVisible(uiState)
    val layoutState = remember(steamVisible, mediaVisible) { StorageLayoutState(steamVisible, mediaVisible) }
    val visibleItems = remember(layoutState) { storageLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) { storageLayout.buildSections(layoutState, context) }

    val gamesBytes = remember(snapshot, walkProgress) {
        displayBytes(snapshot, walkProgress[StorageCategory.GAMES], setOf(StorageCategory.GAMES))
    }
    val musicBytes = remember(snapshot, walkProgress) {
        displayBytes(snapshot, walkProgress[StorageCategory.MUSIC], setOf(StorageCategory.MUSIC))
    }
    val mediaBytes = remember(snapshot, walkProgress) {
        displayBytes(snapshot, walkProgress[StorageCategory.MEDIA], setOf(StorageCategory.MEDIA))
    }
    val mediaCount = remember(snapshot) {
        snapshot?.mediaPerLibrary?.sumOf { it.downloadedCount } ?: 0
    }
    val steamBytes = remember(snapshot, walkProgress) {
        displayBytes(snapshot, walkProgress[StorageCategory.STEAM], setOf(StorageCategory.STEAM))
    }
    val appsBytes = remember(snapshot, walkProgress) {
        displayBytes(snapshot, walkProgress[StorageCategory.ANDROID_APPS], setOf(StorageCategory.ANDROID_APPS))
    }
    val cachesBytes = remember(snapshot) { groupBytes(snapshot, CACHES_CATEGORIES) }
    val cachesFileCount = remember(snapshot) { groupFileCount(snapshot, CACHES_CATEGORIES) }
    val gamesCount = remember(snapshot, storage.downloadedGamesCount) {
        snapshot?.gamesPerPlatform?.takeIf { it.isNotEmpty() }?.sumOf { it.downloadedCount }
            ?: storage.downloadedGamesCount
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val categoryColors = remember(primary, secondary) {
        volumeMeterCategoryColors(primary, secondary, 6)
    }
    val appsVisible = appsBytes > 0L
    val heroCategories = remember(
        snapshot,
        walkProgress,
        steamVisible,
        mediaVisible,
        appsVisible,
        categoryColors,
        context
    ) {
        buildList {
            add(VolumeMeterCategory(
                label = context.getString(R.string.settings_storage_meter_games),
                color = categoryColors[0],
                bytes = gamesBytes,
                perVolume = groupPerVolume(snapshot, setOf(StorageCategory.GAMES))
            ))
            if (mediaVisible) {
                add(VolumeMeterCategory(
                    label = context.getString(R.string.settings_storage_meter_media),
                    color = categoryColors[5],
                    bytes = mediaBytes,
                    perVolume = groupPerVolume(snapshot, setOf(StorageCategory.MEDIA))
                ))
            }
            if (steamVisible) {
                add(VolumeMeterCategory(
                    label = context.getString(R.string.settings_storage_meter_steam),
                    color = categoryColors[1],
                    bytes = steamBytes,
                    perVolume = groupPerVolume(snapshot, setOf(StorageCategory.STEAM))
                ))
            }
            if (appsVisible) {
                add(VolumeMeterCategory(
                    label = context.getString(R.string.settings_storage_meter_apps),
                    color = categoryColors[2],
                    bytes = appsBytes,
                    perVolume = groupPerVolume(snapshot, setOf(StorageCategory.ANDROID_APPS))
                ))
            }
            add(VolumeMeterCategory(
                label = context.getString(R.string.settings_storage_meter_caches),
                color = categoryColors[3],
                bytes = cachesBytes,
                perVolume = groupPerVolume(snapshot, CACHES_CATEGORIES)
            ))
            add(VolumeMeterCategory(
                label = context.getString(R.string.settings_storage_meter_music),
                color = categoryColors[4],
                bytes = musicBytes,
                perVolume = groupPerVolume(snapshot, setOf(StorageCategory.MUSIC))
            ))
        }
    }

    fun isFocused(item: StorageItem): Boolean =
        uiState.focusedIndex == storageLayout.focusIndexOf(item, layoutState)

    fun openFrom(item: StorageItem, enter: () -> Unit) {
        viewModel.setFocusIndex(storageLayout.focusIndexOf(item, layoutState))
        enter()
    }

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { storageLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { it is StorageItem.SectionSpacer },
        isHeader = { it is StorageItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is StorageItem.Header -> SectionHeader(stringResource(item.titleRes))

            is StorageItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            StorageItem.VolumeHero -> VolumeMeterHero(
                volumes = attribution.volumes,
                categories = heroCategories,
                modifier = Modifier.padding(bottom = Dimens.spacingSm)
            )

            StorageItem.RecomputeRow -> RecomputeRow(
                label = storageComputedLabel(snapshot?.computedAt, attribution.isRefreshing),
                isRefreshing = attribution.isRefreshing,
                isFocused = isFocused(item),
                onRefresh = { viewModel.refreshStorageAttribution() },
                onDeepRescan = { viewModel.refreshStorageAttribution(deep = true) }
            )

            StorageItem.GamesTile -> CategoryTile(
                title = stringResource(R.string.settings_storage_games_tile_title),
                icon = Icons.Default.SportsEsports,
                primaryStat = formatBytes(gamesBytes),
                secondaryStat = pluralStringResource(
                    R.plurals.settings_storage_games_tile_count,
                    gamesCount,
                    gamesCount
                ),
                isFocused = isFocused(item),
                isWorking = walkProgress[StorageCategory.GAMES].isActiveWalk(),
                onClick = { openFrom(item) { viewModel.navigateToStorageGames() } }
            )

            StorageItem.MediaTile -> CategoryTile(
                title = stringResource(R.string.settings_storage_media_tile_title),
                icon = Icons.Outlined.Movie,
                primaryStat = formatBytes(mediaBytes),
                secondaryStat = pluralStringResource(
                    R.plurals.settings_storage_media_tile_count,
                    mediaCount,
                    mediaCount
                ),
                isFocused = isFocused(item),
                isWorking = walkProgress[StorageCategory.MEDIA].isActiveWalk(),
                onClick = { openFrom(item) { viewModel.navigateToStorageMedia() } }
            )

            StorageItem.MusicTile -> CategoryTile(
                title = stringResource(R.string.settings_storage_music_tile_title),
                icon = Icons.Outlined.MusicNote,
                primaryStat = formatBytes(musicBytes),
                secondaryStat = stringResource(R.string.settings_storage_music_tile_action),
                isFocused = isFocused(item),
                isWorking = walkProgress[StorageCategory.MUSIC].isActiveWalk(),
                onClick = { openFrom(item) { viewModel.navigateToThemeMusic() } }
            )

            StorageItem.CachesTile -> CategoryTile(
                title = stringResource(R.string.settings_storage_caches_tile_title),
                icon = Icons.Default.Cached,
                primaryStat = formatBytes(cachesBytes),
                secondaryStat = pluralStringResource(
                    R.plurals.settings_storage_caches_tile_count,
                    cachesFileCount,
                    cachesFileCount
                ),
                isFocused = isFocused(item),
                isWorking = CACHES_CATEGORIES.any { walkProgress[it].isActiveWalk() },
                onClick = { openFrom(item) { viewModel.navigateToStorageCaches() } }
            )

            StorageItem.SteamTile -> CategoryTile(
                title = stringResource(R.string.settings_storage_steam_tile_title),
                icon = Icons.Default.CloudQueue,
                primaryStat = formatBytes(steamBytes),
                secondaryStat = stringResource(R.string.settings_storage_steam_tile_subtitle),
                isFocused = isFocused(item),
                isWorking = walkProgress[StorageCategory.STEAM].isActiveWalk(),
                onClick = { openFrom(item) { viewModel.navigateToStorageCachesForSteam() } }
            )

            StorageItem.GlobalRomPath -> ActionPreference(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.settings_storage_rom_path_title),
                subtitle = formatStoragePath(storage.romStoragePath),
                isFocused = isFocused(item),
                trailingText = stringResource(
                    R.string.settings_storage_rom_path_free,
                    formatBytes(storage.availableSpace)
                ),
                onClick = { viewModel.openFolderPicker() }
            )

            StorageItem.ImageCache -> {
                val cachePath = syncSettings.imageCachePath
                val displayPath = if (cachePath != null) {
                    "${cachePath.substringAfterLast("/")}/argosy_images"
                } else {
                    stringResource(R.string.settings_storage_image_cache_internal)
                }
                ActionPreference(
                    icon = Icons.Default.Image,
                    title = stringResource(R.string.settings_storage_image_cache_title),
                    subtitle = if (syncSettings.isImageCacheMigrating) {
                        stringResource(R.string.settings_storage_image_cache_migrating)
                    } else {
                        displayPath
                    },
                    isFocused = isFocused(item),
                    isEnabled = !syncSettings.isImageCacheMigrating,
                    onClick = { viewModel.openImageCachePicker() }
                )
            }

            StorageItem.MusicLocation -> ActionPreference(
                icon = Icons.Outlined.LibraryMusic,
                title = stringResource(R.string.settings_storage_music_location_title),
                subtitle = uiState.ambientAudio.musicDirPath?.let { formatStoragePath(it) }
                    ?: stringResource(R.string.settings_storage_music_location_default),
                isFocused = isFocused(item),
                onClick = { viewModel.openMusicLocationPicker() }
            )

            StorageItem.BiosFolder -> ActionPreference(
                icon = Icons.Default.Memory,
                title = stringResource(R.string.settings_storage_bios_folder_title),
                subtitle = if (uiState.bios.isBiosMigrating) {
                    stringResource(R.string.settings_storage_bios_folder_migrating)
                } else {
                    uiState.bios.customBiosPath?.let { formatStoragePath(it) }
                        ?: stringResource(R.string.settings_storage_bios_folder_internal)
                },
                isFocused = isFocused(item),
                isEnabled = !uiState.bios.isBiosMigrating,
                onClick = { viewModel.openBiosFolderPicker() }
            )

            StorageItem.BuiltinSavePath -> ActionPreference(
                icon = Icons.Default.Save,
                title = stringResource(R.string.settings_storage_builtin_save_path_title),
                subtitle = if (uiState.builtinVideo.isCustomSavePath) {
                    formatStoragePath(uiState.builtinVideo.savePath)
                } else {
                    stringResource(R.string.settings_storage_builtin_save_path_internal)
                },
                isFocused = isFocused(item),
                onClick = { viewModel.openBuiltinSavePathBrowser() }
            )

            StorageItem.BuiltinStatePath -> ActionPreference(
                icon = Icons.Default.History,
                title = stringResource(R.string.settings_storage_builtin_state_path_title),
                subtitle = if (uiState.builtinVideo.isCustomStatePath) {
                    formatStoragePath(uiState.builtinVideo.statePath)
                } else {
                    stringResource(R.string.settings_storage_builtin_state_path_internal)
                },
                isFocused = isFocused(item),
                onClick = { viewModel.openBuiltinStatePathBrowser() }
            )

            StorageItem.MaxDownloads -> SliderPreference(
                title = stringResource(R.string.settings_storage_max_downloads_title),
                value = storage.maxConcurrentDownloads,
                minValue = 1,
                maxValue = 5,
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustMaxConcurrentDownloads(it) }
            )

            StorageItem.Threshold -> {
                val thresholds = remember { listOf(50, 100, 250, 500) }
                val currentIndex = thresholds.indexOf(storage.instantDownloadThresholdMb).coerceAtLeast(0)
                CyclePreference(
                    title = stringResource(R.string.settings_storage_threshold_title),
                    value = stringResource(
                        R.string.settings_storage_threshold_value,
                        storage.instantDownloadThresholdMb
                    ),
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleInstantDownloadThreshold(1) },
                    onPrev = { viewModel.cycleInstantDownloadThreshold(-1) },
                    subtitle = stringResource(R.string.settings_storage_threshold_subtitle),
                    options = remember(context) {
                        thresholds.map { context.getString(R.string.settings_storage_threshold_value, it) }
                    },
                    onSelect = { viewModel.cycleInstantDownloadThreshold(it - currentIndex) },
                    pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
                )
            }

            StorageItem.InternalStaging -> SwitchPreference(
                title = stringResource(R.string.settings_storage_internal_staging_title),
                subtitle = stringResource(R.string.settings_storage_internal_staging_subtitle),
                isEnabled = storage.stageDownloadsInternally,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleStageDownloadsInternally() }
            )

            StorageItem.ResetLibrary -> {
                val isPurging = storage.isPurgingAll
                ActionPreference(
                    title = stringResource(R.string.settings_storage_reset_library_title),
                    subtitle = if (isPurging) {
                        stringResource(R.string.settings_storage_reset_library_busy)
                    } else {
                        stringResource(R.string.settings_storage_reset_library_subtitle)
                    },
                    isFocused = isFocused(item),
                    isDangerous = true,
                    isEnabled = !isPurging && !storage.isHardResetting,
                    onClick = { viewModel.requestPurgeAll() }
                )
            }

            StorageItem.HardReset -> ActionPreference(
                title = stringResource(R.string.settings_storage_hard_reset_title),
                subtitle = if (storage.isHardResetting) {
                    stringResource(R.string.settings_storage_hard_reset_busy)
                } else {
                    stringResource(R.string.settings_storage_hard_reset_subtitle)
                },
                isFocused = isFocused(item),
                isDangerous = true,
                isEnabled = !storage.isHardResetting && !storage.isPurgingAll,
                onClick = { viewModel.requestHardReset() }
            )
        }
    }
}

internal fun formatStoragePath(rawPath: String): String {
    val primaryRoot = com.nendo.argosy.data.storage.StoragePathUtils.primaryExternalRoot
    return when {
        rawPath.startsWith(primaryRoot) ->
            rawPath.replaceFirst(primaryRoot, "Internal")
        rawPath.startsWith("/storage/") -> {
            val parts = rawPath.removePrefix("/storage/").split("/", limit = 2)
            if (parts.size == 2) {
                "/storage/${parts[0]}/${parts[1]}"
            } else null
        }
        else -> null
    } ?: rawPath
}
