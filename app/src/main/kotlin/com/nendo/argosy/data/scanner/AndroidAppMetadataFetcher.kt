package com.nendo.argosy.data.scanner

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.recordArtSource
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameScreenshotDao
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppMetadataFetcher @Inject constructor(
    private val playStoreService: PlayStoreService,
    private val gameDao: GameDao,
    private val gameArtDao: GameArtDao,
    private val gameScreenshotDao: GameScreenshotDao,
    private val imageCacheManager: ImageCacheManager
) {
    suspend fun fetch(gameId: Long, packageName: String) {
        val details = runCatching { playStoreService.getAppDetails(packageName).getOrNull() }
            .getOrNull()

        if (details != null) {
            runCatching {
                gameDao.getById(gameId)?.let { game ->
                    gameDao.update(
                        game.copy(
                            description = details.description ?: game.description,
                            developer = details.developer ?: game.developer,
                            genre = details.genre ?: game.genre,
                            rating = details.ratingPercent ?: game.rating
                        )
                    )
                    details.screenshotUrls.firstOrNull()?.let { url ->
                        recordArtSource(gameArtDao, imageCacheManager, gameId, ArtSlot.BACKGROUND, listOf(url), game.title)
                    }
                    if (details.screenshotUrls.isNotEmpty()) {
                        gameScreenshotDao.replaceSources(gameId, details.screenshotUrls)
                        imageCacheManager.queueScreenshotCacheByGameId(gameId)
                    }
                }
            }
        }

        val coverUrl = details?.coverUrl
        if (coverUrl != null) {
            recordArtSource(gameArtDao, imageCacheManager, gameId, ArtSlot.COVER, listOf(coverUrl), "")
        } else {
            imageCacheManager.queueAppIconCache(gameId, packageName)
        }
    }
}
