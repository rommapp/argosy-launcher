package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.BiosPathRegistry
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.SwitchKeyManager
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.FirmwareEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMFirmware
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BiosRepository"
private const val XBOX_EMULATOR_ID = "hakux"
private const val BIOS_INTERNAL_DIR = "bios"
private const val FIRMWARE_STALL_TIMEOUT_MS = 90_000L
private const val FIRMWARE_PART_SUFFIX = ".part"
private const val FIRMWARE_DOWNLOAD_ATTEMPTS = 6
private const val HTTP_PARTIAL_CONTENT = 206

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

/**
 * Bulk-download progress: which file of how many, and how far into that file.
 *
 * The byte arguments are what let a single large file show movement. Reporting only the file index
 * left a multi-gigabyte download sitting on one unchanging notch for minutes, which reads as a hang.
 */
typealias FirmwareBulkProgress = (
    current: Int,
    total: Int,
    fileName: String,
    fileProgress: Float,
    bytesDownloaded: Long,
    totalBytes: Long
) -> Unit

data class FirmwareDownloadFailure(
    val fileName: String,
    val platformSlug: String,
    val reason: String
)

/**
 * What a bulk firmware download actually achieved.
 *
 * A count alone cannot distinguish a finished run from a partial one, so the caller reported every
 * run as complete and a file that never arrived looked downloaded until the user went looking for
 * it on disk. [failures] is what makes an incomplete run sayable.
 */
data class FirmwareDownloadSummary(
    val downloaded: Int = 0,
    val failures: List<FirmwareDownloadFailure> = emptyList()
)

