package com.nendo.argosy.libretro

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.emulator.CoreSystemDataManager
import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.data.local.dao.CoreVersionHistoryDao
import com.nendo.argosy.data.local.entity.CoreVersionEntity
import com.nendo.argosy.data.local.entity.CoreVersionHistoryEntity
import com.nendo.argosy.data.remote.github.GitHubReleaseClient
import com.nendo.argosy.data.remote.github.ReleaseLookup
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.update.UpdateApkFiles
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.util.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LibretroCoreManager"
private val ELF_MAGIC = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
private const val CORE_HISTORY_LIMIT = 10
private const val DRAIN_BUFFER_BYTES = 64 * 1024
private const val VERSION_CHECK_TIMEOUT_MS = 10_000

internal data class CoreDownloadTarget(val url: String, val version: String?, val sha256: String?)

@Singleton
class LibretroCoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coreVersionDao: CoreVersionDao,
    private val coreVersionHistoryDao: CoreVersionHistoryDao,
    private val coreSystemDataManager: CoreSystemDataManager,
    private val compatCoreCache: CompatCoreCache,
    private val attributionRepository: StorageAttributionRepository,
    private val releaseClient: GitHubReleaseClient
) {
    private val downloadedCoresDir = File(context.filesDir, "libretro/cores").apply { mkdirs() }
    private val coreHistoryDir = File(context.filesDir, "libretro/core_history").apply { mkdirs() }
    private val nativeLibDir = context.applicationInfo.nativeLibraryDir
    private val abiMarkerFile = File(downloadedCoresDir, ".abi")

    init {
        Log.i(TAG, "Device ABI: ${LibretroBuildbot.deviceAbi}, nativeLibDir: $nativeLibDir")
        Log.i(TAG, "Buildbot URL: ${LibretroBuildbot.baseUrl}")
    }

    suspend fun migrateAbiIfNeeded() {
        val storedAbi = if (abiMarkerFile.exists()) abiMarkerFile.readText().trim() else null
        val currentAbi = LibretroBuildbot.deviceAbi

        if (storedAbi != currentAbi) {
            Log.i(TAG, "ABI changed from $storedAbi to $currentAbi, clearing downloaded cores")
            downloadedCoresDir.listFiles()
                ?.filter { it.name != ".abi" }
                ?.forEach { it.delete() }
            coreVersionDao.deleteAll()
            abiMarkerFile.writeText(currentAbi)
            attributionRepository.markDirty(StorageCategory.CORES_SYSTEM)
        }
    }

    fun getCorePathForPlatform(platformSlug: String): String? =
        getCorePathForPlatform(platformSlug, null)

    fun getCorePathForPlatform(platformSlug: String, selectedCoreId: String?): String? {
        val coreInfo = resolveCoreForPlatform(platformSlug, selectedCoreId) ?: return null
        return getCorePath(coreInfo.fileName)
    }

    private fun resolveCoreForPlatform(
        platformSlug: String,
        selectedCoreId: String?
    ): LibretroCoreRegistry.CoreInfo? {
        if (selectedCoreId == null) {
            return LibretroCoreRegistry.getDefaultCoreForPlatform(platformSlug)
        }
        val known = LibretroCoreRegistry.getCoreById(selectedCoreId)
        if (known != null && platformSlug in known.platforms) return known
        val displayName = com.nendo.argosy.data.emulator.EmulatorRegistry
            .getCoresForPlatform(platformSlug)
            .firstOrNull { it.id == selectedCoreId }
            ?.displayName
            ?: selectedCoreId
        return LibretroCoreRegistry.CoreInfo(
            coreId = selectedCoreId,
            fileName = "${selectedCoreId}_libretro_android.so",
            displayName = displayName,
            platforms = setOf(platformSlug)
        )
    }

    fun getCorePathForCoreId(coreId: String): String? {
        val coreInfo = LibretroCoreRegistry.getCoreById(coreId) ?: return null
        return getCorePath(coreInfo.fileName)
    }

    fun isCoreAvailable(platformSlug: String): Boolean =
        getCorePathForPlatform(platformSlug) != null

    fun isCoreInstalled(coreId: String): Boolean {
        val coreInfo = LibretroCoreRegistry.getCoreById(coreId) ?: return false
        return getCorePath(coreInfo.fileName) != null
    }

    fun isPlatformSupported(platformSlug: String): Boolean =
        LibretroCoreRegistry.isPlatformSupported(platformSlug)

    fun getInstalledCores(): List<LibretroCoreRegistry.CoreInfo> {
        val downloadedFiles = downloadedCoresDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        return LibretroCoreRegistry.getAllCores().filter { core ->
            core.fileName in downloadedFiles || File(nativeLibDir, "lib${core.fileName}").exists()
        }
    }

    fun getMissingCoresForPlatforms(platformSlugs: Set<String>): List<LibretroCoreRegistry.CoreInfo> {
        val neededCores = platformSlugs.mapNotNull { LibretroCoreRegistry.getDefaultCoreForPlatform(it) }
            .distinctBy { it.coreId }
        return neededCores.filter { !isCoreInstalled(it.coreId) }
    }

    suspend fun downloadCoreForPlatform(platformSlug: String): Result<String> =
        downloadCoreForPlatform(platformSlug, null)

    suspend fun downloadCoreForPlatform(
        platformSlug: String,
        selectedCoreId: String?,
        onProgress: ((fraction: Float, zipBytes: Long) -> Unit)? = null
    ): Result<String> {
        val coreInfo = resolveCoreForPlatform(platformSlug, selectedCoreId)
            ?: return Result.failure(IllegalArgumentException("No core registered for platform $platformSlug"))
        return downloadSizedCore(coreInfo, onProgress).map { it.absolutePath }
    }

    suspend fun downloadCoreById(coreId: String): Result<File> {
        val coreInfo = LibretroCoreRegistry.getCoreById(coreId)
            ?: return Result.failure(IllegalArgumentException("Unknown core: $coreId"))
        return downloadCore(coreInfo)
    }

    private fun getCorePath(fileName: String): String? {
        val downloadedCore = File(downloadedCoresDir, fileName)
        if (downloadedCore.exists()) {
            val coreInfo = LibretroCoreRegistry.getCoreByFileName(fileName)
            val minSize = (coreInfo?.estimatedSizeBytes ?: 0L) / 4
            if (minSize > 0 && downloadedCore.length() < minSize) {
                Log.w(TAG, "Core $fileName is ${downloadedCore.length()} bytes (estimated >=$minSize). " +
                    "Using anyway; redownload from Core Management if it fails to load.")
            }
            ensureExecutable(downloadedCore)
            Log.d(TAG, "Using downloaded core: ${downloadedCore.absolutePath} (${downloadedCore.length()} bytes)")
            return downloadedCore.absolutePath
        }

        val bundledCore = File(nativeLibDir, "lib$fileName")
        if (bundledCore.exists()) {
            Log.d(TAG, "Using bundled core: ${bundledCore.absolutePath}")
            return bundledCore.absolutePath
        }

        Log.w(TAG, "Core not found: $fileName (checked $downloadedCore and $bundledCore)")
        return null
    }

    private fun ensureExecutable(file: File) {
        if (!file.canExecute()) {
            file.setExecutable(true)
        }
    }

    suspend fun downloadCore(
        coreInfo: LibretroCoreRegistry.CoreInfo,
        onProgress: ((Float) -> Unit)? = null
    ): Result<File> = downloadSizedCore(coreInfo, onProgress?.let { report -> { fraction, _ -> report(fraction) } })

    private suspend fun downloadSizedCore(
        coreInfo: LibretroCoreRegistry.CoreInfo,
        onProgress: ((fraction: Float, zipBytes: Long) -> Unit)?
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetFile = File(downloadedCoresDir, coreInfo.fileName)
                if (targetFile.exists()) {
                    val existingHash = computeFileHash(targetFile)
                    compatCoreCache.storeCompatCore(targetFile, existingHash)
                    archiveToHistory(coreInfo.coreId, targetFile, existingHash)
                }
                val target = resolveDownload(coreInfo)
                val zipUrl = target.url

                Log.i(TAG, "Downloading ${coreInfo.displayName}: $zipUrl")
                Log.i(TAG, "Device ABI: ${LibretroBuildbot.deviceAbi}")

                val url = URL(zipUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorMessage = "HTTP $responseCode: ${connection.responseMessage}"
                    Log.e(TAG, "Core download failed: $errorMessage for $zipUrl")
                    val failure = when {
                        responseCode == HttpURLConnection.HTTP_NOT_FOUND ->
                            CoreDownloadFailure.NotPublished
                        responseCode >= 500 -> CoreDownloadFailure.ServerError
                        else -> CoreDownloadFailure.Unknown("Core not available: $errorMessage")
                    }
                    throw CoreDownloadException(failure, "Core not available: $errorMessage")
                }

                val version = target.version
                    ?: connection.getHeaderField("Last-Modified")
                    ?: connection.contentLengthLong.toString()

                val zipLength = connection.contentLengthLong
                val zipDigest = MessageDigest.getInstance("SHA-256")
                val hashedInput = DigestInputStream(connection.inputStream, zipDigest)
                val countingInput = object : java.io.FilterInputStream(hashedInput) {
                    private var read = 0L
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = super.read(b, off, len)
                        if (n > 0 && zipLength > 0) {
                            read += n
                            onProgress?.invoke((read.toFloat() / zipLength).coerceIn(0f, 1f), zipLength)
                        }
                        return n
                    }
                }
                val partFile = File(downloadedCoresDir, "${coreInfo.fileName}.part")
                try {
                    countingInput.use { input ->
                        ZipInputStream(input).use { zip ->
                            val entry = zip.nextEntry
                            if (entry != null && entry.name == coreInfo.fileName) {
                                partFile.outputStream().use { output ->
                                    zip.copyTo(output)
                                }
                                if (entry.size > 0 && partFile.length() != entry.size) {
                                    throw CoreDownloadException(
                                        CoreDownloadFailure.Corrupted,
                                        "Downloaded core is truncated: ${partFile.length()} of ${entry.size} bytes"
                                    )
                                }
                            } else {
                                throw IllegalStateException("Expected ${coreInfo.fileName} in zip, got ${entry?.name}")
                            }
                            if (target.sha256 != null) {
                                val drain = ByteArray(DRAIN_BUFFER_BYTES)
                                while (countingInput.read(drain) >= 0) Unit
                                val actual = zipDigest.digest().joinToString("") { "%02x".format(it) }
                                if (!actual.equals(target.sha256, ignoreCase = true)) {
                                    throw CoreDownloadException(
                                        CoreDownloadFailure.Corrupted,
                                        "Downloaded core does not match its release checksum"
                                    )
                                }
                            }
                        }
                    }
                    connection.disconnect()

                    val partSize = partFile.length()
                    val minExpectedSize = coreInfo.estimatedSizeBytes / 4
                    if (partSize < minExpectedSize || !isElf(partFile)) {
                        throw CoreDownloadException(
                            CoreDownloadFailure.Corrupted,
                            "Downloaded core is corrupted: ${coreInfo.fileName} is $partSize bytes, " +
                                "expected an ELF library of at least $minExpectedSize bytes"
                        )
                    }
                    if (!partFile.renameTo(targetFile)) {
                        throw CoreDownloadException(
                            CoreDownloadFailure.Unknown("Could not install ${coreInfo.fileName}"),
                            "Could not move the downloaded core into place"
                        )
                    }
                } finally {
                    partFile.delete()
                }
                val fileSize = targetFile.length()

                if (!targetFile.setExecutable(true)) {
                    Log.w(TAG, "Failed to set executable permission")
                }

                coreVersionDao.upsert(
                    CoreVersionEntity(
                        coreId = coreInfo.coreId,
                        installedVersion = version,
                        latestVersion = version,
                        installedAt = Instant.now(),
                        lastCheckedAt = Instant.now(),
                        updateAvailable = false,
                        installedHash = computeFileHash(targetFile),
                        installedSize = fileSize,
                        corrupt = false
                    )
                )

                Log.i(TAG, "Downloaded ${coreInfo.displayName}: ${targetFile.length()} bytes, version=$version")

                if (coreSystemDataManager.needsSystemData(coreInfo.coreId, systemDir)) {
                    Log.i(TAG, "Downloading system data for ${coreInfo.coreId}")
                    coreSystemDataManager.ensureCoreSystemData(coreInfo.coreId, systemDir)
                }

                attributionRepository.markDirty(StorageCategory.CORES_SYSTEM)
                targetFile
            }
        }

    internal suspend fun resolveDownload(
        coreInfo: LibretroCoreRegistry.CoreInfo,
        abi: String = LibretroBuildbot.deviceAbi
    ): CoreDownloadTarget =
        when (val source = coreInfo.source) {
            CoreSource.Buildbot ->
                CoreDownloadTarget("${LibretroBuildbot.baseUrlFor(abi)}/${coreInfo.fileName}.zip", version = null, sha256 = null)
            is CoreSource.GitHubRelease -> {
                val release = when (val lookup = releaseClient.latestRelease(source.owner, source.repo)) {
                    is ReleaseLookup.Found -> lookup.release
                    ReleaseLookup.NotFound -> throw CoreDownloadException(
                        CoreDownloadFailure.NotPublished,
                        "${source.owner}/${source.repo} has no release"
                    )
                    is ReleaseLookup.RateLimited, is ReleaseLookup.Failed -> throw CoreDownloadException(
                        CoreDownloadFailure.ServerError,
                        "GitHub did not answer the release lookup for ${source.owner}/${source.repo}"
                    )
                }
                val asset = release.assets.firstOrNull { source.assetNameMatches(it.name, abi) }
                    ?: throw CoreDownloadException(
                        CoreDownloadFailure.NotPublished,
                        "${release.tagName} of ${source.owner}/${source.repo} has no $abi build"
                    )
                CoreDownloadTarget(asset.downloadUrl, release.tagName, UpdateApkFiles.sha256Of(asset.digest))
            }
        }

    /**
     * The version a fresh download of [coreInfo] would install: the buildbot zip's Last-Modified,
     * or the GitHub release tag. Null when the source could not be asked.
     */
    suspend fun latestVersion(coreInfo: LibretroCoreRegistry.CoreInfo): String? = withContext(Dispatchers.IO) {
        when (coreInfo.source) {
            CoreSource.Buildbot -> buildbotVersion(coreInfo.fileName)
            is CoreSource.GitHubRelease -> runCatching { resolveDownload(coreInfo).version }
                .onFailure { Log.w(TAG, "Release check failed for ${coreInfo.coreId}: ${it.message}") }
                .getOrNull()
        }
    }

    private fun buildbotVersion(fileName: String): String? = try {
        val connection = URL("${LibretroBuildbot.baseUrl}/$fileName.zip").openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = VERSION_CHECK_TIMEOUT_MS
        connection.readTimeout = VERSION_CHECK_TIMEOUT_MS
        try {
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.getHeaderField("Last-Modified") ?: connection.contentLengthLong.toString()
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to fetch version for $fileName: ${e.message}")
        null
    }

    suspend fun resolveNetplayCorePath(coreId: String, hostCoreHash: String): NetplayCoreResolution {
        val currentPath = getCorePathForCoreId(coreId)
        if (currentPath != null) {
            val currentHash = computeFileHash(File(currentPath))
            if (currentHash.equals(hostCoreHash, ignoreCase = true)) {
                return NetplayCoreResolution.Matched(currentPath)
            }
        }

        val coreInfo = LibretroCoreRegistry.getCoreById(coreId)
        if (coreInfo != null) {
            try {
                val result = withTimeoutOrNull(15_000) { downloadCore(coreInfo) }
                if (result?.isSuccess == true) {
                    val updatedPath = getCorePathForCoreId(coreId)
                    if (updatedPath != null) {
                        val updatedHash = computeFileHash(File(updatedPath))
                        if (updatedHash.equals(hostCoreHash, ignoreCase = true)) {
                            return NetplayCoreResolution.Updated(updatedPath)
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        val compatPath = compatCoreCache.getCompatCorePath(hostCoreHash)
        if (compatPath != null) {
            return NetplayCoreResolution.CompatFound(compatPath)
        }

        return NetplayCoreResolution.Unresolvable
    }

    private suspend fun archiveToHistory(coreId: String, file: File, hash: String) {
        val version = coreVersionDao.getByCoreId(coreId)?.installedVersion ?: return
        if (coreVersionHistoryDao.getByCoreAndVersion(coreId, version) != null) return
        val dir = File(coreHistoryDir, coreId).apply { mkdirs() }
        val archived = File(dir, "$hash.so")
        file.copyTo(archived, overwrite = true)
        coreVersionHistoryDao.insert(
            CoreVersionHistoryEntity(
                coreId = coreId,
                version = version,
                hash = hash,
                size = file.length(),
                fileName = archived.name,
                archivedAt = Instant.now()
            )
        )
        coreVersionHistoryDao.getByCore(coreId).drop(CORE_HISTORY_LIMIT).forEach { stale ->
            File(dir, stale.fileName).delete()
            coreVersionHistoryDao.deleteById(stale.id)
        }
        attributionRepository.markDirty(StorageCategory.CORES_SYSTEM)
    }

    suspend fun getCoreHistory(coreId: String): List<CoreVersionHistoryEntity> =
        withContext(Dispatchers.IO) { coreVersionHistoryDao.getByCore(coreId) }

    suspend fun rollbackToHistory(coreId: String, history: CoreVersionHistoryEntity): Boolean =
        withContext(Dispatchers.IO) {
            val coreInfo = LibretroCoreRegistry.getCoreById(coreId) ?: return@withContext false
            val archived = File(File(coreHistoryDir, coreId), history.fileName)
            if (!archived.exists()) return@withContext false
            val crashedVersion = coreVersionDao.getByCoreId(coreId)?.installedVersion
            val target = File(downloadedCoresDir, coreInfo.fileName)
            archived.copyTo(target, overwrite = true)
            target.setExecutable(true)
            coreVersionDao.upsert(
                CoreVersionEntity(
                    coreId = coreId,
                    installedVersion = history.version,
                    latestVersion = history.version,
                    installedAt = Instant.now(),
                    lastCheckedAt = Instant.now(),
                    updateAvailable = false,
                    installedHash = history.hash,
                    installedSize = history.size,
                    corrupt = false,
                    blockedVersion = crashedVersion
                )
            )
            attributionRepository.markDirty(StorageCategory.CORES_SYSTEM)
            true
        }

    suspend fun blockInstalledVersion(coreId: String) = withContext(Dispatchers.IO) {
        val installed = coreVersionDao.getByCoreId(coreId)?.installedVersion ?: return@withContext
        coreVersionDao.setBlockedVersion(coreId, installed)
    }

    suspend fun verifyDownloadedCore(coreInfo: LibretroCoreRegistry.CoreInfo): Boolean? =
        withContext(Dispatchers.IO) {
            val file = File(downloadedCoresDir, coreInfo.fileName)
            if (!file.exists()) return@withContext null
            val baseline = coreVersionDao.getByCoreId(coreInfo.coreId)?.installedHash
                ?: return@withContext null
            val intact = computeFileHash(file).equals(baseline, ignoreCase = true)
            coreVersionDao.setCorrupt(coreInfo.coreId, !intact)
            intact
        }

    private fun isElf(file: File): Boolean {
        val magic = ByteArray(ELF_MAGIC.size)
        val read = file.inputStream().use { it.read(magic) }
        return read == ELF_MAGIC.size && magic.contentEquals(ELF_MAGIC)
    }

    private fun computeFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private val systemDir: File
        get() = AppPaths.libretroSystemDir(context.filesDir).apply { mkdirs() }

    fun getDownloadedCores(): List<LibretroCoreRegistry.CoreInfo> {
        val downloadedFiles = downloadedCoresDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        return downloadedFiles.mapNotNull { LibretroCoreRegistry.getCoreByFileName(it) }
    }

    suspend fun deleteCore(coreId: String): Boolean {
        val coreInfo = LibretroCoreRegistry.getCoreById(coreId) ?: return false
        val deleted = File(downloadedCoresDir, coreInfo.fileName).delete()
        if (deleted) {
            coreVersionDao.delete(coreId)
            attributionRepository.markDirty(StorageCategory.CORES_SYSTEM)
        }
        return deleted
    }

    fun getCoresDir(): File = downloadedCoresDir

    suspend fun getPendingCoreUpdates(): List<PendingCoreUpdate> =
        withContext(Dispatchers.IO) {
            coreVersionDao.getWithUpdatesAvailable().mapNotNull(::pendingUpdateOf)
        }

    /**
     * Downloads the latest build of every core [getPendingCoreUpdates] reports, one at a time.
     * [onCoreStarted] receives each core with its 1-based position and the total before its
     * download begins.
     */
    suspend fun updateInstalledCores(
        onCoreStarted: (core: LibretroCoreRegistry.CoreInfo, position: Int, total: Int) -> Unit
    ): CoreBulkUpdateResult {
        val pending = getPendingCoreUpdates().map { it.coreInfo }
        val updated = mutableListOf<LibretroCoreRegistry.CoreInfo>()
        val failed = mutableListOf<LibretroCoreRegistry.CoreInfo>()
        pending.forEachIndexed { index, coreInfo ->
            onCoreStarted(coreInfo, index + 1, pending.size)
            downloadCore(coreInfo).fold(
                onSuccess = { updated += coreInfo },
                onFailure = { error ->
                    Log.w(TAG, "Bulk update failed for ${coreInfo.coreId}: ${error.message}")
                    failed += coreInfo
                }
            )
        }
        return CoreBulkUpdateResult(updated = updated, failed = failed)
    }

    companion object {
        /**
         * The one rule for whether [latestVersion] is an update over [installedVersion]: both are
         * known, they differ, and [latestVersion] is not the build the user rolled back from.
         */
        fun isUpdateAvailable(
            installedVersion: String?,
            latestVersion: String?,
            blockedVersion: String?
        ): Boolean =
            installedVersion != null &&
                latestVersion != null &&
                installedVersion != latestVersion &&
                latestVersion != blockedVersion

        fun pendingUpdateOf(row: CoreVersionEntity): PendingCoreUpdate? {
            val installed = row.installedVersion ?: return null
            val latest = row.latestVersion ?: return null
            if (!row.updateAvailable || !isUpdateAvailable(installed, latest, row.blockedVersion)) {
                return null
            }
            val coreInfo = LibretroCoreRegistry.getCoreById(row.coreId) ?: return null
            return PendingCoreUpdate(coreInfo, installedVersion = installed, latestVersion = latest)
        }
    }
}

data class PendingCoreUpdate(
    val coreInfo: LibretroCoreRegistry.CoreInfo,
    val installedVersion: String,
    val latestVersion: String
)

data class CoreBulkUpdateResult(
    val updated: List<LibretroCoreRegistry.CoreInfo>,
    val failed: List<LibretroCoreRegistry.CoreInfo>
)
