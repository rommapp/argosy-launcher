package com.nendo.argosy.data.emulator

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import com.nendo.argosy.domain.usecase.state.StateSyncResult
import com.nendo.argosy.domain.usecase.state.SyncStatesOnSessionEndUseCase
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.event.GameUpdateBus
import com.nendo.argosy.util.PermissionHelper
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
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
    val isOnOlderSave: Boolean = false,
    val isNetplayGuest: Boolean = false
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
    val serverTimestamp: Instant,
    val serverDeviceName: String? = null
)

@Singleton
class PlaySessionTracker @Inject constructor(
    private val application: Application,
    private val gameDao: GameDao,
    private val playSessionDao: PlaySessionDao,
    private val saveCacheDao: SaveCacheDao,
    private val pendingSyncQueueDao: com.nendo.argosy.data.local.dao.PendingSyncQueueDao,
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
    private val fileAccessLayer: FileAccessLayer,
    private val socialRepository: dagger.Lazy<SocialRepository>,
    private val saveRecoveryGate: com.nendo.argosy.data.sync.SaveRecoveryGate
) {
    companion object {
        private const val TAG = "PlaySessionTracker"
        private const val MIN_PLAY_SECONDS_FOR_COMPLETION = 20
    }
    private val scope = SafeCoroutineScope(Dispatchers.IO, "PlaySessionTracker")
    private val sessionStateStore by lazy { SessionStateStore(application) }
    private val endingSession = AtomicBoolean(false)
    private var backgroundTimeoutJob: Job? = null
    private var emulatorBackgroundJob: Job? = null

    private fun broadcastSessionChanged(gameId: Long?, channelName: String?, isHardcore: Boolean) {
        // Write to SharedPreferences for companion process to read on startup
        if (gameId != null && gameId > 0) {
            val startMillis = _activeSession.value?.startTime?.toEpochMilli()
                ?: System.currentTimeMillis()
            val emulatorPkg = _activeSession.value?.emulatorPackage
            sessionStateStore.setActiveSession(gameId, channelName, isHardcore, startMillis, emulatorPkg)
        } else {
            sessionStateStore.clearSession()
        }

        DualScreenManagerHolder.instance?.onSessionChanged(gameId ?: -1L, isHardcore, channelName)
    }

    private suspend fun clearSessionAndBroadcast() {
        DualScreenManagerHolder.instance?.setEmulatorDisplay(null)
        preferencesRepository.clearActiveSession()
        broadcastSessionChanged(null, null, false)
    }

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()
    val hasActiveSession: StateFlow<Boolean> = _activeSession
        .map { it != null }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    private val _conflictEvents = MutableSharedFlow<SaveConflictEvent>()
    val conflictEvents: SharedFlow<SaveConflictEvent> = _conflictEvents.asSharedFlow()

    private var wasInBackground = false
    private var lastPauseTime: Instant? = null

    private var lastScreenOnTime: Instant? = null
    private var isScreenOn = true
    private var lastScreenOffTime: Instant? = null
    private var marathonSegmentDuration: Duration = Duration.ZERO
    private var longestMarathonSegment: Duration = Duration.ZERO

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
                backgroundTimeoutJob?.cancel()
                backgroundTimeoutJob = scope.launch {
                    delay(30 * 60 * 1000L)
                    if (_activeSession.value?.gameId == session.gameId) {
                        Logger.warn(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Emergency end after 30-minute background timeout")
                        endSession()
                    }
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                backgroundTimeoutJob?.cancel()
                backgroundTimeoutJob = null
            }
        })
    }

    suspend fun checkOrphanedSession() {
        if (!endingSession.compareAndSet(false, true)) {
            Logger.debug(TAG, "[SaveSync] ORPHAN | Skipping orphan check, endSession is handling recovery")
            saveRecoveryGate.markComplete()
            return
        }
        try {
        val orphaned = preferencesRepository.getPersistedSession() ?: return
        Logger.warn(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Detected orphaned session from ${orphaned.startTime}")

        val endTime = Instant.now()
        val sessionDuration = Duration.between(orphaned.startTime, endTime)
        if (sessionDuration.seconds >= MIN_PLAY_SECONDS_FOR_COMPLETION) {
            try {
                val orphanedGame = gameDao.getById(orphaned.gameId)
                val prefs = preferencesRepository.userPreferences.first()
                val deviceId = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
                val totalMs = sessionDuration.toMillis()
                val (activePlayMs, standbyMs) = resolveActivePlayTime(orphaned.emulatorPackage, orphaned.startTime, endTime, totalMs)

                playSessionDao.insert(
                    PlaySessionEntity(
                        userId = prefs.socialUserId,
                        gameId = orphaned.gameId,
                        igdbId = orphanedGame?.igdbId,
                        gameTitle = orphanedGame?.title ?: "Unknown",
                        platformSlug = orphanedGame?.platformSlug ?: "unknown",
                        startTime = orphaned.startTime,
                        endTime = endTime,
                        continued = false,
                        deviceId = deviceId,
                        deviceManufacturer = Build.MANUFACTURER,
                        deviceModel = Build.MODEL,
                        activePlayMs = activePlayMs,
                        standbyMs = standbyMs
                    )
                )
                Logger.info(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Play session entity created | duration=${sessionDuration.seconds}s, activePlayMs=$activePlayMs, standbyMs=$standbyMs")
                socialRepository.get().syncPlaySessions()

                recordPlayTime(
                    ActiveSession(
                        gameId = orphaned.gameId,
                        startTime = orphaned.startTime,
                        emulatorPackage = orphaned.emulatorPackage,
                        coreName = orphaned.coreName,
                        isHardcore = orphaned.isHardcore
                    ),
                    Duration.ofMillis(activePlayMs)
                )
            } catch (e: Exception) {
                Logger.error(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Failed to create play session entity", e)
            }
        } else {
            Logger.debug(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Too short for play session (${sessionDuration.seconds}s)")
        }

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
                cachedSaveId = game.saveId ?: game.titleId,
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
                    val syncResult = syncSaveOnSessionEndUseCase.get()(orphaned)
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
        } finally {
            endingSession.set(false)
            saveRecoveryGate.markComplete()
        }
    }

    private suspend fun recoverOrphanedPlaySession(stopService: Boolean): Boolean {
        val orphaned = preferencesRepository.getPersistedSession() ?: return false
        val endTime = Instant.now()
        val sessionDuration = Duration.between(orphaned.startTime, endTime)

        if (sessionDuration.seconds < MIN_PLAY_SECONDS_FOR_COMPLETION) {
            Logger.debug(TAG, "[SaveSync] SESSION RECOVER gameId=${orphaned.gameId} | Too short (${sessionDuration.seconds}s), skipping")
            clearSessionAndBroadcast()
            if (stopService) GameSessionService.stop(application)
            return false
        }

        val totalMs = sessionDuration.toMillis()
        val (activePlayMs, standbyMs) = resolveActivePlayTime(orphaned.emulatorPackage, orphaned.startTime, endTime, totalMs)

        Logger.info(TAG, "[SaveSync] SESSION RECOVER gameId=${orphaned.gameId} | Recovering orphaned session | startTime=${orphaned.startTime}, duration=${sessionDuration.seconds}s, activePlayMs=$activePlayMs, standbyMs=$standbyMs")

        try {
            val game = gameDao.getById(orphaned.gameId)
            val prefs = preferencesRepository.userPreferences.first()
            val deviceId = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

            playSessionDao.insert(
                PlaySessionEntity(
                    userId = prefs.socialUserId,
                    gameId = orphaned.gameId,
                    igdbId = game?.igdbId,
                    gameTitle = game?.title ?: "Unknown",
                    platformSlug = game?.platformSlug ?: "unknown",
                    startTime = orphaned.startTime,
                    endTime = endTime,
                    continued = false,
                    deviceId = deviceId,
                    deviceManufacturer = Build.MANUFACTURER,
                    deviceModel = Build.MODEL,
                    activePlayMs = activePlayMs,
                    standbyMs = standbyMs
                )
            )
            Logger.info(TAG, "[SaveSync] SESSION RECOVER gameId=${orphaned.gameId} | Play session entity created | activePlayMs=$activePlayMs")
            socialRepository.get().syncPlaySessions()
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] SESSION RECOVER gameId=${orphaned.gameId} | Failed to create play session entity", e)
        }

        try {
            recordPlayTime(
                ActiveSession(
                    gameId = orphaned.gameId,
                    startTime = orphaned.startTime,
                    emulatorPackage = orphaned.emulatorPackage,
                    coreName = orphaned.coreName,
                    isHardcore = orphaned.isHardcore
                ),
                Duration.ofMillis(activePlayMs)
            )
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] SESSION RECOVER gameId=${orphaned.gameId} | Failed to record play time", e)
        }

        clearSessionAndBroadcast()
        if (stopService) GameSessionService.stop(application)
        return true
    }

    private fun resolveActivePlayTime(
        emulatorPackage: String,
        startTime: Instant,
        endTime: Instant,
        totalMs: Long
    ): Pair<Long, Long> {
        // Built-in libretro uses a synthetic package name; query the launcher's
        // own package for UsageStats since the activity runs in this process.
        val statsPackage = if (emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE) {
            application.packageName
        } else {
            emulatorPackage
        }
        val durations = permissionHelper.getSessionDurations(
            application,
            statsPackage,
            startTime.toEpochMilli(),
            endTime.toEpochMilli()
        )
        val foregroundMs = if (durations.foregroundMs >= 0) durations.foregroundMs else totalMs
        val screenOnMs = if (durations.screenOnMs >= 0) durations.screenOnMs else totalMs
        val active = minOf(foregroundMs, screenOnMs).coerceAtMost(totalMs)
        val standby = (totalMs - active).coerceAtLeast(0)
        return active to standby
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

            val offTime = lastScreenOffTime
            if (offTime != null) {
                val gap = Duration.between(offTime, Instant.now())
                if (gap.toMinutes() > 10) {
                    if (marathonSegmentDuration > longestMarathonSegment) {
                        longestMarathonSegment = marathonSegmentDuration
                    }
                    marathonSegmentDuration = Duration.ZERO
                }
            }
            lastScreenOffTime = null

            Logger.debug(TAG, "Screen ON - resuming active time tracking")
        }
    }

    private fun onScreenOff() {
        if (isScreenOn && lastScreenOnTime != null) {
            isScreenOn = false
            val elapsed = Duration.between(lastScreenOnTime, Instant.now())
            marathonSegmentDuration = marathonSegmentDuration.plus(elapsed)
            lastScreenOnTime = null
            lastScreenOffTime = Instant.now()
            Logger.debug(TAG, "Screen OFF - paused after ${elapsed.toMinutes()} minutes active")
        }
    }

    fun onEmulatorForegrounded() {
        if (_activeSession.value == null) return
        emulatorBackgroundJob?.cancel()
        emulatorBackgroundJob = null
        Logger.debug(TAG, "Emulator FOREGROUNDED (cancelled pending session end)")
    }

    fun onEmulatorBackgrounded() {
        val session = _activeSession.value ?: return
        Logger.debug(TAG, "Emulator BACKGROUNDED -- scheduling session end in 30s")
        emulatorBackgroundJob?.cancel()
        emulatorBackgroundJob = scope.launch {
            delay(30_000L)
            Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Ending session via onEmulatorBackgrounded (bypasses GameLaunchDelegate conflict UI)")
            Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | isOnOlderSave=${session.isOnOlderSave}, channel=${session.channelName}, emulator=${session.emulatorPackage}")
            endSession()
        }
    }

    fun startSession(gameId: Long, emulatorPackage: String, coreName: String? = null, isHardcore: Boolean = false, isNewGame: Boolean = false, isNetplayGuest: Boolean = false) {
        emulatorBackgroundJob?.cancel()
        emulatorBackgroundJob = null
        endingSession.set(false)
        lastScreenOnTime = Instant.now()
        isScreenOn = true
        lastScreenOffTime = null
        marathonSegmentDuration = Duration.ZERO
        longestMarathonSegment = Duration.ZERO

        val startTime = Instant.now()
        _activeSession.value = ActiveSession(
            gameId = gameId,
            startTime = startTime,
            emulatorPackage = emulatorPackage,
            coreName = coreName,
            isHardcore = isHardcore,
            isNewGame = isNewGame,
            isNetplayGuest = isNetplayGuest
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

            val displayId = if (sessionStateStore.isRolesSwapped()) {
                android.view.Display.DEFAULT_DISPLAY + 1
            } else {
                android.view.Display.DEFAULT_DISPLAY
            }
            DualScreenManagerHolder.instance?.setEmulatorDisplay(displayId)

            broadcastSessionChanged(gameId, channelName, isHardcore)

            if (isNewGame && game != null && game.playCount <= 1) {
                socialRepository.get().emitStartedPlaying(
                    igdbId = game.igdbId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug
                )
            }

            val prefs = preferencesRepository.userPreferences.first()
            if (prefs.saveSyncEnabled && channelName != null) {
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

    private suspend fun startGameSessionService(gameId: Long, emulatorPackage: String, coreName: String?, isHardcore: Boolean, sessionStartTime: Long) {
        val game = gameDao.getById(gameId) ?: return
        val emulatorId = emulatorResolver.resolveEmulatorId(emulatorPackage)

        val savePath = if (emulatorId != null && SavePathRegistry.getConfig(emulatorId) != null) {
            saveSyncRepository.get().discoverSavePath(
                emulatorId = emulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedSaveId = game.saveId ?: game.titleId,
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

        val emulatorDisplayId = if (sessionStateStore.isRolesSwapped()) {
            android.view.Display.DEFAULT_DISPLAY + 1
        } else {
            android.view.Display.DEFAULT_DISPLAY
        }

        Logger.debug(TAG, "[GameSession] Starting service for gameId=$gameId | watchPath=$watchPath | savePath=$savePath | displayId=$emulatorDisplayId")
        GameSessionService.start(
            context = application,
            watchPath = watchPath,
            savePath = savePath,
            gameId = gameId,
            emulatorId = emulatorId,
            emulatorPackage = emulatorPackage,
            gameTitle = game.title,
            channelName = channelName,
            isHardcore = isHardcore,
            sessionStartTime = sessionStartTime,
            emulatorDisplayId = emulatorDisplayId
        )
    }

    suspend fun endSession(stopService: Boolean = true, skipSaveSync: Boolean = false): SessionEndResult {
        val pendingJob = emulatorBackgroundJob
        emulatorBackgroundJob = null
        if (pendingJob != null && pendingJob != coroutineContext[Job]) {
            pendingJob.cancel()
        }
        if (!endingSession.compareAndSet(false, true)) {
            Logger.debug(TAG, "[SaveSync] SESSION | endSession already in progress, skipping")
            return SessionEndResult.Skipped
        }

        try {
            val session = _activeSession.value ?: run {
                Logger.debug(TAG, "[SaveSync] SESSION | endSession called but no active session, checking persisted session")
                val recovered = recoverOrphanedPlaySession(stopService)
                if (!recovered) {
                    clearSessionAndBroadcast()
                    if (stopService) GameSessionService.stop(application)
                }
                return if (recovered) SessionEndResult.Success else SessionEndResult.Skipped
            }
            _activeSession.value = null
            if (stopService) GameSessionService.stop(application)

            val endTime = Instant.now()
            val sessionDuration = Duration.between(session.startTime, endTime)
            val totalMs = sessionDuration.toMillis()
            val (activePlayMs, standbyMs) = resolveActivePlayTime(
                session.emulatorPackage, session.startTime, endTime, totalMs
            )
            Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Session ended | duration=${sessionDuration.seconds}s, activePlayMs=$activePlayMs, standbyMs=$standbyMs, emulator=${session.emulatorPackage}")
            try {
                val game = gameDao.getById(session.gameId)
                val prefs = preferencesRepository.userPreferences.first()
                val deviceId = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
                playSessionDao.insert(
                    PlaySessionEntity(
                        userId = prefs.socialUserId,
                        gameId = session.gameId,
                        igdbId = game?.igdbId,
                        gameTitle = game?.title ?: "Unknown",
                        platformSlug = game?.platformSlug ?: "unknown",
                        startTime = session.startTime,
                        endTime = endTime,
                        continued = false,
                        deviceId = deviceId,
                        deviceManufacturer = Build.MANUFACTURER,
                        deviceModel = Build.MODEL,
                        activePlayMs = activePlayMs,
                        standbyMs = standbyMs
                    )
                )
                Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Play session entity created | activePlayMs=$activePlayMs, standbyMs=$standbyMs")
                socialRepository.get().syncPlaySessions()
            } catch (e: Exception) {
                Logger.error(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Failed to create play session entity", e)
            }

            if (isScreenOn) {
                marathonSegmentDuration = marathonSegmentDuration.plus(
                    Duration.between(lastScreenOnTime ?: session.startTime, Instant.now())
                )
            }
            if (marathonSegmentDuration > longestMarathonSegment) {
                longestMarathonSegment = marathonSegmentDuration
            }
            val marathonMins = longestMarathonSegment.toMinutes()
            if (marathonMins >= 240) {
                try {
                    val game = gameDao.getById(session.gameId)
                    if (game != null) {
                        socialRepository.get().emitMarathonSession(
                            igdbId = game.igdbId,
                            gameTitle = game.title,
                            durationMins = marathonMins.toInt(),
                            platformSlug = game.platformSlug
                        )
                    }
                } catch (e: Exception) {
                    Logger.warn(TAG, "Failed to emit marathon session event", e)
                }
            }

            val effectiveSkipSaveSync = skipSaveSync || session.isNetplayGuest
            return try {
                val cacheResult = coroutineScope {
                    val saveJob = async {
                        recordPlayTime(session, Duration.ofMillis(activePlayMs))
                        markGameIncompleteIfNeeded(session, sessionDuration)
                        if (!effectiveSkipSaveSync) syncAndCacheSave(session) else null
                    }

                    val stateJob = async {
                        if (!effectiveSkipSaveSync) syncStateData(session)
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
        } finally {
            endingSession.set(false)
        }
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
        saveCacheDao.clearAllDirtyFlags(session.gameId)

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
            val uploadedCacheId = when (cacheResult) {
                is SaveCacheManager.CacheResult.Created -> cacheResult.cacheId
                is SaveCacheManager.CacheResult.Duplicate -> cacheResult.cacheId
                else -> null
            }
            linkCacheToServer(session.gameId, activeChannel, result, uploadedCacheId)
            pendingSyncQueueDao.deleteActiveByGameAndType(session.gameId, com.nendo.argosy.data.local.entity.SyncType.SAVE_FILE)
        }

        handleSaveSyncResult(session, game, result)
        return cacheResult
    }

    private suspend fun linkCacheToServer(
        gameId: Long,
        channelName: String?,
        uploadResult: SyncSaveOnSessionEndUseCase.Result.Uploaded,
        uploadedCacheId: Long?
    ) {
        val rommSaveId = uploadResult.rommSaveId ?: return

        val cacheEntry = uploadedCacheId?.let { saveCacheDao.getById(it) }
            ?: run {
                Logger.warn(TAG, "[SaveSync] SESSION gameId=$gameId | linkCacheToServer: no uploadedCacheId, skipping metadata link to avoid clobbering an unrelated cache")
                com.nendo.argosy.util.SaveDebugLogger.logLinkCache(
                    gameId = gameId,
                    channel = channelName,
                    cacheId = null,
                    rommSaveId = rommSaveId,
                    serverTimestamp = uploadResult.serverTimestamp,
                    method = "skipped:noUploadedCacheId"
                )
                if (channelName != null) {
                    saveCacheDao.clearDirtyFlagForChannel(gameId, channelName, excludeId = -1)
                } else {
                    saveCacheDao.clearAllDirtyFlags(gameId)
                }
                return
            }

        saveCacheDao.updateRommSaveId(cacheEntry.id, rommSaveId)

        val serverTimestamp = uploadResult.serverTimestamp
        if (serverTimestamp != null) {
            saveCacheDao.updateCachedAt(cacheEntry.id, serverTimestamp)
            gameDao.updateActiveSaveTimestamp(gameId, serverTimestamp.toEpochMilli())
        }

        com.nendo.argosy.util.SaveDebugLogger.logLinkCache(
            gameId = gameId,
            channel = channelName,
            cacheId = cacheEntry.id,
            rommSaveId = rommSaveId,
            serverTimestamp = serverTimestamp,
            method = "byUploadedCacheId"
        )

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
                        serverTimestamp = result.serverTimestamp,
                        serverDeviceName = result.serverDeviceName
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
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun getSessionDuration(): Duration? {
        val session = _activeSession.value ?: return null
        return Duration.between(session.startTime, Instant.now())
    }

    fun cancelSession() {
        if (endingSession.get()) {
            Logger.debug(TAG, "[SaveSync] SESSION | cancelSession skipped: endSession already in progress")
            return
        }
        val session = _activeSession.value ?: return
        _activeSession.value = null
        GameSessionService.stop(application)
        Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Cancelled (no backup)")
        scope.launch { clearSessionAndBroadcast() }
    }

    fun endSessionInBackground(skipSaveSync: Boolean = false) {
        scope.launch { endSession(skipSaveSync = skipSaveSync) }
    }

    /** Cache the current session's save to local Room with needsRemoteSync=true, synchronously. Called from the in-game Quit handler so the save is persisted before libretro core teardown begins -- teardown can be slow enough on some cores (PSP shader cache, etc.) to trigger an OS-imposed process kill, taking endSession's coroutine with it. The upload itself is handled later by the sync coordinator draining the dirty cache row. */
    suspend fun cacheCurrentSessionForQuit(): SaveCacheManager.CacheResult? {
        val session = _activeSession.value ?: return null
        val game = gameDao.getById(session.gameId) ?: return null
        val emulatorId = emulatorResolver.resolveEmulatorId(session.emulatorPackage)
            ?: return null
        val savePath = saveSyncRepository.get().discoverSavePath(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = session.coreName,
            emulatorPackage = session.emulatorPackage,
            gameId = session.gameId
        ) ?: return null
        val activeChannel = if (session.isHardcore) null else session.channelName
        return try {
            saveCacheManager.get().cacheCurrentSave(
                gameId = session.gameId,
                emulatorId = emulatorId,
                savePath = savePath,
                channelName = activeChannel,
                isLocked = false,
                isHardcore = session.isHardcore,
                skipDuplicateCheck = false,
                needsRemoteSync = true
            ).also { result ->
                Logger.debug(TAG, "[SaveSync] QUIT gameId=${session.gameId} | Pre-quit cache result=${result::class.simpleName}")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] QUIT gameId=${session.gameId} | Pre-quit cache failed", e)
            SaveCacheManager.CacheResult.Failed
        }
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
                cachedSaveId = game.saveId ?: game.titleId,
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
