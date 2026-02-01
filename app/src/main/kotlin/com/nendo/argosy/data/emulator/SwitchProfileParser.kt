package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwitchProfileParser @Inject constructor(
    private val fal: FileAccessLayer
) {
    private val TAG = "SwitchProfileParser"

    companion object {
        private val EDEN_PACKAGES = setOf(
            "dev.eden.eden_emulator",
            "dev.eden.eden_emulator.debug"
        )

        private const val PROFILES_DAT_PATH = "nand/system/save/8000000000000010/su/avators/profiles.dat"
        private const val UUID_OFFSET = 0x10
        private const val UUID_SIZE = 16
    }

    fun parseActiveProfile(emulatorPackage: String, dataPath: String): String? {
        return when {
            EDEN_PACKAGES.any { emulatorPackage.startsWith(it.substringBefore(".debug")) } ->
                parseEdenProfile(dataPath)
            else -> null
        }
    }

    private fun parseEdenProfile(dataPath: String): String? {
        val profilesPath = "$dataPath/$PROFILES_DAT_PATH"
        val bytes = fal.readBytes(profilesPath)
        if (bytes == null) {
            Logger.debug(TAG, "profiles.dat not found | path=$profilesPath")
            return null
        }
        if (bytes.size < UUID_OFFSET + UUID_SIZE) {
            Logger.debug(TAG, "profiles.dat too small | size=${bytes.size}")
            return null
        }

        val uuidBytes = bytes.copyOfRange(UUID_OFFSET, UUID_OFFSET + UUID_SIZE)
        val folderName = uuidBytesToFolderName(uuidBytes)
        Logger.debug(TAG, "Parsed Eden profile UUID | folder=$folderName")
        return folderName
    }

    private fun uuidBytesToFolderName(bytes: ByteArray): String {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val low = buffer.getLong()
        val high = buffer.getLong()
        return String.format("%016X%016X", high, low)
    }
}
