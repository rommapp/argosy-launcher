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
import com.nendo.argosy.data.model.GameSource
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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
    val isSteam: Boolean = false,
    val gameId: Long? = null
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
    private val _localCoverWritten = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 64)
    val localCoverWritten: SharedFlow<Pair<Long, String>> = _localCoverWritten.asSharedFlow()

    private val defaultCacheDir: File by lazy {
        File(context.filesDir, "images").also {
            it.mkdirs()
            ensureNoMedia(it)
        }
    }

    private val legacyImagesDir: File get() = File(context.cacheDir, "images")
    private val legacySteamDir: File get() = File(context.cacheDir, "steam")
    private val steamCoverDir: File get() = File(context.filesDir, "steam")

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
            File(path, CACHE_SUBFOLDER).also {
                it.mkdirs()
                ensureNoMedia(it)
            }
        }
        Log.d(TAG, "Custom cache base path set to: $path")
    }

    fun getCustomCachePath(): String? = customCacheBasePath

    fun getDefaultCachePath(): String = defaultCacheDir.absolutePath

    fun getCurrentCachePath(): String = cacheDir.absolutePath

    companion object {
        private const val CACHE_SUBFOLDER = "argosy_images"
        private const val FALLBACK_PLATFORM = "_misc"
        private const val LOGOS_DIR = "_logos"
    }

    private fun ensureNoMedia(dir: File) {
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create .nomedia in ${dir.absolutePath}: ${e.message}")
            }
        }
    }

    private fun platformDir(platformSlug: String, type: String): File {
        return File(File(cacheDir, platformSlug), type).also { it.mkdirs() }
    }

    private fun logosDir(): File {
        return File(cacheDir, LOGOS_DIR).also { it.mkdirs() }
    }

    private suspend fun resolveGamePlatformSlug(gameId: Long): String {
        return gameDao.getById(gameId)?.platformSlug ?: FALLBACK_PLATFORM
    }

    private suspend fun resolveRommPlatformSlug(rommId: Long): String {
        return gameDao.getByRommId(rommId)?.platformSlug ?: FALLBACK_PLATFORM
    }

    private suspend fun resolveSteamPlatformSlug(steamAppId: Long): String {
        return gameDao.getBySteamAppId(steamAppId)?.platformSlug ?: FALLBACK_PLATFORM
    }

    private suspend fun resolveBadgePlatformSlug(achievementId: Long): String {
        val achievement = achievementDao.getById(achievementId) ?: return FALLBACK_PLATFORM
        return resolveGamePlatformSlug(achievement.gameId)
    }

    private val logoQueue = Channel<PlatformLogoCacheRequest>(256)
    private val coverQueue = Channel<ImageCacheRequest>(256)

    private val cacheExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "ImageCacheWorker").apply {
            priority = Thread.MIN_PRIORITY
        }
    }
    private val cacheDispatcher = cacheExecutor.asCoroutineDispatcher()
    private val scope = SafeCoroutineScope(cacheDispatcher, "ImageCacheManager")
    private val queue = Channel<ImageCacheRequest>(256)
    private val screenshotQueue = Channel<ScreenshotCacheRequest>(256)
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

    fun queueBackgroundCacheByGameId(url: String, gameId: Long, gameTitle: String = "") {
        scope.launch {
            queue.send(ImageCacheRequest(url, gameId, ImageType.BACKGROUND, gameTitle, gameId = gameId))
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
                yield()

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
        val isGameIdRequest = request.gameId != null
        val prefix = when {
            isGameIdRequest -> "bg_g${request.gameId}"
            request.isSteam -> "steam_bg_${request.id}"
            else -> "bg_${request.id}"
        }
        val fileName = "${prefix}_${request.url.md5Hash()}.jpg"
        val slug = when {
            isGameIdRequest -> resolveGamePlatformSlug(request.gameId!!)
            request.isSteam -> resolveSteamPlatformSlug(request.id)
            else -> resolveRommPlatformSlug(request.id)
        }
        val cachedFile = File(platformDir(slug, "backgrounds"), fileName)

        if (cachedFile.exists()) {
            if (isValidImageFile(cachedFile)) {
                updateGameBackgroundForRequest(request, cachedFile.absolutePath)
                return
            } else {
                cachedFile.delete()
                Log.w(TAG, "Deleted invalid cached background: ${cachedFile.name}")
            }
        }

        val bitmap = downloadAndResize(request.url, 1280) ?: return

        FileOutputStream(cachedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 87, out)
        }
        bitmap.recycle()

        if (!isValidImageFile(cachedFile)) {
            cachedFile.delete()
            Log.w(TAG, "Deleted newly cached invalid background: ${cachedFile.name}")
            return
        }

        val idLabel = when {
            isGameIdRequest -> "gameId ${request.gameId}"
            request.isSteam -> "steamAppId ${request.id}"
            else -> "rommId ${request.id}"
        }
        Log.d(TAG, "Cached background for $idLabel: ${cachedFile.length() / 1024}KB")
        updateGameBackgroundForRequest(request, cachedFile.absolutePath)
    }

    private suspend fun updateGameBackgroundForRequest(request: ImageCacheRequest, localPath: String) {
        if (request.gameId != null) {
            val game = gameDao.getById(request.gameId) ?: return
            if (game.backgroundPath?.startsWith("/") == true) return
            gameDao.updateBackgroundPath(request.gameId, localPath)
        } else {
            updateGameBackground(request.id, localPath, request.isSteam)
        }
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

            val tempFile = File.createTempFile("img_", ".tmp", cacheDir)
            try {
                connection.getInputStream().use { inputStream ->
                    tempFile.outputStream().use { out ->
                        inputStream.copyTo(out)
                    }
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
        } catch (e: java.io.FileNotFoundException) {
            Log.w(TAG, "Image not found: $url")
            null
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to download image from $url: ${e.javaClass.simpleName}: ${e.message}")
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

    fun isLikelyAppIcon(file: File): Boolean {
        if (!file.exists()) return true
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return true

        val isSquare = kotlin.math.abs(options.outWidth - options.outHeight) < 50
        val isSmall = options.outWidth < 400 || options.outHeight < 400

        return isSquare && isSmall
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { entry ->
            if (entry.isDirectory) {
                entry.deleteRecursively()
            } else if (entry.name != ".nomedia") {
                entry.delete()
            }
        }
    }

    suspend fun deleteGameImages(rommId: Long) {
        withContext(Dispatchers.IO) {
            val slug = resolveRommPlatformSlug(rommId)
            val prefixes = listOf("cover_${rommId}_", "bg_${rommId}_", "ss_${rommId}_")
            val types = listOf("covers", "backgrounds", "screenshots")
            types.forEach { type ->
                val dir = File(File(cacheDir, slug), type)
                dir.listFiles()?.forEach { file ->
                    if (prefixes.any { prefix -> file.name.startsWith(prefix) }) {
                        file.delete()
                        Log.d(TAG, "Deleted cached image: ${file.name}")
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            context.imageLoader.memoryCache?.clear()
            Log.d(TAG, "Cleared Coil memory cache after deleting images for rommId $rommId")
        }
    }

    fun getCacheSize(): Long {
        return cacheDir.walk().filter { it.isFile && it.name != ".nomedia" }.sumOf { it.length() }
    }

    fun getCacheSizeForBasePath(basePath: String): Long {
        val dir = if (basePath == defaultCacheDir.absolutePath) {
            File(basePath)
        } else {
            File(basePath, CACHE_SUBFOLDER)
        }
        return dir.walk().filter { it.isFile && it.name != ".nomedia" }.sumOf { it.length() }
    }

    fun getCacheFileCount(): Int {
        return cacheDir.walk().count { it.isFile && it.name != ".nomedia" }
    }

    fun getCacheFileCountForBasePath(basePath: String): Int {
        val dir = if (basePath == defaultCacheDir.absolutePath) {
            File(basePath)
        } else {
            File(basePath, CACHE_SUBFOLDER)
        }
        return dir.walk().count { it.isFile && it.name != ".nomedia" }
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

        val files = sourceDir.walk().filter { it.isFile && it.name != ".nomedia" }.toList()
        val total = files.size
        var copied = 0
        var failed = 0

        Log.d(TAG, "Starting cache migration: $total files from ${sourceDir.absolutePath} to ${destDir.absolutePath}")

        files.forEach { sourceFile ->
            try {
                val relativePath = sourceFile.relativeTo(sourceDir).path
                val destFile = File(destDir, relativePath)
                destFile.parentFile?.mkdirs()
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
            sourceDir.listFiles()?.forEach { entry ->
                if (entry.isDirectory) entry.deleteRecursively()
                else if (entry.name != ".nomedia") entry.delete()
            }
            Log.d(TAG, "Source files deleted after successful migration")
            updateDatabasePaths(sourceDir.absolutePath, destDir.absolutePath)
        }

        ensureNoMedia(destDir)
        failed == 0
    }

    fun needsLegacyCacheDirsMigration(): Boolean {
        val imagesPending = hasContent(legacyImagesDir) &&
            legacyImagesDir.absolutePath != cacheDir.absolutePath
        return imagesPending || hasContent(legacySteamDir)
    }

    suspend fun migrateLegacyCacheDirs() = withContext(cacheDispatcher) {
        val imagesTarget = cacheDir
        if (hasContent(legacyImagesDir) && legacyImagesDir.absolutePath != imagesTarget.absolutePath) {
            moveLegacyDir(legacyImagesDir, imagesTarget)
        }
        if (hasContent(legacySteamDir)) {
            moveLegacyDir(legacySteamDir, steamCoverDir.also { it.mkdirs() })
        }
    }

    private suspend fun moveLegacyDir(source: File, dest: File) {
        dest.mkdirs()
        val files = source.walk().filter { it.isFile && it.name != ".nomedia" }.toList()
        var failed = 0
        files.forEach { file ->
            try {
                val target = File(dest, file.relativeTo(source).path)
                target.parentFile?.mkdirs()
                if (!file.renameTo(target)) {
                    file.copyTo(target, overwrite = true)
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Legacy cache migration failed for ${file.name}: ${e.message}")
                failed++
            }
        }
        if (failed == 0) {
            source.listFiles()?.forEach { entry ->
                if (entry.isDirectory) entry.deleteRecursively()
                else if (entry.name != ".nomedia") entry.delete()
            }
            updateDatabasePaths(source.absolutePath, dest.absolutePath)
        }
        ensureNoMedia(dest)
    }

    private fun hasContent(dir: File): Boolean =
        dir.isDirectory && (dir.listFiles()?.any { it.name != ".nomedia" } == true)

    private suspend fun updateDatabasePaths(oldBasePath: String, newBasePath: String) {
        val infos = gameDao.getAllImageCacheInfo()
        var updated = 0

        infos.forEach { info ->
            var changed = false
            var newCoverPath = info.coverPath
            var newBackgroundPath = info.backgroundPath
            var newCachedScreenshotPaths = info.cachedScreenshotPaths

            if (info.coverPath?.startsWith(oldBasePath) == true) {
                newCoverPath = info.coverPath.replace(oldBasePath, newBasePath)
                changed = true
            }
            if (info.backgroundPath?.startsWith(oldBasePath) == true) {
                newBackgroundPath = info.backgroundPath.replace(oldBasePath, newBasePath)
                changed = true
            }
            if (info.cachedScreenshotPaths?.contains(oldBasePath) == true) {
                newCachedScreenshotPaths = info.cachedScreenshotPaths.replace(oldBasePath, newBasePath)
                changed = true
            }

            if (changed) {
                gameDao.updateImagePaths(info.id, newCoverPath, newBackgroundPath, newCachedScreenshotPaths)
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
            val slug = resolveGamePlatformSlug(gameId)

            screenshotUrls.forEachIndexed { index, url ->
                val fileName = "ss_g${gameId}_${index}_${url.md5Hash()}.jpg"
                val cachedFile = File(platformDir(slug, "screenshots"), fileName)

                if (cachedFile.exists()) {
                    if (isValidImageFile(cachedFile)) {
                        cachedPaths.add(cachedFile.absolutePath)
                        return@forEachIndexed
                    } else {
                        cachedFile.delete()
                        Log.w(TAG, "Deleted invalid cached screenshot: ${cachedFile.name}")
                    }
                }

                val bitmap = downloadAndResize(url, 960) ?: return@forEachIndexed

                try {
                    FileOutputStream(cachedFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write screenshot cache for gameId $gameId: ${e.message}", e)
                    cachedFile.delete()
                    return@forEachIndexed
                } finally {
                    bitmap.recycle()
                }

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
                    // Use second screenshot (gameplay) if available, otherwise first
                    val backgroundPath = cachedPaths.getOrNull(1) ?: cachedPaths.first()
                    gameDao.updateBackgroundPath(gameId, backgroundPath)
                    Log.d(TAG, "Set screenshot ${if (cachedPaths.size > 1) "2" else "1"} as background for gameId $gameId")
                }
            }
        }
    }

    suspend fun cacheSingleScreenshot(gameId: Long, url: String, index: Int): String? {
        val slug = resolveGamePlatformSlug(gameId)
        val fileName = "ss_g${gameId}_${index}_${url.md5Hash()}.jpg"
        val cachedFile = File(platformDir(slug, "screenshots"), fileName)

        if (cachedFile.exists() && isValidImageFile(cachedFile)) {
            return cachedFile.absolutePath
        }

        val bitmap = downloadAndResize(url, 480) ?: return null
        FileOutputStream(cachedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
        }
        bitmap.recycle()

        return if (isValidImageFile(cachedFile)) cachedFile.absolutePath else null
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
                yield()

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
        val slug = resolveRommPlatformSlug(request.rommId)

        request.screenshotUrls.forEachIndexed { index, url ->
            val fileName = "ss_${request.rommId}_${index}_${url.md5Hash()}.jpg"
            val cachedFile = File(platformDir(slug, "screenshots"), fileName)

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
                yield()

                if (logoQueue.isEmpty) break
            }
            isProcessingLogos = false
        }
    }

    private suspend fun processLogoRequest(request: PlatformLogoCacheRequest) {
        val fileName = "logo_${request.platformId}_${request.logoUrl.md5Hash()}.png"
        val cachedFile = File(logosDir(), fileName)

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
            coverQueue.send(ImageCacheRequest(url, gameId, ImageType.COVER, gameId = gameId))
            startCoverProcessingIfNeeded()
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
                yield()

                if (coverQueue.isEmpty) break
            }
            isProcessingCovers = false
        }
    }

    private suspend fun processCoverRequest(request: ImageCacheRequest) {
        val isGameIdRequest = request.gameId != null
        val game = if (isGameIdRequest) {
            gameDao.getById(request.gameId!!)
        } else {
            gameDao.getByRommId(request.id)
        }
        val currentDbPath = game?.coverPath
        if (currentDbPath != null && currentDbPath.startsWith("/") && File(currentDbPath).exists()) {
            return
        }
        val prefix = if (isGameIdRequest) "cover_g${request.gameId}" else "cover_${request.id}"
        val fileName = "${prefix}_${request.url.md5Hash()}.jpg"
        val slug = if (isGameIdRequest) resolveGamePlatformSlug(request.gameId!!)
                   else resolveRommPlatformSlug(request.id)
        val cachedFile = File(platformDir(slug, "covers"), fileName)

        if (cachedFile.exists()) {
            if (isValidImageFile(cachedFile)) {
                if (isGameIdRequest) {
                    gameDao.updateCoverPath(request.gameId!!, cachedFile.absolutePath)
                    _localCoverWritten.tryEmit(request.gameId to cachedFile.absolutePath)
                } else {
                    updateGameCover(request.id, cachedFile.absolutePath)
                }
                return
            } else {
                cachedFile.delete()
                Log.w(TAG, "Deleted invalid cached cover: ${cachedFile.name}")
            }
        }

        val bitmap = downloadAndResize(request.url, 400)
            ?: game?.steamAppId?.let { downloadSteamCoverFallback(it) }
            ?: return

        FileOutputStream(cachedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        bitmap.recycle()

        if (!isValidImageFile(cachedFile)) {
            cachedFile.delete()
            Log.w(TAG, "Deleted newly cached invalid cover: ${cachedFile.name}")
            return
        }

        val idLabel = if (isGameIdRequest) "gameId ${request.gameId}" else "rommId ${request.id}"
        Log.d(TAG, "Cached cover for $idLabel: ${cachedFile.length() / 1024}KB")
        if (isGameIdRequest) {
            gameDao.updateCoverPath(request.gameId!!, cachedFile.absolutePath)
            _localCoverWritten.tryEmit(request.gameId to cachedFile.absolutePath)
        } else {
            updateGameCover(request.id, cachedFile.absolutePath)
        }
    }

    private fun downloadSteamCoverFallback(steamAppId: Long): Bitmap? {
        val fallbackUrls = listOf(
            "https://steamcdn-a.akamaihd.net/steam/apps/$steamAppId/header.jpg",
            "https://steamcdn-a.akamaihd.net/steam/apps/$steamAppId/capsule_616x353.jpg"
        )
        for (url in fallbackUrls) {
            downloadAndResize(url, 400)?.let { return it }
        }
        return null
    }

    private suspend fun updateGameCover(rommId: Long, localPath: String) {
        val game = gameDao.getByRommId(rommId) ?: return
        if (game.coverPath?.startsWith("/") == true && File(game.coverPath).exists()) return
        gameDao.updateCoverPath(game.id, localPath)
        _localCoverWritten.tryEmit(game.id to localPath)
    }

    fun resumePendingCoverCache() {
        scope.launch {
            val uncached = gameDao.getGamesWithUncachedCovers()
            if (uncached.isEmpty()) return@launch

            Log.d(TAG, "Resuming cache for ${uncached.size} games with uncached covers")
            uncached.forEach { game ->
                val url = game.coverPath ?: return@forEach
                val rommId = game.rommId
                if (rommId != null) {
                    queueCoverCache(url, rommId, game.title)
                } else {
                    queueCoverCacheByGameId(url, game.id)
                }
            }
        }
    }

    /**
     * Re-derive a source for games left with no cover (e.g. a cover file truncated by an
     * ungraceful power-off and then nulled by validation). Steam covers rebuild from the app id,
     * Android from the launcher icon; RomM games self-heal on the next library sync.
     */
    fun recoverMissingCovers() {
        scope.launch {
            val games = gameDao.getGamesWithMissingCovers()
            if (games.isEmpty()) return@launch

            var recovered = 0
            games.forEach { game ->
                when {
                    game.steamAppId != null -> {
                        val url = "https://steamcdn-a.akamaihd.net/steam/apps/${game.steamAppId}/library_600x900.jpg"
                        gameDao.updateCoverPath(game.id, url)
                        queueCoverCacheByGameId(url, game.id)
                        recovered++
                    }
                    game.source == GameSource.ANDROID_APP && game.packageName != null -> {
                        queueAppIconCache(game.id, game.packageName)
                        recovered++
                    }
                }
            }
            if (recovered > 0) {
                Log.i(TAG, "Cover recovery: re-derived source for $recovered games with missing covers")
            }
        }
    }

    private val badgeQueue = Channel<AchievementBadgeCacheRequest>(256)
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
                yield()

                if (badgeQueue.isEmpty) break
            }
            isProcessingBadges = false
        }
    }

    private suspend fun processBadgeRequest(request: AchievementBadgeCacheRequest) {
        val slug = resolveBadgePlatformSlug(request.achievementId)
        val badgeDir = platformDir(slug, "badges")
        val unlockedFileName = "badge_${request.achievementId}_${request.badgeUrl.md5Hash()}.png"
        val unlockedFile = File(badgeDir, unlockedFileName)

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
            val lockedFile = File(badgeDir, lockedFileName)

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

                val slug = resolveGamePlatformSlug(gameId)
                val fileName = "bg_custom_${gameId}_${System.currentTimeMillis()}.jpg"
                val cachedFile = File(platformDir(slug, "backgrounds"), fileName)

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

    private val appIconQueue = Channel<AppIconCacheRequest>(256)
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
        val slug = resolveGamePlatformSlug(request.gameId)
        val fileName = "appicon_${request.packageName.hashCode()}.png"
        val cachedFile = File(platformDir(slug, "icons"), fileName)

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
            val iconDir = platformDir("android", "icons")
            val fileName = "appicon_${packageName.hashCode()}.png"
            val cachedFile = File(iconDir, fileName)

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

    private fun shouldClearMissingPath(path: String): Boolean {
        if (!path.startsWith("/")) return false
        val file = File(path)
        if (file.exists()) return false
        val parent = file.parentFile ?: return false
        return parent.exists() && parent.isDirectory
    }

    suspend fun validateAndCleanCache(
        onProgress: (suspend (phase: String, current: Int, total: Int) -> Unit)? = null
    ): CacheValidationResult {
        var deleted = 0
        var cleared = 0

        val files = withContext(Dispatchers.IO) {
            cacheDir.walk().filter { it.isFile && it.name != ".nomedia" }.toList()
        }
        val totalFiles = files.size
        onProgress?.invoke("Checking $totalFiles cached files...", 0, totalFiles)

        withContext(Dispatchers.IO) {
            files.forEachIndexed { index, file ->
                if (!isValidImageFile(file, minSizeBytes = 512)) {
                    file.delete()
                    deleted++
                    Log.w(TAG, "Validation deleted invalid file: ${file.name}")
                }
                if (index % 50 == 0) {
                    onProgress?.invoke("Checking cached files...", index, totalFiles)
                }
            }
        }

        val infos = gameDao.getAllImageCacheInfo()
        val totalGames = infos.size
        onProgress?.invoke("Validating $totalGames game paths...", 0, totalGames)

        infos.forEachIndexed { index, info ->
            if (info.coverPath != null && shouldClearMissingPath(info.coverPath)) {
                gameDao.clearCoverPath(info.id)
                cleared++
            }
            if (info.backgroundPath != null && shouldClearMissingPath(info.backgroundPath)) {
                gameDao.clearBackgroundPath(info.id)
                cleared++
            }
            if (info.cachedScreenshotPaths != null) {
                val paths = info.cachedScreenshotPaths.split(",")
                val validPaths = paths.filter { path -> !shouldClearMissingPath(path) }
                if (validPaths.size != paths.size) {
                    if (validPaths.isEmpty()) {
                        gameDao.clearCachedScreenshotPaths(info.id)
                    } else {
                        gameDao.updateCachedScreenshotPaths(info.id, validPaths.joinToString(","))
                    }
                    cleared += paths.size - validPaths.size
                }
            }
            if (index % 100 == 0) {
                onProgress?.invoke("Validating game paths...", index, totalGames)
            }
        }

        val platforms = platformDao.getAllPlatforms()
        onProgress?.invoke("Checking ${platforms.size} platform logos...", 0, platforms.size)

        platforms.forEach { platform ->
            if (platform.logoPath != null && shouldClearMissingPath(platform.logoPath)) {
                platformDao.clearLogoPath(platform.id)
                cleared++
            }
        }

        migrateLegacyIgdbCovers()

        val orphanDirs = listOf("image_cache", "steam")
        withContext(Dispatchers.IO) {
            for (name in orphanDirs) {
                val dir = File(context.cacheDir, name)
                if (dir.exists() && dir.isDirectory) {
                    val count = dir.walk().count { it.isFile }
                    dir.deleteRecursively()
                    deleted += count
                    Log.i(TAG, "Cleaned up legacy cache directory: $name ($count files)")
                }
            }
        }

        withContext(Dispatchers.Main) {
            context.imageLoader.memoryCache?.clear()
        }

        Log.i(TAG, "Cache validation complete: $deleted files deleted, $cleared paths cleared")
        return CacheValidationResult(deleted, cleared)
    }

    private suspend fun migrateLegacyIgdbCovers() {
        val legacyDir = File(context.cacheDir, "steam")
        if (!legacyDir.exists() || !legacyDir.isDirectory) return

        withContext(Dispatchers.IO) {
            legacyDir.listFiles()?.forEach { file ->
                val name = file.name
                if (!name.startsWith("cover_") || !name.endsWith(".jpg")) return@forEach
                val steamAppId = name.removePrefix("cover_").removeSuffix(".jpg").toLongOrNull()
                    ?: return@forEach
                val game = gameDao.getBySteamAppId(steamAppId) ?: return@forEach
                if (game.coverPath != file.absolutePath) return@forEach

                val hash = "legacy-igdb-$steamAppId".md5Hash()
                val newFile = File(platformDir(game.platformSlug, "covers"), "cover_g${game.id}_$hash.jpg")
                if (file.renameTo(newFile)) {
                    gameDao.updateCoverPath(game.id, newFile.absolutePath)
                    Log.i(TAG, "Migrated legacy IGDB cover for gameId=${game.id}")
                }
            }
        }
    }

    fun needsFlatToShardedMigration(): Boolean {
        val files = cacheDir.listFiles() ?: return false
        return files.any { it.isFile && it.name != ".nomedia" && !it.name.endsWith(".tmp") }
    }

    suspend fun migrateFlatToSharded(
        onProgress: (suspend (current: Int, total: Int) -> Unit)? = null
    ) {
        withContext(cacheDispatcher) {
            val rootFiles = cacheDir.listFiles()
                ?.filter { it.isFile && it.name != ".nomedia" && !it.name.endsWith(".tmp") }
                ?: return@withContext

            val total = rootFiles.size
            Log.i(TAG, "Starting flat-to-sharded migration for $total files")

            var migrated = 0
            var failed = 0

            rootFiles.forEachIndexed { index, file ->
                try {
                    val destination = resolveShardedDestination(file.name)
                    if (destination != null) {
                        destination.parentFile?.mkdirs()
                        file.renameTo(destination)
                        migrated++
                    } else {
                        val miscDir = File(File(cacheDir, FALLBACK_PLATFORM), "other")
                        miscDir.mkdirs()
                        file.renameTo(File(miscDir, file.name))
                        migrated++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate ${file.name}: ${e.message}")
                    failed++
                }

                if (index % 100 == 0) {
                    onProgress?.invoke(index, total)
                }
            }

            Log.i(TAG, "Flat-to-sharded migration complete: $migrated migrated, $failed failed")
            onProgress?.invoke(total, total)

            updateDatabasePathsAfterSharding()
        }
    }

    private suspend fun resolveShardedDestination(fileName: String): File? {
        return when {
            fileName.startsWith("steam_bg_") -> {
                val steamAppId = fileName.removePrefix("steam_bg_").substringBefore("_").toLongOrNull()
                    ?: return null
                val slug = resolveSteamPlatformSlug(steamAppId)
                File(platformDir(slug, "backgrounds"), fileName)
            }
            fileName.startsWith("bg_custom_") -> {
                val gameId = fileName.removePrefix("bg_custom_").substringBefore("_").toLongOrNull()
                    ?: return null
                val slug = resolveGamePlatformSlug(gameId)
                File(platformDir(slug, "backgrounds"), fileName)
            }
            fileName.startsWith("bg_") -> {
                val rommId = fileName.removePrefix("bg_").substringBefore("_").toLongOrNull()
                    ?: return null
                val slug = resolveRommPlatformSlug(rommId)
                File(platformDir(slug, "backgrounds"), fileName)
            }
            fileName.startsWith("cover_g") -> {
                val gameId = fileName.removePrefix("cover_g").substringBefore("_").toLongOrNull()
                    ?: return null
                val slug = resolveGamePlatformSlug(gameId)
                File(platformDir(slug, "covers"), fileName)
            }
            fileName.startsWith("cover_") -> {
                val rommId = fileName.removePrefix("cover_").substringBefore("_").toLongOrNull()
                    ?: return null
                val slug = resolveRommPlatformSlug(rommId)
                File(platformDir(slug, "covers"), fileName)
            }
            fileName.startsWith("ss_g") -> {
                val gameId = fileName.removePrefix("ss_g").substringBefore("_").toLongOrNull()
                    ?: return null
                val slug = resolveGamePlatformSlug(gameId)
                File(platformDir(slug, "screenshots"), fileName)
            }
            fileName.startsWith("ss_") -> {
                val rommId = fileName.removePrefix("ss_").substringBefore("_").toLongOrNull()
                    ?: return null
                val slug = resolveRommPlatformSlug(rommId)
                File(platformDir(slug, "screenshots"), fileName)
            }
            fileName.startsWith("badge_lock_") -> {
                val achievementId = fileName.removePrefix("badge_lock_").substringBefore("_").toLongOrNull()
                    ?: return null
                val slug = resolveBadgePlatformSlug(achievementId)
                File(platformDir(slug, "badges"), fileName)
            }
            fileName.startsWith("badge_") -> {
                val achievementId = fileName.removePrefix("badge_").substringBefore("_").toLongOrNull()
                    ?: return null
                val slug = resolveBadgePlatformSlug(achievementId)
                File(platformDir(slug, "badges"), fileName)
            }
            fileName.startsWith("logo_") -> {
                File(logosDir(), fileName)
            }
            fileName.startsWith("appicon_") -> {
                File(platformDir("android", "icons"), fileName)
            }
            else -> null
        }
    }

    private suspend fun updateDatabasePathsAfterSharding() {
        val cachePath = cacheDir.absolutePath
        var updated = 0

        gameDao.getAllImageCacheInfo().forEach { info ->
            var changed = false
            var newCoverPath = info.coverPath
            var newBackgroundPath = info.backgroundPath
            var newCachedScreenshotPaths = info.cachedScreenshotPaths

            if (info.coverPath?.startsWith(cachePath) == true && !File(info.coverPath).exists()) {
                val fileName = File(info.coverPath).name
                val dest = resolveShardedDestination(fileName)
                if (dest != null && dest.exists()) {
                    newCoverPath = dest.absolutePath
                    changed = true
                }
            }
            if (info.backgroundPath?.startsWith(cachePath) == true && !File(info.backgroundPath).exists()) {
                val fileName = File(info.backgroundPath).name
                val dest = resolveShardedDestination(fileName)
                if (dest != null && dest.exists()) {
                    newBackgroundPath = dest.absolutePath
                    changed = true
                }
            }
            if (info.cachedScreenshotPaths?.contains(cachePath) == true) {
                val paths = info.cachedScreenshotPaths.split(",")
                val newPaths = paths.map { path ->
                    if (path.startsWith(cachePath) && !File(path).exists()) {
                        val fileName = File(path).name
                        val dest = resolveShardedDestination(fileName)
                        if (dest != null && dest.exists()) dest.absolutePath else path
                    } else path
                }
                if (newPaths != paths) {
                    newCachedScreenshotPaths = newPaths.joinToString(",")
                    changed = true
                }
            }

            if (changed) {
                gameDao.updateImagePaths(info.id, newCoverPath, newBackgroundPath, newCachedScreenshotPaths)
                updated++
            }
        }

        platformDao.getAllPlatforms().forEach { platform ->
            if (platform.logoPath?.startsWith(cachePath) == true && !File(platform.logoPath).exists()) {
                val fileName = File(platform.logoPath).name
                val dest = resolveShardedDestination(fileName)
                if (dest != null && dest.exists()) {
                    platformDao.updateLogoPath(platform.id, dest.absolutePath)
                    updated++
                }
            }
        }

        achievementDao.getWithUncachedBadges()
        val allGameIds = gameDao.getAllGameIds()
        allGameIds.forEach { gameId ->
            val achievements = achievementDao.getByGameId(gameId)
            achievements.forEach { achievement ->
                var badgeChanged = false
                var newBadgePath = achievement.cachedBadgeUrl
                var newBadgeLockPath = achievement.cachedBadgeUrlLock

                if (achievement.cachedBadgeUrl?.startsWith(cachePath) == true && !File(achievement.cachedBadgeUrl).exists()) {
                    val fileName = File(achievement.cachedBadgeUrl).name
                    val dest = resolveShardedDestination(fileName)
                    if (dest != null && dest.exists()) {
                        newBadgePath = dest.absolutePath
                        badgeChanged = true
                    }
                }
                if (achievement.cachedBadgeUrlLock?.startsWith(cachePath) == true && !File(achievement.cachedBadgeUrlLock).exists()) {
                    val fileName = File(achievement.cachedBadgeUrlLock).name
                    val dest = resolveShardedDestination(fileName)
                    if (dest != null && dest.exists()) {
                        newBadgeLockPath = dest.absolutePath
                        badgeChanged = true
                    }
                }

                if (badgeChanged) {
                    if (newBadgePath != null) achievementDao.updateCachedBadgeUrl(achievement.id, newBadgePath)
                    if (newBadgeLockPath != null) achievementDao.updateCachedBadgeUrlLock(achievement.id, newBadgeLockPath)
                    updated++
                }
            }
        }

        Log.i(TAG, "Updated $updated database paths after sharding migration")
    }
}
