package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.savepath.Pcsx2DataFolder
import com.nendo.argosy.data.emulator.savepath.PpssppMemoryStick
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger

class EmulatorSaveRoots(
    private val context: Context,
    private val fal: FileAccessLayer,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository
) {

    /**
     * The folder an external emulator keeps its saves under for the Sigil [layout]: the user's
     * chosen path, then the live PPSSPP memory stick, then the live PCSX2-family data folder, then
     * the evaluated default, then the packaged paths, the first that exists winning. For
     * [SigilSaveHandler.PCSX2_STANDALONE_LAYOUT] only, the packaged paths are also placed on every
     * storage volume, and the live data folder is the one holding the newest `PCSX2-Android.ini`.
     */
    suspend fun rootFor(
        config: SavePathConfig,
        platformSlug: String,
        layout: String,
        emulatorPackage: String?
    ): String? {
        val pcsx2Family = layout == SigilSaveHandler.PCSX2_STANDALONE_LAYOUT
        val volumes = fal.externalStorageRoots()
        val packaged = SavePathRegistry.resolvePathWithPackage(
            config, emulatorPackage, context.filesDir.absolutePath, volumes
        )
        val registered = if (pcsx2Family) Pcsx2DataFolder.acrossVolumes(packaged, volumes) else packaged
        val chosen = emulatorSaveConfigRepository.resolveUserSavePath(config.emulatorId, platformSlug)
            ?.takeIf { it.isNotBlank() }
            ?: PpssppMemoryStick.liveSaveData(config, registered, fal)
            ?: (if (pcsx2Family) Pcsx2DataFolder.liveCardsDir(registered, fal) else null)
            ?: emulatorSaveConfigRepository.resolveEvaluatedSavePath(config.emulatorId)
        val candidates = listOfNotNull(chosen) + registered
        val roots = candidates.mapNotNull {
            if (layout == SigilSaveHandler.FLYCAST_STANDALONE_LAYOUT) {
                it.trimEnd('/')
            } else {
                SigilSaveHandler.rootFor(it, layout) ?: SigilSaveHandler.rootAbove(it, layout, fal::isDirectory)
            }
        }.distinct()
        if (roots.isEmpty()) {
            Logger.warn(TAG, "[SaveSync] SIGIL | no root for layout=$layout in ${candidates.firstOrNull()}")
        }
        return roots.firstOrNull { fal.isDirectory(it) } ?: roots.firstOrNull()
    }

    private companion object {
        const val TAG = "EmulatorSaveRoots"
    }
}
