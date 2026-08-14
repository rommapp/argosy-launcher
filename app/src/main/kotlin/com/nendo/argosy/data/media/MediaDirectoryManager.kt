package com.nendo.argosy.data.media

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.util.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val relocationMutex = Mutex()

    /**
     * Serialises whatever must not see a half-moved tree. Moving the files and repointing the rows
     * that name them are one operation from outside: between the two, every stored path names a file
     * that has already left, and a reader that concluded those files were deleted would clear records
     * that are about to be corrected.
     */
    suspend fun <T> underRelocationLock(block: suspend () -> T): T = relocationMutex.withLock { block() }

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
     * Moves the media tree into [destination], preserving structure.
     *
     * The destination can be a folder the user already keeps files in, and can sit inside the source.
     * Neither costs them anything: a name already taken is freed by renaming the incumbent rather
     * than deleting it, and the destination itself is stepped over as the walk reaches it, so the
     * move never descends into its own output.
     */
    suspend fun relocate(source: File, destination: File): Unit = withContext(Dispatchers.IO) {
        if (!source.exists() || source.absolutePath == destination.absolutePath) return@withContext
        moveTree(source, destination, destination.absolutePath)
    }

    private fun moveTree(source: File, destination: File, destinationRoot: String) {
        destination.mkdirs()
        val files = source.listFiles() ?: return
        for (file in files) {
            if (file.absolutePath == destinationRoot) continue
            val target = File(destination, file.name)
            try {
                if (file.isDirectory) {
                    moveTree(file, target, destinationRoot)
                } else {
                    moveFile(file, target)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to move ${file.name}: ${e.message}")
            }
        }
        if (source.listFiles()?.isEmpty() == true) source.delete()
    }

    private fun moveFile(file: File, target: File) {
        if (target.exists() && !displace(target)) {
            Log.w(TAG, "Left ${file.name} in place; ${target.name} is occupied")
            return
        }
        if (!file.renameTo(target)) {
            file.copyTo(target, overwrite = false)
            file.delete()
        }
    }

    /**
     * Frees a name by renaming whatever holds it to the next unused variant, so a collision costs
     * the user a rename rather than a file.
     */
    private fun displace(target: File): Boolean {
        val extension = target.name.substringAfterLast('.', "")
        val base = target.name.substringBeforeLast('.', target.name)
        for (index in 1..MAX_DISPLACEMENT_ATTEMPTS) {
            val name = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            val candidate = File(target.parentFile, name)
            if (!candidate.exists() && target.renameTo(candidate)) return true
        }
        return false
    }

    private fun sanitize(name: String): String =
        name.replace(INVALID_CHARS, "_").trim().ifEmpty { "item" }

    companion object {
        private const val MAX_DISPLACEMENT_ATTEMPTS = 99
        private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|;=]")
    }
}
