package com.nendo.argosy.domain.usecase.storage

import com.nendo.argosy.core.storage.StorageVolumeType
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.storage.StoragePathUtils
import com.nendo.argosy.data.storage.StorageVolumeInfo
import com.nendo.argosy.data.storage.StorageVolumeLocator
import com.nendo.argosy.data.storage.UNKNOWN_LOCATION_KEY
import com.nendo.argosy.data.storage.gameFileVolumeKey
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.VariantCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject

private const val TMP_SUFFIX = ".tmp"

enum class GameStorageBucket { BASE, UPDATES, DLC, HACKS, SOUNDTRACK, OTHER }

/**
 * Single source of truth for grouping a game's on-disk files into display buckets.
 * Mirrors the base-vs-addon split in FilePickerFlowUseCase.buildManageRows: category GAME
 * (and root-level UNKNOWN) is the base; a deeper-than-root UNKNOWN file is folder add-on
 * content (the NSW "extcontent" case) and counts as DLC.
 */
fun bucketForCategory(category: VariantCategory): GameStorageBucket = when (category) {
    VariantCategory.GAME, VariantCategory.UNKNOWN -> GameStorageBucket.BASE
    VariantCategory.UPDATE -> GameStorageBucket.UPDATES
    VariantCategory.DLC -> GameStorageBucket.DLC
    VariantCategory.HACK, VariantCategory.MOD,
    VariantCategory.PATCH, VariantCategory.TRANSLATION -> GameStorageBucket.HACKS
    VariantCategory.SOUNDTRACK -> GameStorageBucket.SOUNDTRACK
    VariantCategory.MANUAL, VariantCategory.WALKTHROUGH, VariantCategory.CHEAT,
    VariantCategory.DEMO, VariantCategory.PROTOTYPE,
    VariantCategory.SCREENSHOT -> GameStorageBucket.OTHER
}

fun bucketForFile(category: String?, isDeeperThanRoot: Boolean): GameStorageBucket {
    val cat = VariantCategory.fromKey(category)
    if (cat == VariantCategory.UNKNOWN && isDeeperThanRoot) return GameStorageBucket.DLC
    return bucketForCategory(cat)
}

private fun addBase(
    counts: MutableMap<GameStorageBucket, Int>,
    bytes: MutableMap<GameStorageBucket, Long>,
    count: Int,
    total: Long
) {
    if (count <= 0) return
    counts[GameStorageBucket.BASE] = (counts[GameStorageBucket.BASE] ?: 0) + count
    bytes[GameStorageBucket.BASE] = (bytes[GameStorageBucket.BASE] ?: 0L) + total
}

data class GameStorageBucketRow(
    val bucket: GameStorageBucket,
    val fileCount: Int,
    val totalBytes: Long,
    val rommFileIds: List<Long>
) {
    val selectable: Boolean get() = bucket != GameStorageBucket.BASE && rommFileIds.isNotEmpty()
}

enum class GameStorageLocation { INTERNAL, REMOVABLE, MIXED, UNKNOWN }

data class GameStorageFile(
    val path: String,
    val bytes: Long,
    val location: GameStorageLocation
)

data class GameStorageBreakdown(
    val gameId: Long,
    val title: String,
    val source: GameSource,
    val buckets: List<GameStorageBucketRow>,
    val totalBytes: Long,
    val unsyncedSaves: Int,
    val files: List<GameStorageFile> = emptyList(),
    val platformName: String? = null,
    val lastPlayed: Instant? = null,
    val playTimeMinutes: Int = 0
) {
    val hasSoundtrack: Boolean get() = buckets.any { it.bucket == GameStorageBucket.SOUNDTRACK }
    val isLocalOnly: Boolean get() = source == GameSource.LOCAL_ONLY

    /**
     * Where the game's files are: UNKNOWN when any file sits on no detected volume, MIXED when
     * they span internal and removable storage.
     */
    val location: GameStorageLocation get() {
        val locations = files.map { it.location }.toSet()
        return when {
            GameStorageLocation.UNKNOWN in locations -> GameStorageLocation.UNKNOWN
            locations.size > 1 -> GameStorageLocation.MIXED
            else -> locations.firstOrNull() ?: GameStorageLocation.UNKNOWN
        }
    }
}

data class GameDeleteSummary(
    val title: String,
    val hasSoundtrack: Boolean,
    val unsyncedSaves: Int
)

private val BUCKET_ORDER = listOf(
    GameStorageBucket.BASE, GameStorageBucket.UPDATES, GameStorageBucket.DLC,
    GameStorageBucket.HACKS, GameStorageBucket.SOUNDTRACK, GameStorageBucket.OTHER
)

class GameStorageBreakdownUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val gameDiscDao: GameDiscDao,
    private val saveCacheDao: SaveCacheDao,
    private val platformDao: PlatformDao,
    private val volumeLocator: StorageVolumeLocator,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository
) {
    suspend fun loadPlatform(platformId: Long): List<GameStorageBreakdown> = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val volumes = volumeLocator.detectVolumes()
        gameDao.getDownloadedGamesByPlatform(platformId, ownerUserId).mapNotNull { game ->
            buildBreakdown(game, volumes)
        }
    }

    /**
     * Every downloaded game RomM does not have, across platforms, in title order.
     */
    suspend fun loadLocalOnly(): List<GameStorageBreakdown> = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val volumes = volumeLocator.detectVolumes()
        val platformNames = platformDao.getAllPlatformsOrdered().associate { it.id to it.name }
        gameDao.getDownloadedGamesBySource(GameSource.LOCAL_ONLY, ownerUserId).mapNotNull { game ->
            buildBreakdown(game, volumes)?.copy(platformName = platformNames[game.platformId])
        }
    }

    suspend fun deleteSummary(gameId: Long): GameDeleteSummary? = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext null
        val breakdown = buildBreakdown(game, volumeLocator.detectVolumes())
        GameDeleteSummary(
            title = game.title,
            hasSoundtrack = breakdown?.hasSoundtrack == true,
            unsyncedSaves = saveCacheDao.countNeedingRemoteSyncForGame(gameId)
        )
    }

    private suspend fun buildBreakdown(game: GameEntity, volumes: List<StorageVolumeInfo>): GameStorageBreakdown? {
        val gameId = game.id
        val localPath = game.localPath
        val files = gameFileDao.getFilesForGame(gameId).filter { it.isLocallyPresent() }
        val discs = gameDiscDao.getDiscsForGame(gameId)
            .filter { it.localPath?.let { p -> File(p).exists() } == true }
        val rootDepth = files.minOfOrNull { it.filePath.count { c -> c == '/' } } ?: 0

        val counts = mutableMapOf<GameStorageBucket, Int>()
        val bytes = mutableMapOf<GameStorageBucket, Long>()
        val ids = mutableMapOf<GameStorageBucket, MutableList<Long>>()
        val entries = mutableListOf<GameStorageFile>()
        val internalKey = volumes.firstOrNull { it.type == StorageVolumeType.INTERNAL }?.key

        fun record(path: String, size: Long) {
            val canonical = StoragePathUtils.canonicalize(path)
            entries += GameStorageFile(canonical, size, locationOf(canonical, volumes, internalKey))
        }

        fun add(bucket: GameStorageBucket, size: Long, rommFileId: Long?) {
            counts[bucket] = (counts[bucket] ?: 0) + 1
            bytes[bucket] = (bytes[bucket] ?: 0L) + size
            if (rommFileId != null) ids.getOrPut(bucket) { mutableListOf() }.add(rommFileId)
        }

        files.forEach { f: GameFileEntity ->
            val bucket = bucketForFile(f.category, f.filePath.count { c -> c == '/' } > rootDepth)
            val onDiskBytes = f.localPath
                ?.takeIf { com.nendo.argosy.data.emulator.TexturePackInstaller.isManifestPath(it) }
                ?.let { com.nendo.argosy.data.emulator.TexturePackInstaller.measureManifestBytes(File(it)).takeIf { b -> b > 0L } }
                ?: f.fileSize
            add(bucket, onDiskBytes, f.rommFileId)
            f.localPath?.let { record(it, onDiskBytes) }
        }

        val countedPaths = files.mapNotNull { it.localPath }
            .mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }
            .toSet()
        val baseCoveredByFile = localPath != null && files.any { it.localPath == localPath }
        when {
            discs.isNotEmpty() -> {
                addBase(counts, bytes, discs.size, discs.sumOf { it.fileSize })
                discs.forEach { disc -> disc.localPath?.let { record(it, disc.fileSize) } }
            }
            localPath != null && !baseCoveredByFile -> {
                val root = File(localPath)
                when {
                    root.isDirectory -> {
                        var count = 0
                        var total = 0L
                        root.walkTopDown().forEach { entry ->
                            if (!entry.isFile || entry.name.endsWith(TMP_SUFFIX)) return@forEach
                            val canonical = runCatching { entry.canonicalPath }.getOrNull()
                            if (canonical != null && canonical !in countedPaths) {
                                count += 1
                                total += entry.length()
                            }
                        }
                        if (count > 0) {
                            addBase(counts, bytes, count, total)
                            record(localPath, total)
                        }
                    }
                    root.isFile -> {
                        val size = game.fileSizeBytes ?: root.length()
                        addBase(counts, bytes, 1, size)
                        record(localPath, size)
                    }
                }
            }
        }

        val rows = BUCKET_ORDER.mapNotNull { bucket ->
            val count = counts[bucket] ?: return@mapNotNull null
            GameStorageBucketRow(
                bucket = bucket,
                fileCount = count,
                totalBytes = bytes[bucket] ?: 0L,
                rommFileIds = ids[bucket]?.toList() ?: emptyList()
            )
        }
        if (rows.isEmpty()) return null

        return GameStorageBreakdown(
            gameId = gameId,
            title = game.title,
            source = game.source,
            buckets = rows,
            totalBytes = rows.sumOf { it.totalBytes },
            unsyncedSaves = saveCacheDao.countNeedingRemoteSyncForGame(gameId),
            files = entries,
            lastPlayed = game.lastPlayed,
            playTimeMinutes = game.playTimeMinutes
        )
    }

    private fun locationOf(
        canonicalPath: String,
        volumes: List<StorageVolumeInfo>,
        internalKey: String?
    ): GameStorageLocation {
        val key = gameFileVolumeKey(canonicalPath, volumes, internalKey)
        if (key == null || key == UNKNOWN_LOCATION_KEY) return GameStorageLocation.UNKNOWN
        val type = volumes.firstOrNull { it.key == key }?.type ?: StorageVolumeType.INTERNAL
        return if (type == StorageVolumeType.INTERNAL) GameStorageLocation.INTERNAL else GameStorageLocation.REMOVABLE
    }
}
