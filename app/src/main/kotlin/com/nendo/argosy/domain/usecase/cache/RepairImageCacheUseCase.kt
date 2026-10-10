package com.nendo.argosy.domain.usecase.cache

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.recordArtSource
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.clearOverride
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.storage.StorageVolumeHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class RepairImageCacheUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameArtDao: GameArtDao,
    private val romMRepository: RomMRepository,
    private val imageCacheManager: ImageCacheManager,
    private val volumeHealth: StorageVolumeHealth
) {
    suspend fun repairCover(gameId: Long, localPath: String?): String? =
        repairArt(gameId, ArtSlot.COVER, localPath) { game, rommId ->
            when (val result = romMRepository.getRom(rommId)) {
                is RomMResult.Success -> romMRepository.buildCoverUrls(result.data)
                is RomMResult.Error -> null
            }.also { urls -> if (urls != null) record(game, ArtSlot.COVER, urls) }
        }

    suspend fun repairBackground(gameId: Long, localPath: String?): String? =
        repairArt(gameId, ArtSlot.BACKGROUND, localPath) { game, rommId ->
            when (val result = romMRepository.getRom(rommId)) {
                is RomMResult.Success -> (
                    result.data.backgroundUrls +
                        result.data.screenshotPaths?.mapNotNull { romMRepository.buildMediaUrlPublic(it) }.orEmpty()
                    ).distinct()
                is RomMResult.Error -> null
            }.also { urls -> if (urls != null) record(game, ArtSlot.BACKGROUND, urls) }
        }

    private suspend fun repairArt(
        gameId: Long,
        slot: ArtSlot,
        localPath: String?,
        refetch: suspend (GameEntity, Long) -> List<String>?
    ): String? {
        if (localPath == null) return null
        if (!localPath.startsWith("/")) return localPath
        if (File(localPath).exists()) return localPath

        val game = gameDao.getById(gameId) ?: return null
        val row = gameArtDao.get(gameId, slot.name)
        if (row != null && row.overridePath == localPath) {
            if (isGenuinelyAbsent(localPath)) gameArtDao.clearOverride(gameId, slot)
            return row.cachedPath ?: row.sourceUrl
        }
        if (row != null && row.cachedPath == localPath && isGenuinelyAbsent(localPath)) {
            imageCacheManager.forgetCachedArt(gameId, slot)
        }
        val rommId = game.rommId
        if (rommId == null || !romMRepository.isConnected()) {
            val source = row?.sourceUrl ?: return null
            imageCacheManager.queueArtIfStale(gameId, slot, listOf(source), null, game.steamAppId, game.title)
            return source
        }
        return refetch(game, rommId)?.firstOrNull()
    }

    private suspend fun record(game: GameEntity, slot: ArtSlot, urls: List<String>) {
        if (urls.isEmpty()) return
        recordArtSource(gameArtDao, imageCacheManager, game.id, slot, urls, game.title, rommId = game.rommId)
    }

    private suspend fun isGenuinelyAbsent(path: String): Boolean =
        withContext(Dispatchers.IO) { volumeHealth.newProbe().isGenuinelyAbsent(path) }

    fun isLocalPathValid(path: String?): Boolean {
        if (path == null) return false
        if (!path.startsWith("/")) return true
        val file = File(path)
        return file.exists() && file.length() > 512
    }
}
