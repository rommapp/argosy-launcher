package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayOptionsState(
    val showPlayOptions: Boolean = false,
    val playOptionsFocusIndex: Int = 0,
    val hasCasualSaves: Boolean = false,
    val hasHardcoreSave: Boolean = false,
    val hasRASupport: Boolean = false,
    val isRALoggedIn: Boolean = false,
    val isOnline: Boolean = false
) {
    /** Hardcore requires a RetroAchievements login. */
    val hardcoreAvailable: Boolean get() = hasRASupport && isRALoggedIn

    /**
     * Continue-in-hardcore is offered whenever hardcore is available and there is any resumable
     * save. RESUME_HARDCORE loads the latest hardcore save if present, else falls back to the
     * active SRAM -- so a casual save can be continued in a hardcore session.
     */
    val showResumeHardcore: Boolean get() = hardcoreAvailable && (hasCasualSaves || hasHardcoreSave)
}

class PlayOptionsDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val getUnifiedSavesUseCase: GetUnifiedSavesUseCase,
    private val raRepository: RetroAchievementsRepository,
    private val launchGameUseCase: LaunchGameUseCase,
    private val soundManager: SoundFeedbackManager,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val _state = MutableStateFlow(PlayOptionsState())
    val state: StateFlow<PlayOptionsState> = _state.asStateFlow()

    private val _launchEvents = MutableSharedFlow<LaunchEvent>()
    val launchEvents: SharedFlow<LaunchEvent> = _launchEvents.asSharedFlow()

    fun reset() {
        _state.value = PlayOptionsState()
    }

    /**
     * Opens the play-options modal. Save existence (Continue vs New) comes from the unified
     * local+server view so a server-only cloud save still offers Continue, but the server fetch is
     * skipped when offline to avoid the sync client's connect timeout.
     */
    fun showPlayOptions(scope: CoroutineScope, gameId: Long, hasAchievements: Boolean) {
        scope.launch {
            val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(context)
            val entries = getUnifiedSavesUseCase(gameId, expandHistory = false, includeServer = isOnline)
            val hasCasualSaves = entries.any { !it.isHardcore }
            val hasHardcoreSave = entries.any { it.isHardcore }
            val isRALoggedIn = raRepository.isLoggedIn()
            val defaultToHardcore = isDefaultToHardcore()

            val newState = PlayOptionsState(
                showPlayOptions = true,
                hasCasualSaves = hasCasualSaves,
                hasHardcoreSave = hasHardcoreSave,
                hasRASupport = hasAchievements,
                isRALoggedIn = isRALoggedIn,
                isOnline = isOnline
            )
            openModal(newState, PlayOptionAction.ResumeHardcore.takeIf { defaultToHardcore })
        }
    }

    /**
     * The play options shown, in display order -- the single source of truth for the modal's row
     * layout, its focus bounds, and confirm mapping. Must match [PlayOptionsModal]'s rendering.
     */
    private fun visibleActions(state: PlayOptionsState): List<PlayOptionAction> = buildList {
        if (state.hasCasualSaves) add(PlayOptionAction.Resume)
        if (state.hasCasualSaves && state.isOnline) add(PlayOptionAction.ResumeNoSync)
        if (state.showResumeHardcore) add(PlayOptionAction.ResumeHardcore)
        add(PlayOptionAction.NewCasual)
        if (state.hardcoreAvailable) add(PlayOptionAction.NewHardcore)
    }

    /** Show the play-options modal, pre-focusing [preferred] if present (else the first row). */
    private fun openModal(state: PlayOptionsState, preferred: PlayOptionAction?) {
        val focusIndex = preferred?.let { visibleActions(state).indexOf(it).coerceAtLeast(0) } ?: 0
        _state.value = state.copy(playOptionsFocusIndex = focusIndex)
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissPlayOptions() {
        _state.update { it.copy(showPlayOptions = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun movePlayOptionsFocus(delta: Int) {
        _state.update {
            val maxIndex = (visibleActions(it).size - 1).coerceAtLeast(0)
            it.copy(playOptionsFocusIndex = (it.playOptionsFocusIndex + delta).coerceIn(0, maxIndex))
        }
    }

    fun confirmPlayOptionSelection(): PlayOptionAction? {
        val state = _state.value
        val action = visibleActions(state).getOrNull(state.playOptionsFocusIndex) ?: return null
        if (action == PlayOptionAction.NewHardcore && !state.isOnline) return null
        return action
    }

    suspend fun shouldShowModeSelection(
        gameId: Long,
        isBuiltInEmulator: Boolean,
        hasAchievements: Boolean
    ): Boolean {
        if (!isBuiltInEmulator || !hasAchievements) return false
        if (!raRepository.isLoggedIn()) return false
        val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(context)
        return getUnifiedSavesUseCase(gameId, expandHistory = false, includeServer = isOnline).isEmpty()
    }

    fun showFreshGameModeSelection(scope: CoroutineScope, gameId: Long) {
        scope.launch {
            val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(context)
            val newState = PlayOptionsState(
                showPlayOptions = true,
                hasCasualSaves = false,
                hasHardcoreSave = false,
                hasRASupport = true,
                isRALoggedIn = true,
                isOnline = isOnline
            )
            openModal(newState, PlayOptionAction.NewHardcore.takeIf { isDefaultToHardcore() && isOnline })
        }
    }

    private suspend fun isDefaultToHardcore(): Boolean =
        userPreferencesRepository.getBuiltinEmulatorSettings().first().defaultToHardcore
}
