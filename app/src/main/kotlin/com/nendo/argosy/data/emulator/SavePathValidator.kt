package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import com.nendo.argosy.util.StorageAccess
import javax.inject.Inject
import javax.inject.Singleton

enum class PackageDataAccess {
    DIRECT,
    UNICODE,
    ROOT,
    BLOCKED
}

@Singleton
class SavePathValidator @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val fileAccessLayer: FileAccessLayer,
    private val androidDataAccessor: com.nendo.argosy.data.storage.AndroidDataAccessor,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry
) {
    companion object {
        private const val TAG = "SavePathValidator"
        private const val PRIVATE_APP_DATA = "/data/data/"
    }

    sealed class Result {
        data object Valid : Result()
        data object PermissionRequired : Result()
        data class SavePathNotFound(val checkedPaths: List<String>) : Result()
        data class AccessDenied(val path: String) : Result()
        data object NotFolderBased : Result()
        data object NoConfig : Result()
    }

    suspend fun validateAccess(emulatorId: String, emulatorPackage: String? = null): Result =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            validateAccessOnIo(emulatorId, emulatorPackage)
        }

    private suspend fun validateAccessOnIo(emulatorId: String, emulatorPackage: String?): Result {
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

        val resolvedPaths = resolvePaths(config, emulatorPackage)

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
        config: SavePathConfig,
        emulatorPackage: String?,
        platformSlug: String? = null
    ): List<String> {
        val basePath = emulatorSaveConfigDao.getByEmulator(config.emulatorId)
            ?.takeIf { it.isUserOverride || it.isAutoDetected }
            ?.savePathPattern
            ?.takeIf { it.isNotBlank() }
            ?.let { SavePathRegistry.pathForPackage(it, emulatorPackage) }
        return if (basePath != null) {
            val effectivePath = platformSlug?.let { slug ->
                saveHandlerRegistry.getFolderHandler(slug)?.resolveBasePath(config, basePath)
            } ?: basePath
            listOf(effectivePath)
        } else {
            SavePathRegistry.resolvePathWithPackage(config, emulatorPackage, context.filesDir.absolutePath)
        }
    }

    fun packageDataAccess(emulatorId: String, emulatorPackage: String? = null): PackageDataAccess {
        if (!hasFileAccessPermission()) return PackageDataAccess.BLOCKED

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId) ?: return PackageDataAccess.BLOCKED
        if (config.requiresRoot && !RootShell.isAvailable) return PackageDataAccess.BLOCKED
        val resolvedPaths = SavePathRegistry.resolvePathWithPackage(config, emulatorPackage, context.filesDir.absolutePath)
        if (config.requiresRoot && resolvedPaths.any { it.startsWith(PRIVATE_APP_DATA) }) return PackageDataAccess.ROOT
        val roots = resolvedPaths.mapNotNull { packageDataRoot(it) }.distinct()

        val reachable = roots.firstOrNull { root ->
            try {
                fileAccessLayer.exists(root) && fileAccessLayer.listFiles(root) != null
            } catch (_: SecurityException) {
                false
            }
        } ?: return PackageDataAccess.BLOCKED
        return when {
            config.requiresRoot -> PackageDataAccess.ROOT
            fileAccessLayer.isRestrictedPath(reachable) && androidDataAccessor.isAltAccessSupported() -> PackageDataAccess.UNICODE
            else -> PackageDataAccess.DIRECT
        }
    }

    private val packageDataPattern = Regex(".*/Android/data/[^/]+")

    private fun packageDataRoot(path: String): String? =
        packageDataPattern.find(path)?.value

    private fun hasFileAccessPermission(): Boolean = StorageAccess.isGranted(context)
}
