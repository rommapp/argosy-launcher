package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.gamedetail.LaunchEvent
import com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction
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
import javax.inject.Singleton

data class PlayOptionsState(
    val showPlayOptions: Boolean = false,
    val playOptionsFocusIndex: Int = 0,
    val hasCasualSaves: Boolean = false,
    val hasHardcoreSave: Boolean = false,
    val hasRASupport: Boolean = false,
    val isRALoggedIn: Boolean = false,
    val isOnline: Boolean = false
)

@Singleton
class PlayOptionsDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val saveCacheManager: SaveCacheManager,
    private val raRepository: RetroAchievementsRepository,
    private val launchGameUseCase: LaunchGameUseCase,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(PlayOptionsState())
    val state: StateFlow<PlayOptionsState> = _state.asStateFlow()

    private val _launchEvents = MutableSharedFlow<LaunchEvent>()
    val launchEvents: SharedFlow<LaunchEvent> = _launchEvents.asSharedFlow()

    fun reset() {
        _state.value = PlayOptionsState()
    }

    fun showPlayOptions(scope: CoroutineScope, gameId: Long, hasAchievements: Boolean) {
        scope.launch {
            val hasCasualSaves = saveCacheManager.getCachesForGameOnce(gameId)
                .any { !it.isHardcore }
            val hasHardcoreSave = saveCacheManager.hasHardcoreSave(gameId)
            val isRALoggedIn = raRepository.isLoggedIn()
            val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(context)

            _state.update {
                it.copy(
                    showPlayOptions = true,
                    playOptionsFocusIndex = 0,
                    hasCasualSaves = hasCasualSaves,
                    hasHardcoreSave = hasHardcoreSave,
                    hasRASupport = hasAchievements,
                    isRALoggedIn = isRALoggedIn,
                    isOnline = isOnline
                )
            }
            soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    fun dismissPlayOptions() {
        _state.update { it.copy(showPlayOptions = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun movePlayOptionsFocus(delta: Int) {
        _state.update {
            val state = it
            var optionCount = 1

            if (state.hasCasualSaves) optionCount++
            if (state.hasHardcoreSave) optionCount++
            if (state.hasRASupport && state.isRALoggedIn) optionCount++

            val maxIndex = (optionCount - 1).coerceAtLeast(0)
            val newIndex = (it.playOptionsFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(playOptionsFocusIndex = newIndex)
        }
    }

    fun confirmPlayOptionSelection(): PlayOptionAction? {
        val state = _state.value
        val focusIndex = state.playOptionsFocusIndex

        var currentIdx = 0
        val resumeIdx = if (state.hasCasualSaves) currentIdx++ else -1
        val resumeHardcoreIdx = if (state.hasHardcoreSave) currentIdx++ else -1
        val newCasualIdx = currentIdx++
        val newHardcoreIdx = if (state.hasRASupport && state.isRALoggedIn) currentIdx else -1

        return when (focusIndex) {
            resumeIdx -> PlayOptionAction.Resume
            resumeHardcoreIdx -> PlayOptionAction.ResumeHardcore
            newCasualIdx -> PlayOptionAction.NewCasual
            newHardcoreIdx -> {
                if (!state.isOnline) return null
                PlayOptionAction.NewHardcore
            }
            else -> null
        }
    }

    suspend fun shouldShowModeSelection(
        gameId: Long,
        isBuiltInEmulator: Boolean,
        hasAchievements: Boolean
    ): Boolean {
        if (!isBuiltInEmulator || !hasAchievements) return false
        val hasSaves = saveCacheManager.getCachesForGameOnce(gameId).isNotEmpty()
        val isRALoggedIn = raRepository.isLoggedIn()
        return !hasSaves && isRALoggedIn
    }

    fun showFreshGameModeSelection(scope: CoroutineScope, gameId: Long) {
        scope.launch {
            val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(context)
            _state.update {
                it.copy(
                    showPlayOptions = true,
                    playOptionsFocusIndex = 0,
                    hasCasualSaves = false,
                    hasHardcoreSave = false,
                    isRALoggedIn = true,
                    isOnline = isOnline
                )
            }
            soundManager.play(SoundType.OPEN_MODAL)
        }
    }
}
