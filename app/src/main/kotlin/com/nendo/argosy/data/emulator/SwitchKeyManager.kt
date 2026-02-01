package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
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
}
