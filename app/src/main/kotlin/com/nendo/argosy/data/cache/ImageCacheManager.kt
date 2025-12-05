package com.nendo.argosy.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ImageCacheManager"

data class ImageCacheRequest(
    val url: String,
    val rommId: Long,
    val type: ImageType,
    val gameTitle: String = ""
)

enum class ImageType { BACKGROUND, SCREENSHOT }

data class ImageCacheProgress(
    val isProcessing: Boolean = false,
    val currentGameTitle: String = "",
    val currentType: String = "",
    val cachedCount: Int = 0,
    val totalCount: Int = 0
) {
    val progressPercent: Int
        get() = if (totalCount > 0) (cachedCount * 100 / totalCount) else 0
}

data class ScreenshotCacheRequest(
    val gameId: Long,
    val rommId: Long,
    val screenshotUrls: List<String>,
    val gameTitle: String = ""
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class ImageCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao
) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, "images").also { it.mkdirs() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<ImageCacheRequest>(Channel.UNLIMITED)
    private val screenshotQueue = Channel<ScreenshotCacheRequest>(Channel.UNLIMITED)
    private var isProcessing = false
    private var isProcessingScreenshots = false

    private val _progress = kotlinx.coroutines.flow.MutableStateFlow(ImageCacheProgress())
    val progress: kotlinx.coroutines.flow.StateFlow<ImageCacheProgress> = _progress

    private val _screenshotProgress = kotlinx.coroutines.flow.MutableStateFlow(ImageCacheProgress())
    val screenshotProgress: kotlinx.coroutines.flow.StateFlow<ImageCacheProgress> = _screenshotProgress

    fun queueBackgroundCache(url: String, rommId: Long, gameTitle: String = "") {
        scope.launch {
            queue.send(ImageCacheRequest(url, rommId, ImageType.BACKGROUND, gameTitle))
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
                try {
                    _progress.value = _progress.value.copy(
                        currentGameTitle = request.gameTitle,
                        currentType = if (request.type == ImageType.BACKGROUND) "background" else "cover"
                    )
                    processRequest(request)
                    updateProgressFromDb(isProcessing = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process ${request.rommId}: ${e.message}")
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
        val fileName = "bg_${request.rommId}_${request.url.md5Hash()}.jpg"
        val cachedFile = File(cacheDir, fileName)

        if (cachedFile.exists()) {
            updateGameBackground(request.rommId, cachedFile.absolutePath)
            return
        }

        val bitmap = downloadAndResize(request.url, 640) ?: return

        FileOutputStream(cachedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        bitmap.recycle()

        Log.d(TAG, "Cached background for rommId ${request.rommId}: ${cachedFile.length() / 1024}KB")
        updateGameBackground(request.rommId, cachedFile.absolutePath)
    }

    private suspend fun updateGameBackground(rommId: Long, localPath: String) {
        val game = gameDao.getByRommId(rommId) ?: return
        if (game.backgroundPath?.startsWith("/") == true) return
        gameDao.updateBackgroundPath(game.id, localPath)
    }

    private fun downloadAndResize(url: String, maxWidth: Int): Bitmap? {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000

        val inputStream = connection.getInputStream()
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        val tempFile = File.createTempFile("img_", ".tmp", cacheDir)
        try {
            tempFile.outputStream().use { out ->
                inputStream.copyTo(out)
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

            return bitmap
        } finally {
            tempFile.delete()
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

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun getPendingCount(): Int = queue.isEmpty.let { if (it) 0 else -1 }

    fun resumePendingCache() {
        scope.launch {
            val uncached = gameDao.getGamesWithUncachedBackgrounds()
            if (uncached.isEmpty()) return@launch

            Log.d(TAG, "Resuming cache for ${uncached.size} games with uncached backgrounds")
            uncached.forEach { game ->
                val url = game.backgroundPath ?: return@forEach
                val rommId = game.rommId ?: return@forEach
                queueBackgroundCache(url, rommId, game.title)
            }
        }
    }

    fun queueScreenshotCache(gameId: Long, rommId: Long, screenshotUrls: List<String>, gameTitle: String) {
        scope.launch {
            screenshotQueue.send(ScreenshotCacheRequest(gameId, rommId, screenshotUrls, gameTitle))
            startScreenshotProcessingIfNeeded()
        }
    }

    private fun startScreenshotProcessingIfNeeded() {
        if (isProcessingScreenshots) return
        isProcessingScreenshots = true

        scope.launch {
            Log.d(TAG, "Starting screenshot cache processing")
            updateScreenshotProgressFromDb(isProcessing = true)

            for (request in screenshotQueue) {
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
                cachedPaths.add(cachedFile.absolutePath)
                return@forEachIndexed
            }

            val bitmap = downloadAndResize(url, 480) ?: return@forEachIndexed

            FileOutputStream(cachedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            bitmap.recycle()

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
}
