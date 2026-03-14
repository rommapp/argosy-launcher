package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreSystemDataManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun ensureCoreSystemData(coreId: String, systemDir: File): Boolean {
        return when (coreId) {
            "dolphin" -> ensureDolphinSysData(systemDir)
            else -> true
        }
    }

    fun needsSystemData(coreId: String, systemDir: File): Boolean {
        return when (coreId) {
            "dolphin" -> {
                val versionFile = File(systemDir, "$DOLPHIN_SYS_PATH/.argosy_version")
                if (!versionFile.exists()) return true
                val installed = versionFile.readText().trim().toIntOrNull()
                installed != BuildConfig.DOLPHIN_SYS_VERSION
            }
            else -> false
        }
    }

    private suspend fun ensureDolphinSysData(systemDir: File): Boolean = withContext(Dispatchers.IO) {
        val targetDir = File(systemDir, DOLPHIN_SYS_PATH)
        val versionFile = File(targetDir, ".argosy_version")
        val currentVersion = BuildConfig.DOLPHIN_SYS_VERSION

        if (versionFile.exists()) {
            val installed = versionFile.readText().trim().toIntOrNull()
            if (installed == currentVersion) return@withContext true
        }

        Logger.info(TAG, "Downloading Dolphin Sys data (version $currentVersion)")

        try {
            downloadAndExtract(DOLPHIN_SYS_URL, systemDir)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to download Dolphin Sys data: ${e.message}")
            return@withContext false
        }

        versionFile.parentFile?.mkdirs()
        versionFile.writeText(currentVersion.toString())
        Logger.info(TAG, "Dolphin Sys data installed successfully")
        true
    }

    private fun downloadAndExtract(url: String, destDir: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP $responseCode from $url")
            }

            connection.inputStream.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val outFile = File(destDir, entry.name)
                        if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                            throw SecurityException("Zip entry outside target: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out -> zip.copyTo(out) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "CoreSystemData"
        private const val DOLPHIN_SYS_URL =
            "https://buildbot.libretro.com/assets/system/Dolphin.zip"
        private const val DOLPHIN_SYS_PATH = "dolphin-emu/Sys"
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
