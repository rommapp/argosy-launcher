package com.nendo.argosy.data.download

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.util.Log
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.local.dao.MediaDownloadQueueDao
import com.nendo.argosy.data.local.entity.MediaDownloadDbState
import com.nendo.argosy.data.local.entity.MediaDownloadQueueEntity
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.media.MediaDirectoryManager
import com.nendo.argosy.data.preferences.MediaDownloadQuality
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.jellyfin.JellyfinApiClient
import com.nendo.argosy.data.remote.jellyfin.JellyfinDeviceProfile
import com.nendo.argosy.data.remote.jellyfin.JellyfinDeviceProfileBuilder
import com.nendo.argosy.data.remote.jellyfin.JellyfinMediaSource
import com.nendo.argosy.data.remote.jellyfin.JellyfinPlaybackInfoRequest
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.remote.jellyfin.JellyfinTranscodingProfile
import com.nendo.argosy.data.remote.jellyfin.PROFILE_CONTEXT_STREAMING
import com.nendo.argosy.data.remote.jellyfin.PROFILE_PROTOCOL_HTTP
import com.nendo.argosy.data.remote.jellyfin.PROFILE_TYPE_VIDEO
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.StorageAttributionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private const val TAG = "MediaDownloadManager"
private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
private const val UI_UPDATE_INTERVAL_MS = 500L
private const val DB_UPDATE_INTERVAL_MS = 5_000L
private const val THERMAL_PAUSE_POLL_MS = 5_000L
private const val THROTTLE_STEP_MS = 100
private const val STORAGE_BUFFER_BYTES = 200 * 1024 * 1024L
private const val TRANSCODE_HEADROOM = 1.15
private const val KBPS_TO_BPS = 1000L
private const val BITS_PER_BYTE = 8L
private const val DEFAULT_CONTAINER = "mp4"
private const val TRANSCODE_CONTAINER = "mp4"
private const val DEFAULT_LIBRARY_DIR = "Library"
private const val HLS_SUB_PROTOCOL = "hls"
private const val PARTIAL_SUFFIX = ".part"
private const val DISPLACED_SUFFIX = ".replaced"

sealed class MediaDownloadState {
    data object Idle : MediaDownloadState()
    data class Preparing(val itemId: String, val itemName: String, val detail: String) : MediaDownloadState()
    data class Downloading(val itemId: String, val itemName: String, val progress: Float) : MediaDownloadState()
    data class Paused(
        val itemId: String,
        val itemName: String,
        val progress: Float,
        val reason: String? = null
    ) : MediaDownloadState()
    data class Completed(val itemId: String, val itemName: String, val path: String) : MediaDownloadState()
    data class Failed(val itemId: String, val itemName: String, val error: String) : MediaDownloadState()
}

/**
 * Whether this state is work the queue still owes the user. A finished or failed download is neither
 * queued nor in progress, and counting it as either is what leaves a failure reading as pending.
 */
val MediaDownloadState.isInFlight: Boolean
    get() = this is MediaDownloadState.Preparing ||
        this is MediaDownloadState.Downloading ||
        this is MediaDownloadState.Paused

data class MediaDownloadProgress(
    val itemId: String,
    val itemName: String,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val posterUrl: String? = null,
    val quality: MediaDownloadQuality,
    val totalBytes: Long,
    val bytesDownloaded: Long,
    val state: MediaDownloadState,
    val bytesPerSecond: Long = 0L,
    val sizeIsEstimated: Boolean = false
) {
    val progress: Float get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val displayTitle: String get() = if (seriesName.isNullOrBlank()) itemName else "$seriesName - $itemName"
}

data class QueuedMediaDownload(
    val itemId: String,
    val itemName: String,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val itemType: String,
    val quality: MediaDownloadQuality,
    val posterUrl: String? = null
)

/**
 * What a removal batch did. [unreachable] is the count kept because their storage is not mounted -
 * those are still downloaded, and the caller is expected to say so rather than report a clean sweep.
 */
data class MediaRemovalResult(
    val removed: Int,
    val unreachable: Int,
    val absent: Int
)

/**
 * Fetches movies and episodes for offline playback.
 *
 * A separate resolver rather than a branch inside [DownloadManager]: that queue is keyed on a game id
 * end to end, so media gets its own queue and its own progress, and the downloads screen merges the
 * two the way it already merges Steam. Media inherits the same bandwidth contention Steam has today -
 * one shared slot budget, no global bandwidth scheduler.
 *
 * The two qualities are two different fetches, not one fetch with a parameter. The original is a
 * static file the server already has, so it carries a real size, honours a Range header and resumes
 * from a half-written file. A transcode is produced on demand: the server has to start ffmpeg before
 * any byte exists, there is no length to resume against, and abandoning one leaks that process until
 * the server times it out, which is why the play session is recorded on the queue row and explicitly
 * stopped.
 */
