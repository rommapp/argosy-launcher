package com.nendo.argosy.data.download

import android.util.Log
import android.content.Context
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.download.nsz.NszDecompressor
import com.nendo.argosy.data.emulator.M3uManager
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.DualScreenManagerHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nendo.argosy.util.FileNames
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val STORAGE_BUFFER_BYTES = 50 * 1024 * 1024L
private const val INTERNAL_STAGING_RESERVE_BYTES = 1024 * 1024 * 1024L
private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
private const val UI_UPDATE_INTERVAL_MS = 500L
private const val DB_UPDATE_INTERVAL_MS = 5000L

data class DownloadProgress(
    val id: Long = 0,
    val gameId: Long,
    val rommId: Long,
    val discId: Long? = null,
    val discNumber: Int? = null,
    val gameFileId: Long? = null,
    val fileCategory: String? = null,
    val fileName: String,
    val gameTitle: String,
    val gameFolderName: String? = null,
    val platformSlug: String,
    val coverPath: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: DownloadState,
    val errorReason: DownloadFailureReason? = null,
    val extractionBytesWritten: Long = 0,
    val extractionTotalBytes: Long = 0,
    val isMultiFileRom: Boolean = false,
    val bytesPerSecond: Long = 0,
    val statusMessage: String? = null,
    val selectedFileIds: List<Long>? = null,
    /**
     * What the storage gate asked for when it held this download back, which is the transfer plus
     * the room its unpack needs, not the transfer alone. Null until a gate refuses.
     */
    val requiredStorageBytes: Long? = null
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f

    val extractionPercent: Float
        get() = if (extractionTotalBytes > 0) extractionBytesWritten.toFloat() / extractionTotalBytes else 0f

    val isDiscDownload: Boolean get() = discId != null
    val isGameFileDownload: Boolean get() = gameFileId != null

    val displayTitle: String
        get() = when {
            discNumber != null -> "$gameTitle (Disc $discNumber)"
            fileCategory != null -> "$gameTitle (${fileCategory.replaceFirstChar { it.uppercase() }})"
            else -> gameTitle
        }
}