@Singleton
class BiosRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firmwareDao: FirmwareDao,
    private val platformDao: PlatformDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val switchKeyManager: SwitchKeyManager,
    private val attributionRepository: StorageAttributionRepository,
    private val xboxDiskImageProvisioner: com.nendo.argosy.data.sync.xbox.XboxDiskImageProvisioner
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

    private fun resolveBiosDir(basePath: String): File {
        val baseDir = File(basePath)
        return if (baseDir.name.equals("bios", ignoreCase = true)) {
            baseDir
        } else {
            File(basePath, "bios")
        }
    }

    /**
     * The directory BIOS is actually kept in: the configured location when one is set, the app's
     * own storage otherwise.
     *
     * Downloads resolve through here rather than writing to internal storage unconditionally. With
     * the write pinned to internal, a newly downloaded file did not appear at the configured
     * location until the setting was changed again and [migrateToCustomPath] happened to move it,
     * so the folder a user had pointed an emulator at stayed empty with nothing to explain why.
     */
    private suspend fun biosRootsArgosyWritesTo(): List<String> {
        val roots = mutableListOf(getInternalBiosDir().absolutePath)
        val customPath = userPreferencesRepository.preferences.first().customBiosPath ?: return roots

        val customDir = resolveBiosDir(customPath)
        if (customDir.isDirectory && customDir.list() != null) {
            roots += customDir.absolutePath
        } else {
            Logger.info(TAG, "reconcile: configured BIOS dir unreadable, leaving its rows alone: $customDir")
        }
        return roots
    }

    private suspend fun getActiveBiosPlatformDir(platformSlug: String): File {
        val customPath = userPreferencesRepository.preferences.first().customBiosPath
        val base = if (customPath != null) resolveBiosDir(customPath) else getInternalBiosDir()
        val dir = File(base, platformSlug)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun copyBiosForPlatformTo(platformSlug: String, targetPath: String): Int = withContext(Dispatchers.IO) {
        val sourceDir = getActiveBiosPlatformDir(platformSlug)
        val targetDir = File(targetPath)
        if (!targetDir.exists()) targetDir.mkdirs()
        val sourceFiles = sourceDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(FIRMWARE_PART_SUFFIX) }
            ?: return@withContext 0
        var copied = 0
        for (file in sourceFiles) {
            try {
                file.copyTo(File(targetDir, file.name), overwrite = true)
                copied++
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to copy BIOS file ${file.name} to $targetPath", e)
            }
        }
        Logger.info(TAG, "Copied $copied BIOS files for $platformSlug to $targetPath")
        copied
    }

    fun getLibretroSystemDir(): File {
        val dir = AppPaths.libretroSystemDir(context.filesDir)
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

        val platformDir = getActiveBiosPlatformDir(firmware.platformSlug)
        val targetFile = File(platformDir, firmware.fileName)
        val partFile = File(platformDir, "${firmware.fileName}$FIRMWARE_PART_SUFFIX")

        val expectedBytes = firmware.fileSizeBytes

        try {
            Logger.info(TAG, "Downloading firmware: ${firmware.fileName} (id=${firmware.rommId})")

            if (expectedBytes > 0 && partFile.length() > expectedBytes) {
                partFile.delete()
            }

            var attempt = 0
            var lastError: String? = null

            while (attempt < FIRMWARE_DOWNLOAD_ATTEMPTS) {
                attempt++
                val resumeFrom = partFile.length()
                if (expectedBytes > 0 && resumeFrom == expectedBytes) break

                val range = if (resumeFrom > 0) "bytes=$resumeFrom-" else null
                if (range != null) {
                    Logger.info(
                        TAG,
                        "Resuming ${firmware.fileName} from $resumeFrom bytes (attempt $attempt)"
                    )
                }

                val response = currentApi.downloadFirmware(firmware.rommId, firmware.fileName, range)
                if (!response.isSuccessful) {
                    Logger.error(TAG, "Download failed for ${firmware.fileName}: HTTP ${response.code()}")
                    return@withContext BiosDownloadResult.Error("Download failed: HTTP ${response.code()}")
                }

                val body = response.body()
                    ?: return@withContext BiosDownloadResult.Error("Empty response")

                val serverResumed = response.code() == HTTP_PARTIAL_CONTENT
                if (resumeFrom > 0 && !serverResumed) {
                    Logger.info(TAG, "Server ignored the range request, restarting ${firmware.fileName}")
                    partFile.delete()
                }

                val startedAt = if (serverResumed) resumeFrom else 0L
                val totalBytes = if (expectedBytes > 0) expectedBytes else body.contentLength()

                try {
                    body.byteStream().use { input ->
                        FileOutputStream(partFile, startedAt > 0).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = startedAt
                            var lastReadAt = System.currentTimeMillis()

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (System.currentTimeMillis() - lastReadAt > FIRMWARE_STALL_TIMEOUT_MS) {
                                    throw java.io.IOException("Firmware download stalled")
                                }
                                output.write(buffer, 0, bytesRead)
                                lastReadAt = System.currentTimeMillis()
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
                    lastError = null
                } catch (e: java.io.IOException) {
                    lastError = e.message ?: "Connection interrupted"
                    Logger.warn(
                        TAG,
                        "${firmware.fileName} interrupted at ${partFile.length()} bytes " +
                            "(attempt $attempt of $FIRMWARE_DOWNLOAD_ATTEMPTS): $lastError"
                    )
                    if (partFile.length() <= resumeFrom) break
                    continue
                }

                if (expectedBytes <= 0 || partFile.length() >= expectedBytes) break
            }

            if (lastError != null && (expectedBytes <= 0 || partFile.length() != expectedBytes)) {
                Logger.error(TAG, "Gave up on ${firmware.fileName}: $lastError")
                return@withContext BiosDownloadResult.Error(lastError)
            }

            if (expectedBytes > 0 && partFile.length() != expectedBytes) {
                Logger.error(
                    TAG,
                    "Short read for ${firmware.fileName}: got ${partFile.length()} of $expectedBytes bytes"
                )
                return@withContext BiosDownloadResult.Error("Download incomplete, try again")
            }

            if (firmware.md5Hash != null) {
                val actualMd5 = calculateMd5(partFile)
                if (!actualMd5.equals(firmware.md5Hash, ignoreCase = true)) {
                    partFile.delete()
                    return@withContext BiosDownloadResult.Error("MD5 mismatch: expected ${firmware.md5Hash}, got $actualMd5")
                }
            }

            targetFile.delete()
            if (!partFile.renameTo(targetFile)) {
                partFile.delete()
                Logger.error(TAG, "Could not move ${firmware.fileName} into place")
                return@withContext BiosDownloadResult.Error("Could not save the downloaded file")
            }

            firmwareDao.updateLocalPath(firmware.id, targetFile.absolutePath, Instant.now())
            Logger.info(TAG, "Downloaded firmware: ${firmware.fileName}")

            attributionRepository.markDirty(StorageCategory.BIOS)
            BiosDownloadResult.Success(targetFile.absolutePath)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to download firmware: ${e.message}", e)
            BiosDownloadResult.Error(e.message ?: "Download failed")
        }
    }

    /**
     * Clears the recorded path of any firmware whose file is no longer on disk, so a BIOS that was
     * moved or deleted counts as missing again and can be fetched a second time.
     *
     * Only directories Argosy downloads into are examined, and a configured one only while it
     * reads back, so an unmounted card is unreachable rather than empty. Copies distributed into
     * an emulator's own folder are left alone: absent there means the emulator or the user moved
     * it, which redownloading does not address.
     *
     * Returns how many rows were cleared.
     */
    suspend fun reconcileDownloadedFirmware(): Int = withContext(Dispatchers.IO) {
        val ownedRoots = biosRootsArgosyWritesTo()
        var cleared = 0
        for (firmware in firmwareDao.getAllDownloaded()) {
            val path = firmware.localPath ?: continue
            if (ownedRoots.none { path.startsWith(it) }) continue
            if (File(path).exists()) continue
            firmwareDao.updateLocalPath(firmware.id, null, null)
            cleared++
        }
        if (cleared > 0) {
            Logger.info(TAG, "Reconciled firmware: $cleared recorded file(s) no longer on disk")
            attributionRepository.markDirty(StorageCategory.BIOS)
        }
        cleared
    }

    suspend fun downloadAllMissing(
        onProgress: FirmwareBulkProgress? = null
    ): FirmwareDownloadSummary = withContext(Dispatchers.IO) {
        reconcileDownloadedFirmware()
        val missing = firmwareDao.getSyncEnabledMissing()
        if (missing.isEmpty()) return@withContext FirmwareDownloadSummary()

        Logger.info(TAG, "Downloading ${missing.size} missing firmware files")
        var downloaded = 0
        val failed = mutableListOf<FirmwareDownloadFailure>()

        missing.forEachIndexed { index, firmware ->
            onProgress?.invoke(index + 1, missing.size, firmware.fileName, 0f, 0L, firmware.fileSizeBytes)
            val step: (BiosDownloadProgress) -> Unit = { p ->
                onProgress?.invoke(
                    index + 1, missing.size, firmware.fileName, p.progress, p.bytesDownloaded, p.totalBytes
                )
            }
            when (val result = downloadFirmware(firmware.rommId, step)) {
                is BiosDownloadResult.Success -> downloaded++
                is BiosDownloadResult.Error -> failed += FirmwareDownloadFailure(
                    fileName = firmware.fileName,
                    platformSlug = firmware.platformSlug,
                    reason = result.message
                )
            }
        }

        Logger.info(TAG, "Downloaded $downloaded of ${missing.size} firmware files")
        FirmwareDownloadSummary(downloaded, failed)
    }

    suspend fun redownloadAll(
        onProgress: FirmwareBulkProgress? = null
    ): FirmwareDownloadSummary = withContext(Dispatchers.IO) {
        cleanupDisabledPlatformBios()
        reconcileDownloadedFirmware()

        val all = firmwareDao.getSyncEnabledAll()
        if (all.isEmpty()) return@withContext FirmwareDownloadSummary()

        Logger.info(TAG, "Redownloading all ${all.size} firmware files")
        var downloaded = 0
        val failed = mutableListOf<FirmwareDownloadFailure>()

        all.forEachIndexed { index, firmware ->
            onProgress?.invoke(index + 1, all.size, firmware.fileName, 0f, 0L, firmware.fileSizeBytes)
            val step: (BiosDownloadProgress) -> Unit = { p ->
                onProgress?.invoke(
                    index + 1, all.size, firmware.fileName, p.progress, p.bytesDownloaded, p.totalBytes
                )
            }
            when (val result = downloadFirmware(firmware.rommId, step)) {
                is BiosDownloadResult.Success -> downloaded++
                is BiosDownloadResult.Error -> failed += FirmwareDownloadFailure(
                    fileName = firmware.fileName,
                    platformSlug = firmware.platformSlug,
                    reason = result.message
                )
            }
        }

        Logger.info(TAG, "Redownloaded $downloaded of ${all.size} firmware files")
        FirmwareDownloadSummary(downloaded, failed)
    }

    private suspend fun cleanupDisabledPlatformBios() {
        val toClean = firmwareDao.getDownloadedForDisabledPlatforms()
        if (toClean.isEmpty()) return

        val platformSlugs = toClean.map { it.platformSlug }.distinct()
        Logger.info(TAG, "Cleaning up BIOS for disabled platforms: $platformSlugs")

        var cleaned = 0
        for (firmware in toClean) {
            firmware.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            firmwareDao.updateLocalPath(firmware.id, null, null)
            cleaned++
        }

        for (slug in platformSlugs) {
            cleanupDistributedCopies(slug)
            val platformDir = File(getInternalBiosDir(), slug)
            if (platformDir.exists()) platformDir.deleteRecursively()
        }

        Logger.info(TAG, "Cleaned up $cleaned firmware files for disabled platforms")
        attributionRepository.markDirty(StorageCategory.BIOS)
    }

    /**
     * Removes a distributed copy only when the file on disk is the one Argosy put there.
     *
     * The destination is a directory the user manages and shares with the emulator, so a name
     * collision with their own BIOS is ordinary rather than exceptional. Size is compared first
     * because a mismatch is the common case and hashing every candidate would read hundreds of
     * megabytes to learn nothing; the hash decides only once the size already agrees.
     */
    internal fun deleteDistributedCopy(target: File, firmware: FirmwareEntity) {
        if (!target.exists() || !target.isFile) return
        if (firmware.fileSizeBytes > 0 && target.length() != firmware.fileSizeBytes) return

        val expected = firmware.md5Hash
        if (expected != null && !calculateMd5(target).equals(expected, ignoreCase = true)) return

        target.delete()
    }

    private suspend fun cleanupDistributedCopies(platformSlug: String) {
        val firmwareFiles = firmwareDao.getByPlatformSlug(platformSlug)
        val emulators = BiosPathRegistry.getUnpromptedEmulatorsForPlatform(
            PlatformDefinitions.getCanonicalSlug(platformSlug)
        )

        for (config in emulators) {
            val dirs = if (config.emulatorId == EmulatorRegistry.BUILTIN_PACKAGE) {
                listOf(getLibretroSystemDir())
            } else {
                config.defaultPaths.map { File(it) }
            }

            for (dir in dirs) {
                if (!dir.exists()) continue
                for (firmware in firmwareFiles) {
                    if (config.isWriteOnce(firmware.fileName)) continue
                    try {
                        deleteDistributedCopy(File(dir, firmware.fileName), firmware)
                        deleteDistributedCopy(File(dir, config.targetNameFor(firmware.fileName)), firmware)
                        BiosPathRegistry.getNestedBiosPath(firmware.fileName)?.let { nested ->
                            deleteDistributedCopy(File(dir, nested), firmware)
                        }
                        firmware.md5Hash?.let { md5 ->
                            BiosPathRegistry.getRetroArchBiosName(md5)?.let { raName ->
                                deleteDistributedCopy(File(dir, raName), firmware)
                            }
                        }
                    } catch (e: Exception) {
                        Logger.debug(TAG, "Could not clean distributed BIOS ${firmware.fileName} from $dir: ${e.message}")
                    }
                }
            }
        }
    }

    suspend fun distributeBiosToEmulator(
        platformSlug: String,
        emulatorId: String
    ): Int = withContext(Dispatchers.IO) {
        val config = BiosPathRegistry.getEmulatorBiosPaths(emulatorId) ?: return@withContext 0
        val canonicalSlug = PlatformDefinitions.getCanonicalSlug(platformSlug)
        if (!config.supports(canonicalSlug)) return@withContext 0

        val downloaded = firmwareDao.getByPlatformSlug(platformSlug).filter { it.localPath != null }
        if (downloaded.isEmpty()) return@withContext 0

        val requiresExactFilenames = emulatorId.startsWith("retroarch") ||
            emulatorId == "melonds" ||
            emulatorId == EmulatorRegistry.BUILTIN_PACKAGE

        val targetPaths = if (emulatorId == EmulatorRegistry.BUILTIN_PACKAGE) {
            listOf(getLibretroSystemDir().absolutePath)
        } else {
            config.defaultPaths
        }

        var copiedCount = 0
        for (targetPath in targetPaths) {
            val targetDir = File(targetPath)
            if (!targetDir.exists()) {
                if (!targetDir.mkdirs()) continue
            }
            if (!targetDir.canWrite()) continue

            for (firmware in downloaded) {
                val sourceFile = File(firmware.localPath!!)
                if (!sourceFile.exists()) continue
                if (firmware.fileName.startsWith(".")) continue

                val targetFileName = if (requiresExactFilenames) {
                    BiosPathRegistry.getNestedBiosPath(firmware.fileName)
                        ?: run {
                            val md5 = firmware.md5Hash ?: calculateMd5(sourceFile)
                            BiosPathRegistry.getRetroArchBiosName(md5) ?: firmware.fileName
                        }
                } else {
                    config.targetNameFor(firmware.fileName)
                }

                val targetFile = File(targetDir, targetFileName)
                if (config.isWriteOnce(firmware.fileName) && targetFile.exists()) {
                    Logger.debug(TAG, "Keeping existing ${targetFile.name}; it is written once")
                    continue
                }
                try {
                    targetFile.parentFile?.mkdirs()
                    sourceFile.copyTo(targetFile, overwrite = true)
                    Logger.debug(TAG, "Copied ${firmware.fileName} -> $targetFileName to $targetPath")
                    copiedCount++
                } catch (e: Exception) {
                    Logger.error(TAG, "Failed to copy ${firmware.fileName}: ${e.message}")
                }
            }

            if (copiedCount > 0) break
        }

        provisionXboxDiskImage(emulatorId, targetPaths)
        copiedCount
    }

    /**
     * hakuX cannot boot without a hard disk, and unlike the flash ROM and MCPX that disk holds
     * nothing copyrighted, so a blank one is generated rather than demanded from the user. An
     * image already in place is left alone; it is where their saves live.
     */
    private suspend fun provisionXboxDiskImage(emulatorId: String, targetPaths: List<String>) {
        if (emulatorId != XBOX_EMULATOR_ID) return
        targetPaths.firstOrNull { File(it).isDirectory }
            ?.let { xboxDiskImageProvisioner.ensureImageExists(it) }
    }

    suspend fun distributeAllBiosToEmulators(): Map<String, Int> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Int>()
        val platformSlugs = firmwareDao.getPlatformSlugsWithDownloadedFirmware()

        for (slug in platformSlugs) {
            val emulators = BiosPathRegistry.getUnpromptedEmulatorsForPlatform(slug)
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
        coroutineScope {
            val results = mutableMapOf<String, MutableMap<String, Int>>()
            val platformSlugs = firmwareDao.getPlatformSlugsWithDownloadedFirmware()

            val switchJob = if (isSwitchFirmwareReady() && isEdenInstalled()) {
                async {
                    val result = installSwitchFirmware()
                    if (result is SwitchInstallResult.Success) {
                        Pair("eden", "switch" to 2)
                    } else null
                }
            } else null

            val regularSlugs = platformSlugs.filter { it != "switch" }
            for (slug in regularSlugs) {
                val emulators = BiosPathRegistry.getUnpromptedEmulatorsForPlatform(slug)
                for (config in emulators) {
                    val count = distributeBiosToEmulator(slug, config.emulatorId)
                    if (count > 0) {
                        val emulatorResults = results.getOrPut(config.emulatorId) { mutableMapOf() }
                        emulatorResults[slug] = count
                    }
                }
            }

            switchJob?.await()?.let { (emulatorId, platformResult) ->
                val emulatorResults = results.getOrPut(emulatorId) { mutableMapOf() }
                emulatorResults[platformResult.first] = platformResult.second
            }

            results.map { (emulatorId, platformResults) ->
                DetailedDistributeResult(emulatorId, platformResults)
            }
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

        // Determine source directory (old custom path or internal)
        val oldBiosDir = if (oldPath != null) resolveBiosDir(oldPath) else null
        val sourceDir = oldBiosDir ?: internalDir

        if (newPath != null) {
            val newDir = resolveBiosDir(newPath)
            if (!newDir.exists() && !newDir.mkdirs()) {
                Logger.error(TAG, "Failed to create new BIOS directory: $newPath")
                return@withContext false
            }

            try {
                // Copy from source (old custom or internal) to new custom path
                if (sourceDir.exists()) {
                    sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relativePath = file.relativeTo(sourceDir)
                        val targetFile = File(newDir, relativePath.path)
                        targetFile.parentFile?.mkdirs()
                        if (!file.renameTo(targetFile)) {
                            file.copyTo(targetFile, overwrite = true)
                        }
                    }
                    Logger.info(TAG, "Copied BIOS files from ${sourceDir.absolutePath} to ${newDir.absolutePath}")
                }

                // Update localPath in firmware database
                val allFirmware = firmwareDao.getDownloaded()
                allFirmware.forEach { firmware ->
                    val oldLocalPath = firmware.localPath ?: return@forEach
                    val oldFile = File(oldLocalPath)
                    val relativePath = try {
                        oldFile.relativeTo(sourceDir)
                    } catch (e: IllegalArgumentException) {
                        // File not under source dir, skip
                        return@forEach
                    }
                    val newLocalPath = File(newDir, relativePath.path).absolutePath
                    firmwareDao.updateLocalPath(firmware.id, newLocalPath, firmware.downloadedAt)
                }
                Logger.info(TAG, "Updated firmware database paths")
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to copy BIOS files: ${e.message}", e)
                return@withContext false
            }
        } else {
            // Moving back to internal - update database paths
            try {
                if (sourceDir.exists() && sourceDir != internalDir) {
                    sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relativePath = file.relativeTo(sourceDir)
                        val targetFile = File(internalDir, relativePath.path)
                        targetFile.parentFile?.mkdirs()
                        file.copyTo(targetFile, overwrite = true)
                    }
                    Logger.info(TAG, "Copied BIOS files back to internal directory")
                }

                val allFirmware = firmwareDao.getDownloaded()
                allFirmware.forEach { firmware ->
                    val oldLocalPath = firmware.localPath ?: return@forEach
                    val oldFile = File(oldLocalPath)
                    val relativePath = try {
                        oldFile.relativeTo(sourceDir)
                    } catch (e: IllegalArgumentException) {
                        return@forEach
                    }
                    val newLocalPath = File(internalDir, relativePath.path).absolutePath
                    firmwareDao.updateLocalPath(firmware.id, newLocalPath, firmware.downloadedAt)
                }
                Logger.info(TAG, "Updated firmware database paths to internal")
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to migrate back to internal: ${e.message}", e)
                return@withContext false
            }
        }

        // Delete old custom directory if different from new
        if (oldBiosDir != null && oldBiosDir.absolutePath != (if (newPath != null) resolveBiosDir(newPath).absolutePath else null)) {
            try {
                if (oldBiosDir.exists()) {
                    oldBiosDir.deleteRecursively()
                    Logger.info(TAG, "Deleted old BIOS directory: ${oldBiosDir.absolutePath}")
                }
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to delete old BIOS directory: ${e.message}")
            }
        }

        userPreferencesRepository.setCustomBiosPath(newPath)
        attributionRepository.markDirty(StorageCategory.BIOS)
        true
    }

    sealed class SwitchInstallResult {
        data object Success : SwitchInstallResult()
        data class ValidationFailed(val message: String) : SwitchInstallResult()
        data class Error(val message: String) : SwitchInstallResult()
        data object EdenNotInstalled : SwitchInstallResult()
        data object MissingFiles : SwitchInstallResult()
    }

    fun findInstalledEdenPackage(): String? {
        val pm = context.packageManager
        return BiosPathRegistry.EDEN_PACKAGES.firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun isEdenInstalled(): Boolean = findInstalledEdenPackage() != null

    suspend fun installSwitchFirmware(
        onProgress: ((current: Int, total: Int, fileName: String) -> Unit)? = null
    ): SwitchInstallResult = withContext(Dispatchers.IO) {
        val edenPackage = findInstalledEdenPackage()
        if (edenPackage == null) {
            Logger.info(TAG, "Switch firmware install skipped: Eden not installed")
            return@withContext SwitchInstallResult.EdenNotInstalled
        }
        val edenBasePath = BiosPathRegistry.getEdenDataPath(edenPackage)

        val switchFirmware = firmwareDao.getByPlatformSlug("switch")
        val prodKeysFile = switchFirmware.find {
            it.fileName.matches(Regex("prod.*\\.keys", RegexOption.IGNORE_CASE))
        }
        val firmwareZipFile = switchFirmware.find {
            it.fileName.endsWith(".zip", ignoreCase = true)
        }

        if (prodKeysFile?.localPath == null || firmwareZipFile?.localPath == null) {
            Logger.warn(TAG, "Switch firmware install: missing required files | prodKeys=${prodKeysFile?.localPath}, firmwareZip=${firmwareZipFile?.localPath}")
            return@withContext SwitchInstallResult.MissingFiles
        }

        val prodKeysPath = prodKeysFile.localPath!!
        val firmwareZipPath = firmwareZipFile.localPath!!

        Logger.info(TAG, "Installing Switch firmware | prodKeys=$prodKeysPath, firmwareZip=$firmwareZipPath, eden=$edenBasePath")

        try {
            FileInputStream(firmwareZipPath).use { firmwareStream ->
                val isValid = switchKeyManager.validateKeysForFirmware(prodKeysPath, firmwareStream)
                if (!isValid) {
                    Logger.warn(TAG, "Switch firmware validation failed: keys incompatible with firmware")
                    return@withContext SwitchInstallResult.ValidationFailed("prod.keys incompatible with firmware")
                }
            }

            // Create all Eden expected directories
            val edenDirs = listOf(
                "amiibo", "cache", "config", "crash_dumps", "dump",
                "keys", "load", "log", "play_time", "screenshots",
                "sdmc", "shader", "tas", "icons"
            )
            edenDirs.forEach { dir ->
                File(edenBasePath, dir).mkdirs()
            }

            val keysDir = File(edenBasePath, "keys")
            val targetProdKeys = File(keysDir, "prod.keys")
            File(prodKeysPath).copyTo(targetProdKeys, overwrite = true)
            Logger.info(TAG, "Copied prod.keys to ${targetProdKeys.absolutePath}")

            val internalKeysDir = File(context.filesDir, "bios/switch")
            internalKeysDir.mkdirs()
            val internalProdKeys = File(internalKeysDir, "prod.keys")
            File(prodKeysPath).copyTo(internalProdKeys, overwrite = true)
            Logger.info(TAG, "Copied prod.keys to internal fallback | path=${internalProdKeys.absolutePath}")

            val registeredDir = File(edenBasePath, "nand/system/Contents/registered")
            registeredDir.mkdirs()

            val ncaFiles = mutableListOf<String>()
            ZipInputStream(FileInputStream(firmwareZipPath)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".nca", ignoreCase = true)) {
                        ncaFiles.add(entry.name)
                    }
                    entry = zip.nextEntry
                }
            }

            val totalFiles = ncaFiles.size
            var extractedCount = 0

            ZipInputStream(FileInputStream(firmwareZipPath)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".nca", ignoreCase = true)) {
                        val ncaFileName = File(entry.name).name
                        onProgress?.invoke(extractedCount + 1, totalFiles, ncaFileName)

                        val outFile = File(registeredDir, ncaFileName)
                        outFile.outputStream().use { out ->
                            zip.copyTo(out)
                        }
                        extractedCount++
                    }
                    entry = zip.nextEntry
                }
            }

            Logger.info(TAG, "Extracted $extractedCount NCA files to ${registeredDir.absolutePath}")

            createDefaultEdenProfile(edenBasePath)

            SwitchInstallResult.Success
        } catch (e: Exception) {
            Logger.error(TAG, "Switch firmware install failed", e)
            SwitchInstallResult.Error(e.message ?: "Installation failed")
        }
    }

    private fun createDefaultEdenProfile(edenBasePath: String) {
        val profileDir = File(edenBasePath, "nand/system/save/8000000000000010/su/avators")
        val profileFile = File(profileDir, "profiles.dat")

        if (profileFile.exists()) {
            Logger.debug(TAG, "Eden profile already exists, skipping creation")
            return
        }

        profileDir.mkdirs()

        // profiles.dat format (0x650 bytes total):
        // [0x00-0x0F] 16 bytes header padding
        // [0x10-0xD7] User 0 (0xC8 bytes per user, 8 users max)
        //   [0x10-0x1F] UUID (16 bytes)
        //   [0x20-0x2F] UUID2 (16 bytes, same as UUID)
        //   [0x30-0x37] timestamp (8 bytes, little-endian)
        //   [0x38-0x57] username (32 bytes, null-padded UTF-8)
        //   [0x58-0xD7] extra_data (128 bytes)

        val uuid = UUID.randomUUID()
        val uuidBytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(uuid.leastSignificantBits)
            .putLong(uuid.mostSignificantBits)
            .array()

        val timestamp = System.currentTimeMillis() / 1000
        val timestampBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(timestamp)
            .array()

        val username = "Argosy".toByteArray(Charsets.UTF_8)
        val usernameBytes = ByteArray(32)
        System.arraycopy(username, 0, usernameBytes, 0, minOf(username.size, 31))

        val profileData = ByteArray(0x650)
        System.arraycopy(uuidBytes, 0, profileData, 0x10, 16)
        System.arraycopy(uuidBytes, 0, profileData, 0x20, 16)
        System.arraycopy(timestampBytes, 0, profileData, 0x30, 8)
        System.arraycopy(usernameBytes, 0, profileData, 0x38, 32)

        profileFile.writeBytes(profileData)

        val folderName = String.format("%016X%016X", uuid.mostSignificantBits, uuid.leastSignificantBits)
        Logger.info(TAG, "Created default Eden profile | uuid=$folderName, path=${profileFile.absolutePath}")

        // Create user save directory structure so save sync works immediately
        val userSaveDir = File(edenBasePath, "nand/user/save/0000000000000000/$folderName")
        userSaveDir.mkdirs()
        Logger.info(TAG, "Created Eden user save directory | path=${userSaveDir.absolutePath}")
    }

    suspend fun isSwitchFirmwareReady(): Boolean = withContext(Dispatchers.IO) {
        val switchFirmware = firmwareDao.getByPlatformSlug("switch")
        val hasProdKeys = switchFirmware.any {
            it.fileName.matches(Regex("prod.*\\.keys", RegexOption.IGNORE_CASE)) && it.localPath != null
        }
        val hasFirmwareZip = switchFirmware.any {
            it.fileName.endsWith(".zip", ignoreCase = true) && it.localPath != null
        }
        hasProdKeys && hasFirmwareZip
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