@Singleton
@Suppress("TooManyFunctions")
class MediaDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDownloadQueueDao: MediaDownloadQueueDao,
    private val mediaRepository: MediaRepository,
    private val apiClient: JellyfinApiClient,
    private val deviceProfileBuilder: JellyfinDeviceProfileBuilder,
    private val directoryManager: MediaDirectoryManager,
    private val fileAccessLayer: FileAccessLayer,
    private val storageAttribution: StorageAttributionRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val notificationManager: NotificationManager,
    private val downloadManager: dagger.Lazy<DownloadManager>,
    private val thermalManager: dagger.Lazy<DownloadThermalManager>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadState = MutableStateFlow<MediaDownloadState>(MediaDownloadState.Idle)
    val downloadState: StateFlow<MediaDownloadState> = _downloadState.asStateFlow()

    private val _activeDownload = MutableStateFlow<MediaDownloadProgress?>(null)
    val activeDownload: StateFlow<MediaDownloadProgress?> = _activeDownload.asStateFlow()

    private val _downloadQueue = MutableStateFlow<List<QueuedMediaDownload>>(emptyList())
    val downloadQueue: StateFlow<List<QueuedMediaDownload>> = _downloadQueue.asStateFlow()

    private val _completedDownloads = MutableStateFlow<List<MediaDownloadProgress>>(emptyList())
    val completedDownloads: StateFlow<List<MediaDownloadProgress>> = _completedDownloads.asStateFlow()

    private var currentDownloadJob: Job? = null
    private var isCancelled = false
    private val dispatching = AtomicBoolean(false)

    init {
        scope.launch { restoreQueueFromDatabase() }
    }

    fun hasActiveMediaDownload(): Boolean {
        val state = _downloadState.value
        return state is MediaDownloadState.Preparing || state is MediaDownloadState.Downloading
    }

    fun hasBlockingDownloadState(): Boolean =
        hasActiveMediaDownload() || _downloadState.value is MediaDownloadState.Paused

    /**
     * Clears what finished successfully. A failed row is left alone: it still carries the reason and
     * the partial file the user can resume from, and dropping it would strand both.
     */
    fun clearCompletedDownloads() {
        _completedDownloads.value = emptyList()
        if (_activeDownload.value?.state is MediaDownloadState.Completed) {
            _activeDownload.value = null
        }
        scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            mediaDownloadQueueDao.observeQueue(owner).first()
                .filter { it.state == MediaDownloadDbState.COMPLETED.name }
                .forEach { row ->
                    row.tempFilePath?.let { deleteIfPresent(it) }
                    mediaDownloadQueueDao.deleteByItemId(owner, row.itemId)
                }
        }
    }

    fun removeFromCompleted(itemId: String) {
        _completedDownloads.value = _completedDownloads.value.filterNot { it.itemId == itemId }
        scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            mediaDownloadQueueDao.deleteByItemId(owner, itemId)
        }
    }

    fun onDownloadSlotFreed() {
        if (_downloadQueue.value.isNotEmpty() && currentDownloadJob?.isActive != true) {
            processNextInQueue()
        }
    }

    /**
     * The size a transcode will occupy, from the bitrate the server is being asked to hit. The
     * original file has no such answer before negotiation, so it reports null rather than a guess.
     */
    fun estimateBytes(runTimeTicks: Long?, quality: MediaDownloadQuality): Long? {
        val kbps = quality.maxBitrateKbps ?: return null
        val seconds = (runTimeTicks ?: return null) / MediaRepository.TICKS_PER_SECOND
        if (seconds <= 0) return null
        return (seconds * kbps * KBPS_TO_BPS / BITS_PER_BYTE * TRANSCODE_HEADROOM).toLong()
    }

    suspend fun availableBytes(): Long = withContext(Dispatchers.IO) {
        val dir = directoryManager.resolveMediaDir()
        if (!fileAccessLayer.exists(dir.absolutePath)) fileAccessLayer.mkdirs(dir.absolutePath)
        runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(0L)
    }

    suspend fun defaultQuality(): MediaDownloadQuality =
        preferencesRepository.userPreferences.first().mediaDownloadQuality

    fun enqueue(itemId: String, quality: MediaDownloadQuality) = enqueueAll(listOf(itemId), quality)

    fun enqueueAll(itemIds: List<String>, quality: MediaDownloadQuality) {
        if (itemIds.isEmpty()) return
        scope.launch {
            val owner = mediaRepository.currentUserId()
            if (owner == null) {
                notify("Sign in to download", "Connect a media server from Settings")
                return@launch
            }
            val outcomes = itemIds.map { enqueueOne(owner, it, quality) }
            if (outcomes.none { it == EnqueueOutcome.ACCEPTED }) {
                val (title, subtitle) = refusalNotice(outcomes, quality)
                notify(title, subtitle)
                return@launch
            }
            DownloadForegroundService.start(context)
            processNextInQueue()
        }
    }

    /**
     * Why a whole batch was refused, said once. Every branch of [enqueueOne] that declines work is
     * represented here, so no ask can end in silence.
     */
    private fun refusalNotice(
        outcomes: List<EnqueueOutcome>,
        quality: MediaDownloadQuality
    ): Pair<String, String> {
        val downloaded = outcomes.count { it == EnqueueOutcome.ALREADY_DOWNLOADED }
        val queued = outcomes.count { it == EnqueueOutcome.ALREADY_QUEUED }
        return when {
            downloaded > 0 && queued == 0 ->
                "Already downloaded" to "${titleCount(downloaded)} on this device at ${quality.displayName}"
            queued > 0 && downloaded == 0 ->
                "Already in the queue" to "${titleCount(queued)} waiting to download"
            downloaded > 0 ->
                "Nothing new to download" to "${titleCount(downloaded)} downloaded, ${titleCount(queued)} queued"
            else -> "Nothing to download" to "This title has no file the server will hand over"
        }
    }

    private fun titleCount(count: Int): String = if (count == 1) "1 title" else "$count titles"

    fun pauseActiveDownload() {
        val active = _activeDownload.value ?: return
        isCancelled = true
        currentDownloadJob?.cancel()
        currentDownloadJob = null
        val paused = MediaDownloadState.Paused(active.itemId, active.itemName, active.progress)
        _downloadState.value = paused
        _activeDownload.value = active.copy(state = paused, bytesPerSecond = 0L)
        scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            mediaDownloadQueueDao.updateState(owner, active.itemId, MediaDownloadDbState.PAUSED.name)
            stopTranscodeFor(owner, active.itemId)
            downloadManager.get().onExternalSlotFreed()
        }
    }

    fun resumeDownload(itemId: String) {
        scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            val row = mediaDownloadQueueDao.getByItemId(owner, itemId) ?: return@launch
            mediaDownloadQueueDao.updateState(owner, itemId, MediaDownloadDbState.QUEUED.name)
            if (_downloadQueue.value.none { it.itemId == itemId }) {
                _downloadQueue.value = _downloadQueue.value + row.toQueued()
            }
            DownloadForegroundService.start(context)
            if (currentDownloadJob?.isActive != true) processNextInQueue()
        }
    }

    fun cancelDownload(itemId: String) {
        scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            val active = _activeDownload.value
            if (active?.itemId == itemId) {
                isCancelled = true
                currentDownloadJob?.cancel()
                currentDownloadJob = null
                _downloadState.value = MediaDownloadState.Idle
                _activeDownload.value = null
            }
            _downloadQueue.value = _downloadQueue.value.filterNot { it.itemId == itemId }
            stopTranscodeFor(owner, itemId)
            mediaDownloadQueueDao.getByItemId(owner, itemId)?.tempFilePath?.let { deleteIfPresent(it) }
            mediaDownloadQueueDao.deleteByItemId(owner, itemId)
            if (active?.itemId == itemId) {
                processNextInQueue()
                downloadManager.get().onExternalSlotFreed()
            }
        }
    }

    suspend fun removeDownload(itemId: String): Boolean = removeDownloads(listOf(itemId)).removed > 0

    /**
     * Forgets downloaded copies and deletes their files.
     *
     * A title whose storage is not reachable right now is kept, not forgotten: an unplugged card is
     * not a deleted download, and clearing the row there would lose the only record of where the
     * file lives. Whatever could not be removed is reported once rather than left silent.
     */
    suspend fun removeDownloads(itemIds: List<String>): MediaRemovalResult = withContext(Dispatchers.IO) {
        var removed = 0
        var unreachable = 0
        var absent = 0
        for (itemId in itemIds) {
            when (removeOne(itemId)) {
                RemovalOutcome.REMOVED -> removed++
                RemovalOutcome.UNREACHABLE -> unreachable++
                RemovalOutcome.ABSENT -> absent++
            }
        }
        if (unreachable > 0) {
            notify(
                "Storage unavailable",
                "${titleCount(unreachable)} stay downloaded until their storage is back"
            )
        }
        MediaRemovalResult(removed = removed, unreachable = unreachable, absent = absent)
    }

    private suspend fun removeOne(itemId: String): RemovalOutcome {
        val item = mediaRepository.getItem(itemId) ?: return RemovalOutcome.ABSENT
        val path = item.localPath ?: return RemovalOutcome.ABSENT
        if (!storageAttribution.isPathAvailable(path)) return RemovalOutcome.UNREACHABLE
        deleteIfPresent(path)
        mediaRepository.clearDownloaded(itemId)
        return RemovalOutcome.REMOVED
    }

    private suspend fun enqueueOne(
        owner: String,
        itemId: String,
        quality: MediaDownloadQuality
    ): EnqueueOutcome {
        val item = mediaRepository.getItem(itemId) ?: return EnqueueOutcome.UNAVAILABLE
        if (!item.isDownloadable) return EnqueueOutcome.UNAVAILABLE
        if (isInFlight(itemId)) return EnqueueOutcome.ALREADY_QUEUED
        if (_downloadQueue.value.any { it.itemId == itemId }) return EnqueueOutcome.ALREADY_QUEUED
        if (item.localPath != null && item.downloadQuality == quality.name) {
            return EnqueueOutcome.ALREADY_DOWNLOADED
        }

        releaseStaleRow(owner, itemId, quality)
        if (_activeDownload.value?.itemId == itemId) _activeDownload.value = null

        mediaDownloadQueueDao.insert(
            MediaDownloadQueueEntity(
                ownerUserId = owner,
                itemId = itemId,
                seriesId = item.seriesId,
                itemName = item.name,
                seriesName = item.seriesName,
                itemType = item.itemType,
                quality = quality.name,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                state = MediaDownloadDbState.QUEUED.name
            )
        )
        _downloadQueue.value = _downloadQueue.value + QueuedMediaDownload(
            itemId = itemId,
            itemName = item.name,
            seriesId = item.seriesId,
            seriesName = item.seriesName,
            itemType = item.itemType,
            quality = quality,
            posterUrl = mediaRepository.posterUrl(itemId, item.primaryImageTag)
        )
        return EnqueueOutcome.ACCEPTED
    }

    /**
     * A re-queue replaces the row for the same title, and with it the transcode session recorded on
     * that row. The encoder is stopped first, or it runs on the server until its own timeout with
     * nothing left that can address it. A partial file from a different quality is dropped rather
     * than carried into a download that would append to it.
     */
    private suspend fun releaseStaleRow(owner: String, itemId: String, quality: MediaDownloadQuality) {
        val existing = mediaDownloadQueueDao.getByItemId(owner, itemId) ?: return
        existing.playSessionId?.let { apiClient.stopActiveEncoding(it) }
        if (existing.quality != quality.name) existing.tempFilePath?.let { deleteIfPresent(it) }
    }

    private fun isInFlight(itemId: String): Boolean {
        val active = _activeDownload.value ?: return false
        return active.itemId == itemId && active.state.isInFlight
    }

    private suspend fun restoreQueueFromDatabase() {
        val owner = mediaRepository.currentUserId() ?: return
        mediaDownloadQueueDao.clearFinished(owner)
        val pending = mediaDownloadQueueDao.getPendingDownloads(owner)
        sweepOrphanPartials(pending)
        if (pending.isEmpty()) return

        for (row in pending) {
            if (row.state == MediaDownloadDbState.DOWNLOADING.name ||
                row.state == MediaDownloadDbState.PREPARING.name
            ) {
                mediaDownloadQueueDao.updateState(owner, row.itemId, MediaDownloadDbState.PAUSED.name)
                row.playSessionId?.let { apiClient.stopActiveEncoding(it) }
                mediaDownloadQueueDao.updateSource(owner, row.itemId, row.mediaSourceId, null)
            }
        }

        val restored = mediaDownloadQueueDao.getPendingDownloads(owner)
        val paused = restored.filter { it.state == MediaDownloadDbState.PAUSED.name }
        val queued = restored.filter { it.state == MediaDownloadDbState.QUEUED.name }

        val primary = paused.firstOrNull()
        if (primary != null) {
            val pausedState = MediaDownloadState.Paused(primary.itemId, primary.itemName, 0f)
            _downloadState.value = pausedState
            _activeDownload.value = MediaDownloadProgress(
                itemId = primary.itemId,
                itemName = primary.itemName,
                seriesId = primary.seriesId,
                seriesName = primary.seriesName,
                posterUrl = mediaRepository.getItem(primary.itemId)
                    ?.let { mediaRepository.posterUrl(it.itemId, it.primaryImageTag) },
                quality = MediaDownloadQuality.fromString(primary.quality),
                totalBytes = primary.totalBytes,
                bytesDownloaded = primary.bytesDownloaded,
                state = pausedState
            )
        }
        _downloadQueue.value = (paused.drop(1) + queued).map { it.toQueued() }
        Log.d(TAG, "Restored ${paused.size} paused + ${queued.size} queued media downloads")
    }

    /**
     * Claims the next queued title. The claim is taken before the coroutine suspends on preferences:
     * two entrants that each saw no active job would otherwise both start the same download.
     */
    private fun processNextInQueue() {
        if (currentDownloadJob?.isActive == true) return
        if (_downloadQueue.value.isEmpty()) return
        if (!dispatching.compareAndSet(false, true)) return

        scope.launch {
            try {
                val next = _downloadQueue.value.firstOrNull() ?: return@launch
                val maxConcurrent = preferencesRepository.userPreferences.first().maxConcurrentDownloads
                if (downloadManager.get().activeDownloadCount + 1 > maxConcurrent) {
                    Log.d(TAG, "No download slot for ${next.itemName}, staying queued")
                    return@launch
                }
                _downloadQueue.value = _downloadQueue.value.filterNot { it.itemId == next.itemId }
                startDownload(next)
            } finally {
                dispatching.set(false)
            }
        }
    }

    @Suppress("LongMethod")
    private fun startDownload(queued: QueuedMediaDownload) {
        isCancelled = false
        currentDownloadJob = scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            var plan: MediaFetchPlan? = null
            try {
                setPreparing(queued, "Preparing")
                mediaDownloadQueueDao.updateState(owner, queued.itemId, MediaDownloadDbState.PREPARING.name)

                val item = mediaRepository.getItem(queued.itemId)
                    ?: throw IllegalStateException("This title is no longer in the library")

                val negotiated = negotiate(item, queued.quality)
                    ?: throw IllegalStateException("The server would not hand over this title")
                plan = negotiated
                mediaDownloadQueueDao.updateSource(
                    owner, queued.itemId, negotiated.mediaSourceId, negotiated.playSessionId
                )

                val destination = resolveDestination(item, negotiated.container)
                val temp = partialFileFor(destination, negotiated.quality)
                fileAccessLayer.mkdirs(destination.parent.orEmpty())
                mediaDownloadQueueDao.updatePaths(
                    owner, queued.itemId, destination.absolutePath, temp.absolutePath
                )

                val resumeFrom = resumableBytes(negotiated, temp)

                val required = (negotiated.expectedBytes - resumeFrom).coerceAtLeast(0L)
                val available = availableBytes()
                if (required > 0 && available < required + STORAGE_BUFFER_BYTES) {
                    holdForStorage(owner, queued, required, available)
                    return@launch
                }

                if (negotiated.isTranscode) {
                    setPreparing(queued, "Waiting for the server to start the transcode")
                }
                val outcome = fetch(owner, queued, negotiated, temp, resumeFrom)

                if (isCancelled) return@launch
                if (outcome.declaredTotal != null && outcome.bytesWritten < outcome.declaredTotal) {
                    throw IllegalStateException("The connection dropped before the file was complete")
                }

                val written = outcome.bytesWritten
                finalizeDownload(item, destination, temp, negotiated, written)
                mediaDownloadQueueDao.updateState(owner, queued.itemId, MediaDownloadDbState.COMPLETED.name)

                val completedState = MediaDownloadState.Completed(
                    queued.itemId, queued.itemName, destination.absolutePath
                )
                _downloadState.value = completedState
                _completedDownloads.value = _completedDownloads.value + MediaDownloadProgress(
                    itemId = queued.itemId,
                    itemName = queued.itemName,
                    seriesId = queued.seriesId,
                    seriesName = queued.seriesName,
                    posterUrl = queued.posterUrl,
                    quality = negotiated.quality,
                    totalBytes = written,
                    bytesDownloaded = written,
                    state = completedState
                )
                _activeDownload.value = null
                stopTranscodeFor(owner, queued.itemId)
                advanceQueue()
            } catch (_: kotlinx.coroutines.CancellationException) {
                plan?.playSessionId?.let { session ->
                    scope.launch { apiClient.stopActiveEncoding(session) }
                }
                if (!isCancelled) {
                    _activeDownload.value = null
                    advanceQueue()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Media download failed: ${queued.itemName}", e)
                plan?.playSessionId?.let { apiClient.stopActiveEncoding(it) }
                val message = e.message ?: "Download failed"
                val failed = MediaDownloadState.Failed(queued.itemId, queued.itemName, message)
                _downloadState.value = failed
                _activeDownload.value = _activeDownload.value?.copy(state = failed, bytesPerSecond = 0L)
                mediaDownloadQueueDao.updateState(
                    owner, queued.itemId, MediaDownloadDbState.FAILED.name, message
                )
                advanceQueue()
            }
        }
    }

    private fun advanceQueue() {
        currentDownloadJob = null
        processNextInQueue()
        downloadManager.get().onExternalSlotFreed()
    }

    private suspend fun holdForStorage(
        owner: String,
        queued: QueuedMediaDownload,
        required: Long,
        available: Long
    ) {
        val message = "Needs ${formatGigabytes(required)}, ${formatGigabytes(available)} free"
        Log.w(TAG, "Holding ${queued.itemName}: $message")
        notify("Not enough storage for ${queued.itemName}", message)
        val paused = MediaDownloadState.Paused(queued.itemId, queued.itemName, 0f, message)
        _downloadState.value = paused
        _activeDownload.value = _activeDownload.value?.copy(state = paused)
        mediaDownloadQueueDao.updateState(owner, queued.itemId, MediaDownloadDbState.PAUSED.name, message)
        advanceQueue()
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun fetch(
        owner: String,
        queued: QueuedMediaDownload,
        plan: MediaFetchPlan,
        temp: File,
        resumeFrom: Long
    ): FetchOutcome {
        val api = apiClient.api ?: throw IllegalStateException("Not connected to the media server")
        val range = if (resumeFrom > 0) "bytes=$resumeFrom-" else null
        val response = api.downloadVideo(queued.itemId, plan.params, range)
        if (response.code() == HTTP_RANGE_NOT_SATISFIABLE) {
            deleteIfPresent(temp.absolutePath)
            throw IllegalStateException("The partial file no longer matched the server and was discarded")
        }
        if (!response.isSuccessful) {
            throw IllegalStateException("Server refused the download (${response.code()})")
        }
        val body = response.body() ?: throw IllegalStateException("Server sent no data")

        val isPartial = response.code() == HTTP_PARTIAL_CONTENT
        val startOffset = if (isPartial) resumeFrom else 0L
        val declared = body.contentLength().takeIf { it > 0 }?.plus(startOffset)
        val total = declared ?: plan.expectedBytes
        val sizeIsEstimated = declared == null

        var bytesWritten = startOffset
        publishDownloading(queued, plan, bytesWritten, total, 0L, sizeIsEstimated)

        val target = fileAccessLayer.getTransformedFile(temp.absolutePath)
        body.byteStream().use { input ->
            FileOutputStream(target, isPartial).use { output ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                var lastUiUpdate = System.currentTimeMillis()
                var lastDbUpdate = lastUiUpdate
                var lastBytesForSpeed = bytesWritten
                while (true) {
                    coroutineContext.ensureActive()

                    val thermal = thermalManager.get().thermalStatus.value
                    if (thermal.state == ThermalState.PAUSED) {
                        delay(THERMAL_PAUSE_POLL_MS)
                        continue
                    }

                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesWritten += read

                    if (thermal.throttleMultiplier < 1.0f) {
                        delay((((1.0f / thermal.throttleMultiplier) - 1) * THROTTLE_STEP_MS).toLong())
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate > UI_UPDATE_INTERVAL_MS) {
                        val speed = ((bytesWritten - lastBytesForSpeed) * 1000) / (now - lastUiUpdate)
                        publishDownloading(
                            queued, plan, bytesWritten,
                            maxOf(total, bytesWritten), speed, sizeIsEstimated
                        )
                        lastUiUpdate = now
                        lastBytesForSpeed = bytesWritten
                    }
                    if (now - lastDbUpdate > DB_UPDATE_INTERVAL_MS) {
                        lastDbUpdate = now
                        mediaDownloadQueueDao.updateProgress(owner, queued.itemId, bytesWritten, total)
                    }
                }
                output.flush()
            }
        }
        mediaDownloadQueueDao.updateProgress(owner, queued.itemId, bytesWritten, bytesWritten)
        return FetchOutcome(bytesWritten = bytesWritten, declaredTotal = declared)
    }

    /**
     * Puts the fetched file where the library expects it.
     *
     * A copy that is already there is moved aside rather than deleted, and only dropped once the
     * replacement is in place: on a full card the move can fail, and the copy the user already had
     * is the one thing that must survive that.
     */
    private suspend fun finalizeDownload(
        item: MediaItemEntity,
        destination: File,
        temp: File,
        plan: MediaFetchPlan,
        bytesWritten: Long
    ) {
        val destinationPath = destination.absolutePath
        val displaced = displaceExisting(destinationPath)
        val target = fileAccessLayer.getTransformedFile(temp.absolutePath)
        val finalFile = fileAccessLayer.getTransformedFile(destinationPath)

        val moved = runCatching { target.renameTo(finalFile) }.getOrDefault(false) ||
            fileAccessLayer.copyFile(temp.absolutePath, destinationPath)
        if (!moved) {
            restoreDisplaced(displaced, destinationPath)
            throw IllegalStateException("Could not move the download into place")
        }
        deleteIfPresent(temp.absolutePath)
        displaced?.let { deleteIfPresent(it) }

        val previousPath = item.localPath
        if (previousPath != null &&
            previousPath != destinationPath &&
            storageAttribution.isPathAvailable(previousPath)
        ) {
            deleteIfPresent(previousPath)
        }
        mediaRepository.markDownloaded(
            itemId = item.itemId,
            localPath = destinationPath,
            quality = plan.quality.name,
            bytes = bytesWritten
        )
    }

    private fun displaceExisting(destinationPath: String): String? {
        if (!fileAccessLayer.exists(destinationPath)) return null
        val asidePath = destinationPath + DISPLACED_SUFFIX
        deleteIfPresent(asidePath)
        val existing = fileAccessLayer.getTransformedFile(destinationPath)
        val aside = fileAccessLayer.getTransformedFile(asidePath)
        if (!runCatching { existing.renameTo(aside) }.getOrDefault(false)) {
            throw IllegalStateException("Could not set the existing copy aside")
        }
        return asidePath
    }

    private fun restoreDisplaced(displacedPath: String?, destinationPath: String) {
        val aside = displacedPath ?: return
        if (fileAccessLayer.exists(destinationPath)) return
        val source = fileAccessLayer.getTransformedFile(aside)
        val restored = fileAccessLayer.getTransformedFile(destinationPath)
        if (!runCatching { source.renameTo(restored) }.getOrDefault(false)) {
            Log.w(TAG, "Left the previous copy at $aside")
        }
    }

    /**
     * Asks the server what it will hand over.
     *
     * The original asks for the file the server already holds, which is what `static=true` fetches.
     * Any other quality asks for a progressive transcode, and the query the fetch then replays is the
     * server's own - reconstructing one by hand invents parameter names the server matches literally.
     * A server that answers a transcode request with the source file anyway is taken at its word and
     * the download is recorded as the original, rather than refused.
     */
    private suspend fun negotiate(item: MediaItemEntity, quality: MediaDownloadQuality): MediaFetchPlan? {
        val userId = apiClient.currentUserId() ?: mediaRepository.currentUserId() ?: return null
        val prefs = preferencesRepository.userPreferences.first()
        val wantsTranscode = quality != MediaDownloadQuality.ORIGINAL

        val request = JellyfinPlaybackInfoRequest(
            userId = userId,
            maxStreamingBitrate = quality.maxBitrateKbps?.times(KBPS_TO_BPS.toInt()),
            enableDirectPlay = !wantsTranscode,
            enableDirectStream = !wantsTranscode,
            enableTranscoding = wantsTranscode,
            allowVideoStreamCopy = !wantsTranscode,
            allowAudioStreamCopy = !wantsTranscode,
            deviceProfile = if (wantsTranscode) {
                transcodeProfile(quality)
            } else {
                deviceProfileBuilder.build(burnInImageSubtitles = prefs.mediaBurnInImageSubtitles)
            }
        )

        val response = when (val result = apiClient.getPlaybackInfo(item.itemId, request)) {
            is JellyfinResult.Success -> result.data
            is JellyfinResult.Error -> throw IllegalStateException(result.message)
        }
        val source = response.mediaSources.firstOrNull() ?: return null
        val transcodingUrl = source.transcodingUrl
            ?.takeIf { wantsTranscode && !source.transcodingSubProtocol.equals(HLS_SUB_PROTOCOL, true) }

        if (transcodingUrl == null) {
            if (wantsTranscode) {
                notify(
                    "Downloading the original file",
                    "${item.name} could not be transcoded for download"
                )
            }
            return originalPlan(source, item)
        }
        return transcodePlan(source, transcodingUrl, quality, item, response.playSessionId)
    }

    private fun originalPlan(source: JellyfinMediaSource, item: MediaItemEntity): MediaFetchPlan =
        MediaFetchPlan(
            quality = MediaDownloadQuality.ORIGINAL,
            mediaSourceId = source.id,
            playSessionId = null,
            params = apiClient.buildOriginalFileParams(source.id),
            container = source.container?.takeIf { it.isNotBlank() }?.substringBefore(',')
                ?: item.container?.substringBefore(',') ?: DEFAULT_CONTAINER,
            expectedBytes = source.size ?: 0L,
            supportsResume = true,
            isTranscode = false
        )

    private fun transcodePlan(
        source: JellyfinMediaSource,
        transcodingUrl: String,
        quality: MediaDownloadQuality,
        item: MediaItemEntity,
        playSessionId: String?
    ): MediaFetchPlan {
        val normalized = transcodingUrl.replace("&amp;", "&")
        val uri = Uri.parse(if (normalized.startsWith("http")) normalized else "http://localhost$normalized")
        val params = uri.queryParameterNames
            .associateWith { uri.getQueryParameter(it).orEmpty() }
            .toMutableMap()
        params["static"] = "false"
        val container = source.transcodingContainer?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
            ?: TRANSCODE_CONTAINER
        params.putIfAbsent("container", container)
        quality.maxHeight?.let { params.putIfAbsent("maxHeight", it.toString()) }
        quality.maxBitrateKbps?.let { params.putIfAbsent("videoBitRate", (it * KBPS_TO_BPS).toString()) }

        return MediaFetchPlan(
            quality = quality,
            mediaSourceId = source.id,
            playSessionId = playSessionId,
            params = params,
            container = container,
            expectedBytes = estimateBytes(item.runTimeTicks, quality) ?: 0L,
            supportsResume = false,
            isTranscode = true
        )
    }

    /**
     * A profile with nothing the device claims to play, so the server has no direct-play answer left
     * and negotiates the progressive file this path can actually save. The streaming profile cannot
     * be reused: it offers HLS first, and a playlist of segments is not a downloadable file.
     */
    private fun transcodeProfile(quality: MediaDownloadQuality): JellyfinDeviceProfile =
        JellyfinDeviceProfile(
            name = "Argosy Download",
            maxStreamingBitrate = quality.maxBitrateKbps?.times(KBPS_TO_BPS.toInt()),
            directPlayProfiles = emptyList(),
            transcodingProfiles = listOf(
                JellyfinTranscodingProfile(
                    container = TRANSCODE_CONTAINER,
                    type = PROFILE_TYPE_VIDEO,
                    videoCodec = "h264",
                    audioCodec = "aac",
                    protocol = PROFILE_PROTOCOL_HTTP,
                    context = PROFILE_CONTEXT_STREAMING,
                    maxAudioChannels = JellyfinDeviceProfileBuilder.DEFAULT_MAX_AUDIO_CHANNELS.toString()
                )
            )
        )

    private suspend fun resolveDestination(item: MediaItemEntity, container: String): File {
        val libraryName = libraryNameFor(item) ?: DEFAULT_LIBRARY_DIR
        return directoryManager.targetFileFor(
            libraryName = libraryName,
            itemPath = itemPathFor(item),
            fileName = "${fileBaseNameFor(item)}.$container"
        )
    }

    private suspend fun libraryNameFor(item: MediaItemEntity): String? {
        item.libraryId?.let { return mediaRepository.getLibraryName(it) }
        val seriesId = item.seriesId ?: return null
        val series = mediaRepository.getItem(seriesId) ?: return null
        return series.libraryId?.let { mediaRepository.getLibraryName(it) }
    }

    private fun itemPathFor(item: MediaItemEntity): String =
        if (MediaItemType.fromWire(item.itemType) == MediaItemType.EPISODE) {
            val series = item.seriesName?.takeIf { it.isNotBlank() } ?: "Series"
            val season = item.parentIndexNumber?.let { "Season %02d".format(it) } ?: "Season"
            "$series/$season"
        } else {
            listOfNotNull(item.name, item.productionYear?.let { "($it)" }).joinToString(" ")
        }

    private fun fileBaseNameFor(item: MediaItemEntity): String {
        if (MediaItemType.fromWire(item.itemType) != MediaItemType.EPISODE) return item.name
        val season = item.parentIndexNumber
        val episode = item.indexNumber
        if (season == null || episode == null) return item.name
        return "S%02dE%02d - %s".format(season, episode, item.name)
    }

    private fun setPreparing(queued: QueuedMediaDownload, detail: String) {
        val state = MediaDownloadState.Preparing(queued.itemId, queued.itemName, detail)
        _downloadState.value = state
        val existing = _activeDownload.value
        _activeDownload.value = if (existing?.itemId == queued.itemId) {
            existing.copy(state = state, bytesPerSecond = 0L)
        } else {
            MediaDownloadProgress(
                itemId = queued.itemId,
                itemName = queued.itemName,
                seriesId = queued.seriesId,
                seriesName = queued.seriesName,
                posterUrl = queued.posterUrl,
                quality = queued.quality,
                totalBytes = 0L,
                bytesDownloaded = 0L,
                state = state
            )
        }
    }

    private fun publishDownloading(
        queued: QueuedMediaDownload,
        plan: MediaFetchPlan,
        bytesWritten: Long,
        total: Long,
        speed: Long,
        sizeIsEstimated: Boolean
    ) {
        val progress = if (total > 0) (bytesWritten.toFloat() / total).coerceIn(0f, 1f) else 0f
        val state = MediaDownloadState.Downloading(queued.itemId, queued.itemName, progress)
        _downloadState.value = state
        _activeDownload.value = MediaDownloadProgress(
            itemId = queued.itemId,
            itemName = queued.itemName,
            seriesId = queued.seriesId,
            seriesName = queued.seriesName,
            posterUrl = queued.posterUrl,
            quality = plan.quality,
            totalBytes = total,
            bytesDownloaded = bytesWritten,
            state = state,
            bytesPerSecond = speed,
            sizeIsEstimated = sizeIsEstimated
        )
    }

    private suspend fun stopTranscodeFor(owner: String, itemId: String) {
        val session = mediaDownloadQueueDao.getByItemId(owner, itemId)?.playSessionId ?: return
        apiClient.stopActiveEncoding(session)
        mediaDownloadQueueDao.updateSource(owner, itemId, null, null)
    }

    private fun deleteIfPresent(path: String) {
        if (fileAccessLayer.exists(path)) fileAccessLayer.delete(path)
    }

    /**
     * Where a partial download lives. The quality is part of the name because two qualities of one
     * title resolve to the same container and so to the same destination: one shared partial would
     * let a re-download append its bytes to a different quality's and record the result as complete.
     */
    private fun partialFileFor(destination: File, quality: MediaDownloadQuality): File =
        File(destination.parentFile, "${destination.name}.${quality.name.lowercase()}$PARTIAL_SUFFIX")

    /**
     * How much of an existing partial this fetch may keep. A transcode has no stable length to
     * resume against, and a partial already at or past the expected size is what makes the server
     * answer 416 for as long as the file is there, so both start again from zero.
     */
    private fun resumableBytes(plan: MediaFetchPlan, temp: File): Long {
        val path = temp.absolutePath
        if (!plan.supportsResume) {
            deleteIfPresent(path)
            return 0L
        }
        if (!fileAccessLayer.exists(path)) return 0L
        val onDisk = fileAccessLayer.length(path)
        if (plan.expectedBytes > 0 && onDisk >= plan.expectedBytes) {
            deleteIfPresent(path)
            return 0L
        }
        return onDisk
    }

    /**
     * Clears partial files no queue row still refers to, and puts back any copy displaced by a
     * finalize that did not finish. Nothing else reads either file, so bytes left by a dropped row
     * would sit on the card for good.
     */
    private suspend fun sweepOrphanPartials(live: List<MediaDownloadQueueEntity>) {
        val referenced = live.mapNotNull { it.tempFilePath }.toSet()
        val root = directoryManager.resolveMediaDir()
        if (!root.exists()) return
        runCatching {
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                when {
                    file.name.endsWith(PARTIAL_SUFFIX) && file.absolutePath !in referenced ->
                        deleteIfPresent(file.absolutePath)
                    file.name.endsWith(DISPLACED_SUFFIX) -> recoverDisplaced(file)
                    else -> Unit
                }
            }
        }.onFailure { Log.w(TAG, "Partial sweep failed: ${it.message}") }
    }

    private fun recoverDisplaced(file: File) {
        val restored = File(file.parentFile, file.name.removeSuffix(DISPLACED_SUFFIX))
        if (restored.exists()) {
            deleteIfPresent(file.absolutePath)
        } else if (!file.renameTo(restored)) {
            Log.w(TAG, "Could not restore ${file.name}")
        }
    }

    private fun notify(title: String, subtitle: String) {
        notificationManager.show(
            title = title,
            subtitle = subtitle,
            type = NotificationType.WARNING,
            key = "media_download"
        )
    }

    private fun MediaDownloadQueueEntity.toQueued(): QueuedMediaDownload = QueuedMediaDownload(
        itemId = itemId,
        itemName = itemName,
        seriesId = seriesId,
        seriesName = seriesName,
        itemType = itemType,
        quality = MediaDownloadQuality.fromString(quality)
    )

    private val MediaItemEntity.isDownloadable: Boolean
        get() = MediaItemType.fromWire(itemType).let {
            it == MediaItemType.MOVIE || it == MediaItemType.EPISODE
        }

    private enum class EnqueueOutcome { ACCEPTED, ALREADY_QUEUED, ALREADY_DOWNLOADED, UNAVAILABLE }

    private enum class RemovalOutcome { REMOVED, UNREACHABLE, ABSENT }

    /**
     * [declaredTotal] is what the server said the whole file would be, or null when it never said -
     * a transcode has no length until it ends. Only a declared total can prove a fetch was complete.
     */
    private data class FetchOutcome(val bytesWritten: Long, val declaredTotal: Long?)

    private data class MediaFetchPlan(
        val quality: MediaDownloadQuality,
        val mediaSourceId: String?,
        val playSessionId: String?,
        val params: Map<String, String>,
        val container: String,
        val expectedBytes: Long,
        val supportsResume: Boolean,
        val isTranscode: Boolean
    )

    private companion object {
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val BYTES_PER_GIGABYTE = 1024.0 * 1024.0 * 1024.0

        fun formatGigabytes(bytes: Long): String = "%.1f GB".format(bytes / BYTES_PER_GIGABYTE)
    }
}
