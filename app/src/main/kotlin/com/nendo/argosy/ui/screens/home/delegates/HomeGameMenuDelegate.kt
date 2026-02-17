package com.nendo.argosy.ui.screens.home.delegates

import android.content.Intent
import android.net.Uri
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.RefreshAndroidResult
import com.nendo.argosy.ui.screens.home.HomeGameUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MENU_INDEX_MAX_DOWNLOADED = 5
private const val MENU_INDEX_MAX_REMOTE = 4

sealed class GameMenuAction {
    data class Play(val gameId: Long, val needsInstall: Boolean, val isDownloaded: Boolean) : GameMenuAction()
    data class ToggleFavorite(val gameId: Long) : GameMenuAction()
    data class ViewDetails(val gameId: Long) : GameMenuAction()
    data class AddToCollection(val gameId: Long) : GameMenuAction()
    data class Refresh(val gameId: Long, val isAndroidApp: Boolean) : GameMenuAction()
    data class Delete(val gameId: Long) : GameMenuAction()
    data class RemoveFromHome(val gameId: Long) : GameMenuAction()
    data class Hide(val gameId: Long) : GameMenuAction()
}

data class GameMenuState(
    val showGameMenu: Boolean = false,
    val gameMenuFocusIndex: Int = 0
)

class HomeGameMenuDelegate @Inject constructor(
    private val gameActions: GameActionsDelegate,
    private val gameRepository: GameRepository,
    private val soundManager: SoundFeedbackManager,
    private val notificationManager: NotificationManager
) {
    private val _state = MutableStateFlow(GameMenuState())
    val state: StateFlow<GameMenuState> = _state.asStateFlow()

    private val _uninstallIntent = MutableSharedFlow<Intent>()
    val uninstallIntent: SharedFlow<Intent> = _uninstallIntent.asSharedFlow()

    fun toggleGameMenu() {
        val wasShowing = _state.value.showGameMenu
        _state.update {
            it.copy(showGameMenu = !it.showGameMenu, gameMenuFocusIndex = 0)
        }
        if (!wasShowing) {
            soundManager.play(SoundType.OPEN_MODAL)
        } else {
            soundManager.play(SoundType.CLOSE_MODAL)
        }
    }

    fun resetMenu() {
        _state.update { it.copy(showGameMenu = false) }
    }

    fun moveGameMenuFocus(delta: Int, focusedGame: HomeGameUi?) {
        _state.update {
            val isDownloaded = focusedGame?.isDownloaded == true
            val needsInstall = focusedGame?.needsInstall == true
            val isRommGame = focusedGame?.isRommGame == true
            val isAndroidApp = focusedGame?.isAndroidApp == true
            var maxIndex = if (isDownloaded || needsInstall) MENU_INDEX_MAX_DOWNLOADED else MENU_INDEX_MAX_REMOTE
            if (isRommGame || isAndroidApp) maxIndex++
            if (isAndroidApp) maxIndex++
            val newIndex = (it.gameMenuFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(gameMenuFocusIndex = newIndex)
        }
    }

    fun resolveMenuAction(focusIndex: Int, game: HomeGameUi): GameMenuAction {
        var currentIdx = 0
        val playIdx = currentIdx++
        val favoriteIdx = currentIdx++
        val detailsIdx = currentIdx++
        val addToCollectionIdx = currentIdx++
        val refreshIdx = if (game.isRommGame || game.isAndroidApp) currentIdx++ else -1
        val deleteIdx = if (game.isDownloaded || game.needsInstall) currentIdx++ else -1
        val removeFromHomeIdx = if (game.isAndroidApp) currentIdx++ else -1
        val hideIdx = currentIdx

        return when (focusIndex) {
            playIdx -> GameMenuAction.Play(game.id, game.needsInstall, game.isDownloaded)
            favoriteIdx -> GameMenuAction.ToggleFavorite(game.id)
            detailsIdx -> GameMenuAction.ViewDetails(game.id)
            addToCollectionIdx -> GameMenuAction.AddToCollection(game.id)
            refreshIdx -> GameMenuAction.Refresh(game.id, game.isAndroidApp)
            deleteIdx -> GameMenuAction.Delete(game.id)
            removeFromHomeIdx -> GameMenuAction.RemoveFromHome(game.id)
            hideIdx -> GameMenuAction.Hide(game.id)
            else -> GameMenuAction.Hide(game.id)
        }
    }

    fun toggleFavorite(scope: CoroutineScope, gameId: Long, onComplete: suspend () -> Unit) {
        scope.launch {
            gameActions.toggleFavorite(gameId)
            onComplete()
        }
    }

    fun hideGame(scope: CoroutineScope, gameId: Long, onComplete: suspend () -> Unit) {
        scope.launch {
            gameActions.hideGame(gameId)
            onComplete()
        }
    }

    fun removeFromHome(scope: CoroutineScope, gameId: Long, onComplete: suspend () -> Unit) {
        scope.launch {
            val game = gameRepository.getById(gameId)
            if (game != null && game.source == GameSource.ANDROID_APP) {
                gameRepository.delete(game)
                onComplete()
                soundManager.play(SoundType.UNFAVORITE)
            }
        }
    }

    fun refreshGameData(scope: CoroutineScope, gameId: Long, onComplete: suspend () -> Unit) {
        scope.launch {
            when (val result = gameActions.refreshGameData(gameId)) {
                is RomMResult.Success -> {
                    notificationManager.showSuccess("Game data refreshed")
                    onComplete()
                }
                is RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            toggleGameMenu()
        }
    }

    fun refreshAndroidGameData(scope: CoroutineScope, gameId: Long, onComplete: suspend () -> Unit) {
        scope.launch {
            when (val result = gameActions.refreshAndroidGameData(gameId)) {
                is RefreshAndroidResult.Success -> {
                    notificationManager.showSuccess("Game data refreshed")
                    onComplete()
                }
                is RefreshAndroidResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            toggleGameMenu()
        }
    }
}
