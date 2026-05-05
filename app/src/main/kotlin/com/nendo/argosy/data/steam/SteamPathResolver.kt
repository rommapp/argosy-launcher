package com.nendo.argosy.data.steam

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.StorageVolumeDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SteamPathResolver"
private const val STEAM_PLATFORM_DIR = "steam"
private const val GN_PACKAGE = "app.gamenative"
private const val DOWNLOAD_INFO_DIR = ".DownloadInfo"

data class SteamInstallVolume(
    val path: String,
    val label: String,
    val freeBytes: Long,
    val hasGnPath: Boolean,
    val target: SteamInstallTarget
)

@Singleton
class SteamPathResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val androidDataAccessor: AndroidDataAccessor,
    private val storageVolumeDetector: StorageVolumeDetector,
    private val gnInstallProbe: GnInstallProbe
) {

    suspend fun getConfiguredTarget(): SteamInstallTarget {
        val raw = preferencesRepository.userPreferences.first().steamInstallVolume
        return SteamInstallTarget.fromPreferenceValue(raw)
    }

    fun resolveBaseForTarget(target: SteamInstallTarget): String? {
        val result = gnInstallProbe.probe(target)
        val pkgRoot = result.resolvedPackageRoot ?: fallbackPackageRoot(target)
        if (!result.writable) {
            Log.w(TAG, "Target $target probe not writable; using $pkgRoot anyway (explicit selection)")
        }
        return pkgRoot?.let { "$it/files" }
    }

    private fun fallbackPackageRoot(target: SteamInstallTarget): String? {
        val root = when (target) {
            is SteamInstallTarget.Internal -> Environment.getExternalStorageDirectory().absolutePath
            is SteamInstallTarget.CustomVolume -> target.path
            is SteamInstallTarget.ExternalAuto -> return null
        }
        val raw = "$root/Android/data/$GN_PACKAGE"
        return if (target is SteamInstallTarget.Internal) androidDataAccessor.transformPath(raw) else raw
    }

    suspend fun getInstallDirByName(dirName: String): File {
        val steamBase = getResolvedSteamBase()
        if (steamBase != null) {
            return File("$steamBase/$dirName")
        }
        val gnBasePath = findGnStoragePath()
        if (gnBasePath != null) {
            return File("$gnBasePath/Steam/steamapps/common/$dirName")
        }
        val basePath = externalFilesDir() ?: internalFilesDir()
        return File(basePath, "$STEAM_PLATFORM_DIR/$dirName")
    }

    suspend fun snapshotInstallDirAtEnqueue(appId: Long, steamInstallDir: String?): File {
        return if (steamInstallDir != null) getInstallDirByName(steamInstallDir) else getInstallDir(appId)
    }

    suspend fun getInstallDir(appId: Long): File {
        val steamBase = getResolvedSteamBase()
        if (steamBase != null) {
            val game = gameDao.getBySteamAppId(appId)
            val leaf = preferredLeafName(game) ?: appId.toString()
            return File("$steamBase/$leaf")
        }
        val gnPath = findGnInstallPath(appId)
        if (gnPath != null) return gnPath

        val basePath = externalFilesDir() ?: internalFilesDir()
        return File(basePath, "$STEAM_PLATFORM_DIR/$appId")
    }

    suspend fun getSteamDir(): File {
        val prefs = preferencesRepository.userPreferences.first()
        val basePath = prefs.romStoragePath
            ?: externalFilesDir()
            ?: internalFilesDir()
        return File(basePath, STEAM_PLATFORM_DIR)
    }

    suspend fun getResolvedSteamBase(): String? {
        val steam = platformDao.getById(LocalPlatformIds.STEAM)
        steam?.customRomPath?.takeIf { it.isNotBlank() }?.let { return it }
        val romsRoot = preferencesRepository.userPreferences.first().romStoragePath
        if (romsRoot.isNullOrBlank()) return null
        val slug = steam?.slug ?: STEAM_PLATFORM_DIR
        return "$romsRoot/$slug"
    }

    private fun preferredLeafName(game: com.nendo.argosy.data.local.entity.GameEntity?): String? {
        if (game == null) return null
        game.steamInstallDir?.takeIf { it.isNotBlank() }?.let { return sanitizeGameName(it) }
        return game.title.takeIf { it.isNotBlank() }?.let { sanitizeGameName(it) }
    }

    suspend fun isGameInstalled(appId: Long): Boolean {
        val game = gameDao.getBySteamAppId(appId)
        val path = game?.localPath ?: return false
        return File(path, ".download_complete").exists() || androidDataAccessor.exists("$path/.download_complete")
    }

    fun findGnStoragePath(): String? = findAllGnStoragePaths().firstOrNull()

    fun findAllGnStoragePaths(): List<String> {
        val primary = Environment.getExternalStorageDirectory().absolutePath
        val removables = storageVolumeDetector.detectStorageVolumes().map { it.path }
        val roots = (removables + primary).distinct()

        val results = mutableListOf<String>()
        for (root in roots) {
            val packageDir = "$root/Android/data/$GN_PACKAGE"
            val basePath = "$packageDir/files"

            if (File(packageDir).exists()) {
                results += basePath
            } else if (androidDataAccessor.exists(packageDir)) {
                results += androidDataAccessor.transformPath(basePath)
            }
        }
        return results.distinct()
    }

    fun sanitizeGameName(name: String): String {
        return name.replace(Regex("[<>:\"/\\\\|?*]"), "_").trim()
    }

    fun getAvailableBytes(dir: File): Long? {
        return try {
            var check: File? = dir
            while (check != null) {
                try {
                    return android.os.StatFs(check.absolutePath).availableBytes
                } catch (_: IllegalArgumentException) {
                    check = check.parentFile
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun getDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var totalSize = 0L
        var fileCount = 0
        dir.walkTopDown()
            .onEnter { it.name != ".DepotDownloader" && it.name != DOWNLOAD_INFO_DIR }
            .forEach { file ->
                if (file.isFile && !file.name.startsWith(".download_")) {
                    totalSize += file.length()
                    fileCount++
                }
            }
        if (fileCount > 0 || totalSize > 0) {
            Log.v(TAG, "getDirectorySize(${dir.name}): $fileCount files, ${totalSize / 1024 / 1024}MB")
        }
        return totalSize
    }

    fun moveDirectory(source: File, destination: File): Boolean {
        try {
            if (source.renameTo(destination)) {
                Log.d(TAG, "Moved directory via rename")
                return true
            }

            Log.d(TAG, "Rename failed, copying files to destination...")
            destination.mkdirs()

            var filesCopied = 0
            var bytesCopied = 0L

            source.walkTopDown().forEach { file ->
                val relativePath = file.relativeTo(source).path
                val destFile = File(destination, relativePath)

                if (file.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    file.copyTo(destFile, overwrite = true)
                    filesCopied++
                    bytesCopied += file.length()

                    if (filesCopied % 100 == 0) {
                        Log.d(TAG, "Copied $filesCopied files (${bytesCopied / 1024 / 1024}MB)...")
                    }
                }
            }

            Log.d(TAG, "Copied $filesCopied files (${bytesCopied / 1024 / 1024}MB) to destination")

            val destSize = getDirectorySize(destination)
            if (destSize == 0L) {
                Log.e(TAG, "Destination directory is empty after copy!")
                return false
            }

            source.deleteRecursively()
            Log.d(TAG, "Deleted source directory after move")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move directory: ${e.message}", e)
            return false
        }
    }

    fun getAvailableVolumes(): List<SteamInstallVolume> {
        return storageVolumeDetector.detectStorageVolumes().map { vol ->
            val target = SteamInstallTarget.CustomVolume(vol.path)
            SteamInstallVolume(
                path = vol.path,
                label = vol.displayName,
                freeBytes = vol.availableBytes,
                hasGnPath = gnInstallProbe.probe(target).writable,
                target = target
            )
        }
    }

    private suspend fun findGnInstallPath(appId: Long): File? {
        val gameName = gameDao.getBySteamAppId(appId)?.title ?: return null
        val gnBasePath = findGnStoragePath() ?: return null
        val sanitized = sanitizeGameName(gameName)
        return File("$gnBasePath/Steam/steamapps/common/$sanitized")
    }

    private fun externalFilesDir(): String? {
        return context.getExternalFilesDir(null)?.absolutePath
    }

    private fun internalFilesDir(): String {
        return context.filesDir.absolutePath
    }
}
