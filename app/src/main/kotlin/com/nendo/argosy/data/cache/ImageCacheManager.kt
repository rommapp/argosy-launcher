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
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.StorageVolumeHealth
import com.nendo.argosy.data.storage.VolumeProbe
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameScreenshotDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.clearCached
import com.nendo.argosy.data.local.dao.clearCachedPathsChunked
import com.nendo.argosy.data.local.dao.clearOverride
import com.nendo.argosy.data.local.dao.resolved
import com.nendo.argosy.data.local.entity.toResolvedArtByGame
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.ResolvedGameArt
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
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
    val urls: List<String>,
    val id: Long,
    val type: ImageType,
    val gameTitle: String = "",
    val isSteam: Boolean = false,
    val gameId: Long? = null,
    val revalidateFrom: String? = null
) {
    constructor(
        url: String,
        id: Long,
        type: ImageType,
        gameTitle: String = "",
        isSteam: Boolean = false,
        gameId: Long? = null
    ) : this(listOf(url), id, type, gameTitle, isSteam, gameId)
}

enum class ImageType { BACKGROUND, SCREENSHOT, COVER, BOX_BACK, BOX_SPINE, LOGO, BOX_3D }

internal val ImageType.isBoxFace: Boolean
    get() = this == ImageType.BOX_BACK || this == ImageType.BOX_SPINE ||
        this == ImageType.LOGO || this == ImageType.BOX_3D

data class CachedGameImages(
    val coverPath: String?,
    val backgroundPath: String?,
    val logoPath: String?
)

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

internal fun artUrlHash(url: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(12)
}

internal fun isCachedFileFrom(cachedPath: String, sourceUrl: String): Boolean =
    File(cachedPath).nameWithoutExtension.endsWith("_${artUrlHash(sourceUrl)}")

internal fun canBackfillCachedFromUrl(cachedPath: String?, cachedFromUrl: String?, sourceUrl: String): Boolean =
    cachedPath != null && cachedFromUrl == null && isCachedFileFrom(cachedPath, sourceUrl)

internal enum class CachedArtDecision { KEEP_AND_RENAME, REPLACE, SKIP, DROP }

