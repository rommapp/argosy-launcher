package com.nendo.argosy.data.download

import android.content.Context
import android.os.StatFs
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.data.remote.romm.RomMResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val fileName: String,
    val gameTitle: String,
    val platformSlug: String,
    val coverPath: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: DownloadState,
    val errorReason: String? = null
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f

    val isDiscDownload: Boolean get() = discId != null
}

enum class DownloadState {
    QUEUED,
    WAITING_FOR_STORAGE,
    DOWNLOADING,
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

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(DownloadQueueState())
    val state: StateFlow<DownloadQueueState> = _state.asStateFlow()

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
        val pending = downloadQueueDao.getPendingDownloads()
        if (pending.isEmpty()) {
            updateAvailableStorage()
            return
        }

        val restored = pending.map { it.toDownloadProgress() }
        _state.value = DownloadQueueState(
            queue = restored,
            availableStorageBytes = getAvailableStorageBytes()
        )

        processQueue()
    }

    private suspend fun getDownloadDir(): File {
        val prefs = preferencesRepository.userPreferences.first()
        val customPath = prefs.romStoragePath
        return if (customPath != null) {
            File(customPath).also { it.mkdirs() }
        } else {
            defaultDownloadDir
        }
    }

    private suspend fun getAvailableStorageBytes(): Long {
        return withContext(Dispatchers.IO) {
            try {
                val downloadDir = getDownloadDir()
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

    private suspend fun updateAvailableStorage() {
        val available = getAvailableStorageBytes()
        _state.value = _state.value.copy(availableStorageBytes = available)
    }

    suspend fun enqueueDownload(
        gameId: Long,
        rommId: Long,
        fileName: String,
        gameTitle: String,
        platformSlug: String,
        coverPath: String?,
        expectedSizeBytes: Long = 0
    ) {
        val currentState = _state.value
        if (currentState.activeDownloads.any { it.gameId == gameId }) return
        if (currentState.queue.any { it.gameId == gameId }) return

        val existing = downloadQueueDao.getByGameId(gameId)
        if (existing != null) return

        val downloadDir = getDownloadDir()
        val platformDir = File(downloadDir, platformSlug)
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
            createdAt = Instant.now()
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
            state = DownloadState.QUEUED
        )

        _state.value = _state.value.copy(
            queue = _state.value.queue + progress
        )

        processQueue()
    }

    suspend fun enqueueDiscDownload(
        gameId: Long,
        discId: Long,
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

        val downloadDir = getDownloadDir()
        val platformDir = File(downloadDir, platformSlug)
        val tempFilePath = File(platformDir, "${fileName}.tmp").absolutePath

        val entity = DownloadQueueEntity(
            gameId = gameId,
            rommId = rommId,
            discId = discId,
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
            fileName = fileName,
            gameTitle = gameTitle,
            platformSlug = platformSlug,
            coverPath = coverPath,
            bytesDownloaded = 0,
            totalBytes = expectedSizeBytes,
            state = DownloadState.QUEUED
        )

        _state.value = _state.value.copy(
            queue = _state.value.queue + progress
        )

        processQueue()
    }

    private suspend fun processQueue() {
        val maxConcurrent = preferencesRepository.userPreferences.first().maxConcurrentDownloads
        val currentActive = _state.value.activeDownloads.size

        if (currentActive >= maxConcurrent) return

        val slotsAvailable = maxConcurrent - currentActive
        val nextItems = _state.value.queue
            .filter { it.state == DownloadState.QUEUED }
            .take(slotsAvailable)

        if (nextItems.isEmpty()) return

        val availableStorage = getAvailableStorageBytes()

        for (next in nextItems) {
            if (downloadJobs[next.id]?.isActive == true) continue

            val requiredBytes = next.totalBytes - next.bytesDownloaded

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
                }
            }
        }
    }

    private suspend fun downloadRom(progress: DownloadProgress): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val downloadDir = getDownloadDir()
                val platformDir = File(downloadDir, progress.platformSlug).also { it.mkdirs() }
                val tempFile = File(platformDir, "${progress.fileName}.tmp")
                val targetFile = File(platformDir, progress.fileName)

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

                                while (true) {
                                    coroutineContext.ensureActive()
                                    val read = input.read(buffer)
                                    if (read == -1) break

                                    output.write(buffer, 0, read)
                                    bytesRead += read

                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdateTime > UI_UPDATE_INTERVAL_MS) {
                                        updateProgress(
                                            progress.copy(
                                                bytesDownloaded = bytesRead,
                                                totalBytes = totalSize
                                            )
                                        )
                                        lastUpdateTime = now
                                    }

                                    if (now - lastDbUpdateTime > DB_UPDATE_INTERVAL_MS) {
                                        downloadQueueDao.updateProgress(progress.id, bytesRead)
                                        lastDbUpdateTime = now
                                    }
                                }

                                updateProgress(
                                    progress.copy(
                                        bytesDownloaded = bytesRead,
                                        totalBytes = totalSize
                                    )
                                )
                                downloadQueueDao.updateProgress(progress.id, bytesRead)

                                if (!tempFile.renameTo(targetFile)) {
                                    tempFile.copyTo(targetFile, overwrite = true)
                                    tempFile.delete()
                                }

                                if (progress.isDiscDownload && progress.discId != null) {
                                    gameDiscDao.updateLocalPath(progress.discId, targetFile.absolutePath)
                                } else {
                                    gameDao.updateLocalPath(
                                        progress.gameId,
                                        targetFile.absolutePath,
                                        GameSource.ROMM_SYNCED
                                    )
                                }

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

    private fun updateProgress(progress: DownloadProgress) {
        _state.value = _state.value.copy(
            activeDownloads = _state.value.activeDownloads.map {
                if (it.id == progress.id) progress.copy(state = DownloadState.DOWNLOADING) else it
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

    suspend fun retryFailedDownloads() {
        val failed = downloadQueueDao.getFailedDownloads()
        if (failed.isEmpty()) return

        val retryable = failed.filter { entity ->
            val reason = entity.errorReason ?: ""
            !reason.contains("not found", ignoreCase = true) &&
                !reason.contains("HTTP 400", ignoreCase = true) &&
                !reason.contains("HTTP 404", ignoreCase = true)
        }

        if (retryable.isEmpty()) return

        for (entity in retryable) {
            downloadQueueDao.updateState(entity.id, DownloadState.QUEUED.name, null)
        }

        restoreQueueFromDatabase()
    }

    suspend fun recheckStorageAndResume() {
        val waiting = _state.value.queue.filter { it.state == DownloadState.WAITING_FOR_STORAGE }
        if (waiting.isEmpty()) {
            updateAvailableStorage()
            return
        }

        val availableStorage = getAvailableStorageBytes()

        var anyResumed = false
        for (item in waiting) {
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
                    val downloadDir = getDownloadDir()
                    val tempFile = File(downloadDir, "${active.platformSlug}/${active.fileName}.tmp")
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
                        val downloadDir = getDownloadDir()
                        val tempFile = File(downloadDir, "${queued.platformSlug}/${queued.fileName}.tmp")
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

    suspend fun getDownloadPath(platformSlug: String, fileName: String): File {
        val downloadDir = getDownloadDir()
        val platformDir = File(downloadDir, platformSlug)
        return File(platformDir, fileName)
    }

    private fun DownloadQueueEntity.toDownloadProgress(): DownloadProgress {
        return DownloadProgress(
            id = id,
            gameId = gameId,
            rommId = rommId,
            discId = discId,
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
            errorReason = errorReason
        )
    }
}
