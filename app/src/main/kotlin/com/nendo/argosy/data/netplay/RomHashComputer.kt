package com.nendo.argosy.data.netplay

import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.NetplaySupportLevel
import java.io.File
import java.security.MessageDigest

object RomHashComputer {

    const val HASH_PREFIX_BYTES: Long = 1L * 1024L * 1024L

    fun isNetplayEligible(platformSlug: String): Boolean {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return LibretroCoreRegistry.getCoresForPlatform(canonical)
            .any { it.netplaySupport == NetplaySupportLevel.SUPPORTED }
    }

    fun computeRomHashPrefix(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var remaining = HASH_PREFIX_BYTES
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    remaining -= read
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun computeRomHashPrefix(path: String): String? = computeRomHashPrefix(File(path))
}
