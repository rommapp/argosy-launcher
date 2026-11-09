package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.isUserScreenshotFileCurrent
import com.nendo.argosy.data.cache.userScreenshotExtension
import com.nendo.argosy.data.local.dao.GameScreenshotDao
import com.nendo.argosy.data.local.entity.GameScreenshotEntity
import com.nendo.argosy.data.local.entity.ScreenshotSource
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.parseTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMGalleryService"
private const val PARTIAL_SUFFIX = ".part"

data class GalleryShot(val source: String, val screenshot: RomMUserScreenshot)

data class GalleryPlan(val rows: List<GameScreenshotEntity>, val removedFiles: List<String>)

/**
 * The gallery screenshots RomM shows the signed-in user for this rom: their own first, then other
 * users' public ones, each newest first by `created_at`. Save and state thumbnails are left out.
 * A server that sends no `all_user_screenshots` yields the user's own shots only.
 */
fun RomMRom.galleryShots(): List<GalleryShot> {
    val visible = allUserScreenshots.orEmpty().filter { it.isGallery }
    val visibleById = visible.associateBy { it.id }
    val own = userScreenshots.orEmpty().filter { it.isGallery }.map { visibleById[it.id] ?: it }
    val ownIds = own.mapTo(HashSet()) { it.id }
    val community = visible.filter { it.id !in ownIds }
    return own.newestFirst().map { GalleryShot(ScreenshotSource.OWN, it) } +
        community.newestFirst().map { GalleryShot(ScreenshotSource.COMMUNITY, it) }
}

private fun List<RomMUserScreenshot>.newestFirst(): List<RomMUserScreenshot> =
    distinctBy { it.id }.sortedWith(
        compareByDescending<RomMUserScreenshot> { shot -> shot.createdAt?.let(::parseTimestamp) ?: Long.MIN_VALUE }
            .thenByDescending { it.id }
    )

/**
 * The gallery rows [gameId] holds once RomM reports [shots], one per shot in the same order and
 * numbered from 0 within each source. A shot already stored keeps its cached file. [GalleryPlan.removedFiles]
 * holds the cached files of stored gallery rows absent from [shots]. Official rows in [existing]
 * are ignored.
 */
fun planGalleryRows(
    gameId: Long,
    existing: Collection<GameScreenshotEntity>,
    shots: List<GalleryShot>,
    contentUrl: (Long) -> String
): GalleryPlan {
    val gallery = existing.filterNot { it.isOfficial }
    val storedById = gallery.filter { it.rommScreenshotId != null }.associateBy { it.rommScreenshotId }
    val nextPosition = HashMap<String, Int>()
    val rows = shots.map { (source, shot) ->
        val position = nextPosition.getOrDefault(source, 0)
        nextPosition[source] = position + 1
        val stored = storedById[shot.id]
        GameScreenshotEntity(
            gameId = gameId,
            position = position,
            sourceUrl = contentUrl(shot.id),
            cachedPath = stored?.cachedPath,
            cachedFromUrl = stored?.cachedFromUrl,
            source = source,
            rommScreenshotId = shot.id,
            ownerUsername = shot.username
        )
    }
    val reportedIds = shots.mapTo(HashSet()) { it.screenshot.id }
    val removed = gallery.filter { it.rommScreenshotId !in reportedIds }.mapNotNull { it.cachedPath }
    return GalleryPlan(rows, removed)
}

@Singleton
class RomMGalleryService @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val apiClient: RomMApiClient,
    private val gameScreenshotDao: GameScreenshotDao,
    private val imageCacheManager: ImageCacheManager
) {
    private val refreshLock = Mutex()

    /**
     * Reconciles [gameId]'s gallery rows with what RomM reports for [rommId] and caches each
     * shot's current file, downloading only when [downloadEnabled]. Returns every screenshot row
     * of the game afterwards, or null when RomM could not be read, leaving stored rows and files
     * untouched.
     */
    suspend fun refresh(gameId: Long, rommId: Long, downloadEnabled: Boolean): List<GameScreenshotEntity>? =
        withContext(Dispatchers.IO) {
            refreshLock.withLock {
                val api = connectionManager.getApi() ?: return@withLock null
                val rom = fetchRom(api, rommId) ?: return@withLock null
                val shots = rom.galleryShots()
                val plan = planGalleryRows(
                    gameId,
                    gameScreenshotDao.getGalleryForGame(gameId),
                    shots,
                    apiClient::buildScreenshotContentUrl
                )
                gameScreenshotDao.replaceGallery(gameId, plan.rows)
                plan.removedFiles.forEach { File(it).delete() }
                val shotsById = shots.associate { it.screenshot.id to it.screenshot }
                plan.rows.forEach { row ->
                    val shot = row.rommScreenshotId?.let(shotsById::get) ?: return@forEach
                    cacheShot(api, row, rommId, shot, downloadEnabled)
                }
                gameScreenshotDao.getAllForGame(gameId)
            }
        }

    private suspend fun fetchRom(api: RomMApi, rommId: Long): RomMRom? = try {
        val response = api.getRom(rommId)
        if (response.isSuccessful) response.body() else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warn(TAG, "fetchRom: rommId $rommId: ${e.message}")
        null
    }

    private suspend fun cacheShot(
        api: RomMApi,
        row: GameScreenshotEntity,
        rommId: Long,
        shot: RomMUserScreenshot,
        downloadEnabled: Boolean
    ) {
        val version = shot.updatedAt.orEmpty()
        val cached = row.cachedPath?.let(::File)
        if (cached != null && cached.length() > 0 && isUserScreenshotFileCurrent(cached.name, rommId, shot.id, version)) {
            return
        }
        try {
            val declared = imageCacheManager.userScreenshotTargetFile(
                rommId, shot.id, version, userScreenshotExtension(shot.fileExtension, null)
            )
            val target = when {
                declared.length() > 0 -> declared
                downloadEnabled -> download(api, rommId, shot, version)
                else -> null
            } ?: return
            gameScreenshotDao.setGalleryCached(row.gameId, shot.id, target.absolutePath, row.sourceUrl)
            imageCacheManager.pruneStaleUserScreenshots(rommId, shot.id, target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "cacheShot: screenshot ${shot.id} of rommId $rommId: ${e.message}")
        }
    }

    private suspend fun download(api: RomMApi, rommId: Long, shot: RomMUserScreenshot, version: String): File? {
        val response = api.downloadScreenshotContent(shot.id)
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            body?.close()
            return null
        }
        val extension = userScreenshotExtension(shot.fileExtension, body.contentType()?.toString())
        val target = imageCacheManager.userScreenshotTargetFile(rommId, shot.id, version, extension)
        val partial = File(target.path + PARTIAL_SUFFIX)
        try {
            body.use { content ->
                content.byteStream().use { input -> partial.outputStream().use { output -> input.copyTo(output) } }
            }
            if (partial.length() > 0 && partial.renameTo(target)) return target
        } finally {
            partial.delete()
        }
        return null
    }
}
