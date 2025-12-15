package com.nendo.argosy.ui.screens.common

import android.content.Intent
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.model.SyncState
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.domain.usecase.game.LaunchWithSyncUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncOverlayState(
    val gameTitle: String,
    val syncState: SyncState
)

class GameLaunchDelegate @Inject constructor(
    private val gameDao: GameDao,
    private val emulatorResolver: EmulatorResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val launchGameUseCase: LaunchGameUseCase,
    private val launchWithSyncUseCase: LaunchWithSyncUseCase,
    private val playSessionTracker: PlaySessionTracker,
    private val soundManager: SoundFeedbackManager,
    private val notificationManager: NotificationManager
) {
    private val _syncOverlayState = MutableStateFlow<SyncOverlayState?>(null)
    val syncOverlayState: StateFlow<SyncOverlayState?> = _syncOverlayState.asStateFlow()

    val isSyncing: Boolean get() = _syncOverlayState.value != null

    fun launchGame(
        scope: CoroutineScope,
        gameId: Long,
        discId: Long? = null,
        onLaunch: (Intent) -> Unit
    ) {
        if (isSyncing) return

        scope.launch {
            val game = gameDao.getById(gameId) ?: return@launch
            val gameTitle = game.title

            val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId)
            val emulatorId = emulatorPackage?.let { emulatorResolver.resolveEmulatorId(it) }
            val prefs = preferencesRepository.preferences.first()
            val canSync = emulatorId != null && SavePathRegistry.canSyncWithSettings(
                emulatorId,
                prefs.saveSyncEnabled,
                prefs.experimentalFolderSaveSync
            )

            val syncStartTime = if (canSync) {
                _syncOverlayState.value = SyncOverlayState(gameTitle, SyncState.CheckingConnection)
                System.currentTimeMillis()
            } else null

            launchWithSyncUseCase.invoke(gameId).collect { state ->
                if (canSync && state != SyncState.Skipped && state != SyncState.Idle) {
                    _syncOverlayState.value = SyncOverlayState(gameTitle, state)
                }
            }

            syncStartTime?.let { startTime ->
                val elapsed = System.currentTimeMillis() - startTime
                val minDisplayTime = 2000L
                if (elapsed < minDisplayTime) {
                    delay(minDisplayTime - elapsed)
                }
            }

            _syncOverlayState.value = null

            when (val result = launchGameUseCase(gameId, discId)) {
                is LaunchResult.Success -> {
                    soundManager.play(SoundType.LAUNCH_GAME)
                    onLaunch(result.intent)
                }
                is LaunchResult.NoEmulator -> {
                    notificationManager.showError("No emulator installed for this platform")
                }
                is LaunchResult.NoRomFile -> {
                    notificationManager.showError("ROM file not found")
                }
                is LaunchResult.NoSteamLauncher -> {
                    notificationManager.showError("Steam launcher not installed")
                }
                is LaunchResult.NoCore -> {
                    notificationManager.showError("No compatible RetroArch core installed for ${result.platformId}")
                }
                is LaunchResult.MissingDiscs -> {
                    val discText = result.missingDiscNumbers.joinToString(", ")
                    notificationManager.showError("Missing discs: $discText. View game details to repair.")
                }
                is LaunchResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
        }
    }

    fun handleSessionEnd(
        scope: CoroutineScope,
        onSyncComplete: () -> Unit = {}
    ) {
        val session = playSessionTracker.activeSession.value ?: return
        if (isSyncing) return

        val emulatorId = emulatorResolver.resolveEmulatorId(session.emulatorPackage) ?: return
        if (SavePathRegistry.getConfig(emulatorId) == null) {
            playSessionTracker.endSession()
            return
        }

        scope.launch {
            val game = gameDao.getById(session.gameId)
            val gameTitle = game?.title ?: "Game"

            _syncOverlayState.value = SyncOverlayState(gameTitle, SyncState.Uploading)

            val syncStartTime = System.currentTimeMillis()
            playSessionTracker.endSession()

            val elapsed = System.currentTimeMillis() - syncStartTime
            val minDisplayTime = 2000L
            if (elapsed < minDisplayTime) {
                delay(minDisplayTime - elapsed)
            }

            _syncOverlayState.value = null
            onSyncComplete()
        }
    }
}
