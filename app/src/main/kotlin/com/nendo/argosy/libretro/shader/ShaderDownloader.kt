package com.nendo.argosy.libretro.shader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ShaderDownloader(private val catalogDir: File) {

    data class SyncResult(
        val downloaded: Int,
        val failed: Int,
        val alreadyInstalled: Int
    )

    suspend fun downloadShader(entry: ShaderRegistry.ShaderEntry): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                catalogDir.mkdirs()
                val targetFile = File(catalogDir, "${entry.id}.glsl")
                val url = ShaderRegistry.downloadUrl(entry)

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000

                try {
                    if (connection.responseCode != 200) {
                        throw ShaderDownloadException(
                            "HTTP ${connection.responseCode} for ${entry.id}"
                        )
                    }
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    targetFile.writeText(content)
                    targetFile
                } finally {
                    connection.disconnect()
                }
            }
        }

    suspend fun syncPreInstalls(
        registry: ShaderRegistry
    ): SyncResult = withContext(Dispatchers.IO) {
        val missing = registry.getMissingPreInstalls()
        val installed = registry.getPreInstalls().size - missing.size

        if (missing.isEmpty()) {
            return@withContext SyncResult(0, 0, installed)
        }

        var downloaded = 0
        var failed = 0

        for (entry in missing) {
            val result = downloadShader(entry)
            if (result.isSuccess) {
                downloaded++
            } else {
                failed++
                Log.w(TAG, "Failed to download ${entry.id}: ${result.exceptionOrNull()?.message}")
            }
        }

        SyncResult(downloaded, failed, installed)
    }

    fun isInstalled(shaderId: String): Boolean {
        return File(catalogDir, "$shaderId.glsl").exists()
    }

    class ShaderDownloadException(message: String) : Exception(message)

    companion object {
        private const val TAG = "ShaderDownloader"
    }
}
