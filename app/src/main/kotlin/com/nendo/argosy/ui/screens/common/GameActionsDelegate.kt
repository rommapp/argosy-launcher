package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.usecase.download.DownloadGameUseCase
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.domain.usecase.game.DeleteGameUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameActionsDelegate @Inject constructor(
    private val gameDao: GameDao,
    private val deleteGameUseCase: DeleteGameUseCase,
    private val downloadGameUseCase: DownloadGameUseCase,
    private val soundManager: SoundFeedbackManager,
    private val romMRepository: RomMRepository
) {
    suspend fun toggleFavorite(gameId: Long): Boolean? {
        val game = gameDao.getById(gameId) ?: return null
        val newFavoriteState = !game.isFavorite

        val rommId = game.rommId
        if (rommId != null) {
            romMRepository.toggleFavoriteWithSync(gameId, rommId, newFavoriteState)
        } else {
            gameDao.updateFavorite(gameId, newFavoriteState)
        }

        soundManager.play(if (newFavoriteState) SoundType.FAVORITE else SoundType.UNFAVORITE)
        return newFavoriteState
    }

    suspend fun hideGame(gameId: Long) {
        gameDao.updateHidden(gameId, true)
    }

    suspend fun unhideGame(gameId: Long) {
        gameDao.updateHidden(gameId, false)
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
}
