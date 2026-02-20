package com.nendo.argosy.data.download

import android.util.Log
import android.content.Context
import android.os.StatFs
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.download.nsz.NszDecompressor
import com.nendo.argosy.data.emulator.EdenContentManager
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.M3uManager
import com.nendo.argosy.DualScreenManagerHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val STORAGE_BUFFER_BYTES = 50 * 1024 * 1024L
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
    val platformSlug: String,
    val coverPath: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: DownloadState,
    val errorReason: String? = null,
    val extractionBytesWritten: Long = 0,
    val extractionTotalBytes: Long = 0,
    val isMultiFileRom: Boolean = false,
    val bytesPerSecond: Long = 0
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
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

private sealed class DownloadResult {
    data class Success(val bytesWritten: Long) : DownloadResult()
    data class Failure(val reason: String) : DownloadResult()
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
    private val emulatorResolver: EmulatorResolver,
    private val edenContentManager: EdenContentManager
) {
    private val _state = MutableStateFlow(DownloadQueueState())
    val state: StateFlow<DownloadQueueState> = _state.asStateFlow()

    private val _completionEvents = MutableSharedFlow<DownloadCompletionEvent>()
    val completionEvents: SharedFlow<DownloadCompletionEvent> = _completionEvents.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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

        if (pending.isEmpty()) {
            updateAvailableStorage()
            return
        }

        // Reset active states to QUEUED so they can be reprocessed on restart
        val statesToReset = setOf(
            DownloadState.DOWNLOADING.name,
            DownloadState.EXTRACTING.name
        )
        for (entity in pending) {
            if (entity.state in statesToReset) {
                Log.d(TAG, "restoreQueueFromDatabase: resetting ${entity.gameTitle} from ${entity.state} to QUEUED")
                downloadQueueDao.updateState(entity.id, DownloadState.QUEUED.name)
            }
        }

        val restored = pending.map {
            val progress = it.toDownloadProgress()
            // Ensure restored items are in QUEUED state for processing
            if (progress.state == DownloadState.DOWNLOADING || progress.state == DownloadState.EXTRACTING) {
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
    }

    private suspend fun getDownloadDir(platformSlug: String): File {
        val platform = platformDao.getBySlug(platformSlug)
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
            try {
                val downloadDir = getDownloadDir(platformSlug)
                val stat = StatFs(downloadDir.absolutePath)
                stat.availableBytes
            } catch (_: Exception) {
                0L
            }
        }
    }

    private fun hasEnoughStorage(requiredBytes: Long, availableBytes: Long): Boolean {
        return availableBytes >= requiredBytes + STORAGE_BUFFER_BYTES
    }

    private suspend fun getGlobalStorageBytes(): Long {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = preferencesRepository.userPreferences.first()
                val dir = prefs.romStoragePath?.let { File(it) } ?: defaultDownloadDir
                val stat = StatFs(dir.absolutePath)
                stat.availableBytes
            } catch (_: Exception) {
                0L
            }
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
        val availableStorage = getAvailableStorageBytes(progress.platformSlug)
        val requiredBytes = progress.totalBytes - progress.bytesDownloaded

        if (!hasEnoughStorage(requiredBytes, availableStorage)) {
            downloadQueueDao.updateState(progress.id, DownloadState.WAITING_FOR_STORAGE.name)
            _state.value = _state.value.copy(
                activeDownloads = _state.value.activeDownloads.filter { it.id != progress.id },
                queue = _state.value.queue.map {
                    if (it.id == progress.id) it.copy(state = DownloadState.WAITING_FOR_STORAGE) else it
                } + if (_state.value.queue.none { it.id == progress.id }) {
                    listOf(progress.copy(state = DownloadState.WAITING_FOR_STORAGE))
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
            val result = downloadRom(progress)

            val finalProgress = when (result) {
                is DownloadResult.Success -> {
                    downloadQueueDao.updateState(progress.id, DownloadState.COMPLETED.name)
                    soundManager.play(SoundType.DOWNLOAD_COMPLETE)
                    progress.copy(
                        state = DownloadState.COMPLETED,
                        bytesDownloaded = result.bytesWritten
                    )
                }
                is DownloadResult.Failure -> {
                    downloadQueueDao.updateState(progress.id, DownloadState.FAILED.name, result.reason)
                    soundManager.play(SoundType.ERROR)
                    progress.copy(
                        state = DownloadState.FAILED,
                        errorReason = result.reason
                    )
                }
                is DownloadResult.Cancelled -> {
                    progress.copy(state = DownloadState.PAUSED)
                }
            }

            downloadJobs.remove(progress.id)

            if (result !is DownloadResult.Cancelled) {
                _state.value = _state.value.copy(
                    activeDownloads = _state.value.activeDownloads.filter { it.id != progress.id },
                    completed = _state.value.completed + finalProgress
                )
                processQueue()
                if (result is DownloadResult.Success) {
                    broadcastDownloadCompleted(progress.gameId)
                }
            }
        }
    }

    private fun broadcastDownloadCompleted(gameId: Long) {
        DualScreenManagerHolder.instance?.onDownloadCompleted(gameId)
    }

    suspend fun enqueueDownload(
        gameId: Long,
        rommId: Long,
        fileName: String,
        gameTitle: String,
        platformSlug: String,
        coverPath: String?,
        expectedSizeBytes: Long = 0,
        isMultiFileRom: Boolean = false
    ) {
        val currentState = _state.value
        if (currentState.activeDownloads.any { it.gameId == gameId }) return
        if (currentState.queue.any { it.gameId == gameId }) return

        val existing = downloadQueueDao.getByGameId(gameId)
        if (existing != null) return

        val platformDir = getDownloadDir(platformSlug)
        val tempFilePath = File(platformDir, "${fileName}.tmp").absolutePath

        val entity = DownloadQueueEntity(
            gameId = gameId,
            rommId = rommId,
            fileName = fileName,
            gameTitle = gameTitle,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED.name,
            errorReason = null,
            tempFilePath = tempFilePath,
            createdAt = Instant.now(),
            isMultiFileRom = isMultiFileRom
        )

        val id = downloadQueueDao.insert(entity)

        val progress = DownloadProgress(
            id = id,
            gameId = gameId,
            rommId = rommId,
            fileName = fileName,
            gameTitle = gameTitle,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED,
            isMultiFileRom = isMultiFileRom
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
        platformSlug: String,
        coverPath: String?,
        expectedSizeBytes: Long = 0
    ) {
        val currentState = _state.value
        if (currentState.activeDownloads.any { it.discId == discId }) return
        if (currentState.queue.any { it.discId == discId }) return

        val platformDir = getDownloadDir(platformSlug)
        val tempFilePath = File(platformDir, "${fileName}.tmp").absolutePath

        val entity = DownloadQueueEntity(
            gameId = gameId,
            rommId = rommId,
            discId = discId,
            discNumber = discNumber,
            fileName = fileName,
            gameTitle = gameTitle,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED.name,
            errorReason = null,
            tempFilePath = tempFilePath,
            createdAt = Instant.now()
        )

        val id = downloadQueueDao.insert(entity)

        val progress = DownloadProgress(
            id = id,
            gameId = gameId,
            rommId = rommId,
            discId = discId,
            discNumber = discNumber,
            fileName = fileName,
            gameTitle = gameTitle,
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
        expectedSizeBytes: Long = 0
    ) {
        val currentState = _state.value
        if (currentState.activeDownloads.any { it.gameFileId == gameFileId }) return
        if (currentState.queue.any { it.gameFileId == gameFileId }) return

        val platformDir = getDownloadDir(platformSlug)
        val gameFolder = getGameFolder(platformSlug, gameTitle)
        val categoryFolder = File(gameFolder, category).apply { mkdirs() }
        val tempFilePath = File(categoryFolder, "${fileName}.tmp").absolutePath

        val entity = DownloadQueueEntity(
            gameId = gameId,
            rommId = rommFileId,
            gameFileId = gameFileId,
            fileCategory = category,
            fileName = fileName,
            gameTitle = gameTitle,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED.name,
            errorReason = null,
            tempFilePath = tempFilePath,
            createdAt = Instant.now()
        )

        val id = downloadQueueDao.insert(entity)

        val progress = DownloadProgress(
            id = id,
            gameId = gameId,
            rommId = rommFileId,
            gameFileId = gameFileId,
            fileCategory = category,
            fileName = fileName,
            gameTitle = gameTitle,
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

    private suspend fun getGameFolder(platformSlug: String, gameTitle: String): File {
        val platformDir = getDownloadDir(platformSlug)
        val sanitizedTitle = gameTitle
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
        return File(platformDir, sanitizedTitle).apply { mkdirs() }
    }

    private suspend fun processQueue() {
        val maxConcurrent = preferencesRepository.userPreferences.first().maxConcurrentDownloads
        val currentActive = _state.value.activeDownloads.size

        Log.d(TAG, "processQueue: maxConcurrent=$maxConcurrent, currentActive=$currentActive")
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

            val availableStorage = getAvailableStorageBytes(next.platformSlug)
            val requiredBytes = next.totalBytes - next.bytesDownloaded
            Log.d(TAG, "processQueue: ${next.gameTitle} requires $requiredBytes bytes, available=$availableStorage")

            if (!hasEnoughStorage(requiredBytes, availableStorage)) {
                downloadQueueDao.updateState(next.id, DownloadState.WAITING_FOR_STORAGE.name)
                _state.value = _state.value.copy(
                    queue = _state.value.queue.map {
                        if (it.id == next.id) it.copy(state = DownloadState.WAITING_FOR_STORAGE) else it
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
                val result = downloadRom(next)

                val finalProgress = when (result) {
                    is DownloadResult.Success -> {
                        downloadQueueDao.updateState(next.id, DownloadState.COMPLETED.name)
                        soundManager.play(SoundType.DOWNLOAD_COMPLETE)
                        next.copy(
                            state = DownloadState.COMPLETED,
                            bytesDownloaded = result.bytesWritten
                        )
                    }
                    is DownloadResult.Failure -> {
                        downloadQueueDao.updateState(next.id, DownloadState.FAILED.name, result.reason)
                        soundManager.play(SoundType.ERROR)
                        next.copy(
                            state = DownloadState.FAILED,
                            errorReason = result.reason
                        )
                    }
                    is DownloadResult.Cancelled -> {
                        next.copy(state = DownloadState.PAUSED)
                    }
                }

                downloadJobs.remove(next.id)

                if (result !is DownloadResult.Cancelled) {
                    _state.value = _state.value.copy(
                        activeDownloads = _state.value.activeDownloads.filter { it.id != next.id },
                        completed = _state.value.completed + finalProgress
                    )
                    processQueue()
                    if (result is DownloadResult.Success) {
                        broadcastDownloadCompleted(next.gameId)
                    }
                }
            }
        }
    }

    private suspend fun downloadRom(progress: DownloadProgress): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val platformDir = getDownloadDir(progress.platformSlug)

                // Game file downloads (DLC/updates) go to category subfolders
                val downloadDir = if (progress.isGameFileDownload && progress.fileCategory != null) {
                    val gameFolder = getGameFolder(progress.platformSlug, progress.gameTitle)
                    File(gameFolder, progress.fileCategory).apply { mkdirs() }
                } else {
                    platformDir
                }

                val tempFile = File(downloadDir, "${progress.fileName}.tmp")
                val targetFile = File(downloadDir, progress.fileName)

                // Check if download is already complete (e.g., app crashed during extraction)
                if (targetFile.exists() && targetFile.length() >= progress.totalBytes && progress.totalBytes > 0) {
                    // Game file downloads (DLC/updates) don't need extraction or organization
                    val finalPath = if (progress.isGameFileDownload) {
                        targetFile.absolutePath
                    } else {
                        processDownloadedFile(
                            targetFile = targetFile,
                            platformDir = platformDir,
                            platformSlug = progress.platformSlug,
                            gameTitle = progress.gameTitle,
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

                    Log.d(TAG, "processDownloadedFile returned: $finalPath")
                    Log.d(TAG, "  targetFile was: ${targetFile.absolutePath}")
                    Log.d(TAG, "  gameTitle: ${progress.gameTitle}")

                    when {
                        progress.isGameFileDownload && progress.gameFileId != null -> {
                            Log.d(TAG, "Storing localPath to DB (gameFile): gameFileId=${progress.gameFileId}, path=$finalPath")
                            gameFileDao.updateLocalPath(
                                progress.gameFileId,
                                finalPath,
                                Instant.now()
                            )
                            autoApplyToEdenIfNeeded(progress, finalPath)
                        }
                        progress.isDiscDownload && progress.discId != null -> {
                            Log.d(TAG, "Storing localPath to DB (disc): discId=${progress.discId}, path=$finalPath")
                            gameDiscDao.updateLocalPath(progress.discId, finalPath)
                            m3uManager.generateM3uIfComplete(progress.gameId)
                        }
                        else -> {
                            Log.d(TAG, "Storing localPath to DB (game): gameId=${progress.gameId}, path=$finalPath")
                            gameDao.updateLocalPath(
                                progress.gameId,
                                finalPath,
                                GameSource.ROMM_SYNCED
                            )
                        }
                    }
                    return@withContext DownloadResult.Success(progress.totalBytes)
                }

                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                val rangeHeader = if (existingBytes > 0) "bytes=$existingBytes-" else null

                when (val result = romMRepository.downloadRom(progress.rommId, progress.fileName, rangeHeader)) {
                    is RomMResult.Success -> {
                        val response = result.data
                        val body = response.body
                        val contentType = body.contentType()?.toString() ?: ""
                        val contentLength = body.contentLength()

                        if (INVALID_CONTENT_TYPES.any { contentType.startsWith(it) }) {
                            return@withContext DownloadResult.Failure("Invalid file type: $contentType")
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
                            return@withContext DownloadResult.Failure("File too small to be a ROM")
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

                                while (true) {
                                    coroutineContext.ensureActive()

                                    val thermalStatus = thermalManager.get().thermalStatus.value
                                    if (thermalStatus.state == ThermalState.PAUSED) {
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

                                if (!tempFile.renameTo(targetFile)) {
                                    tempFile.copyTo(targetFile, overwrite = true)
                                    tempFile.delete()
                                }

                                // Game file downloads (DLC/updates) don't need extraction or organization
                                val finalPath = if (progress.isGameFileDownload) {
                                    targetFile.absolutePath
                                } else {
                                    processDownloadedFile(
                                        targetFile = targetFile,
                                        platformDir = platformDir,
                                        platformSlug = progress.platformSlug,
                                        gameTitle = progress.gameTitle,
                                        progressId = progress.id,
                                        isDiscDownload = progress.isDiscDownload,
                                        expectedSize = totalSize,
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

                                Log.d(TAG, "processDownloadedFile returned: $finalPath")
                                Log.d(TAG, "  targetFile was: ${targetFile.absolutePath}")
                                Log.d(TAG, "  gameTitle: ${progress.gameTitle}")

                                when {
                                    progress.isGameFileDownload && progress.gameFileId != null -> {
                                        Log.d(TAG, "Storing localPath to DB (gameFile): gameFileId=${progress.gameFileId}, path=$finalPath")
                                        gameFileDao.updateLocalPath(
                                            progress.gameFileId,
                                            finalPath,
                                            Instant.now()
                                        )
                                        autoApplyToEdenIfNeeded(progress, finalPath)
                                    }
                                    progress.isDiscDownload && progress.discId != null -> {
                                        Log.d(TAG, "Storing localPath to DB (disc): discId=${progress.discId}, path=$finalPath")
                                        gameDiscDao.updateLocalPath(progress.discId, finalPath)
                                        m3uManager.generateM3uIfComplete(progress.gameId)
                                    }
                                    else -> {
                                        Log.d(TAG, "Storing localPath to DB (game): gameId=${progress.gameId}, path=$finalPath")
                                        gameDao.updateLocalPath(
                                            progress.gameId,
                                            finalPath,
                                            GameSource.ROMM_SYNCED
                                        )
                                    }
                                }

                                _completionEvents.emit(
                                    DownloadCompletionEvent(
                                        gameId = progress.gameId,
                                        rommId = progress.rommId,
                                        localPath = finalPath,
                                        isDiscDownload = progress.isDiscDownload
                                    )
                                )

                                DownloadResult.Success(bytesRead)
                            }
                        }
                    }
                    is RomMResult.Error -> {
                        DownloadResult.Failure(result.message)
                    }
                }
            } catch (_: CancellationException) {
                DownloadResult.Cancelled
            } catch (e: Exception) {
                DownloadResult.Failure(e.message ?: "Unknown error")
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
                val result = organizeDiscFile(targetFile, gameTitle, platformDir)
                Log.d(TAG, "processDownloadedFile: discResult=$result")
                result
            }
            else -> {
                Log.d(TAG, "processDownloadedFile: BRANCH=PASSTHROUGH (no processing)")
                targetFile.absolutePath
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

    private fun organizeDiscFile(romFile: File, gameTitle: String, platformDir: File): String {
        val sanitizedTitle = gameTitle
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
        val gameFolder = File(platformDir, sanitizedTitle).apply { mkdirs() }
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

        var anyResumed = false
        for (item in waiting) {
            val availableStorage = getAvailableStorageBytes(item.platformSlug)
            val requiredBytes = item.totalBytes - item.bytesDownloaded
            if (hasEnoughStorage(requiredBytes, availableStorage)) {
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
            }
            _state.value = _state.value.copy(
                activeDownloads = _state.value.activeDownloads.filter { it.id != active.id }
            )
            scope.launch { processQueue() }
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
                    expectedSizeBytes = item.totalBytes
                )
                else -> enqueueDownload(
                    gameId = item.gameId,
                    rommId = item.rommId,
                    fileName = item.fileName,
                    gameTitle = item.gameTitle,
                    platformSlug = item.platformSlug,
                    coverPath = item.coverPath,
                    expectedSizeBytes = item.totalBytes,
                    isMultiFileRom = item.isMultiFileRom
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
        data class Failure(val reason: String) : ExtractionResult()
    }

    suspend fun retryExtraction(gameId: Long): ExtractionResult {
        val queueEntry = downloadQueueDao.getByGameId(gameId)
            ?: return ExtractionResult.Failure("No download entry found")

        val platformDir = getDownloadDir(queueEntry.platformSlug)
        val targetFile = File(platformDir, queueEntry.fileName)

        if (!targetFile.exists()) {
            return ExtractionResult.Failure("Downloaded file no longer exists")
        }

        return try {
            val discId = queueEntry.discId
            val isDiscDownload = discId != null
            val finalPath = processDownloadedFile(
                targetFile = targetFile,
                platformDir = platformDir,
                platformSlug = queueEntry.platformSlug,
                gameTitle = queueEntry.gameTitle,
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

            if (discId != null) {
                gameDiscDao.updateLocalPath(discId, finalPath)
                m3uManager.generateM3uIfComplete(gameId)
            } else {
                gameDao.updateLocalPath(gameId, finalPath, GameSource.ROMM_SYNCED)
            }
            downloadQueueDao.deleteByGameId(gameId)

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
            downloadQueueDao.updateState(
                queueEntry.id,
                DownloadState.FAILED.name,
                e.message ?: "Extraction failed"
            )
            ExtractionResult.Failure(e.message ?: "Extraction failed")
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

            downloadQueueDao.deleteByGameId(gameId)
        }
    }

    private suspend fun autoApplyToEdenIfNeeded(progress: DownloadProgress, finalPath: String) {
        if (!progress.isGameFileDownload) return
        val category = progress.fileCategory ?: return
        if (category != "update" && category != "dlc") return

        try {
            val game = gameDao.getById(progress.gameId) ?: return
            val emulatorId = emulatorResolver.getEmulatorIdForGame(
                progress.gameId, game.platformId, game.platformSlug
            )
            if (emulatorId != "eden") return

            val gameDir = File(finalPath).parentFile?.parent ?: return
            edenContentManager.registerDirectory(gameDir)
        } catch (e: Exception) {
            Log.w(TAG, "Auto-apply to Eden failed: ${e.message}")
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
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            state = try {
                DownloadState.valueOf(state)
            } catch (e: Exception) {
                DownloadState.QUEUED
            },
            errorReason = errorReason,
            isMultiFileRom = isMultiFileRom
        )
    }
}
