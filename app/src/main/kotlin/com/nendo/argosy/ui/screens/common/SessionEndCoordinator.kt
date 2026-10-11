package com.nendo.argosy.ui.screens.common

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.SavePathValidator
import com.nendo.argosy.data.emulator.SessionEndResult
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.util.SafeCoroutineScope
import com.nendo.argosy.util.openStorageAccessSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SessionEnd"
private const val SHORT_SESSION_SECONDS = 30L
private const val COMPLETE_HOLD_MS = 800L
private const val ERROR_HOLD_MS = 1500L

@Singleton
class SessionEndCoordinator @Inject constructor(
    private val application: Application,
    private val playSessionTracker: PlaySessionTracker,
    private val emulatorResolver: EmulatorResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val savePathValidator: SavePathValidator,
    private val gameLauncher: GameLauncher
) {
    private val scope = SafeCoroutineScope(Dispatchers.Main.immediate, "SessionEnd")

    private val _syncOverlayState = MutableStateFlow<SyncOverlayState?>(null)
    val syncOverlayState: StateFlow<SyncOverlayState?> = _syncOverlayState.asStateFlow()

    /**
     * Ends the session whose game has closed, or recovers a persisted one when none is live. The
     * returned job completes once the session end and its save work have returned.
     */
    fun endClosedSession(): Job {
        val session = playSessionTracker.activeSession.value
            ?: return playSessionTracker.endSessionInBackground()
        return scope.launch { endSession(session) }
    }

    suspend fun stopBackgroundEmulator(packageName: String) = gameLauncher.forceStopEmulator(packageName)

    /**
     * Whether an external emulator is stopped when its session ends: the emulator requires it, or
     * the user closes emulators on session end. Never true for the built-in emulator or Argosy.
     */
    suspend fun shouldStopAfterSession(emulatorPackage: String): Boolean {
        if (emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE) return false
        if (emulatorPackage == application.packageName) return false
        val emulator = emulatorResolver.resolveEmulatorId(emulatorPackage)
            ?.let { EmulatorRegistry.getById(it) }
            ?: return false
        return emulator.launchConfig.requiresEmulatorKill ||
            preferencesRepository.preferences.first().closeEmulatorOnSessionEnd
    }

    private fun stopEmulatorAfterSession(session: ActiveSession) {
        scope.launch(Dispatchers.IO) {
            val current = playSessionTracker.activeSession.value
            if (current?.emulatorPackage == session.emulatorPackage) return@launch
            if (shouldStopAfterSession(session.emulatorPackage)) {
                gameLauncher.forceStopEmulator(session.emulatorPackage)
            }
        }
    }

    private suspend fun endSession(session: ActiveSession) {
        val sessionDuration = playSessionTracker.getSessionDuration()
        val sawSave = playSessionTracker.sawSaveActivity()
        val isShort = sessionDuration != null && sessionDuration.seconds < SHORT_SESSION_SECONDS
        if (isShort && !sawSave) {
            Log.d(TAG, "short session (${sessionDuration?.seconds}s), cancelling without backup")
            playSessionTracker.cancelSession()
            stopEmulatorAfterSession(session)
            return
        }

        val emulatorId = emulatorResolver.resolveEmulatorId(session.emulatorPackage)
        if (emulatorId == null) {
            Log.d(TAG, "cannot resolve emulatorId, ending session without sync")
            playSessionTracker.endSession()
            stopEmulatorAfterSession(session)
            return
        }

        try {
            val prefs = preferencesRepository.preferences.first()
            if (!SavePathRegistry.canSyncWithSettings(emulatorId, prefs.saveSyncEnabled)) {
                playSessionTracker.endSession()
                stopEmulatorAfterSession(session)
                return
            }

            val game = gameRepository.getById(session.gameId)
            val gameTitle = game?.title ?: "Game"
            val emulatorName = EmulatorRegistry.getById(emulatorId)?.displayName

            when (val validation = savePathValidator.validateAccess(emulatorId, session.emulatorPackage)) {
                is SavePathValidator.Result.PermissionRequired -> {
                    showBlockedOverlay(gameTitle, SyncProgress.BlockedReason.PermissionRequired(emulatorName), session)
                    return
                }
                is SavePathValidator.Result.AccessDenied -> {
                    showBlockedOverlay(
                        gameTitle,
                        SyncProgress.BlockedReason.AccessDenied(emulatorName, validation.path, platformSlug = game?.platformSlug),
                        session
                    )
                    return
                }
                is SavePathValidator.Result.SavePathNotFound,
                is SavePathValidator.Result.Valid,
                is SavePathValidator.Result.NotFolderBased,
                is SavePathValidator.Result.NoConfig -> Unit
            }

            Log.d(TAG, "[DualSync] Starting post-session sync | game=$gameTitle, channel=${session.channelName}, emulator=$emulatorId")
            _syncOverlayState.value = SyncOverlayState(gameTitle, SyncProgress.PostSession.CheckingSave(session.channelName))

            val result = playSessionTracker.endSession()
            Log.d(TAG, "[DualSync] endSession result: ${result::class.simpleName}")

            when (result) {
                is SessionEndResult.Success -> {
                    _syncOverlayState.value = SyncOverlayState(gameTitle, SyncProgress.PostSession.Complete)
                    delay(COMPLETE_HOLD_MS)
                    _syncOverlayState.value = null
                }
                is SessionEndResult.Duplicate, is SessionEndResult.Skipped -> _syncOverlayState.value = null
                is SessionEndResult.SaveUnreadable -> {
                    stopEmulatorAfterSession(session)
                    showBlockedOverlay(
                        gameTitle,
                        SyncProgress.BlockedReason.AccessDenied(
                            EmulatorRegistry.getById(result.emulatorId)?.displayName ?: emulatorName,
                            result.dirPath,
                            platformSlug = game?.platformSlug
                        ),
                        session = null
                    )
                    return
                }
                is SessionEndResult.Error -> {
                    Log.w(TAG, "[DualSync] Session end error: ${result.message}")
                    _syncOverlayState.value = SyncOverlayState(gameTitle, SyncProgress.Error(result.message))
                    delay(ERROR_HOLD_MS)
                    _syncOverlayState.value = null
                }
            }
            stopEmulatorAfterSession(session)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "session end failed", e)
            _syncOverlayState.value = null
            playSessionTracker.endSessionInBackground().join()
            stopEmulatorAfterSession(session)
        }
    }

    private fun showBlockedOverlay(gameTitle: String, progress: SyncProgress.BlockedReason, session: ActiveSession?) {
        val isSwitchAccessDenied = progress is SyncProgress.BlockedReason.AccessDenied &&
            progress.platformSlug == "switch"
        _syncOverlayState.value = SyncOverlayState(
            gameTitle = gameTitle,
            syncProgress = progress,
            onGrantPermission = {
                openAllFilesAccessSettings()
                dismissBlockedOverlay(session)
            },
            onOpenSettings = if (isSwitchAccessDenied) {
                { dismissBlockedOverlay(session) }
            } else null,
            onDisableSync = {
                scope.launch { preferencesRepository.setSaveSyncEnabled(false) }
                dismissBlockedOverlay(session)
            },
            onSkip = { dismissBlockedOverlay(session) }
        )
    }

    private fun dismissBlockedOverlay(session: ActiveSession?) {
        _syncOverlayState.value = null
        scope.launch {
            playSessionTracker.endSession()
            session?.let { stopEmulatorAfterSession(it) }
        }
    }

    private fun openAllFilesAccessSettings() {
        application.openStorageAccessSettings()
    }
}
