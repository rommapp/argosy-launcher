package com.nendo.argosy.data.storage

import com.nendo.argosy.core.storage.StorageVolumeType

enum class StorageCategory {
    GAMES,
    MUSIC,
    MEDIA,
    IMAGE_CACHE,
    REMOTE_IMAGE_CACHE,
    SAVE_STATE_CACHE,
    ROM_EXTRACTION,
    ROM_STAGING,
    SFX_CACHE,
    BIOS,
    CORES_SYSTEM,
    STEAM,
    ANDROID_APPS,
    SHADERS_CATALOG,
    SHADERS_CUSTOM,
    FRAMES,
    FONTS,
    EMULATOR_APKS,
    MISC_DOWNLOADS,
    DATABASE
}

sealed interface WalkState {
    data object Pending : WalkState
    data class Walking(val bytes: Long, val files: Int) : WalkState
    data object Complete : WalkState
    data object Failed : WalkState
}

/** Per-volume byte maps are keyed by [StorageVolumeInfo.key] (canonical volume root path). */
data class CategoryUsage(
    val bytes: Long = 0L,
    val fileCount: Int = 0,
    val perVolume: Map<String, Long> = emptyMap()
)

data class PlatformUsage(
    val platformId: Long,
    val name: String,
    val sortOrder: Int,
    val downloadedCount: Int,
    val bytes: Long,
    val perVolume: Map<String, Long>
)

/**
 * Downloaded video attributed to one media library.
 *
 * [bytes] counts only files that were readable when the scan ran. Titles whose storage is not
 * connected keep their last recorded size in [offlineBytes] instead of collapsing to zero, because
 * an unplugged card is not a deleted download; [missingCount] is the separate case of a file that is
 * gone from storage that IS connected.
 */
data class MediaLibraryUsage(
    val libraryId: String,
    val name: String,
    val displayOrder: Int,
    val downloadedCount: Int,
    val bytes: Long,
    val perVolume: Map<String, Long>,
    val offlineCount: Int,
    val offlineBytes: Long,
    val missingCount: Int
)

/**
 * One place downloaded media occupies. There is more than one whenever the media folder has been
 * moved and earlier downloads stayed where they were, so the folder in settings is a destination for
 * new downloads rather than the whole answer to where content lives.
 */
data class MediaLocationUsage(
    val path: String,
    val volumeKey: String?,
    val bytes: Long,
    val fileCount: Int,
    val isCurrentTarget: Boolean,
    val isAvailable: Boolean
)

/** Cheap per-volume change detector: any material write moves [usedBytes]. */
data class VolumeFingerprint(
    val totalBytes: Long,
    val usedBytes: Long
)

data class StorageSnapshot(
    val computedAt: Long,
    val categories: Map<StorageCategory, CategoryUsage>,
    val gamesPerPlatform: List<PlatformUsage>,
    val volumeFingerprints: Map<String, VolumeFingerprint> = emptyMap(),
    val mediaPerLibrary: List<MediaLibraryUsage> = emptyList(),
    val mediaLocations: List<MediaLocationUsage> = emptyList()
)

/** Live-detected volume; never persisted. [key] is the canonical root path used for attribution. */
data class StorageVolumeInfo(
    val key: String,
    val displayName: String,
    val type: StorageVolumeType,
    val totalBytes: Long,
    val availableBytes: Long
)
