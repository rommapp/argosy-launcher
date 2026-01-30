package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.common.GameUpdateBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed class RatingUpdateResult {
    data object Success : RatingUpdateResult()
    data class Error(val message: String) : RatingUpdateResult()
}

@Singleton
class GameRatingDelegate @Inject constructor(
    private val romMRepository: RomMRepository,
    private val notificationManager: NotificationManager,
    private val gameUpdateBus: GameUpdateBus
) {
    fun updateRating(
        scope: CoroutineScope,
        gameId: Long,
        value: Int,
        onComplete: (RatingUpdateResult) -> Unit = {}
    ) {
        scope.launch {
            val result = romMRepository.updateUserRating(gameId, value)
            when (result) {
                is RomMResult.Success -> {
                    notificationManager.showSuccess("Rating saved")
                    onComplete(RatingUpdateResult.Success)
                }
                is RomMResult.Error -> {
                    notificationManager.showError(result.message)
                    onComplete(RatingUpdateResult.Error(result.message))
                }
            }
        }
    }

    fun updateDifficulty(
        scope: CoroutineScope,
        gameId: Long,
        value: Int,
        onComplete: (RatingUpdateResult) -> Unit = {}
    ) {
        scope.launch {
            val result = romMRepository.updateUserDifficulty(gameId, value)
            when (result) {
                is RomMResult.Success -> {
                    notificationManager.showSuccess("Difficulty saved")
                    onComplete(RatingUpdateResult.Success)
                }
                is RomMResult.Error -> {
                    notificationManager.showError(result.message)
                    onComplete(RatingUpdateResult.Error(result.message))
                }
            }
        }
    }

    fun updateStatus(
        scope: CoroutineScope,
        gameId: Long,
        value: String,
        onComplete: (RatingUpdateResult) -> Unit = {}
    ) {
        scope.launch {
            val result = romMRepository.updateUserStatus(gameId, value)
            when (result) {
                is RomMResult.Success -> {
                    notificationManager.showSuccess("Status saved")
                    gameUpdateBus.emit(
                        GameUpdateBus.GameUpdate(
                            gameId = gameId,
                            status = value
                        )
                    )
                    onComplete(RatingUpdateResult.Success)
                }
                is RomMResult.Error -> {
                    notificationManager.showError(result.message)
                    onComplete(RatingUpdateResult.Error(result.message))
                }
            }
        }
    }
}
