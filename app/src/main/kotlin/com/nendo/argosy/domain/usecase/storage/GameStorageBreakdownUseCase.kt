package com.nendo.argosy.domain.usecase.storage

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.VariantCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    VariantCategory.MANUAL, VariantCategory.CHEAT, VariantCategory.DEMO,
    VariantCategory.PROTOTYPE, VariantCategory.SCREENSHOT -> GameStorageBucket.OTHER
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

data class GameStorageBreakdown(
    val gameId: Long,
    val title: String,
    val source: GameSource,
    val buckets: List<GameStorageBucketRow>,
    val totalBytes: Long,
    val unsyncedSaves: Int
) {
    val hasSoundtrack: Boolean get() = buckets.any { it.bucket == GameStorageBucket.SOUNDTRACK }
}

private val BUCKET_ORDER = listOf(
    GameStorageBucket.BASE, GameStorageBucket.UPDATES, GameStorageBucket.DLC,
    GameStorageBucket.HACKS, GameStorageBucket.SOUNDTRACK, GameStorageBucket.OTHER
)

class GameStorageBreakdownUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val gameDiscDao: GameDiscDao,
    private val saveCacheDao: SaveCacheDao
) {
    suspend fun loadPlatform(platformId: Long): List<GameStorageBreakdown> = withContext(Dispatchers.IO) {
        gameDao.getDownloadedGamesByPlatform(platformId).mapNotNull { game ->
            buildBreakdown(game.id, game.title, game.source, game.localPath, game.fileSizeBytes)
        }
    }

    private suspend fun buildBreakdown(
        gameId: Long,
        title: String,
        source: GameSource,
        localPath: String?,
        fileSizeBytes: Long?
    ): GameStorageBreakdown? {
        val files = gameFileDao.getFilesForGame(gameId).filter { it.isLocallyPresent() }
        val discs = gameDiscDao.getDiscsForGame(gameId)
            .filter { it.localPath?.let { p -> File(p).exists() } == true }
        val rootDepth = files.minOfOrNull { it.filePath.count { c -> c == '/' } } ?: 0

        val counts = mutableMapOf<GameStorageBucket, Int>()
        val bytes = mutableMapOf<GameStorageBucket, Long>()
        val ids = mutableMapOf<GameStorageBucket, MutableList<Long>>()

        fun add(bucket: GameStorageBucket, size: Long, rommFileId: Long?) {
            counts[bucket] = (counts[bucket] ?: 0) + 1
            bytes[bucket] = (bytes[bucket] ?: 0L) + size
            if (rommFileId != null) ids.getOrPut(bucket) { mutableListOf() }.add(rommFileId)
        }

        files.forEach { f: GameFileEntity ->
            val bucket = bucketForFile(f.category, f.filePath.count { c -> c == '/' } > rootDepth)
            add(bucket, f.fileSize, f.rommFileId)
        }

        val countedPaths = files.mapNotNull { it.localPath }
            .mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }
            .toSet()
        val baseCoveredByFile = localPath != null && files.any { it.localPath == localPath }
        when {
            discs.isNotEmpty() -> addBase(counts, bytes, discs.size, discs.sumOf { it.fileSize })
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
                        if (count > 0) addBase(counts, bytes, count, total)
                    }
                    root.isFile -> addBase(counts, bytes, 1, fileSizeBytes ?: root.length())
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
            title = title,
            source = source,
            buckets = rows,
            totalBytes = rows.sumOf { it.totalBytes },
            unsyncedSaves = saveCacheDao.countNeedingRemoteSyncForGame(gameId)
        )
    }
}
