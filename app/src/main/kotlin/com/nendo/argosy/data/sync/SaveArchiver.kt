package com.nendo.argosy.data.sync

import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.ManagedStorageAccessor
import com.nendo.argosy.util.Logger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveArchiver @Inject constructor(
    private val androidDataAccessor: AndroidDataAccessor,
    private val managedStorageAccessor: ManagedStorageAccessor
) {

    private val TAG = "SaveArchiver"
    private val BUFFER_SIZE = 8192

    private fun isRestrictedPath(path: String): Boolean {
        return androidDataAccessor.isRestrictedAndroidPath(path)
    }

    private fun extractVolumeAndPath(path: String): Pair<String, String>? {
        val primaryRoot = "/storage/emulated/0"
        val sdcardPattern = Regex("^/storage/([A-F0-9-]+)")

        return when {
            path.startsWith(primaryRoot) -> {
                val rel = path.removePrefix(primaryRoot).trimStart('/')
                "primary" to rel
            }
            path.matches(Regex("^/storage/[A-F0-9-]+.*")) -> {
                val match = sdcardPattern.find(path)
                if (match != null) {
                    val volId = match.groupValues[1]
                    val rel = path.removePrefix("/storage/$volId").trimStart('/')
                    volId to rel
                } else null
            }
            else -> null
        }
    }

    private fun openOutputStreamForPath(path: String): OutputStream? {
        // For restricted paths, try alt access first (most reliable on supported devices)
        val isRestricted = isRestrictedPath(path)
        val altAccessSupported = isRestricted && androidDataAccessor.isAltAccessSupported()

        if (altAccessSupported) {
            val stream = androidDataAccessor.getOutputStream(path)
            if (stream != null) {
                Logger.debug(TAG, "[AltAccess] Opened output stream for $path")
                return stream
            }
            Logger.debug(TAG, "[AltAccess] Failed to open output stream, falling back to ManagedAccess | path=$path")
        }

        // Fallback to ManagedStorageAccessor (DocumentsContract approach)
        if (isRestricted) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return null
            Logger.debug(TAG, "[ManagedAccess] Opening output stream for $path (vol=$volumeId, rel=$relativePath)")
            return managedStorageAccessor.openOutputStreamAtPath(volumeId, relativePath)
        }

        // Standard file I/O for non-restricted paths
        val file = File(path)
        file.parentFile?.mkdirs()
        return FileOutputStream(file)
    }

    private fun openInputStreamForPath(path: String): InputStream? {
        // For restricted paths, try alt access first
        if (isRestrictedPath(path) && androidDataAccessor.isAltAccessSupported()) {
            val stream = androidDataAccessor.getInputStream(path)
            if (stream != null) {
                Logger.debug(TAG, "[AltAccess] Opened input stream for $path")
                return stream
            }
        }

        // Fallback to ManagedStorageAccessor
        if (isRestrictedPath(path)) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return null
            Logger.debug(TAG, "[ManagedAccess] Opening input stream for $path (vol=$volumeId, rel=$relativePath)")
            return managedStorageAccessor.openInputStreamAtPath(volumeId, relativePath)
        }

        // Standard file I/O
        val file = File(path)
        return if (file.exists() && file.canRead()) file.inputStream() else null
    }

    private fun createDirectoryForPath(path: String): Boolean {
        val isRestricted = isRestrictedPath(path)
        val altAccessSupported = isRestricted && androidDataAccessor.isAltAccessSupported()

        // For restricted paths, try alt access first
        if (altAccessSupported) {
            val result = androidDataAccessor.mkdirs(path)
            if (result) {
                Logger.debug(TAG, "[AltAccess] Created directory: $path")
                return true
            }
            Logger.debug(TAG, "[AltAccess] Failed to create directory, falling back to ManagedAccess | path=$path")
        }

        // Fallback to ManagedStorageAccessor (DocumentsContract approach)
        if (isRestricted) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return false
            Logger.debug(TAG, "[ManagedAccess] Creating directory | path=$path (vol=$volumeId, rel=$relativePath)")
            val result = managedStorageAccessor.createDirectoryAtPath(volumeId, relativePath)
            if (!result) {
                Logger.error(TAG, "[ManagedAccess] Failed to create directory: $path")
            }
            return result
        }

        return File(path).mkdirs()
    }

    /**
     * Get a File object for the given path, using alt access if applicable.
     * Use this for file operations that need direct File access.
     */
    fun getFileForPath(path: String): File {
        return if (isRestrictedPath(path) && androidDataAccessor.isAltAccessSupported()) {
            androidDataAccessor.getFile(path)
        } else {
            File(path)
        }
    }

    /**
     * Check if a file/directory exists at the given path, handling restricted paths.
     */
    fun existsAtPath(path: String): Boolean {
        if (isRestrictedPath(path) && androidDataAccessor.isAltAccessSupported()) {
            return androidDataAccessor.exists(path)
        }
        if (isRestrictedPath(path)) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return false
            return managedStorageAccessor.existsAtPath(volumeId, relativePath)
        }
        return File(path).exists()
    }

    /**
     * List files at the given path, handling restricted paths.
     */
    fun listFilesAtPath(path: String): Array<File>? {
        if (isRestrictedPath(path) && androidDataAccessor.isAltAccessSupported()) {
            return androidDataAccessor.listFiles(path)
        }
        // For restricted paths without alt access support, we can't easily return File objects
        // from DocumentsContract, so just try direct access
        return File(path).listFiles()
    }

    /**
     * Get last modified time for a file, handling restricted paths.
     */
    fun lastModifiedAtPath(path: String): Long {
        if (isRestrictedPath(path) && androidDataAccessor.isAltAccessSupported()) {
            return androidDataAccessor.lastModified(path)
        }
        return File(path).lastModified()
    }

    /**
     * Zip a folder at the given path, handling restricted Android/data paths.
     */
    fun zipFolderAtPath(sourcePath: String, targetZip: File): Boolean {
        val sourceFolder = getFileForPath(sourcePath)
        return zipFolder(sourceFolder, targetZip)
    }

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
            ZipArchiveOutputStream(BufferedOutputStream(FileOutputStream(targetZip))).use { zos ->
                zos.setUseZip64(Zip64Mode.AsNeeded)
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

    private fun zipFolderRecursive(folder: File, parentPath: String, zos: ZipArchiveOutputStream) {
        val files = folder.listFiles() ?: return

        for (file in files) {
            val entryPath = "$parentPath/${file.name}"

            if (file.isDirectory) {
                zos.putArchiveEntry(ZipArchiveEntry("$entryPath/"))
                zos.closeArchiveEntry()
                zipFolderRecursive(file, entryPath, zos)
            } else {
                zos.putArchiveEntry(ZipArchiveEntry(entryPath))
                BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { bis ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var count: Int
                    while (bis.read(buffer, 0, BUFFER_SIZE).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                }
                zos.closeArchiveEntry()
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
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(sourceZip))).use { zis ->
                var entry: ZipArchiveEntry?
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
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
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
            Logger.warn(TAG, "[SaveSync] ARCHIVE | Source zip invalid | path=${sourceZip.absolutePath}")
            return false
        }

        val targetPath = targetFolder.absolutePath
        val isRestricted = isRestrictedPath(targetPath)

        // For restricted paths, extract to temp then copy via UC Data path
        if (isRestricted) {
            return unzipViaTemp(sourceZip, targetFolder)
        }

        // Non-restricted paths: extract directly
        return unzipDirect(sourceZip, targetFolder)
    }

    private fun unzipViaTemp(sourceZip: File, targetFolder: File): Boolean {
        val tempDir = File(sourceZip.parentFile, "extract_${System.currentTimeMillis()}")
        Logger.debug(TAG, "[SaveSync] ARCHIVE | Extracting to temp | temp=${tempDir.name}, target=${targetFolder.absolutePath}")

        try {
            // Step 1: Extract to temp directory (unrestricted cache)
            if (!unzipDirect(sourceZip, tempDir)) {
                Logger.error(TAG, "[SaveSync] ARCHIVE | Failed to extract to temp")
                tempDir.deleteRecursively()
                return false
            }

            // Step 2: Move from temp to target via UC Data path
            val moved = androidDataAccessor.moveDirectory(tempDir.absolutePath, targetFolder.absolutePath)
            if (!moved) {
                Logger.error(TAG, "[SaveSync] ARCHIVE | Failed to move to target via AltAccess | target=${targetFolder.absolutePath}")
                tempDir.deleteRecursively()
                return false
            }

            Logger.debug(TAG, "[SaveSync] ARCHIVE | Unzip via temp complete | target=${targetFolder.name}")
            return true
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | unzipViaTemp failed | ${e.message}")
            tempDir.deleteRecursively()
            return false
        }
    }

    private fun unzipDirect(sourceZip: File, targetFolder: File): Boolean {
        return try {
            targetFolder.mkdirs()
            var fileCount = 0
            var totalSize = 0L
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(sourceZip))).use { zis ->
                var entry: ZipArchiveEntry?
                val buffer = ByteArray(BUFFER_SIZE)
                var rootFolder: String? = null

                while (zis.nextEntry.also { entry = it } != null) {
                    val entryName = entry!!.name

                    if (rootFolder == null) {
                        rootFolder = entryName.substringBefore('/').takeIf { entryName.contains('/') }
                    }

                    val relativePath = if (rootFolder != null && entryName.startsWith("$rootFolder/")) {
                        entryName.removePrefix("$rootFolder/")
                    } else {
                        entryName
                    }

                    if (relativePath.isEmpty()) continue

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
                            }
                        }
                        fileCount++
                    }
                }
            }
            Logger.debug(TAG, "[SaveSync] ARCHIVE | Extracted | files=$fileCount, size=${totalSize}bytes")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | Extract failed | ${e.message}")
            false
        }
    }

    fun isJksvFormat(zipFile: File): Boolean {
        if (!zipFile.exists() || !zipFile.isFile) return false

        return try {
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipArchiveEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    if (entry!!.name == JKSV_META_FILE) {
                        return@use true
                    }
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
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipArchiveEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    if (entry!!.name == JKSV_META_FILE) {
                        val metaBytes = zis.readBytes()
                        return@use parseTitleIdFromMeta(metaBytes)
                    }
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

        val isRestricted = isRestrictedPath(targetFolder.absolutePath)
        if (isRestricted) {
            return unzipViaTempExcluding(sourceZip, targetFolder, excludeFiles)
        }
        return unzipDirectExcluding(sourceZip, targetFolder, excludeFiles)
    }

    private fun unzipViaTempExcluding(sourceZip: File, targetFolder: File, excludeFiles: Set<String>): Boolean {
        val tempDir = File(sourceZip.parentFile, "extract_${System.currentTimeMillis()}")
        try {
            if (!unzipDirectExcluding(sourceZip, tempDir, excludeFiles)) {
                tempDir.deleteRecursively()
                return false
            }
            val moved = androidDataAccessor.moveDirectory(tempDir.absolutePath, targetFolder.absolutePath)
            if (!moved) {
                Logger.error(TAG, "[SaveSync] ARCHIVE | Failed to move to target | target=${targetFolder.absolutePath}")
                tempDir.deleteRecursively()
                return false
            }
            return true
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            return false
        }
    }

    private fun unzipDirectExcluding(sourceZip: File, targetFolder: File, excludeFiles: Set<String>): Boolean {
        return try {
            targetFolder.mkdirs()
            var fileCount = 0
            var totalSize = 0L
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(sourceZip))).use { zis ->
                var entry: ZipArchiveEntry?
                val buffer = ByteArray(BUFFER_SIZE)
                var rootFolder: String? = null

                while (zis.nextEntry.also { entry = it } != null) {
                    val entryName = entry!!.name

                    if (rootFolder == null) {
                        rootFolder = entryName.substringBefore('/').takeIf { entryName.contains('/') }
                    }

                    val relativePath = if (rootFolder != null && entryName.startsWith("$rootFolder/")) {
                        entryName.removePrefix("$rootFolder/")
                    } else {
                        entryName
                    }

                    if (relativePath.isEmpty()) continue

                    val fileName = relativePath.substringAfterLast('/')
                    if (excludeFiles.contains(fileName)) continue

                    val entryFile = File(targetFolder, relativePath)
                    if (!entryFile.canonicalPath.startsWith(targetFolder.canonicalPath)) return false

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
                            }
                        }
                        fileCount++
                    }
                }
            }
            true
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | Extract failed | ${e.message}")
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

        val isRestricted = isRestrictedPath(targetFolder.absolutePath)
        if (isRestricted) {
            return unzipViaTempPreserving(sourceZip, targetFolder, excludeFiles)
        }
        return unzipDirectPreserving(sourceZip, targetFolder, excludeFiles)
    }

    private fun unzipViaTempPreserving(sourceZip: File, targetFolder: File, excludeFiles: Set<String>): Boolean {
        val tempDir = File(sourceZip.parentFile, "extract_${System.currentTimeMillis()}")
        try {
            if (!unzipDirectPreserving(sourceZip, tempDir, excludeFiles)) {
                tempDir.deleteRecursively()
                return false
            }
            val moved = androidDataAccessor.moveDirectory(tempDir.absolutePath, targetFolder.absolutePath)
            if (!moved) {
                Logger.error(TAG, "[SaveSync] ARCHIVE | Failed to move to target | target=${targetFolder.absolutePath}")
                tempDir.deleteRecursively()
                return false
            }
            return true
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            return false
        }
    }

    private fun unzipDirectPreserving(sourceZip: File, targetFolder: File, excludeFiles: Set<String>): Boolean {
        return try {
            targetFolder.mkdirs()
            var fileCount = 0
            var totalSize = 0L
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(sourceZip))).use { zis ->
                var entry: ZipArchiveEntry?
                val buffer = ByteArray(BUFFER_SIZE)

                while (zis.nextEntry.also { entry = it } != null) {
                    val entryName = entry!!.name

                    val fileName = entryName.substringAfterLast('/')
                    if (excludeFiles.contains(fileName)) continue

                    val entryFile = File(targetFolder, entryName)
                    if (!entryFile.canonicalPath.startsWith(targetFolder.canonicalPath)) return false

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
                            }
                        }
                        fileCount++
                    }
                }
            }
            true
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] ARCHIVE | Extract failed | ${e.message}")
            false
        }
    }

    /**
     * Write bytes to a file, using UC Data path for restricted locations.
     */
    fun writeBytesToPath(path: String, data: ByteArray): Boolean {
        return if (isRestrictedPath(path)) {
            androidDataAccessor.writeBytes(path, data)
        } else {
            try {
                val file = File(path)
                file.parentFile?.mkdirs()
                file.writeBytes(data)
                true
            } catch (e: Exception) { false }
        }
    }

    /**
     * Copy a file to target path, using UC Data path for restricted locations.
     */
    fun copyFileToPath(source: File, targetPath: String): Boolean {
        return if (isRestrictedPath(targetPath)) {
            androidDataAccessor.copyFile(source.absolutePath, targetPath)
        } else {
            try {
                val target = File(targetPath)
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
                true
            } catch (e: Exception) { false }
        }
    }

    /**
     * Calculate file hash at a path, handling restricted Android/data paths.
     */
    fun calculateFileHashAtPath(path: String): String {
        val file = getFileForPath(path)
        return calculateFileHash(file)
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

    /**
     * Calculate folder hash at a path, handling restricted Android/data paths.
     */
    fun calculateFolderHashAtPath(path: String): String {
        val folder = getFileForPath(path)
        return calculateFolderHash(folder)
    }

    fun calculateFolderHash(folder: File): String {
        val md = MessageDigest.getInstance("MD5")
        folder.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(folder).path }
            .forEach { file ->
                md.update(file.relativeTo(folder).path.toByteArray())
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        md.update(buffer, 0, bytesRead)
                    }
                }
            }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    data class HardcoreTrailer(val version: Int)

    fun appendHardcoreTrailer(file: File): Boolean {
        if (!file.exists() || !file.isFile) {
            Logger.warn(TAG, "[SaveSync] TRAILER | Cannot append trailer - file invalid: ${file.absolutePath}")
            return false
        }

        return try {
            val json = """{"h":true,"v":1}"""
            val jsonBytes = json.toByteArray(Charsets.UTF_8)
            val lenBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(jsonBytes.size)
                .array()

            file.appendBytes(jsonBytes)
            file.appendBytes(lenBytes)
            file.appendBytes(TRAILER_MAGIC)

            Logger.debug(TAG, "[SaveSync] TRAILER | Appended hardcore trailer to ${file.name} (${jsonBytes.size + 12} bytes)")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] TRAILER | Failed to append trailer to ${file.absolutePath}", e)
            false
        }
    }

    fun readHardcoreTrailer(file: File): HardcoreTrailer? {
        if (!file.exists() || !file.isFile) return null
        if (file.length() < TRAILER_MIN_SIZE) return null

        return try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()
                if (fileLen < TRAILER_MIN_SIZE) return null

                raf.seek(fileLen - TRAILER_MAGIC.size)
                val magic = ByteArray(TRAILER_MAGIC.size)
                raf.readFully(magic)
                if (!magic.contentEquals(TRAILER_MAGIC)) return null

                raf.seek(fileLen - TRAILER_MAGIC.size - 4)
                val lenBytes = ByteArray(4)
                raf.readFully(lenBytes)
                val jsonLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                if (jsonLen < 0 || jsonLen > 1024) return null
                if (fileLen < TRAILER_MIN_SIZE + jsonLen) return null

                raf.seek(fileLen - TRAILER_MAGIC.size - 4 - jsonLen)
                val jsonBytes = ByteArray(jsonLen)
                raf.readFully(jsonBytes)

                val json = String(jsonBytes, Charsets.UTF_8)
                if (!json.contains("\"h\":true")) return null

                val version = Regex("\"v\":(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                Logger.debug(TAG, "[SaveSync] TRAILER | Read hardcore trailer from ${file.name}: version=$version")
                HardcoreTrailer(version = version)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] TRAILER | Failed to read trailer from ${file.absolutePath}", e)
            null
        }
    }

    fun hasHardcoreTrailer(file: File): Boolean = readHardcoreTrailer(file) != null

    fun getTrailerSize(file: File): Long {
        if (!file.exists() || !file.isFile) return 0
        if (file.length() < TRAILER_MIN_SIZE) return 0

        return try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()

                raf.seek(fileLen - TRAILER_MAGIC.size)
                val magic = ByteArray(TRAILER_MAGIC.size)
                raf.readFully(magic)
                if (!magic.contentEquals(TRAILER_MAGIC)) return 0

                raf.seek(fileLen - TRAILER_MAGIC.size - 4)
                val lenBytes = ByteArray(4)
                raf.readFully(lenBytes)
                val jsonLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                if (jsonLen < 0 || jsonLen > 1024) return 0
                if (fileLen < TRAILER_MIN_SIZE + jsonLen) return 0

                (TRAILER_MAGIC.size + 4 + jsonLen).toLong()
            }
        } catch (e: Exception) {
            0
        }
    }

    fun readBytesWithoutTrailer(file: File): ByteArray? {
        if (!file.exists() || !file.isFile) return null

        return try {
            val trailerSize = getTrailerSize(file)
            val contentSize = file.length() - trailerSize

            if (contentSize <= 0) return null

            java.io.RandomAccessFile(file, "r").use { raf ->
                val bytes = ByteArray(contentSize.toInt())
                raf.readFully(bytes)
                bytes
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] TRAILER | Failed to read bytes without trailer: ${file.absolutePath}", e)
            null
        }
    }

    companion object {
        private const val JKSV_META_FILE = ".nx_save_meta.bin"
        private const val JKSV_MAGIC = "JKSV"
        private val JKSV_TITLE_ID_OFFSETS = listOf(5, 4, 6)

        private val TRAILER_MAGIC = byteArrayOf(
            'A'.code.toByte(), 'R'.code.toByte(), 'G'.code.toByte(), 'O'.code.toByte(),
            'S'.code.toByte(), 'Y'.code.toByte(), 0x01, 0x00
        )
        private const val TRAILER_MIN_SIZE = 12L
    }
}
