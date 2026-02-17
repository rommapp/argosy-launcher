package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.usecase.download.DownloadGameUseCase
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.domain.usecase.game.DeleteGameUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import javax.inject.Inject
import javax.inject.Singleton

sealed class RefreshAndroidResult {
    data object Success : RefreshAndroidResult()
    data class Error(val message: String) : RefreshAndroidResult()
}

@Singleton
class GameActionsDelegate @Inject constructor(
    private val gameRepository: GameRepository,
    private val deleteGameUseCase: DeleteGameUseCase,
    private val downloadGameUseCase: DownloadGameUseCase,
    private val soundManager: SoundFeedbackManager,
    private val romMRepository: RomMRepository,
    private val playStoreService: PlayStoreService,
    private val imageCacheManager: ImageCacheManager
) {
    suspend fun toggleFavorite(gameId: Long): Boolean? {
        val game = gameRepository.getById(gameId) ?: return null
        val newFavoriteState = !game.isFavorite

        val rommId = game.rommId
        if (rommId != null) {
            romMRepository.toggleFavoriteWithSync(gameId, rommId, newFavoriteState)
        } else {
            gameRepository.updateFavorite(gameId, newFavoriteState)
        }

        soundManager.play(if (newFavoriteState) SoundType.FAVORITE else SoundType.UNFAVORITE)
        return newFavoriteState
    }

    suspend fun hideGame(gameId: Long) {
        gameRepository.updateHidden(gameId, true)
    }

    suspend fun unhideGame(gameId: Long) {
        gameRepository.updateHidden(gameId, false)
    }

    suspend fun deleteLocalFile(gameId: Long) {
        deleteGameUseCase(gameId)
    }

    suspend fun queueDownload(gameId: Long): DownloadResult {
        return downloadGameUseCase(gameId)
    }

    suspend fun repairMissingDiscs(gameId: Long): DownloadResult {
        return downloadGameUseCase.repairMissingDiscs(gameId)
    }

    suspend fun retryExtraction(gameId: Long): DownloadResult {
        return downloadGameUseCase.retryExtraction(gameId)
    }

    suspend fun redownload(gameId: Long): DownloadResult {
        return downloadGameUseCase.redownload(gameId)
    }

    suspend fun refreshGameData(gameId: Long): RomMResult<Unit> {
        return romMRepository.refreshGameData(gameId)
    }

    suspend fun refreshAndroidGameData(gameId: Long): RefreshAndroidResult {
        val game = gameRepository.getById(gameId)
            ?: return RefreshAndroidResult.Error("Game not found")
        val packageName = game.packageName
            ?: return RefreshAndroidResult.Error("Package name not available")

        return try {
            val details = playStoreService.getAppDetails(packageName).getOrNull()
            if (details != null) {
                val updated = game.copy(
                    description = details.description ?: game.description,
                    developer = details.developer ?: game.developer,
                    genre = details.genre ?: game.genre,
                    rating = details.ratingPercent ?: game.rating,
                    screenshotPaths = details.screenshotUrls.takeIf { it.isNotEmpty() }
                        ?.joinToString(",") ?: game.screenshotPaths,
                    backgroundPath = details.screenshotUrls.firstOrNull() ?: game.backgroundPath
                )
                gameRepository.update(updated)

                details.coverUrl?.let { url ->
                    imageCacheManager.queueCoverCacheByGameId(url, gameId)
                }
                if (details.screenshotUrls.isNotEmpty()) {
                    imageCacheManager.queueScreenshotCacheByGameId(gameId, details.screenshotUrls)
                }

                RefreshAndroidResult.Success
            } else {
                RefreshAndroidResult.Error("App not found on Play Store")
            }
        } catch (e: Exception) {
            RefreshAndroidResult.Error("Failed to refresh: ${e.message}")
        }
    }
}
