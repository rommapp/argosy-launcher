package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.domain.model.UnifiedStateEntry
import com.nendo.argosy.domain.usecase.state.GetUnifiedStatesUseCase
import com.nendo.argosy.domain.usecase.state.RestoreStateResult
import com.nendo.argosy.domain.usecase.state.RestoreStateUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class SaveChannelStatesDelegate @Inject constructor(
    private val holder: SaveChannelStateHolder,
    private val getUnifiedStatesUseCase: GetUnifiedStatesUseCase,
    private val restoreStateUseCase: RestoreStateUseCase,
    private val stateCacheManager: StateCacheManager,
    private val gameRepository: GameRepository,
    private val emulatorResolver: EmulatorResolver,
    private val notificationManager: NotificationManager,
    private val soundManager: SoundFeedbackManager
) {
    private val _state get() = holder.state
    private val currentGameId get() = holder.currentGameId

    fun supportsStatesFor(emulatorId: String?): Boolean {
        return emulatorId != null && StatePathRegistry.getConfig(emulatorId) != null
    }

    suspend fun loadInitialStates(
        emulatorId: String?,
        channelName: String?,
        currentCoreId: String?,
        currentCoreVersion: String?
    ): List<UnifiedStateEntry> {
        if (!supportsStatesFor(emulatorId)) return emptyList()
        return getUnifiedStatesUseCase(
            gameId = currentGameId,
            emulatorId = emulatorId,
            channelName = channelName,
            currentCoreId = currentCoreId,
            currentCoreVersion = currentCoreVersion
        )
    }

    fun moveStateFocus(delta: Int) {
        _state.update { s ->
            val size = s.statesEntries.size
            if (size == 0) return@update s
            val maxIndex = (size - 1).coerceAtLeast(0)
            val newIndex = (s.focusIndex + delta).coerceIn(0, maxIndex)
            if (newIndex != s.focusIndex) {
                soundManager.play(SoundType.NAVIGATE)
            }
            s.copy(focusIndex = newIndex)
        }
    }

    fun setFocusIndex(index: Int) {
        _state.update { state ->
            val size = state.statesEntries.size
            if (size == 0) return@update state
            val maxIndex = (size - 1).coerceAtLeast(0)
            state.copy(focusIndex = index.coerceIn(0, maxIndex))
        }
    }

    fun confirmFocusedState(scope: CoroutineScope) {
        val state = _state.value
        val stateEntry = state.focusedStateEntry
        if (stateEntry == null || stateEntry.localCacheId == null) {
            return
        }
        if (stateEntry.versionStatus == UnifiedStateEntry.VersionStatus.MISMATCH) {
            _state.update {
                it.copy(
                    showVersionMismatchDialog = true,
                    versionMismatchState = stateEntry
                )
            }
        } else {
            restoreState(scope, stateEntry, forceRestore = false)
        }
    }

    fun dismissVersionMismatch() {
        _state.update {
            it.copy(
                showVersionMismatchDialog = false,
                versionMismatchState = null
            )
        }
    }

    fun confirmVersionMismatch(scope: CoroutineScope) {
        val stateEntry = _state.value.versionMismatchState ?: return
        _state.update {
            it.copy(
                showVersionMismatchDialog = false,
                versionMismatchState = null
            )
        }
        restoreState(scope, stateEntry, forceRestore = true)
    }

    private fun restoreState(
        scope: CoroutineScope,
        stateEntry: UnifiedStateEntry,
        forceRestore: Boolean
    ) {
        val state = _state.value
        val cacheId = stateEntry.localCacheId ?: return

        scope.launch {
            val game = gameRepository.getById(currentGameId)
            val romPath = game?.localPath
            if (romPath == null) {
                notificationManager.showError("Game has no local path")
                return@launch
            }
            val emulatorId = emulatorResolver.getEmulatorIdForGame(currentGameId, game.platformId, game.platformSlug)
                ?: state.emulatorId
                ?: return@launch

            val result = restoreStateUseCase(
                cacheId = cacheId,
                emulatorId = emulatorId,
                platformId = game.platformSlug,
                romPath = romPath,
                currentCoreId = state.currentCoreId,
                currentCoreVersion = state.currentCoreVersion,
                forceRestore = forceRestore
            )

            when (result) {
                is RestoreStateResult.Success -> {
                    val slotLabel = if (stateEntry.slotNumber == -1) {
                        "auto state"
                    } else "state slot ${stateEntry.slotNumber}"
                    notificationManager.showSuccess("Restored $slotLabel")
                    _state.update { it.copy(isVisible = false) }
                }
                is RestoreStateResult.VersionMismatch -> {
                    _state.update {
                        it.copy(
                            showVersionMismatchDialog = true,
                            versionMismatchState = stateEntry
                        )
                    }
                }
                is RestoreStateResult.Error -> {
                    notificationManager.showError(result.message)
                }
                is RestoreStateResult.NotFound -> {
                    notificationManager.showError("State not found in cache")
                }
                is RestoreStateResult.NoConfig -> {
                    notificationManager.showError(
                        "No state configuration for this emulator"
                    )
                }
            }
        }
    }

    fun showStateDeleteConfirmation() {
        val state = _state.value
        if (state.selectedTab != SaveTab.STATES) return
        val entry = state.focusedStateEntry ?: return
        if (entry.localCacheId == null) return

        _state.update {
            it.copy(
                showStateDeleteConfirmation = true,
                stateDeleteTarget = entry
            )
        }
    }

    fun dismissStateDeleteConfirmation() {
        _state.update {
            it.copy(
                showStateDeleteConfirmation = false,
                stateDeleteTarget = null
            )
        }
    }

    fun confirmDeleteState(scope: CoroutineScope) {
        val state = _state.value
        val entry = state.stateDeleteTarget ?: return
        val cacheId = entry.localCacheId ?: return

        scope.launch {
            stateCacheManager.deleteState(cacheId)
            refreshStates()
            _state.update {
                it.copy(
                    showStateDeleteConfirmation = false,
                    stateDeleteTarget = null,
                    focusIndex = it.focusIndex.coerceAtMost(
                        (it.statesEntries.size - 2).coerceAtLeast(0)
                    )
                )
            }
            val slotLabel = if (entry.slotNumber == -1) {
                "auto state"
            } else "state slot ${entry.slotNumber}"
            notificationManager.showSuccess("Deleted $slotLabel")
        }
    }

    fun showScreenshotPreview() {
        val state = _state.value
        if (state.selectedTab != SaveTab.STATES) return
        val entry = state.focusedStateEntry ?: return
        if (entry.screenshotPath == null) return

        _state.update {
            it.copy(
                showScreenshotPreview = true,
                screenshotPreviewEntry = entry
            )
        }
    }

    fun dismissScreenshotPreview() {
        _state.update {
            it.copy(
                showScreenshotPreview = false,
                screenshotPreviewEntry = null
            )
        }
    }

    fun showStateReplaceAutoConfirmation() {
        val state = _state.value
        if (state.selectedTab != SaveTab.STATES) return
        val entry = state.focusedStateEntry ?: return
        if (entry.localCacheId == null || entry.slotNumber < 0) return

        _state.update {
            it.copy(
                showStateReplaceAutoConfirmation = true,
                stateReplaceAutoTarget = entry
            )
        }
    }

    fun dismissStateReplaceAutoConfirmation() {
        _state.update {
            it.copy(
                showStateReplaceAutoConfirmation = false,
                stateReplaceAutoTarget = null
            )
        }
    }

    fun confirmReplaceAutoWithSlot(scope: CoroutineScope) {
        val state = _state.value
        val sourceEntry = state.stateReplaceAutoTarget ?: return
        val sourceCacheId = sourceEntry.localCacheId ?: return

        scope.launch {
            val sourceCache = stateCacheManager.getStateById(sourceCacheId)
            if (sourceCache == null) {
                notificationManager.showError("Source state not found")
                return@launch
            }

            val sourceFile = stateCacheManager.getCacheFile(sourceCache)
            if (sourceFile == null) {
                notificationManager.showError("Source state file not found")
                return@launch
            }

            val autoState = state.statesEntries.find { it.slotNumber == -1 }
            if (autoState?.localCacheId != null) {
                stateCacheManager.deleteState(autoState.localCacheId)
            }

            val autoFileName = sourceFile.name.replace(
                Regex("\\.state\\d+$"),
                ".state.auto"
            ).let { name ->
                if (!name.endsWith(".state.auto")) {
                    name.replace(".state", ".state.auto")
                } else name
            }

            val coreDir = stateCacheManager.getCoreDir(
                currentGameId,
                sourceCache.platformSlug,
                sourceCache.channelName,
                sourceCache.coreId
            )
            val autoFile = java.io.File(coreDir, autoFileName)
            sourceFile.copyTo(autoFile, overwrite = true)

            val screenshotFile = stateCacheManager.getScreenshotFile(sourceCache)
            if (screenshotFile != null) {
                val autoScreenshot = java.io.File(
                    coreDir, "$autoFileName.png"
                )
                screenshotFile.copyTo(autoScreenshot, overwrite = true)
            }

            val channelDirName = sourceCache.channelName ?: "default"
            val coreDirName = sourceCache.coreId ?: "unknown"
            val autoCachePath = "${sourceCache.platformSlug}/" +
                "${currentGameId}/$channelDirName/$coreDirName/$autoFileName"
            val autoScreenshotPath = if (screenshotFile != null) {
                "$autoCachePath.png"
            } else null

            val autoEntity = sourceCache.copy(
                id = 0,
                slotNumber = -1,
                cachePath = autoCachePath,
                screenshotPath = autoScreenshotPath,
                cachedAt = java.time.Instant.now()
            )
            stateCacheManager.cacheState(
                gameId = autoEntity.gameId,
                platformSlug = autoEntity.platformSlug,
                emulatorId = autoEntity.emulatorId,
                slotNumber = -1,
                statePath = autoFile.absolutePath,
                coreId = autoEntity.coreId,
                coreVersion = autoEntity.coreVersion,
                channelName = autoEntity.channelName,
                isLocked = autoEntity.isLocked
            )

            refreshStates()
            _state.update {
                it.copy(
                    showStateReplaceAutoConfirmation = false,
                    stateReplaceAutoTarget = null
                )
            }
            notificationManager.showSuccess(
                "Replaced auto state with slot ${sourceEntry.slotNumber}"
            )
        }
    }

    private suspend fun refreshStates() {
        val state = _state.value
        val game = gameRepository.getById(currentGameId)
        val resolvedEmulatorId = game?.let {
            emulatorResolver.getEmulatorIdForGame(currentGameId, it.platformId, it.platformSlug)
        } ?: state.emulatorId
        val states = getUnifiedStatesUseCase(
            gameId = currentGameId,
            emulatorId = resolvedEmulatorId,
            channelName = state.activeChannel,
            currentCoreId = state.currentCoreId,
            currentCoreVersion = state.currentCoreVersion
        )
        _state.update { it.copy(statesEntries = states) }
    }
}
