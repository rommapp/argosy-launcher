package com.nendo.argosy.libretro

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.emulator.CoreSystemDataManager
import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.data.local.entity.CoreVersionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LibretroCoreManager"

@Singleton
class LibretroCoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coreVersionDao: CoreVersionDao,
    private val coreSystemDataManager: CoreSystemDataManager,
    private val compatCoreCache: CompatCoreCache
) {
    private val downloadedCoresDir = File(context.filesDir, "libretro/cores").apply { mkdirs() }
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
        }
    }

    fun getCorePathForPlatform(platformSlug: String): String? {
        val coreInfo = LibretroCoreRegistry.getDefaultCoreForPlatform(platformSlug) ?: return null
        return getCorePath(coreInfo.fileName)
    }

    fun getCorePathForPlatform(platformSlug: String, selectedCoreId: String?): String? {
        val coreInfo = if (selectedCoreId != null) {
            val selectedCore = LibretroCoreRegistry.getCoreById(selectedCoreId)
            if (selectedCore != null && platformSlug in selectedCore.platforms) {
                selectedCore
            } else {
                LibretroCoreRegistry.getDefaultCoreForPlatform(platformSlug)
            }
        } else {
            LibretroCoreRegistry.getDefaultCoreForPlatform(platformSlug)
        } ?: return null
        return getCorePath(coreInfo.fileName)
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

    suspend fun downloadCoreForPlatform(platformSlug: String): String? {
        val coreInfo = LibretroCoreRegistry.getDefaultCoreForPlatform(platformSlug) ?: return null
        return downloadCore(coreInfo).fold(
            onSuccess = { it.absolutePath },
            onFailure = { e ->
                Log.e(TAG, "Failed to download core: ${e.message}", e)
                null
            }
        )
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
                Log.w(TAG, "Corrupted core detected: $fileName is ${downloadedCore.length()} bytes, " +
                    "expected at least $minSize. Deleting.")
                downloadedCore.delete()
            } else {
                ensureExecutable(downloadedCore)
                Log.d(TAG, "Using downloaded core: ${downloadedCore.absolutePath} (${downloadedCore.length()} bytes)")
                return downloadedCore.absolutePath
            }
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

    suspend fun downloadCore(coreInfo: LibretroCoreRegistry.CoreInfo): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetFile = File(downloadedCoresDir, coreInfo.fileName)
                if (targetFile.exists()) {
                    val existingHash = computeFileHash(targetFile)
                    compatCoreCache.storeCompatCore(targetFile, existingHash)
                }
                val zipUrl = "${LibretroBuildbot.baseUrl}/${coreInfo.fileName}.zip"

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
                    throw IllegalStateException("Core not available: $errorMessage")
                }

                val version = connection.getHeaderField("Last-Modified")
                    ?: connection.contentLengthLong.toString()

                connection.inputStream.use { input ->
                    ZipInputStream(input).use { zip ->
                        val entry = zip.nextEntry
                        if (entry != null && entry.name == coreInfo.fileName) {
                            targetFile.outputStream().use { output ->
                                zip.copyTo(output)
                            }
                        } else {
                            throw IllegalStateException("Expected ${coreInfo.fileName} in zip, got ${entry?.name}")
                        }
                    }
                }
                connection.disconnect()

                val fileSize = targetFile.length()
                val minExpectedSize = coreInfo.estimatedSizeBytes / 4
                if (fileSize < minExpectedSize) {
                    targetFile.delete()
                    throw IllegalStateException(
                        "Downloaded core is corrupted: ${coreInfo.fileName} is $fileSize bytes, " +
                            "expected at least $minExpectedSize bytes"
                    )
                }

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
                        updateAvailable = false
                    )
                )

                Log.i(TAG, "Downloaded ${coreInfo.displayName}: ${targetFile.length()} bytes, version=$version")

                if (coreSystemDataManager.needsSystemData(coreInfo.coreId, systemDir)) {
                    Log.i(TAG, "Downloading system data for ${coreInfo.coreId}")
                    coreSystemDataManager.ensureCoreSystemData(coreInfo.coreId, systemDir)
                }

                targetFile
            }
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
        get() = File(context.filesDir, "libretro/system").apply { mkdirs() }

    fun getDownloadedCores(): List<LibretroCoreRegistry.CoreInfo> {
        val downloadedFiles = downloadedCoresDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        return downloadedFiles.mapNotNull { LibretroCoreRegistry.getCoreByFileName(it) }
    }

    suspend fun deleteCore(coreId: String): Boolean {
        val coreInfo = LibretroCoreRegistry.getCoreById(coreId) ?: return false
        val deleted = File(downloadedCoresDir, coreInfo.fileName).delete()
        if (deleted) {
            coreVersionDao.delete(coreId)
        }
        return deleted
    }

    fun getCoresDir(): File = downloadedCoresDir

    suspend fun checkAndUpdateCoresIfDue() {
        val all = coreVersionDao.getAll()
        if (all.isEmpty()) return

        val lastCheck = all.mapNotNull { it.lastCheckedAt }.maxOrNull()
        val dayAgo = Instant.now().minus(Duration.ofHours(24))
        if (lastCheck != null && lastCheck.isAfter(dayAgo)) {
            Log.d(TAG, "Core update check skipped (last check: $lastCheck)")
            return
        }

        Log.i(TAG, "Checking for core updates...")
        val outdated = checkForCoreUpdates()
        if (outdated.isEmpty()) {
            Log.i(TAG, "All cores up to date")
            return
        }

        Log.i(TAG, "${outdated.size} core(s) have updates available, downloading...")
        val updated = updateInstalledCores()
        Log.i(TAG, "Updated $updated core(s)")
    }

    suspend fun checkForCoreUpdates(): List<CoreVersionEntity> {
        val installed = coreVersionDao.getAll()
        if (installed.isEmpty()) return emptyList()

        val updated = mutableListOf<CoreVersionEntity>()
        for (core in installed) {
            try {
                val coreInfo = LibretroCoreRegistry.getCoreById(core.coreId) ?: continue
                val zipUrl = "${LibretroBuildbot.baseUrl}/${coreInfo.fileName}.zip"
                val latestVersion = headLastModified(zipUrl) ?: continue
                val needsUpdate = core.installedVersion != latestVersion
                coreVersionDao.updateVersionCheck(
                    core.coreId, latestVersion, Instant.now(), needsUpdate
                )
                if (needsUpdate) {
                    updated.add(core.copy(latestVersion = latestVersion, updateAvailable = true))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check update for ${core.coreId}: ${e.message}")
            }
        }
        return updated
    }

    suspend fun updateInstalledCores(): Int {
        val outdated = coreVersionDao.getWithUpdatesAvailable()
        var count = 0
        for (core in outdated) {
            val coreInfo = LibretroCoreRegistry.getCoreById(core.coreId) ?: continue
            val result = downloadCore(coreInfo)
            if (result.isSuccess) count++
        }
        return count
    }

    private suspend fun headLastModified(urlString: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            try {
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.getHeaderField("Last-Modified")
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
