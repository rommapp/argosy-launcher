package com.nendo.argosy.data.download

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DownloadManager"

data class DownloadProgress(
    val gameId: Long,
    val rommId: Long,
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
}

enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

private sealed class DownloadResult {
    data object Success : DownloadResult()
    data class Failure(val reason: String) : DownloadResult()
}

private val INVALID_CONTENT_TYPES = listOf("image/", "text/html")
private const val MIN_ROM_SIZE_BYTES = 1024L

data class DownloadQueueState(
    val activeDownload: DownloadProgress? = null,
    val queue: List<DownloadProgress> = emptyList(),
    val completed: List<DownloadProgress> = emptyList()
)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val _state = MutableStateFlow(DownloadQueueState())
    val state: StateFlow<DownloadQueueState> = _state.asStateFlow()

    private val defaultDownloadDir: File by lazy {
        File(context.getExternalFilesDir(null), "downloads").also { it.mkdirs() }
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

    suspend fun enqueueDownload(
        gameId: Long,
        rommId: Long,
        fileName: String,
        gameTitle: String,
        platformSlug: String,
        coverPath: String?,
        expectedSizeBytes: Long = 0
    ) {
        Log.d(TAG, "enqueueDownload: $gameTitle ($platformSlug), expectedSize=$expectedSizeBytes")
        val progress = DownloadProgress(
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

        processQueue(platformSlug)
    }

    private suspend fun processQueue(platformSlug: String) {
        if (_state.value.activeDownload != null) return
        val next = _state.value.queue.firstOrNull() ?: return

        _state.value = _state.value.copy(
            activeDownload = next.copy(state = DownloadState.DOWNLOADING),
            queue = _state.value.queue.drop(1)
        )

        val result = downloadRom(next, platformSlug)

        val finalProgress = when (result) {
            is DownloadResult.Success -> next.copy(state = DownloadState.COMPLETED)
            is DownloadResult.Failure -> next.copy(
                state = DownloadState.FAILED,
                errorReason = result.reason
            )
        }

        _state.value = _state.value.copy(
            activeDownload = null,
            completed = _state.value.completed + finalProgress
        )

        processQueue(platformSlug)
    }

    private suspend fun downloadRom(progress: DownloadProgress, platformSlug: String): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "downloadRom: starting download for ${progress.fileName}")

                when (val result = romMRepository.downloadRom(progress.rommId, progress.fileName)) {
                    is RomMResult.Success -> {
                        val body = result.data
                        val contentType = body.contentType()?.toString() ?: ""
                        val contentLength = body.contentLength()

                        Log.d(TAG, "downloadRom: contentType=$contentType, contentLength=$contentLength")

                        if (INVALID_CONTENT_TYPES.any { contentType.startsWith(it) }) {
                            Log.e(TAG, "downloadRom: invalid content type: $contentType")
                            return@withContext DownloadResult.Failure("Invalid file type: $contentType")
                        }

                        if (contentLength in 1 until MIN_ROM_SIZE_BYTES) {
                            Log.w(TAG, "downloadRom: file too small ($contentLength bytes)")
                            return@withContext DownloadResult.Failure("File too small to be a ROM")
                        }

                        val downloadDir = getDownloadDir()
                        val platformDir = File(downloadDir, platformSlug).also { it.mkdirs() }
                        val targetFile = File(platformDir, progress.fileName)

                        Log.d(TAG, "downloadRom: saving to ${targetFile.absolutePath}")

                        val totalSize = when {
                            contentLength > 0 -> contentLength
                            progress.totalBytes > 0 -> progress.totalBytes
                            else -> 0L
                        }
                        updateProgress(progress.copy(totalBytes = totalSize))

                        body.byteStream().use { input ->
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(65536)
                                var bytesRead: Long = 0
                                var lastUpdateTime = System.currentTimeMillis()

                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break

                                    output.write(buffer, 0, read)
                                    bytesRead += read

                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdateTime > 500) {
                                        Log.d(TAG, "downloadRom: progress $bytesRead / $totalSize")
                                        updateProgress(
                                            progress.copy(
                                                bytesDownloaded = bytesRead,
                                                totalBytes = totalSize
                                            )
                                        )
                                        lastUpdateTime = now
                                    }
                                }

                                updateProgress(
                                    progress.copy(
                                        bytesDownloaded = bytesRead,
                                        totalBytes = totalSize
                                    )
                                )
                            }
                        }

                        Log.d(TAG, "downloadRom: download complete, updating database")
                        gameDao.updateLocalPath(
                            progress.gameId,
                            targetFile.absolutePath,
                            GameSource.ROMM_SYNCED
                        )

                        DownloadResult.Success
                    }
                    is RomMResult.Error -> {
                        Log.e(TAG, "downloadRom: failed - ${result.message}")
                        DownloadResult.Failure(result.message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "downloadRom: exception", e)
                DownloadResult.Failure(e.message ?: "Unknown error")
            }
        }

    private fun updateProgress(progress: DownloadProgress) {
        _state.value = _state.value.copy(
            activeDownload = progress.copy(state = DownloadState.DOWNLOADING)
        )
    }

    fun cancelDownload(rommId: Long) {
        _state.value = _state.value.copy(
            queue = _state.value.queue.filterNot { it.rommId == rommId }
        )
    }

    fun clearCompleted() {
        _state.value = _state.value.copy(completed = emptyList())
    }

    suspend fun getDownloadPath(platformSlug: String, fileName: String): File {
        val downloadDir = getDownloadDir()
        val platformDir = File(downloadDir, platformSlug)
        return File(platformDir, fileName)
    }
}
