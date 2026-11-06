package com.nendo.argosy.data.download

import android.util.Log
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.core.input.AppEventFeedback
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.download.nsz.NszDecompressor
import com.nendo.argosy.data.emulator.M3uManager
import com.nendo.argosy.data.repository.CustomGridShapeStore
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.util.FileNames
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.nendo.argosy.data.download.transfer.TransferCheckpoint
import com.nendo.argosy.data.download.transfer.TransferResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val STORAGE_BUFFER_BYTES = 50 * 1024 * 1024L
private const val INTERNAL_STAGING_RESERVE_BYTES = 1024 * 1024 * 1024L
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
    val isAwaitingServer: Boolean = false,
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
    RETRYING,
    WAITING_FOR_NETWORK,
    EXTRACTING,
    MOVING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

private sealed class DownloadResult {
    data class Success(val bytesWritten: Long, val localPath: String? = null) : DownloadResult()
    data class Failure(val reason: DownloadFailureReason) : DownloadResult()
    data class WaitingForStorage(val reason: DownloadFailureReason) : DownloadResult()
    data object Cancelled : DownloadResult()
}

private const val MIN_ROM_SIZE_BYTES = 1024L

private val SMALL_COMPANION_EXTENSIONS = setOf(
    "cue", "m3u", "ccd", "sub", "sbi", "toc", "gdi", "xml", "txt", "nfo", "dat"
)