internal fun cachedArtDecision(
    cachedModifiedAt: Long,
    serverModifiedAt: Long?,
    everyCandidateGone: Boolean = false
): CachedArtDecision = when {
    everyCandidateGone -> CachedArtDecision.DROP
    serverModifiedAt == null -> CachedArtDecision.SKIP
    serverModifiedAt <= 0L -> CachedArtDecision.REPLACE
    serverModifiedAt > cachedModifiedAt -> CachedArtDecision.REPLACE
    else -> CachedArtDecision.KEEP_AND_RENAME
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class ImageCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val gameArtDao: GameArtDao,
    private val gameScreenshotDao: GameScreenshotDao,
    private val platformDao: PlatformDao,
    private val achievementDao: AchievementDao,
    private val volumeHealth: StorageVolumeHealth,
    private val fileAccessLayer: FileAccessLayer
) {
    private val artModelScope = SafeCoroutineScope(Dispatchers.IO, "GameArtModel")

    val gameArt: StateFlow<Map<Long, ResolvedGameArt>> = gameArtDao.observeRowCount()
        .conflate()
        .transform {
            emit(gameArtDao.getAll().toResolvedArtByGame())
            delay(ART_MODEL_RELOAD_INTERVAL_MS)
        }
        .stateIn(artModelScope, SharingStarted.Lazily, emptyMap())

    fun artFor(gameId: Long): ResolvedGameArt? = gameArt.value[gameId]

    suspend fun loadArt(gameId: Long): ResolvedGameArt = gameArtDao.resolved(gameId)

    fun observeArt(gameId: Long): Flow<ResolvedGameArt?> =
        gameArt.map { it[gameId] }.distinctUntilChanged()

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
        private const val MAX_IN_MEMORY_IMAGE_BYTES = 8 * 1024 * 1024
        private const val DEFAULT_IMAGE_BUFFER_BYTES = 64 * 1024
        private const val CACHE_SUBFOLDER = "argosy_images"
        private val DOCUMENT_CONTENT_TYPES = listOf("text/", "html", "json")
        private const val FALLBACK_PLATFORM = "_misc"
        private const val LOGOS_DIR = "_logos"
        private const val BOX_FACE_MAX_WIDTH = 400
        private val BOX_FACE_SLOT_NAMES = setOf(ArtSlot.BOX_SPINE.name, ArtSlot.BOX_BACK.name)
        private const val LOGO_MAX_WIDTH = 1000
        private const val BOX_3D_MAX_WIDTH = 600
        private const val COVER_MAX_WIDTH = 400
        private const val BACKGROUND_MAX_WIDTH = 1280
        private const val BACKGROUND_JPEG_QUALITY = 87
        private val LEGACY_OVERRIDE_FILE_PREFIXES = listOf("cover_manual_", "bg_custom_")
        private const val VALIDATION_MARKER = ".validated"
        private const val SWEEP_RECENT_FILE_GRACE_MS = 10 * 60 * 1000L
        private const val ART_PATH_CHUNK = 900
        private const val ART_MODEL_RELOAD_INTERVAL_MS = 1_000L
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
    private val missingArt = MissingArtRegistry(File(context.filesDir, "missing_art.tsv"))

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

    fun queueBackgroundCache(url: String, rommId: Long, gameTitle: String = "") =
        queueBackgroundCache(listOf(url), rommId, gameTitle)

    fun queueBackgroundCache(urls: List<String>, rommId: Long, gameTitle: String = "") {
        if (urls.isEmpty()) return
        scope.launch {
            queue.send(ImageCacheRequest(urls, rommId, ImageType.BACKGROUND, gameTitle, isSteam = false))
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
        val total = gameArtDao.countWithSource(ArtSlot.BACKGROUND.name)
        val cached = gameArtDao.countCached(ArtSlot.BACKGROUND.name)
        _progress.value = _progress.value.copy(
            isProcessing = isProcessing,
            cachedCount = cached,
            totalCount = total
        )
    }

    private suspend fun processRequest(request: ImageCacheRequest, replacing: Boolean = false) {
        val isGameIdRequest = request.gameId != null
        val prefix = when {
            isGameIdRequest -> "bg_g${request.gameId}"
            request.isSteam -> "steam_bg_${request.id}"
            else -> "bg_${request.id}"
        }
        val slug = when {
            isGameIdRequest -> resolveGamePlatformSlug(request.gameId!!)
            request.isSteam -> resolveSteamPlatformSlug(request.id)
            else -> resolveRommPlatformSlug(request.id)
        }
        val idLabel = when {
            isGameIdRequest -> "gameId ${request.gameId}"
            request.isSteam -> "steamAppId ${request.id}"
            else -> "rommId ${request.id}"
        }
        val backgroundDir = platformDir(slug, "backgrounds")
        request.revalidateFrom?.let { cachedPath ->
            revalidateCachedArt(
                request, File(cachedPath), backgroundDir, prefix,
                store = { path -> storeCachedArt(request, ArtSlot.BACKGROUND, path) },
                replace = { processRequest(request.copy(revalidateFrom = null), replacing = true) },
                drop = { dropCachedArt(request, ArtSlot.BACKGROUND) }
            )
            return
        }
        if (!replacing && isCachedFromSource(request, ArtSlot.BACKGROUND)) return
        val commitBackground: suspend (String) -> Unit = { path ->
            storeCachedArt(request, ArtSlot.BACKGROUND, path)
        }

        for ((index, url) in request.urls.withIndex()) {
            val cachedFile = File(backgroundDir, "${prefix}_${url.md5Hash()}.jpg")

            if (cachedFile.exists()) {
                if (isValidImageFile(cachedFile)) {
                    commitBackground(cachedFile.absolutePath)
                    pruneReplacedArt(backgroundDir, prefix, cachedFile, storedArtPath(request, ArtSlot.BACKGROUND))
                    return
                }
                cachedFile.delete()
                Log.w(TAG, "Deleted invalid cached background: ${cachedFile.name}")
            }

            if (missingArt.isKnownMissing(url)) continue
            val bitmap = downloadAndResize(url, 1280)
            if (bitmap == null) {
                logCandidateRejected("background", idLabel, index, request.urls.size, url, "no decodable image")
                continue
            }

            FileOutputStream(cachedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 87, out)
            }
            bitmap.recycle()

            if (!isValidImageFile(cachedFile)) {
                cachedFile.delete()
                logCandidateRejected("background", idLabel, index, request.urls.size, url, "cached file did not decode")
                continue
            }

            Log.d(TAG, "Cached background for $idLabel: ${cachedFile.length() / 1024}KB")
            commitBackground(cachedFile.absolutePath)
            pruneReplacedArt(backgroundDir, prefix, cachedFile, storedArtPath(request, ArtSlot.BACKGROUND))
            return
        }

        logAllCandidatesRejected("background", idLabel, request)
    }

    private fun logCandidateRejected(
        kind: String,
        idLabel: String,
        index: Int,
        total: Int,
        url: String,
        reason: String
    ) {
        Logger.warn(TAG, "$kind candidate ${index + 1}/$total rejected for $idLabel ($reason): $url")
    }

    private fun logAllCandidatesRejected(kind: String, idLabel: String, request: ImageCacheRequest) {
        if (request.urls.isEmpty()) {
            Logger.warn(TAG, "No $kind url offered for $idLabel (${request.gameTitle})")
        } else {
            Logger.warn(
                TAG,
                "No $kind candidate loaded for $idLabel (${request.gameTitle}), " +
                    "${request.urls.size} tried: ${request.urls.joinToString(", ")}"
            )
        }
    }

    /**
     * Downsampling needs the encoded bytes twice, once for the bounds and once for the
     * real decode, and a network stream cannot be rewound. Artwork is small enough to hold
     * while that happens; anything past [MAX_IN_MEMORY_IMAGE_BYTES] spills to a temp file
     * so an unexpectedly huge response cannot be turned into a heap spike.
     */
    private fun downloadAndResize(url: String, maxWidth: Int): Bitmap? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000

            val status = (connection as? java.net.HttpURLConnection)?.responseCode
            if (status == java.net.HttpURLConnection.HTTP_NOT_FOUND || status == java.net.HttpURLConnection.HTTP_GONE) {
                missingArt.markMissing(url)
                Logger.warn(TAG, "Image not on the server ($status), skipping it for a while: $url")
                return null
            }
            val contentType = connection.contentType?.lowercase()
            if (contentType != null && DOCUMENT_CONTENT_TYPES.any { contentType.contains(it) }) {
                missingArt.markMissing(url)
                Logger.warn(TAG, "Served a document, not an image ($contentType), skipping it for a while: $url")
                return null
            }

            connection.getInputStream().use { inputStream ->
                val buffered = java.io.ByteArrayOutputStream(DEFAULT_IMAGE_BUFFER_BYTES)
                val chunk = ByteArray(DEFAULT_IMAGE_BUFFER_BYTES)
                while (buffered.size() <= MAX_IN_MEMORY_IMAGE_BYTES) {
                    val read = inputStream.read(chunk)
                    if (read == -1) break
                    buffered.write(chunk, 0, read)
                }

                if (buffered.size() <= MAX_IN_MEMORY_IMAGE_BYTES) {
                    val bytes = buffered.toByteArray()
                    decodeSampled(maxWidth) { options ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    } ?: run {
                        Logger.warn(TAG, "Response did not decode as an image: $url")
                        null
                    }
                } else {
                    val head = buffered.toByteArray()
                    val headSize = head.size
                    val tempFile = File.createTempFile("img_", ".tmp", context.cacheDir)
                    try {
                        tempFile.outputStream().use { out ->
                            out.write(head, 0, headSize)
                            inputStream.copyTo(out)
                        }
                        decodeSampled(maxWidth) { options ->
                            BitmapFactory.decodeFile(tempFile.absolutePath, options)
                        }
                    } finally {
                        tempFile.delete()
                    }
                }
            }
        } catch (e: java.io.FileNotFoundException) {
            Logger.warn(TAG, "Image not found: $url")
            null
        } catch (e: Throwable) {
            Logger.warn(TAG, "Failed to download image from $url: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun decodeSampled(maxWidth: Int, decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(options)

        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxWidth)
        options.inJustDecodeBounds = false

        val bitmap = decode(options) ?: return null
        if (bitmap.width <= maxWidth) return bitmap

        val ratio = maxWidth.toFloat() / bitmap.width
        val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * ratio).toInt(), true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    @Suppress("UNUSED_PARAMETER")
    private fun calculateSampleSize(width: Int, height: Int, maxWidth: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxWidth * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun String.md5Hash(): String = artUrlHash(this)

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

    /**
     * Deletes one platform's cached server artwork and clears its cached paths; source urls stay
     * for the pending pass to fetch again. Artwork overrides and their files are kept. Returns the
     * bytes reclaimed.
     */
    suspend fun clearPlatformCache(platformSlug: String): Long = withContext(Dispatchers.IO) {
        val keep = gameArtDao.getOverridePathsForPlatform(platformSlug).toSet()
        val root = File(cacheDir, platformSlug)
        if (!root.exists()) return@withContext 0L
        var reclaimed = 0L
        root.walkBottomUp().forEach { entry ->
            when {
                entry.isDirectory -> if (entry != root && entry.listFiles().isNullOrEmpty()) entry.delete()
                entry.name == ".nomedia" -> Unit
                entry.absolutePath in keep -> Unit
                else -> {
                    val size = entry.length()
                    if (entry.delete()) reclaimed += size
                }
            }
        }
        gameArtDao.clearCachedForPlatform(platformSlug)
        gameScreenshotDao.clearCachedForPlatform(platformSlug)
        clearDecodedImageCache()
        reclaimed
    }

    /**
     * Deletes every cached image file except the artwork overrides games still reference.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val keep = gameArtDao.getAllOverridePaths().toSet()
        cacheDir.walkBottomUp().forEach { entry ->
            when {
                entry == cacheDir -> Unit
                entry.isDirectory -> if (entry.listFiles().isNullOrEmpty()) entry.delete()
                entry.name == ".nomedia" -> Unit
                entry.absolutePath in keep -> Unit
                else -> entry.delete()
            }
        }
    }

    /**
     * Clearing Coil's memory cache evicts every decoded bitmap in the app, so a sync that
     * touches thousands of games must clear once at the end rather than per game.
     */
    suspend fun clearDecodedImageCache() {
        withContext(Dispatchers.Main) {
            context.imageLoader.memoryCache?.clear()
        }
    }

    suspend fun deleteGameImages(rommId: Long, clearDecoded: Boolean = true) {
        withContext(Dispatchers.IO) {
            val slug = resolveRommPlatformSlug(rommId)
            val prefixes = listOf(
                "cover_${rommId}_", "bg_${rommId}_", "ss_${rommId}_",
                "box_back_${rommId}_", "box_spine_${rommId}_", "game_logo_${rommId}_", "box_3d_${rommId}_"
            )
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
        if (clearDecoded) clearDecodedImageCache()
    }

    suspend fun forgetCachedArt(gameId: Long) {
        ArtSlot.entries.forEach { forgetCachedArt(gameId, it) }
        gameScreenshotDao.clearCachedForGame(gameId)
    }

    suspend fun forgetCachedArt(gameId: Long, slot: ArtSlot) {
        gameArtDao.clearCached(gameId, slot)
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
                if (!sourceFile.renameTo(destFile)) {
                    sourceFile.copyTo(destFile, overwrite = true)
                }
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
        var updated = 0

        updated += relocateArtPaths { path ->
            if (path.startsWith(oldBasePath)) path.replace(oldBasePath, newBasePath) else path
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

    private suspend fun relocateArtPaths(relocate: suspend (String) -> String): Int {
        var updated = 0
        gameArtDao.getLocalPaths().forEach { row ->
            row.cachedPath?.let { old ->
                val moved = relocate(old)
                if (moved != old) {
                    gameArtDao.relocateCachedPath(row.gameId, row.slot, old, moved)
                    updated++
                }
            }
            row.overridePath?.let { old ->
                val moved = relocate(old)
                if (moved != old) {
                    gameArtDao.relocateOverridePath(row.gameId, row.slot, old, moved)
                    updated++
                }
            }
        }
        gameScreenshotDao.getCached().forEach { row ->
            val old = row.cachedPath ?: return@forEach
            val moved = relocate(old)
            if (moved != old) {
                gameScreenshotDao.relocateCachedPath(row.gameId, row.position, old, moved)
                updated++
            }
        }
        return updated
    }

    fun getPendingCount(): Int = queue.isEmpty.let { if (it) 0 else -1 }

    /**
     * Queues every art slot whose cached file does not come from its current source url. Rows
     * cached before `cachedFromUrl` existed are matched by file name first and only queued when
     * the name does not carry the source url's hash. Known-missing urls are skipped, and so are
     * box spine and back scans unless [includeBoxFaces].
     */
    fun resumePendingArt(includeBoxFaces: Boolean) {
        scope.launch {
            val pending = gameArtDao.getPending()
                .filterNot { missingArt.isKnownMissing(it.sourceUrl) }
                .filter { includeBoxFaces || it.slot !in BOX_FACE_SLOT_NAMES }
            val (backfill, stale) = pending.partition {
                canBackfillCachedFromUrl(it.cachedPath, it.cachedFromUrl, it.sourceUrl)
            }
            if (backfill.isNotEmpty()) gameArtDao.backfillCachedFromUrls(backfill)
            stale.forEach { art ->
                val slot = ArtSlot.entries.firstOrNull { it.name == art.slot } ?: return@forEach
                queueArt(art.gameId, slot, listOf(art.sourceUrl), art.rommId, art.steamAppId, art.title, art.cachedPath)
            }
            val iconless = gameArtDao.getAndroidGamesWithoutCover()
            iconless.forEach { queueAppIconCache(it.gameId, it.packageName) }
            if (pending.isNotEmpty() || iconless.isNotEmpty()) {
                Log.i(
                    TAG,
                    "Pending art: ${backfill.size} matched by name, ${stale.size} queued, " +
                        "${iconless.size} app icons queued"
                )
            }
        }
    }

    /**
     * Queues [slot] for [gameId] unless its cached file already comes from the first of [urls],
     * which is the source url library sync stored. A row with a cached file but no recorded
     * source is matched by file name before anything is downloaded. A cached file that is
     * genuinely gone from a readable volume is forgotten and downloaded again.
     */
    suspend fun queueArtIfStale(
        gameId: Long,
        slot: ArtSlot,
        urls: List<String>,
        rommId: Long?,
        steamAppId: Long?,
        title: String
    ) {
        val source = urls.firstOrNull() ?: return
        val row = gameArtDao.get(gameId, slot.name)
        val cachedFrom = row?.cachedFromUrl
        val cachedPath = row?.cachedPath?.takeUnless { volumeHealth.newProbe().isGenuinelyAbsent(it) }
        if (cachedPath == null && row?.cachedPath != null) gameArtDao.clearCached(gameId, slot)
        if (cachedPath != null && cachedFrom == source) return
        if (cachedPath != null && canBackfillCachedFromUrl(cachedPath, cachedFrom, source)) {
            gameArtDao.backfillCachedFromUrl(gameId, slot.name, cachedPath, source)
            return
        }
        queueArt(gameId, slot, urls, rommId, steamAppId, title, cachedPath)
    }

    /**
     * Downloads [slot] for [gameId] again when its cached file is gone, for a screen that just
     * failed to draw it. A file that is still on disk, or a slot with no recorded source, is left
     * alone.
     */
    fun repairMissingArt(gameId: Long, slot: ArtSlot) {
        scope.launch {
            val source = gameArtDao.get(gameId, slot.name)?.sourceUrl ?: return@launch
            val game = gameDao.getById(gameId) ?: return@launch
            queueArtIfStale(gameId, slot, listOf(source), game.rommId, game.steamAppId, game.title)
        }
    }

    private fun queueArt(
        gameId: Long,
        slot: ArtSlot,
        urls: List<String>,
        rommId: Long?,
        steamAppId: Long?,
        title: String,
        cachedPath: String?
    ) {
        val url = urls.firstOrNull() ?: return
        when (slot) {
            ArtSlot.COVER -> when {
                rommId != null && cachedPath != null -> queueCoverRevalidation(cachedPath, urls, rommId, title)
                rommId != null -> queueCoverCache(urls, rommId, title)
                else -> queueCoverCacheByGameId(urls, gameId)
            }
            ArtSlot.BACKGROUND -> when {
                rommId != null && cachedPath != null -> queueBackgroundRevalidation(cachedPath, urls, rommId, title)
                rommId != null -> queueBackgroundCache(urls, rommId, title)
                steamAppId != null -> queueSteamBackgroundCache(url, steamAppId, title)
                else -> queueBackgroundCacheByGameId(url, gameId, title)
            }
            ArtSlot.LOGO -> if (rommId != null) queueBoxFaceCache(urls, rommId, title, BoxFace.LOGO)
            ArtSlot.BOX_3D -> if (rommId != null) queueBoxFaceCache(urls, rommId, title, BoxFace.BOX_3D)
            ArtSlot.BOX_SPINE -> if (rommId != null) queueBoxFaceCache(urls, rommId, title, BoxFace.SPINE)
            ArtSlot.BOX_BACK -> if (rommId != null) queueBoxFaceCache(urls, rommId, title, BoxFace.BACK)
        }
    }

    fun queueScreenshotCache(gameId: Long, rommId: Long, gameTitle: String) {
        scope.launch {
            screenshotQueue.send(ScreenshotCacheRequest(gameId, rommId, gameTitle))
            startScreenshotProcessingIfNeeded()
        }
    }

    /**
     * Caches every screenshot row of [gameId] not yet cached from its source url. When the game
     * has no cached background, the second cached screenshot, or the only one, becomes it.
     */
    fun queueScreenshotCacheByGameId(gameId: Long) {
        scope.launch {
            val cachedPaths = cacheScreenshotRows(gameId, "ss_g$gameId", maxWidth = 960, quality = 80)
            if (cachedPaths.isEmpty()) return@launch
            val background = gameArtDao.get(gameId, ArtSlot.BACKGROUND.name)
            if (background?.cachedPath == null) {
                val backgroundPath = cachedPaths.getOrNull(1) ?: cachedPaths.first()
                gameArtDao.setCached(gameId, ArtSlot.BACKGROUND, backgroundPath, null)
                Log.d(TAG, "Set screenshot ${if (cachedPaths.size > 1) "2" else "1"} as background for gameId $gameId")
            }
        }
    }

    private suspend fun cacheScreenshotRows(gameId: Long, filePrefix: String, maxWidth: Int, quality: Int): List<String> {
        val dir = platformDir(resolveGamePlatformSlug(gameId), "screenshots")
        return gameScreenshotDao.getForGame(gameId).mapNotNull { row ->
            val current = row.cachedPath
            when {
                current != null && row.cachedFromUrl == row.sourceUrl -> current
                current != null && canBackfillCachedFromUrl(current, row.cachedFromUrl, row.sourceUrl) -> {
                    gameScreenshotDao.backfillCachedFromUrl(gameId, row.position, current, row.sourceUrl)
                    current
                }
                else -> {
                    val cachedFile = File(dir, "${filePrefix}_${row.position}_${row.sourceUrl.md5Hash()}.jpg")
                    if (!writeScreenshotFile(cachedFile, row.sourceUrl, maxWidth, quality)) return@mapNotNull null
                    gameScreenshotDao.setCached(gameId, row.position, cachedFile.absolutePath, row.sourceUrl)
                    cachedFile.absolutePath
                }
            }
        }
    }

    private fun writeScreenshotFile(cachedFile: File, url: String, maxWidth: Int, quality: Int): Boolean {
        if (cachedFile.exists()) {
            if (isValidImageFile(cachedFile)) return true
            cachedFile.delete()
            Log.w(TAG, "Deleted invalid cached screenshot: ${cachedFile.name}")
        }
        val bitmap = downloadAndResize(url, maxWidth) ?: return false
        try {
            FileOutputStream(cachedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write screenshot cache ${cachedFile.name}: ${e.message}", e)
            cachedFile.delete()
            return false
        } finally {
            bitmap.recycle()
        }
        if (!isValidImageFile(cachedFile)) {
            cachedFile.delete()
            Log.w(TAG, "Deleted newly cached invalid screenshot: ${cachedFile.name}")
            return false
        }
        Log.d(TAG, "Cached screenshot ${cachedFile.name}: ${cachedFile.length() / 1024}KB")
        return true
    }

    suspend fun cacheSingleScreenshot(gameId: Long, url: String, index: Int): String? {
        val slug = resolveGamePlatformSlug(gameId)
        val fileName = "ss_g${gameId}_${index}_${url.md5Hash()}.jpg"
        val cachedFile = File(platformDir(slug, "screenshots"), fileName)

        if (!cachedFile.exists() || !isValidImageFile(cachedFile)) {
            val bitmap = downloadAndResize(url, 480) ?: return null
            FileOutputStream(cachedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            bitmap.recycle()
            if (!isValidImageFile(cachedFile)) return null
        }
        gameScreenshotDao.setCached(gameId, index, cachedFile.absolutePath, url)
        return cachedFile.absolutePath
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
        val total = gameScreenshotDao.countWithSource()
        val cached = gameScreenshotDao.countCached()
        _screenshotProgress.value = _screenshotProgress.value.copy(
            isProcessing = isProcessing,
            cachedCount = cached,
            totalCount = total
        )
    }

    private suspend fun processScreenshotRequest(request: ScreenshotCacheRequest) {
        cacheScreenshotRows(request.gameId, "ss_${request.rommId}", maxWidth = 480, quality = 75)
    }

    suspend fun userScreenshotTargetFile(rommId: Long, screenshotId: Long, version: String): File {
        val slug = resolveRommPlatformSlug(rommId)
        return File(platformDir(slug, "screenshots"), "uss_${rommId}_${screenshotId}_${version.md5Hash()}.png")
    }

    fun pruneStaleUserScreenshots(rommId: Long, screenshotId: Long, keep: File) {
        keep.parentFile?.listFiles()?.forEach { file ->
            if (file.name.startsWith("uss_${rommId}_${screenshotId}_") && file.name != keep.name) file.delete()
        }
    }

    /**
     * Queues every RomM game holding a screenshot whose cached file does not come from its source
     * url. Rows cached before `cachedFromUrl` existed are matched by file name first and only
     * queued when the name does not carry the source url's hash.
     */
    fun resumePendingScreenshotCache() {
        scope.launch {
            val (backfill, stale) = gameScreenshotDao.getPending().partition {
                canBackfillCachedFromUrl(it.cachedPath, it.cachedFromUrl, it.sourceUrl)
            }
            if (backfill.isNotEmpty()) gameScreenshotDao.backfillCachedFromUrls(backfill)
            val queued = stale.distinctBy { it.gameId }.mapNotNull { row ->
                row.rommId?.let { rommId -> queueScreenshotCache(row.gameId, rommId, row.title) }
            }
            if (backfill.isNotEmpty() || queued.isNotEmpty()) {
                Log.i(TAG, "Pending screenshots: ${backfill.size} matched by name, ${queued.size} games queued")
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

    private fun hasTransparentPixels(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { (it ushr 24) < 0xFF }
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

    enum class BoxFace { BACK, SPINE, LOGO, BOX_3D }

    fun queueCoverCache(url: String, rommId: Long, gameTitle: String = "") =
        queueCoverCache(listOf(url), rommId, gameTitle)

    fun queueCoverCache(urls: List<String>, rommId: Long, gameTitle: String = "") {
        if (urls.isEmpty()) return
        scope.launch {
            coverQueue.send(ImageCacheRequest(urls, rommId, ImageType.COVER, gameTitle, isSteam = false))
            startCoverProcessingIfNeeded()
        }
    }

    /**
     * Checks whether the server art behind the cached cover at [cachedPath] actually changed
     * before downloading it again. Unchanged art keeps its file, renamed to match [urls].
     */
    fun queueCoverRevalidation(cachedPath: String, urls: List<String>, rommId: Long, gameTitle: String = "") {
        if (urls.isEmpty()) return
        scope.launch {
            coverQueue.send(
                ImageCacheRequest(urls, rommId, ImageType.COVER, gameTitle, revalidateFrom = cachedPath)
            )
            startCoverProcessingIfNeeded()
        }
    }

    fun queueBackgroundRevalidation(cachedPath: String, urls: List<String>, rommId: Long, gameTitle: String = "") {
        if (urls.isEmpty()) return
        scope.launch {
            queue.send(
                ImageCacheRequest(urls, rommId, ImageType.BACKGROUND, gameTitle, revalidateFrom = cachedPath)
            )
            startProcessingIfNeeded()
        }
    }

    fun queueBoxFaceCache(urls: List<String>, rommId: Long, gameTitle: String = "", face: BoxFace) {
        if (urls.isEmpty()) return
        scope.launch {
            coverQueue.send(ImageCacheRequest(urls, rommId, face.imageType, gameTitle, isSteam = false))
            startCoverProcessingIfNeeded()
        }
    }

    private val BoxFace.imageType: ImageType
        get() = when (this) {
            BoxFace.BACK -> ImageType.BOX_BACK
            BoxFace.SPINE -> ImageType.BOX_SPINE
            BoxFace.LOGO -> ImageType.LOGO
            BoxFace.BOX_3D -> ImageType.BOX_3D
        }

    private val ImageType.boxFaceSlot: ArtSlot
        get() = when (this) {
            ImageType.LOGO -> ArtSlot.LOGO
            ImageType.BOX_3D -> ArtSlot.BOX_3D
            ImageType.BOX_BACK -> ArtSlot.BOX_BACK
            else -> ArtSlot.BOX_SPINE
        }

    fun queueCoverCacheByGameId(url: String, gameId: Long) =
        queueCoverCacheByGameId(listOf(url), gameId)

    fun queueCoverCacheByGameId(urls: List<String>, gameId: Long) {
        if (urls.isEmpty()) return
        scope.launch {
            coverQueue.send(ImageCacheRequest(urls, gameId, ImageType.COVER, gameId = gameId))
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
                    if (request.type.isBoxFace) processBoxFaceRequest(request) else processCoverRequest(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process cover for ${request.id}: ${e.message}")
                }
                yield()

                if (coverQueue.isEmpty) break
            }
            isProcessingCovers = false
        }
    }

    private suspend fun processCoverRequest(request: ImageCacheRequest, replacing: Boolean = false) {
        val isGameIdRequest = request.gameId != null
        val game = if (isGameIdRequest) {
            gameDao.getById(request.gameId!!)
        } else {
            gameDao.getByRommId(request.id)
        }
        val prefix = if (isGameIdRequest) "cover_g${request.gameId}" else "cover_${request.id}"
        val slug = if (isGameIdRequest) resolveGamePlatformSlug(request.gameId!!)
                   else resolveRommPlatformSlug(request.id)
        val coverDir = platformDir(slug, "covers")
        request.revalidateFrom?.let { cachedPath ->
            revalidateCachedArt(
                request, File(cachedPath), coverDir, prefix,
                store = { path -> storeCachedArt(request, ArtSlot.COVER, path) },
                replace = { processCoverRequest(request.copy(revalidateFrom = null), replacing = true) },
                drop = { dropCachedArt(request, ArtSlot.COVER) }
            )
            return
        }
        if (!replacing && isCachedFromSource(request, ArtSlot.COVER)) return
        val idLabel = if (isGameIdRequest) "gameId ${request.gameId}" else "rommId ${request.id}"

        for ((index, url) in request.urls.withIndex()) {
            val baseName = "${prefix}_${url.md5Hash()}"
            val existingFile = listOf("jpg", "png")
                .map { File(coverDir, "$baseName.$it") }
                .firstOrNull { it.exists() }

            if (existingFile != null) {
                if (isValidImageFile(existingFile)) {
                    storeCachedArt(request, ArtSlot.COVER, existingFile.absolutePath)
                    pruneReplacedArt(coverDir, prefix, existingFile, storedArtPath(request, ArtSlot.COVER))
                    return
                }
                existingFile.delete()
                Log.w(TAG, "Deleted invalid cached cover: ${existingFile.name}")
            }

            if (missingArt.isKnownMissing(url)) continue
            val bitmap = downloadAndResize(url, 400)
            if (bitmap == null) {
                logCandidateRejected("cover", idLabel, index, request.urls.size, url, "no decodable image")
                continue
            }

            val cachedFile = writeCoverBitmap(bitmap, coverDir, baseName)
            if (cachedFile == null) {
                logCandidateRejected("cover", idLabel, index, request.urls.size, url, "cached file did not decode")
                continue
            }

            Log.d(TAG, "Cached cover for $idLabel: ${cachedFile.length() / 1024}KB")
            storeCachedArt(request, ArtSlot.COVER, cachedFile.absolutePath)
            pruneReplacedArt(coverDir, prefix, cachedFile, storedArtPath(request, ArtSlot.COVER))
            return
        }

        val steamAppId = game?.steamAppId
        if (steamAppId != null) {
            val steamBitmap = downloadSteamCoverFallback(steamAppId)
            val cachedFile = steamBitmap?.let {
                writeCoverBitmap(it, coverDir, "${prefix}_steam_$steamAppId")
            }
            if (cachedFile != null) {
                Log.d(TAG, "Cached steam fallback cover for $idLabel: ${cachedFile.length() / 1024}KB")
                storeCachedArt(request, ArtSlot.COVER, cachedFile.absolutePath)
                pruneReplacedArt(coverDir, prefix, cachedFile, storedArtPath(request, ArtSlot.COVER))
                return
            }
        }

        logAllCandidatesRejected("cover", idLabel, request)
    }

    private fun writeCoverBitmap(bitmap: Bitmap, coverDir: File, baseName: String): File? {
        val hasTransparency = hasTransparentPixels(bitmap)
        val cachedFile = File(coverDir, "$baseName.${if (hasTransparency) "png" else "jpg"}")
        FileOutputStream(cachedFile).use { out ->
            if (hasTransparency) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        }
        bitmap.recycle()

        if (!isValidImageFile(cachedFile)) {
            cachedFile.delete()
            return null
        }
        return cachedFile
    }

    private suspend fun storeCachedArt(request: ImageCacheRequest, slot: ArtSlot, localPath: String) {
        val game = storedGame(request) ?: return
        gameArtDao.setCached(game.id, slot, localPath, request.urls.firstOrNull())
    }

    private suspend fun isCachedFromSource(request: ImageCacheRequest, slot: ArtSlot): Boolean {
        val source = request.urls.firstOrNull() ?: return false
        val game = storedGame(request) ?: return false
        val row = gameArtDao.get(game.id, slot.name) ?: return false
        val cachedPath = row.cachedPath ?: return false
        return row.cachedFromUrl == source && File(cachedPath).exists()
    }

    private suspend fun storedGame(request: ImageCacheRequest) = when {
        request.gameId != null -> gameDao.getById(request.gameId)
        request.isSteam -> gameDao.getBySteamAppId(request.id)
        else -> gameDao.getByRommId(request.id)
    }

    private suspend fun revalidateCachedArt(
        request: ImageCacheRequest,
        cached: File,
        dir: File,
        prefix: String,
        store: suspend (String) -> Unit,
        replace: suspend () -> Unit,
        drop: suspend () -> Unit
    ) {
        if (!cached.exists()) {
            replace()
            return
        }
        val check = checkCandidates(request.urls)
        val answer = check.answer
        when (cachedArtDecision(cached.lastModified(), answer?.second, check.everyCandidateGone)) {
            CachedArtDecision.SKIP ->
                Logger.info(TAG, "No art candidate answered for ${request.id}, keeping ${cached.name}")
            CachedArtDecision.DROP -> {
                Logger.info(TAG, "Art for ${request.id} is gone from the server, dropping ${cached.name}")
                drop()
            }
            CachedArtDecision.REPLACE -> replace()
            CachedArtDecision.KEEP_AND_RENAME -> {
                val url = answer?.first ?: return
                val renamed = File(dir, "${prefix}_${url.md5Hash()}.${cached.extension}")
                val kept = if (renamed.exists() || cached.renameTo(renamed)) renamed else cached
                store(kept.absolutePath)
                pruneReplacedArt(dir, prefix, kept, kept.absolutePath)
            }
        }
    }

    private class CandidateCheck(val answer: Pair<String, Long>?, val everyCandidateGone: Boolean)

    private fun checkCandidates(urls: List<String>): CandidateCheck {
        var everyCandidateGone = urls.isNotEmpty()
        for (url in urls) {
            if (missingArt.isKnownMissing(url)) continue
            when (val head = serverHead(url)) {
                is ServerHead.Modified -> return CandidateCheck(url to head.at, everyCandidateGone = false)
                ServerHead.Gone -> Unit
                ServerHead.Unknown -> everyCandidateGone = false
            }
        }
        return CandidateCheck(null, everyCandidateGone)
    }

    private sealed interface ServerHead {
        class Modified(val at: Long) : ServerHead
        data object Gone : ServerHead
        data object Unknown : ServerHead
    }

    private fun serverHead(url: String): ServerHead = try {
        val connection = URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            when (val status = connection.responseCode) {
                in 200..299 -> ServerHead.Modified(connection.lastModified)
                java.net.HttpURLConnection.HTTP_NOT_FOUND, java.net.HttpURLConnection.HTTP_GONE -> {
                    missingArt.markMissing(url)
                    Logger.warn(TAG, "Image not on the server ($status), skipping it for a while: $url")
                    ServerHead.Gone
                }
                else -> ServerHead.Unknown
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Logger.warn(TAG, "Could not check cached art against the server: ${e.message}")
        ServerHead.Unknown
    }

    private suspend fun dropCachedArt(request: ImageCacheRequest, slot: ArtSlot) {
        val game = storedGame(request) ?: return
        gameArtDao.clearCached(game.id, slot)
    }

    private suspend fun processBoxFaceRequest(request: ImageCacheRequest) {
        if (isCachedFromSource(request, request.type.boxFaceSlot)) return
        val prefix = when (request.type) {
            ImageType.BOX_BACK -> "box_back_${request.id}"
            ImageType.LOGO -> "game_logo_${request.id}"
            ImageType.BOX_3D -> "box_3d_${request.id}"
            else -> "box_spine_${request.id}"
        }
        val maxWidth = when (request.type) {
            ImageType.LOGO -> LOGO_MAX_WIDTH
            ImageType.BOX_3D -> BOX_3D_MAX_WIDTH
            else -> BOX_FACE_MAX_WIDTH
        }
        val slug = resolveRommPlatformSlug(request.id)
        val coverDir = platformDir(slug, "covers")
        val kind = when (request.type) {
            ImageType.BOX_BACK -> "box back"
            ImageType.LOGO -> "logo"
            ImageType.BOX_3D -> "3d box"
            else -> "box spine"
        }
        val idLabel = "rommId ${request.id}"

        for ((index, url) in request.urls.withIndex()) {
            val baseName = "${prefix}_${url.md5Hash()}"
            val existingFile = listOf("jpg", "png")
                .map { File(coverDir, "$baseName.$it") }
                .firstOrNull { it.exists() }

            if (existingFile != null) {
                if (isValidImageFile(existingFile)) {
                    updateGameBoxFace(request, existingFile.absolutePath)
                    return
                }
                existingFile.delete()
            }

            if (missingArt.isKnownMissing(url)) continue
            val bitmap = downloadAndResize(url, maxWidth)
            if (bitmap == null) {
                logCandidateRejected(kind, idLabel, index, request.urls.size, url, "no decodable image")
                continue
            }

            val cachedFile = writeCoverBitmap(bitmap, coverDir, baseName)
            if (cachedFile == null) {
                logCandidateRejected(kind, idLabel, index, request.urls.size, url, "cached file did not decode")
                continue
            }

            Log.d(TAG, "Cached box face ${cachedFile.name} for $idLabel")
            updateGameBoxFace(request, cachedFile.absolutePath)
            return
        }

        logAllCandidatesRejected(kind, idLabel, request)
    }

    private suspend fun updateGameBoxFace(request: ImageCacheRequest, localPath: String) {
        val game = gameDao.getByRommId(request.id) ?: return
        gameArtDao.setCached(game.id, request.type.boxFaceSlot, localPath, request.urls.firstOrNull())
    }

    /**
     * Caches a game's images and waits for them, for a refresh the player asked for and is
     * watching. The queue is for background work that may take as long as it takes; a manual
     * refresh has to finish its own image work before it reports itself done, or the row is
     * written pointing at a remote url that nothing has fetched yet.
     */
    suspend fun cacheGameImagesNow(
        rommId: Long,
        gameTitle: String,
        coverUrls: List<String>,
        backgroundUrls: List<String>,
        boxBackUrls: List<String> = emptyList(),
        boxSpineUrls: List<String> = emptyList(),
        logoUrls: List<String> = emptyList(),
        box3dUrls: List<String> = emptyList()
    ): CachedGameImages = withContext(Dispatchers.IO) {
        if (coverUrls.isNotEmpty()) {
            processCoverRequest(ImageCacheRequest(coverUrls, rommId, ImageType.COVER, gameTitle, isSteam = false))
        }
        if (backgroundUrls.isNotEmpty()) {
            processRequest(ImageCacheRequest(backgroundUrls, rommId, ImageType.BACKGROUND, gameTitle, isSteam = false))
        }
        listOf(
            ImageType.BOX_BACK to boxBackUrls,
            ImageType.BOX_SPINE to boxSpineUrls,
            ImageType.LOGO to logoUrls,
            ImageType.BOX_3D to box3dUrls
        ).forEach { (type, urls) ->
            if (urls.isNotEmpty()) {
                processBoxFaceRequest(ImageCacheRequest(urls, rommId, type, gameTitle, isSteam = false))
            }
        }

        val game = gameDao.getByRommId(rommId)
        val art = game?.let { gameArtDao.getForGame(it.id) }.orEmpty().associateBy { it.slot }
        CachedGameImages(
            coverPath = art[ArtSlot.COVER.name]?.cachedPath,
            backgroundPath = art[ArtSlot.BACKGROUND.name]?.cachedPath,
            logoPath = art[ArtSlot.LOGO.name]?.cachedPath
        )
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

    suspend fun applyArtOverride(gameId: Long, slot: ArtSlot, url: String): Boolean =
        withContext(Dispatchers.IO) {
            val bitmap = downloadAndResize(url, slot.maxWidth) ?: return@withContext false
            storeArtOverride(gameId, slot, url.md5Hash(), bitmap)
        }

    /**
     * Copies a picture the user already has into the cache as [slot]'s override, read through
     * [FileAccessLayer] so restricted storage paths resolve.
     */
    suspend fun applyArtOverrideFromFile(gameId: Long, slot: ArtSlot, path: String): Boolean =
        withContext(Dispatchers.IO) {
            val bytes = fileAccessLayer.readBytes(path) ?: return@withContext false
            val bitmap = runCatching {
                decodeSampled(slot.maxWidth) { options ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                }
            }.getOrNull() ?: return@withContext false
            storeArtOverride(gameId, slot, path.md5Hash(), bitmap)
        }

    suspend fun clearArtOverride(gameId: Long, slot: ArtSlot): Unit = withContext(Dispatchers.IO) {
        val previous = gameArtDao.get(gameId, slot.name)?.overridePath ?: return@withContext
        gameArtDao.clearOverride(gameId, slot)
        deleteOverrideFile(previous)
    }

    private suspend fun storeArtOverride(
        gameId: Long,
        slot: ArtSlot,
        sourceKey: String,
        bitmap: Bitmap
    ): Boolean {
        val game = gameDao.getById(gameId)
        if (game == null) {
            bitmap.recycle()
            return false
        }
        val dir = platformDir(game.platformSlug.ifBlank { FALLBACK_PLATFORM }, slot.directoryName)
        val baseName = "${slot.filePrefix}${gameId}_$sourceKey"
        val written: File? = try {
            when (slot) {
                ArtSlot.COVER -> writeCoverBitmap(bitmap, dir, baseName)
                ArtSlot.BACKGROUND -> writeValidatedBitmap(
                    bitmap, File(dir, "$baseName.jpg"), Bitmap.CompressFormat.JPEG, BACKGROUND_JPEG_QUALITY
                )
                ArtSlot.BOX_SPINE, ArtSlot.BOX_BACK -> writeCoverBitmap(bitmap, dir, baseName)
                ArtSlot.LOGO, ArtSlot.BOX_3D -> writeValidatedBitmap(
                    bitmap, File(dir, "$baseName.png"), Bitmap.CompressFormat.PNG, 100
                )
            }
        } catch (e: java.io.IOException) {
            Logger.warn(TAG, "Failed to write $slot override for gameId $gameId: ${e.message}")
            null
        }
        val file = written ?: return false

        val previous = gameArtDao.get(gameId, slot.name)?.overridePath
        gameArtDao.setOverride(gameId, slot, file.absolutePath)
        if (previous != null && previous != file.absolutePath) deleteOverrideFile(previous)
        return true
    }

    private fun writeValidatedBitmap(
        bitmap: Bitmap,
        target: File,
        format: Bitmap.CompressFormat,
        quality: Int
    ): File? {
        try {
            FileOutputStream(target).use { out -> bitmap.compress(format, quality, out) }
        } finally {
            bitmap.recycle()
        }
        if (!isValidImageFile(target)) {
            target.delete()
            return null
        }
        return target
    }

    private suspend fun storedArtPath(request: ImageCacheRequest, slot: ArtSlot): String? {
        val game = storedGame(request) ?: return null
        return gameArtDao.get(game.id, slot.name)?.cachedPath
    }

    private fun pruneReplacedArt(dir: File, prefix: String, kept: File, storedPath: String?) {
        val keep = setOfNotNull(kept.absolutePath, storedPath)
        dir.listFiles { file -> file.name.startsWith("${prefix}_") && file.absolutePath !in keep }
            ?.forEach { stale ->
                if (!stale.delete() && stale.exists()) {
                    Logger.warn(TAG, "Could not delete replaced cached art: ${stale.absolutePath}")
                }
            }
    }

    private fun deleteOverrideFile(path: String) {
        val file = File(path)
        val ownedByOverride = ArtSlot.entries.any { file.name.startsWith(it.filePrefix) } ||
            LEGACY_OVERRIDE_FILE_PREFIXES.any { file.name.startsWith(it) }
        if (!ownedByOverride) return
        if (!file.delete() && file.exists()) {
            Logger.warn(TAG, "Could not delete replaced artwork override: $path")
        }
    }

    private val ArtSlot.maxWidth: Int
        get() = when (this) {
            ArtSlot.COVER -> COVER_MAX_WIDTH
            ArtSlot.BACKGROUND -> BACKGROUND_MAX_WIDTH
            ArtSlot.LOGO -> LOGO_MAX_WIDTH
            ArtSlot.BOX_3D -> BOX_3D_MAX_WIDTH
            ArtSlot.BOX_SPINE, ArtSlot.BOX_BACK -> BOX_FACE_MAX_WIDTH
        }

    private val ArtSlot.directoryName: String
        get() = when (this) {
            ArtSlot.COVER, ArtSlot.BOX_3D, ArtSlot.BOX_SPINE, ArtSlot.BOX_BACK -> "covers"
            ArtSlot.BACKGROUND -> "backgrounds"
            ArtSlot.LOGO -> "logos"
        }

    private val ArtSlot.filePrefix: String
        get() = when (this) {
            ArtSlot.COVER -> "cover_override_"
            ArtSlot.BACKGROUND -> "bg_override_"
            ArtSlot.LOGO -> "logo_override_"
            ArtSlot.BOX_3D -> "box_3d_override_"
            ArtSlot.BOX_SPINE -> "box_spine_override_"
            ArtSlot.BOX_BACK -> "box_back_override_"
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

    suspend fun setScreenshotAsBackground(gameId: Long, screenshotPath: String): Boolean =
        if (screenshotPath.startsWith("/")) {
            applyArtOverrideFromFile(gameId, ArtSlot.BACKGROUND, screenshotPath)
        } else {
            applyArtOverride(gameId, ArtSlot.BACKGROUND, screenshotPath)
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
                gameArtDao.setCached(request.gameId, ArtSlot.COVER, cachedFile.absolutePath, null)
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
        gameArtDao.setCached(request.gameId, ArtSlot.COVER, cachedFile.absolutePath, null)
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

    private fun shouldClearMissingPath(path: String, probe: VolumeProbe): Boolean {
        if (!path.startsWith("/")) return false
        return probe.isGenuinelyAbsent(path)
    }

    /**
     * Decoding a header per cached file costs a read per file, and the cache holds one for
     * every cover, screenshot and box face in the library. Only files written since the last
     * pass are checked, because a file that gets rewritten carries a newer timestamp. Pass
     * [force] to sweep everything regardless, which is what the settings action does.
     */
    suspend fun validateAndCleanCache(
        force: Boolean = false,
        onProgress: (suspend (phase: String, current: Int, total: Int) -> Unit)? = null
    ): CacheValidationResult {
        var deleted = 0
        var cleared = 0

        val probe = volumeHealth.newProbe()
        val marker = File(cacheDir, VALIDATION_MARKER)
        val validatedThrough = if (force) 0L else marker.takeIf { it.exists() }?.lastModified() ?: 0L
        val sweepStartedAt = System.currentTimeMillis()

        val listed = withContext(Dispatchers.IO) {
            cacheDir.walk()
                .filter { it.isFile && it.name != ".nomedia" && it.name != VALIDATION_MARKER }
                .map { ArtCacheFile(it.absolutePath, it.name, it.lastModified()) }
                .toList()
        }
        val files = listed.filter { it.lastModified >= validatedThrough }.map { File(it.path) }
        val totalFiles = files.size
        onProgress?.invoke("Checking $totalFiles cached files...", 0, totalFiles)

        val invalid = HashSet<String>()
        withContext(Dispatchers.IO) {
            files.forEachIndexed { index, file ->
                if (!isValidImageFile(file, minSizeBytes = 512)) {
                    file.delete()
                    invalid += file.absolutePath
                    deleted++
                    Log.w(TAG, "Validation deleted invalid file: ${file.name}")
                }
                if (index % 50 == 0) {
                    onProgress?.invoke("Checking cached files...", index, totalFiles)
                }
            }
            runCatching {
                if (!marker.exists()) marker.createNewFile()
                marker.setLastModified(sweepStartedAt)
            }
        }

        withContext(Dispatchers.IO) {
            val screenshotPaths = gameScreenshotDao.getCached().mapNotNull { it.cachedPath }
            val plan = planArtSweep(
                listedFiles = listed.filterNot { it.path in invalid },
                cachedPaths = gameArtDao.getAllCachedPaths() + screenshotPaths,
                overridePaths = gameArtDao.getAllOverridePaths(),
                recentCutoff = sweepStartedAt - SWEEP_RECENT_FILE_GRACE_MS
            )
            plan.orphanFiles.forEach { path ->
                if (File(path).delete()) deleted++
            }
            val goneCached = plan.missingCachedPaths.filter { shouldClearMissingPath(it, probe) }
            if (goneCached.isNotEmpty()) {
                gameArtDao.clearCachedPathsChunked(goneCached)
                gameScreenshotDao.clearCachedPathsChunked(goneCached)
            }
            val goneOverrides = plan.missingOverridePaths.filter { shouldClearMissingPath(it, probe) }
            goneOverrides.chunked(ART_PATH_CHUNK).forEach { gameArtDao.clearOverridePaths(it) }
            cleared += goneCached.size + goneOverrides.size
            if (plan.orphanFiles.isNotEmpty()) {
                Log.i(TAG, "Art sweep removed ${plan.orphanFiles.size} files no game references")
            }
        }

        val platforms = withContext(Dispatchers.IO) { platformDao.getAllPlatforms() }
        onProgress?.invoke("Checking ${platforms.size} platform logos...", 0, platforms.size)

        withContext(Dispatchers.IO) {
            platforms.forEach { platform ->
                if (platform.logoPath != null && shouldClearMissingPath(platform.logoPath, probe)) {
                    platformDao.clearLogoPath(platform.id)
                    cleared++
                }
            }
        }

        migrateLegacyIgdbCovers()

        val orphanDirs = listOf("steam")
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
                if (gameArtDao.get(game.id, ArtSlot.COVER.name)?.cachedPath != file.absolutePath) return@forEach

                val hash = "legacy-igdb-$steamAppId".md5Hash()
                val newFile = File(platformDir(game.platformSlug, "covers"), "cover_g${game.id}_$hash.jpg")
                if (file.renameTo(newFile)) {
                    gameArtDao.relocateCachedPath(game.id, ArtSlot.COVER.name, file.absolutePath, newFile.absolutePath)
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

        updated += relocateArtPaths { path ->
            if (path.startsWith(cachePath) && !File(path).exists()) {
                resolveShardedDestination(File(path).name)
                    ?.takeIf { it.exists() }
                    ?.absolutePath
                    ?: path
            } else {
                path
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
            val achievements = achievementDao.getAllForGame(gameId)
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
