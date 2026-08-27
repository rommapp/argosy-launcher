package com.nendo.argosy.data.download

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.util.Log
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.local.dao.MediaDownloadQueueDao
import com.nendo.argosy.data.local.entity.MediaDownloadDbState
import com.nendo.argosy.data.local.entity.MediaDownloadQueueEntity
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.local.entity.MediaSourceEntity
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.media.MediaAvailabilityVerifier
import com.nendo.argosy.data.media.MediaDirectoryManager
import com.nendo.argosy.data.media.MediaSubtitleSidecars
import com.nendo.argosy.data.media.subtitleDeliveryFor
import com.nendo.argosy.data.preferences.MediaDownloadQuality
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.preferences.alreadySatisfiedBy
import com.nendo.argosy.data.remote.jellyfin.JellyfinApiClient
import com.nendo.argosy.data.remote.jellyfin.JellyfinDeviceProfile
import com.nendo.argosy.data.remote.jellyfin.JellyfinDeviceProfileBuilder
import com.nendo.argosy.data.remote.jellyfin.JellyfinMediaSource
import com.nendo.argosy.data.remote.jellyfin.JellyfinMediaStream
import com.nendo.argosy.data.remote.jellyfin.JellyfinPlaybackInfoRequest
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.remote.jellyfin.JellyfinTranscodingProfile
import com.nendo.argosy.data.remote.jellyfin.PROFILE_CONTEXT_STREAMING
import com.nendo.argosy.data.remote.jellyfin.PROFILE_PROTOCOL_HTTP
import com.nendo.argosy.data.remote.jellyfin.PROFILE_TYPE_VIDEO
import com.nendo.argosy.data.remote.jellyfin.SUBTITLE_METHOD_ENCODE
import com.nendo.argosy.data.remote.jellyfin.bitrateKbps
import com.nendo.argosy.data.remote.jellyfin.resolvedContainer
import com.nendo.argosy.data.remote.jellyfin.videoHeight
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
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

/**
 * Device-sized downloads keep stereo audio on purpose: the encode exists to fit on the device, and
 * a 5.1 track would spend the saved bytes on channels a handheld downmixes anyway. The streaming
 * profile's higher channel ceiling is about preserving what the server can stream-copy, which has
 * no equivalent here - this profile always encodes.
 */
private const val DOWNLOAD_MAX_AUDIO_CHANNELS = 2
private const val DEFAULT_LIBRARY_DIR = "Library"
private const val HLS_SUB_PROTOCOL = "hls"
private const val PARTIAL_SUFFIX = ".part"
private const val DISPLACED_SUFFIX = ".replaced"
private const val STREAM_TYPE_SUBTITLE = "Subtitle"
private const val FAILED_ROW_RETENTION_DAYS = 14L

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

/**
 * A batch's size at one quality. [isSourceSize] marks a figure taken from what the server reported
 * about the file rather than computed from a tier's bitrate, so a caller can say which it is showing.
 */
data class MediaSizeEstimate(
    val bytes: Long,
    val isSourceSize: Boolean
)

/**
 * What subtitles a batch can be expected to keep, as far as anything already negotiated shows.
 *
 * [anythingKnown] separates a batch with no subtitles from one nothing has described yet, which are
 * different answers: the first means there is nothing to say, the second means the rule has to be
 * stated without promising the titles have any.
 */
