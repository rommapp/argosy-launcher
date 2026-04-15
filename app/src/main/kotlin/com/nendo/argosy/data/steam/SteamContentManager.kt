package com.nendo.argosy.data.steam

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.download.DownloadForegroundService
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SteamDownloadQueueDao
import com.nendo.argosy.data.local.entity.SteamDownloadDbState
import com.nendo.argosy.data.local.entity.SteamDownloadQueueEntity
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.future.await
import java.io.Closeable
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SteamContentManager"
private const val DOWNLOAD_INFO_DIR = ".DownloadInfo"

sealed class SteamDownloadState {
    data object Idle : SteamDownloadState()
    data class Preparing(val appId: Long, val gameName: String) : SteamDownloadState()
    data class Connecting(val appId: Long, val gameName: String) : SteamDownloadState()
    data class FetchingManifest(val appId: Long, val gameName: String, val depotId: Int) : SteamDownloadState()
    data class Validating(val appId: Long, val gameName: String, val statusDetail: String = "") : SteamDownloadState()
    data class Downloading(
        val appId: Long,
        val gameName: String,
        val progress: Float,
        val currentDepot: Int,
        val totalDepots: Int
    ) : SteamDownloadState()
    data class Moving(val appId: Long, val gameName: String) : SteamDownloadState()
    data class Completed(val appId: Long, val gameName: String, val installPath: String) : SteamDownloadState()
    data class Failed(val appId: Long, val gameName: String, val error: String) : SteamDownloadState()
    data class Paused(val appId: Long, val gameName: String, val progress: Float, val needsVerification: Boolean = false) : SteamDownloadState()
    data class Cleaning(val appId: Long, val gameName: String) : SteamDownloadState()
}

data class DepotInfo(
    val depotId: Int,
    val manifestId: Long,
    val name: String,
    val os: String?,
    val arch: String?,
    val size: Long
)

data class SteamDownloadProgress(
    val appId: Long,
    val gameName: String,
    val coverPath: String?,
    val progress: Float,
    val totalBytes: Long,
    val bytesDownloaded: Long,
    val state: SteamDownloadState,
    val bytesPerSecond: Long = 0L
) {
    val progressPercent: Int get() = (progress * 100).toInt()
}

data class QueuedSteamDownload(
    val appId: Long,
    val gameName: String,
    val coverPath: String?,
    val appInfo: KeyValue?,
    val targetInstallPath: String? = null
)

