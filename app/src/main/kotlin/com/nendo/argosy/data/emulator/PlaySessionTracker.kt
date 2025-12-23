package com.nendo.argosy.data.emulator

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import com.nendo.argosy.domain.usecase.state.StateSyncResult
import com.nendo.argosy.domain.usecase.state.SyncStatesOnSessionEndUseCase
import com.nendo.argosy.ui.screens.common.GameUpdateBus
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveSession(
    val gameId: Long,
    val startTime: Instant,
    val emulatorPackage: String,
    val coreName: String? = null
)

data class SaveConflictEvent(
    val gameId: Long,
    val localTimestamp: Instant,
    val serverTimestamp: Instant
)

@Singleton
class PlaySessionTracker @Inject constructor(
    private val application: Application,
    private val gameDao: GameDao,
    private val syncSaveOnSessionEndUseCase: dagger.Lazy<SyncSaveOnSessionEndUseCase>,
    private val syncStatesOnSessionEndUseCase: dagger.Lazy<SyncStatesOnSessionEndUseCase>,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val saveSyncRepository: dagger.Lazy<SaveSyncRepository>,
    private val romMRepository: dagger.Lazy<RomMRepository>,
    private val preferencesRepository: UserPreferencesRepository,
    private val permissionHelper: PermissionHelper,
    private val gameUpdateBus: GameUpdateBus
) {
    companion object {
        private const val TAG = "PlaySessionTracker"
        private const val MIN_PLAY_SECONDS_FOR_COMPLETION = 20
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    private val _conflictEvents = MutableSharedFlow<SaveConflictEvent>()
    val conflictEvents: SharedFlow<SaveConflictEvent> = _conflictEvents.asSharedFlow()

    private var wasInBackground = false
    private var lastPauseTime: Instant? = null

    private var screenOnDuration: Duration = Duration.ZERO
    private var lastScreenOnTime: Instant? = null
    private var isScreenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (_activeSession.value == null) return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    init {
        registerLifecycleCallbacks()
        registerScreenReceiver()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        application.registerReceiver(screenReceiver, filter)
    }

    private fun onScreenOn() {
        if (!isScreenOn) {
            isScreenOn = true
            lastScreenOnTime = Instant.now()
            Log.d(TAG, "Screen ON - resuming active time tracking")
        }
    }

    private fun onScreenOff() {
        if (isScreenOn && lastScreenOnTime != null) {
            isScreenOn = false
            val elapsed = Duration.between(lastScreenOnTime, Instant.now())
            screenOnDuration = screenOnDuration.plus(elapsed)
            lastScreenOnTime = null
            Log.d(TAG, "Screen OFF - paused after ${elapsed.toMinutes()} minutes active")
        }
    }

    fun startSession(gameId: Long, emulatorPackage: String, coreName: String? = null) {
        screenOnDuration = Duration.ZERO
        lastScreenOnTime = Instant.now()
        isScreenOn = true

        _activeSession.value = ActiveSession(
            gameId = gameId,
            startTime = Instant.now(),
            emulatorPackage = emulatorPackage,
            coreName = coreName
        )
        Log.d(TAG, "Session started: gameId=$gameId, emulator=$emulatorPackage, screenOn=$isScreenOn")
    }

    fun endSession() {
        val session = _activeSession.value ?: run {
            Log.d(TAG, "endSession called but no active session")
            return
        }
        _activeSession.value = null

        val elapsedSinceScreenOn = if (isScreenOn && lastScreenOnTime != null) {
            Duration.between(lastScreenOnTime, Instant.now())
        } else {
            Duration.ZERO
        }
        screenOnDuration = screenOnDuration.plus(elapsedSinceScreenOn)

        val sessionDuration = Duration.between(session.startTime, Instant.now())
        val finalScreenOnDuration = screenOnDuration

        Log.d(TAG, "endSession: gameId=${session.gameId}, totalSession=${sessionDuration.toMillis() / 1000}s, screenOnDuration=${finalScreenOnDuration.toMillis() / 1000}s, lastElapsed=${elapsedSinceScreenOn.toMillis() / 1000}s")

        scope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            val hasPermission = permissionHelper.hasUsageStatsPermission(application)
            val canTrackAccurately = prefs.accuratePlayTimeEnabled && hasPermission

            Log.d(TAG, "Time tracking check: enabled=${prefs.accuratePlayTimeEnabled}, hasPermission=$hasPermission, canTrack=$canTrackAccurately")

            if (canTrackAccurately) {
                val seconds = finalScreenOnDuration.toMillis() / 1000
                val minutes = ((seconds + 30) / 60).toInt()
                if (minutes > 0) {
                    gameDao.addPlayTime(session.gameId, minutes)
                    val updatedGame = gameDao.getById(session.gameId)
                    updatedGame?.let {
                        gameUpdateBus.emit(GameUpdateBus.GameUpdate(
                            gameId = session.gameId,
                            playTimeMinutes = it.playTimeMinutes
                        ))
                    }
                    Log.d(TAG, "Added $minutes minutes of active play time for game ${session.gameId} (${seconds}s)")
                } else {
                    Log.d(TAG, "Session too short to record: ${seconds}s")
                }
            } else {
                Log.d(TAG, "Skipping play time recording - accurate tracking not enabled or permission missing")
            }

            if (sessionDuration.seconds >= MIN_PLAY_SECONDS_FOR_COMPLETION) {
                val game = gameDao.getById(session.gameId)
                if (game?.rommId != null && game.status == null) {
                    romMRepository.get().updateUserStatus(session.gameId, "incomplete")
                    gameUpdateBus.emit(GameUpdateBus.GameUpdate(
                        gameId = session.gameId,
                        status = "incomplete"
                    ))
                    Log.d(TAG, "Marked game ${session.gameId} as Incomplete after ${sessionDuration.seconds}s session")
                }
            }

            val result = syncSaveOnSessionEndUseCase.get()(
                session.gameId,
                session.emulatorPackage,
                session.startTime.toEpochMilli(),
                session.coreName
            )

            cacheCurrentSave(session)
            when (result) {
                is SyncSaveOnSessionEndUseCase.Result.Conflict -> {
                    _conflictEvents.emit(
                        SaveConflictEvent(
                            gameId = result.gameId,
                            localTimestamp = result.localTimestamp,
                            serverTimestamp = result.serverTimestamp
                        )
                    )
                }
                is SyncSaveOnSessionEndUseCase.Result.Uploaded -> {
                    Log.d(TAG, "Save uploaded for game ${session.gameId}")
                }
                is SyncSaveOnSessionEndUseCase.Result.Queued -> {
                    Log.d(TAG, "Save queued for game ${session.gameId}")
                }
                is SyncSaveOnSessionEndUseCase.Result.NoSaveFound -> {
                    Log.d(TAG, "No save found for game ${session.gameId}")
                }
                is SyncSaveOnSessionEndUseCase.Result.NotConfigured -> {
                    Log.d(TAG, "Save sync not configured for game ${session.gameId}")
                }
                is SyncSaveOnSessionEndUseCase.Result.Error -> {
                    Log.e(TAG, "Save sync error: ${result.message}")
                }
            }

            val stateResult = syncStatesOnSessionEndUseCase.get()(
                session.gameId,
                session.emulatorPackage
            )
            when (stateResult) {
                is StateSyncResult.Cached -> {
                    Log.d(TAG, "Cached ${stateResult.count} states for game ${session.gameId}")
                }
                is StateSyncResult.NoStatesFound -> {
                    Log.d(TAG, "No states found for game ${session.gameId}")
                }
                is StateSyncResult.NotConfigured -> {
                    Log.d(TAG, "State caching not configured for game ${session.gameId}")
                }
                is StateSyncResult.Error -> {
                    Log.e(TAG, "State sync error: ${stateResult.message}")
                }
            }
        }
    }

    private fun registerLifecycleCallbacks() {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                if (wasInBackground && _activeSession.value != null) {
                    wasInBackground = false
                }
            }

            override fun onActivityPaused(activity: Activity) {
                lastPauseTime = Instant.now()
            }

            override fun onActivityStopped(activity: Activity) {
                wasInBackground = true

                val pauseTime = lastPauseTime ?: return
                val elapsed = Duration.between(pauseTime, Instant.now())

                if (elapsed.toMinutes() > 30 && _activeSession.value != null) {
                    endSession()
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun getSessionDuration(): Duration? {
        val session = _activeSession.value ?: return null
        return Duration.between(session.startTime, Instant.now())
    }

    private suspend fun cacheCurrentSave(session: ActiveSession) {
        try {
            val game = gameDao.getById(session.gameId) ?: return

            val emulatorId = resolveEmulatorId(session.emulatorPackage)
            if (emulatorId == null) {
                Log.d(TAG, "Cannot resolve emulator ID for ${session.emulatorPackage}")
                return
            }

            val savePath = saveSyncRepository.get().discoverSavePath(
                emulatorId,
                game.title,
                game.platformSlug,
                game.localPath,
                game.titleId,
                session.coreName
            )

            if (savePath != null) {
                val activeChannel = game.activeSaveChannel
                val cached = saveCacheManager.get().cacheCurrentSave(
                    gameId = session.gameId,
                    emulatorId = emulatorId,
                    savePath = savePath,
                    channelName = activeChannel,
                    isLocked = false
                )
                if (cached) {
                    gameDao.updateActiveSaveTimestamp(session.gameId, null)
                    val channelLog = activeChannel?.let { " (channel: $it)" } ?: ""
                    Log.d(TAG, "Cached save for game ${session.gameId}$channelLog")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache save", e)
        }
    }

    private fun resolveEmulatorId(packageName: String): String? {
        EmulatorRegistry.getByPackage(packageName)?.let { return it.id }
        EmulatorRegistry.findFamilyForPackage(packageName)?.let { return it.baseId }
        return null
    }
}
