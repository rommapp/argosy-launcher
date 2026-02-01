package com.nendo.argosy.data.sync.platform

import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VitaSaveHandler @Inject constructor(
    private val fal: FileAccessLayer
) {
    companion object {
        private const val TAG = "VitaSaveHandler"
    }

    fun findSaveFolderByTitleId(basePath: String, titleId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "Base path does not exist | path=$basePath")
            return null
        }

        val match = fal.listFiles(basePath)?.firstOrNull {
            it.isDirectory && it.name.equals(titleId, ignoreCase = true)
        }

        if (match != null) {
            Logger.debug(TAG, "Save found | path=${match.path}")
            return match.path
        }

        Logger.debug(TAG, "No save found | basePath=$basePath, titleId=$titleId")
        return null
    }

    fun constructSavePath(baseDir: String, titleId: String): String {
        return "$baseDir/$titleId"
    }

    fun resolveBasePath(config: SavePathConfig, basePathOverride: String?): String? {
        if (basePathOverride != null) {
            return basePathOverride
        }

        val resolvedPaths = SavePathRegistry.resolvePath(config, "vita", null)
        return resolvedPaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: resolvedPaths.firstOrNull()
    }
}
