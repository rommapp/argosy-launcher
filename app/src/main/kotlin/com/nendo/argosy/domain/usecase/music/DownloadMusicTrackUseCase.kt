package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

private const val COPY_BUFFER_SIZE = 64 * 1024

/** Streams a single RomM track straight into the public Music directory. */
class DownloadMusicTrackUseCase @Inject constructor(
    private val romMRepository: RomMRepository,
    private val gameFileDao: GameFileDao,
    private val musicDirectoryManager: MusicDirectoryManager,
    private val attributionRepository: StorageAttributionRepository
) {
    suspend operator fun invoke(
        rommFileId: Long,
        fileName: String,
        platformName: String,
        gameName: String,
        trackNumber: Int? = null,
        title: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = musicDirectoryManager.targetFileFor(
            platformName, gameName, trackNumber, title, fileName
        )
        when (val result = romMRepository.downloadRomFile(rommFileId, fileName)) {
            is RomMResult.Success -> writeToTarget(result.data.body, target, rommFileId)
            is RomMResult.Error -> Result.failure(IOException(result.message))
        }
    }

    private suspend fun writeToTarget(
        body: okhttp3.ResponseBody,
        target: File,
        rommFileId: Long
    ): Result<File> {
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        return try {
            target.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                }
            }
            if (target.exists()) target.delete()
            if (!tempFile.renameTo(target)) {
                tempFile.copyTo(target, overwrite = true)
                tempFile.delete()
            }
            gameFileDao.getByRommFileId(rommFileId)?.let { row ->
                gameFileDao.updateLocalPath(row.id, target.absolutePath, Instant.now())
            }
            musicDirectoryManager.scanFile(target)
            attributionRepository.markDirty(StorageCategory.MUSIC)
            Result.success(target)
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }
}
