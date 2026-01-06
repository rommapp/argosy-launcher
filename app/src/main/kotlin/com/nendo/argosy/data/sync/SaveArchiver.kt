package com.nendo.argosy.data.sync

import com.nendo.argosy.util.Logger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
            Logger.warn(TAG, "Source folder does not exist or is not a directory: ${sourceFolder.absolutePath}")
            return false
        }

        return try {
            targetZip.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZip))).use { zos ->
                zipFolderRecursive(sourceFolder, sourceFolder.name, zos)
            }
            Logger.debug(TAG, "Successfully zipped ${sourceFolder.absolutePath} to ${targetZip.absolutePath}")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to zip folder: ${sourceFolder.absolutePath}", e)
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
        if (!zipFile.exists() || !zipFile.isFile) return null

        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                val firstEntry = zis.nextEntry ?: return null
                val entryName = firstEntry.name
                entryName.substringBefore('/').takeIf {
                    it.isNotEmpty() && entryName.contains('/')
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to peek ZIP root folder", e)
            null
        }
    }

    fun unzipSingleFolder(sourceZip: File, targetFolder: File): Boolean {
        if (!sourceZip.exists() || !sourceZip.isFile) {
            Logger.warn(TAG, "Source zip does not exist or is not a file: ${sourceZip.absolutePath}")
            return false
        }

        return try {
            targetFolder.mkdirs()
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
                        Logger.error(TAG, "Zip path traversal detected: $entryName")
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
            Logger.debug(TAG, "Successfully unzipped ${sourceZip.absolutePath} contents to ${targetFolder.absolutePath}")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to unzip file: ${sourceZip.absolutePath}", e)
            false
        }
    }
}
