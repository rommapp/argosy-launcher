package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import kotlinx.coroutines.flow.first
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.GameScreenshotDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.resolved
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.sync.SyncCoordinator
import com.nendo.argosy.data.sync.unsentUserProps
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMUserPropertyService"
private const val MAX_COMPLETION = 100

@Singleton
class RomMUserPropertyService @Inject constructor(
    private val apiClient: RomMApiClient,
    private val connectionManager: RomMConnectionManager,
    private val gameDao: GameDao,
    private val overlayWriter: com.nendo.argosy.data.repository.GameUserOverlayWriter,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val imageCacheManager: ImageCacheManager,
    private val syncCoordinator: dagger.Lazy<SyncCoordinator>,
    private val userPreferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository,
    private val gameFileSync: RomMGameFileSync,
    private val gameFileDao: GameFileDao,
    private val siblingGroupRepository: com.nendo.argosy.data.repository.SiblingGroupRepository,
    private val gameArtDao: GameArtDao,
    private val gameScreenshotDao: GameScreenshotDao
) {
    private val api: RomMApi? get() = connectionManager.getApi()

    suspend fun updateUserRating(gameId: Long, rating: Int): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        overlayWriter.updateUserRating(gameId, rating)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.RATING, intValue = rating)
        return RomMResult.Success(Unit)
    }

    suspend fun updateUserDifficulty(gameId: Long, difficulty: Int): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        overlayWriter.updateUserDifficulty(gameId, difficulty)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.DIFFICULTY, intValue = difficulty)
        return RomMResult.Success(Unit)
    }

    /**
     * Records the user's completion percentage, where 0 means unset. Values outside 0..100 are
     * clamped, matching the range RomM accepts for `rom_user.completion`.
     */
    suspend fun updateCompletion(gameId: Long, completion: Int): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        val value = completion.coerceIn(0, MAX_COMPLETION)
        overlayWriter.updateCompletion(gameId, value)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.COMPLETION, intValue = value)
        return RomMResult.Success(Unit)
    }

    suspend fun updateUserStatus(gameId: Long, status: String?): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        overlayWriter.updateStatus(gameId, status)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.STATUS, stringValue = status)
        return RomMResult.Success(Unit)
    }

    /**
     * The user's own hide choice, and it stays on this device: hiding tidies one launcher's shelf
     * and is never pushed, so a game hidden here stays visible on the server and everywhere else.
     * Unhiding is the exception and does travel, because it is the only way to clear a server flag
     * an older Argosy set, and a write that can only ever reveal a rom cannot lose one.
     */
    suspend fun updateHidden(gameId: Long, hidden: Boolean): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        overlayWriter.setHidden(gameId, hidden)
        siblingGroupRepository.onHiddenChanged(gameId)
        if (hidden) return RomMResult.Success(Unit)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.HIDDEN, intValue = 0)
        return RomMResult.Success(Unit)
    }

    suspend fun refreshUserProps(gameId: Long): RomMResult<Unit> {
        val currentApi = api ?: return RomMResult.Success(Unit)
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        val rommId = game.rommId ?: return RomMResult.Success(Unit)

        return try {
            val response = currentApi.getRom(rommId)
            if (!response.isSuccessful) {
                Logger.warn(TAG, "refreshUserProps: failed to fetch rom $rommId: ${response.code()}")
                return RomMResult.Success(Unit)
            }

            val rom = response.body() ?: return RomMResult.Success(Unit)
            val romUser = rom.romUser ?: return RomMResult.Success(Unit)

            val unsent = pendingSyncQueueDao.unsentUserProps(gameId, overlayWriter.activeOwnerId())

            val current = gameDao.getById(gameId) ?: return RomMResult.Success(Unit)
            if (!unsent.keepsLocal(SyncType.RATING)) overlayWriter.updateUserRating(gameId, romUser.rating)
            if (!unsent.keepsLocal(SyncType.DIFFICULTY)) overlayWriter.updateUserDifficulty(gameId, romUser.difficulty)
            if (!unsent.keepsLocal(SyncType.COMPLETION)) overlayWriter.updateCompletion(gameId, romUser.completion)
            if (!unsent.keepsLocal(SyncType.STATUS)) overlayWriter.updateStatus(gameId, romUser.status)
            if (current.backlogged != romUser.backlogged) {
                overlayWriter.updateBacklogged(gameId, romUser.backlogged)
            }
            if (current.nowPlaying != romUser.nowPlaying) {
                overlayWriter.updateNowPlaying(gameId, romUser.nowPlaying)
            }

            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.warn(TAG, "refreshUserProps: exception for game $gameId: ${e.message}")
            RomMResult.Success(Unit)
        }
    }

    suspend fun fetchUserScreenshots(rommId: Long): List<String> = withContext(Dispatchers.IO) {
        val currentApi = api ?: return@withContext emptyList()
        try {
            val response = currentApi.getRom(rommId)
            if (!response.isSuccessful) return@withContext emptyList()
            val shots = response.body()?.userScreenshots.orEmpty().filter { it.isGallery }
            shots.mapNotNull { shot ->
                val target = imageCacheManager.userScreenshotTargetFile(rommId, shot.id, shot.updatedAt ?: "")
                if (target.exists() && target.length() > 0) return@mapNotNull target.absolutePath
                val content = currentApi.downloadScreenshotContent(shot.id)
                val body = content.body()
                if (!content.isSuccessful || body == null) return@mapNotNull null
                body.byteStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.length() > 0) {
                    imageCacheManager.pruneStaleUserScreenshots(rommId, shot.id, target)
                    target.absolutePath
                } else {
                    target.delete()
                    null
                }
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "fetchUserScreenshots: exception for rommId $rommId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Records a game's files when its row claims a soundtrack but no track was ever stored.
     * The list endpoint carries no files at all, so a library sync can leave a game
     * advertising music it has no way to find; only the single-ROM response holds them.
     * Returns whether anything was fetched, so a caller can retry whatever came up empty.
     */
    suspend fun ensureSoundtrackFiles(gameId: Long): Boolean {
        val currentApi = api ?: return false
        val game = gameDao.getById(gameId) ?: return false
        if (!game.remoteHasSoundtrack) return false
        val rommId = game.rommId ?: return false
        if (gameFileDao.getFilesByCategory(gameId, VariantCategory.SOUNDTRACK.key).isNotEmpty()) return false

        return try {
            val response = currentApi.getRom(rommId)
            val rom = response.body()?.takeIf { response.isSuccessful } ?: return false
            gameFileSync.sync(gameId, rom, game.platformSlug, fileListIsAuthoritative = true)
            Logger.info(TAG, "ensureSoundtrackFiles: recorded ${rom.files?.size ?: 0} files for ${game.title}")
            true
        } catch (e: Exception) {
            Logger.warn(TAG, "ensureSoundtrackFiles: failed for ${game.title}: ${e.message}")
            false
        }
    }

    /**
     * Fetches and caches the clear logo for a game that has none, leaving the rest of the row alone.
     * Returns the stored logo path, local once cached, or null when RomM offers no logo.
     */
    suspend fun fetchLogo(gameId: Long): String? {
        val currentApi = api ?: return null
        val game = gameDao.getById(gameId) ?: return null
        gameArtDao.resolved(gameId).logoPath?.let { return it }
        val rommId = game.rommId ?: return null
        val rom = runCatching { currentApi.getRom(rommId) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body() ?: return null
        val logoUrls = apiClient.buildLogoUrls(rom)
        if (logoUrls.isEmpty()) return null
        gameArtDao.setSourceUrl(gameId, ArtSlot.LOGO, logoUrls.first())
        val cached = imageCacheManager.cacheGameImagesNow(
            rommId = rommId,
            gameTitle = rom.name,
            coverUrls = emptyList(),
            backgroundUrls = emptyList(),
            logoUrls = logoUrls
        )
        return cached.logoPath ?: logoUrls.first()
    }

    suspend fun refreshGameData(gameId: Long): RomMResult<Unit> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        val rommId = game.rommId ?: return RomMResult.Error("Not a RomM game")

        return try {
            val response = currentApi.getRom(rommId)
            if (!response.isSuccessful) {
                return RomMResult.Error("Failed to fetch ROM data", response.code())
            }

            val rom = response.body() ?: return RomMResult.Error("Empty response")

            imageCacheManager.deleteGameImages(rommId)
            imageCacheManager.forgetCachedArt(game.id)

            val screenshotUrls = rom.screenshotUrls.ifEmpty {
                rom.screenshotPaths?.mapNotNull { apiClient.buildMediaUrl(it) } ?: emptyList()
            }

            val backgroundUrls = (
                rom.backgroundUrls + listOfNotNull(screenshotUrls.getOrNull(1)) + screenshotUrls
            ).distinct()
            val coverUrls = apiClient.buildCoverUrls(rom)

            val boxArtEnabled = userPreferencesRepository.userPreferences.first().boxArtCacheEnabled
            val boxBackUrls = if (boxArtEnabled) apiClient.buildBoxBackUrls(rom) else emptyList()
            val boxSpineUrls = if (boxArtEnabled) apiClient.buildBoxSpineUrls(rom) else emptyList()
            val logoUrls = apiClient.buildLogoUrls(rom)
            val box3dUrls = apiClient.buildBox3dUrls(rom)
            gameArtDao.setSourceUrl(game.id, ArtSlot.COVER, coverUrls.firstOrNull())
            gameArtDao.setSourceUrl(game.id, ArtSlot.BACKGROUND, backgroundUrls.firstOrNull())
            gameArtDao.setSourceUrl(game.id, ArtSlot.LOGO, logoUrls.firstOrNull())
            gameArtDao.setSourceUrl(game.id, ArtSlot.BOX_3D, box3dUrls.firstOrNull())
            if (boxArtEnabled) {
                gameArtDao.setSourceUrl(game.id, ArtSlot.BOX_SPINE, boxSpineUrls.firstOrNull())
                gameArtDao.setSourceUrl(game.id, ArtSlot.BOX_BACK, boxBackUrls.firstOrNull())
            }
            gameScreenshotDao.replaceSources(game.id, screenshotUrls)

            imageCacheManager.cacheGameImagesNow(
                rommId = rom.id,
                gameTitle = rom.name,
                coverUrls = coverUrls,
                backgroundUrls = backgroundUrls,
                boxBackUrls = boxBackUrls,
                boxSpineUrls = boxSpineUrls,
                logoUrls = logoUrls,
                box3dUrls = box3dUrls
            )

            val updatedGame = game.withRomMetadata(rom).copy(
                rommFileName = rom.fileName ?: game.rommFileName
            )

            val current = gameDao.getById(game.id) ?: game
            gameDao.update(updatedGame.withCurrentUserColumns(current))
            rom.romUser?.let {
                overlayWriter.setRommMainSibling(overlayWriter.activeOwnerId(), game.id, it.isMainSibling)
            }
            gameFileSync.sync(game.id, rom, game.platformSlug, fileListIsAuthoritative = true)
            siblingGroupRepository.recomputeGroups(
                listOfNotNull(current.siblingGroupKey, updatedGame.siblingGroupKey)
            )
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to refresh game data")
        }
    }
}
