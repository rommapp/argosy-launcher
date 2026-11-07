package com.nendo.argosy.data.emulator.armsx2

import com.nendo.argosy.data.emulator.EmulatorIniFile
import com.nendo.argosy.data.emulator.ini.IniFileManager
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns on texture replacement for ARMSX2, which has no core option for it: the setting only
 * exists as `LoadTextureReplacements` in the emulator's INI, read once at boot.
 *
 * The core writes every default into that INI on its first run, `LoadTextureReplacements = false`
 * among them, so the key being present says nothing about what the player chose. An INI with no
 * `[UI] SettingsVersion` is discarded by the core and rebuilt from defaults, so Argosy never
 * creates one: until the core has run once there is nothing to edit, and that first launch
 * goes without the pack.
 */
@Singleton
class Armsx2TextureManager @Inject constructor(
    private val iniFileManager: IniFileManager
) {
    constructor() : this(IniFileManager())

    fun getIniFile(systemDir: File): File = File(systemDir, ARMSX2_INI_PATH)

    fun isTextureReplacementEnabled(systemDir: File): Boolean? {
        val value = iniFileManager.get(getIniFile(systemDir), SECTION_GS, KEY_LOAD_TEXTURES) ?: return null
        return when (value.trim().lowercase(Locale.ROOT)) {
            "true", "1", "yes", "enabled" -> true
            "false", "0", "no", "disabled" -> false
            else -> null
        }
    }

    fun enableForBuiltinCore(systemDir: File): Boolean {
        val iniFile = getIniFile(systemDir)
        if (!iniFile.isFile) {
            Logger.info(TAG, "ARMSX2 has not written its settings yet; texture replacement starts next launch")
            return false
        }
        if (isTextureReplacementEnabled(systemDir) == true) return true
        return iniFileManager.writeSetting(iniFile, SECTION_GS, KEY_LOAD_TEXTURES, "true")
    }

    fun enableForStandaloneApp(emulatorPackage: String, externalStorageRoot: String, fileAccessLayer: FileAccessLayer): Boolean {
        val iniFile = File(externalStorageRoot, "Android/data/$emulatorPackage/files/$STANDALONE_INI_NAME")
        val updated = EmulatorIniFile.update(iniFile, fileAccessLayer) { existing ->
            existing?.let { EmulatorIniFile.setValue(it, SECTION_GS, KEY_LOAD_TEXTURES, "true") }
        }
        if (!updated) {
            Logger.warn(TAG, "No editable $STANDALONE_INI_NAME for $emulatorPackage; turn on texture replacement in its settings")
        }
        return updated
    }

    companion object {
        private const val TAG = "Armsx2TextureManager"

        const val ARMSX2_INI_PATH = "pcsx2/inis/armsx2-libretro.ini"
        const val STANDALONE_INI_NAME = "PCSX2-Android.ini"
        const val SECTION_GS = "EmuCore/GS"
        const val KEY_LOAD_TEXTURES = "LoadTextureReplacements"
    }
}
