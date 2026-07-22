package com.nendo.argosy.data.storage

import com.nendo.argosy.core.storage.StorageVolumeType

enum class StorageCategory {
    GAMES,
    MUSIC,
    IMAGE_CACHE,
    SAVE_STATE_CACHE,
    ROM_EXTRACTION,
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

/** Cheap per-volume change detector: any material write moves [usedBytes]. */
data class VolumeFingerprint(
    val totalBytes: Long,
    val usedBytes: Long
)

data class StorageSnapshot(
    val computedAt: Long,
    val categories: Map<StorageCategory, CategoryUsage>,
    val gamesPerPlatform: List<PlatformUsage>,
    val volumeFingerprints: Map<String, VolumeFingerprint> = emptyMap()
)

/** Live-detected volume; never persisted. [key] is the canonical root path used for attribution. */
data class StorageVolumeInfo(
    val key: String,
    val displayName: String,
    val type: StorageVolumeType,
    val totalBytes: Long,
    val availableBytes: Long
)
