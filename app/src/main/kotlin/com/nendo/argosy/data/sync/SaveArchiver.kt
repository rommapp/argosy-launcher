package com.nendo.argosy.data.sync

import com.nendo.argosy.util.Logger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveArchiver @Inject constructor() {

    private val TAG = "SaveArchiver"
    private val BUFFER_SIZE = 8192

    fun zipFolder(sourceFolder: File, targetZip: File): Boolean {
        if (!sourceFolder.exists() || !sourceFolder.isDirectory) {
            Logger.warn(TAG, "[SaveSync] ARCHIVE | Source folder invalid | path=${sourceFolder.absolutePath}, exists=${sourceFolder.exists()}, isDir=${sourceFolder.isDirectory}")
            return false
        }

        val fileCount = sourceFolder.walkTopDown().filter { it.isFile }.count()
        val totalSize = sourceFolder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        Logger.debug(TAG, "[SaveSync] ARCHIVE | Zipping folder | source=${sourceFolder.name}, files=$fileCount, size=${totalSize}bytes")

        return try {
            targetZip.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZip))).use { zos ->
                zipFolderRecursive(sourceFolder, sourceFolder.name, zos)
            }
            val ratio = if (totalSize > 0) (targetZip.length() * 100 / totalSize) else 100
            Logger.debug(TAG, "[SaveSync] ARCHIVE | Zip complete | output=${targetZip.name}, compressedSize=${targetZip.length()}bytes, ratio=$ratio%")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | Zip failed | source=${sourceFolder.absolutePath}", e)
            targetZip.delete()
            false
        }
    }

    private fun zipFolderRecursive(folder: File, parentPath: String, zos: ZipOutputStream) {
        val files = folder.listFiles() ?: return

        for (file in files) {
            val entryPath = "$parentPath/${file.name}"

            if (file.isDirectory) {
                zos.putNextEntry(ZipEntry("$entryPath/"))
                zos.closeEntry()
                zipFolderRecursive(file, entryPath, zos)
            } else {
                zos.putNextEntry(ZipEntry(entryPath))
                BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { bis ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var count: Int
                    while (bis.read(buffer, 0, BUFFER_SIZE).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                }
                zos.closeEntry()
            }
        }
    }

    fun unzipToFolder(sourceZip: File, targetFolder: File): Boolean {
        if (!sourceZip.exists() || !sourceZip.isFile) {
            Logger.warn(TAG, "Source zip does not exist or is not a file: ${sourceZip.absolutePath}")
            return false
        }

        return try {
            targetFolder.mkdirs()
            ZipInputStream(BufferedInputStream(FileInputStream(sourceZip))).use { zis ->
                var entry: ZipEntry?
                val buffer = ByteArray(BUFFER_SIZE)

                while (zis.nextEntry.also { entry = it } != null) {
                    val entryFile = File(targetFolder, entry!!.name)

                    if (!entryFile.canonicalPath.startsWith(targetFolder.canonicalPath)) {
                        Logger.error(TAG, "Zip path traversal detected: ${entry!!.name}")
                        return false
                    }

                    if (entry!!.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(entryFile), BUFFER_SIZE).use { bos ->
                            var count: Int
                            while (zis.read(buffer, 0, BUFFER_SIZE).also { count = it } != -1) {
                                bos.write(buffer, 0, count)
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }
            Logger.debug(TAG, "Successfully unzipped ${sourceZip.absolutePath} to ${targetFolder.absolutePath}")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to unzip file: ${sourceZip.absolutePath}", e)
            false
        }
    }

    fun peekRootFolderName(zipFile: File): String? {
        if (!zipFile.exists() || !zipFile.isFile) {
            Logger.debug(TAG, "[SaveSync] ARCHIVE | peekRootFolderName: file invalid | path=${zipFile.absolutePath}")
            return null
        }

        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                val firstEntry = zis.nextEntry ?: return null
                val entryName = firstEntry.name
                val rootFolder = entryName.substringBefore('/').takeIf {
                    it.isNotEmpty() && entryName.contains('/')
                }
                Logger.debug(TAG, "[SaveSync] ARCHIVE | peekRootFolderName | zip=${zipFile.name}, firstEntry=$entryName, rootFolder=$rootFolder")
                rootFolder
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | peekRootFolderName failed | zip=${zipFile.name}", e)
            null
        }
    }

    fun unzipSingleFolder(sourceZip: File, targetFolder: File): Boolean {
        if (!sourceZip.exists() || !sourceZip.isFile) {
            Logger.warn(TAG, "[SaveSync] ARCHIVE | Source zip invalid | path=${sourceZip.absolutePath}, exists=${sourceZip.exists()}, isFile=${sourceZip.isFile}")
            return false
        }

        Logger.debug(TAG, "[SaveSync] ARCHIVE | Unzipping | source=${sourceZip.name}, size=${sourceZip.length()}bytes, target=${targetFolder.absolutePath}")

        return try {
            targetFolder.mkdirs()
            var fileCount = 0
            var totalSize = 0L
            ZipInputStream(BufferedInputStream(FileInputStream(sourceZip))).use { zis ->
                var entry: ZipEntry?
                val buffer = ByteArray(BUFFER_SIZE)
                var rootFolder: String? = null

                while (zis.nextEntry.also { entry = it } != null) {
                    val entryName = entry!!.name

                    if (rootFolder == null) {
                        rootFolder = entryName.substringBefore('/').takeIf {
                            entryName.contains('/')
                        }
                    }

                    val relativePath = if (rootFolder != null && entryName.startsWith("$rootFolder/")) {
                        entryName.removePrefix("$rootFolder/")
                    } else {
                        entryName
                    }

                    if (relativePath.isEmpty()) {
                        zis.closeEntry()
                        continue
                    }

                    val entryFile = File(targetFolder, relativePath)

                    if (!entryFile.canonicalPath.startsWith(targetFolder.canonicalPath)) {
                        Logger.error(TAG, "[SaveSync] ARCHIVE | Zip path traversal detected | entry=$entryName")
                        return false
                    }

                    if (entry!!.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                                var count: Int
                                while (zis.read(buffer, 0, BUFFER_SIZE).also { count = it } != -1) {
                                    bos.write(buffer, 0, count)
                                    totalSize += count
                                }
                                bos.flush()
                            }
                            try {
                                fos.fd.sync()
                            } catch (e: Exception) {
                                Logger.debug(TAG, "[SaveSync] ARCHIVE | fsync not supported for ${entryFile.name}, relying on flush")
                            }
                        }
                        fileCount++
                    }
                    zis.closeEntry()
                }
                Logger.debug(TAG, "[SaveSync] ARCHIVE | Detected root folder | rootFolder=$rootFolder")
            }
            Logger.debug(TAG, "[SaveSync] ARCHIVE | Unzip complete | target=${targetFolder.name}, files=$fileCount, extractedSize=${totalSize}bytes")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | Unzip failed | source=${sourceZip.absolutePath}", e)
            false
        }
    }

    fun isJksvFormat(zipFile: File): Boolean {
        if (!zipFile.exists() || !zipFile.isFile) return false

        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    if (entry!!.name == JKSV_META_FILE) {
                        return@use true
                    }
                    zis.closeEntry()
                }
                false
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | isJksvFormat check failed | zip=${zipFile.name}", e)
            false
        }
    }

    fun parseTitleIdFromJksvMeta(zipFile: File): String? {
        if (!zipFile.exists() || !zipFile.isFile) return null

        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    if (entry!!.name == JKSV_META_FILE) {
                        val metaBytes = zis.readBytes()
                        zis.closeEntry()
                        return@use parseTitleIdFromMeta(metaBytes)
                    }
                    zis.closeEntry()
                }
                null
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | parseTitleIdFromJksvMeta failed | zip=${zipFile.name}", e)
            null
        }
    }

    private fun parseTitleIdFromMeta(metaBytes: ByteArray): String? {
        if (metaBytes.size < JKSV_MAGIC.length) return null

        val magic = String(metaBytes, 0, JKSV_MAGIC.length, Charsets.US_ASCII)
        if (magic != JKSV_MAGIC) {
            Logger.debug(TAG, "[SaveSync] ARCHIVE | Invalid JKSV magic: $magic")
            return null
        }

        for (offset in JKSV_TITLE_ID_OFFSETS) {
            if (metaBytes.size < offset + 8) continue

            val titleIdBytes = metaBytes.copyOfRange(offset, offset + 8)
            val titleId = ByteBuffer.wrap(titleIdBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getLong()

            val formatted = String.format("%016X", titleId)

            if (formatted.startsWith("01")) {
                Logger.debug(TAG, "[SaveSync] ARCHIVE | Parsed JKSV titleId: $formatted (offset=$offset)")
                return formatted
            }
        }

        Logger.debug(TAG, "[SaveSync] ARCHIVE | No valid title ID found in JKSV metadata")
        return null
    }

    fun unzipSingleFolderExcluding(
        sourceZip: File,
        targetFolder: File,
        excludeFiles: Set<String>
    ): Boolean {
        if (!sourceZip.exists() || !sourceZip.isFile) {
            Logger.warn(TAG, "[SaveSync] ARCHIVE | Source zip invalid | path=${sourceZip.absolutePath}")
            return false
        }

        Logger.debug(TAG, "[SaveSync] ARCHIVE | Unzipping (excluding ${excludeFiles.size} patterns) | source=${sourceZip.name}")

        return try {
            targetFolder.mkdirs()
            var fileCount = 0
            var totalSize = 0L
            var skippedCount = 0
            ZipInputStream(BufferedInputStream(FileInputStream(sourceZip))).use { zis ->
                var entry: ZipEntry?
                val buffer = ByteArray(BUFFER_SIZE)
                var rootFolder: String? = null

                while (zis.nextEntry.also { entry = it } != null) {
                    val entryName = entry!!.name

                    if (rootFolder == null) {
                        rootFolder = entryName.substringBefore('/').takeIf {
                            entryName.contains('/')
                        }
                    }

                    val relativePath = if (rootFolder != null && entryName.startsWith("$rootFolder/")) {
                        entryName.removePrefix("$rootFolder/")
                    } else {
                        entryName
                    }

                    if (relativePath.isEmpty()) {
                        zis.closeEntry()
                        continue
                    }

                    val fileName = relativePath.substringAfterLast('/')
                    if (excludeFiles.contains(fileName)) {
                        Logger.debug(TAG, "[SaveSync] ARCHIVE | Skipping excluded file | entry=$entryName")
                        skippedCount++
                        zis.closeEntry()
                        continue
                    }

                    val entryFile = File(targetFolder, relativePath)

                    if (!entryFile.canonicalPath.startsWith(targetFolder.canonicalPath)) {
                        Logger.error(TAG, "[SaveSync] ARCHIVE | Zip path traversal detected | entry=$entryName")
                        return false
                    }

                    if (entry!!.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                                var count: Int
                                while (zis.read(buffer, 0, BUFFER_SIZE).also { count = it } != -1) {
                                    bos.write(buffer, 0, count)
                                    totalSize += count
                                }
                                bos.flush()
                            }
                            try {
                                fos.fd.sync()
                            } catch (e: Exception) {
                                Logger.debug(TAG, "[SaveSync] ARCHIVE | fsync not supported for ${entryFile.name}")
                            }
                        }
                        fileCount++
                    }
                    zis.closeEntry()
                }
            }
            Logger.debug(TAG, "[SaveSync] ARCHIVE | Unzip complete | files=$fileCount, skipped=$skippedCount, size=${totalSize}bytes")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | Unzip failed | source=${sourceZip.absolutePath}", e)
            false
        }
    }

    fun unzipPreservingStructure(
        sourceZip: File,
        targetFolder: File,
        excludeFiles: Set<String>
    ): Boolean {
        if (!sourceZip.exists() || !sourceZip.isFile) {
            Logger.warn(TAG, "[SaveSync] ARCHIVE | Source zip invalid | path=${sourceZip.absolutePath}")
            return false
        }

        Logger.debug(TAG, "[SaveSync] ARCHIVE | Unzipping (preserving structure) | source=${sourceZip.name}")

        return try {
            targetFolder.mkdirs()
            var fileCount = 0
            var totalSize = 0L
            var skippedCount = 0
            ZipInputStream(BufferedInputStream(FileInputStream(sourceZip))).use { zis ->
                var entry: ZipEntry?
                val buffer = ByteArray(BUFFER_SIZE)

                while (zis.nextEntry.also { entry = it } != null) {
                    val entryName = entry!!.name

                    val fileName = entryName.substringAfterLast('/')
                    if (excludeFiles.contains(fileName)) {
                        Logger.debug(TAG, "[SaveSync] ARCHIVE | Skipping excluded file | entry=$entryName")
                        skippedCount++
                        zis.closeEntry()
                        continue
                    }

                    val entryFile = File(targetFolder, entryName)

                    if (!entryFile.canonicalPath.startsWith(targetFolder.canonicalPath)) {
                        Logger.error(TAG, "[SaveSync] ARCHIVE | Zip path traversal detected | entry=$entryName")
                        return false
                    }

                    if (entry!!.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                                var count: Int
                                while (zis.read(buffer, 0, BUFFER_SIZE).also { count = it } != -1) {
                                    bos.write(buffer, 0, count)
                                    totalSize += count
                                }
                                bos.flush()
                            }
                            try {
                                fos.fd.sync()
                            } catch (e: Exception) {
                                Logger.debug(TAG, "[SaveSync] ARCHIVE | fsync not supported for ${entryFile.name}")
                            }
                        }
                        fileCount++
                    }
                    zis.closeEntry()
                }
            }
            Logger.debug(TAG, "[SaveSync] ARCHIVE | Unzip complete | files=$fileCount, skipped=$skippedCount, size=${totalSize}bytes")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | Unzip failed | source=${sourceZip.absolutePath}", e)
            false
        }
    }

    fun calculateFileHash(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val JKSV_META_FILE = ".nx_save_meta.bin"
        private const val JKSV_MAGIC = "JKSV"
        private val JKSV_TITLE_ID_OFFSETS = listOf(5, 4, 6)
    }
}
