package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.BiosPathRegistry
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.FirmwareEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMFirmware
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BiosRepository"
private const val BIOS_INTERNAL_DIR = "bios"

data class BiosPlatformStatus(
    val platformSlug: String,
    val platformName: String,
    val totalFiles: Int,
    val downloadedFiles: Int,
    val missingFiles: Int
)

data class BiosDownloadProgress(
    val firmwareId: Long,
    val fileName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long
) {
    val progress: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}

sealed class BiosDownloadResult {
    data class Success(val localPath: String) : BiosDownloadResult()
    data class Error(val message: String) : BiosDownloadResult()
}

@Singleton
class BiosRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firmwareDao: FirmwareDao,
    private val platformDao: PlatformDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private var api: RomMApi? = null

    fun setApi(api: RomMApi?) {
        this.api = api
    }

    private fun getInternalBiosDir(): File {
        val dir = File(context.filesDir, BIOS_INTERNAL_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getInternalBiosPlatformDir(platformSlug: String): File {
        val dir = File(getInternalBiosDir(), platformSlug)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun syncPlatformFirmware(
        platformId: Long,
        platformSlug: String,
        firmware: List<RomMFirmware>
    ) = withContext(Dispatchers.IO) {
        if (firmware.isEmpty()) {
            Logger.debug(TAG, "No firmware for platform $platformSlug")
            return@withContext
        }

        Logger.info(TAG, "Syncing ${firmware.size} firmware files for $platformSlug")

        val entities = firmware.map { fw ->
            val existing = firmwareDao.getByRommId(fw.id)
            FirmwareEntity(
                id = existing?.id ?: 0,
                platformId = platformId,
                platformSlug = platformSlug,
                rommId = fw.id,
                fileName = fw.fileName,
                filePath = fw.filePath,
                fileSizeBytes = fw.fileSizeBytes,
                md5Hash = fw.md5Hash,
                sha1Hash = fw.sha1Hash,
                localPath = existing?.localPath,
                downloadedAt = existing?.downloadedAt,
                lastVerifiedAt = existing?.lastVerifiedAt
            )
        }

        firmwareDao.upsertAll(entities)
        firmwareDao.deleteRemovedFirmware(platformId, firmware.map { it.id })
    }

    suspend fun downloadFirmware(
        firmwareId: Long,
        onProgress: ((BiosDownloadProgress) -> Unit)? = null
    ): BiosDownloadResult = withContext(Dispatchers.IO) {
        val currentApi = api
        if (currentApi == null) {
            Logger.error(TAG, "Download failed: API not connected")
            return@withContext BiosDownloadResult.Error("Not connected")
        }
        val firmware = firmwareDao.getByRommId(firmwareId)
        if (firmware == null) {
            Logger.error(TAG, "Download failed: Firmware not found for rommId=$firmwareId")
            return@withContext BiosDownloadResult.Error("Firmware not found")
        }

        val platformDir = getInternalBiosPlatformDir(firmware.platformSlug)
        val targetFile = File(platformDir, firmware.fileName)

        try {
            Logger.info(TAG, "Downloading firmware: ${firmware.fileName} (id=${firmware.rommId})")

            val response = currentApi.downloadFirmware(firmware.rommId, firmware.fileName)
            if (!response.isSuccessful) {
                Logger.error(TAG, "Download failed for ${firmware.fileName}: HTTP ${response.code()}")
                return@withContext BiosDownloadResult.Error("Download failed: HTTP ${response.code()}")
            }

            val body = response.body()
                ?: return@withContext BiosDownloadResult.Error("Empty response")

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val totalBytes = body.contentLength()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        onProgress?.invoke(
                            BiosDownloadProgress(
                                firmwareId = firmware.rommId,
                                fileName = firmware.fileName,
                                bytesDownloaded = totalBytesRead,
                                totalBytes = totalBytes
                            )
                        )
                    }
                }
            }

            if (firmware.md5Hash != null) {
                val actualMd5 = calculateMd5(targetFile)
                if (!actualMd5.equals(firmware.md5Hash, ignoreCase = true)) {
                    targetFile.delete()
                    return@withContext BiosDownloadResult.Error("MD5 mismatch: expected ${firmware.md5Hash}, got $actualMd5")
                }
            }

            firmwareDao.updateLocalPath(firmware.id, targetFile.absolutePath, Instant.now())
            Logger.info(TAG, "Downloaded firmware: ${firmware.fileName}")

            BiosDownloadResult.Success(targetFile.absolutePath)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to download firmware: ${e.message}", e)
            targetFile.delete()
            BiosDownloadResult.Error(e.message ?: "Download failed")
        }
    }

    suspend fun downloadAllMissing(
        onProgress: ((current: Int, total: Int, fileName: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val missing = firmwareDao.getMissing()
        if (missing.isEmpty()) return@withContext 0

        Logger.info(TAG, "Downloading ${missing.size} missing firmware files")
        var downloaded = 0

        missing.forEachIndexed { index, firmware ->
            onProgress?.invoke(index + 1, missing.size, firmware.fileName)
            val result = downloadFirmware(firmware.rommId)
            if (result is BiosDownloadResult.Success) {
                downloaded++
            }
        }

        Logger.info(TAG, "Downloaded $downloaded of ${missing.size} firmware files")
        downloaded
    }

    suspend fun distributeBiosToEmulator(
        platformSlug: String,
        emulatorId: String
    ): Int = withContext(Dispatchers.IO) {
        val config = BiosPathRegistry.getEmulatorBiosPaths(emulatorId) ?: return@withContext 0
        if (platformSlug !in config.supportedPlatforms) return@withContext 0

        val downloaded = firmwareDao.getByPlatformSlug(platformSlug).filter { it.localPath != null }
        if (downloaded.isEmpty()) return@withContext 0

        var copiedCount = 0
        for (targetPath in config.defaultPaths) {
            val targetDir = File(targetPath)
            if (!targetDir.exists()) {
                if (!targetDir.mkdirs()) continue
            }
            if (!targetDir.canWrite()) continue

            for (firmware in downloaded) {
                val sourceFile = File(firmware.localPath!!)
                if (!sourceFile.exists()) continue

                val targetFile = File(targetDir, firmware.fileName)
                try {
                    sourceFile.copyTo(targetFile, overwrite = true)
                    Logger.debug(TAG, "Copied ${firmware.fileName} to $targetPath")
                    copiedCount++
                } catch (e: Exception) {
                    Logger.error(TAG, "Failed to copy ${firmware.fileName}: ${e.message}")
                }
            }

            if (copiedCount > 0) break
        }

        copiedCount
    }

    suspend fun distributeAllBiosToEmulators(): Map<String, Int> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Int>()
        val platformSlugs = firmwareDao.getPlatformSlugsWithDownloadedFirmware()

        for (slug in platformSlugs) {
            val emulators = BiosPathRegistry.getEmulatorsForPlatform(slug)
            for (config in emulators) {
                val count = distributeBiosToEmulator(slug, config.emulatorId)
                if (count > 0) {
                    results[config.emulatorId] = (results[config.emulatorId] ?: 0) + count
                }
            }
        }

        results
    }

    data class DetailedDistributeResult(
        val emulatorId: String,
        val platformResults: Map<String, Int>
    )

    suspend fun distributeAllBiosToEmulatorsDetailed(): List<DetailedDistributeResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, MutableMap<String, Int>>()
        val platformSlugs = firmwareDao.getPlatformSlugsWithDownloadedFirmware()

        for (slug in platformSlugs) {
            val emulators = BiosPathRegistry.getEmulatorsForPlatform(slug)
            for (config in emulators) {
                val count = distributeBiosToEmulator(slug, config.emulatorId)
                if (count > 0) {
                    val emulatorResults = results.getOrPut(config.emulatorId) { mutableMapOf() }
                    emulatorResults[slug] = count
                }
            }
        }

        results.map { (emulatorId, platformResults) ->
            DetailedDistributeResult(emulatorId, platformResults)
        }
    }

    suspend fun getStatusByPlatform(): List<BiosPlatformStatus> = withContext(Dispatchers.IO) {
        val allFirmware = firmwareDao.observeAll().first()
        val grouped = allFirmware.groupBy { it.platformSlug }

        grouped.map { (slug, files) ->
            val downloaded = files.count { it.localPath != null }
            val platform = platformDao.getBySlug(slug)
            BiosPlatformStatus(
                platformSlug = slug,
                platformName = platform?.name ?: slug,
                totalFiles = files.size,
                downloadedFiles = downloaded,
                missingFiles = files.size - downloaded
            )
        }.sortedBy { it.platformName }
    }

    fun observeFirmware(): Flow<List<FirmwareEntity>> = firmwareDao.observeAll()

    fun observeFirmwareByPlatform(platformSlug: String): Flow<List<FirmwareEntity>> =
        firmwareDao.observeByPlatformSlug(platformSlug)

    fun observeMissingCount(): Flow<Int> = firmwareDao.observeMissingCount()

    fun observeDownloadedCount(): Flow<Int> = firmwareDao.observeDownloadedCount()

    fun observeTotalAndDownloaded(): Flow<Pair<Int, Int>> {
        return firmwareDao.observeAll().map { list ->
            val total = list.size
            val downloaded = list.count { it.localPath != null }
            total to downloaded
        }
    }

    suspend fun verifyBiosFiles(): Int = withContext(Dispatchers.IO) {
        val downloaded = firmwareDao.getDownloaded()
        var verifiedCount = 0

        for (firmware in downloaded) {
            val file = File(firmware.localPath!!)
            if (!file.exists()) {
                firmwareDao.updateLocalPath(firmware.id, null, null)
                continue
            }

            if (firmware.md5Hash != null) {
                val actualMd5 = calculateMd5(file)
                if (!actualMd5.equals(firmware.md5Hash, ignoreCase = true)) {
                    Logger.warn(TAG, "MD5 mismatch for ${firmware.fileName}")
                    file.delete()
                    firmwareDao.updateLocalPath(firmware.id, null, null)
                    continue
                }
            }

            firmwareDao.updateVerifiedAt(firmware.id, Instant.now())
            verifiedCount++
        }

        verifiedCount
    }

    suspend fun migrateToCustomPath(newPath: String?): Boolean = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.preferences.first()
        val oldPath = prefs.customBiosPath
        val internalDir = getInternalBiosDir()

        if (newPath != null) {
            val newDir = File(newPath, "bios")
            if (!newDir.exists() && !newDir.mkdirs()) {
                Logger.error(TAG, "Failed to create new BIOS directory: $newPath")
                return@withContext false
            }

            try {
                internalDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relativePath = file.relativeTo(internalDir)
                    val targetFile = File(newDir, relativePath.path)
                    targetFile.parentFile?.mkdirs()
                    file.copyTo(targetFile, overwrite = true)
                }
                Logger.info(TAG, "Copied BIOS files to $newPath")
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to copy BIOS files: ${e.message}", e)
                return@withContext false
            }
        }

        if (oldPath != null && oldPath != newPath) {
            try {
                val oldDir = File(oldPath, "bios")
                if (oldDir.exists()) {
                    oldDir.deleteRecursively()
                    Logger.info(TAG, "Deleted old BIOS directory: $oldPath")
                }
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to delete old BIOS directory: ${e.message}")
            }
        }

        userPreferencesRepository.setCustomBiosPath(newPath)
        true
    }

    private fun calculateMd5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
