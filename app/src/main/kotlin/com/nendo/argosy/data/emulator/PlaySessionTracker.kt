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
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import com.nendo.argosy.data.preferences.PersistedSession
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import com.nendo.argosy.domain.usecase.state.StateSyncResult
import com.nendo.argosy.domain.usecase.state.SyncStatesOnSessionEndUseCase
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.event.GameUpdateBus
import com.nendo.argosy.util.PermissionHelper
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val isNetplayGuest: Boolean = false,
    val variantFileId: Long? = null,
    val origin: LaunchOrigin = LaunchOrigin.INTERNAL
)

sealed class SessionEndResult {
    data object Success : SessionEndResult()
    data object Duplicate : SessionEndResult()
    data object Skipped : SessionEndResult()
    data class SaveUnreadable(val dirPath: String, val emulatorId: String) : SessionEndResult()
    data class Error(val message: String) : SessionEndResult()
}

data class SaveConflictEvent(
    val gameId: Long,
    val emulatorId: String,
    val channelName: String?,
    val localTimestamp: Instant,
    val serverTimestamp: Instant,
    val serverDeviceName: String? = null,
    val conflictId: Long? = null,
    val isHardcoreDowngrade: Boolean = false
)

@Singleton
class PlaySessionTracker @Inject constructor(
    private val application: Application,
    private val gameDao: GameDao,
    private val overlayWriter: com.nendo.argosy.data.repository.GameUserOverlayWriter,
    private val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository,
    private val playSessionDao: PlaySessionDao,
    private val syncStatesOnSessionEndUseCase: dagger.Lazy<SyncStatesOnSessionEndUseCase>,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val saveSyncRepository: dagger.Lazy<SaveSyncRepository>,
    private val romMRepository: dagger.Lazy<RomMRepository>,
    private val preferencesRepository: UserPreferencesRepository,
    private val permissionHelper: PermissionHelper,
    private val gameUpdateBus: GameUpdateBus,
    private val emulatorResolver: EmulatorResolver,
    private val fileAccessLayer: FileAccessLayer,
    private val socialRepository: dagger.Lazy<SocialRepository>,
    private val saveRecoveryGate: com.nendo.argosy.data.sync.SaveRecoveryGate,
    private val reconcileAchievementsOnSessionEndUseCase: dagger.Lazy<com.nendo.argosy.domain.usecase.achievement.ReconcileAchievementsOnSessionEndUseCase>,
    private val savePathAuthority: com.nendo.argosy.data.emulator.savepath.SavePathAuthority,
    private val sessionSaveFinalizer: SessionSaveFinalizer,
    private val sessionEndNotices: SessionEndNotices,
    private val snapshotRouter: dagger.Lazy<com.nendo.argosy.data.sync.snapshot.SnapshotSyncRouter>,
    private val sessionRecapFactsRecorder: SessionRecapFactsRecorder
) {
    companion object {
        private const val TAG = "PlaySessionTracker"
        private const val MIN_PLAY_SECONDS_FOR_COMPLETION = 20
        private const val MAX_SESSION_RECOVERY_ATTEMPTS = 3
    }
    private val scope = SafeCoroutineScope(Dispatchers.IO, "PlaySessionTracker")
    private val sessionStateStore by lazy { SessionStateStore(application) }
    private val saveObserved = AtomicBoolean(false)
    private val sessionServiceMutex = Mutex()
    private val sessionServiceStarted = AtomicBoolean(false)

    private fun broadcastSessionChanged(gameId: Long?, channelName: String?, isHardcore: Boolean) {
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

    private suspend fun clearSessionAndBroadcast() = releaseSession(keepRecord = false, startedAt = null)

    private suspend fun releaseSession(keepRecord: Boolean, startedAt: Instant?) {
        if (!keepRecord) {
            preferencesRepository.clearActiveSession(startedAt)
        } else if (!preferencesRepository.keepSessionForRetry(MAX_SESSION_RECOVERY_ATTEMPTS, startedAt)) {
            Logger.error(TAG, "[SaveSync] SESSION | Save capture failed $MAX_SESSION_RECOVERY_ATTEMPTS times; dropped the session record")
        }
        val newer = _activeSession.value
        if (newer != null && newer.startTime != startedAt) {
            Logger.debug(TAG, "[SaveSync] SESSION gameId=${newer.gameId} | Newer session live, leaving its broadcast in place")
            return
        }
        DualScreenManagerHolder.instance?.setEmulatorDisplay(null)
        broadcastSessionChanged(null, null, false)
    }

    private suspend fun syncStateDataLogged(gameId: Long, emulatorPackage: String, phase: String) {
        try {
            syncStateData(gameId, emulatorPackage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "[StateSync] $phase gameId=$gameId | State sync failed", e)
        }
    }

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()
    val hasActiveSession: StateFlow<Boolean> = _activeSession
        .map { it != null }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    private val _pendingSessionConflict = MutableStateFlow<SaveConflictEvent?>(null)

    /**
     * The session-end save conflict awaiting the user's answer, or null once answered. Launch paths
     * hold a new launch on this so the next game's own prompts never stack on an unanswered one.
     */
    val pendingSessionConflict: StateFlow<SaveConflictEvent?> = _pendingSessionConflict.asStateFlow()

    fun clearPendingSessionConflict() {
        _pendingSessionConflict.value = null
    }

    private val _externalSessionClosed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Fires once a session with [LaunchOrigin.EXTERNAL] has closed and its session-end save work
     * has returned. No replay: a close nobody is listening to at that moment is not owed later,
     * so a launcher opened by hand afterwards never reacts to it. A save conflict raised by that
     * close may still be waiting in [pendingSessionConflict] when this fires.
     */
    val externalSessionClosed: SharedFlow<Unit> = _externalSessionClosed.asSharedFlow()

    private fun signalSessionClosed(session: ActiveSession) {
        if (session.origin != LaunchOrigin.EXTERNAL) return
        Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | External session closed, releasing the launcher")
        _externalSessionClosed.tryEmit(Unit)
    }

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
    }

    private suspend fun insertPlaySessionIfAbsent(entity: PlaySessionEntity): Long? {
        if (playSessionDao.existsByGameAndStart(entity.gameId, entity.startTime)) {
            Logger.debug(TAG, "[SaveSync] SESSION gameId=${entity.gameId} | Play session already recorded for startTime=${entity.startTime}, skipping duplicate")
            return null
        }
        return playSessionDao.insert(entity)
    }

    private suspend fun recordOrphanedPlaySession(orphaned: PersistedSession, endTime: Instant): Boolean {
        val totalMs = Duration.between(orphaned.startTime, endTime).toMillis()
        val (activePlayMs, standbyMs) = resolveActivePlayTime(orphaned.emulatorPackage, orphaned.startTime, endTime, totalMs)
        return try {
            val game = gameDao.getById(orphaned.gameId)
            val prefs = preferencesRepository.userPreferences.first()
            val deviceId = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
            val recapFacts = sessionRecapFactsRecorder.factsFor(game, orphaned.emulatorPackage, orphaned.startTime, endTime)
            val sessionId = insertPlaySessionIfAbsent(
                PlaySessionEntity(
                    userId = prefs.socialUserId,
                    ownerUserId = prefs.rommUserId,
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
                    standbyMs = standbyMs,
                    raGameId = recapFacts.raGameId,
                    richPresence = recapFacts.richPresence,
                    richPresenceAt = recapFacts.richPresenceAt
                )
            )
            if (sessionId != null) {
                sessionRecapFactsRecorder.onSessionRecorded(
                    scope, sessionId, orphaned.gameId, orphaned.emulatorPackage, recapFacts, orphaned.startTime, endTime
                )
                Logger.info(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Play session entity created | activePlayMs=$activePlayMs, standbyMs=$standbyMs")
                socialRepository.get().syncPlaySessions()
                reconcileAchievementsInBackground(orphaned.gameId)
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
            }
            sessionId != null
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Failed to create play session entity", e)
            false
        }
    }

    private suspend fun recoverOrphanedSave(orphaned: PersistedSession): SessionSaveOutcome {
        val outcome = try {
            finalizeSave(orphaned.toSaveInput())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Recovery failed, keeping the session for the next attempt", e)
            return SessionSaveOutcome.CacheFailed
        }
        Logger.info(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Recovery outcome=${outcome::class.simpleName} settled=${outcome.isSettled}")
        sessionEndNotices.orphanSaveRecovered(orphaned.gameId, outcome)
        return outcome
    }

    suspend fun checkOrphanedSession() {
        if (_activeSession.value != null) {
            saveRecoveryGate.markComplete()
            return
        }
        if (!saveRecoveryGate.tryClaimSessionEnd()) {
            Logger.debug(TAG, "[SaveSync] ORPHAN | Skipping orphan check, endSession is handling recovery")
            saveRecoveryGate.markComplete()
            return
        }
        try {
            if (_activeSession.value != null) return
            val orphaned = preferencesRepository.getPersistedSession() ?: run {
                clearSessionAndBroadcast()
                GameSessionService.stop(application)
                return
            }
            if (isSessionStillOnScreen(orphaned)) {
                resumePersistedSession(orphaned)
                return
            }
            Logger.warn(TAG, "[SaveSync] ORPHAN gameId=${orphaned.gameId} | Detected orphaned session from ${orphaned.startTime}")
            recoverOrphanedPlaySession(stopService = false)
        } finally {
            saveRecoveryGate.releaseSessionEnd()
            saveRecoveryGate.markComplete()
        }
    }

    /**
     * Whether the session written to disk still has its emulator on a screen.
     *
     * This is the one question that separates a launcher process that died under a running game
     * from a player who put the game down: both come back with every in-memory signal cleared and
     * the same record on disk, and only the emulator's own windows say which happened. Silence -
     * no usage access, no events - answers false, so a session that cannot be observed is still
     * treated as ended and its save is still recovered.
     */
    private fun isSessionStillOnScreen(persisted: PersistedSession): Boolean =
        permissionHelper.isPackageOnScreenOrRecent(application, persisted.emulatorPackage)

    /**
     * Adopts a persisted session back into memory rather than closing it out.
     *
     * The record on disk is the authority here: it outlived the process that wrote it, and the game
     * it describes is still being played. Ending it would archive a save mid-play, hand the player's
     * screen back to the launcher, and leave the real end of the session with nothing to record.
     * The save watcher is started again behind the game so the rest of the session still syncs.
     */
    private suspend fun resumePersistedSession(persisted: PersistedSession) {
        Logger.info(
            TAG,
            "[SaveSync] SESSION gameId=${persisted.gameId} | Persisted session still on screen, resuming it | emulator=${persisted.emulatorPackage}"
        )
        saveObserved.set(false)
        sessionServiceStarted.set(false)
        isScreenOn = true
        lastScreenOnTime = Instant.now()
        lastScreenOffTime = null
        marathonSegmentDuration = Duration.ZERO
        longestMarathonSegment = Duration.ZERO

        _activeSession.value = ActiveSession(
            gameId = persisted.gameId,
            startTime = persisted.startTime,
            emulatorPackage = persisted.emulatorPackage,
            coreName = persisted.coreName,
            isHardcore = persisted.isHardcore,
            channelName = persisted.channelName,
            variantFileId = persisted.variantFileId,
            origin = persisted.origin,
            isNetplayGuest = persisted.isNetplayGuest
        )
        broadcastSessionChanged(persisted.gameId, persisted.channelName, persisted.isHardcore)
        sessionServiceMutex.withLock {
            startGameSessionService(
                gameId = persisted.gameId,
                emulatorPackage = persisted.emulatorPackage,
                coreName = persisted.coreName,
                isHardcore = persisted.isHardcore,
                sessionStartTime = persisted.startTime.toEpochMilli()
            )
        }
    }

    private suspend fun recoverOrphanedStates(orphaned: PersistedSession) {
        if (orphaned.variantFileId != null || orphaned.isNetplayGuest) return
        syncStateDataLogged(orphaned.gameId, orphaned.emulatorPackage, "ORPHAN")
    }

    private suspend fun recoverOrphanedPlaySession(stopService: Boolean): Boolean? {
        val orphaned = preferencesRepository.getPersistedSession() ?: return null
        val endTime = Instant.now()
        val longEnough = Duration.between(orphaned.startTime, endTime).seconds >= MIN_PLAY_SECONDS_FOR_COMPLETION

        if (longEnough) {
            recordOrphanedPlaySession(orphaned, endTime)
        } else {
            Logger.debug(TAG, "[SaveSync] SESSION RECOVER gameId=${orphaned.gameId} | Too short for play session (${Duration.between(orphaned.startTime, endTime).seconds}s)")
        }

        recoverOrphanedStates(orphaned)
        val saved = recoverOrphanedSave(orphaned)
        releaseSession(keepRecord = !saved.isSettled, startedAt = orphaned.startTime)
        if (stopService) GameSessionService.stop(application)
        return longEnough
    }

    private fun resolveActivePlayTime(
        emulatorPackage: String,
        startTime: Instant,
        endTime: Instant,
        totalMs: Long
    ): Pair<Long, Long> {
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

    /**
     * Opens a session for a launch that already went through the launch gate. The gate ends any
     * running session first, so a live session here is a caller that skipped it: the call is
     * refused and logged rather than clobbering the running session's play time and save sync.
     */
    fun startSession(
        gameId: Long,
        emulatorPackage: String,
        coreName: String? = null,
        isHardcore: Boolean = false,
        isNewGame: Boolean = false,
        isNetplayGuest: Boolean = false,
        variantFileId: Long? = null,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL
    ) {
        val running = _activeSession.value
        if (running != null) {
            Logger.error(
                TAG,
                "[SaveSync] SESSION gameId=$gameId | startSession refused, session for gameId=${running.gameId} " +
                    "(emulator=${running.emulatorPackage}) is still active; caller must end it through the launch gate first"
            )
            return
        }
        saveObserved.set(false)
        sessionServiceStarted.set(false)
        lastScreenOnTime = Instant.now()
        isScreenOn = true
        lastScreenOffTime = null
        marathonSegmentDuration = Duration.ZERO
        longestMarathonSegment = Duration.ZERO

        val startTime = Instant.now()
        sessionRecapFactsRecorder.onSessionStarted(scope, gameId, emulatorPackage, startTime)
        _activeSession.value = ActiveSession(
            gameId = gameId,
            startTime = startTime,
            emulatorPackage = emulatorPackage,
            coreName = coreName,
            isHardcore = isHardcore,
            isNewGame = isNewGame,
            isNetplayGuest = isNetplayGuest,
            variantFileId = variantFileId,
            origin = origin
        )
        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Session started | emulator=$emulatorPackage, core=$coreName, hardcore=$isHardcore, newGame=$isNewGame, origin=$origin")

        scope.launch {
            sessionServiceMutex.withLock {
                val game = gameDao.getById(gameId)
                val activeSave = activeSaveRepository.getActiveRow(gameId)
                val channelName = if (variantFileId != null) null
                else snapshotRouter.get().sessionChannel(gameId, isHardcore, activeSave?.channelName)

                _activeSession.update { current ->
                    if (current?.startTime == startTime) current.copy(channelName = channelName) else current
                }
                if (_activeSession.value?.startTime != startTime) {
                    Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Session ended before it was persisted")
                    return@withLock
                }

                preferencesRepository.persistActiveSession(
                    gameId = gameId,
                    emulatorPackage = emulatorPackage,
                    startTime = startTime,
                    coreName = coreName,
                    isHardcore = isHardcore,
                    channelName = channelName,
                    variantFileId = variantFileId,
                    origin = origin,
                    isNetplayGuest = isNetplayGuest
                )

                DualScreenManagerHolder.instance?.assignEmulatorDisplayForSessionStart()

                broadcastSessionChanged(gameId, channelName, isHardcore)

                if (isNewGame && game != null && game.playCount <= 1) {
                    socialRepository.get().emitStartedPlaying(
                        igdbId = game.igdbId,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug
                    )
                }

                startGameSessionService(gameId, emulatorPackage, coreName, isHardcore, startTime.toEpochMilli())
            }
        }
    }

    /**
     * Re-tags the running session once RetroAchievements has answered the hardcore request.
     * Every consumer of the flag (session store, companion, watcher service, orphan record) is
     * refreshed here so the save trailer and the upload flag follow the confirmed mode rather
     * than the launch flag. Hardcore saves live outside named channels, so the channel is
     * recomputed exactly as startSession does.
     */
    fun updateSessionHardcore(gameId: Long, isHardcore: Boolean) {
        scope.launch {
            sessionServiceMutex.withLock {
                val session = _activeSession.value ?: return@withLock
                if (session.gameId != gameId || session.isHardcore == isHardcore) return@withLock
                val activeSave = activeSaveRepository.getActiveRow(gameId)
                val channelName = if (session.variantFileId != null) null
                else snapshotRouter.get().sessionChannel(gameId, isHardcore, activeSave?.channelName)
                val updated = session.copy(
                    isHardcore = isHardcore,
                    channelName = channelName
                )
                _activeSession.update { current ->
                    if (current?.startTime == session.startTime) {
                        current.copy(isHardcore = isHardcore, channelName = channelName)
                    } else {
                        current
                    }
                }
                if (_activeSession.value?.startTime != session.startTime) {
                    Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Session ended before the hardcore claim landed")
                    return@withLock
                }
                Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Hardcore claim updated | hardcore=$isHardcore, channel=$channelName")

                preferencesRepository.persistActiveSession(
                    gameId = gameId,
                    emulatorPackage = updated.emulatorPackage,
                    startTime = updated.startTime,
                    coreName = updated.coreName,
                    isHardcore = isHardcore,
                    channelName = channelName,
                    variantFileId = updated.variantFileId,
                    origin = updated.origin,
                    isNetplayGuest = updated.isNetplayGuest
                )
                sessionStateStore.setActiveSession(
                    gameId,
                    channelName,
                    isHardcore,
                    updated.startTime.toEpochMilli(),
                    updated.emulatorPackage
                )
                DualScreenManagerHolder.instance?.onSessionHardcoreChanged(isHardcore, channelName)
                if (sessionServiceStarted.get()) {
                    GameSessionService.updateHardcore(application, isHardcore, channelName)
                }
            }
        }
    }

    private suspend fun startGameSessionService(gameId: Long, emulatorPackage: String, coreName: String?, isHardcore: Boolean, sessionStartTime: Long) {
        val game = gameDao.getById(gameId) ?: return
        val emulatorId = emulatorResolver.resolveEmulatorId(emulatorPackage)
        val isVariant = _activeSession.value?.variantFileId != null

        val savePath = if (!isVariant && emulatorId != null && SavePathRegistry.getConfig(emulatorId) != null) {
            savePathAuthority.ensureEvaluatedDefault(
                com.nendo.argosy.data.emulator.savepath.SavePathRequest(
                    platformSlug = game.platformSlug,
                    emulatorId = emulatorId,
                    emulatorPackage = emulatorPackage
                )
            )?.let { Logger.info(TAG, "[GameSession] Save folder settled | emulatorId=$emulatorId, path=$it") }
            saveSyncRepository.get().discoverSavePath(
                emulatorId = emulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedSaveId = game.saveId ?: game.titleId,
                coreName = coreName,
                emulatorPackage = emulatorPackage,
                gameId = gameId
            ) ?: saveSyncRepository.get().predictFolderSavePath(
                emulatorId = emulatorId,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                gameId = gameId,
                gameTitle = game.title,
                cachedSaveId = game.saveId ?: game.titleId,
                emulatorPackage = emulatorPackage
            )?.also {
                Logger.debug(TAG, "[GameSession] No existing save; watching predicted first-save path | path=$it")
            }
        } else null

        val watchPath = if (savePath != null) {
            val transformedFile = fileAccessLayer.getTransformedFile(savePath)
            if (transformedFile.isDirectory) {
                transformedFile.absolutePath
            } else {
                transformedFile.parentFile?.absolutePath ?: transformedFile.absolutePath
            }
        } else null

        val liveHardcore = _activeSession.value?.takeIf { it.gameId == gameId }?.isHardcore ?: isHardcore
        val channelName = snapshotRouter.get().sessionChannel(gameId, liveHardcore, activeSaveRepository.getActiveChannel(gameId))

        Logger.debug(TAG, "[GameSession] Starting service for gameId=$gameId | watchPath=$watchPath | savePath=$savePath | hardcore=$liveHardcore")
        GameSessionService.start(
            context = application,
            watchPath = watchPath,
            savePath = savePath,
            gameId = gameId,
            emulatorId = emulatorId,
            gameTitle = game.title,
            channelName = channelName,
            isHardcore = liveHardcore,
            sessionStartTime = sessionStartTime,
            emulatorPackage = emulatorPackage
        )
        sessionServiceStarted.set(true)
    }

    suspend fun endSession(stopService: Boolean = true, skipSaveSync: Boolean = false): SessionEndResult {
        if (!saveRecoveryGate.tryClaimSessionEnd()) {
            Logger.debug(TAG, "[SaveSync] SESSION | endSession already in progress, skipping")
            return SessionEndResult.Skipped
        }

        try {
            val session = _activeSession.value ?: run {
                Logger.debug(TAG, "[SaveSync] SESSION | endSession called but no active session, checking persisted session")
                val recovered = recoverOrphanedPlaySession(stopService)
                if (recovered == null) {
                    clearSessionAndBroadcast()
                    if (stopService) GameSessionService.stop(application)
                }
                return if (recovered == true) SessionEndResult.Success else SessionEndResult.Skipped
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
                val recapFacts = sessionRecapFactsRecorder.factsFor(game, session.emulatorPackage, session.startTime, endTime)
                val sessionId = insertPlaySessionIfAbsent(
                    PlaySessionEntity(
                        userId = prefs.socialUserId,
                        ownerUserId = prefs.rommUserId,
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
                        standbyMs = standbyMs,
                        raGameId = recapFacts.raGameId,
                        richPresence = recapFacts.richPresence,
                        richPresenceAt = recapFacts.richPresenceAt
                    )
                )
                if (sessionId != null) {
                    sessionRecapFactsRecorder.onSessionRecorded(
                        scope, sessionId, session.gameId, session.emulatorPackage, recapFacts, session.startTime, endTime
                    )
                    Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Play session entity created | activePlayMs=$activePlayMs, standbyMs=$standbyMs")
                    socialRepository.get().syncPlaySessions()
                }
            } catch (e: Exception) {
                Logger.error(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Failed to create play session entity", e)
            }
            reconcileAchievementsInBackground(session.gameId)

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

            val effectiveSkipSaveSync = skipSaveSync || session.isNetplayGuest || session.variantFileId != null
            val outcome = try {
                val saveOutcome = coroutineScope {
                    async {
                        recordPlayTime(session, Duration.ofMillis(activePlayMs))
                        markGameIncompleteIfNeeded(session, sessionDuration)
                        if (effectiveSkipSaveSync) {
                            SessionSaveOutcome.Exempt
                        } else {
                            syncStateDataLogged(session.gameId, session.emulatorPackage, "SESSION")
                            finalizeSave(session.toSaveInput())
                        }
                    }.await()
                }

                if (saveOutcome is SessionSaveOutcome.Synced) {
                    sessionEndNotices.sessionSaveSynced(session.gameId, saveOutcome.sync)
                }
                releaseSession(keepRecord = !saveOutcome.isSettled, startedAt = session.startTime)

                when (saveOutcome) {
                    is SessionSaveOutcome.Synced -> when (saveOutcome.cache) {
                        is SaveCacheManager.CacheResult.Duplicate -> SessionEndResult.Duplicate
                        else -> SessionEndResult.Success
                    }
                    SessionSaveOutcome.CacheFailed -> SessionEndResult.Error("Failed to cache save")
                    is SessionSaveOutcome.Unreadable -> SessionEndResult.SaveUnreadable(saveOutcome.dirPath, saveOutcome.emulatorId)
                    SessionSaveOutcome.Exempt, SessionSaveOutcome.NothingToSave -> SessionEndResult.Skipped
                }
            } catch (e: CancellationException) {
                releaseSession(keepRecord = true, startedAt = session.startTime)
                throw e
            } catch (e: Exception) {
                Logger.error(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Session end failed, keeping the session for recovery", e)
                releaseSession(keepRecord = true, startedAt = session.startTime)
                SessionEndResult.Error(e.message ?: "Unknown error")
            }
            signalSessionClosed(session)
            return outcome
        } finally {
            saveRecoveryGate.releaseSessionEnd()
        }
    }

    private suspend fun recordPlayTime(session: ActiveSession, screenOnDuration: Duration) {
        val seconds = screenOnDuration.toMillis() / 1000
        val minutes = ((seconds + 30) / 60).toInt()
        if (minutes <= 0) {
            Logger.debug(TAG, "Session too short to record: ${seconds}s")
            return
        }

        overlayWriter.addPlayTime(session.gameId, minutes)
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

    private suspend fun finalizeSave(input: SessionSaveInput): SessionSaveOutcome {
        val outcome = sessionSaveFinalizer.finalize(input)
        if (outcome is SessionSaveOutcome.Synced) {
            surfaceConflict(outcome)
        }
        return outcome
    }

    private fun surfaceConflict(outcome: SessionSaveOutcome.Synced) {
        val conflict = outcome.sync as? SyncSaveOnSessionEndUseCase.Result.Conflict ?: return
        Logger.warn(TAG, "[SaveSync] SESSION gameId=${conflict.gameId} | Sync result: CONFLICT | conflictId=${outcome.conflictId}, local=${conflict.upload.localTimestamp}, server=${conflict.upload.serverTimestamp}")
        _pendingSessionConflict.value = SaveConflictEvent(
            gameId = conflict.gameId,
            emulatorId = conflict.emulatorId,
            channelName = conflict.channelName,
            localTimestamp = conflict.upload.localTimestamp,
            serverTimestamp = conflict.upload.serverTimestamp,
            serverDeviceName = conflict.upload.serverDeviceName,
            conflictId = outcome.conflictId,
            isHardcoreDowngrade = conflict.upload.isHardcoreDowngrade
        )
    }

    private suspend fun syncStateData(gameId: Long, emulatorPackage: String) {
        val result = syncStatesOnSessionEndUseCase.get()(gameId, emulatorPackage)
        when (result) {
            is StateSyncResult.Cached -> Logger.debug(TAG, "Cached ${result.count} states for game $gameId")
            is StateSyncResult.NoStatesFound -> Logger.debug(TAG, "No states found for game $gameId")
            is StateSyncResult.NotConfigured -> Logger.debug(TAG, "State caching not configured for game $gameId")
            is StateSyncResult.Error -> Logger.error(TAG, "State sync error: ${result.reason}")
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

    /**
     * Whether the save watcher saw the game write during the current session. A short session
     * that produced a save is still a session worth archiving, so this is stronger evidence than
     * elapsed time about whether there is anything to keep.
     */
    fun sawSaveActivity(): Boolean = saveObserved.get()

    fun markSaveObserved() {
        saveObserved.set(true)
    }

    private val _saveWrites = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    val saveWrites: kotlinx.coroutines.flow.SharedFlow<Unit> = _saveWrites

    fun reportSaveWritten() {
        markSaveObserved()
        _saveWrites.tryEmit(Unit)
    }

    fun getSessionDuration(): Duration? {
        val session = _activeSession.value ?: return null
        return Duration.between(session.startTime, Instant.now())
    }

    /**
     * End a session too short to be worth archiving its save, without discarding its save states.
     *
     * The caller decides a session is not worth backing up from elapsed time and whether the
     * watcher saw the game write its SRAM. A game with no battery save never produces that write,
     * so every short session on one lands here - and a state the player deliberately saved is
     * worth keeping whatever the clock says. States are cached to Room before this point but only
     * queued for upload by [endSession], so without this flush the row has nothing left to drain
     * it until some later, longer session for the same game happens to run.
     */
    fun cancelSession() {
        val session = _activeSession.value ?: return
        if (!saveRecoveryGate.tryClaimSessionEnd()) {
            Logger.debug(TAG, "[SaveSync] SESSION | cancelSession skipped: endSession already in progress")
            return
        }
        _activeSession.value = null
        GameSessionService.stop(application)
        Logger.debug(TAG, "[SaveSync] SESSION gameId=${session.gameId} | Cancelled (no save backup)")
        scope.launch {
            try {
                if (session.variantFileId == null && !session.isNetplayGuest) {
                    syncStateDataLogged(session.gameId, session.emulatorPackage, "CANCEL")
                }
                releaseSession(keepRecord = false, startedAt = session.startTime)
                signalSessionClosed(session)
            } finally {
                saveRecoveryGate.releaseSessionEnd()
            }
        }
    }

    /**
     * Ends the session on the tracker's own scope. A caller whose scope dies with the watcher
     * service passes stopService=false and stops the service itself once the job completes.
     */
    fun endSessionInBackground(skipSaveSync: Boolean = false, stopService: Boolean = true): Job =
        scope.launch { endSession(stopService = stopService, skipSaveSync = skipSaveSync) }

    private fun reconcileAchievementsInBackground(gameId: Long) {
        scope.launch {
            try {
                reconcileAchievementsOnSessionEndUseCase.get()(gameId)
            } catch (e: Exception) {
                Logger.warn(TAG, "[Achievements] Session-end reconcile failed for gameId=$gameId", e)
            }
        }
    }

    /** Cache the current session's save to local Room with needsRemoteSync=true, synchronously. Called from the in-game Quit handler so the save is persisted before libretro core teardown begins -- teardown can be slow enough on some cores (PSP shader cache, etc.) to trigger an OS-imposed process kill, taking endSession's coroutine with it. The upload itself is handled later by the sync coordinator draining the dirty cache row. */
    suspend fun cacheCurrentSessionForQuit(): SaveCacheManager.CacheResult? {
        val session = _activeSession.value ?: return null
        if (session.variantFileId != null) return null
        val game = gameDao.getById(session.gameId) ?: return null
        val emulatorId = emulatorResolver.resolveSessionEmulator(
            game.id, game.platformId, game.platformSlug, session.emulatorPackage
        )?.emulatorId ?: return null
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
        val activeChannel = snapshotRouter.get().sessionChannel(session.gameId, session.isHardcore, session.channelName)
        return try {
            saveCacheManager.get().cacheCurrentSave(
                gameId = session.gameId,
                emulatorId = emulatorId,
                savePath = savePath,
                channelName = activeChannel,
                isLocked = false,
                isHardcore = session.isHardcore,
                skipDuplicateCheck = false,
                needsRemoteSync = true,
                coreName = session.coreName,
                claimNewSaves = true
            ).also { result ->
                Logger.debug(TAG, "[SaveSync] QUIT gameId=${session.gameId} | Pre-quit cache result=${result::class.simpleName}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] QUIT gameId=${session.gameId} | Pre-quit cache failed", e)
            SaveCacheManager.CacheResult.Failed
        }
    }

    /** Cache the current session's save STATES into local Room synchronously, before core teardown.
     * Mirrors [cacheCurrentSessionForQuit]: local cache only, uploads deferred to the sync coordinator,
     * so states survive an OS process kill during teardown instead of dying with endSession's coroutine. */
    suspend fun cacheCurrentSessionStatesForQuit() {
        val session = _activeSession.value ?: return
        if (session.variantFileId != null) return
        val result = syncStatesOnSessionEndUseCase.get()(
            session.gameId,
            session.emulatorPackage,
            queueUploads = false
        )
        Logger.debug(TAG, "[StateSync] QUIT gameId=${session.gameId} | Pre-quit state cache result=${result::class.simpleName}")
    }

    fun forceStopService() {
        GameSessionService.stop(application)
        scope.launch { clearSessionAndBroadcast() }
        Logger.debug(TAG, "[SaveSync] SESSION | Force stopped service (no active session)")
    }

    fun canResumeSession(gameId: Long, variantFileId: Long? = null): Boolean {
        val session = _activeSession.value
        if (session == null) {
            Logger.debug(TAG, "canResumeSession($gameId): no active session")
            return false
        }
        if (session.gameId != gameId) {
            Logger.debug(TAG, "canResumeSession($gameId): gameId mismatch (active=${session.gameId})")
            return false
        }
        if (session.variantFileId != variantFileId) {
            Logger.debug(TAG, "canResumeSession($gameId): variant mismatch (active=${session.variantFileId}, requested=$variantFileId)")
            return false
        }
        val inForeground = permissionHelper.isPackageOnScreen(application, session.emulatorPackage)
            ?: permissionHelper.isPackageInForeground(application, session.emulatorPackage, withinMs = 300_000)
        Logger.debug(TAG, "canResumeSession($gameId): emulator=${session.emulatorPackage}, inForeground=$inForeground")
        return inForeground
    }

}

private fun ActiveSession.toSaveInput() = SessionSaveInput(
    gameId = gameId,
    emulatorPackage = emulatorPackage,
    coreName = coreName,
    isHardcore = isHardcore,
    channelName = channelName,
    startTime = startTime,
    variantFileId = variantFileId,
    isNetplayGuest = isNetplayGuest
)

private fun PersistedSession.toSaveInput() = SessionSaveInput(
    gameId = gameId,
    emulatorPackage = emulatorPackage,
    coreName = coreName,
    isHardcore = isHardcore,
    channelName = channelName,
    startTime = startTime,
    variantFileId = variantFileId,
    isNetplayGuest = isNetplayGuest
)
