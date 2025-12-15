package com.nendo.argosy.data.emulator

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveSession(
    val gameId: Long,
    val startTime: Instant,
    val emulatorPackage: String
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
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val saveSyncRepository: dagger.Lazy<SaveSyncRepository>
) {
    companion object {
        private const val TAG = "PlaySessionTracker"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    private val _conflictEvents = MutableSharedFlow<SaveConflictEvent>()
    val conflictEvents: SharedFlow<SaveConflictEvent> = _conflictEvents.asSharedFlow()

    private var wasInBackground = false
    private var lastPauseTime: Instant? = null

    init {
        registerLifecycleCallbacks()
    }

    fun startSession(gameId: Long, emulatorPackage: String) {
        _activeSession.value = ActiveSession(
            gameId = gameId,
            startTime = Instant.now(),
            emulatorPackage = emulatorPackage
        )
    }

    fun endSession() {
        val session = _activeSession.value ?: return
        _activeSession.value = null

        val duration = Duration.between(session.startTime, Instant.now())
        val minutes = duration.toMinutes().toInt()

        scope.launch {
            if (minutes > 0) {
                gameDao.addPlayTime(session.gameId, minutes)
            }

            cacheCurrentSave(session)

            val result = syncSaveOnSessionEndUseCase.get()(session.gameId, session.emulatorPackage)
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
                game.platformId,
                game.localPath
            )

            if (savePath != null) {
                val activeChannel = game.activeSaveChannel
                val cached = saveCacheManager.get().cacheCurrentSave(
                    gameId = session.gameId,
                    emulatorId = emulatorId,
                    savePath = savePath,
                    channelName = activeChannel
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
