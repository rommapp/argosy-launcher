package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpuDriverManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    companion object {
        private const val TAG = "GpuDriverManager"
        private const val GITHUB_API_URL = "https://api.github.com/repos/MrPurple666/purple-turnip/releases/latest"
        private const val GPU_MODEL_PATH = "/sys/class/kgsl/kgsl-3d0/gpu_model"

        private val SUPPORTED_ADRENO_GPUS = setOf(
            "Adreno730",   // SD8 Gen 1
            "Adreno740",   // SD8 Gen 2
            "Adreno750",   // SD8 Gen 3
            "Adreno830"    // SD8 Elite
        )
    }

    data class DriverInfo(
        val name: String,
        val version: String,
        val downloadUrl: String,
        val fileName: String
    )

    fun getDeviceGpu(): String? {
        return try {
            File(GPU_MODEL_PATH).readText().trim()
        } catch (e: Exception) {
            Logger.debug(TAG, "Could not read GPU model: ${e.message}")
            null
        }
    }

    fun isAdrenoGpuSupported(): Boolean {
        val gpu = getDeviceGpu() ?: return false
        return SUPPORTED_ADRENO_GPUS.any { gpu.startsWith(it, ignoreCase = true) }
    }

    suspend fun getLatestDriverInfo(): DriverInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.warn(TAG, "Failed to fetch driver info: ${response.code}")
                    return@withContext null
                }

                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val tagName = json.getString("tag_name")
                val releaseName = json.getString("name")

                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val fileName = asset.getString("name")
                    if (fileName.endsWith(".adpkg.zip")) {
                        return@withContext DriverInfo(
                            name = releaseName,
                            version = tagName,
                            downloadUrl = asset.getString("browser_download_url"),
                            fileName = fileName
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Error fetching driver info", e)
            null
        }
    }

    suspend fun downloadAndInstallDriver(
        driverInfo: DriverInfo,
        edenPackage: String,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val edenBasePath = BiosPathRegistry.getEdenDataPath(edenPackage)
            val driversDir = File(edenBasePath, "gpu_drivers")
            if (!driversDir.exists()) driversDir.mkdirs()

            val targetFile = File(driversDir, driverInfo.fileName)

            Logger.info(TAG, "Downloading GPU driver: ${driverInfo.downloadUrl}")

            val request = Request.Builder()
                .url(driverInfo.downloadUrl)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.error(TAG, "Failed to download driver: ${response.code}")
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()

                FileOutputStream(targetFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress?.invoke(downloaded, contentLength)
                        }
                    }
                }
            }

            Logger.info(TAG, "Driver downloaded to: ${targetFile.absolutePath}")

            updateEdenConfig(edenBasePath, targetFile.absolutePath)

            true
        } catch (e: Exception) {
            Logger.error(TAG, "Error downloading/installing driver", e)
            false
        }
    }

    private fun updateEdenConfig(edenBasePath: String, driverPath: String) {
        val configFile = File(edenBasePath, "config/config.ini")

        if (!configFile.exists()) {
            Logger.warn(TAG, "Eden config not found, creating minimal config")
            configFile.parentFile?.mkdirs()
            configFile.writeText("""
                [GpuDriver]
                driver_path\default=false
                driver_path=$driverPath
            """.trimIndent())
            return
        }

        val lines = configFile.readLines().toMutableList()
        var inGpuDriverSection = false
        var driverPathIndex = -1
        var driverPathDefaultIndex = -1
        var gpuDriverSectionIndex = -1

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed == "[GpuDriver]") {
                inGpuDriverSection = true
                gpuDriverSectionIndex = index
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inGpuDriverSection = false
            } else if (inGpuDriverSection) {
                if (trimmed.startsWith("driver_path\\default=")) {
                    driverPathDefaultIndex = index
                } else if (trimmed.startsWith("driver_path=")) {
                    driverPathIndex = index
                }
            }
        }

        if (gpuDriverSectionIndex == -1) {
            lines.add("")
            lines.add("[GpuDriver]")
            lines.add("driver_path\\default=false")
            lines.add("driver_path=$driverPath")
        } else {
            if (driverPathDefaultIndex != -1) {
                lines[driverPathDefaultIndex] = "driver_path\\default=false"
            } else {
                lines.add(gpuDriverSectionIndex + 1, "driver_path\\default=false")
                if (driverPathIndex != -1) driverPathIndex++
            }

            if (driverPathIndex != -1) {
                lines[driverPathIndex] = "driver_path=$driverPath"
            } else {
                val insertIndex = if (driverPathDefaultIndex != -1) {
                    driverPathDefaultIndex + 1
                } else {
                    gpuDriverSectionIndex + 2
                }
                lines.add(insertIndex, "driver_path=$driverPath")
            }
        }

        configFile.writeText(lines.joinToString("\n"))
        Logger.info(TAG, "Updated Eden config with driver path: $driverPath")
    }

    fun installDriverFromFile(filePath: String, edenPackage: String): Boolean {
        return try {
            val edenBasePath = BiosPathRegistry.getEdenDataPath(edenPackage)
            val driversDir = File(edenBasePath, "gpu_drivers")
            if (!driversDir.exists()) driversDir.mkdirs()

            val sourceFile = File(filePath)
            val targetFile = File(driversDir, sourceFile.name)

            sourceFile.copyTo(targetFile, overwrite = true)

            Logger.info(TAG, "Driver copied to: ${targetFile.absolutePath}")

            updateEdenConfig(edenBasePath, targetFile.absolutePath)

            true
        } catch (e: Exception) {
            Logger.error(TAG, "Error installing driver from file", e)
            false
        }
    }

    fun getInstalledDriverPath(edenPackage: String): String? {
        val edenBasePath = BiosPathRegistry.getEdenDataPath(edenPackage)
        val configFile = File(edenBasePath, "config/config.ini")

        if (!configFile.exists()) return null

        return try {
            configFile.readLines()
                .firstOrNull { it.trim().startsWith("driver_path=") && !it.contains("default") }
                ?.substringAfter("=")
                ?.trim()
                ?.takeIf { File(it).exists() }
        } catch (e: Exception) {
            null
        }
    }

    fun hasInstalledDriver(edenPackage: String): Boolean {
        return getInstalledDriverPath(edenPackage) != null
    }
}
