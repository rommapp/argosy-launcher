package com.nendo.argosy.data.cache

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import coil.imageLoader
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ImageCacheManager"

data class ImageCacheRequest(
    val url: String,
    val id: Long,
    val type: ImageType,
    val gameTitle: String = "",
    val isSteam: Boolean = false
)

enum class ImageType { BACKGROUND, SCREENSHOT, COVER }

data class ImageCacheProgress(
    val isProcessing: Boolean = false,
    val currentGameTitle: String = "",
    val currentType: String = "",
    val cachedCount: Int = 0,
    val totalCount: Int = 0
) {
    val progressPercent: Int
        get() = if (totalCount > 0) {
            val percent = cachedCount * 100 / totalCount
            if (isProcessing && percent == 100) 99 else percent
        } else 0
}

data class ScreenshotCacheRequest(
    val gameId: Long,
    val rommId: Long,
    val screenshotUrls: List<String>,
    val gameTitle: String = ""
)

data class PlatformLogoCacheRequest(
    val platformId: Long,
    val logoUrl: String
)

data class AchievementBadgeCacheRequest(
    val achievementId: Long,
    val badgeUrl: String,
    val badgeUrlLock: String?
)

data class AppIconCacheRequest(
    val gameId: Long,
    val packageName: String
)

data class CacheValidationResult(
    val deletedFiles: Int,
    val clearedPaths: Int
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class ImageCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val achievementDao: AchievementDao
) {
    private val defaultCacheDir: File by lazy {
        File(context.cacheDir, "images").also { it.mkdirs() }
    }

    private var customCacheBasePath: String? = null

    private val cacheDir: File
        get() {
            val custom = customCacheBasePath
            return if (custom != null) {
                File(custom, CACHE_SUBFOLDER).also { it.mkdirs() }
            } else {
                defaultCacheDir
            }
        }

    fun setCustomCachePath(path: String?) {
        customCacheBasePath = path
        if (path != null) {
            File(path, CACHE_SUBFOLDER).mkdirs()
        }
        Log.d(TAG, "Custom cache base path set to: $path")
    }

    fun getCustomCachePath(): String? = customCacheBasePath

    fun getDefaultCachePath(): String = defaultCacheDir.absolutePath

    fun getCurrentCachePath(): String = cacheDir.absolutePath

    companion object {
        private const val CACHE_SUBFOLDER = "argosy_images"
    }

    private val logoQueue = Channel<PlatformLogoCacheRequest>(Channel.UNLIMITED)
    private val coverQueue = Channel<ImageCacheRequest>(Channel.UNLIMITED)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<ImageCacheRequest>(Channel.UNLIMITED)
    private val screenshotQueue = Channel<ScreenshotCacheRequest>(Channel.UNLIMITED)
    private var isProcessing = false
    private var isProcessingScreenshots = false
    private var isProcessingCovers = false

    private val _progress = kotlinx.coroutines.flow.MutableStateFlow(ImageCacheProgress())
    val progress: kotlinx.coroutines.flow.StateFlow<ImageCacheProgress> = _progress

    private val _screenshotProgress = kotlinx.coroutines.flow.MutableStateFlow(ImageCacheProgress())
    val screenshotProgress: kotlinx.coroutines.flow.StateFlow<ImageCacheProgress> = _screenshotProgress

    private var isPaused = false

    fun pauseBackgroundCaching() {
        isPaused = true
        Log.d(TAG, "Background caching paused")
    }

    fun resumeBackgroundCaching() {
        isPaused = false
        Log.d(TAG, "Background caching resumed")
    }

    fun queueBackgroundCache(url: String, rommId: Long, gameTitle: String = "") {
        scope.launch {
            queue.send(ImageCacheRequest(url, rommId, ImageType.BACKGROUND, gameTitle, isSteam = false))
            startProcessingIfNeeded()
        }
    }

    fun queueSteamBackgroundCache(url: String, steamAppId: Long, gameTitle: String = "") {
        scope.launch {
            queue.send(ImageCacheRequest(url, steamAppId, ImageType.BACKGROUND, gameTitle, isSteam = true))
            startProcessingIfNeeded()
        }
    }

    private fun startProcessingIfNeeded() {
        if (isProcessing) return
        isProcessing = true

        scope.launch {
            Log.d(TAG, "Starting background image cache processing")
            updateProgressFromDb(isProcessing = true)

            for (request in queue) {
                while (isPaused) {
                    kotlinx.coroutines.delay(500)
                }
                try {
                    _progress.value = _progress.value.copy(
                        currentGameTitle = request.gameTitle,
                        currentType = if (request.type == ImageType.BACKGROUND) "background" else "cover"
                    )
                    processRequest(request)
                    updateProgressFromDb(isProcessing = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process ${request.id}: ${e.message}")
                }

                if (queue.isEmpty) {
                    _progress.value = ImageCacheProgress()
                }
            }
            isProcessing = false
            _progress.value = ImageCacheProgress()
        }
    }

    private suspend fun updateProgressFromDb(isProcessing: Boolean) {
        val total = gameDao.countGamesWithBackgrounds()
        val cached = gameDao.countGamesWithCachedBackgrounds()
        _progress.value = _progress.value.copy(
            isProcessing = isProcessing,
            cachedCount = cached,
            totalCount = total
        )
    }

    private suspend fun processRequest(request: ImageCacheRequest) {
        val prefix = if (request.isSteam) "steam_bg" else "bg"
        val fileName = "${prefix}_${request.id}_${request.url.md5Hash()}.jpg"
        val cachedFile = File(cacheDir, fileName)

        if (cachedFile.exists()) {
            if (isValidImageFile(cachedFile)) {
                updateGameBackground(request.id, cachedFile.absolutePath, request.isSteam)
                return
            } else {
                cachedFile.delete()
                Log.w(TAG, "Deleted invalid cached background: ${cachedFile.name}")
            }
        }

        val bitmap = downloadAndResize(request.url, 640) ?: return

        FileOutputStream(cachedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        bitmap.recycle()

        if (!isValidImageFile(cachedFile)) {
            cachedFile.delete()
            Log.w(TAG, "Deleted newly cached invalid background: ${cachedFile.name}")
            return
        }

        val idLabel = if (request.isSteam) "steamAppId" else "rommId"
        Log.d(TAG, "Cached background for $idLabel ${request.id}: ${cachedFile.length() / 1024}KB")
        updateGameBackground(request.id, cachedFile.absolutePath, request.isSteam)
    }

    private suspend fun updateGameBackground(id: Long, localPath: String, isSteam: Boolean) {
        val game = if (isSteam) {
            gameDao.getBySteamAppId(id)
        } else {
            gameDao.getByRommId(id)
        } ?: return
        if (game.backgroundPath?.startsWith("/") == true) return
        gameDao.updateBackgroundPath(game.id, localPath)
    }

    private fun downloadAndResize(url: String, maxWidth: Int): Bitmap? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000

            val inputStream = connection.getInputStream()

            val tempFile = File.createTempFile("img_", ".tmp", cacheDir)
            try {
                tempFile.outputStream().use { out ->
                    inputStream.copyTo(out)
                }

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(tempFile.absolutePath, options)

                val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxWidth)
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize

                val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options)
                    ?: return null

                if (bitmap.width > maxWidth) {
                    val ratio = maxWidth.toFloat() / bitmap.width
                    val newHeight = (bitmap.height * ratio).toInt()
                    val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
                    if (scaled != bitmap) bitmap.recycle()
                    return scaled
                }

                bitmap
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download image from $url: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun calculateSampleSize(width: Int, height: Int, maxWidth: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxWidth * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun String.md5Hash(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    private fun isValidImageFile(file: File, minSizeBytes: Long = 1024): Boolean {
        if (!file.exists() || file.length() < minSizeBytes) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun deleteGameImages(rommId: Long) {
        withContext(Dispatchers.IO) {
            val prefixes = listOf("cover_${rommId}_", "bg_${rommId}_", "ss_${rommId}_")
            cacheDir.listFiles()?.forEach { file ->
                if (prefixes.any { prefix -> file.name.startsWith(prefix) }) {
                    file.delete()
                    Log.d(TAG, "Deleted cached image: ${file.name}")
                }
            }
        }
        withContext(Dispatchers.Main) {
            context.imageLoader.memoryCache?.clear()
            Log.d(TAG, "Cleared Coil memory cache after deleting images for rommId $rommId")
        }
    }

    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun getCacheSizeForBasePath(basePath: String): Long {
        val dir = if (basePath == defaultCacheDir.absolutePath) {
            File(basePath)
        } else {
            File(basePath, CACHE_SUBFOLDER)
        }
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun getCacheFileCount(): Int {
        return cacheDir.listFiles()?.size ?: 0
    }

    fun getCacheFileCountForBasePath(basePath: String): Int {
        val dir = if (basePath == defaultCacheDir.absolutePath) {
            File(basePath)
        } else {
            File(basePath, CACHE_SUBFOLDER)
        }
        return dir.listFiles()?.size ?: 0
    }

    suspend fun migrateCache(
        fromBasePath: String,
        toBasePath: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val sourceDir = if (fromBasePath == defaultCacheDir.absolutePath) {
            File(fromBasePath)
        } else {
            File(fromBasePath, CACHE_SUBFOLDER)
        }
        val destDir = if (toBasePath == defaultCacheDir.absolutePath) {
            File(toBasePath).also { it.mkdirs() }
        } else {
            File(toBasePath, CACHE_SUBFOLDER).also { it.mkdirs() }
        }

        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            Log.w(TAG, "Source directory does not exist: ${sourceDir.absolutePath}")
            return@withContext false
        }

        val files = sourceDir.listFiles() ?: return@withContext false
        val total = files.size
        var copied = 0
        var failed = 0

        Log.d(TAG, "Starting cache migration: $total files from ${sourceDir.absolutePath} to ${destDir.absolutePath}")

        files.forEach { sourceFile ->
            try {
                val destFile = File(destDir, sourceFile.name)
                sourceFile.copyTo(destFile, overwrite = true)
                copied++
                onProgress(copied, total)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy ${sourceFile.name}: ${e.message}")
                failed++
            }
        }

        Log.d(TAG, "Cache migration complete: $copied copied, $failed failed")

        if (failed == 0) {
            files.forEach { it.delete() }
            Log.d(TAG, "Source files deleted after successful migration")
            updateDatabasePaths(sourceDir.absolutePath, destDir.absolutePath)
        }

        failed == 0
    }

    private suspend fun updateDatabasePaths(oldBasePath: String, newBasePath: String) {
        val games = gameDao.getAllGames()
        var updated = 0

        games.forEach { game ->
            var changed = false
            var newCoverPath = game.coverPath
            var newBackgroundPath = game.backgroundPath
            var newCachedScreenshotPaths = game.cachedScreenshotPaths

            if (game.coverPath?.startsWith(oldBasePath) == true) {
                newCoverPath = game.coverPath.replace(oldBasePath, newBasePath)
                changed = true
            }
            if (game.backgroundPath?.startsWith(oldBasePath) == true) {
                newBackgroundPath = game.backgroundPath.replace(oldBasePath, newBasePath)
                changed = true
            }
            if (game.cachedScreenshotPaths?.contains(oldBasePath) == true) {
                newCachedScreenshotPaths = game.cachedScreenshotPaths.replace(oldBasePath, newBasePath)
                changed = true
            }

            if (changed) {
                gameDao.updateImagePaths(game.id, newCoverPath, newBackgroundPath, newCachedScreenshotPaths)
                updated++
            }
        }

        val platforms = platformDao.getAllPlatforms()
        platforms.forEach { platform ->
            if (platform.logoPath?.startsWith(oldBasePath) == true) {
                val newLogoPath = platform.logoPath.replace(oldBasePath, newBasePath)
                platformDao.updateLogoPath(platform.id, newLogoPath)
                updated++
            }
        }

        Log.d(TAG, "Updated $updated database paths from $oldBasePath to $newBasePath")
    }

    fun getPendingCount(): Int = queue.isEmpty.let { if (it) 0 else -1 }

    fun resumePendingCache() {
        scope.launch {
            val uncached = gameDao.getGamesWithUncachedBackgrounds()
            if (uncached.isEmpty()) return@launch

            Log.d(TAG, "Resuming cache for ${uncached.size} games with uncached backgrounds")
            uncached.forEach { game ->
                val url = game.backgroundPath ?: return@forEach
                when {
                    game.steamAppId != null -> queueSteamBackgroundCache(url, game.steamAppId, game.title)
                    game.rommId != null -> queueBackgroundCache(url, game.rommId, game.title)
                }
            }
        }
    }

    fun queueScreenshotCache(gameId: Long, rommId: Long, screenshotUrls: List<String>, gameTitle: String) {
        scope.launch {
            screenshotQueue.send(ScreenshotCacheRequest(gameId, rommId, screenshotUrls, gameTitle))
            startScreenshotProcessingIfNeeded()
        }
    }

    fun queueScreenshotCacheByGameId(gameId: Long, screenshotUrls: List<String>) {
        scope.launch {
            val cachedPaths = mutableListOf<String>()

            screenshotUrls.forEachIndexed { index, url ->
                val fileName = "ss_g${gameId}_${index}_${url.md5Hash()}.jpg"
                val cachedFile = File(cacheDir, fileName)

                if (cachedFile.exists()) {
                    if (isValidImageFile(cachedFile)) {
                        cachedPaths.add(cachedFile.absolutePath)
                        return@forEachIndexed
                    } else {
                        cachedFile.delete()
                        Log.w(TAG, "Deleted invalid cached screenshot: ${cachedFile.name}")
                    }
                }

                val bitmap = downloadAndResize(url, 480) ?: return@forEachIndexed

                FileOutputStream(cachedFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                }
                bitmap.recycle()

                if (!isValidImageFile(cachedFile)) {
                    cachedFile.delete()
                    Log.w(TAG, "Deleted newly cached invalid screenshot: ${cachedFile.name}")
                    return@forEachIndexed
                }

                Log.d(TAG, "Cached screenshot $index for gameId $gameId: ${cachedFile.length() / 1024}KB")
                cachedPaths.add(cachedFile.absolutePath)
            }

            if (cachedPaths.isNotEmpty()) {
                gameDao.updateCachedScreenshotPaths(gameId, cachedPaths.joinToString(","))

                val game = gameDao.getById(gameId)
                if (game != null && (game.backgroundPath == null || !game.backgroundPath.startsWith("/"))) {
                    gameDao.updateBackgroundPath(gameId, cachedPaths.first())
                    Log.d(TAG, "Set first screenshot as background for gameId $gameId")
                }
            }
        }
    }

    private fun startScreenshotProcessingIfNeeded() {
        if (isProcessingScreenshots) return
        isProcessingScreenshots = true

        scope.launch {
            Log.d(TAG, "Starting screenshot cache processing")
            updateScreenshotProgressFromDb(isProcessing = true)

            for (request in screenshotQueue) {
                while (isPaused) {
                    kotlinx.coroutines.delay(500)
                }
                try {
                    _screenshotProgress.value = _screenshotProgress.value.copy(
                        currentGameTitle = request.gameTitle,
                        currentType = "screenshots"
                    )
                    processScreenshotRequest(request)
                    updateScreenshotProgressFromDb(isProcessing = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process screenshots for ${request.rommId}: ${e.message}")
                }

                if (screenshotQueue.isEmpty) {
                    _screenshotProgress.value = ImageCacheProgress()
                }
            }
            isProcessingScreenshots = false
            _screenshotProgress.value = ImageCacheProgress()
        }
    }

    private suspend fun updateScreenshotProgressFromDb(isProcessing: Boolean) {
        val total = gameDao.countGamesWithScreenshots()
        val cached = gameDao.countGamesWithCachedScreenshots()
        _screenshotProgress.value = _screenshotProgress.value.copy(
            isProcessing = isProcessing,
            cachedCount = cached,
            totalCount = total
        )
    }

    private suspend fun processScreenshotRequest(request: ScreenshotCacheRequest) {
        val cachedPaths = mutableListOf<String>()

        request.screenshotUrls.forEachIndexed { index, url ->
            val fileName = "ss_${request.rommId}_${index}_${url.md5Hash()}.jpg"
            val cachedFile = File(cacheDir, fileName)

            if (cachedFile.exists()) {
                if (isValidImageFile(cachedFile)) {
                    cachedPaths.add(cachedFile.absolutePath)
                    return@forEachIndexed
                } else {
                    cachedFile.delete()
                    Log.w(TAG, "Deleted invalid cached screenshot: ${cachedFile.name}")
                }
            }

            val bitmap = downloadAndResize(url, 480) ?: return@forEachIndexed

            FileOutputStream(cachedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            bitmap.recycle()

            if (!isValidImageFile(cachedFile)) {
                cachedFile.delete()
                Log.w(TAG, "Deleted newly cached invalid screenshot: ${cachedFile.name}")
                return@forEachIndexed
            }

            Log.d(TAG, "Cached screenshot $index for rommId ${request.rommId}: ${cachedFile.length() / 1024}KB")
            cachedPaths.add(cachedFile.absolutePath)
        }

        if (cachedPaths.isNotEmpty()) {
            gameDao.updateCachedScreenshotPaths(request.gameId, cachedPaths.joinToString(","))
        }
    }

    fun resumePendingScreenshotCache() {
        scope.launch {
            val uncached = gameDao.getGamesWithUncachedScreenshots()
            if (uncached.isEmpty()) return@launch

            Log.d(TAG, "Resuming cache for ${uncached.size} games with uncached screenshots")
            uncached.forEach { game ->
                val urls = game.screenshotPaths?.split(",") ?: return@forEach
                val rommId = game.rommId ?: return@forEach
                queueScreenshotCache(game.id, rommId, urls, game.title)
            }
        }
    }

    fun queuePlatformLogoCache(platformId: Long, logoUrl: String) {
        scope.launch {
            logoQueue.send(PlatformLogoCacheRequest(platformId, logoUrl))
            startLogoProcessingIfNeeded()
        }
    }

    private var isProcessingLogos = false

    private fun startLogoProcessingIfNeeded() {
        if (isProcessingLogos) return
        isProcessingLogos = true

        scope.launch {
            Log.d(TAG, "Starting platform logo cache processing")

            for (request in logoQueue) {
                while (isPaused) {
                    kotlinx.coroutines.delay(500)
                }
                try {
                    processLogoRequest(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process logo for ${request.platformId}: ${e.message}")
                }

                if (logoQueue.isEmpty) break
            }
            isProcessingLogos = false
        }
    }

    private suspend fun processLogoRequest(request: PlatformLogoCacheRequest) {
        val fileName = "logo_${request.platformId}_${request.logoUrl.md5Hash()}.png"
        val cachedFile = File(cacheDir, fileName)

        if (cachedFile.exists()) {
            if (isValidImageFile(cachedFile, minSizeBytes = 512)) {
                platformDao.updateLogoPath(request.platformId, cachedFile.absolutePath)
                return
            } else {
                cachedFile.delete()
                Log.w(TAG, "Deleted invalid cached logo: ${cachedFile.name}")
            }
        }

        val bitmap = downloadBitmap(request.logoUrl) ?: return
        val transparentBitmap = removeBlackBackground(bitmap)
        bitmap.recycle()

        FileOutputStream(cachedFile).use { out ->
            transparentBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        transparentBitmap.recycle()

        if (!isValidImageFile(cachedFile, minSizeBytes = 512)) {
            cachedFile.delete()
            Log.w(TAG, "Deleted newly cached invalid logo: ${cachedFile.name}")
            return
        }

        Log.d(TAG, "Cached logo for platform ${request.platformId}: ${cachedFile.length() / 1024}KB")
        platformDao.updateLogoPath(request.platformId, cachedFile.absolutePath)
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.getInputStream().use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download bitmap from $url: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    private fun removeBlackBackground(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            // Check if pixel is near-black (threshold of 30 for each channel)
            if (r < 30 && g < 30 && b < 30) {
                pixels[i] = Color.TRANSPARENT
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    fun resumePendingLogoCache() {
        scope.launch {
            val uncached = platformDao.getPlatformsWithRemoteLogos()
            if (uncached.isEmpty()) return@launch

            Log.d(TAG, "Resuming cache for ${uncached.size} platforms with uncached logos")
            uncached.forEach { platform ->
                val url = platform.logoPath ?: return@forEach
                queuePlatformLogoCache(platform.id, url)
            }
        }
    }

    fun queueCoverCache(url: String, rommId: Long, gameTitle: String = "") {
        scope.launch {
            coverQueue.send(ImageCacheRequest(url, rommId, ImageType.COVER, gameTitle, isSteam = false))
            startCoverProcessingIfNeeded()
        }
    }

    fun queueCoverCacheByGameId(url: String, gameId: Long) {
        scope.launch {
            val fileName = "cover_g${gameId}_${url.md5Hash()}.jpg"
            val cachedFile = File(cacheDir, fileName)

            if (cachedFile.exists()) {
                if (isValidImageFile(cachedFile)) {
                    gameDao.updateCoverPath(gameId, cachedFile.absolutePath)
                    return@launch
                } else {
                    cachedFile.delete()
                    Log.w(TAG, "Deleted invalid cached cover: ${cachedFile.name}")
                }
            }

            val bitmap = downloadAndResize(url, 400) ?: return@launch

            FileOutputStream(cachedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()

            if (!isValidImageFile(cachedFile)) {
                cachedFile.delete()
                Log.w(TAG, "Deleted newly cached invalid cover: ${cachedFile.name}")
                return@launch
            }

            Log.d(TAG, "Cached cover for gameId $gameId: ${cachedFile.length() / 1024}KB")
            gameDao.updateCoverPath(gameId, cachedFile.absolutePath)
        }
    }

    private fun startCoverProcessingIfNeeded() {
        if (isProcessingCovers) return
        isProcessingCovers = true

        scope.launch {
            Log.d(TAG, "Starting cover image cache processing")

            for (request in coverQueue) {
                while (isPaused) {
                    kotlinx.coroutines.delay(500)
                }
                try {
                    _progress.value = _progress.value.copy(
                        currentGameTitle = request.gameTitle,
                        currentType = "cover"
                    )
                    processCoverRequest(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process cover for ${request.id}: ${e.message}")
                }

                if (coverQueue.isEmpty) break
            }
            isProcessingCovers = false
        }
    }

    private suspend fun processCoverRequest(request: ImageCacheRequest) {
        val fileName = "cover_${request.id}_${request.url.md5Hash()}.jpg"
        val cachedFile = File(cacheDir, fileName)

        if (cachedFile.exists()) {
            if (isValidImageFile(cachedFile)) {
                updateGameCover(request.id, cachedFile.absolutePath)
                return
            } else {
                cachedFile.delete()
                Log.w(TAG, "Deleted invalid cached cover: ${cachedFile.name}")
            }
        }

        val bitmap = downloadAndResize(request.url, 400) ?: return

        FileOutputStream(cachedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        bitmap.recycle()

        if (!isValidImageFile(cachedFile)) {
            cachedFile.delete()
            Log.w(TAG, "Deleted newly cached invalid cover: ${cachedFile.name}")
            return
        }

        Log.d(TAG, "Cached cover for rommId ${request.id}: ${cachedFile.length() / 1024}KB")
        updateGameCover(request.id, cachedFile.absolutePath)
    }

    private suspend fun updateGameCover(rommId: Long, localPath: String) {
        val game = gameDao.getByRommId(rommId) ?: return
        if (game.coverPath?.startsWith("/") == true) return
        gameDao.updateCoverPath(game.id, localPath)
    }

    fun resumePendingCoverCache() {
        scope.launch {
            val uncached = gameDao.getGamesWithUncachedCovers()
            if (uncached.isEmpty()) return@launch

            Log.d(TAG, "Resuming cache for ${uncached.size} games with uncached covers")
            uncached.forEach { game ->
                val url = game.coverPath ?: return@forEach
                val rommId = game.rommId ?: return@forEach
                queueCoverCache(url, rommId, game.title)
            }
        }
    }

    private val badgeQueue = Channel<AchievementBadgeCacheRequest>(Channel.UNLIMITED)
    private var isProcessingBadges = false

    fun queueBadgeCache(achievementId: Long, badgeUrl: String, badgeUrlLock: String?) {
        scope.launch {
            badgeQueue.send(AchievementBadgeCacheRequest(achievementId, badgeUrl, badgeUrlLock))
            startBadgeProcessingIfNeeded()
        }
    }

    private fun startBadgeProcessingIfNeeded() {
        if (isProcessingBadges) return
        isProcessingBadges = true

        scope.launch {
            Log.d(TAG, "Starting achievement badge cache processing")

            for (request in badgeQueue) {
                while (isPaused) {
                    kotlinx.coroutines.delay(500)
                }
                try {
                    processBadgeRequest(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process badge for achievement ${request.achievementId}: ${e.message}")
                }

                if (badgeQueue.isEmpty) break
            }
            isProcessingBadges = false
        }
    }

    private suspend fun processBadgeRequest(request: AchievementBadgeCacheRequest) {
        val unlockedFileName = "badge_${request.achievementId}_${request.badgeUrl.md5Hash()}.png"
        val unlockedFile = File(cacheDir, unlockedFileName)

        if (unlockedFile.exists()) {
            if (isValidImageFile(unlockedFile, minSizeBytes = 512)) {
                achievementDao.updateCachedBadgeUrl(request.achievementId, unlockedFile.absolutePath)
            } else {
                unlockedFile.delete()
                Log.w(TAG, "Deleted invalid cached badge: ${unlockedFile.name}")
            }
        }

        if (!unlockedFile.exists()) {
            val bitmap = downloadBitmap(request.badgeUrl)
            if (bitmap != null) {
                FileOutputStream(unlockedFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()

                if (isValidImageFile(unlockedFile, minSizeBytes = 512)) {
                    Log.d(TAG, "Cached unlocked badge for achievement ${request.achievementId}")
                    achievementDao.updateCachedBadgeUrl(request.achievementId, unlockedFile.absolutePath)
                } else {
                    unlockedFile.delete()
                    Log.w(TAG, "Deleted newly cached invalid badge: ${unlockedFile.name}")
                }
            }
        }

        if (request.badgeUrlLock != null) {
            val lockedFileName = "badge_lock_${request.achievementId}_${request.badgeUrlLock.md5Hash()}.png"
            val lockedFile = File(cacheDir, lockedFileName)

            if (lockedFile.exists()) {
                if (isValidImageFile(lockedFile, minSizeBytes = 512)) {
                    achievementDao.updateCachedBadgeUrlLock(request.achievementId, lockedFile.absolutePath)
                } else {
                    lockedFile.delete()
                    Log.w(TAG, "Deleted invalid cached locked badge: ${lockedFile.name}")
                }
            }

            if (!lockedFile.exists()) {
                val bitmap = downloadBitmap(request.badgeUrlLock)
                if (bitmap != null) {
                    FileOutputStream(lockedFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bitmap.recycle()

                    if (isValidImageFile(lockedFile, minSizeBytes = 512)) {
                        Log.d(TAG, "Cached locked badge for achievement ${request.achievementId}")
                        achievementDao.updateCachedBadgeUrlLock(request.achievementId, lockedFile.absolutePath)
                    } else {
                        lockedFile.delete()
                        Log.w(TAG, "Deleted newly cached invalid locked badge: ${lockedFile.name}")
                    }
                }
            }
        }
    }

    fun resumePendingBadgeCache() {
        scope.launch {
            val uncached = achievementDao.getWithUncachedBadges()
            if (uncached.isEmpty()) return@launch

            Log.d(TAG, "Resuming cache for ${uncached.size} achievements with uncached badges")
            uncached.forEach { achievement ->
                val url = achievement.badgeUrl ?: return@forEach
                queueBadgeCache(achievement.id, url, achievement.badgeUrlLock)
            }
        }
    }

    suspend fun setScreenshotAsBackground(gameId: Long, screenshotPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sourceBitmap = if (screenshotPath.startsWith("/")) {
                    BitmapFactory.decodeFile(screenshotPath)
                } else {
                    downloadBitmap(screenshotPath)
                } ?: return@withContext false

                val resizedBitmap = if (sourceBitmap.width > 640) {
                    val ratio = 640f / sourceBitmap.width
                    val newHeight = (sourceBitmap.height * ratio).toInt()
                    val scaled = Bitmap.createScaledBitmap(sourceBitmap, 640, newHeight, true)
                    if (scaled != sourceBitmap) sourceBitmap.recycle()
                    scaled
                } else {
                    sourceBitmap
                }

                val fileName = "bg_custom_${gameId}_${System.currentTimeMillis()}.jpg"
                val cachedFile = File(cacheDir, fileName)

                FileOutputStream(cachedFile).use { out ->
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                resizedBitmap.recycle()

                gameDao.updateBackgroundPath(gameId, cachedFile.absolutePath)
                Log.d(TAG, "Set screenshot as background for game $gameId: ${cachedFile.length() / 1024}KB")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set screenshot as background: ${e.message}", e)
                false
            }
        }
    }

    private val appIconQueue = Channel<AppIconCacheRequest>(Channel.UNLIMITED)
    private var isProcessingAppIcons = false
    private val packageManager: PackageManager by lazy { context.packageManager }

    fun queueAppIconCache(gameId: Long, packageName: String) {
        scope.launch {
            appIconQueue.send(AppIconCacheRequest(gameId, packageName))
            startAppIconProcessingIfNeeded()
        }
    }

    private fun startAppIconProcessingIfNeeded() {
        if (isProcessingAppIcons) return
        isProcessingAppIcons = true

        scope.launch {
            Log.d(TAG, "Starting app icon cache processing")

            for (request in appIconQueue) {
                while (isPaused) {
                    kotlinx.coroutines.delay(500)
                }
                try {
                    processAppIconRequest(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process app icon for ${request.packageName}: ${e.message}")
                }

                if (appIconQueue.isEmpty) break
            }
            isProcessingAppIcons = false
        }
    }

    private suspend fun processAppIconRequest(request: AppIconCacheRequest) {
        val fileName = "appicon_${request.packageName.hashCode()}.png"
        val cachedFile = File(cacheDir, fileName)

        if (cachedFile.exists()) {
            if (isValidImageFile(cachedFile, minSizeBytes = 512)) {
                gameDao.updateCoverPath(request.gameId, cachedFile.absolutePath)
                return
            } else {
                cachedFile.delete()
                Log.w(TAG, "Deleted invalid cached app icon: ${cachedFile.name}")
            }
        }

        val icon = try {
            val appInfo = packageManager.getApplicationInfo(request.packageName, 0)
            packageManager.getApplicationIcon(appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: ${request.packageName}")
            return
        }

        val bitmap = drawableToBitmap(icon, 256)

        withContext(Dispatchers.IO) {
            FileOutputStream(cachedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        bitmap.recycle()

        if (!isValidImageFile(cachedFile, minSizeBytes = 512)) {
            cachedFile.delete()
            Log.w(TAG, "Deleted newly cached invalid app icon: ${cachedFile.name}")
            return
        }

        Log.d(TAG, "Cached app icon for ${request.packageName}: ${cachedFile.length() / 1024}KB")
        gameDao.updateCoverPath(request.gameId, cachedFile.absolutePath)
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val original = drawable.bitmap
            return if (original.width != size || original.height != size) {
                Bitmap.createScaledBitmap(original, size, size, true)
            } else {
                original.copy(Bitmap.Config.ARGB_8888, false)
            }
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    suspend fun cacheAppIconSync(packageName: String): String? {
        return withContext(Dispatchers.IO) {
            val fileName = "appicon_${packageName.hashCode()}.png"
            val cachedFile = File(cacheDir, fileName)

            if (cachedFile.exists()) {
                if (isValidImageFile(cachedFile, minSizeBytes = 512)) {
                    return@withContext cachedFile.absolutePath
                } else {
                    cachedFile.delete()
                    Log.w(TAG, "Deleted invalid cached app icon: ${cachedFile.name}")
                }
            }

            val icon = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationIcon(appInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Package not found: $packageName")
                return@withContext null
            }

            val bitmap = drawableToBitmap(icon, 256)

            FileOutputStream(cachedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            if (!isValidImageFile(cachedFile, minSizeBytes = 512)) {
                cachedFile.delete()
                Log.w(TAG, "Deleted newly cached invalid app icon: ${cachedFile.name}")
                return@withContext null
            }

            Log.d(TAG, "Cached app icon for $packageName: ${cachedFile.length() / 1024}KB")
            cachedFile.absolutePath
        }
    }

    suspend fun validateAndCleanCache(): CacheValidationResult {
        var deleted = 0
        var cleared = 0

        withContext(Dispatchers.IO) {
            cacheDir.listFiles()?.forEach { file ->
                if (!isValidImageFile(file, minSizeBytes = 512)) {
                    file.delete()
                    deleted++
                    Log.w(TAG, "Validation deleted invalid file: ${file.name}")
                }
            }
        }

        val games = gameDao.getAllGames()
        games.forEach { game ->
            if (game.coverPath?.startsWith("/") == true && !File(game.coverPath).exists()) {
                gameDao.clearCoverPath(game.id)
                cleared++
            }
            if (game.backgroundPath?.startsWith("/") == true && !File(game.backgroundPath).exists()) {
                gameDao.clearBackgroundPath(game.id)
                cleared++
            }
            if (game.cachedScreenshotPaths != null) {
                val paths = game.cachedScreenshotPaths.split(",")
                val validPaths = paths.filter { path ->
                    !path.startsWith("/") || File(path).exists()
                }
                if (validPaths.size != paths.size) {
                    if (validPaths.isEmpty()) {
                        gameDao.clearCachedScreenshotPaths(game.id)
                    } else {
                        gameDao.updateCachedScreenshotPaths(game.id, validPaths.joinToString(","))
                    }
                    cleared += paths.size - validPaths.size
                }
            }
        }

        val platforms = platformDao.getAllPlatforms()
        platforms.forEach { platform ->
            if (platform.logoPath?.startsWith("/") == true && !File(platform.logoPath).exists()) {
                platformDao.clearLogoPath(platform.id)
                cleared++
            }
        }

        withContext(Dispatchers.Main) {
            context.imageLoader.memoryCache?.clear()
        }

        Log.i(TAG, "Cache validation complete: $deleted files deleted, $cleared paths cleared")
        return CacheValidationResult(deleted, cleared)
    }
}
