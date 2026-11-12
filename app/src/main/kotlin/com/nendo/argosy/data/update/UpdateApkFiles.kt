package com.nendo.argosy.data.update

import com.nendo.argosy.data.model.AppUpdateRelease
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class UpdateFileMismatchException(message: String) : IOException(message)

/**
 * Downloaded Argosy updates under one folder, named `argosy-<tag>-<abi>.apk`. A download is
 * written beside its target as `<name>.part` and renamed only once it matches the release asset.
 * Every call does blocking file I/O, so callers run it off the main thread.
 */
class UpdateApkFiles(private val dir: File) {

    fun fileFor(release: AppUpdateRelease): File =
        File(dir, "$PREFIX${safeSegment(release.tag)}-${safeSegment(release.abi)}$APK_SUFFIX")

    fun partFor(release: AppUpdateRelease): File {
        val target = fileFor(release)
        return File(dir, target.name + PART_SUFFIX)
    }

    fun newPart(release: AppUpdateRelease): File {
        dir.mkdirs()
        return partFor(release).also { it.delete() }
    }

    fun isStored(release: AppUpdateRelease): Boolean {
        val file = fileFor(release)
        return file.isFile && file.length() == release.assetSize
    }

    fun reusable(release: AppUpdateRelease): File? =
        fileFor(release).takeIf { matches(it, release.assetSize, release.assetDigest) }

    fun commit(release: AppUpdateRelease): File {
        val part = partFor(release)
        if (!matches(part, release.assetSize, release.assetDigest)) {
            val actualSize = if (part.isFile) part.length() else -1L
            part.delete()
            throw UpdateFileMismatchException(
                "Downloaded ${release.assetName} does not match the release asset (size $actualSize of ${release.assetSize})"
            )
        }
        val target = fileFor(release)
        target.delete()
        if (!part.renameTo(target)) {
            part.delete()
            throw IOException("Could not move ${part.name} into place")
        }
        return target
    }

    fun delete(release: AppUpdateRelease) {
        fileFor(release).delete()
        partFor(release).delete()
    }

    /**
     * Deletes every leftover `.part` file and every stored update whose version is not above
     * [installedVersion]. Returns the files removed.
     */
    fun cleanup(installedVersion: String): List<File> {
        val entries = dir.listFiles() ?: return emptyList()
        return entries.filter { file ->
            file.isFile && isDisposable(file.name, installedVersion) && file.delete()
        }
    }

    private fun isDisposable(name: String, installedVersion: String): Boolean {
        if (name.endsWith(PART_SUFFIX)) return true
        if (!name.startsWith(PREFIX) || !name.endsWith(APK_SUFFIX)) return false
        val tag = tagOf(name) ?: return true
        return !UpdatePromptRules.isNewer(tag, installedVersion)
    }

    companion object {
        private const val PREFIX = "argosy-"
        private const val APK_SUFFIX = ".apk"
        private const val PART_SUFFIX = ".part"
        private const val SHA256_PREFIX = "sha256:"
        private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")
        private val UNSAFE_SEGMENT_CHARS = Regex("[^A-Za-z0-9._-]")

        fun tagOf(fileName: String): String? {
            val stem = fileName.removePrefix(PREFIX).removeSuffix(APK_SUFFIX)
            if (!stem.contains('-')) return null
            return stem.substringBeforeLast('-').takeIf { it.isNotEmpty() }
        }

        /**
         * The lowercase hex sha256 in a GitHub asset digest, or null when the digest is absent or
         * names another algorithm.
         */
        fun sha256Of(digest: String?): String? {
            val value = digest?.trim() ?: return null
            val hex = if (value.startsWith(SHA256_PREFIX, ignoreCase = true)) value.substring(SHA256_PREFIX.length) else value
            return hex.takeIf { SHA256_HEX.matches(it) }?.lowercase()
        }

        /**
         * True when [file] is [expectedSize] bytes and, when [expectedDigest] carries a sha256,
         * hashes to it.
         */
        fun matches(file: File, expectedSize: Long, expectedDigest: String?): Boolean {
            if (!file.isFile || file.length() != expectedSize) return false
            val expected = sha256Of(expectedDigest) ?: return true
            return sha256Hex(file) == expected
        }

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun deleteLegacyCacheApks(cacheDir: File): List<File> =
            cacheDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(APK_SUFFIX) && it.delete() }
                .orEmpty()

        private fun safeSegment(value: String): String = value.replace(UNSAFE_SEGMENT_CHARS, "_")

        private const val BUFFER_SIZE = 64 * 1024
    }
}
