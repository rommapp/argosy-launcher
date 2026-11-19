package com.nendo.argosy.ui.screens.buildinglibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.SyncProgress
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BuildingLibraryPhase { WAITING, SYNCING, DONE }

data class LibraryWallTile(val gameId: Long, val coverPath: String)

data class BuildingLibraryUiState(
    val phase: BuildingLibraryPhase = BuildingLibraryPhase.WAITING,
    val currentPlatform: String = "",
    val platformsDone: Int = 0,
    val platformsTotal: Int = 0,
    val gamesDone: Int = 0,
    val gamesTotal: Int = 0,
    val tiles: List<LibraryWallTile> = emptyList()
) {
    val gamesFraction: Float? get() = if (gamesTotal > 0) (gamesDone.toFloat() / gamesTotal).coerceIn(0f, 1f) else null
}

@HiltViewModel
class BuildingLibraryViewModel @Inject constructor(
    private val romMRepository: RomMRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildingLibraryUiState())
    val uiState: StateFlow<BuildingLibraryUiState> = _uiState.asStateFlow()
    private val pending = ArrayList<Long>()

    init {
        viewModelScope.launch {
            romMRepository.syncProgress.collect { progress -> _uiState.update { it.withProgress(progress) } }
        }
        viewModelScope.launch {
            romMRepository.syncedGames.collect { pending.add(it) }
        }
        viewModelScope.launch {
            while (true) {
                delay(ComponentDefaults.BuildingLibrary.wallBatchMs.toLong())
                if (pending.isEmpty()) continue
                val ids = pending.toList()
                pending.clear()
                val covers = gameRepository.libraryWallCovers(ids).associate { it.gameId to it.coverPath }
                val arrived = ids.mapNotNull { id -> covers[id]?.let { LibraryWallTile(id, it) } }
                _uiState.update {
                    it.copy(tiles = appendWallTiles(it.tiles, arrived, ComponentDefaults.BuildingLibrary.wallCoverLimit))
                }
            }
        }
        viewModelScope.launch {
            delay(ComponentDefaults.BuildingLibrary.startTimeoutMs.toLong())
            _uiState.update { if (it.phase == BuildingLibraryPhase.WAITING) it.copy(phase = BuildingLibraryPhase.DONE) else it }
        }
    }

    fun createInputHandler(onBrowse: () -> Unit, onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onConfirm(): InputResult {
            onBrowse()
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }
    }
}

internal fun appendWallTiles(
    existing: List<LibraryWallTile>,
    arrived: List<LibraryWallTile>,
    limit: Int
): List<LibraryWallTile> {
    val held = existing.mapTo(HashSet()) { it.gameId }
    val fresh = arrived.filter { held.add(it.gameId) }
    if (fresh.isEmpty()) return existing
    return (existing + fresh).takeLast(limit)
}

internal fun BuildingLibraryUiState.withProgress(progress: SyncProgress): BuildingLibraryUiState {
    if (!progress.isSyncing) {
        return if (phase == BuildingLibraryPhase.SYNCING) copy(phase = BuildingLibraryPhase.DONE) else this
    }
    return copy(
        phase = BuildingLibraryPhase.SYNCING,
        currentPlatform = progress.currentPlatform,
        platformsDone = progress.platformsDone,
        platformsTotal = progress.platformsTotal,
        gamesDone = progress.gamesDone,
        gamesTotal = progress.gamesTotal
    )
}
