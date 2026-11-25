package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.emulator.config.ConfigPatchers
import com.nendo.argosy.data.emulator.config.EdenConfigSpec
import com.nendo.argosy.data.emulator.config.EmulatorEditLocks
import com.nendo.argosy.data.emulator.savepath.IniSections
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpuDriverManager @Inject constructor(
    private val fal: FileAccessLayer,
    private val editLocks: EmulatorEditLocks
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
        private const val DRIVER_PACKAGE_SUFFIX = ".adpkg"
        private const val DRIVERS_DIR = EdenConfigSpec.DRIVERS_DIR
        private const val CONFIG_FILE = "config/config.ini"

        private val SUPPORTED_ADRENO_GPUS = setOf(
            "Adreno730",   // SD8 Gen 1
            "Adreno740",   // SD8 Gen 2
            "Adreno750",   // SD8 Gen 3
            "Adreno830"    // SD8 Elite
        )
    }

    data class DriverInfo(
        val name: String,
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
                            name = releaseName.removeSuffix(DRIVER_PACKAGE_SUFFIX),
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
            val targetPath = "${requireDriversDir(edenBasePath)}/${driverInfo.fileName}"

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

                val target = fal.getOutputStream(targetPath)
                    ?: throw java.io.IOException("Cannot write $targetPath")
                target.use { output ->
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

            Logger.info(TAG, "Driver downloaded to: $targetPath")

            updateEdenConfig(edenPackage, edenBasePath, targetPath)

            true
        } catch (e: Exception) {
            Logger.error(TAG, "Error downloading/installing driver", e)
            false
        }
    }

    private fun requireDriversDir(edenBasePath: String): String {
        val driversDir = "$edenBasePath/$DRIVERS_DIR"
        if (!fal.mkdirs(driversDir) && !fal.isDirectory(driversDir)) {
            throw java.io.IOException("Cannot create $driversDir")
        }
        return driversDir
    }

    private fun requireCommitted(edenBasePath: String) {
        val paths = arrayOf("$edenBasePath/$DRIVERS_DIR", "$edenBasePath/${CONFIG_FILE.substringBeforeLast('/')}")
        if (!fal.commitSaveAccess(*paths)) {
            throw java.io.IOException("Cannot copy into Eden's folder | paths=${paths.joinToString()}")
        }
    }

    private fun readEdenConfig(edenBasePath: String): String? =
        fal.readBytes("$edenBasePath/$CONFIG_FILE")?.toString(Charsets.UTF_8)

    private suspend fun updateEdenConfig(edenPackage: String, edenBasePath: String, driverPath: String) {
        editLocks.withEdit(edenPackage) {
            val configPath = "$edenBasePath/$CONFIG_FILE"
            val existing = readEdenConfig(edenBasePath)
            if (existing == null) {
                Logger.warn(TAG, "Eden config not found, creating minimal config")
                fal.mkdirs(configPath.substringBeforeLast('/'))
            }
            writeEdenConfig(configPath, EdenDriverConfig.withDriverPath(existing.orEmpty(), driverPath))
            Logger.info(TAG, "Updated Eden config with driver path: $driverPath")
            requireCommitted(edenBasePath)
        }
    }

    private fun writeEdenConfig(configPath: String, content: String) {
        if (!fal.writeBytes(configPath, content.toByteArray(Charsets.UTF_8))) {
            throw java.io.IOException("Cannot write $configPath")
        }
    }

    suspend fun installDriverFromFile(filePath: String, edenPackage: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val edenBasePath = BiosPathRegistry.getEdenDataPath(edenPackage)
            val targetPath = "${requireDriversDir(edenBasePath)}/${File(filePath).name}"

            if (!fal.copyFile(filePath, targetPath)) {
                throw java.io.IOException("Cannot copy $filePath to $targetPath")
            }

            Logger.info(TAG, "Driver copied to: $targetPath")

            updateEdenConfig(edenPackage, edenBasePath, targetPath)

            true
        } catch (e: Exception) {
            Logger.error(TAG, "Error installing driver from file", e)
            false
        }
    }

    suspend fun hasInstalledDriver(edenPackage: String): Boolean = withContext(Dispatchers.IO) {
        val edenBasePath = BiosPathRegistry.getEdenDataPath(edenPackage)
        val hasDriverFile = fal.fileSizes("$edenBasePath/$DRIVERS_DIR").orEmpty().isNotEmpty()
        hasDriverFile || configuredDriverPath(edenBasePath) != null
    }

    private fun configuredDriverPath(edenBasePath: String): String? =
        readEdenConfig(edenBasePath)
            ?.let(EdenDriverConfig::driverPath)
            ?.takeIf { it.isNotEmpty() && fal.exists(it) }
}

internal object EdenDriverConfig {
    private val SPEC = EdenConfigSpec.SPEC
    private val OPTION = EdenConfigSpec.DRIVER_PATH

    fun withDriverPath(text: String, driverPath: String): String =
        requireNotNull(ConfigPatchers.patch(SPEC, OPTION, driverPath, text))

    fun driverPath(text: String): String? =
        IniSections.entries(text, OPTION.section)[OPTION.key]?.trim()
}
