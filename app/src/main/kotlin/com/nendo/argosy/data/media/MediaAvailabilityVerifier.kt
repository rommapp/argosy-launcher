package com.nendo.argosy.data.media

import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StoragePathUtils
import com.nendo.argosy.data.storage.StorageVolumeInfo
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject
import javax.inject.Singleton

private const val VERIFY_BATCH = 64

/**
 * Establishes, off the main thread, which downloaded copies are actually on disk.
 *
 * The library holds thousands of titles and a grid draws hundreds of rows, so nothing here runs from
 * composition or per item drawn: a pass walks only the rows that carry a path -- the downloaded
 * subset, never the whole library -- in batches on an IO scope, and publishes one map that every
 * screen reads. A composable asks the map, never the filesystem.
 *
 * Reachability is not decided here. [StorageAttributionRepository.isPathAvailable] is the single
 * answer to whether a volume is present, and it is what separates a copy on an unplugged card from
 * one the user deleted. A file that is missing while its own folder cannot even be listed is also
 * reported as unavailable rather than absent, because unreadable is not absent.
 *
 * A pass is also when records are reconciled: a row proven absent is cleared so the title can be
 * downloaded again. That write is taken only after re-establishing absence with the volume list
 * freshly detected and no relocation in flight, so an unplugged card can never destroy a record.
 */
@Singleton
class MediaAvailabilityVerifier @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaDirectoryManager: MediaDirectoryManager,
    private val storageAttribution: StorageAttributionRepository,
    private val fileAccessLayer: FileAccessLayer
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "MediaAvailability")
    private val passMutex = Mutex()
    private val passLock = Any()
    private var activePass: Job? = null

    private val _availability = MutableStateFlow<Map<String, MediaAvailability>>(emptyMap())
    val availability: StateFlow<Map<String, MediaAvailability>> = _availability.asStateFlow()

    @Volatile
    private var verifiedPaths: Map<String, String> = emptyMap()

    @Volatile
    private var verifiedFingerprint: String? = null

    init {
        scope.launch {
            mediaRepository.observeDownloaded().collectLatest { items -> runPass(items, full = false) }
        }
        scope.launch {
            storageAttribution.volumes
                .map { volumes -> fingerprintOf(volumes) }
                .distinctUntilChanged()
                .collect { fingerprint ->
                    if (fingerprint == verifiedFingerprint) return@collect
                    runPass(mediaRepository.observeDownloaded().first(), full = true)
                }
        }
    }

    /**
     * Re-verifies everything, for a screen that has just opened. Single-flight: a pass already
     * running is joined rather than stacked behind a second walk of the same files.
     */
    fun verifyOnOpen(): Job = synchronized(passLock) {
        activePass?.takeIf { it.isActive }?.let { return it }
        scope.launch {
            runPass(mediaRepository.observeDownloaded().first(), full = true)
        }.also { activePass = it }
    }

    /**
     * One title, verified now, for a caller about to act on the answer. Playback and re-download both
     * ask here rather than reading the cached map, because acting on a stale entry is what leaves a
     * viewer staring at a refusal for a file that is not there.
     */
    suspend fun verify(itemId: String): MediaAvailability = withContext(Dispatchers.IO) {
        val item = mediaRepository.getItem(itemId) ?: return@withContext MediaAvailability.NOT_DOWNLOADED
        val path = item.localPath ?: return@withContext MediaAvailability.NOT_DOWNLOADED
        storageAttribution.refreshVolumes()
        val state = classify(StoragePathUtils.canonicalize(path))
        _availability.update { it + (itemId to state) }
        if (state == MediaAvailability.ABSENT) reconcile(listOf(itemId))
        state
    }

    /**
     * The subtitle files stored beside one downloaded copy.
     *
     * Asked here because this is where a downloaded copy's presence on disk is established, and its
     * subtitles are part of what is on that disk: they are named after the video and have no record
     * anywhere else, so the directory is what has to be read.
     */
    suspend fun downloadedSubtitles(videoPath: String): List<MediaSubtitleSidecar> =
        withContext(Dispatchers.IO) { MediaSubtitleSidecars.listFor(videoPath, fileAccessLayer) }

    private suspend fun runPass(items: List<MediaItemEntity>, full: Boolean) {
        passMutex.withLock {
            val volumes = storageAttribution.refreshVolumes()
            if (volumes.isEmpty()) return@withLock
            verifiedFingerprint = fingerprintOf(volumes)

            val previousPaths = verifiedPaths
            val previousStates = _availability.value
            val states = HashMap<String, MediaAvailability>(items.size)
            val paths = HashMap<String, String>(items.size)
            val absent = mutableListOf<String>()

            for (batch in items.chunked(VERIFY_BATCH)) {
                currentCoroutineContext().ensureActive()
                for (item in batch) {
                    val canonical = StoragePathUtils.canonicalize(item.localPath ?: continue)
                    paths[item.itemId] = canonical
                    val cached = previousStates[item.itemId]?.takeIf { known ->
                        !full && known.hasLocalCopy && previousPaths[item.itemId] == canonical
                    }
                    val state = cached ?: classify(canonical)
                    states[item.itemId] = state
                    if (state == MediaAvailability.ABSENT) absent += item.itemId
                }
                yield()
            }

            verifiedPaths = paths.toMap()
            _availability.value = states.toMap()
            if (absent.isNotEmpty()) reconcile(absent)
        }
    }

    /**
     * Present, unavailable, or absent, for one path.
     *
     * The middle case covers two shapes of unreadable: a volume that is not mounted at all, and a
     * mounted one whose folder cannot be listed. Both leave the record alone. Only a file missing
     * from a folder that could be read is treated as deleted.
     */
    private fun classify(canonicalPath: String): MediaAvailability = when {
        fileAccessLayer.isFile(canonicalPath) -> MediaAvailability.PRESENT
        !storageAttribution.isPathAvailable(canonicalPath) -> MediaAvailability.UNAVAILABLE
        !fileAccessLayer.isDirectory(parentOf(canonicalPath)) -> MediaAvailability.UNAVAILABLE
        else -> MediaAvailability.ABSENT
    }

    /**
     * Forgets the copies proven gone. Absence is established a second time here, inside the
     * relocation lock, so the proof belongs to the moment of the write rather than to a walk that
     * finished before a card was pulled or a media folder was moved out from under it.
     *
     * Subtitle files stored beside a video go when the video is proven gone. They are named after it
     * and play only with it, so a set left behind is bytes on the card that nothing will ever read.
     */
    private suspend fun reconcile(itemIds: List<String>) {
        mediaDirectoryManager.underRelocationLock {
            for (itemId in itemIds) {
                val path = mediaRepository.getItem(itemId)?.localPath ?: continue
                if (classify(StoragePathUtils.canonicalize(path)) != MediaAvailability.ABSENT) continue
                MediaSubtitleSidecars.deleteAllFor(path, fileAccessLayer)
                mediaRepository.clearDownloaded(itemId)
                _availability.update { it + (itemId to MediaAvailability.NOT_DOWNLOADED) }
            }
        }
    }

    /**
     * What has to change before every answer is worth taking again: which volumes are mounted, and
     * whether the one in a slot is still the same one. Usage drift is deliberately not part of it --
     * files coming and going on a volume do not change whether it can be reached.
     */
    private fun fingerprintOf(volumes: List<StorageVolumeInfo>): String = volumes
        .map { "${it.key}:${it.totalBytes}" }
        .sorted()
        .joinToString("|")

    private fun parentOf(canonicalPath: String): String =
        canonicalPath.substringBeforeLast('/', "").ifEmpty { "/" }
}
