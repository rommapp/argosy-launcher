package com.nendo.argosy.ui.screens.home.delegates

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nendo.argosy.R
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.RefreshAndroidResult
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.running.RunningMoveTarget
import dagger.hilt.android.qualifiers.ApplicationContext
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
    data class Stream(val gameId: Long) : GameMenuAction()
    data class Download(val gameId: Long) : GameMenuAction()
    data class PlayOnDisplay(val gameId: Long, val displayId: Int) : GameMenuAction()
    data class ToggleFavorite(val gameId: Long) : GameMenuAction()
    data class ViewDetails(val gameId: Long) : GameMenuAction()
    data class AddToCollection(val gameId: Long) : GameMenuAction()
    data class ActiveVariant(val gameId: Long) : GameMenuAction()
    data class Refresh(val gameId: Long, val isAndroidApp: Boolean) : GameMenuAction()
    data class ResyncPlatform(val platformId: Long, val platformName: String) : GameMenuAction()
    data class Delete(val gameId: Long) : GameMenuAction()
    data class RemoveFromHome(val gameId: Long) : GameMenuAction()
    data class RemoveFromLibrary(val gameId: Long) : GameMenuAction()
    data class Hide(val gameId: Long) : GameMenuAction()
    data class MoveToDisplay(val target: RunningMoveTarget) : GameMenuAction()
    data object CloseGame : GameMenuAction()
}

/**
 * The menu rows that depend on the screens and on the game running: one Play on Screen row per
 * entry of [playDisplays] when there are two or more, one Move to Display row per [moveTargets]
 * entry, and Close last when [closesGame].
 */
data class GameMenuRunningRows(
    val playDisplays: List<Int> = emptyList(),
    val moveTargets: List<RunningMoveTarget> = emptyList(),
    val closesGame: Boolean = false
)

data class GameMenuState(
    val showGameMenu: Boolean = false,
    val gameMenuFocusIndex: Int = 0
)

class HomeGameMenuDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
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

    fun moveGameMenuFocus(
        delta: Int,
        focusedGame: HomeGameUi?,
        isPlatformRow: Boolean,
        runningRows: GameMenuRunningRows = GameMenuRunningRows(),
        hasSiblingGroup: Boolean = false,
        streamable: Boolean = false
    ) {
        _state.update {
            val isDownloaded = focusedGame?.isDownloaded == true
            val needsInstall = focusedGame?.needsInstall == true
            val isRommGame = focusedGame?.isRommGame == true
            val isAndroidApp = focusedGame?.isAndroidApp == true
            val isSyncable = isPlatformRow && focusedGame != null && focusedGame.platformId > 0
            var maxIndex = if (isDownloaded || needsInstall) MENU_INDEX_MAX_DOWNLOADED else MENU_INDEX_MAX_REMOTE
            if (runningRows.playDisplays.size > 1 && isDownloaded) maxIndex += runningRows.playDisplays.size
            maxIndex += runningRows.moveTargets.size
            if (runningRows.closesGame) maxIndex++
            if (hasSiblingGroup) maxIndex++
            if (streamable) maxIndex++
            if (isRommGame || isAndroidApp) maxIndex++
            if (isAndroidApp) maxIndex++
            if (isSyncable) maxIndex++
            if (focusedGame?.canRemoveFromLibrary == true) maxIndex++
            val newIndex = (it.gameMenuFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(gameMenuFocusIndex = newIndex)
        }
    }

    fun resolveMenuAction(
        focusIndex: Int,
        game: HomeGameUi,
        isPlatformRow: Boolean,
        runningRows: GameMenuRunningRows = GameMenuRunningRows(),
        hasSiblingGroup: Boolean = false,
        streamable: Boolean = false,
        streamLeads: Boolean = false
    ): GameMenuAction {
        val playDisplays = runningRows.playDisplays
        var currentIdx = 0
        val leads = streamable && streamLeads
        val streamFirstIdx = if (leads) currentIdx++ else -1
        val playIdx = currentIdx++
        val streamIdx = if (streamable && !leads) currentIdx++ else -1
        if (focusIndex == streamFirstIdx) return GameMenuAction.Stream(game.id)
        if (leads && focusIndex == playIdx) return GameMenuAction.Download(game.id)
        val playOnDisplayRange = if (playDisplays.size > 1 && game.isDownloaded) {
            val start = currentIdx
            currentIdx += playDisplays.size
            start until currentIdx
        } else {
            IntRange.EMPTY
        }
        if (focusIndex in playOnDisplayRange) {
            return GameMenuAction.PlayOnDisplay(
                game.id,
                playDisplays[focusIndex - playOnDisplayRange.first]
            )
        }
        val moveStart = currentIdx
        currentIdx += runningRows.moveTargets.size
        if (focusIndex in moveStart until currentIdx) {
            return GameMenuAction.MoveToDisplay(runningRows.moveTargets[focusIndex - moveStart])
        }
        val favoriteIdx = currentIdx++
        val detailsIdx = currentIdx++
        val addToCollectionIdx = currentIdx++
        val activeVariantIdx = if (hasSiblingGroup) currentIdx++ else -1
        val refreshIdx = if (game.isRommGame || game.isAndroidApp) currentIdx++ else -1
        val resyncIdx = if (isPlatformRow && game.platformId > 0) currentIdx++ else -1
        val deleteIdx = if (game.isDownloaded || game.needsInstall) currentIdx++ else -1
        val removeFromHomeIdx = if (game.isAndroidApp) currentIdx++ else -1
        val removeFromLibraryIdx = if (game.canRemoveFromLibrary) currentIdx++ else -1
        val hideIdx = currentIdx++
        val closeIdx = if (runningRows.closesGame) currentIdx else -1

        return when (focusIndex) {
            closeIdx -> GameMenuAction.CloseGame
            playIdx -> GameMenuAction.Play(game.id, game.needsInstall, game.isDownloaded)
            streamIdx -> GameMenuAction.Stream(game.id)
            favoriteIdx -> GameMenuAction.ToggleFavorite(game.id)
            detailsIdx -> GameMenuAction.ViewDetails(game.id)
            addToCollectionIdx -> GameMenuAction.AddToCollection(game.id)
            activeVariantIdx -> GameMenuAction.ActiveVariant(game.id)
            refreshIdx -> GameMenuAction.Refresh(game.id, game.isAndroidApp)
            resyncIdx -> GameMenuAction.ResyncPlatform(game.platformId, game.platformDisplayName)
            deleteIdx -> GameMenuAction.Delete(game.id)
            removeFromHomeIdx -> GameMenuAction.RemoveFromHome(game.id)
            removeFromLibraryIdx -> GameMenuAction.RemoveFromLibrary(game.id)
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

    fun removeFromLibrary(scope: CoroutineScope, gameId: Long, onComplete: suspend () -> Unit) {
        scope.launch {
            if (gameActions.removeFromLibrary(gameId)) {
                notificationManager.showSuccess(
                    NotificationText.Res(R.string.home_notice_removed_from_library)
                )
                onComplete()
            } else {
                notificationManager.showError(
                    NotificationText.Res(R.string.home_notice_remove_failed)
                )
            }
        }
    }

    fun refreshGameData(scope: CoroutineScope, gameId: Long, onComplete: suspend () -> Unit) {
        scope.launch {
            when (val result = gameActions.refreshGameData(gameId)) {
                is RomMResult.Success -> {
                    notificationManager.showSuccess(
                        NotificationText.Res(R.string.home_notice_game_data_refreshed)
                    )
                    onComplete()
                }
                is RomMResult.Error -> {
                    notificationManager.showError(NotificationText.Raw(result.message))
                }
            }
            toggleGameMenu()
        }
    }

    fun refreshAndroidGameData(scope: CoroutineScope, gameId: Long, onComplete: suspend () -> Unit) {
        scope.launch {
            when (val result = gameActions.refreshAndroidGameData(gameId)) {
                is RefreshAndroidResult.Success -> {
                    notificationManager.showSuccess(
                        NotificationText.Res(R.string.home_notice_android_data_refreshed)
                    )
                    onComplete()
                }
                is RefreshAndroidResult.Error -> {
                    notificationManager.showError(NotificationText.Raw(result.message))
                }
            }
            toggleGameMenu()
        }
    }
}
