package com.nendo.argosy.data.emulator

import android.content.Context
import android.os.Environment
import com.nendo.argosy.data.remote.github.GitHubApi
import com.nendo.argosy.data.remote.github.GitHubAsset
import com.nendo.argosy.data.remote.github.GitHubRelease
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverFetcherRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class SortMode { Default, PublishTime }

    data class DriverRepo(
        val name: String,
        val path: String,
        val sort: Int,
        val useTagName: Boolean = false,
        val sortMode: SortMode = SortMode.Default
    )

    data class DriverGroup(
        val repo: DriverRepo,
        val releases: List<GitHubRelease>,
        val error: String? = null
    )

    data class GpuInfo(
        val rawModel: String?,
        val adrenoNumber: Int,
        val recommendedDriver: String
    )

    val repos: List<DriverRepo> = listOf(
        DriverRepo("Mr. Purple Turnip", "MrPurple666/purple-turnip", 0),
        DriverRepo("GameHub Adreno 8xx", "crueter/GameHub-8Elite-Drivers", 1),
        DriverRepo("KIMCHI Turnip", "K11MCH1/AdrenoToolsDrivers", 2, useTagName = true, sortMode = SortMode.PublishTime),
        DriverRepo("Weab-Chan Freedreno", "Weab-chan/freedreno_turnip-CI", 3)
    )

    private val driverMap: List<Pair<IntRange, String>> = listOf(
        IntRange(Integer.MIN_VALUE, 9) to "Unsupported",
        IntRange(10, 99) to "KIMCHI Latest",
        IntRange(100, 599) to "Unsupported",
        IntRange(600, 639) to "Mr. Purple EOL-24.3.4",
        IntRange(640, 699) to "Mr. Purple T19",
        IntRange(700, 710) to "KIMCHI 25.2.0_r5",
        IntRange(711, 799) to "Mr. Purple T22",
        IntRange(800, 899) to "GameHub Adreno 8xx",
        IntRange(900, Int.MAX_VALUE) to "Unsupported"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val api: GitHubApi by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE)
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(GitHubApi::class.java)
    }

    fun getGpuInfo(): GpuInfo {
        val raw = readGpuModel()
        val adreno = parseAdrenoModel(raw)
        val recommended = driverMap.firstOrNull { adreno in it.first }?.second ?: "Unsupported"
        return GpuInfo(raw, adreno, recommended)
    }

    private fun readGpuModel(): String? = try {
        File(GPU_MODEL_PATH).readText().trim().takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Logger.debug(TAG, "Could not read GPU model: ${e.message}")
        null
    }

    private fun parseAdrenoModel(rawModel: String?): Int {
        val model = rawModel ?: return 0
        // Adreno sysfs reads like "Adreno730" but human-readable EGL output is "Adreno (TM) 730".
        val number = ADRENO_REGEX.find(model)?.groupValues?.get(1) ?: return 0
        return try {
            if (number.startsWith("A", ignoreCase = true)) number.substring(1).toInt()
            else number.toInt()
        } catch (e: NumberFormatException) {
            0
        }
    }

    suspend fun fetchAllGroups(): List<DriverGroup> = coroutineScope {
        repos.map { repo ->
            async(Dispatchers.IO) { fetchGroup(repo) }
        }.awaitAll().sortedBy { it.repo.sort }
    }

    private suspend fun fetchGroup(repo: DriverRepo): DriverGroup {
        return try {
            val (owner, name) = repo.path.split("/", limit = 2)
            val response = api.getRepoReleases(owner, name, perPage = 30)
            if (!response.isSuccessful) {
                return DriverGroup(repo, emptyList(), "HTTP ${response.code()}")
            }
            val raw = response.body().orEmpty().filterNot { it.draft }
            val sorted = when (repo.sortMode) {
                SortMode.PublishTime -> raw.sortedByDescending {
                    parsePublishedAt(it.publishedAt)
                }
                SortMode.Default -> raw
            }
            DriverGroup(repo, sorted)
        } catch (e: Exception) {
            Logger.warn(TAG, "Driver fetch failed for ${repo.path}: ${e.message}")
            DriverGroup(repo, emptyList(), e.message ?: "Fetch failed")
        }
    }

    private fun parsePublishedAt(value: String?): Long = try {
        value?.let { Instant.parse(it).toEpochMilli() } ?: 0L
    } catch (e: Exception) {
        0L
    }

    fun isLatestNonPrerelease(group: DriverGroup, release: GitHubRelease): Boolean {
        val firstStable = group.releases.firstOrNull { !it.prerelease }
        return firstStable != null && firstStable === release
    }

    suspend fun downloadAsset(
        asset: GitHubAsset,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = resolveTargetFile(asset.name)
        try {
            target.parentFile?.takeIf { !it.exists() }?.mkdirs()
            val request = Request.Builder().url(asset.downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Download failed: HTTP ${response.code}")
                    )
                }
                val body = response.body ?: return@withContext Result.failure(
                    IllegalStateException("Empty response body")
                )
                val total = body.contentLength().takeIf { it > 0 } ?: asset.size
                FileOutputStream(target).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress?.invoke(downloaded, total)
                        }
                    }
                }
            }
            Result.success(target)
        } catch (e: Exception) {
            Logger.error(TAG, "Driver download error", e)
            Result.failure(e)
        }
    }

    fun listDownloadedFiles(): List<File> {
        val dir = driversDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun driversDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DRIVERS_SUBDIR)

    private fun resolveTargetFile(fileName: String): File = File(driversDir(), fileName)

    companion object {
        private const val TAG = "DriverFetcherRepository"
        private const val GITHUB_API_BASE = "https://api.github.com/"
        private const val GPU_MODEL_PATH = "/sys/class/kgsl/kgsl-3d0/gpu_model"
        private const val DRIVERS_SUBDIR = "drivers"
        private val ADRENO_REGEX = Regex("""Adreno(?:\s*\(TM\))?\s*(A?\d+)""", RegexOption.IGNORE_CASE)
    }
}
