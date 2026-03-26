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
import `in`.dragonbra.javasteam.steam.cdn.Client as CDNClient
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.DepotKeyCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSProductInfoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.util.log.LogManager
import kotlinx.coroutines.future.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
private const val STEAM_PLATFORM_DIR = "steam"
private const val GN_PACKAGE = "app.gamenative"
private const val WINDOWS_OS = "windows"
private const val ARCH_64 = "64"
private const val DOWNLOAD_INFO_DIR = ".DownloadInfo"
private const val BYTES_DOWNLOADED_FILE = "bytes_downloaded.txt"
private const val SPEED_WINDOW_MS = 30_000L
private const val SPEED_EMA_ALPHA = 0.3

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
    val appInfo: KeyValue?
)

@Singleton
class SteamContentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val steamAuthManager: SteamAuthManager,
    private val steamLibraryManager: SteamLibraryManager,
    private val androidDataAccessor: com.nendo.argosy.data.storage.AndroidDataAccessor,
    private val notificationManager: NotificationManager,
    private val steamDownloadQueueDao: SteamDownloadQueueDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val heartbeatDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "steam-heartbeat").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var steamClient: SteamClient? = null
    private var steamApps: SteamApps? = null
    private var steamContent: SteamContent? = null
    private var callbackManager: CallbackManager? = null
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

    private var currentDownloadJob: kotlinx.coroutines.Job? = null
    private var isCancelled = false
    private val appInfoCache = ConcurrentHashMap<Int, KeyValue>()
    private var lastDbProgressUpdate = 0L
    private companion object {
        const val DB_PROGRESS_INTERVAL_MS = 30_000L
    }

    private data class SpeedSample(val timeMs: Long, val totalBytes: Long)
    private val speedSamples = CopyOnWriteArrayList<SpeedSample>()
    private var emaSpeedBps: Double = 0.0
    private var hasEmaSpeed: Boolean = false

    init {
        scope.launch {
            restoreSteamQueueFromDatabase()
        }
    }

    private suspend fun restoreSteamQueueFromDatabase() = withContext(Dispatchers.IO) {
        steamDownloadQueueDao.clearFinished()

        val pending = steamDownloadQueueDao.getPendingDownloads()
        if (pending.isEmpty()) {
            backfillFromFilesystem()
            return@withContext
        }

        for (entity in pending) {
            if (entity.state in listOf(SteamDownloadDbState.DOWNLOADING.name, SteamDownloadDbState.PREPARING.name)) {
                steamDownloadQueueDao.updateState(entity.appId, SteamDownloadDbState.PAUSED.name)
            }
        }

        val paused = pending.filter {
            it.state in listOf(SteamDownloadDbState.PAUSED.name, SteamDownloadDbState.DOWNLOADING.name, SteamDownloadDbState.PREPARING.name)
        }
        val queued = pending.filter { it.state == SteamDownloadDbState.QUEUED.name }

        if (paused.isNotEmpty()) {
            val primary = paused.first()
            val bytesDownloaded = primary.installPath?.let { loadPersistedBytes(it) } ?: primary.bytesDownloaded
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
                QueuedSteamDownload(entity.appId, entity.gameName, entity.coverPath, null)
            }
        } else if (queued.isNotEmpty()) {
            _downloadQueue.value = queued.map { entity ->
                QueuedSteamDownload(entity.appId, entity.gameName, entity.coverPath, null)
            }
        }

        Log.d(TAG, "Restored ${paused.size} paused + ${queued.size} queued from DB")
    }

    private suspend fun backfillFromFilesystem() {
        val games = gameDao.getAllWithSteamAppId().filter { game ->
            val path = game.localPath ?: return@filter false
            File(path, ".download_in_progress").exists()
        }
        if (games.isEmpty()) return

        for (game in games) {
            val appId = game.steamAppId ?: continue
            val path = game.localPath ?: continue
            steamDownloadQueueDao.insert(SteamDownloadQueueEntity(
                appId = appId,
                gameName = game.title,
                coverPath = game.coverPath,
                installDir = null,
                installPath = path,
                totalBytes = 0L,
                bytesDownloaded = loadPersistedBytes(path),
                state = SteamDownloadDbState.PAUSED.name,
                errorReason = null
            ))
        }
        Log.d(TAG, "Backfilled ${games.size} downloads from filesystem to DB")

        val primary = games.first()
        val pausedState = SteamDownloadState.Paused(primary.steamAppId!!, primary.title, 0f, needsVerification = true)
        _downloadState.value = pausedState
        _activeDownload.value = SteamDownloadProgress(
            appId = primary.steamAppId,
            gameName = primary.title,
            coverPath = primary.coverPath,
            progress = 0f,
            totalBytes = 0L,
            bytesDownloaded = loadPersistedBytes(primary.localPath!!),
            state = pausedState
        )

        val rest = games.drop(1)
        if (rest.isNotEmpty()) {
            _downloadQueue.value = rest.map { game ->
                QueuedSteamDownload(game.steamAppId!!, game.title, game.coverPath, null)
            }
        }
    }

    fun initialize(client: SteamClient, apps: SteamApps, cm: CallbackManager) {
        steamClient = client
        steamApps = apps
        steamContent = client.getHandler(SteamContent::class.java)
        callbackManager = cm
        Log.d(TAG, "SteamContentManager initialized")
    }

    suspend fun restorePausedDownloads() {
        val active = _activeDownload.value ?: return
        if (active.state !is SteamDownloadState.Paused) return
        if (!isConnected() || currentDownloadJob?.isActive == true) return

        Log.d(TAG, "Auto-resuming ${active.gameName} (Steam connected)")
        queueDownloadOptimistic(active.appId, active.gameName, active.coverPath)
    }

    fun isConnected(): Boolean {
        val hasApps = steamApps != null
        val hasCm = callbackManager != null
        val loggedIn = steamAuthManager.isLoggedIn.value
        Log.d(TAG, "isConnected check: hasApps=$hasApps, hasCm=$hasCm, loggedIn=$loggedIn")
        return hasApps && hasCm && loggedIn
    }

    fun onDisconnected() {
        Log.d(TAG, "Steam disconnected, clearing handlers")
        val state = _downloadState.value
        if (state !is SteamDownloadState.Idle && state !is SteamDownloadState.Paused &&
            state !is SteamDownloadState.Completed && state !is SteamDownloadState.Failed) {
            pauseDownload()
        }
        scope.launch(Dispatchers.IO) {
            val pending = steamDownloadQueueDao.getPendingDownloads()
            pending.filter { it.state in listOf(SteamDownloadDbState.DOWNLOADING.name, SteamDownloadDbState.PREPARING.name) }.forEach {
                steamDownloadQueueDao.updateState(it.appId, SteamDownloadDbState.PAUSED.name)
            }
        }
        steamClient = null
        steamApps = null
        callbackManager = null
    }

    fun getWindowsDepots(appInfo: KeyValue): List<DepotInfo> {
        val depots = mutableListOf<DepotInfo>()
        val depotsKv = appInfo["depots"] ?: return emptyList()

        for (child in depotsKv.children) {
            val depotIdStr = child.name ?: continue
            val depotId = depotIdStr.toIntOrNull() ?: continue

            // Skip special depots (branches, baselines, etc.)
            if (depotId < 1000) continue

            val config = child["config"]
            val oslist = config["oslist"]?.asString()?.lowercase()
            val osarch = config["osarch"]?.asString()

            if (oslist != null && !oslist.contains(WINDOWS_OS)) {
                Log.d(TAG, "Skipping depot $depotId: OS=$oslist (not Windows)")
                continue
            }

            // Prefer 64-bit, but accept if no arch specified
            if (osarch != null && osarch != ARCH_64 && osarch != "32") {
                Log.d(TAG, "Skipping depot $depotId: arch=$osarch")
                continue
            }

            val manifests = child["manifests"]
            val publicManifest = manifests?.get("public")

            var manifestId = publicManifest?.asString()?.toLongOrNull()
                ?: publicManifest?.value?.toString()?.toLongOrNull()
                ?: 0L

            if (manifestId == 0L) {
                manifestId = publicManifest?.get("gid")?.asString()?.toLongOrNull() ?: 0L
            }

            if (manifestId == 0L && manifests != null) {
                Log.d(TAG, "Depot $depotId manifests children: ${manifests.children.map { "${it.name}=${it.value}" }}")
                if (publicManifest != null) {
                    Log.d(TAG, "Depot $depotId public manifest: value=${publicManifest.value}, children=${publicManifest.children.map { "${it.name}=${it.value}" }}")
                }
            }

            // Skip depots with no manifest (nothing to download)
            if (manifestId == 0L) {
                Log.d(TAG, "Skipping depot $depotId: no manifest")
                continue
            }

            val name = child["name"]?.asString() ?: "Depot $depotId"
            val maxSize = child["maxsize"]?.asString()?.toLongOrNull() ?: 0L

            depots.add(
                DepotInfo(
                    depotId = depotId,
                    manifestId = manifestId,
                    name = name,
                    os = oslist,
                    arch = osarch,
                    size = maxSize
                )
            )

            Log.d(TAG, "Found depot $depotId: $name (os=$oslist, arch=$osarch, manifest=$manifestId, size=${maxSize / 1024 / 1024}MB)")
        }

        Log.d(TAG, "Found ${depots.size} Windows depots total")
        return depots
    }

    private suspend fun filterAccessibleDepots(appId: Int, depots: List<DepotInfo>): List<DepotInfo> = withContext(Dispatchers.IO) {
        val apps = steamApps ?: return@withContext depots
        val cm = callbackManager ?: return@withContext depots

        depots.filter { depot ->
            if (depot.manifestId == 0L) return@filter false
            val key = getDepotKey(apps, cm, depot.depotId, appId)
            if (key == null) {
                Log.d(TAG, "Depot ${depot.depotId} not accessible (no key)")
                false
            } else {
                true
            }
        }
    }

    data class DepotSizeResult(val totalSize: Long, val accessibleDepotIds: List<Int>)

    suspend fun fetchDepotSizes(appId: Int, depots: List<DepotInfo>): DepotSizeResult = withContext(Dispatchers.IO) {
        val client = steamClient ?: return@withContext DepotSizeResult(0L, emptyList())
        val content = steamContent ?: return@withContext DepotSizeResult(0L, emptyList())
        val apps = steamApps ?: return@withContext DepotSizeResult(0L, emptyList())
        val cm = callbackManager ?: return@withContext DepotSizeResult(0L, emptyList())

        Log.d(TAG, "Fetching depot sizes for ${depots.size} depots (max 4 concurrent)")

        val servers = content.getServersForSteamPipe(null, null, scope).await()
        if (servers.isEmpty()) return@withContext DepotSizeResult(0L, emptyList())

        val cdnClient = CDNClient(client)
        val semaphore = Semaphore(4)
        val results = depots.filter { it.manifestId != 0L }.map { depot ->
            scope.async {
                semaphore.withPermit {
                    try {
                        val depotKey = getDepotKey(apps, cm, depot.depotId, appId) ?: return@withPermit null
                        val requestCode = content.getManifestRequestCode(
                            depot.depotId, appId, depot.manifestId, "public", null, scope
                        ).await()
                        if (requestCode.toLong() == 0L) return@withPermit null

                        val server = servers.random()
                        val authToken = content.getCDNAuthToken(appId, depot.depotId, server.host ?: "", scope).await()
                        val manifest = cdnClient.downloadManifestFuture(
                            depot.depotId, depot.manifestId, requestCode.toLong(), server, depotKey, null, authToken.token
                        ).await()

                        val size = manifest.totalUncompressedSize
                        Log.d(TAG, "Depot ${depot.depotId}: ${size / 1024 / 1024}MB")
                        depot.depotId to size
                    } catch (e: Exception) {
                        Log.w(TAG, "Depot ${depot.depotId} size fetch failed: ${e.message}")
                        null
                    }
                }
            }
        }

        val completed = results.mapNotNull { it.await() }
        val totalSize = completed.sumOf { it.second }
        val accessible = completed.map { it.first }

        Log.d(TAG, "Total: ${totalSize / 1024 / 1024}MB (${accessible.size}/${depots.size} depots)")
        DepotSizeResult(totalSize, accessible)
    }

    private suspend fun getDepotKey(apps: SteamApps, cm: CallbackManager, depotId: Int, appId: Int): ByteArray? {
        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { continuation ->
                var subscription: Closeable? = null
                subscription = cm.subscribe(DepotKeyCallback::class.java) { callback ->
                    if (callback.depotID == depotId) {
                        subscription?.close()
                        if (callback.result == `in`.dragonbra.javasteam.enums.EResult.OK) {
                            continuation.resume(callback.depotKey)
                        } else {
                            continuation.resume(null)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    subscription.close()
                }

                apps.getDepotDecryptionKey(depotId, appId)
            }
        }
    }

    suspend fun ensureConnected(): Boolean {
        if (steamClient != null && steamApps != null && steamAuthManager.isLoggedIn.value) return true

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
            if (steamClient != null && steamApps != null && steamAuthManager.isLoggedIn.value) {
                Log.d(TAG, "Steam connected after waiting")
                return true
            }
            kotlinx.coroutines.delay(500)
        }
        Log.e(TAG, "Steam connection timeout (client=${steamClient != null}, apps=${steamApps != null}, loggedIn=${steamAuthManager.isLoggedIn.value})")
        return false
    }

    fun notifyConnected() {
        Log.d(TAG, "notifyConnected called")
    }

    suspend fun fetchAppInfo(appId: Int): KeyValue {
        appInfoCache[appId]?.let {
            Log.d(TAG, "AppInfo cache hit for app $appId")
            return it
        }

        return withTimeout(30_000L) {
            if (steamApps == null || callbackManager == null) {
                if (!ensureConnected()) {
                    throw IllegalStateException("Steam not connected")
                }
            }

            suspendCancellableCoroutine { continuation ->
                val apps = steamApps
                val cm = callbackManager

                if (apps == null || cm == null) {
                    continuation.resumeWithException(IllegalStateException("Steam not connected"))
                    return@suspendCancellableCoroutine
                }

                Log.d(TAG, "Fetching PICS info for app $appId")

                var subscription: Closeable? = null
                subscription = cm.subscribe(PICSProductInfoCallback::class.java) { callback ->
                    val appInfo = callback.apps[appId]
                    if (appInfo != null) {
                        Log.d(TAG, "Received PICS info for app $appId")
                        subscription?.close()
                        appInfoCache[appId] = appInfo.keyValues
                        continuation.resume(appInfo.keyValues)
                    }
                }

                continuation.invokeOnCancellation {
                    subscription.close()
                }

                try {
                    apps.picsGetProductInfo(PICSRequest(appId), null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request PICS info for app $appId", e)
                    subscription.close()
                    continuation.resumeWithException(e)
                }
            }
        }
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
            try {
                steamDownloadQueueDao.insert(SteamDownloadQueueEntity(
                    appId = appId,
                    gameName = gameName,
                    coverPath = coverPath,
                    installDir = null,
                    installPath = null,
                    totalBytes = 0L,
                    bytesDownloaded = 0L,
                    state = SteamDownloadDbState.QUEUED.name,
                    errorReason = null
                ))
                Log.d(TAG, "Persisted queue entry: $gameName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist queue entry for $gameName", e)
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
            try {
                steamDownloadQueueDao.insert(SteamDownloadQueueEntity(
                    appId = appId,
                    gameName = gameName,
                    coverPath = coverPath,
                    installDir = installDirName,
                    installPath = null,
                    totalBytes = 0L,
                    bytesDownloaded = 0L,
                    state = SteamDownloadDbState.QUEUED.name,
                    errorReason = null
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist queue entry for $gameName", e)
            }
        }

        val queued = QueuedSteamDownload(appId, gameName, coverPath, appInfo)
        _downloadQueue.value = _downloadQueue.value + queued
        Log.d(TAG, "Queued Steam download: $gameName (queue size: ${_downloadQueue.value.size})")

        if (currentDownloadJob?.isActive != true) {
            processNextInQueue()
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
            startDownload(next.appId, next.gameName, appInfo, next.coverPath)
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
        coverPath: String?
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
        resetSpeedTracking()

        currentDownloadJob = scope.launch {
            var heartbeatJob: Job? = null
            try {
                _downloadState.value = SteamDownloadState.Preparing(appId, gameName)

                // Resolve final destination (GN SD card path or fallback)
                val steamInstallDir = appInfo["config"]?.get("installdir")?.asString()
                val finalDir = if (steamInstallDir != null) {
                    getInstallDirByName(steamInstallDir)
                } else {
                    getInstallDir(appId)
                }

                // Download to internal app storage (fast I/O), move to destination after
                val stagingDir = File(context.filesDir, "steam_staging/$appId")
                stagingDir.mkdirs()
                Log.d(TAG, "Staging path for $appId: ${stagingDir.absolutePath}")
                Log.d(TAG, "Final path for $appId: ${finalDir.absolutePath}")

                // Preserve .DepotDownloader if resuming (has in-progress marker)
                // Clean it only for fresh downloads (no marker = cancelled or new)
                val isResume = File(stagingDir, ".download_in_progress").exists()
                val depotDir = File(stagingDir, ".DepotDownloader")
                if (depotDir.exists() && !isResume) {
                    depotDir.deleteRecursively()
                    Log.d(TAG, "Cleaned stale .DepotDownloader state (fresh download)")
                }

                File(stagingDir, ".download_complete").delete()
                File(stagingDir, ".download_in_progress").createNewFile()

                // Store staging path so restore/cleanup can find it
                gameDao.getBySteamAppId(appId)?.let { game ->
                    if (game.localPath != stagingDir.absolutePath) {
                        gameDao.update(game.copy(localPath = stagingDir.absolutePath))
                    }
                }

                steamDownloadQueueDao.updateInstallPath(appId, stagingDir.absolutePath)
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

                val allDepots = getWindowsDepots(appInfo)
                if (allDepots.isEmpty()) {
                    throw IllegalStateException("No Windows depots found for this game")
                }

                val sizeResult = fetchDepotSizes(appId.toInt(), allDepots)
                val totalSize = sizeResult.totalSize
                val depots = if (sizeResult.accessibleDepotIds.isNotEmpty()) {
                    allDepots.filter { it.depotId in sizeResult.accessibleDepotIds }
                } else {
                    allDepots
                }
                if (depots.isEmpty()) {
                    throw IllegalStateException("No accessible depots for this game")
                }
                Log.d(TAG, "Total: ${totalSize / 1024 / 1024}MB for $gameName (${depots.size}/${allDepots.size} depots)")

                val baselineBytes = loadPersistedBytes(stagingDir.absolutePath)
                val bytesDownloaded = java.util.concurrent.atomic.AtomicLong(baselineBytes)
                val completedDepots = java.util.concurrent.atomic.AtomicInteger(0)

                Log.d(TAG, "Starting: $gameName (${depots.size} depots, ${totalSize / 1024 / 1024}MB, baseline=${baselineBytes / 1024 / 1024}MB)")

                val initialProgress = if (totalSize > 0) (baselineBytes.toFloat() / totalSize).coerceIn(0f, 1f) else 0f
                // Stay in Preparing until DepotDownloader starts actual chunk downloads.
                // The onStatusUpdate callback will transition through FetchingManifest/Validating.
                // The progress poller will flip to Downloading once bytes start flowing.
                val initialState = SteamDownloadState.Preparing(appId, gameName)
                _downloadState.value = initialState
                _activeDownload.value = SteamDownloadProgress(
                    appId, gameName, coverPath, initialProgress, totalSize, baselineBytes, initialState
                )

                Log.d(TAG, "Loading licenses...")
                val licenses = steamLibraryManager.getLicenses()
                if (licenses.isEmpty()) {
                    throw IllegalStateException("No Steam licenses available")
                }

                // Thread pool sizes based on CPU cores (balanced for mobile)
                val cpuCores = Runtime.getRuntime().availableProcessors()
                val maxDownloads = (cpuCores * 1.2).toInt().coerceAtLeast(2)
                val maxDecompress = (cpuCores * 0.4).toInt().coerceAtLeast(2)
                Log.d(TAG, "CPU cores=$cpuCores, maxDownloads=$maxDownloads, maxDecompress=$maxDecompress")

                val depotDownloader = DepotDownloader(
                    client,
                    licenses,
                    debug = false,
                    androidEmulation = true,
                    maxDownloads = maxDownloads,
                    maxDecompress = maxDecompress,
                    parentJob = coroutineContext[Job],
                    autoStartDownload = false
                )
                activeDepotDownloader = depotDownloader

                val depotIds = depots.map { it.depotId }.sorted()
                val depotIdToIndex = depotIds.mapIndexed { index, id -> id to index }.toMap()

                depotDownloader.addListener(object : IDownloadListener {

                    override fun onItemAdded(item: DownloadItem) {
                        Log.d(TAG, "DepotDownloader: item ${item.appId} added")
                    }

                    override fun onDownloadStarted(item: DownloadItem) {
                        Log.d(TAG, "DepotDownloader: item ${item.appId} started (baseline=${baselineBytes / 1024 / 1024}MB)")
                    }

                    override fun onDownloadCompleted(item: DownloadItem) {
                        Log.i(TAG, "DepotDownloader: item ${item.appId} completed")
                    }

                    override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
                        Log.e(TAG, "DepotDownloader: item ${item.appId} failed", error)
                        _downloadState.value = SteamDownloadState.Failed(appId, gameName, error.message ?: "Download failed")
                    }

                    override fun onStatusUpdate(message: String) {
                        Log.d(TAG, "DepotDownloader status: $message")
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
                                val detail = message.removePrefix("Validating: ").take(60)
                                val state = SteamDownloadState.Validating(appId, gameName, detail)
                                _downloadState.value = state
                                _activeDownload.value = _activeDownload.value?.copy(state = state)
                            }
                        }
                    }

                    override fun onFileCompleted(depotId: Int, fileName: String, pct: Float) {
                        Log.d(TAG, "DepotDownloader file completed: depot=$depotId, file=$fileName, pct=$pct")
                    }

                    override fun onChunkCompleted(
                        depotId: Int,
                        depotPercentComplete: Float,
                        compressedBytes: Long,
                        uncompressedBytes: Long
                    ) {
                        val currentBytes = baselineBytes + uncompressedBytes
                        bytesDownloaded.set(currentBytes)

                        val now = System.currentTimeMillis()
                        addSpeedSample(now, currentBytes)
                        val progress = if (totalSize > 0) (currentBytes.toFloat() / totalSize).coerceIn(0f, 1f) else 0f
                        val speed = computeSpeed(now)

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
                            scope.launch(Dispatchers.IO) {
                                steamDownloadQueueDao.updateProgress(appId, currentBytes, totalSize)
                            }
                        }
                    }

                    override fun onDepotCompleted(depotId: Int, compressedBytes: Long, uncompressedBytes: Long) {
                        Log.i(TAG, "Depot $depotId completed: ${uncompressedBytes / 1024 / 1024}MB, bytesDownloaded=${bytesDownloaded.get() / 1024 / 1024}MB / ${totalSize / 1024 / 1024}MB")

                        val count = completedDepots.incrementAndGet()
                        persistBytes(stagingDir.absolutePath, bytesDownloaded.get())
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
                            if (state is SteamDownloadState.Validating) 0L else computeSpeed(System.currentTimeMillis())
                        )
                        Log.d(TAG, "UI update: ${totalDownloaded / 1024 / 1024}MB / ${totalSize / 1024 / 1024}MB (${(progress * 100).toInt()}%) state=${state::class.simpleName}")
                    }
                })

                val appItem = AppItem(
                    appId.toInt(),
                    installDirectory = stagingDir.absolutePath,
                    depot = depotIds
                )
                depotDownloader.add(appItem)
                depotDownloader.finishAdding()
                depotDownloader.startDownloading()

                Log.i(TAG, "DepotDownloader running for $gameName -> staging: ${stagingDir.absolutePath}, final: ${finalDir.absolutePath}")

                heartbeatJob = scope.launch(heartbeatDispatcher) {
                    while (true) {
                        kotlinx.coroutines.delay(2000)
                        if (_downloadState.value is SteamDownloadState.Downloading) {
                            val speed = computeSpeed(System.currentTimeMillis())
                            val current = _activeDownload.value
                            if (current != null && speed != current.bytesPerSecond) {
                                _activeDownload.value = current.copy(bytesPerSecond = speed)
                            }
                        }
                    }
                }

                depotDownloader.getCompletion().await()
                heartbeatJob?.cancel()
                depotDownloader.close()
                activeDepotDownloader = null

                if (isCancelled) {
                    val currentBytes = bytesDownloaded.get()
                    val progress = if (totalSize > 0) (currentBytes.toFloat() / totalSize).coerceIn(0f, 1f) else 0f
                    _downloadState.value = SteamDownloadState.Paused(appId, gameName, progress)
                    persistBytes(stagingDir.absolutePath, currentBytes)
                    return@launch
                }

                Log.d(TAG, "Download complete in staging: ${stagingDir.absolutePath}")

                // Clean up DepotDownloader state before moving
                File(stagingDir, ".download_in_progress").delete()
                File(stagingDir, ".DepotDownloader").let { if (it.exists()) it.deleteRecursively() }
                clearPersistedBytes(stagingDir.absolutePath)

                // Move from staging (internal fast storage) to final destination (GN/SD card)
                _downloadState.value = SteamDownloadState.Moving(appId, gameName)
                _activeDownload.value = _activeDownload.value?.copy(
                    state = SteamDownloadState.Moving(appId, gameName),
                    bytesPerSecond = 0L
                )
                Log.d(TAG, "Moving from staging to final: ${finalDir.absolutePath}")

                finalDir.mkdirs()
                val moved = moveDirectory(stagingDir, finalDir)
                if (!moved) {
                    throw IllegalStateException("Failed to move download from staging to ${finalDir.absolutePath}")
                }

                File(finalDir, ".download_complete").createNewFile()
                File(finalDir, DOWNLOAD_INFO_DIR).mkdirs()
                Log.d(TAG, "Created GN markers at ${finalDir.absolutePath}")

                gameDao.getBySteamAppId(appId)?.let { game ->
                    gameDao.update(game.copy(
                        localPath = finalDir.absolutePath,
                        source = GameSource.STEAM,
                        addedAt = java.time.Instant.now()
                    ))
                }

                steamDownloadQueueDao.updateInstallPath(appId, finalDir.absolutePath)

                val completedState = SteamDownloadState.Completed(appId, gameName, finalDir.absolutePath)
                _downloadState.value = completedState

                val finalSize = bytesDownloaded.get().coerceAtLeast(totalSize)
                val completedProgress = SteamDownloadProgress(
                    appId, gameName, coverPath, 1f, finalSize, finalSize, completedState
                )
                _completedDownloads.value = _completedDownloads.value + completedProgress

                steamDownloadQueueDao.updateState(appId, SteamDownloadDbState.COMPLETED.name)
                Log.d(TAG, "Download complete: $gameName -> ${finalDir.absolutePath}")

                processNextInQueue()

            } catch (_: kotlinx.coroutines.CancellationException) {
                heartbeatJob?.cancel()
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
                activeDepotDownloader?.close()
                activeDepotDownloader = null
                Log.e(TAG, "Download failed: ${e.message}", e)
                val failedState = SteamDownloadState.Failed(appId, gameName, e.message ?: "Unknown error")
                _downloadState.value = failedState
                _activeDownload.value = _activeDownload.value?.copy(state = failedState)
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
        val depots = getWindowsDepots(appInfo)
        return depots.sumOf { it.size }
    }

    private suspend fun getInstallDirByName(dirName: String): File {
        val gnBasePath = findGnStoragePath()
        if (gnBasePath != null) {
            return File("$gnBasePath/Steam/steamapps/common/$dirName")
        }
        val prefs = preferencesRepository.userPreferences.first()
        val basePath = prefs.romStoragePath
            ?: context.getExternalFilesDir(null)?.absolutePath
            ?: context.filesDir.absolutePath
        return File(basePath, "$STEAM_PLATFORM_DIR/$dirName")
    }

    suspend fun getInstallDir(appId: Long): File {
        // Prefer GameNative path for GN-compatible installs
        val gnPath = findGnInstallPath(appId)
        if (gnPath != null) return gnPath

        val prefs = preferencesRepository.userPreferences.first()
        val basePath = prefs.romStoragePath
            ?: context.getExternalFilesDir(null)?.absolutePath
            ?: context.filesDir.absolutePath

        return File(basePath, "$STEAM_PLATFORM_DIR/$appId")
    }

    private suspend fun findGnInstallPath(appId: Long): File? {
        val gameName = gameDao.getBySteamAppId(appId)?.title ?: return null
        val gnBasePath = findGnStoragePath() ?: return null
        val sanitized = sanitizeGameName(gameName)
        return File("$gnBasePath/Steam/steamapps/common/$sanitized")
    }

    private fun findGnStoragePath(): String? {
        val volumes = mutableListOf(android.os.Environment.getExternalStorageDirectory().absolutePath)
        try {
            File("/storage").listFiles()?.forEach { vol ->
                if (vol.isDirectory && vol.name != "emulated" && vol.name != "self") {
                    volumes.add(vol.absolutePath)
                }
            }
        } catch (_: Exception) {}

        for (root in volumes) {
            val basePath = "$root/Android/data/$GN_PACKAGE/files"
            val steamappsPath = "$basePath/Steam/steamapps"

            // Try raw path first (works on SD cards with MANAGE_EXTERNAL_STORAGE)
            if (File(steamappsPath).exists()) {
                return basePath
            }

            // Fall back to alt-access path (needed for internal storage on Android 11+)
            if (androidDataAccessor.exists(steamappsPath)) {
                return androidDataAccessor.transformPath(basePath)
            }
        }
        return null
    }

    private fun sanitizeGameName(name: String): String {
        return name.replace(Regex("[<>:\"/\\\\|?*]"), "_").trim()
    }


    suspend fun isGameInstalled(appId: Long): Boolean {
        val game = gameDao.getBySteamAppId(appId)
        val path = game?.localPath ?: return false
        return File(path, ".download_complete").exists() || androidDataAccessor.exists("$path/.download_complete")
    }

    suspend fun isGameDownloading(appId: Long): Boolean {
        val game = gameDao.getBySteamAppId(appId)
        val path = game?.localPath ?: return false
        return File(path, ".download_in_progress").exists() || androidDataAccessor.exists("$path/.download_in_progress")
    }

    private fun getDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var totalSize = 0L
        var fileCount = 0
        dir.walkTopDown()
            .onEnter { it.name != ".DepotDownloader" && it.name != DOWNLOAD_INFO_DIR }
            .forEach { file ->
                if (file.isFile && !file.name.startsWith(".download_")) {
                    totalSize += file.length()
                    fileCount++
                }
            }
        if (fileCount > 0 || totalSize > 0) {
            Log.v(TAG, "getDirectorySize(${dir.name}): $fileCount files, ${totalSize / 1024 / 1024}MB")
        }
        return totalSize
    }

    private fun moveDirectory(source: File, destination: File): Boolean {
        try {
            if (source.renameTo(destination)) {
                Log.d(TAG, "Moved directory via rename")
                return true
            }

            Log.d(TAG, "Rename failed, copying files to destination...")
            destination.mkdirs()

            var filesCopied = 0
            var bytesCopied = 0L

            source.walkTopDown().forEach { file ->
                val relativePath = file.relativeTo(source).path
                val destFile = File(destination, relativePath)

                if (file.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    file.copyTo(destFile, overwrite = true)
                    filesCopied++
                    bytesCopied += file.length()

                    if (filesCopied % 100 == 0) {
                        Log.d(TAG, "Copied $filesCopied files (${bytesCopied / 1024 / 1024}MB)...")
                    }
                }
            }

            Log.d(TAG, "Copied $filesCopied files (${bytesCopied / 1024 / 1024}MB) to destination")

            val destSize = getDirectorySize(destination)
            if (destSize == 0L) {
                Log.e(TAG, "Destination directory is empty after copy!")
                return false
            }

            source.deleteRecursively()
            Log.d(TAG, "Deleted source directory after move")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move directory: ${e.message}", e)
            return false
        }
    }

    fun cancelDownload() {
        val activeAppId = _activeDownload.value?.appId
        Log.d(TAG, "cancelDownload called, activeAppId=$activeAppId")

        isCancelled = true
        activeDepotDownloader?.close()
        activeDepotDownloader = null
        currentDownloadJob?.cancel()
        currentDownloadJob = null
        resetSpeedTracking()

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

                // Clean staging directory (internal storage)
                val stagingDir = File(context.filesDir, "steam_staging/$activeAppId")
                if (stagingDir.exists()) {
                    val deleted = stagingDir.deleteRecursively()
                    Log.d(TAG, "Deleted staging dir: $deleted")
                }

                // Clean final install path if anything was partially moved
                if (installPath != null) {
                    val installDir = File(installPath)
                    if (installDir.exists()) {
                        var deleted = installDir.deleteRecursively()
                        if (!deleted) {
                            kotlinx.coroutines.delay(3000)
                            deleted = installDir.deleteRecursively()
                        }
                        Log.d(TAG, "Deleted install dir: $deleted")
                    }
                }

                cleanupIncompleteDownload(activeAppId)
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
            val stagingDir = File(context.filesDir, "steam_staging/${active.appId}")
            persistBytes(stagingDir.absolutePath, active.bytesDownloaded)
            steamDownloadQueueDao.updateState(active.appId, SteamDownloadDbState.PAUSED.name)
            steamDownloadQueueDao.updateProgress(active.appId, active.bytesDownloaded, active.totalBytes)
        }
        activeDepotDownloader?.close()
        activeDepotDownloader = null
        currentDownloadJob?.cancel()
        currentDownloadJob = null
        resetSpeedTracking()

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

    suspend fun getSteamDir(): File {
        val prefs = preferencesRepository.userPreferences.first()
        val basePath = prefs.romStoragePath
            ?: context.getExternalFilesDir(null)?.absolutePath
            ?: context.filesDir.absolutePath
        return File(basePath, STEAM_PLATFORM_DIR)
    }

    suspend fun discoverLocalSteamGames(): Int = withContext(Dispatchers.IO) {
        var discovered = 0

        val steamDir = getSteamDir()
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

        val gnBasePath = findGnStoragePath()
        if (gnBasePath != null) {
            val commonDir = File("$gnBasePath/Steam/steamapps/common")
            if (commonDir.exists()) {
                val gameDirs = commonDir.listFiles { file ->
                    file.isDirectory && File(file, ".download_complete").exists()
                } ?: emptyArray()

                val steamGames = gameDao.getAllWithSteamAppId()
                for (gameDir in gameDirs) {
                    val gameName = gameDir.name
                    val match = steamGames.find { sanitizeGameName(it.title) == gameName }
                    if (match != null && match.localPath == null) {
                        gameDao.update(match.copy(localPath = gameDir.absolutePath, source = GameSource.STEAM))
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

        val steamDir = getSteamDir()
        if (steamDir.exists()) {
            val stagingDirs = steamDir.listFiles { file ->
                file.isDirectory && file.name.endsWith("_staging")
            } ?: emptyArray()

            for (stagingDir in stagingDirs) {
                val appIdStr = stagingDir.name.removeSuffix("_staging")
                val appId = appIdStr.toLongOrNull() ?: continue
                val installDir = File(steamDir, appIdStr)

                val bytesDownloaded = getDirectorySize(installDir) + getDirectorySize(stagingDir)
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

                val bytesDownloaded = getDirectorySize(appDir)
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
                val bytesDownloaded = getDirectorySize(installDir) + getDirectorySize(stagingDir)
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
        pathsToClean.add(getInstallDir(appId))
        gameDao.getBySteamAppId(appId)?.localPath?.let { pathsToClean.add(File(it)) }

        // Fallback storage path -- GN path may have changed since download started
        val steamDir = getSteamDir()
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
        steamApps = null
        callbackManager = null
    }

    private fun addSpeedSample(timestampMs: Long, totalBytes: Long) {
        speedSamples.add(SpeedSample(timestampMs, totalBytes))
        val cutoff = timestampMs - SPEED_WINDOW_MS
        while (speedSamples.isNotEmpty() && speedSamples.first().timeMs < cutoff) {
            speedSamples.removeAt(0)
        }
    }

    private fun computeSpeed(nowMs: Long): Long {
        val cutoff = nowMs - SPEED_WINDOW_MS
        val samples = speedSamples.toTypedArray().filter { it.timeMs >= cutoff }
        if (samples.size < 2) return 0L

        val first = samples.first()
        val last = samples.last()
        val elapsedMs = last.timeMs - first.timeMs
        if (elapsedMs <= 0L) return 0L

        val bytesDelta = last.totalBytes - first.totalBytes
        if (bytesDelta <= 0L) return 0L

        val currentBps = bytesDelta.toDouble() / (elapsedMs.toDouble() / 1000.0)
        if (currentBps <= 0.0) return 0L

        emaSpeedBps = if (!hasEmaSpeed) {
            hasEmaSpeed = true
            currentBps
        } else {
            SPEED_EMA_ALPHA * currentBps + (1 - SPEED_EMA_ALPHA) * emaSpeedBps
        }

        return emaSpeedBps.toLong()
    }

    private fun resetSpeedTracking() {
        speedSamples.clear()
        emaSpeedBps = 0.0
        hasEmaSpeed = false
    }

    private fun persistBytes(appDirPath: String, bytes: Long) {
        try {
            val dir = File(appDirPath, DOWNLOAD_INFO_DIR)
            if (!dir.exists()) dir.mkdirs()
            File(dir, BYTES_DOWNLOADED_FILE).writeText(bytes.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist bytes: ${e.message}")
        }
    }

    private fun loadPersistedBytes(appDirPath: String): Long {
        return try {
            val file = File(File(appDirPath, DOWNLOAD_INFO_DIR), BYTES_DOWNLOADED_FILE)
            if (file.exists()) file.readText().trim().toLongOrNull() ?: 0L else 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun clearPersistedBytes(appDirPath: String) {
        try {
            File(File(appDirPath, DOWNLOAD_INFO_DIR), BYTES_DOWNLOADED_FILE).delete()
        } catch (_: Exception) {}
    }
}
