package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.storage.FileAccessLayer
import java.io.File

object DolphinConfigModifier {
    private const val GFX_INI = "GFX.ini"
    private const val SETTINGS_SECTION = "Settings"
    private const val HIRES_TEXTURES_KEY = "HiresTextures"

    fun findConfigDir(targetTextureDir: File): File? {
        var curr: File? = targetTextureDir
        while (curr != null) {
            val configDirect = File(curr, "Config")
            if (configDirect.isDirectory) return configDirect

            val name = curr.name
            if (name == "files" || name.contains("dolphin", ignoreCase = true) || name.contains("mmjr", ignoreCase = true)) {
                return File(curr, "Config")
            }
            curr = curr.parentFile
        }
        return null
    }

    fun enableCustomTextures(
        targetTextureDir: File,
        fileAccessLayer: FileAccessLayer? = null
    ): Boolean {
        val configDir = findConfigDir(targetTextureDir) ?: return false
        return EmulatorIniFile.update(File(configDir, GFX_INI), fileAccessLayer, ::injectHiresTexturesSetting)
    }

    internal fun injectHiresTexturesSetting(rawText: String?): String =
        EmulatorIniFile.setValue(rawText, SETTINGS_SECTION, HIRES_TEXTURES_KEY, "True")
}
