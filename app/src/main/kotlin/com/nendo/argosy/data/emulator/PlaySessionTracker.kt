package com.nendo.argosy.data.emulator

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import com.nendo.argosy.domain.usecase.state.StateSyncResult
import com.nendo.argosy.domain.usecase.state.SyncStatesOnSessionEndUseCase
import com.nendo.argosy.ui.notification.NotificationDuration
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationType
import com.nendo.argosy.ui.screens.common.GameUpdateBus
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val coreName: String? = null,
    val isHardcore: Boolean = false,
    val isNewGame: Boolean = false,
    val channelName: String? = null,
    val flashReturnCount: Int = 0,
    val isOnOlderSave: Boolean = false
)

sealed class SessionEndResult {
    data object Success : SessionEndResult()
    data object Duplicate : SessionEndResult()
    data object Skipped : SessionEndResult()
    data class Error(val message: String) : SessionEndResult()
}

data class SaveConflictEvent(
    val gameId: Long,
    val emulatorId: String,
    val channelName: String?,
    val localTimestamp: Instant,
    val serverTimestamp: Instant
)

@Singleton
class PlaySessionTracker @Inject constructor(
    private val application: Application,
    private val gameDao: GameDao,
    private val saveCacheDao: SaveCacheDao,
    private val syncSaveOnSessionEndUseCase: dagger.Lazy<SyncSaveOnSessionEndUseCase>,
    private val syncStatesOnSessionEndUseCase: dagger.Lazy<SyncStatesOnSessionEndUseCase>,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val saveSyncRepository: dagger.Lazy<SaveSyncRepository>,
    private val romMRepository: dagger.Lazy<RomMRepository>,
    private val preferencesRepository: UserPreferencesRepository,
    private val permissionHelper: PermissionHelper,
    private val gameUpdateBus: GameUpdateBus,
    private val emulatorResolver: EmulatorResolver,
    private val notificationManager: NotificationManager,
    private val fileAccessLayer: FileAccessLayer
) {
    companion object {
        private const val TAG = "PlaySessionTracker"
        private const val MIN_PLAY_SECONDS_FOR_COMPLETION = 20

        // Broadcast actions for companion process
        private const val ACTION_SESSION_CHANGED = "com.nendo.argosy.SESSION_CHANGED"
        private const val EXTRA_GAME_ID = "game_id"
        private const val EXTRA_CHANNEL_NAME = "channel_name"
        private const val EXTRA_IS_HARDCORE = "is_hardcore"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionStateStore by lazy { SessionStateStore(application) }

    private fun broadcastSessionChanged(gameId: Long?, channelName: String?, isHardcore: Boolean) {
        // Write to SharedPreferences for companion process to read on startup
        if (gameId != null && gameId > 0) {
            val startMillis = _activeSession.value?.startTime?.toEpochMilli()
                ?: System.currentTimeMillis()
            sessionStateStore.setActiveSession(gameId, channelName, isHardcore, startMillis)
        } else {
            sessionStateStore.clearSession()
        }

        // Broadcast for live updates
        val intent = Intent(ACTION_SESSION_CHANGED).apply {
            setPackage(application.packageName)
            putExtra(EXTRA_GAME_ID, gameId ?: -1L)
            putExtra(EXTRA_CHANNEL_NAME, channelName)
            putExtra(EXTRA_IS_HARDCORE, isHardcore)
        }
        application.sendBroadcast(intent)
    }

    private suspend fun clearSessionAndBroadcast() {
        preferencesRepository.clearActiveSession()
        broadcastSessionChanged(null, null, false)
    }

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
        registerProcessLifecycleObserver()
    }

    private fun registerProcessLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                val session = _activeSession.value ?: return
                Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | App going to background with active session")
            }
        })
    }

    suspend fun checkOrphanedSession() {
        val orphaned = preferencesRepository.getPersistedSession() ?: return
        Logger.warn(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Detected orphaned session from ${orphaned.startTime}")

        try {
            val game = gameDao.getById(orphaned.gameId)
            if (game == null) {
                Logger.warn(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Game not found, clearing session")
                clearSessionAndBroadcast()
                return
            }

            val emulatorId = emulatorResolver.resolveEmulatorId(orphaned.emulatorPackage)
            if (emulatorId == null) {
                Logger.warn(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Cannot resolve emulator ID | package=${orphaned.emulatorPackage}")
                clearSessionAndBroadcast()
                return
            }

            val savePath = saveSyncRepository.get().discoverSavePath(
                emulatorId = emulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedTitleId = game.titleId,
                coreName = orphaned.coreName,
                emulatorPackage = orphaned.emulatorPackage,
                gameId = orphaned.gameId
            )

            if (savePath != null) {
                val activeChannel = if (orphaned.isHardcore) null else orphaned.channelName
                val cacheResult = saveCacheManager.get().cacheCurrentSave(
                    gameId = orphaned.gameId,
                    emulatorId = emulatorId,
                    savePath = savePath,
                    channelName = activeChannel,
                    isLocked = false,
                    isHardcore = orphaned.isHardcore,
                    skipDuplicateCheck = false
                )
                when (cacheResult) {
                    is SaveCacheManager.CacheResult.Created -> {
                        gameDao.updateActiveSaveTimestamp(orphaned.gameId, cacheResult.timestamp)
                        gameDao.updateActiveSaveApplied(orphaned.gameId, false)
                        Logger.info(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Recovery backup created | path=$savePath")
                    }
                    is SaveCacheManager.CacheResult.Duplicate -> {
                        Logger.info(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Save unchanged (already backed up)")
                    }
                    is SaveCacheManager.CacheResult.Failed -> {
                        Logger.warn(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Failed to create recovery backup")
                    }
                }

                // Sync to RomM after caching
                if (cacheResult !is SaveCacheManager.CacheResult.Duplicate) {
                    val syncResult = syncSaveOnSessionEndUseCase.get()(
                        orphaned.gameId,
                        orphaned.emulatorPackage,
                        orphaned.startTime.toEpochMilli(),
                        orphaned.coreName,
                        orphaned.isHardcore,
                        channelName = orphaned.channelName
                    )
                    when (syncResult) {
                        is SyncSaveOnSessionEndUseCase.Result.Uploaded -> {
                            Logger.info(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Synced to RomM")
                            notificationManager.show(
                                title = "Save Uploaded",
                                subtitle = game.title,
                                type = NotificationType.SUCCESS,
                                imagePath = game.coverPath,
                                duration = NotificationDuration.MEDIUM,
                                key = "sync-${orphaned.gameId}",
                                immediate = true
                            )
                        }
                        is SyncSaveOnSessionEndUseCase.Result.Queued -> {
                            Logger.info(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Queued for sync")
                        }
                        is SyncSaveOnSessionEndUseCase.Result.Error -> {
                            Logger.error(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Sync failed: ${syncResult.message}")
                        }
                        else -> {}
                    }
                }
            } else {
                Logger.warn(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | No save path found for recovery")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Recovery failed", e)
        } finally {
            clearSessionAndBroadcast()
        }
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
            Logger.debug(TAG, "Screen ON - resuming active time tracking")
        }
    }

    private fun onScreenOff() {
        if (isScreenOn && lastScreenOnTime != null) {
            isScreenOn = false
            val elapsed = Duration.between(lastScreenOnTime, Instant.now())
            screenOnDuration = screenOnDuration.plus(elapsed)
            lastScreenOnTime = null
            Logger.debug(TAG, "Screen OFF - paused after ${elapsed.toMinutes()} minutes active")
        }
    }

    fun startSession(gameId: Long, emulatorPackage: String, coreName: String? = null, isHardcore: Boolean = false, isNewGame: Boolean = false) {
        screenOnDuration = Duration.ZERO
        lastScreenOnTime = Instant.now()
        isScreenOn = true

        val startTime = Instant.now()
        _activeSession.value = ActiveSession(
            gameId = gameId,
            startTime = startTime,
            emulatorPackage = emulatorPackage,
            coreName = coreName,
            isHardcore = isHardcore,
            isNewGame = isNewGame
        )
        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Session started | emulator=$emulatorPackage, core=$coreName, hardcore=$isHardcore, newGame=$isNewGame")

        scope.launch {
            val game = gameDao.getById(gameId)
            val channelName = if (isHardcore) null else game?.activeSaveChannel

            _activeSession.value = _activeSession.value?.copy(channelName = channelName)

            preferencesRepository.persistActiveSession(
                gameId = gameId,
                emulatorPackage = emulatorPackage,
                startTime = startTime,
                coreName = coreName,
                isHardcore = isHardcore,
                channelName = channelName
            )

            broadcastSessionChanged(gameId, channelName, isHardcore)

            val prefs = preferencesRepository.userPreferences.first()
            if (prefs.saveSyncEnabled) {
                if (channelName != null) {
                    val activeSaveTimestamp = game?.activeSaveTimestamp
                    val latestCache = saveCacheDao.getLatestCasualSaveInChannel(gameId, channelName)
                    val isOnOlderSave = if (activeSaveTimestamp == null) {
                        false
                    } else {
                        latestCache != null && activeSaveTimestamp < latestCache.cachedAt.toEpochMilli()
                    }
                    saveSyncRepository.get().setSessionOnOlderSave(gameId, isOnOlderSave)
                    _activeSession.value = _activeSession.value?.copy(isOnOlderSave = isOnOlderSave)
                    Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | isOnOlderSave=$isOnOlderSave | channel=$channelName | activeSaveTs=${activeSaveTimestamp} | latestCacheTs=${latestCache?.cachedAt?.toEpochMilli()} | latestCacheId=${latestCache?.id}")
                }
                startGameSessionService(gameId, emulatorPackage, coreName, isHardcore, startTime.toEpochMilli())
            }
        }
    }

    private suspend fun startGameSessionService(gameId: Long, emulatorPackage: String, coreName: String?, isHardcore: Boolean, sessionStartTime: Long) {
        val game = gameDao.getById(gameId) ?: return
        val emulatorId = emulatorResolver.resolveEmulatorId(emulatorPackage)

        val savePath = if (emulatorId != null) {
            saveSyncRepository.get().discoverSavePath(
                emulatorId = emulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedTitleId = game.titleId,
                coreName = coreName,
                emulatorPackage = emulatorPackage,
                gameId = gameId
            )
        } else null

        val watchPath = if (savePath != null) {
            val transformedFile = fileAccessLayer.getTransformedFile(savePath)
            if (transformedFile.isDirectory) {
                transformedFile.absolutePath
            } else {
                transformedFile.parentFile?.absolutePath ?: transformedFile.absolutePath
            }
        } else null

        val channelName = if (isHardcore) null else game.activeSaveChannel

        Logger.debug(TAG, "[GameSession] Starting service for gameId=$gameId | watchPath=$watchPath | savePath=$savePath")
        GameSessionService.start(
            context = application,
            watchPath = watchPath,
            savePath = savePath,
            gameId = gameId,
            emulatorId = emulatorId,
            gameTitle = game.title,
            channelName = channelName,
            isHardcore = isHardcore,
            sessionStartTime = sessionStartTime
        )
    }

    suspend fun endSession(): SessionEndResult {
        val session = _activeSession.value ?: run {
            Logger.debug(TAG, "[SaveSync] SESSION | endSession called but no active session")
            clearSessionAndBroadcast()
            GameSessionService.stop(application)
            return SessionEndResult.Skipped
        }
        _activeSession.value = null
        GameSessionService.stop(application)

        val finalScreenOnDuration = calculateFinalScreenOnDuration()
        val sessionDuration = Duration.between(session.startTime, Instant.now())

        Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Session ended | duration=${sessionDuration.seconds}s, screenOnTime=${finalScreenOnDuration.seconds}s, emulator=${session.emulatorPackage}")

        return try {
            val cacheResult = coroutineScope {
                val saveJob = async {
                    recordPlayTime(session, finalScreenOnDuration)
                    markGameIncompleteIfNeeded(session, sessionDuration)
                    syncAndCacheSave(session)
                }

                val stateJob = async {
                    syncStateData(session)
                }

                val result = saveJob.await()
                stateJob.await()
                result
            }

            saveSyncRepository.get().clearSessionOnOlderSave(session.gameId)
            clearSessionAndBroadcast()

            when (cacheResult) {
                is SaveCacheManager.CacheResult.Created -> SessionEndResult.Success
                is SaveCacheManager.CacheResult.Duplicate -> SessionEndResult.Duplicate
                is SaveCacheManager.CacheResult.Failed -> SessionEndResult.Error("Failed to cache save")
                null -> SessionEndResult.Skipped
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Session end failed", e)
            saveSyncRepository.get().clearSessionOnOlderSave(session.gameId)
            clearSessionAndBroadcast()
            SessionEndResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun calculateFinalScreenOnDuration(): Duration {
        val elapsedSinceScreenOn = if (isScreenOn && lastScreenOnTime != null) {
            Duration.between(lastScreenOnTime, Instant.now())
        } else {
            Duration.ZERO
        }
        screenOnDuration = screenOnDuration.plus(elapsedSinceScreenOn)
        return screenOnDuration
    }

    private suspend fun recordPlayTime(session: ActiveSession, screenOnDuration: Duration) {
        val prefs = preferencesRepository.userPreferences.first()
        val hasPermission = permissionHelper.hasUsageStatsPermission(application)
        val canTrackAccurately = prefs.accuratePlayTimeEnabled && hasPermission

        Logger.debug(TAG, "Time tracking check: enabled=${prefs.accuratePlayTimeEnabled}, hasPermission=$hasPermission, canTrack=$canTrackAccurately")

        if (!canTrackAccurately) {
            Logger.debug(TAG, "Skipping play time recording - accurate tracking not enabled or permission missing")
            return
        }

        val seconds = screenOnDuration.toMillis() / 1000
        val minutes = ((seconds + 30) / 60).toInt()
        if (minutes <= 0) {
            Logger.debug(TAG, "Session too short to record: ${seconds}s")
            return
        }

        gameDao.addPlayTime(session.gameId, minutes)
        gameDao.getById(session.gameId)?.let { updatedGame ->
            gameUpdateBus.emit(GameUpdateBus.GameUpdate(
                gameId = session.gameId,
                playTimeMinutes = updatedGame.playTimeMinutes
            ))
        }
        Logger.debug(TAG, "Added $minutes minutes of active play time for game ${session.gameId} (${seconds}s)")
    }

    private suspend fun markGameIncompleteIfNeeded(session: ActiveSession, sessionDuration: Duration) {
        if (sessionDuration.seconds < MIN_PLAY_SECONDS_FOR_COMPLETION) return

        val game = gameDao.getById(session.gameId) ?: return
        if (game.rommId == null || game.status != null) return

        romMRepository.get().updateUserStatus(session.gameId, "incomplete")
        gameUpdateBus.emit(GameUpdateBus.GameUpdate(
            gameId = session.gameId,
            status = "incomplete"
        ))
        Logger.debug(TAG, "Marked game ${session.gameId} as Incomplete after ${sessionDuration.seconds}s session")
    }

    private suspend fun syncAndCacheSave(session: ActiveSession): SaveCacheManager.CacheResult? {
        val cacheResult = cacheCurrentSave(session)

        if (cacheResult is SaveCacheManager.CacheResult.Failed || cacheResult == null) {
            return cacheResult
        }

        val game = gameDao.getById(session.gameId)
        val result = syncSaveOnSessionEndUseCase.get()(
            session.gameId,
            session.emulatorPackage,
            session.startTime.toEpochMilli(),
            session.coreName,
            session.isHardcore,
            channelName = session.channelName
        )

        if (result is SyncSaveOnSessionEndUseCase.Result.Uploaded) {
            val activeChannel = if (session.isHardcore) null else session.channelName
            linkCacheToServer(session.gameId, activeChannel, result)
        }

        handleSaveSyncResult(session, game, result)
        return cacheResult
    }

    private suspend fun linkCacheToServer(
        gameId: Long,
        channelName: String?,
        uploadResult: SyncSaveOnSessionEndUseCase.Result.Uploaded
    ) {
        val rommSaveId = uploadResult.rommSaveId ?: return

        val cacheEntry = if (channelName != null) {
            saveCacheDao.getMostRecentInChannel(gameId, channelName)
        } else {
            saveCacheDao.getMostRecent(gameId)
        } ?: return

        saveCacheDao.updateRommSaveId(cacheEntry.id, rommSaveId)

        val serverTimestamp = uploadResult.serverTimestamp
        if (serverTimestamp != null) {
            saveCacheDao.updateCachedAt(cacheEntry.id, serverTimestamp)
            gameDao.updateActiveSaveTimestamp(gameId, serverTimestamp.toEpochMilli())
        }

        if (channelName != null) {
            saveCacheDao.clearDirtyFlagForChannel(gameId, channelName, excludeId = -1)
        } else {
            saveCacheDao.clearAllDirtyFlags(gameId)
        }

        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Linked cache id=${cacheEntry.id} to rommSaveId=$rommSaveId | channel=$channelName")
    }

    private suspend fun handleSaveSyncResult(
        session: ActiveSession,
        game: com.nendo.argosy.data.local.entity.GameEntity?,
        result: SyncSaveOnSessionEndUseCase.Result
    ) {
        when (result) {
            is SyncSaveOnSessionEndUseCase.Result.Conflict -> {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Sync result: CONFLICT | local=${result.localTimestamp}, server=${result.serverTimestamp}")
                _conflictEvents.emit(
                    SaveConflictEvent(
                        gameId = result.gameId,
                        emulatorId = result.emulatorId,
                        channelName = result.channelName,
                        localTimestamp = result.localTimestamp,
                        serverTimestamp = result.serverTimestamp
                    )
                )
            }
            is SyncSaveOnSessionEndUseCase.Result.Uploaded -> {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Sync result: UPLOADED")
                notificationManager.show(
                    title = "Save Uploaded",
                    subtitle = game?.title,
                    type = NotificationType.SUCCESS,
                    imagePath = game?.coverPath,
                    duration = NotificationDuration.MEDIUM,
                    key = "sync-${session.gameId}",
                    immediate = true
                )
            }
            is SyncSaveOnSessionEndUseCase.Result.Queued -> {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Sync result: QUEUED")
            }
            is SyncSaveOnSessionEndUseCase.Result.NoSaveFound -> {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Sync result: NO_SAVE_FOUND")
            }
            is SyncSaveOnSessionEndUseCase.Result.NotConfigured -> {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Sync result: NOT_CONFIGURED")
            }
            is SyncSaveOnSessionEndUseCase.Result.Error -> {
                Logger.error(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Sync result: ERROR | ${result.message}")
                notificationManager.show(
                    title = "Upload Failed",
                    subtitle = "${game?.title ?: "Save"}: ${result.message}",
                    type = NotificationType.ERROR,
                    imagePath = game?.coverPath,
                    duration = NotificationDuration.MEDIUM,
                    key = "sync-${session.gameId}",
                    immediate = true
                )
            }
        }
    }

    private suspend fun syncStateData(session: ActiveSession) {
        val result = syncStatesOnSessionEndUseCase.get()(
            session.gameId,
            session.emulatorPackage
        )
        when (result) {
            is StateSyncResult.Cached -> Logger.debug(TAG, "Cached ${result.count} states for game ${session.gameId}")
            is StateSyncResult.NoStatesFound -> Logger.debug(TAG, "No states found for game ${session.gameId}")
            is StateSyncResult.NotConfigured -> Logger.debug(TAG, "State caching not configured for game ${session.gameId}")
            is StateSyncResult.Error -> Logger.error(TAG, "State sync error: ${result.message}")
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
                    scope.launch { endSession() }
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

    fun markFlashReturn() {
        val session = _activeSession.value ?: return
        _activeSession.value = session.copy(flashReturnCount = session.flashReturnCount + 1)
        Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Flash return marked (count=${session.flashReturnCount + 1})")
    }

    fun cancelSession() {
        val session = _activeSession.value ?: return
        _activeSession.value = null
        GameSessionService.stop(application)
        Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Cancelled (no backup)")
        scope.launch { clearSessionAndBroadcast() }
    }

    fun forceStopService() {
        GameSessionService.stop(application)
        scope.launch { clearSessionAndBroadcast() }
        Logger.debug(TAG, "[SaveSync] SESSION | Force stopped service (no active session)")
    }

    fun canResumeSession(gameId: Long): Boolean {
        val session = _activeSession.value
        if (session == null) {
            Logger.debug(TAG, "canResumeSession($gameId): no active session")
            return false
        }
        if (session.gameId != gameId) {
            Logger.debug(TAG, "canResumeSession($gameId): gameId mismatch (active=${session.gameId})")
            return false
        }
        val inForeground = permissionHelper.isPackageInForeground(application, session.emulatorPackage, withinMs = 300_000)
        Logger.debug(TAG, "canResumeSession($gameId): emulator=${session.emulatorPackage}, inForeground=$inForeground")
        return inForeground
    }

    private suspend fun cacheCurrentSave(session: ActiveSession): SaveCacheManager.CacheResult? {
        try {
            val game = gameDao.getById(session.gameId) ?: return null

            val emulatorId = emulatorResolver.resolveEmulatorId(session.emulatorPackage)
            if (emulatorId == null) {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Cannot resolve emulator ID | package=${session.emulatorPackage}")
                return null
            }

            val savePath = saveSyncRepository.get().discoverSavePath(
                emulatorId = emulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedTitleId = game.titleId,
                coreName = session.coreName,
                emulatorPackage = session.emulatorPackage,
                gameId = session.gameId
            )

            if (savePath != null) {
                val activeChannel = if (session.isHardcore) null else session.channelName
                val cacheResult = saveCacheManager.get().cacheCurrentSave(
                    gameId = session.gameId,
                    emulatorId = emulatorId,
                    savePath = savePath,
                    channelName = activeChannel,
                    isLocked = false,
                    isHardcore = session.isHardcore,
                    skipDuplicateCheck = false
                )
                when (cacheResult) {
                    is SaveCacheManager.CacheResult.Created -> {
                        gameDao.updateActiveSaveTimestamp(session.gameId, cacheResult.timestamp)
                        gameDao.updateActiveSaveApplied(session.gameId, false)
                        Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Cached local save | path=$savePath, channel=$activeChannel")
                    }
                    is SaveCacheManager.CacheResult.Duplicate -> {
                        Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Save unchanged (duplicate hash), keeping active timestamp | path=$savePath")
                    }
                    is SaveCacheManager.CacheResult.Failed -> {
                        Logger.warn(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Failed to cache save | path=$savePath")
                    }
                }
                return cacheResult
            } else {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | No save path found for caching | emulator=$emulatorId")
                return null
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Failed to cache save", e)
            return SaveCacheManager.CacheResult.Failed
        }
    }

}
