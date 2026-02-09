package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.AesXts
import com.nendo.argosy.util.Logger
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwitchKeyManager @Inject constructor(
    private val fal: FileAccessLayer
) {
    private val TAG = "SwitchKeyManager"

    private var cachedHeaderKey: ByteArray? = null
    private var cachedProdKeysPath: String? = null

    fun getHeaderKey(prodKeysPath: String): ByteArray? {
        if (prodKeysPath == cachedProdKeysPath && cachedHeaderKey != null) {
            return cachedHeaderKey
        }

        val content = fal.readBytes(prodKeysPath)?.toString(Charsets.UTF_8) ?: run {
            Logger.debug(TAG, "Failed to read prod.keys | path=$prodKeysPath")
            return null
        }

        val headerKeyLine = content.lines().find { it.trim().startsWith("header_key") } ?: run {
            Logger.debug(TAG, "header_key not found in prod.keys | path=$prodKeysPath")
            return null
        }

        val hexValue = headerKeyLine.substringAfter("=").trim()
        if (hexValue.length != 64) {
            Logger.debug(TAG, "Invalid header_key length | expected=64, got=${hexValue.length}")
            return null
        }

        return try {
            val key = hexValue.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            cachedHeaderKey = key
            cachedProdKeysPath = prodKeysPath
            Logger.debug(TAG, "Loaded header_key from prod.keys | path=$prodKeysPath")
            key
        } catch (e: NumberFormatException) {
            Logger.warn(TAG, "Invalid hex in header_key | path=$prodKeysPath", e)
            null
        }
    }

    fun findProdKeysPath(emulatorPackage: String): String? {
        val candidates = listOf(
            "/storage/emulated/0/Android/data/$emulatorPackage/files/keys/prod.keys"
        )
        return candidates.firstOrNull { fal.exists(it) }?.also {
            Logger.debug(TAG, "Found prod.keys | pkg=$emulatorPackage, path=$it")
        }
    }

    fun clearCache() {
        cachedHeaderKey = null
        cachedProdKeysPath = null
    }

    fun validateKeysForFirmware(prodKeysPath: String, firmwareZipStream: InputStream): Boolean {
        val headerKey = getHeaderKey(prodKeysPath)
        if (headerKey == null) {
            Logger.warn(TAG, "Cannot validate: no header_key in prod.keys")
            return false
        }

        return try {
            validateWithHeaderKey(headerKey, firmwareZipStream)
        } catch (e: Exception) {
            Logger.warn(TAG, "Firmware validation failed", e)
            false
        }
    }

    fun validateKeysForFirmwareBytes(prodKeysContent: ByteArray, firmwareZipStream: InputStream): Boolean {
        val headerKey = parseHeaderKeyFromContent(prodKeysContent.toString(Charsets.UTF_8))
        if (headerKey == null) {
            Logger.warn(TAG, "Cannot validate: no header_key in prod.keys content")
            return false
        }

        return try {
            validateWithHeaderKey(headerKey, firmwareZipStream)
        } catch (e: Exception) {
            Logger.warn(TAG, "Firmware validation failed", e)
            false
        }
    }

    private fun validateWithHeaderKey(headerKey: ByteArray, firmwareZipStream: InputStream): Boolean {
        ZipInputStream(firmwareZipStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".nca", ignoreCase = true)) {
                    val ncaHeader = ByteArray(NCA_HEADER_SIZE)
                    var totalRead = 0
                    while (totalRead < NCA_HEADER_SIZE) {
                        val bytesRead = zip.read(ncaHeader, totalRead, NCA_HEADER_SIZE - totalRead)
                        if (bytesRead == -1) break
                        totalRead += bytesRead
                    }

                    if (totalRead >= NCA_HEADER_SIZE) {
                        val decrypted = AesXts.decrypt(ncaHeader, headerKey, 0)
                        val magic = String(decrypted, NCA_MAGIC_OFFSET, 4)
                        if (magic == "NCA3") {
                            Logger.debug(TAG, "Firmware validated: NCA3 magic found in ${entry.name}")
                            return true
                        }
                    }
                }
                entry = zip.nextEntry
            }
        }

        Logger.warn(TAG, "Firmware validation failed: no NCA3 magic found")
        return false
    }

    private fun parseHeaderKeyFromContent(content: String): ByteArray? {
        val headerKeyLine = content.lines().find { it.trim().startsWith("header_key") }
            ?: return null

        val hexValue = headerKeyLine.substringAfter("=").trim()
        if (hexValue.length != 64) return null

        return try {
            hexValue.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: NumberFormatException) {
            null
        }
    }

    companion object {
        private const val NCA_HEADER_SIZE = 0xC00
        private const val NCA_MAGIC_OFFSET = 0x200
    }
}