data class MediaSubtitleOutlook(
    val hasTextSubtitles: Boolean,
    val hasImageSubtitles: Boolean,
    val burnsInImageSubtitles: Boolean,
    val anythingKnown: Boolean
)

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
    private val availabilityVerifier: MediaAvailabilityVerifier,
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
     * Clears what finished successfully. A failed row is left alone, on screen as well as in the
     * database: it still carries the reason and the partial file the user can resume from, and
     * dropping either would strand both. Dismissing one is [removeFromCompleted], which is a
     * deliberate act on that title rather than a sweep of everything finished.
     */
    fun clearCompletedDownloads() {
        _completedDownloads.value = _completedDownloads.value.filter {
            it.state is MediaDownloadState.Failed
        }
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
            mediaDownloadQueueDao.getByItemId(owner, itemId)?.tempFilePath?.let { deleteIfPresent(it) }
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
    private fun estimateBytes(runTimeTicks: Long?, quality: MediaDownloadQuality): Long? {
        val kbps = quality.maxBitrateKbps ?: return null
        val seconds = (runTimeTicks ?: return null) / MediaRepository.TICKS_PER_SECOND
        if (seconds <= 0) return null
        return (seconds * kbps * KBPS_TO_BPS / BITS_PER_BYTE * TRANSCODE_HEADROOM).toLong()
    }

    /**
     * How much disk this batch would take at each quality, leaving out any quality nothing can be
     * said about. The batch is read once and every quality answered from that reading, because the
     * quality picker asks about all of them at the same moment.
     */
    suspend fun estimateBatch(itemIds: List<String>): Map<MediaDownloadQuality, MediaSizeEstimate> {
        if (itemIds.isEmpty()) return emptyMap()
        val batch = itemIds.map { itemId ->
            KnownItem(
                runTimeTicks = mediaRepository.getItem(itemId)?.runTimeTicks,
                facts = mediaRepository.knownSourceFacts(itemId)
            )
        }
        return MediaDownloadQuality.entries.mapNotNull { quality ->
            estimateFor(batch, quality)?.let { quality to it }
        }.toMap()
    }

    /**
     * A tier the source already fits inside is answered with the source's own recorded size, because
     * that tier fetches the original file rather than an encode of it - estimating those bytes from
     * the tier's bitrate is what produced a figure wrong in either direction. A title nothing has
     * negotiated yet falls back to the bitrate estimate exactly as before, and the original file with
     * no recorded size still has no figure to offer.
     */
    private fun estimateFor(
        batch: List<KnownItem>,
        quality: MediaDownloadQuality
    ): MediaSizeEstimate? {
        var total = 0L
        var wholeBatchFromSource = true
        for (item in batch) {
            val facts = item.facts
            val sourceSize = facts?.sizeBytes?.takeIf { it > 0 }
            val fetchesSource = quality == MediaDownloadQuality.ORIGINAL ||
                (facts != null && quality.alreadySatisfiedBy(facts.videoHeight, facts.bitrateKbps))
            if (fetchesSource && sourceSize != null) {
                total += sourceSize
                continue
            }
            wholeBatchFromSource = false
            total += estimateBytes(item.runTimeTicks, quality) ?: return null
        }
        return MediaSizeEstimate(bytes = total, isSourceSize = wholeBatchFromSource)
    }

    /**
     * What the picker can say about subtitles before anything is fetched. Read from the tracks
     * previous negotiations recorded, so a title the app has only listed answers "unknown" rather
     * than "none" - the difference between saying nothing and promising something.
     */
    suspend fun subtitleOutlook(itemIds: List<String>): MediaSubtitleOutlook {
        val streams = itemIds.flatMap { mediaRepository.knownSubtitleStreams(it) }
        val text = streams.count { subtitleDeliveryFor(it.codec, isTextSubtitleStream = false) != null }
        return MediaSubtitleOutlook(
            hasTextSubtitles = text > 0,
            hasImageSubtitles = streams.size > text,
            burnsInImageSubtitles = preferencesRepository.userPreferences.first()
                .mediaBurnInImageSubtitles,
            anythingKnown = itemIds.any { mediaRepository.knownSourceFacts(it) != null }
        )
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
                notify(
                    NotificationText.Res(R.string.sync_media_signin_title),
                    NotificationText.Res(R.string.sync_media_signin_subtitle)
                )
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
    ): Pair<NotificationText, NotificationText> {
        val downloaded = outcomes.count { it == EnqueueOutcome.ALREADY_DOWNLOADED }
        val queued = outcomes.count { it == EnqueueOutcome.ALREADY_QUEUED }
        return when {
            downloaded > 0 && queued == 0 ->
                NotificationText.Res(R.string.sync_media_refusal_downloaded_title) to
                    NotificationText.Res(
                        R.string.sync_media_refusal_downloaded_subtitle,
                        listOf(titleCount(downloaded), quality.displayName)
                    )
            queued > 0 && downloaded == 0 ->
                NotificationText.Res(R.string.sync_media_refusal_queued_title) to
                    NotificationText.Res(R.string.sync_media_refusal_queued_subtitle, listOf(titleCount(queued)))
            downloaded > 0 ->
                NotificationText.Res(R.string.sync_media_refusal_mixed_title) to
                    NotificationText.Res(
                        R.string.sync_media_refusal_mixed_subtitle,
                        listOf(titleCount(downloaded), titleCount(queued))
                    )
            else -> NotificationText.Res(R.string.sync_media_refusal_unavailable_title) to
                NotificationText.Res(R.string.sync_media_refusal_unavailable_subtitle)
        }
    }

    private fun titleCount(count: Int): String =
        context.resources.getQuantityString(R.plurals.sync_media_title_count, count, count)

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

    /**
     * Stops a whole series without abandoning it. The episode actually in flight is paused through
     * the same path a single download takes, so its partial file and its transcode session are
     * handled exactly once; everything still waiting is simply moved out of the queue's way.
     */
    fun pauseSeries(seriesId: String) {
        scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            if (_activeDownload.value?.seriesId == seriesId) pauseActiveDownload()
            mediaDownloadQueueDao.updateSeriesState(
                ownerUserId = owner,
                seriesId = seriesId,
                from = listOf(MediaDownloadDbState.QUEUED.name, MediaDownloadDbState.PREPARING.name),
                to = MediaDownloadDbState.PAUSED.name
            )
            _downloadQueue.value = _downloadQueue.value.filterNot { it.seriesId == seriesId }
        }
    }

    /**
     * Puts a paused series back in line, in the order its episodes were queued in rather than the
     * order they happen to be read back in.
     */
    fun resumeSeries(seriesId: String) {
        scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            mediaDownloadQueueDao.updateSeriesState(
                ownerUserId = owner,
                seriesId = seriesId,
                from = listOf(MediaDownloadDbState.PAUSED.name),
                to = MediaDownloadDbState.QUEUED.name
            )
            val rows = mediaDownloadQueueDao.getBySeries(owner, seriesId)
                .filter { it.state == MediaDownloadDbState.QUEUED.name }
            val known = _downloadQueue.value.map { it.itemId }.toSet()
            _downloadQueue.value = _downloadQueue.value +
                rows.filterNot { it.itemId in known }.map { it.toQueued() }
            DownloadForegroundService.start(context)
            processNextInQueue()
        }
    }

    /**
     * Drops a series from the queue entirely. Each episode goes through the single-item path so an
     * in-flight one is torn down properly rather than left with a transcode running on the server.
     */
    fun cancelSeries(seriesId: String) {
        scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            mediaDownloadQueueDao.getBySeries(owner, seriesId)
                .filterNot { it.state == MediaDownloadDbState.COMPLETED.name }
                .forEach { cancelDownload(it.itemId) }
        }
    }

    fun resumeDownload(itemId: String) {
        scope.launch {
            val owner = mediaRepository.currentUserId() ?: return@launch
            val row = mediaDownloadQueueDao.getByItemId(owner, itemId) ?: return@launch
            _completedDownloads.value = _completedDownloads.value.filterNot { it.itemId == itemId }
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
     *
     * The mount table is re-read before any of that is decided. A card unplugged since the last
     * attribution pass is still listed until it is, and acting on the stale list is what turns an
     * unreachable copy into a record cleared for a file nobody deleted.
     */
    suspend fun removeDownloads(itemIds: List<String>): MediaRemovalResult = withContext(Dispatchers.IO) {
        storageAttribution.refreshVolumes()
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
                NotificationText.Res(R.string.sync_media_unreachable_title),
                NotificationText.Res(R.string.sync_media_unreachable_subtitle, listOf(titleCount(unreachable)))
            )
        }
        MediaRemovalResult(removed = removed, unreachable = unreachable, absent = absent)
    }

    /**
     * The subtitle files stored beside the video go with it: they are named after it and nothing
     * else refers to them, so a delete that took only the video would leave them behind for good.
     */
    private suspend fun removeOne(itemId: String): RemovalOutcome {
        val item = mediaRepository.getItem(itemId) ?: return RemovalOutcome.ABSENT
        val path = item.localPath ?: return RemovalOutcome.ABSENT
        if (!storageAttribution.isPathAvailable(path)) return RemovalOutcome.UNREACHABLE
        MediaSubtitleSidecars.deleteAllFor(path, fileAccessLayer)
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
        if (item.localPath != null && satisfiedByExistingCopy(item, quality) && copyStillCounts(itemId)) {
            return EnqueueOutcome.ALREADY_DOWNLOADED
        }

        releaseStaleRow(owner, itemId, quality)
        if (_activeDownload.value?.itemId == itemId) _activeDownload.value = null
        _completedDownloads.value = _completedDownloads.value.filterNot { it.itemId == itemId }

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
     * Whether the copy already on this device is the one this request would produce.
     *
     * A stored original also answers a request for a tier, but only when the source the server
     * described genuinely sits inside that tier: negotiation would bypass the encoder and hand back
     * the same file, so fetching it again would spend the bandwidth to arrive at identical bytes.
     * A source above the tier is a real request for a smaller copy and still downloads, and a source
     * nothing has negotiated yet is unknown rather than small, so it downloads too - which is exactly
     * what happens today.
     */
    /**
     * Whether the recorded copy is still worth refusing a re-download for.
     *
     * A stored path is not a file. One that turned out to be gone from storage that could be read is
     * no reason to decline: the record is dropped as part of asking, and the request goes through
     * rather than answering "already downloaded" about a file the user deleted themselves. A copy on
     * a card that is merely unplugged still counts, and re-fetching it would cost the download twice.
     */
    private suspend fun copyStillCounts(itemId: String): Boolean =
        availabilityVerifier.verify(itemId) != MediaAvailability.ABSENT

    private suspend fun satisfiedByExistingCopy(
        item: MediaItemEntity,
        quality: MediaDownloadQuality
    ): Boolean {
        if (item.downloadQuality == quality.name) return true
        if (item.downloadQuality != MediaDownloadQuality.ORIGINAL.name) return false
        val facts = mediaRepository.knownSourceFacts(item.itemId) ?: return false
        return quality.alreadySatisfiedBy(facts.videoHeight, facts.bitrateKbps)
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

    /**
     * Brings the queue back as it was left.
     *
     * A failure survives the restart it happened before. Its row still names the partial file and
     * the reason it stopped, which is the whole of what a resume needs, and a multi-gigabyte film
     * that failed at ninety per cent is not something to make the user fetch again because the app
     * was closed. Rows that age past [FAILED_ROW_RETENTION_DAYS] are dropped, so a failure nobody
     * came back to stops holding a partial on the card for the life of the install.
     */
    private suspend fun restoreQueueFromDatabase() {
        val owner = mediaRepository.currentUserId() ?: return
        mediaDownloadQueueDao.clearCompleted(owner)
        mediaDownloadQueueDao.clearFailedBefore(
            owner,
            Instant.now().minus(FAILED_ROW_RETENTION_DAYS, ChronoUnit.DAYS).toEpochMilli()
        )
        val failed = mediaDownloadQueueDao.getFailedDownloads(owner)
        val pending = mediaDownloadQueueDao.getPendingDownloads(owner)
        sweepOrphanPartials(pending + failed)
        restoreFailedDownloads(failed)
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
     * Puts failures back on the downloads screen, where the row's own action is what resumes them.
     * A row kept in the database that nothing draws is a resume the user has no way to ask for.
     */
    private suspend fun restoreFailedDownloads(rows: List<MediaDownloadQueueEntity>) {
        if (rows.isEmpty()) return
        _completedDownloads.value = _completedDownloads.value + rows.map { row ->
            val state = MediaDownloadState.Failed(
                row.itemId,
                row.itemName,
                row.errorReason ?: "Download failed"
            )
            MediaDownloadProgress(
                itemId = row.itemId,
                itemName = row.itemName,
                seriesId = row.seriesId,
                seriesName = row.seriesName,
                posterUrl = mediaRepository.getItem(row.itemId)
                    ?.let { mediaRepository.posterUrl(it.itemId, it.primaryImageTag) },
                quality = MediaDownloadQuality.fromString(row.quality),
                totalBytes = row.totalBytes,
                bytesDownloaded = row.bytesDownloaded,
                state = state
            )
        }
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
                if (!downloadManager.get().hasFreeDownloadSlot()) {
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
                setPreparing(queued, context.getString(R.string.sync_media_step_preparing))
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
                    setPreparing(queued, context.getString(R.string.sync_media_step_awaiting_transcode))
                }
                val outcome = fetch(owner, queued, negotiated, temp, resumeFrom)

                if (isCancelled) return@launch
                if (outcome.declaredTotal != null && outcome.bytesWritten < outcome.declaredTotal) {
                    throw IllegalStateException("The connection dropped before the file was complete")
                }

                val written = outcome.bytesWritten
                finalizeDownload(item, destination, temp)
                val subtitles = storeSubtitleSidecars(item, negotiated, destination)
                mediaRepository.markDownloaded(
                    itemId = item.itemId,
                    localPath = destination.absolutePath,
                    quality = negotiated.quality.name,
                    bytes = written + subtitles.bytes
                )
                mediaDownloadQueueDao.updateState(owner, queued.itemId, MediaDownloadDbState.COMPLETED.name)
                reportSubtitleShortfall(item, negotiated, subtitles)

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
                    _downloadState.value = MediaDownloadState.Idle
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
        notify(NotificationText.Res(R.string.sync_media_storage_title, listOf(queued.itemName)), NotificationText.Raw(message))
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
     *
     * A copy this download replaces at another path takes its subtitle files with it. Those are
     * named after the video they belong to and nothing else refers to them, so leaving them behind
     * would strand them on the card for good.
     */
    private suspend fun finalizeDownload(
        item: MediaItemEntity,
        destination: File,
        temp: File
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
            MediaSubtitleSidecars.deleteAllFor(previousPath, fileAccessLayer)
            deleteIfPresent(previousPath)
        }
    }

    /**
     * Fetches every text subtitle the plan named and stores it beside the video.
     *
     * This is what keeps subtitles on a device-sized download at all: the encode the server produces
     * carries one video stream and one audio stream, so a track not saved here is a track the file
     * does not have. Whatever is already beside the video is cleared first, because a re-download at
     * another quality can have a different set of tracks and a leftover file would be offered as one
     * of them.
     *
     * A track the server refuses is skipped rather than failing the download - the video is the
     * thing that was asked for - and the shortfall is reported once at the end.
     */
    private suspend fun storeSubtitleSidecars(
        item: MediaItemEntity,
        plan: MediaFetchPlan,
        destination: File
    ): SidecarOutcome {
        val destinationPath = destination.absolutePath
        MediaSubtitleSidecars.deleteAllFor(destinationPath, fileAccessLayer)
        val sourceId = plan.mediaSourceId ?: return SidecarOutcome(0L, plan.sidecarSubtitles.size)
        var stored = 0
        var bytes = 0L
        var failed = 0
        for (subtitle in plan.sidecarSubtitles) {
            val result = apiClient.fetchSubtitle(
                itemId = item.itemId,
                mediaSourceId = sourceId,
                streamIndex = subtitle.streamIndex,
                format = subtitle.format
            )
            val content = (result as? JellyfinResult.Success)?.data
            if (content == null) {
                Log.w(TAG, "Subtitle ${subtitle.label} unavailable for ${item.name}")
                failed++
                continue
            }
            val path = MediaSubtitleSidecars.pathFor(
                videoPath = destinationPath,
                streamIndex = subtitle.streamIndex,
                language = subtitle.language,
                format = subtitle.format
            )
            if (fileAccessLayer.writeBytes(path, content)) {
                stored++
                bytes += content.size.toLong()
            } else {
                failed++
            }
        }
        Log.d(TAG, "Stored $stored subtitle files for ${item.name}, $failed unavailable")
        return SidecarOutcome(bytes = bytes, failed = failed)
    }

    /**
     * Says what the copy on disk does not have. A viewer who picked a smaller size to save space did
     * not ask to lose the subtitles with it, so a track that could not travel is named at the moment
     * it is missed rather than discovered during playback.
     */
    private fun reportSubtitleShortfall(
        item: MediaItemEntity,
        plan: MediaFetchPlan,
        outcome: SidecarOutcome
    ) {
        val missing = outcome.failed + plan.droppedSubtitles
        if (missing <= 0) return
        val detail: NotificationText = when {
            plan.burnedInSubtitle != null -> NotificationText.Res(
                R.string.sync_media_subtitle_shortfall_burned_in, listOf(plan.burnedInSubtitle)
            )
            outcome.failed > 0 -> NotificationText.Res(R.string.sync_media_subtitle_shortfall_refused)
            plan.isTranscode -> NotificationText.Res(R.string.sync_media_subtitle_shortfall_transcoded)
            else -> NotificationText.Res(R.string.sync_media_subtitle_shortfall_external)
        }
        notify(
            NotificationText.Plural(
                R.plurals.sync_media_subtitle_shortfall_title,
                missing,
                listOf(item.name, missing)
            ),
            detail
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
     *
     * A source already inside the requested tier is fetched as the original instead. Transcoding it
     * cannot raise the resolution the file never had, so the encode would spend server time and a
     * generation of picture quality to arrive back where it started. What lands on disk is recorded
     * as the original, because that is what it is.
     *
     * Which subtitle a transcode carries is decided on the fetch rather than here. The server's own
     * contract for the stream endpoint is that an omitted subtitle index means no subtitles at all,
     * which is what makes a picture subtitle a question this path has to answer deliberately.
     */
    private suspend fun negotiate(item: MediaItemEntity, quality: MediaDownloadQuality): MediaFetchPlan? {
        val userId = apiClient.currentUserId() ?: mediaRepository.currentUserId() ?: return null
        val prefs = preferencesRepository.userPreferences.first()
        val wantsTranscode = quality != MediaDownloadQuality.ORIGINAL

        val response = when (
            val result = apiClient.getPlaybackInfo(
                item.itemId,
                playbackRequest(userId, quality, prefs.mediaBurnInImageSubtitles, wantsTranscode)
            )
        ) {
            is JellyfinResult.Success -> result.data
            is JellyfinResult.Error -> throw IllegalStateException(result.message)
        }
        val source = response.mediaSources.firstOrNull() ?: return null
        mediaRepository.recordSourceFacts(item.itemId, source)
        if (wantsTranscode && quality.alreadySatisfiedBy(source.videoHeight, source.bitrateKbps)) {
            return originalPlan(source, item)
        }
        val transcodingUrl = source.transcodingUrl
            ?.takeIf { wantsTranscode && !source.transcodingSubProtocol.equals(HLS_SUB_PROTOCOL, true) }

        if (transcodingUrl == null) {
            if (wantsTranscode) {
                notify(
                    NotificationText.Res(R.string.sync_media_transcode_unavailable_title),
                    NotificationText.Res(R.string.sync_media_transcode_unavailable_subtitle, listOf(item.name))
                )
            }
            return originalPlan(source, item)
        }

        return transcodePlan(
            source = source,
            transcodingUrl = transcodingUrl,
            quality = quality,
            item = item,
            playSessionId = response.playSessionId,
            burnIn = if (prefs.mediaBurnInImageSubtitles) burnInTarget(source) else null
        )
    }

    private fun playbackRequest(
        userId: String,
        quality: MediaDownloadQuality,
        burnInImageSubtitles: Boolean,
        wantsTranscode: Boolean
    ): JellyfinPlaybackInfoRequest = JellyfinPlaybackInfoRequest(
        userId = userId,
        maxStreamingBitrate = quality.maxBitrateKbps?.times(KBPS_TO_BPS.toInt()),
        enableDirectPlay = !wantsTranscode,
        enableDirectStream = !wantsTranscode,
        enableTranscoding = wantsTranscode,
        allowVideoStreamCopy = !wantsTranscode,
        allowAudioStreamCopy = !wantsTranscode,
        deviceProfile = if (wantsTranscode) {
            transcodeProfile(quality, burnInImageSubtitles)
        } else {
            deviceProfileBuilder.build(burnInImageSubtitles = burnInImageSubtitles)
        }
    )

    /**
     * The picture subtitle worth putting into the encode, or nothing.
     *
     * Only a title with no text subtitle at all is a candidate. Where a text track exists it is
     * saved beside the video and can be turned off again, while a burned-in one is in the picture
     * for as long as the file is kept - paying that price for a viewer who already had a readable
     * track is not a trade they asked for.
     */
    private fun burnInTarget(source: JellyfinMediaSource): JellyfinMediaStream? {
        val subtitles = source.mediaStreams.filter { it.type == STREAM_TYPE_SUBTITLE }
        if (subtitles.any { it.isDeliverableAsText }) return null
        val images = subtitles.filterNot { it.isDeliverableAsText }
        return images.firstOrNull { it.isForced }
            ?: images.firstOrNull { it.isDefault }
            ?: images.firstOrNull()
    }

    private fun originalPlan(source: JellyfinMediaSource, item: MediaItemEntity): MediaFetchPlan =
        MediaFetchPlan(
            quality = MediaDownloadQuality.ORIGINAL,
            mediaSourceId = source.id,
            playSessionId = null,
            params = apiClient.buildOriginalFileParams(source.id),
            container = source.resolvedContainer
                ?: item.container?.substringBefore(',') ?: DEFAULT_CONTAINER,
            expectedBytes = source.size ?: 0L,
            supportsResume = true,
            isTranscode = false,
            sidecarSubtitles = source.sidecarPlan(keepsEmbeddedTracks = true),
            droppedSubtitles = source.undeliverableSubtitles(keepsEmbeddedTracks = true)
        )

    /**
     * The fetch the server described, plus what it left for the caller to decide.
     *
     * [burnIn] is asked for on this request rather than during negotiation. The server documents the
     * stream endpoint as carrying no subtitles when no index is given, and the address it hands back
     * carries none - so a picture subtitle reaches the file only by naming it here, and only where
     * the viewer asked for that.
     */
    private fun transcodePlan(
        source: JellyfinMediaSource,
        transcodingUrl: String,
        quality: MediaDownloadQuality,
        item: MediaItemEntity,
        playSessionId: String?,
        burnIn: JellyfinMediaStream? = null
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
        burnIn?.let {
            params["subtitleStreamIndex"] = it.index.toString()
            params["subtitleMethod"] = SUBTITLE_METHOD_ENCODE
        }

        return MediaFetchPlan(
            quality = quality,
            mediaSourceId = source.id,
            playSessionId = playSessionId,
            params = params,
            container = container,
            expectedBytes = estimateBytes(item.runTimeTicks, quality) ?: 0L,
            supportsResume = false,
            isTranscode = true,
            sidecarSubtitles = source.sidecarPlan(keepsEmbeddedTracks = false),
            droppedSubtitles = source.undeliverableSubtitles(keepsEmbeddedTracks = false) -
                if (burnIn != null) 1 else 0,
            burnedInSubtitle = burnIn?.displayLabel
        )
    }

    /**
     * A profile with nothing the device claims to play, so the server has no direct-play answer left
     * and negotiates the progressive file this path can actually save. The streaming profile cannot
     * be reused: it offers HLS first, and a playlist of segments is not a downloadable file.
     *
     * Burn-in is the only subtitle method declared, and only when it was asked for. A saved file
     * cannot come back for a track later, so an image subtitle either goes into the picture during
     * the encode or is not in the download at all; text tracks are not declared here because they
     * are fetched as their own files instead.
     */
    private fun transcodeProfile(
        quality: MediaDownloadQuality,
        burnInImageSubtitles: Boolean
    ): JellyfinDeviceProfile =
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
                    maxAudioChannels = DOWNLOAD_MAX_AUDIO_CHANNELS.toString()
                )
            ),
            subtitleProfiles = if (burnInImageSubtitles) {
                deviceProfileBuilder.burnInSubtitleProfiles()
            } else {
                emptyList()
            }
        )

    /**
     * Which subtitle tracks have to be fetched as their own files for this fetch to keep them.
     *
     * A transcode keeps none: the encode the server produces carries one video stream and one audio
     * stream, so every text track has to be saved beside it. The source file keeps whatever is
     * inside it, so only tracks the server holds as separate files are fetched - fetching the
     * embedded ones would store a second copy of subtitles the file already has.
     */
    private fun JellyfinMediaSource.sidecarPlan(keepsEmbeddedTracks: Boolean): List<PlannedSidecar> =
        mediaStreams
            .filter { it.type == STREAM_TYPE_SUBTITLE }
            .filter { !keepsEmbeddedTracks || it.isExternal }
            .mapNotNull { stream ->
                val delivery = subtitleDeliveryFor(stream.codec, stream.isTextSubtitleStream)
                    ?: return@mapNotNull null
                PlannedSidecar(
                    streamIndex = stream.index,
                    language = stream.language,
                    format = delivery.format,
                    label = stream.displayLabel
                )
            }

    /**
     * How many subtitle tracks this fetch cannot carry in any form. These are picture subtitles, and
     * a picture subtitle exists for a viewer only if the server draws it into the video.
     */
    private fun JellyfinMediaSource.undeliverableSubtitles(keepsEmbeddedTracks: Boolean): Int =
        mediaStreams
            .filter { it.type == STREAM_TYPE_SUBTITLE }
            .filter { !keepsEmbeddedTracks || it.isExternal }
            .count { !it.isDeliverableAsText }

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
            val season = item.parentIndexNumber
                ?.let { String.format(Locale.ROOT, "Season %02d", it) } ?: "Season"
            "$series/$season"
        } else {
            listOfNotNull(item.name, item.productionYear?.let { "($it)" }).joinToString(" ")
        }

    private fun fileBaseNameFor(item: MediaItemEntity): String {
        if (MediaItemType.fromWire(item.itemType) != MediaItemType.EPISODE) return item.name
        val season = item.parentIndexNumber
        val episode = item.indexNumber
        if (season == null || episode == null) return item.name
        return String.format(Locale.ROOT, "S%02dE%02d - %s", season, episode, item.name)
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
     *
     * Every media location the app has written to is swept, not only the one in use. Moving the
     * media folder without moving the files is an offered choice, and a card that then goes back to
     * internal storage would otherwise keep whatever the earlier location was left holding. The
     * locations named by live rows are included for the same reason: a row can name a path outside
     * the current root, and its partial is the one file the sweep must be careful to keep.
     */
    private suspend fun sweepOrphanPartials(live: List<MediaDownloadQueueEntity>) {
        val referenced = live.mapNotNull { it.tempFilePath }.toSet()
        for (root in sweepRoots(live)) {
            if (!fileAccessLayer.isDirectory(root)) continue
            runCatching {
                fileAccessLayer.walk(root).filter { it.isFile }.forEach { file ->
                    when {
                        file.name.endsWith(PARTIAL_SUFFIX) && file.path !in referenced ->
                            deleteIfPresent(file.path)
                        file.name.endsWith(DISPLACED_SUFFIX) -> recoverDisplaced(file.path)
                        else -> Unit
                    }
                }
            }.onFailure { Log.w(TAG, "Partial sweep of $root failed: ${it.message}") }
        }
    }

    /**
     * The directories worth walking: where media goes now, where it went before any override was
     * chosen, and the folders live rows point at.
     */
    private suspend fun sweepRoots(live: List<MediaDownloadQueueEntity>): Set<String> = buildSet {
        add(directoryManager.resolveMediaDir().absolutePath)
        add(directoryManager.defaultMediaDir().absolutePath)
        live.forEach { row ->
            listOfNotNull(row.tempFilePath, row.destinationPath).forEach { path ->
                path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }
    }

    private fun recoverDisplaced(path: String) {
        val restored = path.removeSuffix(DISPLACED_SUFFIX)
        if (fileAccessLayer.exists(restored)) {
            deleteIfPresent(path)
            return
        }
        val displaced = fileAccessLayer.getTransformedFile(path)
        if (!runCatching { displaced.renameTo(fileAccessLayer.getTransformedFile(restored)) }
                .getOrDefault(false)
        ) {
            Log.w(TAG, "Could not restore ${path.substringAfterLast('/')}")
        }
    }

    private fun notify(title: NotificationText, subtitle: NotificationText) {
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

    private val JellyfinMediaStream.isDeliverableAsText: Boolean
        get() = subtitleDeliveryFor(codec, isTextSubtitleStream) != null

    private val JellyfinMediaStream.displayLabel: String
        get() = displayTitle ?: title ?: language ?: codec
            ?: context.getString(R.string.sync_media_subtitle_label_fallback)

    private val MediaItemEntity.isDownloadable: Boolean
        get() = MediaItemType.fromWire(itemType).let {
            it == MediaItemType.MOVIE || it == MediaItemType.EPISODE
        }

    private enum class EnqueueOutcome { ACCEPTED, ALREADY_QUEUED, ALREADY_DOWNLOADED, UNAVAILABLE }

    /**
     * One title of a batch, as much as is known before anything is negotiated. [facts] is null for a
     * title the server has never described, which is unknown rather than small.
     */
    private data class KnownItem(val runTimeTicks: Long?, val facts: MediaSourceEntity?)

    private enum class RemovalOutcome { REMOVED, UNREACHABLE, ABSENT }

    /**
     * [declaredTotal] is what the server said the whole file would be, or null when it never said -
     * a transcode has no length until it ends. Only a declared total can prove a fetch was complete.
     */
    private data class FetchOutcome(val bytesWritten: Long, val declaredTotal: Long?)

    /**
     * [sidecarSubtitles] are the tracks this fetch has to store as their own files, [droppedSubtitles]
     * counts the ones it cannot carry at all, and [burnedInSubtitle] names the one the server was
     * asked to draw into the picture. Together they are what the download can say about subtitles
     * once it finishes.
     */
    private data class MediaFetchPlan(
        val quality: MediaDownloadQuality,
        val mediaSourceId: String?,
        val playSessionId: String?,
        val params: Map<String, String>,
        val container: String,
        val expectedBytes: Long,
        val supportsResume: Boolean,
        val isTranscode: Boolean,
        val sidecarSubtitles: List<PlannedSidecar> = emptyList(),
        val droppedSubtitles: Int = 0,
        val burnedInSubtitle: String? = null
    )

    private data class PlannedSidecar(
        val streamIndex: Int,
        val language: String?,
        val format: String,
        val label: String
    )

    /**
     * What was stored beside the video, and what could not be. A track the server refused is
     * reported rather than swallowed: the viewer chose this tier expecting subtitles with it.
     */
    private data class SidecarOutcome(val bytes: Long, val failed: Int)

    private companion object {
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val BYTES_PER_GIGABYTE = 1024.0 * 1024.0 * 1024.0

        fun formatGigabytes(bytes: Long): String = "%.1f GB".format(bytes / BYTES_PER_GIGABYTE)
    }
}