enum class DownloadState {
    QUEUED,
    WAITING_FOR_STORAGE,
    DOWNLOADING,
    EXTRACTING,
    MOVING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

private sealed class DownloadResult {
    data class Success(val bytesWritten: Long) : DownloadResult()
    data class Failure(val reason: DownloadFailureReason) : DownloadResult()
    data class WaitingForStorage(val reason: DownloadFailureReason) : DownloadResult()
    data object Cancelled : DownloadResult()
}

private val INVALID_CONTENT_TYPES = listOf("image/", "text/html")
private const val MIN_ROM_SIZE_BYTES = 1024L

data class DownloadQueueState(
    val activeDownloads: List<DownloadProgress> = emptyList(),
    val queue: List<DownloadProgress> = emptyList(),
    val completed: List<DownloadProgress> = emptyList(),
    val availableStorageBytes: Long = 0
) {
    @Deprecated("Use activeDownloads instead", ReplaceWith("activeDownloads.firstOrNull()"))
    val activeDownload: DownloadProgress?
        get() = activeDownloads.firstOrNull()
}

data class DownloadCompletionEvent(
    val gameId: Long,
    val rommId: Long,
    val localPath: String,
    val isDiscDownload: Boolean = false
)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val platformDao: PlatformDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val soundManager: SoundFeedbackManager,
    private val m3uManager: M3uManager,
    private val thermalManager: dagger.Lazy<DownloadThermalManager>,
    private val steamContentManager: dagger.Lazy<com.nendo.argosy.data.steam.SteamContentManager>,
    private val mediaDownloadManager: dagger.Lazy<MediaDownloadManager>,
    private val musicDirectoryManager: MusicDirectoryManager,
    private val attributionRepository: StorageAttributionRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val homeTileRepository: com.nendo.argosy.data.repository.HomeTileRepository,
    private val homeTilePromptQueue: com.nendo.argosy.data.repository.HomeTilePromptQueue,
    private val extContentOrganizer: ExtContentOrganizer,
    private val romStagingManager: RomStagingManager
) {
    private val _state = MutableStateFlow(DownloadQueueState())
    val state: StateFlow<DownloadQueueState> = _state.asStateFlow()

    val activeDownloadCount: Int get() = _state.value.activeDownloads.size

    /**
     * How many downloads are running right now, across every queue.
     *
     * Rom, Steam and media downloads share one concurrency budget and one connection, so this is the
     * only number any of them may compare against it. Counting a queue's own kind alone is what let
     * a Steam install and a film start against a budget of one and then compete for the same pipe.
     */
    fun totalActiveDownloads(): Int =
        _state.value.activeDownloads.size +
            (if (steamContentManager.get().hasActiveSteamDownload()) 1 else 0) +
            (if (mediaDownloadManager.get().hasActiveMediaDownload()) 1 else 0)

    /**
     * Whether one more download may start. Asked by every queue before it dispatches, so the budget
     * is enforced once against the whole of what is running rather than once per queue.
     */
    suspend fun hasFreeDownloadSlot(): Boolean =
        totalActiveDownloads() < preferencesRepository.userPreferences.first().maxConcurrentDownloads

    fun onExternalSlotFreed() {
        scope.launch { processQueue() }
    }

    /**
     * Tells every queue that a slot this one was holding is free. All three are told because none of
     * them knows what the others are waiting on, and a release that only wakes its own queue leaves
     * the others sitting on work the budget now allows.
     */
    private fun notifySlotFreed() {
        scope.launch {
            processQueue()
            steamContentManager.get().onDownloadSlotFreed()
            mediaDownloadManager.get().onDownloadSlotFreed()
        }
    }

    private val _completionEvents = MutableSharedFlow<DownloadCompletionEvent>()
    val completionEvents: SharedFlow<DownloadCompletionEvent> = _completionEvents.asSharedFlow()

    private val scope = SafeCoroutineScope(Dispatchers.Main, "DownloadManager")
    private val downloadJobs = mutableMapOf<Long, Job>()

    private val defaultDownloadDir: File by lazy {
        File(context.getExternalFilesDir(null), "downloads").also { it.mkdirs() }
    }

    init {
        scope.launch {
            restoreQueueFromDatabase()
        }
    }

    private suspend fun restoreQueueFromDatabase() {
        Log.d(TAG, "restoreQueueFromDatabase: starting")
        downloadQueueDao.clearFailed()
        downloadQueueDao.clearCompleted()

        val pending = downloadQueueDao.getPendingDownloads()
        Log.d(TAG, "restoreQueueFromDatabase: found ${pending.size} pending downloads")
        pending.forEach { Log.d(TAG, "  - ${it.gameTitle}: state=${it.state}, bytes=${it.bytesDownloaded}/${it.totalBytes}") }

        sweepAbandonedStaging(pending.map { it.id }.toSet())

        if (pending.isEmpty()) {
            updateAvailableStorage()
            return
        }

        val statesToReset = setOf(
            DownloadState.DOWNLOADING.name,
            DownloadState.EXTRACTING.name,
            DownloadState.MOVING.name
        )
        for (entity in pending) {
            if (entity.state in statesToReset) {
                Log.d(TAG, "restoreQueueFromDatabase: resetting ${entity.gameTitle} from ${entity.state} to QUEUED")
                downloadQueueDao.updateState(entity.id, DownloadState.QUEUED.name)
            }
        }

        val restored = pending.map {
            val progress = it.toDownloadProgress()
            if (progress.state.name in statesToReset) {
                progress.copy(state = DownloadState.QUEUED)
            } else {
                progress
            }
        }
        Log.d(TAG, "restoreQueueFromDatabase: restored ${restored.size} items, states: ${restored.map { it.state }}")

        _state.value = DownloadQueueState(
            queue = restored,
            availableStorageBytes = getGlobalStorageBytes()
        )

        processQueue()
    }

    companion object {
        private const val TAG = "DownloadManager"
        private const val CONNECTION_WAIT_TIMEOUT_MS = 30_000L
    }

    /**
     * A custom folder is only trustworthy when the slug identifies one platform. Duplicate-slug
     * rows are a state the schema allows, and taking the first of them would write a download
     * into a different platform's library. The shared location is unambiguous, so an ambiguous
     * slug falls back to it rather than guessing.
     */
    private suspend fun getDownloadDir(platformSlug: String): File {
        val matches = platformDao.getAllBySlug(platformSlug)
        val platform = matches.singleOrNull()
        if (matches.size > 1) {
            Log.w(TAG, "getDownloadDir: slug '$platformSlug' matches ${matches.size} platforms, using the shared path")
        }
        if (platform?.customRomPath != null) {
            return File(platform.customRomPath).also { it.mkdirs() }
        }

        val prefs = preferencesRepository.userPreferences.first()
        val customPath = prefs.romStoragePath
        return if (customPath != null) {
            File(customPath, platformSlug).also { it.mkdirs() }
        } else {
            File(defaultDownloadDir, platformSlug).also { it.mkdirs() }
        }
    }

    private suspend fun getAvailableStorageBytes(platformSlug: String): Long {
        return withContext(Dispatchers.IO) {
            val downloadDir = getDownloadDir(platformSlug)
            romStagingManager.availableBytes(downloadDir) ?: 0L
        }
    }

    private sealed class StoragePlan {
        data class Staged(val destinationDir: File) : StoragePlan()
        data object Direct : StoragePlan()
        data class Insufficient(val requiredBytes: Long, val availableBytes: Long) : StoragePlan()
    }

    /**
     * Where this download will write, and whether the volumes it writes to can hold it.
     *
     * An archive costs its own bytes and its unpacked bytes at the same time, because the unpack
     * writes beside the archive and the archive is only removed once the unpack has finished.
     * Reserving for the transfer alone is what lets a download pass the gate and then run the card
     * dry halfway through extracting, so both halves are charged to whichever volume actually
     * receives them:
     *
     * - staged: internal pays archive + unpacked, the rom folder pays the finished output only
     * - direct: the rom folder pays archive + unpacked together
     *
     * A volume Argosy cannot stat does not veto the download; a volume it can stat does.
     */
    private suspend fun planStorage(progress: DownloadProgress, claimStaging: Boolean): StoragePlan =
        withContext(Dispatchers.IO) {
            val destinationDir = getDownloadDir(progress.platformSlug)
            val destinationFree = romStagingManager.availableBytes(destinationDir)
            val remainingArchive = (progress.totalBytes - progress.bytesDownloaded).coerceAtLeast(0L)
            val expands = !progress.isGameFileDownload &&
                ArchiveExpansion.expandsOnDisk(
                    progress.fileName, progress.platformSlug, progress.isMultiFileRom
                )
            val unpackedBytes = if (expands) {
                ArchiveExpansion.estimate(
                    progress.totalBytes, progress.fileName, progress.platformSlug
                )
            } else {
                0L
            }

            if (expands && preferencesRepository.userPreferences.first().stageDownloadsInternally) {
                val staged = planStaged(
                    progress, destinationDir, destinationFree,
                    remainingArchive, unpackedBytes, claimStaging
                )
                if (staged != null) return@withContext staged
            }

            val directRequirement = remainingArchive + unpackedBytes + STORAGE_BUFFER_BYTES
            if (destinationFree != null && destinationFree < directRequirement) {
                StoragePlan.Insufficient(directRequirement, destinationFree)
            } else {
                StoragePlan.Direct
            }
        }

    private fun planStaged(
        progress: DownloadProgress,
        destinationDir: File,
        destinationFree: Long?,
        remainingArchive: Long,
        unpackedBytes: Long,
        claimStaging: Boolean
    ): StoragePlan? {
        if (claimStaging && !romStagingManager.claim(progress.id)) return null
        val internalFree = romStagingManager.internalAvailableBytes()
        val internalRequirement = remainingArchive + unpackedBytes + INTERNAL_STAGING_RESERVE_BYTES
        val destinationRequirement = unpackedBytes + STORAGE_BUFFER_BYTES
        val fits = internalFree != null && internalFree >= internalRequirement &&
            (destinationFree == null || destinationFree >= destinationRequirement)
        if (fits) return StoragePlan.Staged(destinationDir)
        Logger.info(
            TAG,
            "Staging unavailable, downloading direct | game=${progress.gameTitle} " +
                "internalFree=$internalFree need=$internalRequirement destFree=$destinationFree"
        )
        if (claimStaging) romStagingManager.release(progress.id)
        return null
    }

    private suspend fun getGlobalStorageBytes(): Long {
        return withContext(Dispatchers.IO) {
            val prefs = preferencesRepository.userPreferences.first()
            val dir = prefs.romStoragePath?.let { File(it) } ?: defaultDownloadDir
            romStagingManager.availableBytes(dir) ?: 0L
        }
    }

    private suspend fun updateAvailableStorage() {
        val available = getGlobalStorageBytes()
        _state.value = _state.value.copy(availableStorageBytes = available)
    }

    private suspend fun isInstantDownload(expectedSizeBytes: Long): Boolean {
        if (expectedSizeBytes <= 0) return false
        val thresholdMb = preferencesRepository.userPreferences.first().instantDownloadThresholdMb
        val thresholdBytes = thresholdMb * 1024L * 1024L
        return expectedSizeBytes <= thresholdBytes
    }

    private suspend fun startDownloadJob(progress: DownloadProgress) {
        val plan = planStorage(progress, claimStaging = true)
        val availableStorage = getAvailableStorageBytes(progress.platformSlug)

        if (plan is StoragePlan.Insufficient) {
            downloadQueueDao.updateState(progress.id, DownloadState.WAITING_FOR_STORAGE.name)
            _state.value = _state.value.copy(
                activeDownloads = _state.value.activeDownloads.filter { it.id != progress.id },
                queue = _state.value.queue.map {
                    if (it.id == progress.id) {
                        it.copy(
                            state = DownloadState.WAITING_FOR_STORAGE,
                            requiredStorageBytes = plan.requiredBytes
                        )
                    } else {
                        it
                    }
                } + if (_state.value.queue.none { it.id == progress.id }) {
                    listOf(
                        progress.copy(
                            state = DownloadState.WAITING_FOR_STORAGE,
                            requiredStorageBytes = plan.requiredBytes
                        )
                    )
                } else emptyList(),
                availableStorageBytes = availableStorage
            )
            return
        }

        soundManager.play(SoundType.DOWNLOAD_START)

        _state.value = _state.value.copy(
            activeDownloads = _state.value.activeDownloads + progress.copy(state = DownloadState.DOWNLOADING),
            queue = _state.value.queue.filter { it.id != progress.id },
            availableStorageBytes = availableStorage
        )

        downloadQueueDao.updateState(progress.id, DownloadState.DOWNLOADING.name)

        downloadJobs[progress.id] = scope.launch {
            settleDownload(progress, downloadRom(progress, plan))
        }
    }

    /**
     * Retires a finished job: records the outcome, frees the slot and the staging holder, and moves
     * the row to wherever it now belongs.
     *
     * A download that ran out of room on the rom volume goes back to the queue rather than to the
     * failed list. Its archive is already fetched and unpacked in staging, and a failed row is
     * cleared on the next start, which would throw that work away over a condition the user can fix
     * by deleting one game.
     */
    private suspend fun settleDownload(progress: DownloadProgress, result: DownloadResult) {
        val finalProgress = when (result) {
            is DownloadResult.Success -> {
                downloadQueueDao.updateState(progress.id, DownloadState.COMPLETED.name)
                soundManager.play(SoundType.DOWNLOAD_COMPLETE)
                progress.copy(state = DownloadState.COMPLETED, bytesDownloaded = result.bytesWritten)
            }
            is DownloadResult.Failure -> {
                downloadQueueDao.updateState(
                    progress.id, DownloadState.FAILED.name, DownloadFailureReasonCodec.encode(result.reason)
                )
                soundManager.play(SoundType.ERROR)
                progress.copy(state = DownloadState.FAILED, errorReason = result.reason)
            }
            is DownloadResult.WaitingForStorage -> {
                downloadQueueDao.updateState(
                    progress.id,
                    DownloadState.WAITING_FOR_STORAGE.name,
                    DownloadFailureReasonCodec.encode(result.reason)
                )
                progress.copy(state = DownloadState.WAITING_FOR_STORAGE, errorReason = result.reason)
            }
            is DownloadResult.Cancelled -> progress.copy(state = DownloadState.PAUSED)
        }

        downloadJobs.remove(progress.id)
        romStagingManager.release(progress.id)

        when (result) {
            is DownloadResult.Cancelled -> return
            is DownloadResult.WaitingForStorage -> {
                _state.value = _state.value.copy(
                    activeDownloads = _state.value.activeDownloads.filter { it.id != progress.id },
                    queue = _state.value.queue.filter { it.id != progress.id } + finalProgress,
                    availableStorageBytes = getAvailableStorageBytes(progress.platformSlug)
                )
                notifySlotFreed()
            }
            else -> {
                _state.value = _state.value.copy(
                    activeDownloads = _state.value.activeDownloads.filter { it.id != progress.id },
                    completed = _state.value.completed + finalProgress
                )
                notifySlotFreed()
                if (result is DownloadResult.Success) {
                    broadcastDownloadCompleted(progress.gameId)
                }
            }
        }
    }

    private fun broadcastDownloadCompleted(gameId: Long) {
        DualScreenManagerHolder.instance?.onDownloadCompleted(gameId)
        scope.launch { addToCustomHomeGrid(gameId) }
    }

    /**
     * Honours the custom grid's "add new downloads" choice. Both completion paths funnel through
     * the broadcast above, so hooking it here covers the queue and the direct download alike rather
     * than guarding one of them.
     *
     * Asking first cannot ask from here - a download finishes with no screen of its own, sometimes
     * mid-game - so that mode queues the offer for the home surface to raise when it is next shown.
     */
    private suspend fun addToCustomHomeGrid(gameId: Long) {
        val prefs = preferencesRepository.userPreferences.first()
        val customGrid = prefs.homeLayout.customGrid
        when (customGrid.autoAdd) {
            com.nendo.argosy.domain.model.HomeTileAutoAdd.OFF -> return
            com.nendo.argosy.domain.model.HomeTileAutoAdd.PROMPT -> homeTilePromptQueue.offer(gameId)
            com.nendo.argosy.domain.model.HomeTileAutoAdd.AUTO -> homeTileRepository.appendToLastPage(
                ownerUserId = syncPreferencesRepository.getRommUserId(),
                target = com.nendo.argosy.domain.model.HomeTileTargetRef.Game(gameId),
                columns = customGrid.laneCount
            )
        }
    }

    suspend fun enqueueDownload(
        gameId: Long,
        rommId: Long,
        fileName: String,
        gameTitle: String,
        platformSlug: String,
        coverPath: String?,
        expectedSizeBytes: Long = 0,
        isMultiFileRom: Boolean = false,
        selectedFileIds: List<Long>? = null
    ) {
        val effectiveMultiFile = isMultiFileRom || (selectedFileIds?.size ?: 0) > 1
        val currentState = _state.value
        if (currentState.activeDownloads.any { it.gameId == gameId }) return
        if (currentState.queue.any { it.gameId == gameId }) return

        val existing = downloadQueueDao.getByGameId(gameId)
        if (existing != null) {
            Log.d(TAG, "enqueueDownload: clearing stale queue entry for $gameTitle (state=${existing.state})")
            existing.tempFilePath?.let { path ->
                val tempFile = File(path)
                if (tempFile.exists()) tempFile.delete()
            }
            discardStagingFor(existing.id)
            downloadQueueDao.deleteByGameId(gameId)
        }

        val platformDir = getDownloadDir(platformSlug)
        val diskFileName = FileNames.sanitize(fileName)
        val tempFilePath = File(platformDir, "${diskFileName}.tmp").absolutePath

        val entity = DownloadQueueEntity(
            gameId = gameId,
            rommId = rommId,
            fileName = diskFileName,
            gameTitle = gameTitle,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED.name,
            errorReason = null,
            tempFilePath = tempFilePath,
            createdAt = Instant.now(),
            isMultiFileRom = effectiveMultiFile,
            selectedFileIds = selectedFileIds?.joinToString(","),
            ownerUserId = syncPreferencesRepository.getRommUserId()
        )

        val id = downloadQueueDao.insert(entity)
        Logger.info(
            TAG,
            "Enqueue base rom | game=$gameTitle gameId=$gameId rommId=$rommId " +
                "file=$fileName platform=$platformSlug multiFile=$isMultiFileRom"
        )

        val progress = DownloadProgress(
            id = id,
            gameId = gameId,
            rommId = rommId,
            fileName = diskFileName,
            gameTitle = gameTitle,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED,
            isMultiFileRom = effectiveMultiFile,
            selectedFileIds = selectedFileIds
        )

        if (isInstantDownload(expectedSizeBytes)) {
            startDownloadJob(progress)
        } else {
            _state.value = _state.value.copy(
                queue = _state.value.queue + progress
            )
            processQueue()
        }
    }

    suspend fun enqueueDiscDownload(
        gameId: Long,
        discId: Long,
        discNumber: Int,
        rommId: Long,
        fileName: String,
        gameTitle: String,
        gameFolderName: String? = null,
        platformSlug: String,
        coverPath: String?,
        expectedSizeBytes: Long = 0
    ) {
        val currentState = _state.value
        if (currentState.activeDownloads.any { it.discId == discId }) return
        if (currentState.queue.any { it.discId == discId }) return

        val platformDir = getDownloadDir(platformSlug)
        val diskFileName = FileNames.sanitize(fileName)
        val tempFilePath = File(platformDir, "${diskFileName}.tmp").absolutePath

        val entity = DownloadQueueEntity(
            gameId = gameId,
            rommId = rommId,
            discId = discId,
            discNumber = discNumber,
            fileName = diskFileName,
            gameTitle = gameTitle,
            gameFolderName = gameFolderName,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED.name,
            errorReason = null,
            tempFilePath = tempFilePath,
            createdAt = Instant.now(),
            ownerUserId = syncPreferencesRepository.getRommUserId()
        )

        val id = downloadQueueDao.insert(entity)

        val progress = DownloadProgress(
            id = id,
            gameId = gameId,
            rommId = rommId,
            discId = discId,
            discNumber = discNumber,
            fileName = diskFileName,
            gameTitle = gameTitle,
            gameFolderName = gameFolderName,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED
        )

        if (isInstantDownload(expectedSizeBytes)) {
            startDownloadJob(progress)
        } else {
            _state.value = _state.value.copy(
                queue = _state.value.queue + progress
            )
            processQueue()
        }
    }

    suspend fun enqueueGameFileDownload(
        gameId: Long,
        gameFileId: Long,
        rommFileId: Long,
        fileName: String,
        category: String,
        gameTitle: String,
        platformSlug: String,
        coverPath: String?,
        expectedSizeBytes: Long = 0,
        gameFolderName: String? = null
    ) {
        val currentState = _state.value
        if (currentState.activeDownloads.any { it.gameFileId == gameFileId }) return
        if (currentState.queue.any { it.gameFileId == gameFileId }) return

        val gameFolder = resolveAddonFolder(gameId, platformSlug, gameFolderName, gameTitle, category)
        val categoryFolder = resolveGameFileDir(gameId, gameFileId, platformSlug, category, gameFolder)
        val diskFileName = FileNames.sanitize(fileName)
        val tempFilePath = File(categoryFolder, "${diskFileName}.tmp").absolutePath
        Logger.info(
            TAG,
            "Enqueue $category | game=$gameTitle gameId=$gameId rommFileId=$rommFileId " +
                "file=$diskFileName folder=${gameFolder.name} dest=${categoryFolder.name}"
        )

        val entity = DownloadQueueEntity(
            gameId = gameId,
            rommId = rommFileId,
            gameFileId = gameFileId,
            fileCategory = category,
            fileName = diskFileName,
            gameTitle = gameTitle,
            gameFolderName = gameFolderName,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED.name,
            errorReason = null,
            tempFilePath = tempFilePath,
            createdAt = Instant.now(),
            ownerUserId = syncPreferencesRepository.getRommUserId()
        )

        val id = downloadQueueDao.insert(entity)

        val progress = DownloadProgress(
            id = id,
            gameId = gameId,
            rommId = rommFileId,
            gameFileId = gameFileId,
            fileCategory = category,
            fileName = diskFileName,
            gameTitle = gameTitle,
            gameFolderName = gameFolderName,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED
        )

        if (isInstantDownload(expectedSizeBytes)) {
            startDownloadJob(progress)
        } else {
            _state.value = _state.value.copy(
                queue = _state.value.queue + progress
            )
            processQueue()
        }
    }

    private suspend fun getGameFolder(platformSlug: String, vararg names: String): File {
        val platformDir = getDownloadDir(platformSlug)
        return resolveGameFolder(platformDir, names.map(::sanitizeFolderName)).apply { mkdirs() }
    }

    /**
     * The folder a game's files belong in, preferring the server's own name and falling back to
     * anything a previous release created, so a library downloaded under an older naming rule is
     * added to rather than duplicated. A multi-disc folder conformed for ES-DE carries a `.m3u`
     * suffix, so later downloads for the same game must land in it rather than recreating the
     * unsuffixed folder beside it.
     */
    private fun resolveGameFolder(platformDir: File, names: List<String>): File {
        names.forEach { name ->
            File(platformDir, "$name.m3u").takeIf(File::isDirectory)?.let { return it }
            File(platformDir, name).takeIf(File::isDirectory)?.let { return it }
        }
        return File(platformDir, names.first())
    }

    private fun sanitizeFolderName(name: String): String = name
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(200)

    /** Server dir of this file relative to the rom root, or null for root files. */
    /**
     * Destination for one file of a multi-file rom: `extcontent/` when the game's emulator
     * auto-discovers add-ons there, the game folder itself for the base rom, otherwise the
     * server's own layout. Shared by enqueue and download so a queued temp file and its final
     * target can never disagree.
     */
    private suspend fun resolveGameFileDir(
        gameId: Long,
        gameFileId: Long?,
        platformSlug: String,
        category: String,
        gameFolder: File
    ): File {
        val destination = when {
            extContentOrganizer.placesInExtcontent(gameId, platformSlug, category) ->
                File(gameFolder, ZipExtractor.EXTCONTENT_FOLDER)
            VariantCategory.fromKey(category) == VariantCategory.GAME -> gameFolder
            else -> {
                val serverRelativeDir = gameFileId?.let { resolveServerRelativeDir(gameId, it) }
                if (serverRelativeDir != null) File(gameFolder, serverRelativeDir) else File(gameFolder, category)
            }
        }
        return destination.apply { mkdirs() }
    }

    /**
     * A base rom fetched on its own - the picker's "game" row, downloaded after the add-ons were
     * already on disk - is still the file the game launches from. Without this the game keeps
     * whatever [GameEntity.localPath] it had, which for a part-downloaded game is a playlist or a
     * stale path, and only the per-file row learns where the rom actually landed.
     */
    private suspend fun claimLaunchTargetIfBase(progress: DownloadProgress, finalPath: String) {
        if (VariantCategory.fromKey(progress.fileCategory) != VariantCategory.GAME) return
        val game = gameDao.getById(progress.gameId) ?: return
        val current = game.localPath
        if (current == finalPath) return
        if (current != null && File(current).isFile && !isUnbootableHere(current, game.platformSlug)) return
        gameDao.updateLocalPath(progress.gameId, finalPath, game.source)
        Logger.info(
            TAG,
            "Base rom claimed launch target | game=${progress.gameTitle} path=$finalPath"
        )
    }

    private fun isUnbootableHere(path: String, platformSlug: String): Boolean =
        path.substringAfterLast('.', "").equals("m3u", ignoreCase = true) &&
            !M3uManager.supportsM3u(platformSlug)

    /**
     * Combine Content flattens the base rom and its updates and DLC, and nothing else. Manuals,
     * cheats and the rest keep their own game folder so the platform folder stays a list of games,
     * and so deleting one game cannot strand another's documents beside it.
     */
    private fun isFlatUnderCombine(category: String?): Boolean =
        VariantCategory.fromKey(category) == VariantCategory.GAME ||
            extContentOrganizer.isAddonCategory(category)

    /**
     * True when this game's updates or DLC already sit in the platform-wide `extcontent/`. Promoting
     * its base into a fresh folder would move the rom away from content it leaves behind, so a game
     * the combined layout is holding keeps using the platform folder even once the toggle is off.
     */
    private suspend fun hasPooledAddons(gameId: Long, platformDir: File): Boolean {
        val shared = File(platformDir, ZipExtractor.EXTCONTENT_FOLDER)
        if (!shared.isDirectory) return false
        return gameFileDao.getFilesForGame(gameId).any { row ->
            extContentOrganizer.isAddonCategory(row.category) &&
                row.localPath?.let { File(it).parentFile?.absolutePath } == shared.absolutePath
        }
    }

    private suspend fun resolveServerRelativeDir(gameId: Long, gameFileId: Long): String? {
        val row = gameFileDao.getById(gameFileId) ?: return null
        val rootLen = gameFileDao.getFilesForGame(gameId)
            .minOfOrNull { it.filePath.length } ?: return null
        return row.filePath.takeIf { it.length > rootLen }
            ?.substring(rootLen)?.trim('/')?.takeIf { it.isNotEmpty() }
    }

    private suspend fun resolveAddonFolder(
        gameId: Long,
        platformSlug: String,
        gameFolderName: String?,
        gameTitle: String,
        category: String? = null
    ): File {
        val platformDir = getDownloadDir(platformSlug)
        if (extContentOrganizer.usesCombinedLayout(gameId)) {
            return if (isFlatUnderCombine(category)) {
                platformDir
            } else {
                getGameFolder(platformSlug, *listOfNotNull(gameFolderName, gameTitle).toTypedArray())
            }
        }
        val game = gameDao.getById(gameId)
        val basePath = game?.localPath
        val baseParent = basePath?.let { File(it).parentFile }
        if (baseParent != null && baseParent.isDirectory &&
            baseParent.absolutePath != platformDir.absolutePath
        ) {
            return baseParent
        }
        if (hasPooledAddons(gameId, platformDir)) return platformDir
        val gameFolder = getGameFolder(platformSlug, *listOfNotNull(gameFolderName, gameTitle).toTypedArray())
        val baseFile = basePath?.let { File(it) }
        if (baseFile != null && baseFile.isFile &&
            baseFile.parentFile?.absolutePath == platformDir.absolutePath
        ) {
            val stash = if (gameFolder.absolutePath == baseFile.absolutePath) {
                File(platformDir, ".${baseFile.name}.promoting")
            } else {
                null
            }
            val source = when {
                stash == null -> baseFile
                baseFile.renameTo(stash) && gameFolder.mkdirs() -> stash
                else -> {
                    if (stash.exists() && !stash.renameTo(baseFile)) {
                        Logger.error(
                            TAG,
                            "resolveAddonFolder: stranded ${baseFile.name} at ${stash.name}, " +
                                "could not restore after failed promotion"
                        )
                    }
                    baseFile
                }
            }
            val moved = File(gameFolder, baseFile.name)
            if (source.renameTo(moved)) {
                gameDao.updateLocalPath(gameId, moved.absolutePath, game.source)
                gameFileDao.getByLocalPath(basePath)?.let { row ->
                    gameFileDao.updateLocalPath(row.id, moved.absolutePath, row.downloadedAt ?: Instant.now())
                }
                Logger.info(TAG, "resolveAddonFolder: moved flat base ${baseFile.name} into ${gameFolder.name}")
            } else {
                if (source == stash) stash.renameTo(baseFile)
                Logger.warn(TAG, "resolveAddonFolder: failed to move ${baseFile.name} into ${gameFolder.name}")
            }
        }
        return gameFolder
    }

    private suspend fun processQueue() {
        if (!romMRepository.isConnected()) {
            Log.d(TAG, "processQueue: waiting for RomM connection")
            val connected = withTimeoutOrNull(CONNECTION_WAIT_TIMEOUT_MS) {
                romMRepository.connectionState.first { it is ConnectionState.Connected }
            }
            if (connected == null) {
                Log.d(TAG, "processQueue: RomM connection timeout, deferring")
                return
            }
            Log.d(TAG, "processQueue: RomM connected, proceeding")
        }

        val maxConcurrent = preferencesRepository.userPreferences.first().maxConcurrentDownloads
        val currentActive = totalActiveDownloads()

        Log.d(TAG, "processQueue: maxConcurrent=$maxConcurrent, rommActive=${_state.value.activeDownloads.size}, totalActive=$currentActive")
        Log.d(TAG, "processQueue: queue size=${_state.value.queue.size}, states=${_state.value.queue.map { "${it.gameTitle}:${it.state}" }}")

        if (currentActive >= maxConcurrent) {
            Log.d(TAG, "processQueue: at max capacity, returning")
            return
        }

        val slotsAvailable = maxConcurrent - currentActive
        val nextItems = _state.value.queue
            .filter { it.state == DownloadState.QUEUED }
            .take(slotsAvailable)

        Log.d(TAG, "processQueue: found ${nextItems.size} QUEUED items to process")

        if (nextItems.isEmpty()) {
            Log.d(TAG, "processQueue: no QUEUED items, returning")
            return
        }

        for (next in nextItems) {
            Log.d(TAG, "processQueue: processing ${next.gameTitle}")
            if (downloadJobs[next.id]?.isActive == true) {
                Log.d(TAG, "processQueue: ${next.gameTitle} already has active job, skipping")
                continue
            }

            val plan = planStorage(next, claimStaging = true)
            val availableStorage = getAvailableStorageBytes(next.platformSlug)

            if (plan is StoragePlan.Insufficient) {
                Log.d(TAG, "processQueue: ${next.gameTitle} needs ${plan.requiredBytes}, has ${plan.availableBytes}")
                downloadQueueDao.updateState(next.id, DownloadState.WAITING_FOR_STORAGE.name)
                _state.value = _state.value.copy(
                    queue = _state.value.queue.map {
                        if (it.id == next.id) {
                            it.copy(
                                state = DownloadState.WAITING_FOR_STORAGE,
                                requiredStorageBytes = plan.requiredBytes
                            )
                        } else {
                            it
                        }
                    },
                    availableStorageBytes = availableStorage
                )
                continue
            }

            soundManager.play(SoundType.DOWNLOAD_START)

            _state.value = _state.value.copy(
                activeDownloads = _state.value.activeDownloads + next.copy(state = DownloadState.DOWNLOADING),
                queue = _state.value.queue.filter { it.id != next.id },
                availableStorageBytes = availableStorage
            )

            downloadQueueDao.updateState(next.id, DownloadState.DOWNLOADING.name)

            downloadJobs[next.id] = scope.launch {
                settleDownload(next, downloadRom(next, plan))
            }
        }
    }

    private suspend fun downloadRom(
        progress: DownloadProgress,
        plan: StoragePlan = StoragePlan.Direct
    ): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val platformDir = getDownloadDir(progress.platformSlug)

                val carriedOver = romStagingManager.list()
                    .firstOrNull { it.manifest.downloadId == progress.id }
                if (carriedOver?.manifest?.phase == StagingPhase.MOVING) {
                    return@withContext resumeStagedDeploy(carriedOver, progress)
                }
                val alreadyStaged = carriedOver?.takeIf { hasCompleteStagedArchive(it, progress) }
                if (carriedOver != null && alreadyStaged == null && plan !is StoragePlan.Staged) {
                    Logger.info(
                        TAG,
                        "Discarding staged work, download now goes direct | game=${progress.gameTitle}"
                    )
                    romStagingManager.discard(carriedOver)
                }

                val stagingArea = alreadyStaged ?: (plan as? StoragePlan.Staged)?.let { staged ->
                    romStagingManager.open(
                        StagingManifest(
                            downloadId = progress.id,
                            gameId = progress.gameId,
                            gameTitle = progress.gameTitle,
                            fileName = progress.fileName,
                            destinationDir = staged.destinationDir.absolutePath,
                            phase = StagingPhase.DOWNLOADING
                        )
                    )
                }

                val downloadDir = when {
                    stagingArea != null -> stagingArea.archiveDir
                    progress.isGameFileDownload && progress.fileCategory != null -> {
                        val gameFolder = resolveAddonFolder(
                            progress.gameId, progress.platformSlug, progress.gameFolderName,
                            progress.gameTitle, progress.fileCategory
                        )
                        resolveGameFileDir(
                            progress.gameId, progress.gameFileId, progress.platformSlug,
                            progress.fileCategory, gameFolder
                        )
                    }
                    else -> platformDir
                }

                val tempFile = File(downloadDir, "${progress.fileName}.tmp")
                val targetFile = File(downloadDir, progress.fileName)

                if (targetFile.exists() && targetFile.length() >= progress.totalBytes && progress.totalBytes > 0) {
                    Log.d(TAG, "Target file already complete (${targetFile.length()} bytes), finalizing")
                    return@withContext finalizeCompletedFile(targetFile, platformDir, progress, stagingArea)
                }

                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

                // Temp file already has all the bytes (e.g., app was killed after download
                // finished but before the rename). Promote it directly instead of requesting
                // a Range that the server will reject with 416.
                if (existingBytes > 0 && progress.totalBytes > 0) {
                    if (existingBytes == progress.totalBytes) {
                        Log.d(TAG, "Temp file matches expected size ($existingBytes bytes), promoting to target")
                        promoteTempFile(tempFile, targetFile)
                        return@withContext finalizeCompletedFile(targetFile, platformDir, progress, stagingArea)
                    } else if (existingBytes > progress.totalBytes) {
                        Log.w(TAG, "Temp file oversized ($existingBytes > ${progress.totalBytes}), deleting")
                        tempFile.delete()
                    }
                }

                val rangeHeader = if (existingBytes > 0) "bytes=$existingBytes-" else null

                val endpoint = if (progress.isGameFileDownload) "files/content" else "content"
                Logger.info(
                    TAG,
                    "Download request | game=${progress.gameTitle} gameId=${progress.gameId} " +
                        "endpoint=$endpoint id=${progress.rommId} file=${progress.fileName} " +
                        "dir=${downloadDir.name} resume=${rangeHeader != null}"
                )
                val downloadCall = if (progress.isGameFileDownload) {
                    romMRepository.downloadRomFile(progress.rommId, progress.fileName, rangeHeader)
                } else {
                    romMRepository.downloadRom(
                        progress.rommId, progress.fileName, rangeHeader,
                        fileIds = progress.selectedFileIds?.joinToString(",")
                    )
                }
                when (val result = downloadCall) {
                    is RomMResult.Success -> {
                        val response = result.data
                        val body = response.body
                        val contentType = body.contentType()?.toString() ?: ""
                        val contentLength = body.contentLength()

                        if (INVALID_CONTENT_TYPES.any { contentType.startsWith(it) }) {
                            return@withContext DownloadResult.Failure(
                                DownloadFailureReason.InvalidContentType(contentType)
                            )
                        }

                        if (!response.isPartialContent && existingBytes > 0) {
                            tempFile.delete()
                        }

                        val totalSize = when {
                            response.isPartialContent -> existingBytes + contentLength
                            contentLength > 0 -> contentLength
                            progress.totalBytes > 0 -> progress.totalBytes
                            else -> 0L
                        }

                        if (totalSize > 0 && totalSize < MIN_ROM_SIZE_BYTES) {
                            return@withContext DownloadResult.Failure(DownloadFailureReason.FileTooSmall)
                        }

                        updateProgress(progress.copy(
                            totalBytes = totalSize,
                            bytesDownloaded = if (response.isPartialContent) existingBytes else 0
                        ))

                        val startOffset = if (response.isPartialContent) existingBytes else 0L

                        body.byteStream().use { input ->
                            createOutputStream(tempFile, response.isPartialContent).use { output ->
                                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                                var bytesRead: Long = startOffset
                                var lastUpdateTime = System.currentTimeMillis()
                                var lastDbUpdateTime = System.currentTimeMillis()
                                var lastBytesForSpeed: Long = startOffset
                                var currentSpeed: Long = 0

                                var pauseLogCountdown = 0
                                while (true) {
                                    coroutineContext.ensureActive()

                                    val thermalStatus = thermalManager.get().thermalStatus.value
                                    if (thermalStatus.state == ThermalState.PAUSED) {
                                        if (pauseLogCountdown <= 0) {
                                            Log.w(TAG, "Download paused on thermal state: cpu=${thermalStatus.cpuTemp}C bat=${thermalStatus.batteryTemp}C")
                                            pauseLogCountdown = 6
                                        }
                                        pauseLogCountdown--
                                        delay(5000)
                                        continue
                                    }

                                    val read = input.read(buffer)
                                    if (read == -1) break

                                    output.write(buffer, 0, read)
                                    bytesRead += read

                                    if (thermalStatus.throttleMultiplier < 1.0f) {
                                        val delayMs = ((1.0f / thermalStatus.throttleMultiplier) - 1) * 100
                                        delay(delayMs.toLong())
                                    }

                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdateTime > UI_UPDATE_INTERVAL_MS) {
                                        val timeDeltaMs = now - lastUpdateTime
                                        val bytesDelta = bytesRead - lastBytesForSpeed
                                        currentSpeed = if (timeDeltaMs > 0) {
                                            (bytesDelta * 1000) / timeDeltaMs
                                        } else 0

                                        updateProgress(
                                            progress.copy(
                                                bytesDownloaded = bytesRead,
                                                totalBytes = totalSize,
                                                bytesPerSecond = currentSpeed
                                            )
                                        )
                                        lastUpdateTime = now
                                        lastBytesForSpeed = bytesRead
                                    }

                                    if (now - lastDbUpdateTime > DB_UPDATE_INTERVAL_MS) {
                                        downloadQueueDao.updateProgress(progress.id, bytesRead)
                                        lastDbUpdateTime = now
                                    }
                                }

                                updateProgress(
                                    progress.copy(
                                        bytesDownloaded = bytesRead,
                                        totalBytes = totalSize,
                                        bytesPerSecond = 0
                                    )
                                )
                                downloadQueueDao.updateProgress(progress.id, bytesRead)

                                promoteTempFile(tempFile, targetFile)
                                finalizeCompletedFile(
                                    targetFile, platformDir,
                                    progress.copy(totalBytes = totalSize), stagingArea
                                )
                            }
                        }
                    }
                    is RomMResult.Error -> {
                        if (result.code == 416 && tempFile.exists()) {
                            val tempSize = tempFile.length()
                            if (progress.totalBytes > 0 && tempSize == progress.totalBytes) {
                                Log.w(TAG, "416: temp file ($tempSize bytes) matches expected, promoting as complete")
                                promoteTempFile(tempFile, targetFile)
                                return@withContext finalizeCompletedFile(targetFile, platformDir, progress, stagingArea)
                            } else {
                                Log.w(TAG, "416: temp file ($tempSize bytes) vs expected (${progress.totalBytes}), deleting and retrying")
                                tempFile.delete()
                                downloadQueueDao.updateProgress(progress.id, 0)
                                return@withContext downloadRom(progress.copy(bytesDownloaded = 0), plan)
                            }
                        }
                        Logger.warn(
                            TAG,
                            "Download failed | game=${progress.gameTitle} id=${progress.rommId} " +
                                "file=${progress.fileName} code=${result.code} msg=${result.message}"
                        )
                        DownloadResult.Failure(DownloadFailureReason.ServerError(result.message))
                    }
                }
            } catch (_: CancellationException) {
                DownloadResult.Cancelled
            } catch (e: Exception) {
                Logger.warn(
                    TAG,
                    "Download error | game=${progress.gameTitle} id=${progress.rommId} file=${progress.fileName}",
                    e
                )
                DownloadResult.Failure(DownloadFailureReason.Unexpected(e.message))
            }
        }

    private suspend fun finalizeCompletedFile(
        targetFile: File,
        platformDir: File,
        progress: DownloadProgress,
        stagingArea: StagingArea? = null
    ): DownloadResult {
        val unpackDir = stagingArea?.outputDir ?: platformDir
        checkUnpackRoom(targetFile, unpackDir, progress, staged = stagingArea != null)
            ?.let { return it }

        val unpackedPath = if (progress.isGameFileDownload) {
            targetFile.absolutePath
        } else {
            processDownloadedFile(
                targetFile = targetFile,
                platformDir = unpackDir,
                platformSlug = progress.platformSlug,
                gameTitle = progress.gameTitle,
                gameFolderName = progress.gameFolderName,
                progressId = progress.id,
                isDiscDownload = progress.isDiscDownload,
                expectedSize = progress.totalBytes,
                isMultiFileRom = progress.isMultiFileRom,
                onExtractionProgress = { bytesWritten, totalBytes ->
                    updateProgress(
                        progress.copy(
                            state = DownloadState.EXTRACTING,
                            extractionBytesWritten = bytesWritten,
                            extractionTotalBytes = totalBytes
                        )
                    )
                }
            )
        }

        val finalPath = if (stagingArea != null) {
            when (val deployed = deployStagedOutput(stagingArea, unpackedPath, progress)) {
                is StagedDeployResult.Success -> deployed.finalPath
                is StagedDeployResult.Failure -> return deployed.result
            }
        } else {
            unpackedPath
        }

        return linkCompletedDownload(progress, finalPath, platformDir)
    }

    private suspend fun linkCompletedDownload(
        progress: DownloadProgress,
        deployedPath: String,
        platformDir: File
    ): DownloadResult {
        var finalPath = deployedPath
        Log.d(TAG, "linkCompletedDownload: path=$finalPath, gameTitle=${progress.gameTitle}")

        if (progress.isGameFileDownload && !File(finalPath).exists()) {
            Logger.warn(
                TAG,
                "Download finalize failed | game=${progress.gameTitle} file=${progress.fileName} " +
                    "missing at $finalPath"
            )
            return DownloadResult.Failure(DownloadFailureReason.DownloadedFileMissing)
        }
        Logger.info(
            TAG,
            "Download complete | game=${progress.gameTitle} file=${progress.fileName} path=$finalPath"
        )

        if (!progress.isGameFileDownload &&
            extContentOrganizer.usesExtcontent(progress.gameId, progress.platformSlug)
        ) {
            val combined = if (extContentOrganizer.usesCombinedLayout(progress.gameId)) {
                gameDao.getById(progress.gameId)?.copy(localPath = finalPath)?.let { game ->
                    extContentOrganizer.enforceCombinedLayout(game, getDownloadDir(progress.platformSlug))
                }
            } else {
                null
            }
            if (combined != null) {
                finalPath = combined.absolutePath
            } else {
                extContentOrganizer.consolidate(finalPath, getDownloadDir(progress.platformSlug))
            }
        }

        when {
            progress.isGameFileDownload && progress.gameFileId != null -> {
                gameFileDao.updateLocalPath(progress.gameFileId, finalPath, Instant.now())
                maybeComputeRomHashPrefix(progress.gameFileId, progress.gameId, finalPath)
                claimLaunchTargetIfBase(progress, finalPath)
            }
            progress.isDiscDownload && progress.discId != null -> {
                gameDiscDao.updateLocalPath(progress.discId, finalPath)
                m3uManager.generateM3uIfComplete(progress.gameId)
            }
            else -> {
                gameDao.updateLocalPath(progress.gameId, finalPath, GameSource.ROMM_SYNCED)
                if (progress.selectedFileIds != null) {
                    mapSelectedFilesToDisk(progress.gameId, progress.selectedFileIds, File(finalPath))
                }
                relocateSoundtrackFiles(progress.gameId, File(finalPath), platformDir)
                attributionRepository.markDirty(StorageCategory.MUSIC)
            }
        }
        attributionRepository.markDirty(StorageCategory.GAMES)

        _completionEvents.emit(
            DownloadCompletionEvent(
                gameId = progress.gameId,
                rommId = progress.rommId,
                localPath = finalPath,
                isDiscDownload = progress.isDiscDownload
            )
        )

        return DownloadResult.Success(progress.totalBytes)
    }

    /**
     * True when staging already holds the whole archive for this download.
     *
     * Those bytes are spent: re-fetching them because internal storage no longer has room for a
     * fresh staged download would charge the user a second transfer for a file that is sitting on
     * the device. Whether the unpack can go ahead is a separate question, answered against the
     * real expanded size once the archive is opened.
     */
    private fun hasCompleteStagedArchive(area: StagingArea, progress: DownloadProgress): Boolean {
        if (progress.totalBytes <= 0) return false
        val archive = File(area.archiveDir, progress.fileName)
        return archive.isFile && archive.length() >= progress.totalBytes
    }

    /**
     * The second half of the storage gate, run once the archive is on disk.
     *
     * The pre-download reserve could only guess at how far the archive would expand. Zip and 7z
     * both publish their uncompressed totals, so the guess is replaced with the real figure before
     * a single entry is written - a highly compressible archive can expand far past any multiplier,
     * and the volume it expands onto is the one that runs dry. Returns null when there is room, or
     * the result to settle the download with when there is not; the archive is kept either way, so
     * freeing space and resuming costs no transfer.
     */
    private suspend fun checkUnpackRoom(
        archive: File,
        unpackDir: File,
        progress: DownloadProgress,
        staged: Boolean
    ): DownloadResult? = withContext(Dispatchers.IO) {
        if (progress.isGameFileDownload) return@withContext null
        if (!ArchiveExpansion.expandsOnDisk(progress.fileName, progress.platformSlug, progress.isMultiFileRom)) {
            return@withContext null
        }
        val expandedBytes = ArchiveExpansion.measure(archive) ?: return@withContext null
        val reserve = if (staged) INTERNAL_STAGING_RESERVE_BYTES else STORAGE_BUFFER_BYTES
        val required = expandedBytes + reserve
        val free = romStagingManager.availableBytes(unpackDir) ?: return@withContext null
        if (free >= required) return@withContext null

        val location = if (staged) {
            DownloadFailureReason.StorageLocation.INTERNAL
        } else {
            DownloadFailureReason.StorageLocation.ROM
        }
        Logger.warn(
            TAG,
            "Unpack deferred, $location full | game=${progress.gameTitle} " +
                "expanded=$expandedBytes need=$required free=$free"
        )
        DownloadResult.WaitingForStorage(
            DownloadFailureReason.InsufficientUnpackSpace(
                requiredBytes = required,
                availableBytes = free,
                location = location
            )
        )
    }

    private sealed class StagedDeployResult {
        data class Success(val finalPath: String) : StagedDeployResult()
        data class Failure(val result: DownloadResult) : StagedDeployResult()
    }

    /**
     * Carries a download that is already its own deliverable from the staging area's archive folder
     * into its output folder, and answers with the path the deploy should look for.
     *
     * Platforms whose cores read an archive directly are never unpacked, so nothing is written to
     * the output folder and the file to install is the download itself. Moving it keeps one deploy
     * path for both kinds rather than teaching the mover about a second source.
     */
    private fun promoteArchiveToOutput(area: StagingArea, archivePath: String): String? {
        val source = File(archivePath)
        if (!source.isFile) return null
        if (!area.outputDir.isDirectory && !area.outputDir.mkdirs()) return null
        val destination = File(area.outputDir, source.name)
        if (source.renameTo(destination)) return destination.name
        return runCatching {
            source.copyTo(destination, overwrite = true)
            source.delete()
            destination.name
        }.getOrNull()
    }

    private suspend fun deployStagedOutput(
        area: StagingArea,
        unpackedPath: String,
        progress: DownloadProgress
    ): StagedDeployResult {
        val outputPrefix = area.outputDir.absolutePath + File.separator
        val archivePrefix = area.archiveDir.absolutePath + File.separator
        val relative = when {
            unpackedPath.startsWith(outputPrefix) -> unpackedPath.removePrefix(outputPrefix)
            unpackedPath.startsWith(archivePrefix) -> promoteArchiveToOutput(area, unpackedPath)
            else -> null
        }
        if (relative == null) {
            romStagingManager.discard(area)
            Logger.warn(
                TAG,
                "Staged unpack escaped its folder | game=${progress.gameTitle} path=$unpackedPath"
            )
            return StagedDeployResult.Failure(
                DownloadResult.Failure(DownloadFailureReason.UnpackEscapedStagingFolder)
            )
        }
        return deployStagedArea(romStagingManager.advance(area, StagingPhase.MOVING, relative), progress)
    }

    /**
     * Carries a finished staged tree onto the rom volume.
     *
     * The destination is measured again here rather than trusted from the pre-download estimate:
     * by this point the real unpacked size is on disk and the card may have filled during a
     * multi-hour transfer. Running out here parks the download instead of failing it, because the
     * archive is already fetched and unpacked - throwing that away to make room is the opposite of
     * what the user wants.
     */
    private suspend fun deployStagedArea(
        area: StagingArea,
        progress: DownloadProgress
    ): StagedDeployResult {
        val relative = area.manifest.launchRelPath
            ?: return StagedDeployResult.Failure(DownloadResult.Failure(DownloadFailureReason.StagedPathMissing))
        val destinationDir = area.destinationDir
        val outputBytes = romStagingManager.outputBytes(area)
        val destinationFree = romStagingManager.availableBytes(destinationDir)
        val required = outputBytes + STORAGE_BUFFER_BYTES

        if (destinationFree != null && destinationFree < required) {
            Logger.warn(
                TAG,
                "Deploy deferred, ROM storage full | game=${progress.gameTitle} " +
                    "need=$required free=$destinationFree"
            )
            return StagedDeployResult.Failure(
                DownloadResult.WaitingForStorage(
                    DownloadFailureReason.InsufficientDeploySpace(
                        requiredBytes = required,
                        availableBytes = destinationFree
                    )
                )
            )
        }

        downloadQueueDao.updateState(progress.id, DownloadState.MOVING.name)
        updateProgress(
            progress.copy(
                state = DownloadState.MOVING,
                extractionBytesWritten = 0,
                extractionTotalBytes = outputBytes
            )
        )

        val moved = withContext(Dispatchers.IO) {
            romStagingManager.deploy(area) { copiedBytes, totalBytes ->
                updateProgress(
                    progress.copy(
                        state = DownloadState.MOVING,
                        extractionBytesWritten = copiedBytes,
                        extractionTotalBytes = totalBytes
                    )
                )
            }
        }
        if (!moved) {
            return StagedDeployResult.Failure(
                DownloadResult.Failure(DownloadFailureReason.MoveToStorageFailed)
            )
        }

        val finalFile = File(destinationDir, relative)
        if (!finalFile.exists()) {
            Logger.warn(TAG, "Deploy verify failed | expected ${finalFile.absolutePath}")
            return StagedDeployResult.Failure(
                DownloadResult.Failure(DownloadFailureReason.MovedFileNotFound)
            )
        }

        romStagingManager.discard(area)
        attributionRepository.markDirty(StorageCategory.ROM_STAGING)
        Logger.info(
            TAG,
            "Deployed staged game | game=${progress.gameTitle} bytes=$outputBytes " +
                "path=${finalFile.absolutePath}"
        )
        return StagedDeployResult.Success(finalFile.absolutePath)
    }

    private suspend fun resumeStagedDeploy(area: StagingArea, progress: DownloadProgress): DownloadResult {
        val platformDir = getDownloadDir(progress.platformSlug)
        Logger.info(TAG, "Resuming interrupted move | game=${progress.gameTitle}")
        return when (val deployed = deployStagedArea(area, progress)) {
            is StagedDeployResult.Success ->
                linkCompletedDownload(progress, deployed.finalPath, platformDir)
            is StagedDeployResult.Failure -> deployed.result
        }
    }

    /**
     * A download parked mid-move only needs room for what is left to carry across, not for the
     * archive it already fetched and unpacked. Charging it the download requirement again would
     * hold it in the queue long after the user has freed exactly the space it asked for.
     */
    private suspend fun hasRoomForStagedMove(area: StagingArea): Boolean = withContext(Dispatchers.IO) {
        val free = romStagingManager.availableBytes(area.destinationDir) ?: return@withContext true
        free >= romStagingManager.outputBytes(area) + STORAGE_BUFFER_BYTES
    }

    private suspend fun sweepAbandonedStaging(liveDownloadIds: Set<Long>) {
        val freed = withContext(Dispatchers.IO) { romStagingManager.cleanAbandoned(liveDownloadIds) }
        if (freed > 0) attributionRepository.markDirty(StorageCategory.ROM_STAGING)
    }

    /**
     * Discards every staging folder whose download is no longer queued, and reports the bytes
     * reclaimed. Work belonging to a live queue row is left alone, so this cannot cost a user a
     * download that is merely paused.
     */
    suspend fun cleanAbandonedStaging(): Long {
        val live = downloadQueueDao.getPendingDownloads().map { it.id }.toSet() +
            _state.value.activeDownloads.map { it.id } +
            _state.value.queue.map { it.id }
        val freed = withContext(Dispatchers.IO) { romStagingManager.cleanAbandoned(live) }
        if (freed > 0) attributionRepository.markDirty(StorageCategory.ROM_STAGING)
        Logger.info(TAG, "Cleaned abandoned download staging | bytes=$freed")
        return freed
    }

    private fun promoteTempFile(tempFile: File, targetFile: File) {
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
    }

    private suspend fun maybeComputeRomHashPrefix(
        gameFileId: Long,
        gameId: Long,
        finalPath: String
    ) {
        try {
            val game = gameDao.getById(gameId) ?: return
            if (!com.nendo.argosy.data.netplay.RomHashComputer.isNetplayEligible(game.platformSlug)) {
                return
            }
            val hash = withContext(Dispatchers.IO) {
                com.nendo.argosy.data.netplay.RomHashComputer.computeRomHashPrefix(finalPath)
            } ?: return
            gameFileDao.updateRomHashPrefix(gameFileId, hash)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute ROM hash prefix: ${e.message}")
        }
    }

    private fun createOutputStream(tempFile: File, isResume: Boolean): java.io.OutputStream {
        return if (isResume && tempFile.exists()) {
            RandomAccessFile(tempFile, "rw").apply {
                seek(tempFile.length())
            }.let { raf ->
                object : java.io.OutputStream() {
                    override fun write(b: Int) = raf.write(b)
                    override fun write(b: ByteArray, off: Int, len: Int) = raf.write(b, off, len)
                    override fun close() = raf.close()
                }
            }
        } else {
            FileOutputStream(tempFile)
        }
    }

    private suspend fun processDownloadedFile(
        targetFile: File,
        platformDir: File,
        platformSlug: String,
        gameTitle: String,
        gameFolderName: String? = null,
        progressId: Long = 0,
        isDiscDownload: Boolean = false,
        expectedSize: Long = 0,
        isMultiFileRom: Boolean = false,
        onExtractionProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ): String {
        val shouldExtract = when {
            isMultiFileRom -> ZipExtractor.isArchiveFile(targetFile)
            ZipExtractor.usesZipAsRomFormat(platformSlug) -> false
            else -> ZipExtractor.shouldExtractArchive(targetFile, platformSlug)
        }

        if (shouldExtract && onExtractionProgress != null) {
            downloadQueueDao.updateState(progressId, DownloadState.EXTRACTING.name)
            onExtractionProgress(0L, targetFile.length())
        }

        Log.d(TAG, "processDownloadedFile: targetFile=${targetFile.absolutePath}, shouldExtract=$shouldExtract, isMultiFileRom=$isMultiFileRom, usesZipAsRom=${ZipExtractor.usesZipAsRomFormat(platformSlug)}, isNsw=${ZipExtractor.isNswPlatform(platformSlug)}, isDisc=$isDiscDownload")

        val resultPath = when {
            shouldExtract -> {
                Log.d(TAG, "processDownloadedFile: BRANCH=ZIP_EXTRACT")

                val validationResult = ZipExtractor.validateArchive(targetFile, expectedSize)
                if (validationResult is ZipExtractor.ArchiveValidationResult.Invalid) {
                    Log.e(TAG, "ZIP validation failed: ${validationResult.reason}")
                    targetFile.delete()
                    throw java.io.IOException("${validationResult.reason}. Please try downloading again.")
                }

                val extracted = try {
                    ZipExtractor.extractFolderRom(
                        archiveFilePath = targetFile,
                        gameTitle = gameTitle,
                        platformDir = platformDir,
                        platformSlug = platformSlug,
                        onProgress = onExtractionProgress
                    )
                } catch (e: java.util.zip.ZipException) {
                    Log.e(TAG, "ZIP extraction failed: ${e.message}")
                    targetFile.delete()
                    throw java.io.IOException("ZIP file is corrupted: ${e.message}. Please try downloading again.")
                }

                val extractedPath = extracted.launchPath
                Log.d(TAG, "processDownloadedFile: extracted.launchPath=$extractedPath, extracted.gameFolder=${extracted.gameFolder}")
                if (File(extractedPath).exists()) {
                    if (targetFile.isFile) {
                        targetFile.delete()
                    }
                    extractedPath
                } else {
                    throw java.io.IOException("Extraction failed: $extractedPath does not exist")
                }
            }
            ZipExtractor.isNswPlatform(platformSlug) -> {
                Log.d(TAG, "processDownloadedFile: BRANCH=NSW_ORGANIZE")
                val organizedFile = ZipExtractor.organizeNswSingleFile(
                    romFile = targetFile,
                    gameTitle = gameTitle,
                    platformDir = platformDir
                )
                Log.d(TAG, "processDownloadedFile: organizedFile=${organizedFile.absolutePath}")
                organizedFile.absolutePath
            }
            isDiscDownload && M3uManager.supportsM3u(platformSlug) -> {
                Log.d(TAG, "processDownloadedFile: BRANCH=DISC_ORGANIZE")
                val result = organizeDiscFile(targetFile, listOfNotNull(gameFolderName, gameTitle), platformDir)
                Log.d(TAG, "processDownloadedFile: discResult=$result")
                result
            }
            else -> {
                // RomM occasionally flags a ROM as hasNestedSingleFile but streams
                // the nested file's raw bytes without wrapping in a zip container.
                // Detect that (via content magic) and rename the misleading .zip
                // extension to the real ROM extension so emulators can open it.
                val detected = ZipExtractor.detectRomFormatByMagic(targetFile)
                if (detected != null && !targetFile.extension.equals(detected, ignoreCase = true)) {
                    val renamed = File(targetFile.parentFile, "${targetFile.nameWithoutExtension}.$detected")
                    Log.d(TAG, "processDownloadedFile: BRANCH=RENAME_BY_MAGIC (detected=$detected, ${targetFile.name} -> ${renamed.name})")
                    if (targetFile.renameTo(renamed)) {
                        renamed.absolutePath
                    } else {
                        Log.w(TAG, "processDownloadedFile: rename failed, falling back to original path")
                        targetFile.absolutePath
                    }
                } else {
                    Log.d(TAG, "processDownloadedFile: BRANCH=PASSTHROUGH (no processing)")
                    targetFile.absolutePath
                }
            }
        }

        val resultFile = File(resultPath)
        if (ZipExtractor.isNswPlatform(platformSlug) &&
            NszDecompressor.isCompressedNsw(resultFile)
        ) {
            Log.d(TAG, "processDownloadedFile: NSZ/XCZ detected, decompressing")
            val decompressed = NszDecompressor.decompress(
                inputFile = resultFile,
                onProgress = onExtractionProgress
            )
            return decompressed.absolutePath
        }

        return resultPath
    }

    private fun organizeDiscFile(romFile: File, names: List<String>, platformDir: File): String {
        val gameFolder = resolveGameFolder(platformDir, names.map(::sanitizeFolderName)).apply { mkdirs() }
        val targetFile = File(gameFolder, romFile.name)

        if (romFile.absolutePath != targetFile.absolutePath) {
            if (!romFile.renameTo(targetFile)) {
                romFile.copyTo(targetFile, overwrite = true)
                romFile.delete()
            }
        }

        return targetFile.absolutePath
    }

    private fun updateProgress(progress: DownloadProgress) {
        _state.value = _state.value.copy(
            activeDownloads = _state.value.activeDownloads.map {
                if (it.id == progress.id) progress else it
            }
        )
    }

    fun pauseDownload(rommId: Long) {
        val active = _state.value.activeDownloads.find { it.rommId == rommId }
        if (active != null) {
            downloadJobs[active.id]?.cancel()
            downloadJobs.remove(active.id)

            scope.launch {
                downloadQueueDao.updateState(active.id, DownloadState.PAUSED.name)
                downloadQueueDao.updateProgress(active.id, active.bytesDownloaded)
            }

            _state.value = _state.value.copy(
                activeDownloads = _state.value.activeDownloads.filter { it.id != active.id },
                queue = listOf(active.copy(state = DownloadState.PAUSED)) + _state.value.queue
            )
            notifySlotFreed()
        } else {
            _state.value = _state.value.copy(
                queue = _state.value.queue.map {
                    if (it.rommId == rommId && it.state == DownloadState.QUEUED) {
                        scope.launch { downloadQueueDao.updateState(it.id, DownloadState.PAUSED.name) }
                        it.copy(state = DownloadState.PAUSED)
                    } else it
                }
            )
        }
    }

    fun resumeDownload(gameId: Long) {
        val paused = _state.value.queue.find {
            it.gameId == gameId && (it.state == DownloadState.PAUSED || it.state == DownloadState.WAITING_FOR_STORAGE)
        }
        if (paused != null) {
            scope.launch {
                downloadQueueDao.updateState(paused.id, DownloadState.QUEUED.name)
            }

            _state.value = _state.value.copy(
                queue = listOf(paused.copy(state = DownloadState.QUEUED)) +
                        _state.value.queue.filter { it.gameId != gameId }
            )

            scope.launch { processQueue() }
        }
    }

    suspend fun recheckStorageAndResume() {
        val waiting = _state.value.queue.filter { it.state == DownloadState.WAITING_FOR_STORAGE }
        if (waiting.isEmpty()) {
            updateAvailableStorage()
            return
        }

        val stagedMoves = withContext(Dispatchers.IO) {
            romStagingManager.list()
                .filter { it.manifest.phase == StagingPhase.MOVING }
                .associateBy { it.manifest.downloadId }
        }

        var anyResumed = false
        for (item in waiting) {
            val staged = stagedMoves[item.id]
            val admitted = if (staged != null) {
                hasRoomForStagedMove(staged)
            } else {
                planStorage(item, claimStaging = false) !is StoragePlan.Insufficient
            }
            if (admitted) {
                downloadQueueDao.updateState(item.id, DownloadState.QUEUED.name)
                anyResumed = true
            }
        }

        if (anyResumed) {
            restoreQueueFromDatabase()
        } else {
            updateAvailableStorage()
        }
    }

    fun cancelDownload(rommId: Long) {
        soundManager.play(SoundType.DOWNLOAD_CANCEL)
        val active = _state.value.activeDownloads.find { it.rommId == rommId }
        if (active != null) {
            downloadJobs[active.id]?.cancel()
            downloadJobs.remove(active.id)
            scope.launch {
                downloadQueueDao.deleteById(active.id)
                withContext(Dispatchers.IO) {
                    val platformDir = getDownloadDir(active.platformSlug)
                    val tempFile = File(platformDir, "${active.fileName}.tmp")
                    if (tempFile.exists()) tempFile.delete()
                }
                discardStagingFor(active.id)
            }
            _state.value = _state.value.copy(
                activeDownloads = _state.value.activeDownloads.filter { it.id != active.id }
            )
            notifySlotFreed()
        } else {
            val queued = _state.value.queue.find { it.rommId == rommId }
            if (queued != null) {
                scope.launch {
                    downloadQueueDao.deleteById(queued.id)
                    withContext(Dispatchers.IO) {
                        val platformDir = getDownloadDir(queued.platformSlug)
                        val tempFile = File(platformDir, "${queued.fileName}.tmp")
                        if (tempFile.exists()) tempFile.delete()
                    }
                    discardStagingFor(queued.id)
                }
                _state.value = _state.value.copy(
                    queue = _state.value.queue.filter { it.rommId != rommId }
                )
            }
        }
    }

    fun clearCompleted() {
        scope.launch {
            downloadQueueDao.clearCompleted()
        }
        _state.value = _state.value.copy(completed = emptyList())
    }

    fun clearFinished() {
        scope.launch {
            downloadQueueDao.clearFinished()
        }
        _state.value = _state.value.copy(completed = emptyList())
    }

    fun removeFromCompleted(downloadId: Long) {
        scope.launch {
            downloadQueueDao.deleteById(downloadId)
        }
        _state.value = _state.value.copy(
            completed = _state.value.completed.filter { it.id != downloadId }
        )
    }

    fun retryDownload(downloadId: Long) {
        val item = _state.value.completed.find { it.id == downloadId } ?: return

        scope.launch {
            downloadQueueDao.deleteById(downloadId)

            _state.value = _state.value.copy(
                completed = _state.value.completed.filter { it.id != downloadId }
            )

            when {
                item.isDiscDownload -> enqueueDiscDownload(
                    gameId = item.gameId,
                    discId = item.discId!!,
                    discNumber = item.discNumber ?: 1,
                    rommId = item.rommId,
                    fileName = item.fileName,
                    gameTitle = item.gameTitle,
                    gameFolderName = item.gameFolderName,
                    platformSlug = item.platformSlug,
                    coverPath = item.coverPath,
                    expectedSizeBytes = item.totalBytes
                )
                item.isGameFileDownload -> enqueueGameFileDownload(
                    gameId = item.gameId,
                    gameFileId = item.gameFileId!!,
                    rommFileId = item.rommId,
                    fileName = item.fileName,
                    category = item.fileCategory ?: "unknown",
                    gameTitle = item.gameTitle,
                    platformSlug = item.platformSlug,
                    coverPath = item.coverPath,
                    expectedSizeBytes = item.totalBytes,
                    gameFolderName = item.gameFolderName
                )
                else -> enqueueDownload(
                    gameId = item.gameId,
                    rommId = item.rommId,
                    fileName = item.fileName,
                    gameTitle = item.gameTitle,
                    platformSlug = item.platformSlug,
                    coverPath = item.coverPath,
                    expectedSizeBytes = item.totalBytes,
                    isMultiFileRom = item.isMultiFileRom,
                    selectedFileIds = item.selectedFileIds
                )
            }
        }
    }

    suspend fun getDownloadPath(platformSlug: String, fileName: String): File {
        val platformDir = getDownloadDir(platformSlug)
        return File(platformDir, fileName)
    }

    sealed class ExtractionResult {
        data class Success(val localPath: String) : ExtractionResult()
        data class Failure(val reason: DownloadFailureReason) : ExtractionResult()
    }

    suspend fun retryExtraction(gameId: Long): ExtractionResult {
        val queueEntry = downloadQueueDao.getByGameId(gameId)
            ?: return ExtractionResult.Failure(DownloadFailureReason.NoDownloadEntryFound)

        val platformDir = getDownloadDir(queueEntry.platformSlug)
        val targetFile = File(platformDir, queueEntry.fileName)

        if (!targetFile.exists()) {
            return ExtractionResult.Failure(DownloadFailureReason.DownloadedFileNoLongerExists)
        }

        return try {
            val discId = queueEntry.discId
            val isDiscDownload = discId != null
            val finalPath = processDownloadedFile(
                targetFile = targetFile,
                platformDir = platformDir,
                platformSlug = queueEntry.platformSlug,
                gameTitle = queueEntry.gameTitle,
                gameFolderName = queueEntry.gameFolderName,
                progressId = queueEntry.id,
                isDiscDownload = isDiscDownload,
                expectedSize = queueEntry.totalBytes,
                isMultiFileRom = queueEntry.isMultiFileRom,
                onExtractionProgress = { bytesWritten, totalBytes ->
                    val progress = queueEntry.toDownloadProgress()
                    updateProgress(
                        progress.copy(
                            state = DownloadState.EXTRACTING,
                            extractionBytesWritten = bytesWritten,
                            extractionTotalBytes = totalBytes
                        )
                    )
                }
            )

            when {
                discId != null -> {
                    gameDiscDao.updateLocalPath(discId, finalPath)
                    m3uManager.generateM3uIfComplete(gameId)
                }
                queueEntry.gameFileId != null -> {
                    gameFileDao.updateLocalPath(queueEntry.gameFileId, finalPath, Instant.now())
                }
                else -> {
                    gameDao.updateLocalPath(gameId, finalPath, GameSource.ROMM_SYNCED)
                }
            }
            downloadQueueDao.deleteByGameId(gameId)
            attributionRepository.markDirty(StorageCategory.GAMES)

            _completionEvents.emit(
                DownloadCompletionEvent(
                    gameId = gameId,
                    rommId = queueEntry.rommId,
                    localPath = finalPath,
                    isDiscDownload = isDiscDownload
                )
            )

            soundManager.play(SoundType.DOWNLOAD_COMPLETE)
            ExtractionResult.Success(finalPath)
        } catch (e: Exception) {
            val reason = DownloadFailureReason.ExtractionFailed(e.message)
            downloadQueueDao.updateState(
                queueEntry.id,
                DownloadState.FAILED.name,
                DownloadFailureReasonCodec.encode(reason)
            )
            ExtractionResult.Failure(reason)
        }
    }

    suspend fun deleteFileAndRedownload(gameId: Long) {
        val queueEntry = downloadQueueDao.getByGameId(gameId)
        if (queueEntry != null) {
            val platformDir = getDownloadDir(queueEntry.platformSlug)
            val targetFile = File(platformDir, queueEntry.fileName)
            val tempFile = File(platformDir, "${queueEntry.fileName}.tmp")

            if (targetFile.exists()) targetFile.delete()
            if (tempFile.exists()) tempFile.delete()

            discardStagingFor(queueEntry.id)
            downloadQueueDao.deleteByGameId(gameId)
        }
    }

    private suspend fun discardStagingFor(downloadId: Long) {
        val discarded = withContext(Dispatchers.IO) {
            romStagingManager.list()
                .firstOrNull { it.manifest.downloadId == downloadId }
                ?.also { romStagingManager.discard(it) }
        }
        if (discarded != null) attributionRepository.markDirty(StorageCategory.ROM_STAGING)
    }

    private suspend fun relocateSoundtrackFiles(
        gameId: Long,
        finalTarget: File,
        platformDir: File
    ) {
        val gameFolder = if (finalTarget.isDirectory) finalTarget else finalTarget.parentFile ?: return
        if (gameFolder.absolutePath == platformDir.absolutePath) return
        val rows = gameFileDao.getFilesByCategory(gameId, VariantCategory.SOUNDTRACK.key)
        if (rows.isEmpty()) return
        val game = gameDao.getById(gameId) ?: return
        val platformName = platformDao.getBySlug(game.platformSlug)?.name ?: game.platformSlug
        val folderPrefix = gameFolder.absolutePath + File.separator
        val onDisk = gameFolder.walkTopDown().filter { it.isFile }.toList()
        val emptiedDirs = mutableSetOf<File>()
        for (row in rows) {
            val fromLocalPath = row.localPath?.let(::File)
                ?.takeIf { it.absolutePath.startsWith(folderPrefix) && it.exists() }
            val source = fromLocalPath ?: onDisk.firstOrNull { it.name == row.fileName } ?: continue
            val target = musicDirectoryManager.targetFileFor(
                platformName, game.title, row.trackNumber, row.trackTitle, row.fileName
            )
            if (source.absolutePath == target.absolutePath) continue
            if (musicDirectoryManager.moveIntoMusic(source, target)) {
                gameFileDao.updateLocalPath(row.id, target.absolutePath, Instant.now())
                musicDirectoryManager.scanFile(target)
                source.parentFile
                    ?.takeIf { it.absolutePath != gameFolder.absolutePath }
                    ?.let { emptiedDirs.add(it) }
                Logger.info(TAG, "Relocated soundtrack ${row.fileName} -> ${target.absolutePath}")
            } else {
                Logger.warn(TAG, "Failed to relocate soundtrack ${row.fileName} to ${target.absolutePath}")
            }
        }
        emptiedDirs.forEach { dir ->
            if (dir.listFiles().isNullOrEmpty()) dir.delete()
        }
    }

    private suspend fun mapSelectedFilesToDisk(
        gameId: Long,
        selectedRommFileIds: List<Long>,
        finalTarget: File
    ) {
        val gameFolder = finalTarget.parentFile ?: return
        val onDisk = gameFolder.walkTopDown().filter { it.isFile }.toList()
        for (rommFileId in selectedRommFileIds) {
            val row = gameFileDao.getByRommFileId(rommFileId) ?: continue
            if (row.gameId != gameId || row.localPath != null) continue
            val match = onDisk.firstOrNull { it.name == row.fileName } ?: continue
            gameFileDao.updateLocalPath(row.id, match.absolutePath, Instant.now())
        }
    }

    private fun DownloadQueueEntity.toDownloadProgress(): DownloadProgress {
        return DownloadProgress(
            id = id,
            gameId = gameId,
            rommId = rommId,
            discId = discId,
            discNumber = discNumber,
            gameFileId = gameFileId,
            fileCategory = fileCategory,
            fileName = fileName,
            gameTitle = gameTitle,
            gameFolderName = gameFolderName,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            state = try {
                DownloadState.valueOf(state)
            } catch (e: Exception) {
                DownloadState.QUEUED
            },
            errorReason = DownloadFailureReasonCodec.decode(errorReason),
            isMultiFileRom = isMultiFileRom,
            selectedFileIds = selectedFileIds
                ?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
                ?.takeIf { it.isNotEmpty() }
        )
    }
}
