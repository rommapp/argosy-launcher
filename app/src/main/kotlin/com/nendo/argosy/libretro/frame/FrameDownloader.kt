package com.nendo.argosy.libretro.frame

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class FrameDownloader(private val framesDir: File) {

    data class DownloadResult(
        val downloaded: Int,
        val failed: Int,
        val alreadyInstalled: Int
    )

    suspend fun downloadFrame(entry: FrameRegistry.FrameEntry): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                framesDir.mkdirs()
                val targetFile = File(framesDir, "${entry.id}.png")
                val url = FrameRegistry.downloadUrl(entry)

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000

                try {
                    if (connection.responseCode != 200) {
                        throw FrameDownloadException(
                            "HTTP ${connection.responseCode} for ${entry.id}"
                        )
                    }
                    connection.inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetFile
                } finally {
                    connection.disconnect()
                }
            }
        }

    suspend fun downloadFrames(
        entries: List<FrameRegistry.FrameEntry>,
        registry: FrameRegistry
    ): DownloadResult = withContext(Dispatchers.IO) {
        val missing = entries.filter { !registry.isInstalled(it) }
        val installed = entries.size - missing.size

        if (missing.isEmpty()) {
            return@withContext DownloadResult(0, 0, installed)
        }

        var downloaded = 0
        var failed = 0

        for (entry in missing) {
            val result = downloadFrame(entry)
            if (result.isSuccess) {
                downloaded++
            } else {
                failed++
                Log.w(TAG, "Failed to download ${entry.id}: ${result.exceptionOrNull()?.message}")
            }
        }

        registry.invalidateInstalledCache()
        DownloadResult(downloaded, failed, installed)
    }

    fun isInstalled(frameId: String): Boolean =
        File(framesDir, "$frameId.png").exists()

    class FrameDownloadException(message: String) : Exception(message)

    companion object {
        private const val TAG = "FrameDownloader"
    }
}
