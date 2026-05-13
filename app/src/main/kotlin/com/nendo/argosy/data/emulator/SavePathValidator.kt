package com.nendo.argosy.data.emulator

import android.os.Build
import android.os.Environment
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavePathValidator @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val fileAccessLayer: FileAccessLayer,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry
) {
    companion object {
        private const val TAG = "SavePathValidator"
    }

    sealed class Result {
        data object Valid : Result()
        data object PermissionRequired : Result()
        data class SavePathNotFound(val checkedPaths: List<String>) : Result()
        data class AccessDenied(val path: String) : Result()
        data object NotFolderBased : Result()
        data object NoConfig : Result()
    }

    suspend fun validateAccess(emulatorId: String, emulatorPackage: String? = null): Result {
        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        if (config == null) {
            Logger.debug(TAG, "[SaveSync] VALIDATE | No config for emulator | emulator=$emulatorId")
            return Result.NoConfig
        }

        if (!config.usesFolderBasedSaves) {
            return Result.NotFolderBased
        }

        if (!hasFileAccessPermission()) {
            Logger.debug(TAG, "[SaveSync] VALIDATE | Permission not granted | emulator=$emulatorId")
            return Result.PermissionRequired
        }

        val resolvedPaths = resolvePaths(emulatorId, config, emulatorPackage)

        for (path in resolvedPaths) {
            val checkPath = packageDataRoot(path) ?: path
            if (!fileAccessLayer.exists(checkPath) || !fileAccessLayer.isDirectory(checkPath)) continue

            val canRead = try {
                fileAccessLayer.listFiles(checkPath) != null
            } catch (e: SecurityException) {
                Logger.debug(TAG, "[SaveSync] VALIDATE | SecurityException reading path | path=$checkPath, error=${e.message}")
                false
            }

            if (canRead) {
                Logger.debug(TAG, "[SaveSync] VALIDATE | Path accessible | path=$checkPath (from $path)")
                return Result.Valid
            } else {
                Logger.debug(TAG, "[SaveSync] VALIDATE | Path exists but access denied (SELinux/OEM restriction?) | path=$checkPath")
                return Result.AccessDenied(checkPath)
            }
        }

        Logger.debug(TAG, "[SaveSync] VALIDATE | No save path found | emulator=$emulatorId, package=$emulatorPackage, paths=$resolvedPaths")
        return Result.SavePathNotFound(resolvedPaths)
    }

    suspend fun resolvePaths(
        emulatorId: String,
        config: SavePathConfig,
        emulatorPackage: String?,
        platformSlug: String? = null
    ): List<String> {
        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        return if (userConfig?.isUserOverride == true) {
            val basePath = userConfig.savePathPattern
            // Defer per-platform path normalization (e.g. 3DS Nintendo 3DS subfolder) to the
            // platform handler so this validator stays platform-agnostic.
            val effectivePath = platformSlug?.let { slug ->
                saveHandlerRegistry.getFolderHandler(slug)?.resolveBasePath(config, basePath)
            } ?: basePath
            listOf(effectivePath)
        } else {
            SavePathRegistry.resolvePathWithPackage(config, emulatorPackage, context.filesDir.absolutePath)
        }
    }

    fun isPackageDataAccessible(emulatorId: String, emulatorPackage: String? = null): Boolean {
        if (!hasFileAccessPermission()) return false

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId) ?: return false
        val resolvedPaths = SavePathRegistry.resolvePathWithPackage(config, emulatorPackage, context.filesDir.absolutePath)
        val roots = resolvedPaths.mapNotNull { packageDataRoot(it) }.distinct()

        return roots.any { root ->
            try {
                fileAccessLayer.exists(root) && fileAccessLayer.listFiles(root) != null
            } catch (_: SecurityException) {
                false
            }
        }
    }

    private val packageDataPattern = Regex(".*/Android/data/[^/]+")

    private fun packageDataRoot(path: String): String? =
        packageDataPattern.find(path)?.value

    private fun hasFileAccessPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
}
