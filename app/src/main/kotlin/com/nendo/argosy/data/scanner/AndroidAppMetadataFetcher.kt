package com.nendo.argosy.data.scanner

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills in what the system cannot tell us about an installed app: description, developer, genre,
 * rating, screenshots and a cover.
 *
 * Shared so an app added by hand and an app found by a scan end up with the same artwork and
 * detail. Every step is best-effort - the app is already in the library and playable without any
 * of this, so a failed lookup degrades the row rather than failing the operation.
 */
@Singleton
class AndroidAppMetadataFetcher @Inject constructor(
    private val playStoreService: PlayStoreService,
    private val gameDao: GameDao,
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
                            rating = details.ratingPercent ?: game.rating,
                            screenshotPaths = details.screenshotUrls.takeIf { it.isNotEmpty() }
                                ?.joinToString(",") ?: game.screenshotPaths,
                            backgroundPath = details.screenshotUrls.firstOrNull() ?: game.backgroundPath
                        )
                    )
                    details.screenshotUrls.firstOrNull()?.let { url ->
                        imageCacheManager.queueBackgroundCacheByGameId(url, gameId, game.title)
                    }
                    if (details.screenshotUrls.isNotEmpty()) {
                        imageCacheManager.queueScreenshotCacheByGameId(gameId, details.screenshotUrls)
                    }
                }
            }
        }

        if (details?.coverUrl != null) {
            imageCacheManager.queueCoverCacheByGameId(details.coverUrl, gameId)
        } else {
            imageCacheManager.queueAppIconCache(gameId, packageName)
        }
    }
}
