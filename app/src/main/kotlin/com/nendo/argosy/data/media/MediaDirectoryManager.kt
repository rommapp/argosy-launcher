package com.nendo.argosy.data.media

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.util.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaDirectoryManager"

/**
 * Resolves the directory downloaded movies and episodes live in, and moves that tree when the
 * user picks a different location. Internal storage is the default; an override is used verbatim
 * so an SD card or USB volume keeps whatever layout the user chose.
 */
@Singleton
class MediaDirectoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storagePreferences: StoragePreferencesRepository
) {

    fun defaultMediaDir(): File = AppPaths.mediaDir(context.filesDir)

    suspend fun resolveMediaDir(): File {
        val override = storagePreferences.preferences.first().mediaStoragePath
        return if (override.isNullOrBlank()) defaultMediaDir() else File(override)
    }

    suspend fun targetFileFor(libraryName: String, itemPath: String, fileName: String): File {
        val extension = fileName.substringAfterLast('.', "")
        val baseName = sanitize(fileName.substringBeforeLast('.'))
        val targetName = if (extension.isNotEmpty()) "$baseName.$extension" else baseName
        val itemDir = itemPath.split('/')
            .filter { it.isNotBlank() }
            .fold(File(resolveMediaDir(), sanitize(libraryName))) { dir, segment ->
                File(dir, sanitize(segment))
            }
        return File(itemDir, targetName)
    }

    suspend fun countFiles(): Int = withContext(Dispatchers.IO) {
        val dir = resolveMediaDir()
        if (dir.exists()) dir.walkTopDown().count { it.isFile } else 0
    }

    /**
     * Moves the media tree into [destination] preserving structure; conflicts are overwritten.
     */
    suspend fun relocate(source: File, destination: File): Unit = withContext(Dispatchers.IO) {
        if (!source.exists() || source.absolutePath == destination.absolutePath) return@withContext
        moveTree(source, destination)
    }

    private fun moveTree(source: File, destination: File) {
        destination.mkdirs()
        val files = source.listFiles() ?: return
        for (file in files) {
            val target = File(destination, file.name)
            try {
                if (file.isDirectory) {
                    moveTree(file, target)
                } else {
                    if (target.exists()) target.delete()
                    if (!file.renameTo(target)) {
                        file.copyTo(target, overwrite = true)
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to move ${file.name}: ${e.message}")
            }
        }
        if (source.listFiles()?.isEmpty() == true) source.delete()
    }

    private fun sanitize(name: String): String =
        name.replace(INVALID_CHARS, "_").trim().ifEmpty { "item" }

    companion object {
        private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|;=]")
    }
}