private fun isSmallCompanionFile(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in SMALL_COMPANION_EXTENSIONS

data class DownloadQueueState(
    val activeDownloads: List<DownloadProgress> = emptyList(),
    val queue: List<DownloadProgress> = emptyList(),
    val completed: List<DownloadProgress> = emptyList(),
    val availableStorageBytes: Long = 0
) {
    @Deprecated("Use activeDownloads instead", ReplaceWith("activeDownloads.firstOrNull()"))
    val activeDownload: DownloadProgress?
        get() = activeDownloads.firstOrNull()

    val hasPaused: Boolean
        get() = queue.any { it.isPausedQueueRow() }
}

private fun DownloadProgress.isPausedQueueRow(): Boolean = id > 0 && state == DownloadState.PAUSED

data class DownloadCompletionEvent(
    val gameId: Long,
    val rommId: Long,
    val localPath: String,
    val isDiscDownload: Boolean = false
)

@Singleton
class DownloadManager internal constructor(
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val platformDao: PlatformDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val soundManager: AppEventFeedback,
    private val m3uManager: M3uManager,
    private val thermalManager: dagger.Lazy<DownloadThermalManager>,
    private val mediaDownloadManager: dagger.Lazy<MediaDownloadManager>,
    private val musicDirectoryManager: MusicDirectoryManager,
    private val attributionRepository: StorageAttributionRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val homeTileRepository: com.nendo.argosy.data.repository.HomeTileRepository,
    private val homeTilePromptQueue: com.nendo.argosy.data.repository.HomeTilePromptQueue,
    private val customGridShapeStore: CustomGridShapeStore,
    private val extContentOrganizer: ExtContentOrganizer,
    private val romStagingManager: RomStagingManager,
    private val platformFolderResolver: PlatformFolderResolver,
    private val scope: CoroutineScope,
    private val connectivity: DownloadConnectivity = DownloadConnectivity.AlwaysAvailable
) {
    @Inject constructor(
        gameDao: GameDao,
        gameDiscDao: GameDiscDao,
        gameFileDao: GameFileDao,
        downloadQueueDao: DownloadQueueDao,
        platformDao: PlatformDao,
        romMRepository: RomMRepository,
        preferencesRepository: UserPreferencesRepository,
        soundManager: AppEventFeedback,
        m3uManager: M3uManager,
        thermalManager: dagger.Lazy<DownloadThermalManager>,
        mediaDownloadManager: dagger.Lazy<MediaDownloadManager>,
        musicDirectoryManager: MusicDirectoryManager,
        attributionRepository: StorageAttributionRepository,
        syncPreferencesRepository: SyncPreferencesRepository,
        homeTileRepository: com.nendo.argosy.data.repository.HomeTileRepository,
        homeTilePromptQueue: com.nendo.argosy.data.repository.HomeTilePromptQueue,
        customGridShapeStore: CustomGridShapeStore,
        extContentOrganizer: ExtContentOrganizer,
        romStagingManager: RomStagingManager,
        platformFolderResolver: PlatformFolderResolver,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ) : this(
        gameDao, gameDiscDao, gameFileDao, downloadQueueDao, platformDao, romMRepository,
        preferencesRepository, soundManager, m3uManager, thermalManager,
        mediaDownloadManager, musicDirectoryManager, attributionRepository, syncPreferencesRepository,
        homeTileRepository, homeTilePromptQueue, customGridShapeStore, extContentOrganizer,
        romStagingManager, platformFolderResolver,
        SafeCoroutineScope(Dispatchers.Main, TAG),
        AndroidDownloadConnectivity(context)
    )

    private val _state = MutableStateFlow(DownloadQueueState())
    val state: StateFlow<DownloadQueueState> = _state.asStateFlow()

    val activeDownloadCount: Int get() = _state.value.activeDownloads.size

    /**
     * How many downloads are running right now, across every queue.
     *
     * Rom and media downloads share one concurrency budget and one connection, so this is the
     * only number either of them may compare against it.
     */
    fun totalActiveDownloads(): Int =
        (_state.value.activeDownloads.map { it.id } + downloadJobs.keys).toSet().size +
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

    private fun notifySlotFreed() {
        scope.launch {
            processQueue()
            mediaDownloadManager.get().onDownloadSlotFreed()
        }
    }

    private val _completionEvents = MutableSharedFlow<DownloadCompletionEvent>()
    val completionEvents: SharedFlow<DownloadCompletionEvent> = _completionEvents.asSharedFlow()

    private val downloadJobs = java.util.concurrent.ConcurrentHashMap<Long, Job>()
    private val admissionMutex = Mutex()
    private val enqueueMutex = Mutex()
    private val stagingClaims = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private val stagingBlocked = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private val stagingLock = Any()

    init {
        scope.launch {
            enqueueMutex.withLock { restoreQueueFromDatabase() }
            romMRepository.connectionState.collect {
                if (it is ConnectionState.Connected) processQueue()
            }
        }
    }

    private suspend fun restoreQueueFromDatabase() {
        Log.d(TAG, "restoreQueueFromDatabase: starting")
        downloadQueueDao.clearCompleted()

        val pending = downloadQueueDao.getRecoverableDownloads()
        Log.d(TAG, "restoreQueueFromDatabase: found ${pending.size} pending downloads")
        pending.forEach { Log.d(TAG, "  - ${it.gameTitle}: state=${it.state}, bytes=${it.bytesDownloaded}/${it.totalBytes}") }

        sweepAbandonedStaging(pending.map { it.id }.toSet())

        if (pending.isEmpty()) {
            updateAvailableStorage()
            return
        }

        val statesToReset = setOf(
            DownloadState.DOWNLOADING.name,
            DownloadState.RETRYING.name,
            DownloadState.WAITING_FOR_NETWORK.name,
            DownloadState.EXTRACTING.name,
            DownloadState.MOVING.name
        )
        val restored = pending.map {
            val progress = reconcileRetained(it).toDownloadProgress()
            if (progress.state.name in statesToReset) {
                downloadQueueDao.updateState(progress.id, DownloadState.QUEUED.name)
                progress.copy(state = DownloadState.QUEUED)
            } else {
                progress
            }
        }
        Log.d(TAG, "restoreQueueFromDatabase: restored ${restored.size} items, states: ${restored.map { it.state }}")

        _state.value = DownloadQueueState(
            queue = restored.filter { it.state != DownloadState.FAILED },
            completed = restored.filter { it.state == DownloadState.FAILED },
            availableStorageBytes = getGlobalStorageBytes()
        )

        processQueue()
    }

    companion object {
        private const val TAG = "DownloadManager"
    }

    private suspend fun getDownloadDir(gameId: Long, platformSlug: String): File =
        platformFolderResolver.downloadDirForGame(gameId, platformSlug).also { it.mkdirs() }

    private suspend fun getAvailableStorageBytes(gameId: Long, platformSlug: String): Long {
        return withContext(Dispatchers.IO) {
            val downloadDir = getDownloadDir(gameId, platformSlug)
            romStagingManager.availableBytes(downloadDir) ?: 0L
        }
    }

    private sealed class StoragePlan {
        data class Staged(val destinationDir: File) : StoragePlan()
        data object Direct : StoragePlan()
        data object StagingBusy : StoragePlan()
        data class Insufficient(val requiredBytes: Long, val availableBytes: Long) : StoragePlan()
    }

    private suspend fun reconcileRetained(row: DownloadQueueEntity): DownloadQueueEntity =
        withContext(Dispatchers.IO) {
            val area = romStagingManager.list().firstOrNull { it.manifest.downloadId == row.id }
            val part = area?.let { File(it.archiveDir, "${row.fileName}.tmp") }
                ?: row.tempFilePath?.let(::File)
            val target = part?.parentFile?.let { File(it, row.fileName) }
            val bytes = when {
                part?.isFile == true -> part.length()
                target != null && VerifiedDownloadFiles.matches(target, row.transferCheckpoint()) -> target.length()
                else -> 0L
            }
            val reconciled = row.copy(tempFilePath = part?.absolutePath, bytesDownloaded = bytes)
            if (reconciled != row) downloadQueueDao.update(reconciled)
            reconciled
        }

    private fun acquireStaging(id: Long, blockOnFailure: Boolean = false): Boolean = synchronized(stagingLock) {
        if (!romStagingManager.claim(id)) {
            if (blockOnFailure) stagingBlocked.add(id)
            false
        } else {
            stagingClaims.add(id)
            true
        }
    }

    private fun releaseStaging(id: Long): Boolean {
        val released = synchronized(stagingLock) {
            if (!stagingClaims.remove(id)) return@synchronized false
            romStagingManager.release(id)
            stagingBlocked.clear()
            true
        }
        if (released) notifySlotFreed()
        return released
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
            val row = downloadQueueDao.getById(progress.id)?.let { reconcileRetained(it) }
            val area = romStagingManager.list().firstOrNull { it.manifest.downloadId == progress.id }
            val part = row?.tempFilePath?.let(::File)
            val retainedDirect = area == null && (part?.isFile == true || (row?.bytesDownloaded ?: 0) > 0)
            val destinationDir = area?.destinationDir ?: if (retainedDirect) part!!.parentFile!! else
                getDownloadDir(progress.gameId, progress.platformSlug)
            val destinationFree = romStagingManager.availableBytes(destinationDir)
            val total = row?.responseTotalBytes ?: progress.totalBytes
            val remainingArchive = (total - (row?.bytesDownloaded ?: 0)).coerceAtLeast(0L)
            val expands = !progress.isGameFileDownload &&
                ArchiveExpansion.expandsOnDisk(
                    progress.fileName, progress.platformSlug, progress.isMultiFileRom
                )
            val unpackedBytes = if (expands) {
                ArchiveExpansion.estimate(
                    total, progress.fileName, progress.platformSlug
                )
            } else {
                0L
            }

            if (area != null) {
                if (claimStaging && !acquireStaging(progress.id, blockOnFailure = true)) {
                    return@withContext StoragePlan.StagingBusy
                }
                if (area.manifest.phase == StagingPhase.MOVING || remainingArchive == 0L) {
                    return@withContext StoragePlan.Staged(destinationDir)
                }
                val internalRequired = remainingArchive + unpackedBytes + INTERNAL_STAGING_RESERVE_BYTES
                val internalFree = romStagingManager.availableBytes(area.archiveDir)
                val destinationRequired = unpackedBytes + STORAGE_BUFFER_BYTES
                return@withContext when {
                    internalFree != null && internalFree < internalRequired ->
                        StoragePlan.Insufficient(internalRequired, internalFree)
                    destinationFree != null && destinationFree < destinationRequired ->
                        StoragePlan.Insufficient(destinationRequired, destinationFree)
                    else -> StoragePlan.Staged(destinationDir)
                }
            }

            if (!retainedDirect && expands && preferencesRepository.userPreferences.first().stageDownloadsInternally) {
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
        if (claimStaging && !acquireStaging(progress.id)) return null
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
        if (claimStaging) {
            releaseStaging(progress.id)
        }
        return null
    }

    private suspend fun getGlobalStorageBytes(): Long {
        return withContext(Dispatchers.IO) {
            val dir = platformFolderResolver.storageBase().also { it.mkdirs() }
            romStagingManager.availableBytes(dir) ?: 0L
        }
    }

    private suspend fun updateAvailableStorage() {
        val available = getGlobalStorageBytes()
        _state.update { it.copy(availableStorageBytes = available) }
    }

    private suspend fun isInstantDownload(expectedSizeBytes: Long): Boolean {
        if (expectedSizeBytes <= 0) return false
        val thresholdMb = preferencesRepository.userPreferences.first().instantDownloadThresholdMb
        val thresholdBytes = thresholdMb * 1024L * 1024L
        return expectedSizeBytes <= thresholdBytes
    }

    private suspend fun startDownloadJob(
        progress: DownloadProgress,
        onResult: (DownloadResult) -> Unit = {}
    ): Job {
        downloadJobs[progress.id]?.let { return it }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (downloadQueueDao.getById(progress.id) == null) {
                    removeProgress(progress.id)
                    return@launch
                }
                val plan = planStorage(progress, claimStaging = true)
                if (plan is StoragePlan.StagingBusy) {
                    _state.update { current -> current.copy(
                        activeDownloads = current.activeDownloads.filter { it.id != progress.id },
                        queue = (current.queue.filter { it.id != progress.id } + progress.copy(state = DownloadState.QUEUED))
                            .sortedBy { it.id }
                    ) }
                } else if (plan is StoragePlan.Insufficient) {
                    parkForStorage(progress, plan)
                } else {
                    soundManager.play(SoundType.DOWNLOAD_START)
                    downloadQueueDao.updateState(progress.id, DownloadState.DOWNLOADING.name)
                    val result = downloadRom(progress, plan)
                    settleDownload(progress, result)
                    onResult(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val result = DownloadResult.Failure(DownloadFailureReason.Unexpected(e.message))
                settleDownload(progress, result)
                onResult(result)
            } finally {
                if (downloadJobs[progress.id] === coroutineContext[Job]) {
                    downloadJobs.remove(progress.id)
                    if (!releaseStaging(progress.id)) notifySlotFreed()
                }
            }
        }
        downloadJobs[progress.id] = job
        _state.update { current ->
            current.copy(
                activeDownloads = current.activeDownloads +
                    progress.copy(state = DownloadState.DOWNLOADING, isAwaitingServer = true),
                queue = current.queue.filter { it.id != progress.id }
            )
        }
        job.start()
        return job
    }

    private suspend fun parkForStorage(progress: DownloadProgress, plan: StoragePlan.Insufficient) {
        val availableStorage = getAvailableStorageBytes(progress.gameId, progress.platformSlug)
        downloadQueueDao.updateState(progress.id, DownloadState.WAITING_FOR_STORAGE.name)
        _state.update { current ->
            current.copy(
                activeDownloads = current.activeDownloads.filter { it.id != progress.id },
                queue = current.queue.filter { it.id != progress.id } + progress.copy(
                    state = DownloadState.WAITING_FOR_STORAGE,
                    requiredStorageBytes = plan.requiredBytes
                ),
                availableStorageBytes = availableStorage
            )
        }
    }

    private suspend fun settleDownload(progress: DownloadProgress, result: DownloadResult) {
        if (downloadQueueDao.getById(progress.id) == null) {
            removeProgress(progress.id)
            return
        }
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

        when (result) {
            is DownloadResult.Cancelled -> return
            is DownloadResult.WaitingForStorage -> {
                val availableStorage = getAvailableStorageBytes(progress.gameId, progress.platformSlug)
                _state.update { current ->
                    current.copy(
                        activeDownloads = current.activeDownloads.filter { it.id != progress.id },
                        queue = current.queue.filter { it.id != progress.id } + finalProgress,
                        availableStorageBytes = availableStorage
                    )
                }
            }
            else -> {
                _state.update { current ->
                    current.copy(
                        activeDownloads = current.activeDownloads.filter { it.id != progress.id },
                        completed = current.completed + finalProgress
                    )
                }
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
            com.nendo.argosy.domain.model.HomeTileAutoAdd.AUTO -> homeTileRepository.append(
                ownerUserId = syncPreferencesRepository.getRommUserId(),
                target = com.nendo.argosy.domain.model.HomeTileTargetRef.Game(gameId),
                layout = customGridShapeStore.layoutFor(customGrid)
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
        selectedFileIds: List<Long>? = null,
        startNow: Boolean = false
    ) {
        val effectiveMultiFile = isMultiFileRom || (selectedFileIds?.size ?: 0) > 1
        enqueueMutex.withLock {
            if ((_state.value.activeDownloads + _state.value.queue).any { it.gameId == gameId }) return@withLock
            val initialState = initialQueueState(startNow)
            val platformDir = platformFolderResolver.downloadDirForGame(gameId, platformSlug)
            val diskFileName = FileNames.sanitize(fileName)
            val tempFilePath = newTempFile(platformDir).absolutePath

            val entity = DownloadQueueEntity(
                gameId = gameId,
                rommId = rommId,
                fileName = diskFileName,
                gameTitle = gameTitle,
                platformSlug = platformSlug,
                coverPath = coverPath,
                bytesDownloaded = 0,
                totalBytes = expectedSizeBytes,
                state = initialState.name,
                errorReason = null,
                tempFilePath = tempFilePath,
                createdAt = Instant.now(),
                isMultiFileRom = effectiveMultiFile,
                selectedFileIds = selectedFileIds?.joinToString(","),
                ownerUserId = syncPreferencesRepository.getRommUserId()
            )

            if (reuseFailedDownload(entity)) return@withLock
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
                state = initialState,
                isMultiFileRom = effectiveMultiFile,
                selectedFileIds = selectedFileIds
            )

            admitEnqueued(progress, expectedSizeBytes)
        }
    }

    private suspend fun reuseFailedDownload(request: DownloadQueueEntity): Boolean {
        val row = downloadQueueDao.getRecoverableDownloads().firstOrNull {
            it.state == DownloadState.FAILED.name && it.gameId == request.gameId &&
                it.discId == request.discId && it.gameFileId == request.gameFileId &&
                it.ownerUserId == request.ownerUserId
        } ?: return false
        return admissionMutex.withLock {
            downloadJobs[row.id]?.cancelAndJoin()
            val currentRow = downloadQueueDao.getById(row.id) ?: run {
                removeProgress(row.id)
                return@withLock false
            }
            if (currentRow.state != DownloadState.FAILED.name) return@withLock false
            if (currentRow.rommId != request.rommId || currentRow.fileName != request.fileName ||
                currentRow.platformSlug != request.platformSlug ||
                currentRow.isMultiFileRom != request.isMultiFileRom ||
                currentRow.selectedFileIds != request.selectedFileIds
            ) {
                withContext(Dispatchers.IO) { currentRow.tempFilePath?.let(::File)?.delete() }
                discardStagingFor(row.id)
                downloadQueueDao.deleteById(row.id)
                removeProgress(row.id)
                return@withLock false
            }
            val retained = reconcileRetained(currentRow)
            downloadQueueDao.updateState(row.id, request.state)
            val progress = retained.toDownloadProgress().copy(
                state = DownloadState.valueOf(request.state), errorReason = null
            )
            _state.update { current ->
                current.copy(
                    activeDownloads = current.activeDownloads.filter { it.id != row.id },
                    completed = current.completed.filter { it.id != row.id },
                    queue = (current.queue.filter { it.id != row.id } + progress).sortedBy { it.id }
                )
            }
            if (progress.state == DownloadState.QUEUED) scope.launch { processQueue() }
            true
        }
    }

    private fun newTempFile(directory: File): File =
        File(directory, "${UUID.randomUUID()}.tmp")

    private suspend fun initialQueueState(startNow: Boolean): DownloadState =
        if (startNow || preferencesRepository.userPreferences.first().autoStartDownloads) {
            DownloadState.QUEUED
        } else {
            DownloadState.PAUSED
        }

    private suspend fun admitEnqueued(progress: DownloadProgress, expectedSizeBytes: Long) {
        when {
            progress.state == DownloadState.PAUSED -> _state.update { it.copy(queue = it.queue + progress) }
            isInstantDownload(expectedSizeBytes) -> {
                admissionMutex.withLock {
                    if (romMRepository.isConnected() && hasFreeDownloadSlot()) {
                        startDownloadJob(progress)
                    } else {
                        _state.update { it.copy(queue = it.queue + progress) }
                    }
                }
                processQueue()
            }
            else -> {
                _state.update { it.copy(queue = it.queue + progress) }
                processQueue()
            }
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
        expectedSizeBytes: Long = 0,
        startNow: Boolean = false
    ) {
        enqueueMutex.withLock {
            if ((_state.value.activeDownloads + _state.value.queue).any { it.discId == discId }) return@withLock
            val initialState = initialQueueState(startNow)

            val platformDir = platformFolderResolver.downloadDirForGame(gameId, platformSlug)
            val diskFileName = FileNames.sanitize(fileName)
            val tempFilePath = newTempFile(platformDir).absolutePath

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
                state = initialState.name,
                errorReason = null,
                tempFilePath = tempFilePath,
                createdAt = Instant.now(),
                ownerUserId = syncPreferencesRepository.getRommUserId()
            )

            if (reuseFailedDownload(entity)) return@withLock
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
                state = initialState
            )

            admitEnqueued(progress, expectedSizeBytes)
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
        gameFolderName: String? = null,
        startNow: Boolean = false
    ) {
        enqueueMutex.withLock {
            if ((_state.value.activeDownloads + _state.value.queue).any { it.gameFileId == gameFileId }) return@withLock
            val initialState = initialQueueState(startNow)

            val (gameFolder, categoryFolder) = withContext(Dispatchers.IO) {
                val folder = resolveAddonFolder(
                    gameId, platformSlug, gameFolderName, gameTitle, category
                )
                folder to resolveGameFileDir(gameId, gameFileId, platformSlug, category, folder)
            }
            val diskFileName = FileNames.sanitize(fileName)
            val tempFilePath = newTempFile(categoryFolder).absolutePath
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
                state = initialState.name,
                errorReason = null,
                tempFilePath = tempFilePath,
                createdAt = Instant.now(),
                ownerUserId = syncPreferencesRepository.getRommUserId()
            )

            if (reuseFailedDownload(entity)) return@withLock
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
                state = initialState
            )

            admitEnqueued(progress, expectedSizeBytes)
        }
    }

    private suspend fun romFileFolderName(gameId: Long): String? {
        if (!preferencesRepository.userPreferences.first().folderNameFromRom) return null
        val game = gameDao.getById(gameId) ?: return null
        val baseFileName = gameFileDao.getFilesForGame(gameId)
            .firstOrNull { it.isLaunchTarget && it.fileName.contains('.') }
            ?.fileName
            ?.takeIf { it.isNotBlank() }
        val fromServer = game.rommFileName?.takeIf { it.isNotBlank() }
        val fromDisk = game.localPath
            ?.let { File(it) }
            ?.takeIf { it.isFile }
            ?.name
        return baseFileName ?: fromServer ?: fromDisk
    }

    private suspend fun gameFolderNameFor(
        gameId: Long,
        platformDir: File,
        gameFolderName: String?,
        gameTitle: String
    ): String {
        val candidates = listOfNotNull(romFileFolderName(gameId), gameFolderName, gameTitle)
            .map(::sanitizeFolderName)
            .filter { it.isNotBlank() }
        if (candidates.isEmpty()) return sanitizeFolderName(gameTitle)
        return resolveGameFolder(platformDir, candidates)
            .name
            .removeSuffix(".m3u")
    }

    private suspend fun getGameFolder(
        gameId: Long, platformSlug: String, vararg names: String
    ): File {
        val platformDir = getDownloadDir(gameId, platformSlug)
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

    private fun sanitizeFolderName(name: String): String = FileNames.gameFolderName(name)

    private suspend fun separateFromOtherOwner(
        downloadDir: File,
        platformDir: File,
        unstaged: Boolean,
        progress: DownloadProgress
    ): File {
        if (!unstaged || downloadDir.absolutePath != platformDir.absolutePath) return downloadDir
        val shared = File(downloadDir, progress.fileName)
        if (!shared.exists()) return downloadDir
        val owner = gameDao.getByPath(shared.absolutePath) ?: return downloadDir
        if (owner.id == progress.gameId) return downloadDir
        val own = File(platformDir, sanitizeFolderName(progress.gameTitle)).also { it.mkdirs() }
        Log.w(TAG, "${shared.name} belongs to game ${owner.id}; downloading game ${progress.gameId} into ${own.name}")
        return own
    }

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
        gameDao.updateLocalPath(progress.gameId, finalPath, game.source, FileOrigin.ROMM_DOWNLOAD)
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
        val siblings = gameFileDao.getFilesForGame(gameId).map { it.filePath }
        val rommFileName = gameDao.getById(gameId)?.rommFileName
        val romFolderNames = listOfNotNull(rommFileName, rommFileName?.substringBeforeLast('.'))
        return ServerRomLayout.relativeDir(row.filePath, siblings, romFolderNames)
    }

    private suspend fun resolveAddonFolder(
        gameId: Long,
        platformSlug: String,
        gameFolderName: String?,
        gameTitle: String,
        category: String? = null
    ): File {
        val platformDir = getDownloadDir(gameId, platformSlug)
        val romFolderName = romFileFolderName(gameId)
        if (extContentOrganizer.usesCombinedLayout(gameId)) {
            return if (isFlatUnderCombine(category)) {
                platformDir
            } else {
                getGameFolder(
                    gameId,
                    platformSlug,
                    *listOfNotNull(romFolderName, gameTitle, gameFolderName).toTypedArray()
                )
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
        val gameFolder = getGameFolder(
            gameId,
            platformSlug,
            *listOfNotNull(romFolderName, gameTitle, gameFolderName).toTypedArray()
        )
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
                gameDao.updateLocalPath(gameId, moved.absolutePath, game.source, game.fileOrigin)
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
        admissionMutex.withLock {
            if (!romMRepository.isConnected()) return
            val maxConcurrent = preferencesRepository.userPreferences.first().maxConcurrentDownloads
            val slotsAvailable = (maxConcurrent - totalActiveDownloads()).coerceAtLeast(0)
            val nextItems = _state.value.queue
                .filter { it.state == DownloadState.QUEUED && it.id !in stagingBlocked && !downloadJobs.containsKey(it.id) }
                .take(slotsAvailable)
            for (next in nextItems) {
                startDownloadJob(next)
            }
        }
    }

    private suspend fun downloadRom(
        progress: DownloadProgress,
        plan: StoragePlan = StoragePlan.Direct
    ): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val platformDir = getDownloadDir(progress.gameId, progress.platformSlug)

                val carriedOver = romStagingManager.list()
                    .firstOrNull { it.manifest.downloadId == progress.id }
                if (carriedOver != null && progress.id !in stagingClaims) {
                    return@withContext DownloadResult.Failure(DownloadFailureReason.Unexpected(null))
                }
                if (carriedOver?.manifest?.phase == StagingPhase.MOVING) {
                    return@withContext resumeStagedDeploy(carriedOver, progress)
                }
                val row = downloadQueueDao.getById(progress.id)
                    ?: return@withContext DownloadResult.Cancelled
                val persistedPart = row.tempFilePath?.let(::File)
                val stagingArea = carriedOver ?: (plan as? StoragePlan.Staged)
                    ?.takeIf { persistedPart?.exists() != true }?.let { staged ->
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

                val fileDir = separateFromOtherOwner(downloadDir, platformDir, stagingArea == null, progress)
                val tempFile = stagingArea?.let { File(it.archiveDir, "${progress.fileName}.tmp") }
                    ?: persistedPart?.let {
                        if (row.requestKey != null || fileDir == downloadDir) it else File(fileDir, it.name)
                    }
                    ?: newTempFile(fileDir)
                val targetFile = File(tempFile.parentFile, progress.fileName)
                tempFile.parentFile?.mkdirs()
                val requestKey = DownloadTransferRunner(connectivity).resolveRequestKey(
                    row.retryAttempt, row.nextRetryAt,
                    resolve = {
                        romMRepository.downloadRequestKey(
                            progress.rommId, progress.fileName,
                            fileIds = progress.selectedFileIds?.joinToString(","),
                            isGameFile = progress.isGameFileDownload
                        )
                    },
                    persistRetry = { attempt, at -> downloadQueueDao.updateRetry(progress.id, attempt, at) },
                    onState = { updateTransferState(progress, it) }
                )
                val checkpoint = row.transferCheckpoint()
                if (row.requestKey != null && row.requestKey != requestKey) {
                    return@withContext DownloadResult.Failure(DownloadFailureReason.Unexpected(null))
                }
                downloadQueueDao.updateTransferPath(progress.id, tempFile.absolutePath, requestKey)
                if (VerifiedDownloadFiles.matches(targetFile, checkpoint) && !tempFile.exists()) {
                    coroutineContext.ensureActive()
                    if (targetFile.length() < MIN_ROM_SIZE_BYTES && !isSmallCompanionFile(progress.fileName)) {
                        return@withContext DownloadResult.Failure(DownloadFailureReason.FileTooSmall)
                    }
                    return@withContext finalizeCompletedFile(
                        targetFile, platformDir, progress.copy(totalBytes = targetFile.length()), stagingArea
                    )
                }
                val result = if (VerifiedDownloadFiles.matches(tempFile, checkpoint)) {
                    TransferResult.Verified(tempFile.length())
                } else {
                    val retryRow = downloadQueueDao.getById(progress.id)
                        ?: return@withContext DownloadResult.Cancelled
                    transferUntilComplete(progress, tempFile, checkpoint, retryRow.retryAttempt, retryRow.nextRetryAt, requestKey)
                }
                if (result !is TransferResult.Verified) {
                    val failure = result as TransferResult.Permanent
                    Logger.warn(TAG, "Transfer rejected | id=${progress.id} reason=${failure.message}")
                    return@withContext DownloadResult.Failure(failure.reason)
                }
                if (result.bytes < MIN_ROM_SIZE_BYTES && !isSmallCompanionFile(progress.fileName)) {
                    return@withContext DownloadResult.Failure(DownloadFailureReason.FileTooSmall)
                }
                val verifiedRow = downloadQueueDao.getById(progress.id)
                    ?: return@withContext DownloadResult.Cancelled
                coroutineContext.ensureActive()
                VerifiedDownloadFiles.promote(tempFile, targetFile, verifiedRow.transferCheckpoint())
                finalizeCompletedFile(targetFile, platformDir, progress.copy(totalBytes = result.bytes), stagingArea)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(
                    TAG,
                    "Download error | game=${progress.gameTitle} id=${progress.rommId} file=${progress.fileName}",
                    e
                )
                DownloadResult.Failure(DownloadFailureReason.Unexpected(e.message))
            }
        }

    private suspend fun updateTransferState(progress: DownloadProgress, state: DownloadState) {
        downloadQueueDao.updateState(progress.id, state.name)
        val current = _state.value.activeDownloads.firstOrNull { it.id == progress.id } ?: progress
        updateProgress(current.copy(
            state = state,
            bytesPerSecond = 0,
            isAwaitingServer = state == DownloadState.DOWNLOADING
        ))
    }

    private suspend fun transferUntilComplete(
        progress: DownloadProgress,
        tempFile: File,
        checkpoint: TransferCheckpoint,
        retryAttempt: Int,
        nextRetryAt: Long?,
        requestKey: String
    ): TransferResult {
        var lastUiUpdate = 0L
        var lastDbUpdate = 0L
        var lastBytes = tempFile.length()
        return DownloadTransferRunner(connectivity).run(
            tempFile, checkpoint, retryAttempt, nextRetryAt,
            newCall = { range, ifRange ->
                val call = romMRepository.newDownloadCall(
                    progress.rommId, progress.fileName, range,
                    fileIds = progress.selectedFileIds?.joinToString(","),
                    ifRange = ifRange, isGameFile = progress.isGameFileDownload
                )
                val actual = call.request().url.newBuilder().username("").password("").fragment(null).build().toString()
                if (actual != requestKey) {
                    call.cancel()
                    throw DownloadResourceChanged()
                }
                call
            },
            persist = {
                downloadQueueDao.updateTransferCheckpoint(
                    progress.id, it.validator, it.totalBytes, it.validatedBytes, it.representationChecksum
                )
            },
            persistRetry = { attempt, at -> downloadQueueDao.updateRetry(progress.id, attempt, at) },
            onState = { updateTransferState(progress, it) },
            onProgress = { bytes, total ->
                val now = System.currentTimeMillis()
                if (now - lastUiUpdate >= UI_UPDATE_INTERVAL_MS) {
                    val speed = if (lastUiUpdate == 0L) 0 else
                        ((bytes - lastBytes).coerceAtLeast(0) * 1000 / (now - lastUiUpdate))
                    updateProgress(progress.copy(
                        state = DownloadState.DOWNLOADING, bytesDownloaded = bytes,
                        totalBytes = total ?: 0, bytesPerSecond = speed, isAwaitingServer = false
                    ))
                    lastUiUpdate = now
                    lastBytes = bytes
                }
                if (now - lastDbUpdate >= DB_UPDATE_INTERVAL_MS) {
                    downloadQueueDao.updateProgress(progress.id, bytes)
                    lastDbUpdate = now
                }
            },
            beforeRead = {
                thermalManager.get().thermalStatus.first { it.state != ThermalState.PAUSED }
            },
            afterWrite = {
                val multiplier = thermalManager.get().thermalStatus.value.throttleMultiplier
                if (multiplier < 1.0f) delay((((1.0f / multiplier) - 1) * 100).toLong())
            },
            requireZip = progress.isMultiFileRom || progress.fileName.endsWith(".zip", ignoreCase = true),
            initialRange = progress.isMultiFileRom
        )
    }

    private class DownloadResourceChanged : IllegalStateException()

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
                gameId = progress.gameId,
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
                    extContentOrganizer.enforceCombinedLayout(
                        game, getDownloadDir(progress.gameId, progress.platformSlug)
                    )
                }
            } else {
                null
            }
            if (combined != null) {
                finalPath = combined.absolutePath
            } else {
                extContentOrganizer.consolidate(finalPath, getDownloadDir(progress.gameId, progress.platformSlug))
            }
        }

        when {
            progress.isGameFileDownload && progress.gameFileId != null -> {
                gameFileDao.updateLocalPath(progress.gameFileId, finalPath, Instant.now())
                maybeComputeRomHashPrefix(progress.gameFileId, progress.gameId, finalPath)
                val isDisc = gameFileDao.getById(progress.gameFileId)
                    ?.let { m3uManager.recordDiscFile(progress.gameId, it.fileName, finalPath) } == true
                if (!isDisc) claimLaunchTargetIfBase(progress, finalPath)
            }
            progress.isDiscDownload && progress.discId != null -> {
                gameDiscDao.updateLocalPath(progress.discId, finalPath)
                m3uManager.refreshPlaylist(progress.gameId)
            }
            else -> {
                gameDao.updateLocalPath(progress.gameId, finalPath, GameSource.ROMM_SYNCED, FileOrigin.ROMM_DOWNLOAD)
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

        return DownloadResult.Success(progress.totalBytes, finalPath)
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
        return null
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
        releaseStaging(progress.id)
        attributionRepository.markDirty(StorageCategory.ROM_STAGING)
        Logger.info(
            TAG,
            "Deployed staged game | game=${progress.gameTitle} bytes=$outputBytes " +
                "path=${finalFile.absolutePath}"
        )
        return StagedDeployResult.Success(finalFile.absolutePath)
    }

    private suspend fun resumeStagedDeploy(area: StagingArea, progress: DownloadProgress): DownloadResult {
        val platformDir = getDownloadDir(progress.gameId, progress.platformSlug)
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
        val live = downloadQueueDao.getRecoverableDownloads().map { it.id }.toSet() +
            _state.value.activeDownloads.map { it.id } +
            _state.value.queue.map { it.id }
        val freed = withContext(Dispatchers.IO) { romStagingManager.cleanAbandoned(live) }
        if (freed > 0) attributionRepository.markDirty(StorageCategory.ROM_STAGING)
        Logger.info(TAG, "Cleaned abandoned download staging | bytes=$freed")
        return freed
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

    private suspend fun processDownloadedFile(
        targetFile: File,
        platformDir: File,
        platformSlug: String,
        gameId: Long,
        gameTitle: String,
        gameFolderName: String? = null,
        progressId: Long = 0,
        isDiscDownload: Boolean = false,
        expectedSize: Long = 0,
        isMultiFileRom: Boolean = false,
        onExtractionProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ): String {
        val folderName = gameFolderNameFor(gameId, platformDir, gameFolderName, gameTitle)

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

                val comparableSize = if (isMultiFileRom) 0L else expectedSize
                val validationResult = ZipExtractor.validateArchive(targetFile, comparableSize)
                if (validationResult is ZipExtractor.ArchiveValidationResult.Invalid) {
                    Log.e(TAG, "ZIP validation failed: ${validationResult.reason}")
                    targetFile.delete()
                    throw java.io.IOException("${validationResult.reason}. Please try downloading again.")
                }

                val extracted = try {
                    ZipExtractor.extractFolderRom(
                        archiveFilePath = targetFile,
                        gameTitle = folderName,
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
                    gameTitle = folderName,
                    platformDir = platformDir
                )
                Log.d(TAG, "processDownloadedFile: organizedFile=${organizedFile.absolutePath}")
                organizedFile.absolutePath
            }
            isDiscDownload && M3uManager.supportsM3u(platformSlug) -> {
                Log.d(TAG, "processDownloadedFile: BRANCH=DISC_ORGANIZE")
                val result = organizeDiscFile(
                    targetFile,
                    listOfNotNull(folderName, gameFolderName, gameTitle),
                    platformDir
                )
                Log.d(TAG, "processDownloadedFile: discResult=$result")
                result
            }
            else -> {
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

        if (!ZipExtractor.isNswPlatform(platformSlug)) return resultPath

        return decompressNswContainers(File(resultPath), onExtractionProgress)
    }

    /**
     * Expands every compressed container the download produced, not only the launch target.
     *
     * A zip or a multi-file rom lands a whole game folder, and an emulator cannot open any of it
     * compressed. Decompressing the launch path alone converted the base game and left the
     * updates and DLC beside it as nsz. Scope is the game's own folder, which at download time
     * still holds its addons; the shared extcontent folder is a later, platform-level step.
     */
    private fun decompressNswContainers(
        launchFile: File,
        onExtractionProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ): String {
        val gameFolder = launchFile.parentFile ?: return launchFile.absolutePath
        val compressed = gameFolder.walkTopDown()
            .filter { it.isFile && NszDecompressor.isCompressedNsw(it) }
            .toList()
        if (compressed.isEmpty()) return launchFile.absolutePath

        Log.d(TAG, "processDownloadedFile: decompressing ${compressed.size} NSZ/XCZ container(s)")
        var launchResult = launchFile

        for (container in compressed) {
            val isLaunchTarget = container.absolutePath == launchFile.absolutePath
            try {
                val decompressed = NszDecompressor.decompress(
                    inputFile = container,
                    onProgress = if (isLaunchTarget) onExtractionProgress else null
                )
                if (isLaunchTarget) launchResult = decompressed
            } catch (e: Exception) {
                if (isLaunchTarget) throw e
                Log.w(TAG, "NSZ decompression failed for ${container.name}, leaving it compressed: ${e.message}")
            }
        }

        return launchResult.absolutePath
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
        _state.update { current ->
            current.copy(
                activeDownloads = current.activeDownloads.map {
                    if (it.id == progress.id) progress else it
                }
            )
        }
    }

    fun pauseDownload(rommId: Long) {
        val active = _state.value.activeDownloads.find { it.rommId == rommId }
        if (active != null) {
            stopDownload(active, remove = false)
        } else {
            val pausedIds = _state.value.queue
                .filter { it.rommId == rommId && it.state == DownloadState.QUEUED }
                .map { it.id }
                .toSet()
            pausedIds.forEach { id ->
                scope.launch { downloadQueueDao.updateState(id, DownloadState.PAUSED.name) }
            }
            _state.update { current ->
                current.copy(
                    queue = current.queue.map {
                        if (it.id in pausedIds) it.copy(state = DownloadState.PAUSED) else it
                    }
                )
            }
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

            _state.update { current ->
                current.copy(
                    queue = listOf(paused.copy(state = DownloadState.QUEUED)) +
                            current.queue.filter { it.id != paused.id }
                )
            }

            scope.launch { processQueue() }
        }
    }

    fun resumeAllPaused() {
        val snapshot = _state.value
        if (!snapshot.hasPaused) return
        val pausedIds = snapshot.queue
            .filter { it.isPausedQueueRow() }
            .map { it.id }
            .toSet()
        _state.update { current ->
            current.copy(
                queue = current.queue.map {
                    if (it.id in pausedIds) it.copy(state = DownloadState.QUEUED) else it
                }
            )
        }
        scope.launch {
            pausedIds.forEach { downloadQueueDao.updateState(it, DownloadState.QUEUED.name) }
            processQueue()
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
            val rows = downloadQueueDao.getRecoverableDownloads().associateBy { it.id }
            _state.update { current ->
                current.copy(queue = current.queue.map { item ->
                    if (item.state == DownloadState.WAITING_FOR_STORAGE &&
                        rows[item.id]?.state == DownloadState.QUEUED.name
                    ) item.copy(state = DownloadState.QUEUED) else item
                })
            }
            processQueue()
        } else {
            updateAvailableStorage()
        }
    }

    fun cancelDownload(rommId: Long) {
        soundManager.play(SoundType.DOWNLOAD_CANCEL)
        val item = (_state.value.activeDownloads + _state.value.queue).find { it.rommId == rommId }
            ?: return
        stopDownload(item, remove = true)
    }

    private fun stopDownload(item: DownloadProgress, remove: Boolean) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            enqueueMutex.withLock {
                admissionMutex.withLock {
                    downloadJobs[item.id]?.cancelAndJoin()
                    val row = downloadQueueDao.getById(item.id)
                    val bytes = withContext(Dispatchers.IO) {
                        row?.tempFilePath?.let(::File)?.length() ?: item.bytesDownloaded
                    }
                    if (remove) {
                        withContext(Dispatchers.IO) { row?.tempFilePath?.let(::File)?.delete() }
                        discardStagingFor(item.id)
                        downloadQueueDao.deleteById(item.id)
                    } else if (row != null) {
                        downloadQueueDao.updateState(item.id, DownloadState.PAUSED.name)
                        downloadQueueDao.updateProgress(item.id, bytes)
                    }
                    _state.update { current ->
                        current.copy(
                            activeDownloads = current.activeDownloads.filter { it.id != item.id },
                            completed = current.completed.filter { it.id != item.id },
                            queue = current.queue.filter { it.id != item.id } +
                                if (remove || row == null) emptyList() else listOf(row.toDownloadProgress().copy(
                                    state = DownloadState.PAUSED, bytesDownloaded = bytes
                                ))
                        )
                    }
                }
            }
            notifySlotFreed()
        }
    }

    fun clearCompleted() {
        clearFinishedIds(_state.value.completed.filter { it.state == DownloadState.COMPLETED }.map { it.id }, false)
    }

    fun clearFinished() {
        clearFinishedIds(_state.value.completed.map { it.id }, true)
    }

    fun removeFromCompleted(downloadId: Long) {
        if (_state.value.completed.none { it.id == downloadId }) return
        clearFinishedIds(listOf(downloadId), true)
    }

    private fun clearFinishedIds(ids: List<Long>, includeFailed: Boolean) {
        scope.launch {
            enqueueMutex.withLock {
                admissionMutex.withLock {
                    for (id in ids) {
                        val row = downloadQueueDao.getById(id)
                        if (row != null && row.state != DownloadState.COMPLETED.name &&
                            !(includeFailed && row.state == DownloadState.FAILED.name)
                        ) continue
                        downloadJobs[id]?.join()
                        val retained = downloadQueueDao.getById(id)
                        if (retained?.state != row?.state) continue
                        if (retained?.state == DownloadState.FAILED.name) {
                            try {
                                withContext(Dispatchers.IO) {
                                    retained.tempFilePath?.let { Files.deleteIfExists(File(it).toPath()) }
                                    val area = romStagingManager.list()
                                        .firstOrNull { it.manifest.downloadId == id }
                                    val stagingRoot = area?.root ?: File(romStagingManager.stagingRoot, id.toString())
                                    discardStagingFor(id)
                                    if (area == null) stagingRoot.deleteRecursively()
                                    if (!Files.notExists(stagingRoot.toPath())) {
                                        throw IOException("Staging cleanup incomplete for download $id")
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Logger.warn(TAG, "Failed download cleanup | id=$id reason=${e.message}")
                                continue
                            }
                        }
                        if (retained != null) downloadQueueDao.deleteById(id)
                        _state.update { current ->
                            current.copy(completed = current.completed.filter { it.id != id })
                        }
                    }
                }
            }
        }
    }

    private fun removeProgress(downloadId: Long) {
        _state.update { current ->
            current.copy(
                activeDownloads = current.activeDownloads.filter { it.id != downloadId },
                queue = current.queue.filter { it.id != downloadId },
                completed = current.completed.filter { it.id != downloadId }
            )
        }
    }

    fun retryDownload(downloadId: Long) {
        val item = _state.value.completed.find { it.id == downloadId } ?: return

        scope.launch {
            requeueFailed(item)
        }
    }

    internal suspend fun requeueFailed(item: DownloadProgress) {
        val reused = enqueueMutex.withLock {
            downloadQueueDao.getById(item.id)?.let {
                reuseFailedDownload(it.copy(state = DownloadState.QUEUED.name))
            } ?: false
        }
        if (reused) return
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
                expectedSizeBytes = item.totalBytes,
                startNow = true
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
                gameFolderName = item.gameFolderName,
                startNow = true
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
                selectedFileIds = item.selectedFileIds,
                startNow = true
            )
        }
    }

    suspend fun getDownloadPath(gameId: Long, platformSlug: String, fileName: String): File {
        val platformDir = getDownloadDir(gameId, platformSlug)
        return File(platformDir, fileName)
    }

    sealed class ExtractionResult {
        data class Success(val localPath: String) : ExtractionResult()
        data class Failure(val reason: DownloadFailureReason) : ExtractionResult()
    }

    suspend fun retryExtraction(gameId: Long): ExtractionResult = withContext(Dispatchers.IO) {
        val queueEntry = failedBaseRecoveryEntry(gameId)
            ?: return@withContext ExtractionResult.Failure(DownloadFailureReason.NoDownloadEntryFound)

        val isStaged = romStagingManager.list().any { it.manifest.downloadId == queueEntry.id } ||
            queueEntry.tempFilePath?.let { File(it).toPath().startsWith(romStagingManager.stagingRoot.toPath()) } == true
        if (!isStaged) downloadJobs[queueEntry.id]?.cancelAndJoin()
        val platformDir = getDownloadDir(queueEntry.gameId, queueEntry.platformSlug)
        val targetFile = File(queueEntry.tempFilePath?.let(::File)?.parentFile ?: platformDir, queueEntry.fileName)

        val verified = withContext(Dispatchers.IO) {
            VerifiedDownloadFiles.matches(targetFile, queueEntry.transferCheckpoint())
        }
        if (!verified) {
            return@withContext ExtractionResult.Failure(DownloadFailureReason.DownloadedFileNoLongerExists)
        }
        if (withContext(Dispatchers.IO) { targetFile.length() < MIN_ROM_SIZE_BYTES } &&
            !isSmallCompanionFile(queueEntry.fileName)
        ) {
            return@withContext ExtractionResult.Failure(DownloadFailureReason.FileTooSmall)
        }

        if (isStaged) {
            return@withContext withContext(scope.coroutineContext.minusKey(Job)) admission@{
                var result: DownloadResult? = null
                val job = admissionMutex.withLock {
                    val retained = downloadQueueDao.getById(queueEntry.id) ?: return@withLock null
                    if (downloadJobs.containsKey(retained.id) ||
                        retained.state !in setOf(
                            DownloadState.FAILED.name, DownloadState.PAUSED.name,
                            DownloadState.WAITING_FOR_STORAGE.name
                        ) || !hasFreeDownloadSlot()
                    ) return@withLock null
                    downloadQueueDao.updateState(retained.id, DownloadState.QUEUED.name)
                    val progress = retained.copy(state = DownloadState.QUEUED.name, errorReason = null)
                        .toDownloadProgress()
                    _state.update { current ->
                        current.copy(completed = current.completed.filter { it.id != retained.id })
                    }
                    startDownloadJob(progress) { result = it }
                }
                    ?: return@admission ExtractionResult.Failure(DownloadFailureReason.ExtractionFailed(null))
                job.join()
                if (job.isCancelled) throw CancellationException("Extraction retry cancelled")
                when (val completed = result) {
                    is DownloadResult.Success -> completed.localPath?.let { ExtractionResult.Success(it) }
                        ?: ExtractionResult.Failure(DownloadFailureReason.DownloadedFileMissing)
                    is DownloadResult.Failure -> ExtractionResult.Failure(completed.reason)
                    is DownloadResult.WaitingForStorage -> ExtractionResult.Failure(completed.reason)
                    else -> ExtractionResult.Failure(DownloadFailureReason.ExtractionFailed(null))
                }
            }
        }

        try {
            val discId = queueEntry.discId
            val isDiscDownload = discId != null
            val finalPath = processDownloadedFile(
                targetFile = targetFile,
                platformDir = platformDir,
                platformSlug = queueEntry.platformSlug,
                gameId = queueEntry.gameId,
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
                    m3uManager.refreshPlaylist(gameId)
                }
                queueEntry.gameFileId != null -> {
                    gameFileDao.updateLocalPath(queueEntry.gameFileId, finalPath, Instant.now())
                    gameFileDao.getById(queueEntry.gameFileId)
                        ?.let { m3uManager.recordDiscFile(gameId, it.fileName, finalPath) }
                }
                else -> {
                    gameDao.updateLocalPath(gameId, finalPath, GameSource.ROMM_SYNCED, FileOrigin.ROMM_DOWNLOAD)
                }
            }
            enqueueMutex.withLock {
                val currentRow = downloadQueueDao.getById(queueEntry.id)
                if (currentRow == null || (!downloadJobs.containsKey(queueEntry.id) &&
                    currentRow.state in setOf(DownloadState.FAILED.name, DownloadState.EXTRACTING.name))
                ) {
                    if (currentRow != null) downloadQueueDao.deleteById(queueEntry.id)
                    removeProgress(queueEntry.id)
                }
            }
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
        } catch (e: CancellationException) {
            throw e
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

    private suspend fun failedBaseRecoveryEntry(gameId: Long): DownloadQueueEntity? =
        downloadQueueDao.getRecoverableDownloads().filter {
            it.gameId == gameId && it.discId == null && it.gameFileId == null &&
                it.state == DownloadState.FAILED.name
        }.singleOrNull()

    suspend fun deleteFileAndRedownload(gameId: Long) = enqueueMutex.withLock {
        val queueEntry = failedBaseRecoveryEntry(gameId)
        if (queueEntry != null) {
            admissionMutex.withLock cleanup@{
                downloadJobs[queueEntry.id]?.cancelAndJoin()
                val retained = downloadQueueDao.getById(queueEntry.id)
                if (retained == null) {
                    removeProgress(queueEntry.id)
                    return@cleanup
                }
                val platformDir = getDownloadDir(retained.gameId, retained.platformSlug)
                val tempFile = retained.tempFilePath?.let(::File)
                    ?: File(platformDir, "${retained.fileName}.tmp")
                val targetFile = File(tempFile.parentFile, retained.fileName)

                withContext(Dispatchers.IO) {
                    if (targetFile.exists()) targetFile.delete()
                    if (tempFile.exists()) tempFile.delete()
                }

                discardStagingFor(queueEntry.id)
                downloadQueueDao.deleteById(queueEntry.id)
                removeProgress(queueEntry.id)
            }
            notifySlotFreed()
        }
    }

    private suspend fun discardStagingFor(downloadId: Long) {
        val discarded = withContext(Dispatchers.IO) {
            romStagingManager.list()
                .firstOrNull { it.manifest.downloadId == downloadId }
                ?.also {
                    romStagingManager.discard(it)
                    releaseStaging(downloadId)
                }
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
            totalBytes = responseTotalBytes ?: totalBytes,
            state = try {
                DownloadState.valueOf(state)
            } catch (e: Exception) {
                DownloadState.QUEUED
            },
            errorReason = DownloadFailureReasonCodec.decode(errorReason),
            isMultiFileRom = isMultiFileRom,
            selectedFileIds = selectedFileIds
                ?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
        )
    }

    private fun DownloadQueueEntity.transferCheckpoint() = TransferCheckpoint(
        resumeValidator, responseTotalBytes, validatedBytes, representationChecksum
    )
}
