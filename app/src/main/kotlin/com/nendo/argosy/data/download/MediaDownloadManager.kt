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

/**
 * Directories that exist whether or not the volume beneath them is mounted. Finding one of these as
 * the nearest surviving ancestor of a downloaded file means the volume is gone, not that the file
 * was deleted.
 */
private val MOUNT_ROOTS = setOf("/", "/storage", "/mnt", "/storage/emulated", "/storage/self")

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

    init {
        scope.launch { restoreQueueFromDatabase() }
    }

    fun hasActiveMediaDownload(): Boolean {
        val state = _downloadState.value
        return state is MediaDownloadState.Preparing || state is MediaDownloadState.Downloading
    }

    fun hasBlockingDownloadState(): Boolean =
        hasActiveMediaDownload() || _downloadState.value is MediaDownloadState.Paused

    fun clearCompletedDownloads() {
        _completedDownloads.value = emptyList()
        if (_activeDownload.value?.state is MediaDownloadState.Completed) {
            _activeDownload.value = null
        }
        scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            mediaDownloadQueueDao.clearFinished(owner)
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
            var accepted = 0
            for (itemId in itemIds) {
                if (enqueueOne(owner, itemId, quality)) accepted++
            }
            if (accepted == 0) return@launch
            DownloadForegroundService.start(context)
            if (currentDownloadJob?.isActive != true) processNextInQueue()
        }
    }

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
            if (active?.itemId == itemId) processNextInQueue()
        }
    }

    /**
     * Forgets a downloaded copy and deletes its file.
     *
     * Answers false and changes nothing when the file sits on a volume that is not currently
     * mounted: an unplugged card is not a deleted download, and clearing the row there would lose
     * the only record of where the file lives. A file the user deleted themselves leaves its
     * directory behind, which is how the two cases are told apart.
     */
    suspend fun removeDownload(itemId: String): Boolean {
        val item = mediaRepository.getItem(itemId) ?: return false
        val path = item.localPath ?: return false
        if (!volumeAvailable(path)) {
            notify("Storage unavailable", "${item.name} stays downloaded until its storage is back")
            return false
        }
        deleteIfPresent(path)
        mediaRepository.clearDownloaded(itemId)
        return true
    }

    private suspend fun enqueueOne(owner: String, itemId: String, quality: MediaDownloadQuality): Boolean {
        val item = mediaRepository.getItem(itemId) ?: return false
        if (!item.isDownloadable) return false
        if (_activeDownload.value?.itemId == itemId) return false
        if (_downloadQueue.value.any { it.itemId == itemId }) return false
        if (item.localPath != null && item.downloadQuality == quality.name) {
            notify("Already downloaded", "${item.name} is on this device at ${quality.displayName}")
            return false
        }

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
        return true
    }

    private suspend fun restoreQueueFromDatabase() {
        val owner = mediaRepository.currentUserId() ?: return
        mediaDownloadQueueDao.clearFinished(owner)
        val pending = mediaDownloadQueueDao.getPendingDownloads(owner)
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

    private fun processNextInQueue() {
        if (currentDownloadJob?.isActive == true) return
        val queue = _downloadQueue.value
        if (queue.isEmpty()) return
        val next = queue.first()

        scope.launch {
            val maxConcurrent = preferencesRepository.userPreferences.first().maxConcurrentDownloads
            if (downloadManager.get().activeDownloadCount + 1 > maxConcurrent) {
                Log.d(TAG, "No download slot for ${next.itemName}, staying queued")
                return@launch
            }
            _downloadQueue.value = _downloadQueue.value.filterNot { it.itemId == next.itemId }
            startDownload(next)
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
                val temp = File(destination.parentFile, destination.name + PARTIAL_SUFFIX)
                fileAccessLayer.mkdirs(destination.parent.orEmpty())
                mediaDownloadQueueDao.updatePaths(
                    owner, queued.itemId, destination.absolutePath, temp.absolutePath
                )

                val tempPath = temp.absolutePath
                val resumeFrom = if (negotiated.supportsResume && fileAccessLayer.exists(tempPath)) {
                    fileAccessLayer.length(tempPath)
                } else {
                    0L
                }
                if (!negotiated.supportsResume) deleteIfPresent(tempPath)

                val required = (negotiated.expectedBytes - resumeFrom).coerceAtLeast(0L)
                val available = availableBytes()
                if (required > 0 && available < required + STORAGE_BUFFER_BYTES) {
                    holdForStorage(owner, queued, required, available)
                    return@launch
                }

                if (negotiated.isTranscode) {
                    setPreparing(queued, "Waiting for the server to start the transcode")
                }
                fetch(owner, queued, negotiated, temp, resumeFrom)

                if (isCancelled) return@launch

                val written = fileAccessLayer.length(temp.absolutePath)
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
                downloadManager.get().onExternalSlotFreed()
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
    ) {
        val api = apiClient.api ?: throw IllegalStateException("Not connected to the media server")
        val range = if (resumeFrom > 0) "bytes=$resumeFrom-" else null
        val response = api.downloadVideo(queued.itemId, plan.params, range)
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
    }

    private suspend fun finalizeDownload(
        item: MediaItemEntity,
        destination: File,
        temp: File,
        plan: MediaFetchPlan,
        bytesWritten: Long
    ) {
        val previousPath = item.localPath
        deleteIfPresent(destination.absolutePath)
        val target = fileAccessLayer.getTransformedFile(temp.absolutePath)
        val finalFile = fileAccessLayer.getTransformedFile(destination.absolutePath)
        if (!target.renameTo(finalFile)) {
            if (!fileAccessLayer.copyFile(temp.absolutePath, destination.absolutePath)) {
                throw IllegalStateException("Could not move the download into place")
            }
            deleteIfPresent(temp.absolutePath)
        }
        if (previousPath != null && previousPath != destination.absolutePath && volumeAvailable(previousPath)) {
            deleteIfPresent(previousPath)
        }
        mediaRepository.markDownloaded(
            itemId = item.itemId,
            localPath = destination.absolutePath,
            quality = plan.quality.name,
            bytes = bytesWritten
        )
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

    private fun volumeAvailable(path: String): Boolean {
        var candidate: File? = File(path).parentFile
        while (candidate != null) {
            val absolute = candidate.absolutePath
            if (fileAccessLayer.exists(absolute)) {
                return absolute !in MOUNT_ROOTS
            }
            candidate = candidate.parentFile
        }
        return false
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
        const val BYTES_PER_GIGABYTE = 1024.0 * 1024.0 * 1024.0

        fun formatGigabytes(bytes: Long): String = "%.1f GB".format(bytes / BYTES_PER_GIGABYTE)
    }
}
