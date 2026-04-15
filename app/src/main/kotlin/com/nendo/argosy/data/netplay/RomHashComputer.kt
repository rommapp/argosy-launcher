package com.nendo.argosy.data.netplay

import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.NetplaySupportLevel
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

object RomHashComputer {

    const val HASH_PREFIX_BYTES: Long = 1L * 1024L * 1024L
    private val ARCHIVE_EXTENSIONS = setOf("zip")

    fun isNetplayEligible(platformSlug: String): Boolean {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return LibretroCoreRegistry.getCoresForPlatform(canonical)
            .any { it.netplaySupport == NetplaySupportLevel.SUPPORTED }
    }

    fun computeRomHashPrefix(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        val ext = file.extension.lowercase()
        return if (ext in ARCHIVE_EXTENSIONS) {
            hashFirstRomInZip(file) ?: hashStream(file.inputStream())
        } else {
            hashStream(file.inputStream())
        }
    }

    fun computeRomHashPrefix(path: String): String? = computeRomHashPrefix(File(path))

    private fun hashFirstRomInZip(file: File): String? = try {
        ZipFile(file).use { zf ->
            val entry = zf.entries().asSequence()
                .filter { !it.isDirectory }
                .sortedBy { it.name }
                .firstOrNull { entry ->
                    val name = entry.name.substringAfterLast('/').lowercase()
                    val inner = name.substringAfterLast('.', "")
                    inner.isNotEmpty() && inner !in ARCHIVE_EXTENSIONS
                }
            entry?.let { hashStream(zf.getInputStream(it)) }
        }
    } catch (_: Exception) { null }

    private fun hashStream(input: InputStream): String? = try {
        input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var remaining = HASH_PREFIX_BYTES
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = stream.read(buffer, 0, toRead)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                remaining -= read
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (_: Exception) { null }
}
