package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.local.dao.GameDao
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
    private val soundManager: SoundFeedbackManager
) {
    suspend fun toggleFavorite(gameId: Long): Boolean? {
        val game = gameDao.getById(gameId) ?: return null
        val newFavoriteState = !game.isFavorite
        gameDao.updateFavorite(gameId, newFavoriteState)
        soundManager.play(if (newFavoriteState) SoundType.FAVORITE else SoundType.UNFAVORITE)
        return newFavoriteState
    }

    suspend fun hideGame(gameId: Long) {
        gameDao.updateHidden(gameId, true)
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
}
