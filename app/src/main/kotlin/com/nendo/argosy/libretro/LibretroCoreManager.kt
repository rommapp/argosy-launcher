package com.nendo.argosy.libretro

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.data.local.entity.CoreVersionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LibretroCoreManager"
private const val BUILDBOT_BASE = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"

@Singleton
class LibretroCoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coreVersionDao: CoreVersionDao
) {
    private val downloadedCoresDir = File(context.filesDir, "libretro/cores").apply { mkdirs() }
    private val nativeLibDir = context.applicationInfo.nativeLibraryDir

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
            ensureExecutable(downloadedCore)
            return downloadedCore.absolutePath
        }

        val bundledCore = File(nativeLibDir, "lib$fileName")
        if (bundledCore.exists()) {
            return bundledCore.absolutePath
        }

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
                val zipUrl = "$BUILDBOT_BASE/${coreInfo.fileName}.zip"

                Log.i(TAG, "Downloading ${coreInfo.displayName}: $zipUrl")

                val url = URL(zipUrl)
                val connection = url.openConnection() as HttpURLConnection
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
                targetFile
            }
        }

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
}