@Singleton
class SteamContentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val steamAuthManager: SteamAuthManager,
    private val steamLibraryManager: SteamLibraryManager,
    private val notificationManager: NotificationManager,
    private val steamDownloadQueueDao: SteamDownloadQueueDao,
    private val downloadManager: dagger.Lazy<com.nendo.argosy.data.download.DownloadManager>,
    val depotManager: SteamDepotManager,
    val pathResolver: SteamPathResolver,
    private val progressTracker: SteamProgressTracker,
    private val downloadTracker: SteamDownloadTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val heartbeatDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "steam-heartbeat").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var steamClient: SteamClient? = null
    private var activeDepotDownloader: DepotDownloader? = null
    private var picsSubscription: Closeable? = null

    private val _downloadState = MutableStateFlow<SteamDownloadState>(SteamDownloadState.Idle)
    val downloadState: StateFlow<SteamDownloadState> = _downloadState.asStateFlow()

    private val _activeDownload = MutableStateFlow<SteamDownloadProgress?>(null)
    val activeDownload: StateFlow<SteamDownloadProgress?> = _activeDownload.asStateFlow()

    private val _downloadQueue = MutableStateFlow<List<QueuedSteamDownload>>(emptyList())
    val downloadQueue: StateFlow<List<QueuedSteamDownload>> = _downloadQueue.asStateFlow()

    private val _completedDownloads = MutableStateFlow<List<SteamDownloadProgress>>(emptyList())
    val completedDownloads: StateFlow<List<SteamDownloadProgress>> = _completedDownloads.asStateFlow()

    fun clearCompletedDownloads() {
        _completedDownloads.value = emptyList()
        if (_activeDownload.value?.state is SteamDownloadState.Completed) {
            _activeDownload.value = null
        }
    }

    private var currentDownloadJob: kotlinx.coroutines.Job? = null
    private var isCancelled = false
    private var lastDbProgressUpdate = 0L
    private companion object {
        const val DB_PROGRESS_INTERVAL_MS = 30_000L
    }

    init {
        scope.launch {
            restoreSteamQueueFromDatabase()
        }
    }

    private suspend fun restoreSteamQueueFromDatabase() = withContext(Dispatchers.IO) {
        steamDownloadQueueDao.clearFinished()

        // Recover orphaned downloads: game has localPath pointing to staging but no queue entry
        recoverOrphanedStagingDownloads()

        val pending = steamDownloadQueueDao.getPendingDownloads()
        if (pending.isEmpty()) return@withContext

        // Handle interrupted deploys first -- resume file copy
        val deploying = pending.filter { it.state == SteamDownloadDbState.DEPLOYING.name }
        for (entity in deploying) {
            resumeInterruptedDeploy(entity)
        }

        // Re-fetch after deploy handling (some may now be COMPLETED)
        val afterDeploy = steamDownloadQueueDao.getPendingDownloads()
        if (afterDeploy.isEmpty()) return@withContext

        for (entity in afterDeploy) {
            if (entity.state in listOf(SteamDownloadDbState.DOWNLOADING.name, SteamDownloadDbState.PREPARING.name)) {
                steamDownloadQueueDao.updateState(entity.appId, SteamDownloadDbState.PAUSED.name)
                // Clear localPath if it points to staging so game doesn't appear installed
                gameDao.getBySteamAppId(entity.appId)?.let { game ->
                    if (game.localPath?.contains("steam_staging") == true) {
                        gameDao.update(game.copy(localPath = null))
                    }
                }
            }
        }

        val paused = afterDeploy.filter {
            it.state in listOf(SteamDownloadDbState.PAUSED.name, SteamDownloadDbState.DOWNLOADING.name, SteamDownloadDbState.PREPARING.name)
        }
        val queued = afterDeploy.filter { it.state == SteamDownloadDbState.QUEUED.name }

        if (paused.isNotEmpty()) {
            val primary = paused.first()
            val bytesDownloaded = primary.installPath?.let { progressTracker.loadPersistedBytes(it) } ?: primary.bytesDownloaded
            val pausedState = SteamDownloadState.Paused(primary.appId, primary.gameName, 0f, needsVerification = true)
            _downloadState.value = pausedState
            _activeDownload.value = SteamDownloadProgress(
                appId = primary.appId,
                gameName = primary.gameName,
                coverPath = primary.coverPath,
                progress = 0f,
                totalBytes = primary.totalBytes,
                bytesDownloaded = bytesDownloaded,
                state = pausedState
            )
            Log.d(TAG, "Restored active from DB: ${primary.gameName}")

            val restPaused = paused.drop(1)
            _downloadQueue.value = (restPaused + queued).map { entity ->
                QueuedSteamDownload(entity.appId, entity.gameName, entity.coverPath, null, entity.installPath)
            }
        } else if (queued.isNotEmpty()) {
            _downloadQueue.value = queued.map { entity ->
                QueuedSteamDownload(entity.appId, entity.gameName, entity.coverPath, null, entity.installPath)
            }
        }

        Log.d(TAG, "Restored ${paused.size} paused + ${queued.size} queued from DB")
    }

    private suspend fun recoverOrphanedStagingDownloads() {
        val stagingRoot = File(context.filesDir, "steam_staging")
        if (!stagingRoot.exists()) return

        val steamGames = gameDao.getAllWithSteamAppId()
        for (game in steamGames) {
            val localPath = game.localPath ?: continue
            if (!localPath.contains("steam_staging")) continue

            val appId = game.steamAppId ?: continue
            val existing = steamDownloadQueueDao.getByAppId(appId)
            if (existing != null) continue

            val stagingDir = File(localPath)
            if (stagingDir.exists() && (stagingDir.listFiles()?.isNotEmpty() == true)) {
                Log.d(TAG, "Recovering orphaned staging download: ${game.title} (appId=$appId)")
                steamDownloadQueueDao.insert(SteamDownloadQueueEntity(
                    appId = appId,
                    gameName = game.title,
                    coverPath = game.coverPath,
                    installDir = null,
                    installPath = localPath,
                    totalBytes = 0L,
                    bytesDownloaded = progressTracker.loadPersistedBytes(localPath),
                    state = SteamDownloadDbState.PAUSED.name,
                    errorReason = null
                ))
                gameDao.update(game.copy(localPath = null))
            } else {
                Log.d(TAG, "Clearing stale staging path for ${game.title}")
                gameDao.update(game.copy(localPath = null))
            }
        }
    }

    private suspend fun resumeInterruptedDeploy(entity: SteamDownloadQueueEntity) {
        val appId = entity.appId
        val gameName = entity.gameName
        val stagingDir = File(context.filesDir, "steam_staging/$appId")

        // Resolve final path from installDir (Steam name), not installPath (may still point to staging)
        val finalDir = if (entity.installDir != null) {
            pathResolver.getInstallDirByName(entity.installDir)
        } else {
            pathResolver.getInstallDir(appId)
        }

        Log.d(TAG, "Resuming interrupted deploy for $gameName: staging=${stagingDir.exists()}, final=${finalDir.exists()}")

        // Case 1: Final dir has .download_complete -- move finished, just clean up
        if (File(finalDir, ".download_complete").exists()) {
            Log.d(TAG, "Deploy already complete for $gameName, cleaning up")
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            gameDao.getBySteamAppId(appId)?.let { game ->
                if (game.localPath != finalDir.absolutePath) {
                    gameDao.update(game.copy(localPath = finalDir.absolutePath, source = GameSource.STEAM))
                }
            }
            steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.COMPLETED.name)
            return
        }

        // Case 2: Staging dir still has files -- resume the copy
        if (stagingDir.exists() && (stagingDir.listFiles()?.isNotEmpty() == true)) {
            Log.d(TAG, "Resuming file copy for $gameName: staging -> ${finalDir.absolutePath}")

            _downloadState.value = SteamDownloadState.Moving(appId, gameName)
            _activeDownload.value = SteamDownloadProgress(
                appId = appId,
                gameName = gameName,
                coverPath = entity.coverPath,
                progress = 1f,
                totalBytes = entity.totalBytes,
                bytesDownloaded = entity.totalBytes,
                state = SteamDownloadState.Moving(appId, gameName)
            )

            scope.launch(Dispatchers.IO) {
                try {
                    finalDir.mkdirs()
                    val destFree = pathResolver.getAvailableBytes(finalDir)
                    val stagingSize = pathResolver.getDirectorySize(stagingDir)
                    if (destFree != null && destFree < stagingSize) {
                        Log.e(TAG, "Insufficient destination space for deploy resume: ${destFree / 1024 / 1024}MB free, need ${stagingSize / 1024 / 1024}MB")
                        steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.PAUSED.name,
                            "Waiting for destination storage (need ${stagingSize / 1024 / 1024}MB)")
                        _downloadState.value = SteamDownloadState.Paused(appId, gameName, 1f)
                        _activeDownload.value = _activeDownload.value?.copy(
                            state = SteamDownloadState.Paused(appId, gameName, 1f)
                        )
                        return@launch
                    }
                    val moved = pathResolver.moveDirectory(stagingDir, finalDir)
                    if (!moved) {
                        Log.e(TAG, "Failed to resume deploy for $gameName")
                        steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.FAILED.name, "File move failed")
                        _downloadState.value = SteamDownloadState.Failed(appId, gameName, "File move failed")
                        _activeDownload.value = _activeDownload.value?.copy(
                            state = SteamDownloadState.Failed(appId, gameName, "File move failed")
                        )
                        return@launch
                    }

                    gameDao.getBySteamAppId(appId)?.let { game ->
                        gameDao.update(game.copy(
                            localPath = finalDir.absolutePath,
                            source = GameSource.STEAM,
                            addedAt = java.time.Instant.now()
                        ))
                    }
                    File(finalDir, ".download_complete").createNewFile()
                    File(finalDir, ".download_in_progress").delete()
                    File(finalDir, DOWNLOAD_INFO_DIR).mkdirs()

                    steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.COMPLETED.name)
                    _downloadState.value = SteamDownloadState.Completed(appId, gameName, finalDir.absolutePath)
                    _activeDownload.value = null
                    Log.d(TAG, "Deploy resume complete: $gameName -> ${finalDir.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Deploy resume failed for $gameName", e)
                    steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.FAILED.name, e.message)
                    _downloadState.value = SteamDownloadState.Failed(appId, gameName, e.message ?: "Deploy failed")
                }
            }
            return
        }

        // Case 3: Staging gone, final dir incomplete -- need re-download
        Log.w(TAG, "Cannot resume deploy for $gameName: staging gone, final incomplete. Marking as failed.")
        if (finalDir.exists()) finalDir.deleteRecursively()
        steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.FAILED.name, "Deploy interrupted, staging lost")
    }

    fun initialize(client: SteamClient, apps: SteamApps, cm: CallbackManager) {
        steamClient = client
        val content = client.getHandler(SteamContent::class.java)!!
        depotManager.initialize(client, apps, content, cm)
        Log.d(TAG, "SteamContentManager initialized")
    }

    suspend fun hasPendingDownloads(): Boolean {
        return steamDownloadQueueDao.getPendingDownloads().isNotEmpty()
    }

    suspend fun restorePausedDownloads() {
        val active = _activeDownload.value ?: return
        if (active.state !is SteamDownloadState.Paused) return
        if (!isConnected() || currentDownloadJob?.isActive == true) return

        Log.d(TAG, "Auto-resuming ${active.gameName} (Steam connected)")
        queueDownloadOptimistic(active.appId, active.gameName, active.coverPath)
    }

    fun onDownloadSlotFreed() {
        if (_downloadQueue.value.isNotEmpty() && currentDownloadJob?.isActive != true) {
            Log.d(TAG, "Download slot freed, checking Steam queue")
            processNextInQueue()
        }
    }

    fun hasActiveSteamDownload(): Boolean {
        val state = _downloadState.value
        return state is SteamDownloadState.Downloading ||
            state is SteamDownloadState.Preparing ||
            state is SteamDownloadState.Connecting ||
            state is SteamDownloadState.FetchingManifest ||
            state is SteamDownloadState.Validating ||
            state is SteamDownloadState.Moving
    }

    fun isConnected(): Boolean {
        val hasHandlers = depotManager.isConnected()
        val loggedIn = steamAuthManager.isLoggedIn.value
        Log.v(TAG, "isConnected check: hasHandlers=$hasHandlers, loggedIn=$loggedIn")
        return hasHandlers && loggedIn
    }

    fun onDisconnected() {
        Log.d(TAG, "Steam disconnected, clearing handlers")
        val state = _downloadState.value
        // Moving and Cleaning are local file operations that don't need Steam -- let them finish
        if (state !is SteamDownloadState.Idle && state !is SteamDownloadState.Paused &&
            state !is SteamDownloadState.Completed && state !is SteamDownloadState.Failed &&
            state !is SteamDownloadState.Moving && state !is SteamDownloadState.Cleaning) {
            pauseDownload()
        }
        scope.launch(Dispatchers.IO) {
            val pending = steamDownloadQueueDao.getPendingDownloads()
            pending.filter { it.state in listOf(SteamDownloadDbState.DOWNLOADING.name, SteamDownloadDbState.PREPARING.name) }.forEach {
                steamDownloadQueueDao.updateState(it.appId, SteamDownloadDbState.PAUSED.name)
            }
        }
        steamClient = null
        depotManager.clearHandlers()
    }

    suspend fun ensureConnected(): Boolean {
        if (isConnected()) return true

        if (steamAuthManager.sessionDead) {
            Log.e(TAG, "Steam session dead, cannot auto-connect")
            notificationManager.show(
                title = "Steam session expired",
                subtitle = "Sign in from Settings > Steam to download",
                type = NotificationType.WARNING,
                key = "steam_not_signed_in"
            )
            return false
        }

        Log.d(TAG, "Steam not connected, starting service and waiting...")
        val active = _activeDownload.value
        if (active != null) {
            val connectingState = SteamDownloadState.Connecting(active.appId, active.gameName)
            _downloadState.value = connectingState
            _activeDownload.value = active.copy(state = connectingState)
        }
        val intent = android.content.Intent(context, SteamService::class.java).apply {
            putExtra(SteamService.EXTRA_FORCE_CONNECT, true)
        }
        context.startService(intent)

        // Poll for connection with timeout
        val deadline = System.currentTimeMillis() + 30_000L
        while (System.currentTimeMillis() < deadline) {
            if (isConnected()) {
                Log.d(TAG, "Steam connected after waiting")
                return true
            }
            kotlinx.coroutines.delay(500)
        }
        Log.e(TAG, "Steam connection timeout (connected=${isConnected()}, loggedIn=${steamAuthManager.isLoggedIn.value})")
        return false
    }

    fun notifyConnected() {
        Log.v(TAG, "notifyConnected called")
    }

    suspend fun fetchAppInfo(appId: Int): KeyValue {
        if (!isConnected()) {
            if (!ensureConnected()) {
                throw IllegalStateException("Steam not connected")
            }
        }
        return depotManager.fetchAppInfo(appId)
    }

    suspend fun downloadGame(
        appId: Long,
        gameName: String,
        coverPath: String?
    ) = withContext(Dispatchers.IO) {
        val appInfo = fetchAppInfo(appId.toInt())
        downloadGame(appId, gameName, appInfo, coverPath)
    }

    fun queueDownloadOptimistic(appId: Long, gameName: String, coverPath: String?) {
        if (!canQueue(appId)) return
        if (steamAuthManager.sessionDead) {
            notificationManager.show(
                title = "Steam session expired",
                subtitle = "Sign in from Settings > Steam to download",
                type = NotificationType.WARNING,
                key = "steam_not_signed_in"
            )
            // Revert to paused so the download stays visible
            val active = _activeDownload.value
            if (active != null) {
                val pausedState = SteamDownloadState.Paused(appId, gameName, active.progress)
                _downloadState.value = pausedState
                _activeDownload.value = active.copy(state = pausedState)
            }
            return
        }
        DownloadForegroundService.start(context)

        val active = _activeDownload.value
        if (currentDownloadJob?.isActive == true && active?.appId != appId) {
            // Something else is downloading -- add to queue with optimistic UI
            _downloadQueue.value = _downloadQueue.value + QueuedSteamDownload(appId, gameName, coverPath, appInfo = null)
            persistQueueEntry(appId, gameName, coverPath)
            Log.d(TAG, "Optimistically queued: $gameName (queue size: ${_downloadQueue.value.size})")
            return
        }

        // Nothing active or resuming paused/failed -- become the active download immediately
        if (active?.appId == appId && (active.state is SteamDownloadState.Paused || active.state is SteamDownloadState.Failed)) {
            val queuedState = SteamDownloadState.Preparing(appId, gameName)
            _downloadState.value = queuedState
            _activeDownload.value = active.copy(state = queuedState)
            Log.d(TAG, "Optimistic retry for $appId, transitioning ${active.state::class.simpleName} -> Preparing")
        } else {
            _downloadState.value = SteamDownloadState.Preparing(appId, gameName)
            _activeDownload.value = SteamDownloadProgress(
                appId = appId,
                gameName = gameName,
                coverPath = coverPath,
                progress = 0f,
                totalBytes = 0L,
                bytesDownloaded = 0L,
                state = SteamDownloadState.Preparing(appId, gameName)
            )
        }

        _downloadQueue.value = _downloadQueue.value + QueuedSteamDownload(appId, gameName, coverPath, appInfo = null)
        persistQueueEntry(appId, gameName, coverPath)
        Log.d(TAG, "Optimistically activated: $gameName")

        if (currentDownloadJob?.isActive != true) {
            processNextInQueue()
        }
    }

    private fun persistQueueEntry(appId: Long, gameName: String, coverPath: String?) {
        scope.launch(Dispatchers.IO) {
            val snapshotPath = runCatching {
                pathResolver.snapshotInstallDirAtEnqueue(appId, null).absolutePath
            }.getOrNull()
            try {
                steamDownloadQueueDao.insert(SteamDownloadQueueEntity(
                    appId = appId,
                    gameName = gameName,
                    coverPath = coverPath,
                    installDir = null,
                    installPath = snapshotPath,
                    totalBytes = 0L,
                    bytesDownloaded = 0L,
                    state = SteamDownloadDbState.QUEUED.name,
                    errorReason = null
                ))
                Log.d(TAG, "Persisted queue entry: $gameName -> ${snapshotPath ?: "<resolve later>"}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist queue entry for $gameName", e)
            }
            if (snapshotPath != null) {
                _downloadQueue.value = _downloadQueue.value.map {
                    if (it.appId == appId && it.targetInstallPath == null) it.copy(targetInstallPath = snapshotPath) else it
                }
            }
        }
    }

    fun queueDownload(appId: Long, gameName: String, appInfo: KeyValue, coverPath: String?) {
        if (!canQueue(appId)) return
        DownloadForegroundService.start(context)

        val active = _activeDownload.value

        // Transition paused/failed -> Preparing without nulling activeDownload
        if (active?.appId == appId && (active.state is SteamDownloadState.Paused || active.state is SteamDownloadState.Failed)) {
            val preparingState = SteamDownloadState.Preparing(appId, gameName)
            _downloadState.value = preparingState
            _activeDownload.value = active.copy(state = preparingState)
            Log.d(TAG, "Retrying $appId: ${active.state::class.simpleName} -> Preparing")
        }

        val installDirName = appInfo["config"]?.get("installdir")?.asString()
        scope.launch(Dispatchers.IO) {
            val snapshotPath = runCatching {
                pathResolver.snapshotInstallDirAtEnqueue(appId, installDirName).absolutePath
            }.getOrNull()
            try {
                steamDownloadQueueDao.insert(SteamDownloadQueueEntity(
                    appId = appId,
                    gameName = gameName,
                    coverPath = coverPath,
                    installDir = installDirName,
                    installPath = snapshotPath,
                    totalBytes = 0L,
                    bytesDownloaded = 0L,
                    state = SteamDownloadDbState.QUEUED.name,
                    errorReason = null
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist queue entry for $gameName", e)
            }
            val queued = QueuedSteamDownload(appId, gameName, coverPath, appInfo, snapshotPath)
            _downloadQueue.value = _downloadQueue.value + queued
            Log.d(TAG, "Queued Steam download: $gameName -> ${snapshotPath ?: "<resolve later>"} (queue size: ${_downloadQueue.value.size})")

            if (currentDownloadJob?.isActive != true) {
                processNextInQueue()
            }
        }
    }

    private fun canQueue(appId: Long): Boolean {
        val active = _activeDownload.value
        if (active?.appId == appId) {
            val state = active.state
            if (state is SteamDownloadState.Paused || state is SteamDownloadState.Failed) {
                // Allow re-queuing paused or failed downloads
                return true
            }
            Log.d(TAG, "Game $appId already active in state ${state::class.simpleName}")
            return false
        }
        if (_downloadQueue.value.any { it.appId == appId }) {
            Log.d(TAG, "Game $appId already in queue")
            return false
        }
        return true
    }

    private fun processNextInQueue() {
        val queue = _downloadQueue.value
        if (queue.isEmpty()) {
            Log.d(TAG, "Download queue empty")
            return
        }

        val next = queue.first()
        _downloadQueue.value = queue.drop(1)
        Log.d(TAG, "Starting next queued download: ${next.gameName}")

        scope.launch {
            // Check shared slot budget before starting
            val maxConcurrent = preferencesRepository.userPreferences.first().maxConcurrentDownloads
            val rommActive = downloadManager.get().activeDownloadCount
            if (rommActive + 1 > maxConcurrent) {
                Log.d(TAG, "No download slots available (romm=$rommActive, max=$maxConcurrent), re-queuing ${next.gameName}")
                _downloadQueue.value = listOf(next) + _downloadQueue.value
                return@launch
            }

            val appInfo = next.appInfo ?: try {
                fetchAppInfo(next.appId.toInt())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch app info for ${next.gameName}: ${e.message}")
                val active = _activeDownload.value
                if (active != null) {
                    val pausedState = SteamDownloadState.Paused(next.appId, next.gameName, active.progress)
                    _downloadState.value = pausedState
                    _activeDownload.value = active.copy(state = pausedState)
                }
                steamDownloadQueueDao.updateState(next.appId, SteamDownloadDbState.PAUSED.name)
                return@launch
            }
            startDownload(next.appId, next.gameName, appInfo, next.coverPath, next.targetInstallPath)
        }
    }

    suspend fun downloadGame(
        appId: Long,
        gameName: String,
        appInfo: KeyValue,
        coverPath: String?
    ) {
        queueDownload(appId, gameName, appInfo, coverPath)
    }

    private suspend fun startDownload(
        appId: Long,
        gameName: String,
        appInfo: KeyValue,
        coverPath: String?,
        snapshottedInstallPath: String? = null
    ) {
        val client = steamClient
        if (client == null) {
            val failedState = SteamDownloadState.Failed(appId, gameName, "Steam not connected")
            _downloadState.value = failedState
            _activeDownload.value = _activeDownload.value?.copy(state = failedState)
            scope.launch(Dispatchers.IO) {
                steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.FAILED.name, "Steam not connected")
            }
            return
        }

        isCancelled = false
        progressTracker.resetSpeedTracking()

        currentDownloadJob = scope.launch {
            var heartbeatJob: Job? = null
            try {
                _downloadState.value = SteamDownloadState.Preparing(appId, gameName)

                // Resolve destination: honor enqueue-time snapshot so mid-queue preference
                // changes don't redirect an in-flight download. New downloads re-snapshot.
                val steamInstallDir = appInfo["config"]?.get("installdir")?.asString()
                val installDir = when {
                    snapshottedInstallPath != null -> File(snapshottedInstallPath)
                    steamInstallDir != null -> pathResolver.getInstallDirByName(steamInstallDir)
                    else -> pathResolver.getInstallDir(appId)
                }
                installDir.mkdirs()
                if (!installDir.exists() || !installDir.canWrite()) {
                    val msg = "Install path not accessible: ${installDir.absolutePath}"
                    Log.w(TAG, msg)
                    notificationManager.show(
                        title = "Cannot download $gameName",
                        subtitle = msg,
                        type = NotificationType.WARNING,
                        key = "steam_path_$appId"
                    )
                    val pausedState = SteamDownloadState.Paused(appId, gameName, 0f)
                    _downloadState.value = pausedState
                    _activeDownload.value = _activeDownload.value?.copy(state = pausedState)
                    steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.PAUSED.name, msg)
                    processNextInQueue()
                    return@launch
                }
                File(installDir, ".download_in_progress").createNewFile()
                Log.d(TAG, "Install path for $appId: ${installDir.absolutePath}")

                // Migrate old staging downloads to destination
                val oldStagingDir = File(context.filesDir, "steam_staging/$appId")
                if (oldStagingDir.exists() && oldStagingDir.listFiles()?.isNotEmpty() == true) {
                    if (!installDir.exists() || installDir.listFiles().isNullOrEmpty()) {
                        Log.d(TAG, "Migrating old staging -> destination: ${oldStagingDir.absolutePath} -> ${installDir.absolutePath}")
                        pathResolver.moveDirectory(oldStagingDir, installDir)
                    } else {
                        Log.d(TAG, "Cleaning stale staging dir (destination already has files)")
                        oldStagingDir.deleteRecursively()
                    }
                }

                // Preserve .DepotDownloader if resuming from DB queue.
                // Clean it only for fresh downloads.
                val isResume = steamDownloadQueueDao.getByAppId(appId)?.state in
                    listOf(SteamDownloadDbState.PAUSED.name, SteamDownloadDbState.DOWNLOADING.name, SteamDownloadDbState.PREPARING.name)
                val depotDir = File(installDir, ".DepotDownloader")
                if (depotDir.exists() && !isResume) {
                    depotDir.deleteRecursively()
                    Log.d(TAG, "Cleaned stale .DepotDownloader state (fresh download)")
                }

                File(installDir, ".download_complete").delete()

                // Store install path so restore/cleanup can find it
                gameDao.getBySteamAppId(appId)?.let { game ->
                    if (game.localPath != installDir.absolutePath) {
                        gameDao.update(game.copy(localPath = installDir.absolutePath))
                    }
                }

                steamDownloadQueueDao.updateInstallPath(appId, installDir.absolutePath)
                if (steamInstallDir != null) {
                    steamDownloadQueueDao.updateInstallDir(appId, steamInstallDir)
                }
                steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.PREPARING.name)

                _activeDownload.value = SteamDownloadProgress(
                    appId = appId,
                    gameName = gameName,
                    coverPath = coverPath,
                    progress = 0f,
                    totalBytes = 0L,
                    bytesDownloaded = 0L,
                    state = _downloadState.value
                )

                val allDepots = depotManager.getWindowsDepots(appInfo)
                if (allDepots.isEmpty()) {
                    throw IllegalStateException("No Windows depots found for this game")
                }
                Log.v(TAG, "All depots discovered: ${allDepots.map { "${it.depotId}(manifest=${it.manifestId})" }}")

                val sizeResult = depotManager.fetchDepotSizes(appId.toInt(), allDepots)
                val totalSize = sizeResult.totalSize
                Log.v(TAG, "Size result: accessible=${sizeResult.accessibleDepotIds}, sizes=${sizeResult.depotSizes.map { "${it.key}=${it.value / 1024 / 1024}MB" }}")

                val depots = if (sizeResult.accessibleDepotIds.isNotEmpty()) {
                    allDepots.filter { it.depotId in sizeResult.accessibleDepotIds }
                } else {
                    Log.w(TAG, "No depots returned sizes, falling back to all ${allDepots.size} depots")
                    allDepots
                }
                if (depots.isEmpty()) {
                    throw IllegalStateException("No accessible depots for this game")
                }
                Log.d(TAG, "Final depot list: ${depots.map { "${it.depotId}(${it.name})" }}")
                Log.d(TAG, "Total: ${totalSize / 1024 / 1024}MB for $gameName (${depots.size}/${allDepots.size} depots)")

                // Storage check -- downloading directly to destination
                val requiredDestBytes = (totalSize * 1.05).toLong()
                val destFreeBytes = pathResolver.getAvailableBytes(installDir)
                if (destFreeBytes != null) {
                    Log.d(TAG, "Storage check: destination free=${destFreeBytes / 1024 / 1024}MB, required=${requiredDestBytes / 1024 / 1024}MB (1.05x)")
                    if (destFreeBytes < requiredDestBytes) {
                        val msg = "Not enough storage (${destFreeBytes / 1024 / 1024}MB free, need ${requiredDestBytes / 1024 / 1024}MB)"
                        Log.w(TAG, msg)
                        notificationManager.show(title = "Cannot download $gameName", subtitle = msg, type = NotificationType.WARNING, key = "steam_storage_$appId")
                        val pausedState = SteamDownloadState.Paused(appId, gameName, 0f)
                        _downloadState.value = pausedState
                        _activeDownload.value = _activeDownload.value?.copy(state = pausedState)
                        steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.PAUSED.name, msg)
                        processNextInQueue()
                        return@launch
                    }
                } else {
                    Log.w(TAG, "Cannot check destination storage (path not stattable), proceeding")
                }

                val depotSizes = sizeResult.depotSizes
                val baselineBytes = progressTracker.loadPersistedBytes(installDir.absolutePath)
                // Track cumulative uncompressed bytes per depot (concurrent-safe).
                // Total progress = baselineBytes + sum of all per-depot bytes.
                val perDepotBytes = java.util.concurrent.ConcurrentHashMap<Int, Long>()
                val bytesDownloaded = java.util.concurrent.atomic.AtomicLong(baselineBytes)
                val completedDepots = java.util.concurrent.atomic.AtomicInteger(0)

                Log.d(TAG, "Starting: $gameName (${depots.size} depots, ${totalSize / 1024 / 1024}MB, baseline=${baselineBytes / 1024 / 1024}MB)")
                Log.v(TAG, "depotSizes map: ${depotSizes.entries.joinToString { "${it.key}=${it.value / 1024 / 1024}MB" }}")

                val initialProgress = if (totalSize > 0) (baselineBytes.toFloat() / totalSize).coerceIn(0f, 1f) else 0f
                // Stay in Preparing until DepotDownloader starts actual chunk downloads.
                // The onStatusUpdate callback will transition through FetchingManifest/Validating.
                // The progress poller will flip to Downloading once bytes start flowing.
                val initialState = SteamDownloadState.Preparing(appId, gameName)
                _downloadState.value = initialState
                _activeDownload.value = SteamDownloadProgress(
                    appId, gameName, coverPath, initialProgress, totalSize, baselineBytes, initialState
                )

                // Load resume tracking data
                val completedDepotIds = downloadTracker.getCompletedDepotIds(appId)
                val completedFileNames = downloadTracker.getCompletedFileNames(appId)
                if (completedDepotIds.isNotEmpty() || completedFileNames.isNotEmpty()) {
                    Log.d(TAG, "Resume tracking: ${completedDepotIds.size} depots complete, ${completedFileNames.size} files pre-validated")
                }

                Log.d(TAG, "Loading licenses...")
                val licenses = steamLibraryManager.getLicenses()
                if (licenses.isEmpty()) {
                    throw IllegalStateException("No Steam licenses available")
                }

                // Thread pool sizes based on CPU cores (balanced for mobile)
                val cpuCores = Runtime.getRuntime().availableProcessors()
                val maxDownloads = (cpuCores * 1.2).toInt().coerceAtLeast(2)
                val maxDecompress = (cpuCores * 0.4).toInt().coerceAtLeast(2)
                Log.v(TAG, "CPU cores=$cpuCores, maxDownloads=$maxDownloads, maxDecompress=$maxDecompress")

                val depotDownloader = DepotDownloader(
                    client,
                    licenses,
                    debug = false,
                    androidEmulation = true,
                    maxDownloads = maxDownloads,
                    maxDecompress = maxDecompress,
                    parentJob = coroutineContext[Job]
                )
                activeDepotDownloader = depotDownloader

                val depotIds = depots.map { it.depotId }.sorted()
                val depotIdToIndex = depotIds.mapIndexed { index, id -> id to index }.toMap()

                depotDownloader.addListener(object : IDownloadListener {

                    override fun onItemAdded(item: DownloadItem) {
                        Log.v(TAG, "DepotDownloader: item ${item.appId} added")
                    }

                    override fun onDownloadStarted(item: DownloadItem) {
                        Log.v(TAG, "DepotDownloader: item ${item.appId} started (baseline=${baselineBytes / 1024 / 1024}MB)")
                    }

                    override fun onDownloadCompleted(item: DownloadItem) {
                        Log.i(TAG, "DepotDownloader: item ${item.appId} completed")
                    }

                    override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
                        Log.e(TAG, "DepotDownloader: item ${item.appId} failed", error)
                        _downloadState.value = SteamDownloadState.Failed(appId, gameName, error.message ?: "Download failed")
                    }

                    override fun onStatusUpdate(message: String) {
                        Log.v(TAG, "DepotDownloader status: $message")
                        // Only update _downloadState -- the progress poller is the single
                        // source of truth for _activeDownload to avoid racing values
                        when {
                            message.startsWith("Downloading manifest") -> {
                                val depotMatch = Regex("depot (\\d+)").find(message)
                                val depotId = depotMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                val state = SteamDownloadState.FetchingManifest(appId, gameName, depotId)
                                _downloadState.value = state
                                _activeDownload.value = _activeDownload.value?.copy(state = state)
                            }
                            message.startsWith("Validating") -> {
                                // Don't override Downloading state -- validation and download
                                // run concurrently during resume, causing UI flicker
                                if (_downloadState.value !is SteamDownloadState.Downloading) {
                                    val detail = message.removePrefix("Validating: ").take(60)
                                    val state = SteamDownloadState.Validating(appId, gameName, detail)
                                    _downloadState.value = state
                                    _activeDownload.value = _activeDownload.value?.copy(state = state)
                                }
                            }
                            message.startsWith("Finalizing") -> {
                                val state = SteamDownloadState.Validating(appId, gameName, "Finalizing...")
                                _downloadState.value = state
                                _activeDownload.value = _activeDownload.value?.copy(
                                    state = state,
                                    bytesPerSecond = 0L
                                )
                            }
                        }
                    }

                    override fun onFileCompleted(depotId: Int, fileName: String, pct: Float) {
                        Log.v(TAG, "DepotDownloader file completed: depot=$depotId, file=$fileName, pct=$pct")
                        val manifestId = depots.firstOrNull { it.depotId == depotId }?.manifestId ?: 0L
                        val installPrefix = installDir.absolutePath + "/"
                        val relativeName = if (fileName.startsWith(installPrefix)) {
                            fileName.removePrefix(installPrefix)
                        } else {
                            fileName
                        }
                        downloadTracker.onFileCompleted(appId, depotId, manifestId, relativeName)
                    }

                    override fun onChunkCompleted(
                        depotId: Int,
                        depotPercentComplete: Float,
                        compressedBytes: Long,
                        uncompressedBytes: Long
                    ) {
                        // uncompressedBytes is cumulative within this depot.
                        // Store per-depot and sum all depots for total progress.
                        perDepotBytes[depotId] = uncompressedBytes
                        val currentBytes = baselineBytes + perDepotBytes.values.sum()
                        bytesDownloaded.set(currentBytes)
                        val pctInt = (depotPercentComplete * 100).toInt()
                        if (pctInt % 25 == 0 && pctInt > 0) {
                            Log.v(TAG, "Chunk: depot=$depotId, depotPct=$pctInt%, depotBytes=${uncompressedBytes / 1024 / 1024}MB, total=${currentBytes / 1024 / 1024}MB/${totalSize / 1024 / 1024}MB")
                        }

                        val now = System.currentTimeMillis()
                        progressTracker.addSpeedSample(now, currentBytes)
                        val progress = if (totalSize > 0) (currentBytes.toFloat() / totalSize).coerceIn(0f, 1f) else 0f
                        val speed = progressTracker.computeSpeed(now)

                        val prevState = _downloadState.value
                        val state = SteamDownloadState.Downloading(
                            appId, gameName, progress, completedDepots.get(), depots.size
                        )
                        _downloadState.value = state
                        _activeDownload.value = SteamDownloadProgress(
                            appId, gameName, coverPath, progress, totalSize, currentBytes, state, speed
                        )

                        if (prevState !is SteamDownloadState.Downloading) {
                            scope.launch(Dispatchers.IO) {
                                steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.DOWNLOADING.name)
                            }
                        }
                        if (now - lastDbProgressUpdate > DB_PROGRESS_INTERVAL_MS) {
                            lastDbProgressUpdate = now
                            progressTracker.persistBytes(installDir.absolutePath, currentBytes)
                            scope.launch(Dispatchers.IO) {
                                steamDownloadQueueDao.updateProgress(appId, currentBytes, totalSize)
                            }
                        }
                    }

                    override fun onDepotCompleted(depotId: Int, compressedBytes: Long, uncompressedBytes: Long) {
                        val knownSize = depotSizes[depotId] ?: 0L
                        val currentTotal = bytesDownloaded.get()
                        Log.i(TAG, "Depot $depotId completed: " +
                            "depotBytes=${uncompressedBytes / 1024 / 1024}MB, " +
                            "manifestSize=${knownSize / 1024 / 1024}MB, " +
                            "totalProgress=${currentTotal / 1024 / 1024}MB, " +
                            "totalSize=${totalSize / 1024 / 1024}MB")

                        val count = completedDepots.incrementAndGet()
                        Log.d(TAG, "Depot count: $count/${depots.size} complete")
                        progressTracker.persistBytes(installDir.absolutePath, bytesDownloaded.get())
                        scope.launch(Dispatchers.IO) {
                            steamDownloadQueueDao.updateProgress(appId, bytesDownloaded.get(), totalSize)
                        }

                        val totalDownloaded = bytesDownloaded.get()
                        val progress = if (totalSize > 0) (totalDownloaded.toFloat() / totalSize).coerceIn(0f, 1f) else 0f

                        val state = if (count >= depots.size) {
                            SteamDownloadState.Validating(appId, gameName, "Unpacking...")
                        } else {
                            SteamDownloadState.Downloading(appId, gameName, progress, count, depots.size)
                        }
                        _downloadState.value = state
                        _activeDownload.value = SteamDownloadProgress(
                            appId, gameName, coverPath, progress, totalSize, totalDownloaded, state,
                            if (state is SteamDownloadState.Validating) 0L else progressTracker.computeSpeed(System.currentTimeMillis())
                        )
                        Log.v(TAG, "UI update: ${totalDownloaded / 1024 / 1024}MB / ${totalSize / 1024 / 1024}MB (${(progress * 100).toInt()}%) state=${state::class.simpleName}")

                        val depotManifestId = depots.firstOrNull { it.depotId == depotId }?.manifestId ?: 0L
                        downloadTracker.onDepotCompleted(appId, depotId, depotManifestId)
                    }
                })

                val appItem = AppItem(
                    appId.toInt(),
                    installDirectory = installDir.absolutePath,
                    depot = depotIds,
                    preValidatedFiles = if (completedFileNames.isNotEmpty()) completedFileNames else null
                    // completedDepotIds disabled: onDepotCompleted fires before all files
                    // are flushed to disk, so a cancel after depot callback leaves incomplete
                    // files. File-level pre-validation is safe; depot-level skipping needs
                    // a verification step before it can be trusted on resume.
                )
                depotDownloader.add(appItem)
                depotDownloader.finishAdding()
                downloadTracker.startPeriodicFlush()

                Log.i(TAG, "DepotDownloader running for $gameName -> ${installDir.absolutePath}")

                heartbeatJob = scope.launch(heartbeatDispatcher) {
                    while (true) {
                        kotlinx.coroutines.delay(2000)
                        if (_downloadState.value is SteamDownloadState.Downloading) {
                            val speed = progressTracker.computeSpeed(System.currentTimeMillis())
                            val current = _activeDownload.value
                            if (current != null && speed != current.bytesPerSecond) {
                                _activeDownload.value = current.copy(bytesPerSecond = speed)
                            }
                        }
                    }
                }

                depotDownloader.getCompletion().await()
                heartbeatJob?.cancel()
                downloadTracker.stopPeriodicFlush()
                depotDownloader.close()
                activeDepotDownloader = null

                if (isCancelled) {
                    val currentBytes = bytesDownloaded.get()
                    val progress = if (totalSize > 0) (currentBytes.toFloat() / totalSize).coerceIn(0f, 1f) else 0f
                    _downloadState.value = SteamDownloadState.Paused(appId, gameName, progress)
                    progressTracker.persistBytes(installDir.absolutePath, currentBytes)
                    return@launch
                }

                Log.d(TAG, "Download complete: $gameName at ${installDir.absolutePath}")

                // Files already at destination -- finalize
                gameDao.getBySteamAppId(appId)?.let { game ->
                    gameDao.update(game.copy(
                        localPath = installDir.absolutePath,
                        source = GameSource.STEAM,
                        addedAt = java.time.Instant.now()
                    ))
                }
                steamDownloadQueueDao.updateInstallPath(appId, installDir.absolutePath)
                steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.COMPLETED.name)
                Log.d(TAG, "DB updated: $gameName -> ${installDir.absolutePath}")

                File(installDir, ".download_complete").createNewFile()
                File(installDir, ".download_in_progress").delete()
                File(installDir, DOWNLOAD_INFO_DIR).mkdirs()
                scope.launch(Dispatchers.IO) {
                    File(installDir, ".DepotDownloader").let { if (it.exists()) it.deleteRecursively() }
                    progressTracker.clearPersistedBytes(installDir.absolutePath)
                    downloadTracker.clearForApp(appId)
                }

                val completedState = SteamDownloadState.Completed(appId, gameName, installDir.absolutePath)
                _downloadState.value = completedState

                val finalSize = bytesDownloaded.get().coerceAtLeast(totalSize)
                val completedProgress = SteamDownloadProgress(
                    appId, gameName, coverPath, 1f, finalSize, finalSize, completedState
                )
                _completedDownloads.value = _completedDownloads.value + completedProgress
                _activeDownload.value = null

                Log.d(TAG, "Download finalized: $gameName -> ${installDir.absolutePath}")

                // Notify RomM queue that a shared slot freed up
                downloadManager.get().onExternalSlotFreed()
                processNextInQueue()

            } catch (_: kotlinx.coroutines.CancellationException) {
                heartbeatJob?.cancel()
                downloadTracker.stopPeriodicFlush()
                activeDepotDownloader?.close()
                activeDepotDownloader = null
                val wasPaused = _downloadState.value is SteamDownloadState.Paused
                Log.d(TAG, "Download coroutine cancelled, wasPaused=$wasPaused")
                if (!wasPaused) {
                    _activeDownload.value = null
                    processNextInQueue()
                }
            } catch (e: Exception) {
                heartbeatJob?.cancel()
                downloadTracker.stopPeriodicFlush()
                activeDepotDownloader?.close()
                activeDepotDownloader = null
                Log.e(TAG, "Download failed: ${e.message}", e)
                val failedState = SteamDownloadState.Failed(appId, gameName, e.message ?: "Unknown error")
                _downloadState.value = failedState
                _activeDownload.value = _activeDownload.value?.copy(state = failedState)
                // Clear localPath if download_complete marker is missing
                gameDao.getBySteamAppId(appId)?.let { game ->
                    val path = game.localPath
                    if (path != null && !File(path, ".download_complete").exists()) {
                        gameDao.update(game.copy(localPath = null))
                    }
                }
                scope.launch(Dispatchers.IO) {
                    steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.FAILED.name, e.message)
                }
            }
        }
    }

    suspend fun checkForUpdate(appId: Long, currentBuildId: Long?, appInfo: KeyValue): Boolean {
        if (currentBuildId == null) return true

        val latestBuildId = appInfo["depots"]["branches"]["public"]["buildid"]
            ?.asString()?.toLongOrNull() ?: return false

        val hasUpdate = latestBuildId > currentBuildId
        Log.d(TAG, "Update check for $appId: current=$currentBuildId, latest=$latestBuildId, hasUpdate=$hasUpdate")
        return hasUpdate
    }

    fun getEstimatedSize(appInfo: KeyValue): Long {
        return depotManager.getEstimatedSize(appInfo)
    }

    suspend fun isGameInstalled(appId: Long): Boolean {
        return pathResolver.isGameInstalled(appId)
    }

    fun cancelDownload() {
        val activeAppId = _activeDownload.value?.appId
        Log.d(TAG, "cancelDownload called, activeAppId=$activeAppId")

        isCancelled = true
        activeDepotDownloader?.close()
        activeDepotDownloader = null
        currentDownloadJob?.cancel()
        currentDownloadJob = null
        progressTracker.resetSpeedTracking()

        val gameName = _activeDownload.value?.gameName ?: ""
        if (activeAppId != null) {
            _downloadState.value = SteamDownloadState.Cleaning(activeAppId, gameName)
            _activeDownload.value = _activeDownload.value?.copy(
                state = SteamDownloadState.Cleaning(activeAppId, gameName)
            )

            scope.launch {
                val game = gameDao.getBySteamAppId(activeAppId)
                val installPath = game?.localPath

                if (game != null && installPath != null) {
                    gameDao.update(game.copy(localPath = null))
                }

                kotlinx.coroutines.delay(3000)

                // Clean old staging directory if it exists (migration period)
                val oldStagingDir = File(context.filesDir, "steam_staging/$activeAppId")
                if (oldStagingDir.exists()) {
                    val deleted = oldStagingDir.deleteRecursively()
                    Log.d(TAG, "Deleted old staging dir: $deleted")
                }

                // Clean install path (destination directory)
                if (installPath != null) {
                    val destDir = File(installPath)
                    if (destDir.exists()) {
                        var deleted = destDir.deleteRecursively()
                        if (!deleted) {
                            kotlinx.coroutines.delay(3000)
                            deleted = destDir.deleteRecursively()
                        }
                        Log.d(TAG, "Deleted install dir: $deleted")
                    }
                }

                cleanupIncompleteDownload(activeAppId)
                downloadTracker.clearForApp(activeAppId)
                steamDownloadQueueDao.deleteByAppId(activeAppId)

                _downloadState.value = SteamDownloadState.Idle
                _activeDownload.value = null
                Log.d(TAG, "Cleanup complete for appId=$activeAppId")
            }
        } else {
            Log.w(TAG, "No activeAppId to clean up")
        }
    }

    fun cancelQueuedDownload(appId: Long) {
        _downloadQueue.value = _downloadQueue.value.filter { it.appId != appId }
        scope.launch(Dispatchers.IO) {
            steamDownloadQueueDao.deleteByAppId(appId)
        }
        Log.d(TAG, "Removed $appId from queue (new queue size: ${_downloadQueue.value.size})")
    }

    fun pauseDownload() {
        val active = _activeDownload.value ?: return
        val currentProgress = active.progress
        val currentState = _downloadState.value

        // Set paused state BEFORE cancelling job to prevent finally block from clearing it
        val needsVerification = currentState is SteamDownloadState.Preparing
        val pausedState = SteamDownloadState.Paused(active.appId, active.gameName, currentProgress, needsVerification)
        _downloadState.value = pausedState
        _activeDownload.value = active.copy(state = pausedState)

        isCancelled = true
        scope.launch(Dispatchers.IO) {
            downloadTracker.flushNow()
            val installPath = steamDownloadQueueDao.getByAppId(active.appId)?.installPath
            if (installPath != null) {
                progressTracker.persistBytes(installPath, active.bytesDownloaded)
            }
            steamDownloadQueueDao.updateState(active.appId, SteamDownloadDbState.PAUSED.name)
            steamDownloadQueueDao.updateProgress(active.appId, active.bytesDownloaded, active.totalBytes)
        }
        activeDepotDownloader?.close()
        activeDepotDownloader = null
        currentDownloadJob?.cancel()
        currentDownloadJob = null
        progressTracker.resetSpeedTracking()

        Log.d(TAG, "Download paused at ${(currentProgress * 100).toInt()}% (needsVerification=$needsVerification)")
    }

    suspend fun deleteGame(appId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val game = gameDao.getBySteamAppId(appId) ?: return@withContext false
            val installPath = game.localPath ?: return@withContext false
            val installDir = File(installPath)

            if (installDir.exists()) {
                installDir.deleteRecursively()
                Log.d(TAG, "Deleted game files: ${installDir.absolutePath}")
            }

            val stagingDir = File(context.filesDir, "steam_staging/$appId")
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
                Log.d(TAG, "Deleted staging: ${stagingDir.absolutePath}")
            }

            gameDao.update(game.copy(localPath = null))
            Log.d(TAG, "Cleared local path for game: ${game.title}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete game", e)
            false
        }
    }

    data class IncompleteDownload(
        val appId: Long,
        val installPath: String,
        val stagingPath: String,
        val bytesDownloaded: Long,
        val gameName: String?
    )

    suspend fun discoverLocalSteamGames(): Int = withContext(Dispatchers.IO) {
        var discovered = 0

        val steamDir = pathResolver.getSteamDir()
        if (steamDir.exists()) {
            val appDirs = steamDir.listFiles { file ->
                file.isDirectory && file.name.toLongOrNull() != null &&
                    !file.name.endsWith("_staging") &&
                    File(file, ".download_complete").exists()
            } ?: emptyArray()

            for (appDir in appDirs) {
                val appId = appDir.name.toLongOrNull() ?: continue

                val game = gameDao.getBySteamAppId(appId)
                if (game?.localPath != null) continue

                if (game != null) {
                    gameDao.update(game.copy(localPath = appDir.absolutePath, source = GameSource.STEAM))
                    Log.d(TAG, "Discovered Steam game: ${game.title} at ${appDir.absolutePath}")
                    discovered++
                }
            }
        }

        val gnBasePath = pathResolver.findGnStoragePath()
        if (gnBasePath != null) {
            val commonDir = File("$gnBasePath/Steam/steamapps/common")
            if (commonDir.exists()) {
                val gameDirs = commonDir.listFiles { file ->
                    file.isDirectory && File(file, ".download_complete").exists()
                } ?: emptyArray()

                val steamGames = gameDao.getAllWithSteamAppId()
                for (gameDir in gameDirs) {
                    val dirName = gameDir.name
                    // Match by sanitized title, or by Steam installdir from queue table
                    val match = steamGames.find { game ->
                        if (pathResolver.sanitizeGameName(game.title) == dirName) return@find true
                        val appId = game.steamAppId ?: return@find false
                        val queueEntry = steamDownloadQueueDao.getByAppId(appId)
                        queueEntry?.installDir == dirName
                    }
                    if (match != null && match.localPath == null) {
                        gameDao.update(match.copy(localPath = gameDir.absolutePath, source = GameSource.STEAM))
                        match.steamAppId?.let { appId ->
                            steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.COMPLETED.name)
                            steamDownloadQueueDao.updateInstallPath(appId, gameDir.absolutePath)
                        }
                        Log.d(TAG, "Discovered GN game: ${match.title} at ${gameDir.absolutePath}")
                        discovered++
                    }
                }
            }
        }

        Log.d(TAG, "Steam discovery complete: $discovered games recovered")
        discovered
    }

    suspend fun getIncompleteDownloads(): List<IncompleteDownload> = withContext(Dispatchers.IO) {
        val incomplete = mutableListOf<IncompleteDownload>()

        val steamDir = pathResolver.getSteamDir()
        if (steamDir.exists()) {
            val stagingDirs = steamDir.listFiles { file ->
                file.isDirectory && file.name.endsWith("_staging")
            } ?: emptyArray()

            for (stagingDir in stagingDirs) {
                val appIdStr = stagingDir.name.removeSuffix("_staging")
                val appId = appIdStr.toLongOrNull() ?: continue
                val installDir = File(steamDir, appIdStr)

                val bytesDownloaded = pathResolver.getDirectorySize(installDir) + pathResolver.getDirectorySize(stagingDir)
                val game = gameDao.getBySteamAppId(appId)

                incomplete.add(IncompleteDownload(
                    appId = appId,
                    installPath = installDir.absolutePath,
                    stagingPath = stagingDir.absolutePath,
                    bytesDownloaded = bytesDownloaded,
                    gameName = game?.title
                ))
            }
        }

        val internalSteamDir = File(context.filesDir, "steam_downloads")
        if (internalSteamDir.exists()) {
            val internalStagingDirs = internalSteamDir.listFiles { file ->
                file.isDirectory && file.name.endsWith("_staging")
            } ?: emptyArray()

            val internalAppDirs = internalSteamDir.listFiles { file ->
                file.isDirectory && !file.name.endsWith("_staging") && file.name.toLongOrNull() != null
            } ?: emptyArray()

            for (appDir in internalAppDirs) {
                val appId = appDir.name.toLongOrNull() ?: continue

                if (incomplete.any { it.appId == appId }) continue

                val game = gameDao.getBySteamAppId(appId)
                val isCompleted = game?.localPath?.let { File(it).exists() } ?: false
                if (isCompleted) continue

                val bytesDownloaded = pathResolver.getDirectorySize(appDir)
                if (bytesDownloaded < 1024 * 1024) continue // Skip if less than 1MB

                val stagingDir = File(internalSteamDir, "${appId}_staging")
                incomplete.add(IncompleteDownload(
                    appId = appId,
                    installPath = appDir.absolutePath,
                    stagingPath = stagingDir.absolutePath,
                    bytesDownloaded = bytesDownloaded,
                    gameName = game?.title
                ))
                Log.d(TAG, "Found paused download in internal storage: $appId (${bytesDownloaded / 1024 / 1024}MB)")
            }

            for (stagingDir in internalStagingDirs) {
                val appIdStr = stagingDir.name.removeSuffix("_staging")
                val appId = appIdStr.toLongOrNull() ?: continue

                if (incomplete.any { it.appId == appId }) continue

                val installDir = File(internalSteamDir, appIdStr)
                val bytesDownloaded = pathResolver.getDirectorySize(installDir) + pathResolver.getDirectorySize(stagingDir)
                val game = gameDao.getBySteamAppId(appId)

                incomplete.add(IncompleteDownload(
                    appId = appId,
                    installPath = installDir.absolutePath,
                    stagingPath = stagingDir.absolutePath,
                    bytesDownloaded = bytesDownloaded,
                    gameName = game?.title
                ))
            }
        }

        Log.d(TAG, "Found ${incomplete.size} incomplete Steam downloads")
        incomplete
    }

    suspend fun recoverDownload(appId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot recover download - not connected to Steam")
            return@withContext false
        }

        val game = gameDao.getBySteamAppId(appId)
        if (game == null) {
            Log.w(TAG, "Cannot recover download - game $appId not in database")
            return@withContext false
        }

        try {
            Log.d(TAG, "Recovering download for ${game.title} (appId: $appId)")
            val appInfo = fetchAppInfo(appId.toInt())
            queueDownload(appId, game.title, appInfo, game.coverPath)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recover download for $appId", e)
            false
        }
    }

    suspend fun cleanupIncompleteDownload(appId: Long): Boolean = withContext(Dispatchers.IO) {
        var cleaned = false

        val pathsToClean = mutableSetOf<File>()
        pathsToClean.add(pathResolver.getInstallDir(appId))
        gameDao.getBySteamAppId(appId)?.localPath?.let { pathsToClean.add(File(it)) }

        // Fallback storage path -- GN path may have changed since download started
        val steamDir = pathResolver.getSteamDir()
        pathsToClean.add(File(steamDir, appId.toString()))

        val stagingDir = File(context.filesDir, "steam_staging/$appId")

        Log.d(TAG, "Cleanup paths for $appId:")
        for (dir in pathsToClean) {
            Log.d(TAG, "  ${dir.absolutePath} (exists=${dir.exists()})")
            if (dir.exists()) {
                val deleted = dir.deleteRecursively()
                Log.d(TAG, "  Deleted: $deleted")
                cleaned = true
            }
        }

        if (stagingDir.exists()) {
            val deleted = stagingDir.deleteRecursively()
            Log.d(TAG, "  Staging ${stagingDir.absolutePath}: deleted=$deleted")
            cleaned = true
        }

        gameDao.getBySteamAppId(appId)?.let { game ->
            if (game.localPath != null) {
                gameDao.update(game.copy(localPath = null))
                Log.d(TAG, "Cleared local path in database for ${game.title}")
            }
        }

        cleaned
    }

    suspend fun recoverAllIncomplete(): Int = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot recover downloads - not connected to Steam")
            return@withContext 0
        }

        val incomplete = getIncompleteDownloads()
        var recovered = 0

        for (download in incomplete) {
            if (recoverDownload(download.appId)) {
                recovered++
            }
        }

        Log.d(TAG, "Recovered $recovered of ${incomplete.size} incomplete downloads")
        recovered
    }

    fun cleanup() {
        cancelDownload()
        heartbeatDispatcher.close()
        picsSubscription?.close()
        picsSubscription = null
        steamClient = null
        depotManager.clearHandlers()
    }
}
