package com.nendo.argosy.data.emulator.armsx2

import com.nendo.argosy.data.emulator.EmulatorIniFile
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `[EmuCore/GS] LoadTextureReplacements` switch in an ARMSX2 INI that already exists, for the
 * built-in core and the standalone app. A missing INI is never created.
 */
@Singleton
class Armsx2TextureManager @Inject constructor() {

    fun getIniFile(systemDir: File): File = File(systemDir, ARMSX2_INI_PATH)

    fun enableForBuiltinCore(systemDir: File): Boolean {
        val iniFile = getIniFile(systemDir)
        if (!iniFile.isFile) {
            Logger.info(TAG, "ARMSX2 has not written its settings yet; texture replacement starts next launch")
            return false
        }
        return enable(iniFile, null)
    }

    fun enableForStandaloneApp(emulatorPackage: String, externalStorageRoot: String, fileAccessLayer: FileAccessLayer): Boolean {
        val iniFile = File(externalStorageRoot, "Android/data/$emulatorPackage/files/$STANDALONE_INI_NAME")
        val updated = enable(iniFile, fileAccessLayer)
        if (!updated) {
            Logger.warn(TAG, "No editable $STANDALONE_INI_NAME for $emulatorPackage; turn on texture replacement in its settings")
        }
        return updated
    }

    private fun enable(iniFile: File, fileAccessLayer: FileAccessLayer?): Boolean =
        EmulatorIniFile.update(iniFile, fileAccessLayer) { existing ->
            existing?.let { EmulatorIniFile.setValue(it, SECTION_GS, KEY_LOAD_TEXTURES, "true") }
        }

    companion object {
        private const val TAG = "Armsx2TextureManager"

        const val ARMSX2_INI_PATH = "pcsx2/inis/armsx2-libretro.ini"
        const val STANDALONE_INI_NAME = "PCSX2-Android.ini"
        const val SECTION_GS = "EmuCore/GS"
        const val KEY_LOAD_TEXTURES = "LoadTextureReplacements"
    }
}
