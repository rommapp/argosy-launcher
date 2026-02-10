package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.data.local.dao.CollectionDao
import java.io.File
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.OrphanedFileDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.OrphanedFileEntity
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.sync.SyncCoordinator
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_PAGE_SIZE = 100
private const val TAG = "RomMRepository"
private const val FAVORITES_CHECK_DEBOUNCE_SECONDS = 30L
private const val FAVORITES_COLLECTION_NAME = "Favorites"

@Singleton
class RomMRepository @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val platformDao: PlatformDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val orphanedFileDao: OrphanedFileDao,
    private val collectionDao: CollectionDao,
    private val imageCacheManager: ImageCacheManager,
    private val saveSyncRepository: dagger.Lazy<com.nendo.argosy.data.repository.SaveSyncRepository>,
    private val gameRepository: dagger.Lazy<com.nendo.argosy.data.repository.GameRepository>,
    private val biosRepository: BiosRepository,
    private val syncVirtualCollectionsUseCase: dagger.Lazy<com.nendo.argosy.domain.usecase.collection.SyncVirtualCollectionsUseCase>,
    private val syncCoordinator: dagger.Lazy<SyncCoordinator>
) {
    private var api: RomMApi? = null
    private var baseUrl: String = ""
    private var accessToken: String? = null
    private val syncMutex = Mutex()
    private var raProgressionRefreshedThisSession = false

    private var cachedRAProgression: Map<Long, List<RomMEarnedAchievement>> = emptyMap()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data class Connected(val version: String) : ConnectionState()
        data class Failed(val reason: String) : ConnectionState()
    }

    private fun getConnectedVersion(): String? {
        return (_connectionState.value as? ConnectionState.Connected)?.version
    }

    private fun isVersionAtLeast(minVersion: String): Boolean {
        val current = getConnectedVersion() ?: return false
        return compareVersions(current, minVersion) >= 0
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    private fun buildRomsQueryParams(
        platformId: Long? = null,
        searchTerm: String? = null,
        orderBy: String = "name",
        orderDir: String = "asc",
        limit: Int = 100,
        offset: Int = 0
    ): Map<String, String> {
        val usePluralizedParams = isVersionAtLeast("4.6.0")
        val platformKey = if (usePluralizedParams) "platform_ids" else "platform_id"

        return buildMap {
            platformId?.let { put(platformKey, it.toString()) }
            searchTerm?.let { put("search_term", it) }
            put("order_by", orderBy)
            put("order_dir", orderDir)
            put("limit", limit.toString())
            put("offset", offset.toString())
        }
    }

    suspend fun initialize() {
        val prefs = userPreferencesRepository.preferences.first()
        Logger.info(TAG, "initialize: baseUrl=${prefs.rommBaseUrl?.take(30)}, hasToken=${prefs.rommToken != null}")
        if (!prefs.rommBaseUrl.isNullOrBlank()) {
            val result = connect(prefs.rommBaseUrl, prefs.rommToken)
            Logger.info(TAG, "initialize: connect result=$result, state=${_connectionState.value}")
            if (result is RomMResult.Success && prefs.rommToken != null) {
                refreshRAProgressionOnStartup()
            }
        }
        populateVirtualCollectionsIfNeeded()
    }

    private suspend fun populateVirtualCollectionsIfNeeded() {
        val genreCount = collectionDao.countByType(CollectionType.GENRE)
        val gameModeCount = collectionDao.countByType(CollectionType.GAME_MODE)

        if (genreCount == 0 && gameModeCount == 0) {
            val hasGenres = gameDao.getDistinctGenres().isNotEmpty()
            val hasGameModes = gameDao.getDistinctGameModes().isNotEmpty()

            if (hasGenres || hasGameModes) {
                Logger.info(TAG, "Populating virtual collections for existing games")
                syncVirtualCollectionsUseCase.get()()
            }
        }
    }

    private var shouldForceRefreshOnNextInit = false

    fun onAppResumed() {
        raProgressionRefreshedThisSession = false
        cachedRAProgression = emptyMap()
        shouldForceRefreshOnNextInit = true
    }

    private suspend fun refreshRAProgressionOnStartup() {
        val currentApi = api ?: return
        try {
            val userResponse = currentApi.getCurrentUser()
            if (!userResponse.isSuccessful) return

            val user = userResponse.body() ?: return
            if (user.raUsername.isNullOrBlank()) return

            var progression = user.raProgression?.results ?: emptyList()
            shouldForceRefreshOnNextInit = false

            val refreshResponse = currentApi.refreshRAProgression(user.id)
            if (refreshResponse.isSuccessful) {
                raProgressionRefreshedThisSession = true
                val refreshedUserResponse = currentApi.getCurrentUser()
                if (refreshedUserResponse.isSuccessful) {
                    progression = refreshedUserResponse.body()?.raProgression?.results ?: emptyList()
                }
            } else {
                raProgressionRefreshedThisSession = true
            }

            cachedRAProgression = progression
                .filter { it.romRaId != null }
                .associate { gameProgress ->
                    gameProgress.romRaId!! to gameProgress.earnedAchievements
                }
        } catch (_: Exception) {
        }
    }

    fun getEarnedBadgeIds(raGameId: Long): Set<String> {
        return cachedRAProgression[raGameId]?.map { it.id }?.toSet() ?: emptySet()
    }

    fun getEarnedAchievements(raGameId: Long): List<RomMEarnedAchievement> {
        return cachedRAProgression[raGameId] ?: emptyList()
    }

    suspend fun connect(url: String, token: String? = null): RomMResult<String> {
        _connectionState.value = ConnectionState.Connecting

        val urlsToTry = buildUrlsToTry(url)
        var lastError: String? = null

        for (candidateUrl in urlsToTry) {
            val normalizedUrl = candidateUrl.trimEnd('/') + "/"
            try {
                val newApi = createApi(normalizedUrl, token)
                val response = newApi.heartbeat()

                if (response.isSuccessful) {
                    baseUrl = normalizedUrl
                    accessToken = token
                    api = newApi
                    saveSyncRepository.get().setApi(api)
                    biosRepository.setApi(api)
                    val version = response.body()?.version ?: "unknown"
                    _connectionState.value = ConnectionState.Connected(version)
                    Logger.info(TAG, "connect: success at $normalizedUrl, version=$version")
                    return RomMResult.Success(normalizedUrl)
                } else {
                    lastError = "Server returned ${response.code()}"
                    Logger.info(TAG, "connect: heartbeat failed at $normalizedUrl with ${response.code()}")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
                Logger.info(TAG, "connect: exception at $normalizedUrl: ${e.message}")
            }
        }

        _connectionState.value = ConnectionState.Failed(lastError ?: "Connection failed")
        return RomMResult.Error(lastError ?: "Connection failed")
    }

    private fun buildUrlsToTry(url: String): List<String> {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return listOf(trimmed)
        }

        val hostPart = trimmed.removePrefix("//")
        val isIpAddress = hostPart.split("/").first().split(":").first().let { host ->
            host.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) ||
                host == "localhost"
        }

        return if (isIpAddress) {
            listOf("http://$hostPart", "https://$hostPart")
        } else {
            listOf("https://$hostPart", "http://$hostPart")
        }
    }

    suspend fun login(username: String, password: String): RomMResult<String> {
        val currentApi = api ?: return RomMResult.Error("Not connected")

        return try {
            val scope = "me.read me.write platforms.read roms.read assets.read assets.write roms.user.read roms.user.write collections.read collections.write firmware.read"
            val response = currentApi.login(username, password, scope)
            if (response.isSuccessful) {
                val token = response.body()?.accessToken
                    ?: return RomMResult.Error("No token received")

                accessToken = token
                api = createApi(baseUrl, token)
                saveSyncRepository.get().setApi(api)
                biosRepository.setApi(api)

                userPreferencesRepository.setRomMCredentials(baseUrl, token, username)
                RomMResult.Success(token)
            } else {
                RomMResult.Error("Login failed", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Login failed")
        }
    }

    suspend fun syncLibrary(
        onProgress: ((current: Int, total: Int, platformName: String) -> Unit)? = null
    ): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext SyncResult(0, 0, 0, 0, listOf("Sync already in progress"))
        }

        try {
            return@withContext doSyncLibrary(onProgress)
        } finally {
            syncMutex.unlock()
        }
    }

    suspend fun syncPlatform(platformId: Long): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext SyncResult(0, 0, 0, 0, listOf("Sync already in progress"))
        }

        try {
            return@withContext doSyncPlatform(platformId)
        } finally {
            syncMutex.unlock()
        }
    }

    suspend fun syncPlatformsOnly(): Result<Int> = withContext(Dispatchers.IO) {
        val currentApi = api ?: return@withContext Result.failure(Exception("Not connected to server"))
        try {
            val response = currentApi.getPlatforms()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch platforms: ${response.code()}"))
            }
            val platforms = response.body() ?: emptyList()
            for (platform in platforms) {
                syncPlatformMetadata(platform)
            }
            Result.success(platforms.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun doSyncPlatform(platformId: Long): SyncResult {
        val currentApi = api ?: return SyncResult(0, 0, 0, 0, listOf("Not connected"))

        platformDao.getById(platformId)
            ?: return SyncResult(0, 0, 0, 0, listOf("Platform not found locally"))

        val prefs = userPreferencesRepository.preferences.first()
        val filters = prefs.syncFilters

        _syncProgress.value = SyncProgress(isSyncing = true, platformsTotal = 1)

        try {
            val platformResponse = currentApi.getPlatform(platformId)
            if (!platformResponse.isSuccessful) {
                return SyncResult(0, 0, 0, 0, listOf("Failed to fetch platform: ${platformResponse.code()}"))
            }

            val platform = platformResponse.body()
                ?: return SyncResult(0, 0, 0, 0, listOf("Platform not found"))

            syncPlatformMetadata(platform)

            _syncProgress.value = _syncProgress.value.copy(currentPlatform = platform.name)

            val result = syncPlatformRoms(currentApi, platform, filters)

            consolidateMultiDiscGames(currentApi, result.multiDiscGroups)

            var gamesDeleted = 0
            if (filters.deleteOrphans) {
                gamesDeleted = deleteOrphanedGamesForPlatform(platform.id, result.seenIds)
            }

            val count = gameDao.countByPlatform(platform.id)
            platformDao.updateGameCount(platform.id, count)

            syncVirtualCollectionsUseCase.get()()

            return SyncResult(1, result.added, result.updated, gamesDeleted, result.error?.let { listOf(it) } ?: emptyList())
        } catch (e: Exception) {
            return SyncResult(0, 0, 0, 0, listOf(e.message ?: "Platform sync failed"))
        } finally {
            _syncProgress.value = SyncProgress(isSyncing = false)
        }
    }

    private suspend fun deleteOrphanedGamesForPlatform(platformId: Long, seenRommIds: Set<Long>): Int {
        var deleted = 0

        val remoteGames = gameDao.getBySource(GameSource.ROMM_REMOTE).filter { it.platformId == platformId }
        for (game in remoteGames) {
            val rommId = game.rommId ?: continue
            if (rommId !in seenRommIds) {
                gameDao.delete(game.id)
                deleted++
            }
        }

        val syncedGames = gameDao.getBySource(GameSource.ROMM_SYNCED).filter { it.platformId == platformId }
        for (game in syncedGames) {
            val rommId = game.rommId ?: continue
            if (rommId !in seenRommIds) {
                game.localPath?.let { safeDeleteFile(it) }
                gameDao.delete(game.id)
                deleted++
            }
        }

        return deleted
    }

    private suspend fun doSyncLibrary(
        onProgress: ((current: Int, total: Int, platformName: String) -> Unit)?
    ): SyncResult {
        val currentApi = api ?: return SyncResult(0, 0, 0, 0, listOf("Not connected"))
        val errors = mutableListOf<String>()
        var platformsSynced = 0
        var gamesAdded = 0
        var gamesUpdated = 0
        var gamesDeleted = 0

        val prefs = userPreferencesRepository.preferences.first()
        val filters = prefs.syncFilters
        val seenRommIds = mutableSetOf<Long>()
        val allMultiDiscGroups = mutableListOf<MultiDiscGroup>()

        _syncProgress.value = SyncProgress(isSyncing = true)

        try {
            val platformsResponse = currentApi.getPlatforms()

            if (!platformsResponse.isSuccessful) {
                val errorMsg = when (platformsResponse.code()) {
                    401, 403 -> "Authentication failed - token may be invalid or missing permissions"
                    else -> "Failed to fetch platforms: ${platformsResponse.code()}"
                }
                return SyncResult(0, 0, 0, 0, listOf(errorMsg))
            }

            val platforms = platformsResponse.body()
            if (platforms.isNullOrEmpty()) {
                return SyncResult(0, 0, 0, 0, listOf("No platforms returned from server"))
            }

            for (platform in platforms) {
                syncPlatformMetadata(platform)
            }

            val enabledPlatforms = platforms.filter { platform ->
                val local = platformDao.getById(platform.id)
                local?.syncEnabled != false
            }

            _syncProgress.value = _syncProgress.value.copy(platformsTotal = enabledPlatforms.size)

            for ((index, platform) in enabledPlatforms.withIndex()) {
                onProgress?.invoke(index + 1, enabledPlatforms.size, platform.name)

                _syncProgress.value = _syncProgress.value.copy(
                    currentPlatform = platform.name,
                    platformsDone = index
                )

                val result = syncPlatformRoms(currentApi, platform, filters)
                gamesAdded += result.added
                gamesUpdated += result.updated
                seenRommIds.addAll(result.seenIds)
                allMultiDiscGroups.addAll(result.multiDiscGroups)
                result.error?.let { errors.add(it) }

                platformsSynced++
            }

            consolidateMultiDiscGames(currentApi, allMultiDiscGroups)

            gamesDeleted += cleanupInvalidExtensionGames()
            gamesDeleted += cleanupDuplicateGames()
            cleanupLegacyPlatforms(platforms)

            platforms.forEach { platform ->
                val count = gameDao.countByPlatform(platform.id)
                platformDao.updateGameCount(platform.id, count)
            }

            if (filters.deleteOrphans) {
                gamesDeleted += deleteOrphanedGames(seenRommIds)
            }

            userPreferencesRepository.setLastRommSyncTime(Instant.now())

            syncVirtualCollectionsUseCase.get()()

        } catch (e: Exception) {
            errors.add(e.message ?: "Sync failed")
        } finally {
            _syncProgress.value = SyncProgress(isSyncing = false)
        }

        gameRepository.get().cleanupEmptyNumericFolders()

        return SyncResult(platformsSynced, gamesAdded, gamesUpdated, gamesDeleted, errors)
    }

    private suspend fun syncPlatformMetadata(remote: RomMPlatform) {
        val platformId = remote.id
        val existing = platformDao.getById(platformId)
        val platformDef = PlatformDefinitions.getBySlug(remote.slug)

        val logoUrl = remote.logoUrl?.let { buildMediaUrl(it) }
        val normalizedName = remote.displayName ?: remote.name
        val entity = PlatformEntity(
            id = platformId,
            slug = remote.slug,
            fsSlug = remote.fsSlug,
            name = normalizedName,
            shortName = platformDef?.shortName ?: normalizedName,
            romExtensions = platformDef?.extensions?.joinToString(",") ?: "",
            gameCount = remote.romCount,
            isVisible = existing?.isVisible ?: true,
            logoPath = logoUrl ?: existing?.logoPath,
            sortOrder = platformDef?.sortOrder ?: existing?.sortOrder ?: 999,
            lastScanned = existing?.lastScanned,
            syncEnabled = existing?.syncEnabled ?: true,
            customRomPath = existing?.customRomPath
        )

        if (existing == null) {
            platformDao.insert(entity)
        } else {
            platformDao.update(entity)
        }

        if (logoUrl != null && logoUrl.startsWith("http")) {
            imageCacheManager.queuePlatformLogoCache(platformId, logoUrl)
        }

        remote.firmware?.let { firmware ->
            if (firmware.isNotEmpty()) {
                biosRepository.syncPlatformFirmware(platformId, remote.slug, firmware)
            }
        }
    }

    private suspend fun syncRom(rom: RomMRom, platformId: Long, platformSlug: String): Pair<Boolean, GameEntity> {
        val existing = gameDao.getByRommId(rom.id)

        val migrationSources = if (existing == null && rom.igdbId != null) {
            gameDao.getAllByIgdbIdAndPlatform(rom.igdbId, platformId)
                .filter { it.rommId != null && it.rommId != rom.id }
        } else emptyList()

        if (migrationSources.isNotEmpty()) {
            Logger.info(TAG, "syncRom: detected migration for ${rom.name} (igdbId=${rom.igdbId}): ${migrationSources.size} old entries -> new rommId=${rom.id}")
        }

        val validatedExisting = existing?.let { game ->
            val path = game.localPath
            if (path != null && !File(path).exists()) {
                Logger.warn(TAG, "syncRom: existing localPath no longer exists: $path, clearing for ${rom.name}")
                game.copy(localPath = null)
            } else {
                game
            }
        }

        val localDataSource = validatedExisting ?: GameMigrationHelper.aggregateMultiDiscData(migrationSources) { path ->
            val exists = File(path).exists()
            if (!exists) {
                Logger.warn(TAG, "syncRom: migrated localPath no longer exists: $path")
            }
            exists
        }

        val screenshotUrls = rom.screenshotUrls.ifEmpty {
            rom.screenshotPaths?.map { buildMediaUrl(it) } ?: emptyList()
        }

        val contentChanged = existing != null && existing.title != rom.name
        if (contentChanged) {
            imageCacheManager.deleteGameImages(rom.id)
        }

        val backgroundUrl = rom.backgroundUrls.firstOrNull()
        val cachedBackground = when {
            !contentChanged && existing?.backgroundPath?.startsWith("/") == true -> existing.backgroundPath
            backgroundUrl != null -> {
                imageCacheManager.queueBackgroundCache(backgroundUrl, rom.id, rom.name)
                backgroundUrl
            }
            else -> null
        }

        val coverUrl = rom.coverLarge?.let { buildMediaUrl(it) }
        val cachedCover = when {
            !contentChanged && existing?.coverPath?.startsWith("/") == true -> existing.coverPath
            coverUrl != null -> {
                imageCacheManager.queueCoverCache(coverUrl, rom.id, rom.name)
                coverUrl
            }
            else -> null
        }

        // Determine if game should be multi-disc
        // For sibling-based multi-disc: hasDiscSiblings is true and NOT isFolderMultiDisc
        // For folder-based: download as single ROM, detect multi-disc after extraction
        val isSiblingBasedMultiDisc = rom.hasDiscSiblings && !rom.isFolderMultiDisc
        val shouldBeMultiDisc = isSiblingBasedMultiDisc

        // If game was multi-disc but RomM no longer returns siblings, reset it
        // But preserve isMultiDisc if game was detected as multi-disc from extraction
        if (existing?.isMultiDisc == true && !shouldBeMultiDisc && !rom.isFolderMultiDisc) {
            // Only reset if it was sibling-based (had disc entries with no parentRommId)
            val existingDiscs = gameDiscDao.getDiscsForGame(existing.id)
            val wasSiblingBased = existingDiscs.any { it.parentRommId == null }
            if (wasSiblingBased) {
                gameDiscDao.deleteByGameId(existing.id)
            }
        }

        Logger.debug(TAG, "syncRom: ${rom.name} - rom.raId=${rom.raId}, existing.raId=${existing?.raId}")

        val game = GameEntity(
            id = existing?.id ?: 0,
            platformId = platformId,
            platformSlug = platformSlug,
            title = rom.name,
            sortTitle = RomMUtils.createSortTitle(rom.name),
            localPath = localDataSource?.localPath,
            rommId = rom.id,
            rommFileName = rom.fileName,
            igdbId = rom.igdbId,
            raId = rom.raId,
            source = if (localDataSource?.localPath != null) GameSource.ROMM_SYNCED else GameSource.ROMM_REMOTE,
            coverPath = cachedCover,
            backgroundPath = cachedBackground,
            screenshotPaths = screenshotUrls.joinToString(","),
            description = rom.summary,
            releaseYear = rom.firstReleaseDateMillis?.let {
                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).year
            },
            genre = rom.genres?.firstOrNull(),
            developer = rom.companies?.firstOrNull(),
            rating = rom.metadatum?.averageRating,
            regions = rom.regions?.joinToString(","),
            languages = rom.languages?.joinToString(","),
            gameModes = rom.metadatum?.gameModes?.joinToString(","),
            franchises = rom.metadatum?.franchises?.joinToString(","),
            userRating = rom.romUser?.rating ?: localDataSource?.userRating ?: 0,
            userDifficulty = rom.romUser?.difficulty ?: localDataSource?.userDifficulty ?: 0,
            completion = rom.romUser?.completion ?: localDataSource?.completion ?: 0,
            status = rom.romUser?.status ?: localDataSource?.status,
            backlogged = rom.romUser?.backlogged ?: localDataSource?.backlogged ?: false,
            nowPlaying = rom.romUser?.nowPlaying ?: localDataSource?.nowPlaying ?: false,
            isFavorite = localDataSource?.isFavorite ?: false,
            isHidden = localDataSource?.isHidden ?: false,
            isMultiDisc = when {
                rom.isFolderMultiDisc -> localDataSource?.isMultiDisc == true && localDataSource.localPath != null
                shouldBeMultiDisc -> localDataSource?.isMultiDisc ?: false
                else -> false
            },
            playCount = localDataSource?.playCount ?: 0,
            playTimeMinutes = localDataSource?.playTimeMinutes ?: 0,
            lastPlayed = localDataSource?.lastPlayed,
            addedAt = localDataSource?.addedAt ?: java.time.Instant.now(),
            achievementCount = rom.raMetadata?.achievements?.size ?: localDataSource?.achievementCount ?: 0,
            youtubeVideoId = rom.youtubeVideoId,
            fileSizeBytes = rom.files
                ?.filter { it.category == null && !it.fileName.startsWith(".") }
                ?.maxByOrNull { it.fileSizeBytes }
                ?.fileSizeBytes
                ?: rom.fileSize.takeIf { it > 0 }
        )

        val isNew = existing == null
        gameDao.insert(game)

        if (migrationSources.isNotEmpty()) {
            migrationSources.forEach { source ->
                gameDao.delete(source.id)
            }
            Logger.info(TAG, "syncRom: deleted ${migrationSources.size} old game entries after migration")
        }

        val savedGame = gameDao.getByRommId(rom.id)
        if (savedGame != null) {
            syncGameFiles(savedGame.id, rom)
        }

        return isNew to game
    }

    private suspend fun syncGameFiles(gameId: Long, rom: RomMRom) {
        val files = rom.files?.filter { file ->
            file.category in setOf("update", "dlc") &&
            !file.fileName.startsWith(".")
        } ?: return

        if (files.isEmpty()) {
            gameFileDao.deleteByGameId(gameId)
            return
        }

        gameFileDao.deleteInvalidFiles(gameId, files.map { it.id })

        val entities = files.map { file ->
            val existing = gameFileDao.getByRommFileId(file.id)
            GameFileEntity(
                id = existing?.id ?: 0,
                gameId = gameId,
                rommFileId = file.id,
                romId = file.romId,
                fileName = file.fileName,
                filePath = file.filePath,
                category = file.category ?: "update",
                fileSize = file.fileSizeBytes,
                localPath = existing?.localPath,
                downloadedAt = existing?.downloadedAt
            )
        }
        gameFileDao.insertAll(entities)
    }

    private fun buildMediaUrl(path: String): String {
        return if (path.startsWith("http")) path else "$baseUrl$path"
    }

    fun buildMediaUrlPublic(path: String): String = buildMediaUrl(path)

    private data class PlatformSyncResult(
        val added: Int,
        val updated: Int,
        val seenIds: Set<Long>,
        val multiDiscGroups: List<MultiDiscGroup>,
        val error: String? = null
    )

    private suspend fun syncPlatformRoms(
        api: RomMApi,
        platform: RomMPlatform,
        filters: SyncFilterPreferences
    ): PlatformSyncResult {
        var added = 0
        var updated = 0
        val seenRommIds = mutableSetOf<Long>()
        val seenDedupKeys = mutableMapOf<String, Long>()
        val romsWithRA = mutableSetOf<Long>()
        val multiDiscGroups = mutableListOf<MultiDiscGroup>()
        val processedDiscIds = mutableSetOf<Long>()
        val skipIndividualDiscIds = mutableSetOf<Long>()
        var offset = 0
        var totalFetched = 0

        while (true) {
            val romsResponse = api.getRoms(
                buildRomsQueryParams(
                    platformId = platform.id,
                    limit = SYNC_PAGE_SIZE,
                    offset = offset
                )
            )

            if (!romsResponse.isSuccessful) {
                return PlatformSyncResult(added, updated, seenRommIds, multiDiscGroups,
                    "Failed to fetch ROMs for ${platform.name}: ${romsResponse.code()}")
            }

            val romsPage = romsResponse.body()
            if (romsPage == null || romsPage.items.isEmpty()) break

            totalFetched += romsPage.items.size
            _syncProgress.value = _syncProgress.value.copy(
                gamesTotal = romsPage.total,
                gamesDone = totalFetched
            )

            for (rom in romsPage.items) {
                if (!RomMSyncFilter.shouldSyncRom(rom, filters)) continue

                // Skip individual disc ROMs when a folder-based multi-disc version exists
                if (rom.id in skipIndividualDiscIds) {
                    Logger.debug(TAG, "syncPlatformRoms: skipping individual disc ${rom.name} - folder-based version preferred")
                    continue
                }

                // When we find a folder-based multi-disc ROM, mark its individual disc siblings to skip
                if (rom.isFolderMultiDisc) {
                    val discSiblings = rom.siblings?.filter { it.isDiscVariant } ?: emptyList()
                    if (discSiblings.isNotEmpty()) {
                        val siblingIds = discSiblings.map { it.id }
                        skipIndividualDiscIds.addAll(siblingIds)
                        Logger.info(TAG, "syncPlatformRoms: ${rom.name} is folder-based multi-disc, marking ${siblingIds.size} individual disc siblings to skip")
                        for (siblingId in siblingIds) {
                            val existingGame = gameDao.getByRommId(siblingId)
                            if (existingGame != null) {
                                Logger.info(TAG, "syncPlatformRoms: deleting redundant individual disc game: ${existingGame.title}")
                                gameDao.delete(existingGame.id)
                            }
                        }
                    }
                }

                val dedupKey = RomMUtils.getDedupKey(rom)
                val hasRA = rom.raId != null || rom.raMetadata?.achievements?.isNotEmpty() == true

                if (dedupKey != null) {
                    val existingRomId = seenDedupKeys[dedupKey]
                    if (existingRomId != null) {
                        val existingHasRA = romsWithRA.contains(existingRomId)
                        if (!existingHasRA && hasRA) {
                            seenRommIds.remove(existingRomId)
                            romsWithRA.remove(existingRomId)
                            seenDedupKeys[dedupKey] = rom.id
                            seenRommIds.add(rom.id)
                            if (hasRA) romsWithRA.add(rom.id)
                            try {
                                syncRom(rom, platform.id, platform.slug)
                                updated++
                            } catch (_: Exception) {}
                        }
                        continue
                    }
                    seenDedupKeys[dedupKey] = rom.id
                }

                seenRommIds.add(rom.id)
                if (hasRA) romsWithRA.add(rom.id)

                try {
                    val (isNew, _) = syncRom(rom, platform.id, platform.slug)
                    if (isNew) added++ else updated++

                    // Only add sibling-based multi-disc to consolidation groups
                    // Folder-based multi-disc downloads as single ZIP, handled by extraction
                    val isSiblingBasedMultiDisc = rom.hasDiscSiblings && !rom.isFolderMultiDisc
                    if (isSiblingBasedMultiDisc && rom.id !in processedDiscIds) {
                        val discSiblings = rom.siblings?.filter { it.isDiscVariant } ?: emptyList()
                        val siblingIds = discSiblings.map { it.id }

                        processedDiscIds.add(rom.id)
                        processedDiscIds.addAll(siblingIds)
                        seenRommIds.addAll(siblingIds)

                        multiDiscGroups.add(MultiDiscGroup(
                            primaryRommId = rom.id,
                            siblingRommIds = siblingIds,
                            platformSlug = platform.slug
                        ))
                    }
                } catch (e: Exception) {
                    Logger.warn(TAG, "syncPlatformRoms: failed to sync ROM ${rom.id} (${rom.name}): ${e.message}")
                }
            }

            if (totalFetched >= romsPage.total) break
            offset += SYNC_PAGE_SIZE
        }

        return PlatformSyncResult(added, updated, seenRommIds, multiDiscGroups)
    }

    private suspend fun consolidateMultiDiscGames(
        api: RomMApi,
        groups: List<MultiDiscGroup>
    ) {
        for (group in groups) {
            try {
                consolidateMultiDiscGroup(api, group)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun consolidateMultiDiscGroup(
        api: RomMApi,
        group: MultiDiscGroup
    ) {
        val allRommIds = listOf(group.primaryRommId) + group.siblingRommIds
        val existingGames = allRommIds.mapNotNull { rommId ->
            gameDao.getByRommId(rommId)
        }.distinctBy { it.id }

        if (existingGames.isEmpty()) return

        val primaryGame = existingGames.find { it.isMultiDisc }
            ?: existingGames.minByOrNull { game ->
                allRommIds.indexOf(game.rommId).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
            ?: existingGames.first()

        val redundantGames = existingGames.filter { it.id != primaryGame.id }

        if (primaryGame.isMultiDisc && redundantGames.isEmpty()) {
            // Still clean up any invalid disc entries before returning
            gameDiscDao.deleteInvalidDiscs(primaryGame.id, allRommIds)
            return
        }

        val mergedIsFavorite = existingGames.any { it.isFavorite }
        val mergedPlayCount = existingGames.sumOf { it.playCount }
        val mergedPlayTime = existingGames.sumOf { it.playTimeMinutes }
        val mergedLastPlayed = existingGames.mapNotNull { it.lastPlayed }.maxOrNull()
        val mergedUserRating = existingGames.maxOf { it.userRating }
        val mergedUserDifficulty = existingGames.maxOf { it.userDifficulty }
        val mergedCompletion = existingGames.maxOf { it.completion }
        val mergedBacklogged = existingGames.any { it.backlogged }
        val mergedNowPlaying = existingGames.any { it.nowPlaying }
        val earliestAddedAt = existingGames.minOf { it.addedAt }

        val updatedGame = primaryGame.copy(
            isFavorite = mergedIsFavorite,
            playCount = mergedPlayCount,
            playTimeMinutes = mergedPlayTime,
            lastPlayed = mergedLastPlayed ?: primaryGame.lastPlayed,
            userRating = mergedUserRating,
            userDifficulty = mergedUserDifficulty,
            completion = mergedCompletion,
            backlogged = mergedBacklogged,
            nowPlaying = mergedNowPlaying,
            addedAt = earliestAddedAt,
            isMultiDisc = true
        )

        gameDao.update(updatedGame)

        val localPathsByRommId = existingGames
            .filter { it.localPath != null && it.rommId != null }
            .associate { it.rommId!! to it.localPath!! }

        val existingDiscs = gameDiscDao.getDiscsForGame(primaryGame.id)
        val existingDiscRommIds = existingDiscs.map { it.rommId }.toSet()

        val discsToInsert = mutableListOf<GameDiscEntity>()

        for (rommId in allRommIds) {
            if (rommId in existingDiscRommIds) continue

            val existingDisc = gameDiscDao.getByRommId(rommId)
            val localPath = localPathsByRommId[rommId] ?: existingDisc?.localPath

            val romData = try {
                val response = api.getRom(rommId)
                if (response.isSuccessful) response.body() else null
            } catch (e: Exception) {
                Logger.warn(TAG, "consolidateMultiDiscGroup: failed to fetch ROM $rommId: ${e.message}")
                null
            }

            // Skip creating new disc entries without proper data - no disc better than broken record
            if (romData == null && existingDisc == null) {
                Logger.warn(TAG, "consolidateMultiDiscGroup: skipping disc $rommId - no data available")
                continue
            }

            discsToInsert.add(GameDiscEntity(
                id = existingDisc?.id ?: 0,
                gameId = primaryGame.id,
                discNumber = romData?.discNumber ?: existingDisc?.discNumber ?: (discsToInsert.size + existingDiscs.size + 1),
                rommId = rommId,
                fileName = romData?.fileName ?: existingDisc?.fileName ?: "Disc",
                localPath = localPath,
                fileSize = romData?.fileSize ?: existingDisc?.fileSize ?: 0
            ))
        }

        if (discsToInsert.isNotEmpty()) {
            gameDiscDao.insertAll(discsToInsert)
        }

        // Remove any disc entries that don't belong to this multi-disc group
        gameDiscDao.deleteInvalidDiscs(primaryGame.id, allRommIds)

        for (redundantGame in redundantGames) {
            gameDao.delete(redundantGame.id)
        }
    }

    private suspend fun cleanupInvalidExtensionGames(): Int {
        var deleted = 0
        val allGames = gameDao.getBySource(GameSource.ROMM_REMOTE) + gameDao.getBySource(GameSource.ROMM_SYNCED)

        for (game in allGames) {
            val localPath = game.localPath ?: continue
            val extension = localPath.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) continue

            val platformDef = PlatformDefinitions.getBySlug(game.platformSlug) ?: continue
            if (platformDef.extensions.isEmpty()) continue

            if (extension !in platformDef.extensions) {
                safeDeleteFile(localPath)
                gameDao.delete(game.id)
                deleted++
            }
        }
        return deleted
    }

    private suspend fun cleanupDuplicateGames(): Int {
        var deleted = 0
        val allGames = gameDao.getBySource(GameSource.ROMM_REMOTE) + gameDao.getBySource(GameSource.ROMM_SYNCED)
        val deletedIds = mutableSetOf<Long>()

        val gamesByPlatformAndIgdb = allGames
            .filter { it.igdbId != null }
            .groupBy { "${it.platformId}:${it.igdbId}" }

        for ((_, duplicates) in gamesByPlatformAndIgdb) {
            if (duplicates.size <= 1) continue

            val sorted = duplicates.sortedWith(
                compareByDescending<GameEntity> { it.achievementCount > 0 }
                    .thenByDescending { it.localPath != null }
                    .thenBy { it.id }
            )

            for (game in sorted.drop(1)) {
                game.localPath?.let { safeDeleteFile(it) }
                gameDao.delete(game.id)
                deletedIds.add(game.id)
                deleted++
            }
        }

        val remainingGames = allGames.filter { it.id !in deletedIds }
        val gamesByPlatformAndTitle = remainingGames
            .groupBy { "${it.platformId}:${it.title.lowercase()}" }

        for ((_, duplicates) in gamesByPlatformAndTitle) {
            if (duplicates.size <= 1) continue

            val sorted = duplicates.sortedWith(
                compareByDescending<GameEntity> { it.achievementCount > 0 }
                    .thenByDescending { it.localPath != null }
                    .thenBy { it.id }
            )

            for (game in sorted.drop(1)) {
                game.localPath?.let { safeDeleteFile(it) }
                gameDao.delete(game.id)
                deleted++
            }
        }

        return deleted
    }

    private suspend fun cleanupLegacyPlatforms(remotePlatforms: List<RomMPlatform>) {
        val remoteIds = remotePlatforms.map { it.id }.toSet()
        val remoteByKey = remotePlatforms.associateBy { it.slug to it.fsSlug }
        val remoteBySlug = remotePlatforms.groupBy { it.slug }
        val allLocal = platformDao.getAllPlatforms()

        for (local in allLocal) {
            if (local.id in remoteIds) continue

            val matchingRemote = remoteByKey[local.slug to local.fsSlug]
                ?: if (local.fsSlug == null) remoteBySlug[local.slug]?.firstOrNull() else null

            if (matchingRemote != null) {
                gameDao.migratePlatform(local.id, matchingRemote.id)
                platformDao.deleteById(local.id)
                Logger.info(TAG, "Migrated legacy platform ${local.id} -> ${matchingRemote.id}")
            }
        }
    }

    private suspend fun deleteOrphanedGames(seenRommIds: Set<Long>): Int {
        var deleted = 0

        val disabledPlatforms = platformDao.observeAllPlatforms().first()
            .filter { !it.syncEnabled }
            .map { it.id }
            .toSet()

        val remoteGames = gameDao.getBySource(GameSource.ROMM_REMOTE)
        for (game in remoteGames) {
            if (game.platformId in disabledPlatforms) continue
            val rommId = game.rommId ?: continue
            if (rommId !in seenRommIds) {
                gameDao.delete(game.id)
                deleted++
            }
        }

        val syncedGames = gameDao.getBySource(GameSource.ROMM_SYNCED)
        for (game in syncedGames) {
            if (game.platformId in disabledPlatforms) continue
            val rommId = game.rommId ?: continue
            if (rommId !in seenRommIds) {
                game.localPath?.let { safeDeleteFile(it) }
                gameDao.delete(game.id)
                deleted++
            }
        }

        return deleted
    }

    suspend fun getRom(romId: Long): RomMResult<RomMRom> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getRom(romId)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return RomMResult.Error("Empty response from server")
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch ROM", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch ROM")
        }
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

            val hasRating = pendingSyncQueueDao.hasPending(gameId, SyncType.RATING)
            val hasDifficulty = pendingSyncQueueDao.hasPending(gameId, SyncType.DIFFICULTY)
            val hasStatus = pendingSyncQueueDao.hasPending(gameId, SyncType.STATUS)

            val updatedGame = game.copy(
                userRating = if (hasRating) game.userRating else romUser.rating,
                userDifficulty = if (hasDifficulty) game.userDifficulty else romUser.difficulty,
                status = if (hasStatus) game.status else romUser.status,
                backlogged = romUser.backlogged,
                nowPlaying = romUser.nowPlaying
            )

            if (updatedGame != game) {
                gameDao.update(updatedGame)
            }

            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.warn(TAG, "refreshUserProps: exception for game $gameId: ${e.message}")
            RomMResult.Success(Unit)
        }
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

            val screenshotUrls = rom.screenshotUrls.ifEmpty {
                rom.screenshotPaths?.map { buildMediaUrl(it) } ?: emptyList()
            }

            val backgroundUrl = rom.backgroundUrls.firstOrNull()
            val coverUrl = rom.coverLarge?.let { buildMediaUrl(it) }

            if (backgroundUrl != null) {
                imageCacheManager.queueBackgroundCache(backgroundUrl, rom.id, rom.name)
            }
            if (coverUrl != null) {
                imageCacheManager.queueCoverCache(coverUrl, rom.id, rom.name)
            }

            val updatedGame = game.copy(
                title = rom.name,
                sortTitle = RomMUtils.createSortTitle(rom.name),
                coverPath = coverUrl,
                backgroundPath = backgroundUrl,
                screenshotPaths = screenshotUrls.joinToString(","),
                description = rom.summary,
                releaseYear = rom.firstReleaseDateMillis?.let {
                    java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).year
                },
                genre = rom.genres?.firstOrNull(),
                developer = rom.companies?.firstOrNull(),
                rating = rom.metadatum?.averageRating,
                regions = rom.regions?.joinToString(","),
                languages = rom.languages?.joinToString(","),
                gameModes = rom.metadatum?.gameModes?.joinToString(","),
                franchises = rom.metadatum?.franchises?.joinToString(","),
                achievementCount = rom.raMetadata?.achievements?.size ?: game.achievementCount
            )

            gameDao.update(updatedGame)
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to refresh game data")
        }
    }

    suspend fun getCurrentUser(): RomMResult<RomMUser> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getCurrentUser()
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return RomMResult.Error("Empty response from server")
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch user", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch user")
        }
    }

    suspend fun refreshRAProgressionIfNeeded(): RomMResult<Unit> {
        if (raProgressionRefreshedThisSession) {
            return RomMResult.Success(Unit)
        }

        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val userResponse = currentApi.getCurrentUser()
            if (!userResponse.isSuccessful) {
                return RomMResult.Error("Failed to get user", userResponse.code())
            }
            val user = userResponse.body() ?: return RomMResult.Error("No user data")
            if (user.raUsername.isNullOrBlank()) {
                return RomMResult.Error("No RetroAchievements username configured")
            }

            val response = currentApi.refreshRAProgression(user.id)
            if (response.isSuccessful) {
                raProgressionRefreshedThisSession = true
                RomMResult.Success(Unit)
            } else {
                RomMResult.Error("Failed to refresh RA progression", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to refresh RA progression")
        }
    }

    suspend fun downloadRom(
        romId: Long,
        fileName: String,
        rangeHeader: String? = null
    ): RomMResult<DownloadResponse> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.downloadRom(romId, fileName, rangeHeader)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val isPartial = response.code() == 206
                    RomMResult.Success(DownloadResponse(body, isPartial))
                } else {
                    RomMResult.Error("Empty response body")
                }
            } else {
                val code = response.code()
                val message = when (code) {
                    400 -> "Bad request - try resyncing (HTTP 400)"
                    401, 403 -> "Authentication failed (HTTP $code)"
                    404 -> "ROM not found on server - try resyncing"
                    500, 502, 503 -> "Server error (HTTP $code)"
                    else -> "Download failed (HTTP $code)"
                }
                RomMResult.Error(message, code)
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Download failed")
        }
    }

    suspend fun getLibrarySummary(): RomMResult<Pair<Int, Int>> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getPlatforms()
            if (response.isSuccessful) {
                val platforms = response.body() ?: emptyList()
                val platformCount = platforms.size
                val totalRoms = platforms.sumOf { it.romCount }
                RomMResult.Success(platformCount to totalRoms)
            } else {
                RomMResult.Error("Failed to fetch library", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch library")
        }
    }

    suspend fun fetchAndStorePlatforms(defaultSyncEnabled: Boolean = true): RomMResult<List<PlatformEntity>> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val response = currentApi.getPlatforms()
            if (response.isSuccessful) {
                val platforms = response.body() ?: emptyList()
                val entities = platforms.map { remote ->
                    val existing = platformDao.getById(remote.id)
                        ?: platformDao.getBySlugAndFsSlug(remote.slug, remote.fsSlug)
                        ?: platformDao.getBySlug(remote.slug)  // Fallback for NULL fsSlug migration
                    val platformDef = PlatformDefinitions.getBySlug(remote.slug)
                    val logoUrl = remote.logoUrl?.let { buildMediaUrl(it) }
                    val normalizedName = remote.displayName ?: remote.name
                    PlatformEntity(
                        id = remote.id,
                        slug = remote.slug,
                        fsSlug = remote.fsSlug,
                        name = normalizedName,
                        shortName = platformDef?.shortName ?: normalizedName,
                        romExtensions = platformDef?.extensions?.joinToString(",") ?: "",
                        gameCount = remote.romCount,
                        isVisible = existing?.isVisible ?: true,
                        logoPath = logoUrl ?: existing?.logoPath,
                        sortOrder = platformDef?.sortOrder ?: existing?.sortOrder ?: 999,
                        lastScanned = existing?.lastScanned,
                        syncEnabled = existing?.syncEnabled ?: defaultSyncEnabled,
                        customRomPath = existing?.customRomPath
                    )
                }
                entities.forEach { platformDao.insert(it) }
                RomMResult.Success(entities.sortedBy { it.sortOrder })
            } else {
                RomMResult.Error("Failed to fetch platforms", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch platforms")
        }
    }

    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

    fun disconnect() {
        api = null
        biosRepository.setApi(null)
        accessToken = null
        baseUrl = ""
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun createApi(baseUrl: String, token: String?): RomMApi {
        val moshi = Moshi.Builder().build()

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val authInterceptor = Interceptor { chain ->
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .dns(okhttp3.Dns.SYSTEM)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RomMApi::class.java)
    }

    suspend fun updateUserRating(gameId: Long, rating: Int): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        gameDao.updateUserRating(gameId, rating)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.RATING, intValue = rating)
        return RomMResult.Success(Unit)
    }

    suspend fun updateUserDifficulty(gameId: Long, difficulty: Int): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        gameDao.updateUserDifficulty(gameId, difficulty)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.DIFFICULTY, intValue = difficulty)
        return RomMResult.Success(Unit)
    }

    suspend fun updateUserStatus(gameId: Long, status: String?): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        gameDao.updateStatus(gameId, status)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.STATUS, stringValue = status)
        return RomMResult.Success(Unit)
    }

    suspend fun updateRomUserProps(
        rommId: Long,
        userRating: Int? = null,
        userDifficulty: Int? = null,
        userStatus: String? = null
    ): Boolean {
        val currentApi = api ?: return false
        return try {
            val props = RomMUserPropsUpdate(
                data = RomMUserPropsUpdateData(
                    rating = userRating,
                    difficulty = userDifficulty,
                    status = userStatus
                )
            )
            val response = currentApi.updateRomUserProps(rommId, props)
            response.isSuccessful
        } catch (e: Exception) {
            Logger.error(TAG, "updateRomUserProps failed: ${e.message}")
            false
        }
    }

    suspend fun checkConnection(retryCount: Int = 2) {
        if (api == null) {
            Logger.info(TAG, "checkConnection: api is null, initializing")
            initialize()
            return
        }

        val currentApi = api ?: return
        try {
            val response = currentApi.heartbeat()
            if (response.isSuccessful) {
                val version = response.body()?.version ?: "unknown"
                _connectionState.value = ConnectionState.Connected(version)
                Logger.info(TAG, "checkConnection: connected, version=$version")
            } else {
                Logger.info(TAG, "checkConnection: heartbeat failed with ${response.code()}, reinitializing")
                _connectionState.value = ConnectionState.Disconnected
                api = null
                initialize()
            }
        } catch (e: Exception) {
            Logger.info(TAG, "checkConnection: exception: ${e.message}, retries left=$retryCount")
            _connectionState.value = ConnectionState.Disconnected
            api = null
            if (retryCount > 0) {
                delay(1000)
                initialize()
                if (_connectionState.value !is ConnectionState.Connected && retryCount > 1) {
                    delay(2000)
                    checkConnection(retryCount - 1)
                }
            } else {
                initialize()
            }
        }
    }

    private fun parseTimestamp(timestamp: String?): Instant? {
        if (timestamp.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(timestamp).toInstant()
        } catch (e: Exception) {
            Logger.warn(TAG, "parseTimestamp: failed to parse '$timestamp': ${e.message}")
            null
        }
    }

    private suspend fun getOrCreateFavoritesCollection(): RomMCollection? {
        val currentApi = api ?: return null

        try {
            val response = currentApi.getCollections(isFavorite = true)
            if (response.isSuccessful) {
                val collections = response.body() ?: emptyList()
                val existing = collections.firstOrNull { it.isFavorite }
                if (existing != null) return existing

                val createResponse = currentApi.createCollection(
                    isFavorite = true,
                    collection = RomMCollectionCreate(name = FAVORITES_COLLECTION_NAME)
                )
                if (createResponse.isSuccessful) {
                    return createResponse.body()
                }
            }
        } catch (e: Exception) {
            Logger.info(TAG, "getOrCreateFavoritesCollection: failed: ${e.message}")
        }
        return null
    }

    private suspend fun updateFavoritesCollection(collectionId: Long, romIds: List<Long>): RomMCollection? {
        val currentApi = api ?: return null

        try {
            val jsonArray = romIds.joinToString(",", "[", "]")
            val requestBody = jsonArray.toRequestBody("application/json".toMediaType())
            val response = currentApi.updateCollection(collectionId, requestBody)
            if (response.isSuccessful) {
                return response.body()
            }
        } catch (e: Exception) {
            Logger.info(TAG, "updateFavoritesCollection: failed: ${e.message}")
        }
        return null
    }

    suspend fun syncFavorites(): RomMResult<Unit> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        if (_connectionState.value !is ConnectionState.Connected) {
            return RomMResult.Error("Not connected")
        }

        return try {
            val collection = getOrCreateFavoritesCollection()
                ?: return RomMResult.Error("Failed to get favorites collection")

            val remoteRommIds = collection.romIds.toSet()
            val localRommIds = gameDao.getFavoriteRommIds().toSet()
            val prefs = userPreferencesRepository.preferences.first()
            val isFirstSync = prefs.lastFavoritesSync == null

            if (isFirstSync) {
                val mergedIds = (remoteRommIds + localRommIds).toList()
                Logger.info(TAG, "syncFavorites: first sync, merging ${remoteRommIds.size} remote + ${localRommIds.size} local = ${mergedIds.size} total")

                val result = updateFavoritesCollection(collection.id, mergedIds)
                if (result != null) {
                    if (mergedIds.isNotEmpty()) {
                        gameDao.setFavoritesByRommIds(mergedIds)
                    }
                    parseTimestamp(result.updatedAt)?.let { userPreferencesRepository.setLastFavoritesSyncTime(it) }
                    userPreferencesRepository.setLastFavoritesCheckTime(Instant.now())
                    return RomMResult.Success(Unit)
                }
                return RomMResult.Error("Failed to update favorites collection")
            }

            if (remoteRommIds.isNotEmpty()) {
                gameDao.setFavoritesByRommIds(remoteRommIds.toList())
                gameDao.clearFavoritesNotInRommIds(remoteRommIds.toList())
            } else {
                gameDao.clearFavoritesNotInRommIds(emptyList())
            }
            parseTimestamp(collection.updatedAt)?.let { userPreferencesRepository.setLastFavoritesSyncTime(it) }
            userPreferencesRepository.setLastFavoritesCheckTime(Instant.now())

            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.info(TAG, "syncFavorites: failed: ${e.message}")
            RomMResult.Error(e.message ?: "Failed to sync favorites")
        }
    }

    suspend fun toggleFavoriteWithSync(gameId: Long, rommId: Long, isFavorite: Boolean): RomMResult<Unit> {
        gameDao.updateFavorite(gameId, isFavorite)
        syncCoordinator.get().queueFavoriteChange(gameId, rommId, isFavorite)
        return RomMResult.Success(Unit)
    }

    suspend fun syncFavorite(rommId: Long, isFavorite: Boolean): Boolean {
        val currentApi = api ?: return false
        if (_connectionState.value !is ConnectionState.Connected) return false

        return try {
            val collection = getOrCreateFavoritesCollection() ?: return false
            val currentIds = collection.romIds.toMutableSet()
            if (isFavorite) {
                currentIds.add(rommId)
            } else {
                currentIds.remove(rommId)
            }
            val result = updateFavoritesCollection(collection.id, currentIds.toList())
            if (result != null) {
                parseTimestamp(result.updatedAt)?.let { userPreferencesRepository.setLastFavoritesSyncTime(it) }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "syncFavorite: failed for rommId=$rommId, isFavorite=$isFavorite: ${e.message}")
            false
        }
    }

    suspend fun refreshFavoritesIfNeeded(): RomMResult<Unit> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        if (_connectionState.value !is ConnectionState.Connected) {
            return RomMResult.Error("Not connected")
        }

        val prefs = userPreferencesRepository.preferences.first()
        val lastCheck = prefs.lastFavoritesCheck
        if (lastCheck != null) {
            val elapsed = Duration.between(lastCheck, Instant.now())
            if (elapsed.seconds < FAVORITES_CHECK_DEBOUNCE_SECONDS) {
                return RomMResult.Success(Unit)
            }
        }

        return try {
            val collection = getOrCreateFavoritesCollection()
                ?: return RomMResult.Error("Failed to get favorites collection")

            val remoteUpdatedAt = parseTimestamp(collection.updatedAt)
            val lastSync = prefs.lastFavoritesSync

            userPreferencesRepository.setLastFavoritesCheckTime(Instant.now())

            if (lastSync == null || remoteUpdatedAt == null) {
                Logger.info(TAG, "refreshFavoritesIfNeeded: no comparison possible (lastSync=$lastSync, remoteUpdatedAt=$remoteUpdatedAt), delegating to syncFavorites")
                return syncFavorites()
            }

            if (!remoteUpdatedAt.isAfter(lastSync)) {
                return RomMResult.Success(Unit)
            }

            Logger.info(TAG, "refreshFavoritesIfNeeded: remote is newer, applying changes")
            val remoteRommIds = collection.romIds

            if (remoteRommIds.isNotEmpty()) {
                gameDao.setFavoritesByRommIds(remoteRommIds)
                gameDao.clearFavoritesNotInRommIds(remoteRommIds)
            } else {
                gameDao.clearFavoritesNotInRommIds(emptyList())
            }

            userPreferencesRepository.setLastFavoritesSyncTime(remoteUpdatedAt)
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.info(TAG, "refreshFavoritesIfNeeded: failed: ${e.message}")
            RomMResult.Error(e.message ?: "Failed to refresh favorites")
        }
    }

    private suspend fun safeDeleteFile(path: String) {
        try {
            val file = java.io.File(path)
            if (file.exists() && !file.delete()) {
                Logger.warn(TAG, "safeDeleteFile: failed to delete $path, adding to orphan index")
                orphanedFileDao.insert(OrphanedFileEntity(path = path))
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "safeDeleteFile: error deleting $path: ${e.message}")
            orphanedFileDao.insert(OrphanedFileEntity(path = path))
        }
    }

    suspend fun syncCollections(): RomMResult<Unit> = withContext(Dispatchers.IO) {
        Logger.info(TAG, "syncCollections: starting")
        val currentApi = api ?: run {
            Logger.info(TAG, "syncCollections: not connected (no api)")
            return@withContext RomMResult.Error("Not connected")
        }
        if (_connectionState.value !is ConnectionState.Connected) {
            Logger.info(TAG, "syncCollections: not connected (state=${_connectionState.value})")
            return@withContext RomMResult.Error("Not connected")
        }

        try {
            Logger.info(TAG, "syncCollections: fetching local collections")
            val localCollections = collectionDao.getAllCollections()
            Logger.info(TAG, "syncCollections: found ${localCollections.size} local collections")

            // Push local-only collections to remote first
            for (local in localCollections) {
                if (local.rommId == null && local.isUserCreated && local.name.lowercase() != "favorites") {
                    try {
                        val createResponse = currentApi.createCollection(
                            isFavorite = false,
                            collection = RomMCollectionCreate(name = local.name, description = local.description)
                        )
                        if (createResponse.isSuccessful) {
                            val remoteCollection = createResponse.body()
                            if (remoteCollection != null) {
                                collectionDao.updateCollection(local.copy(rommId = remoteCollection.id))
                                val gameIds = collectionDao.getGameIdsInCollection(local.id)
                                if (gameIds.isNotEmpty()) {
                                    val romIds = gameIds.mapNotNull { gameDao.getById(it)?.rommId }
                                    if (romIds.isNotEmpty()) {
                                        val jsonArray = romIds.joinToString(",", "[", "]")
                                        val requestBody = jsonArray.toRequestBody("application/json".toMediaType())
                                        currentApi.updateCollection(remoteCollection.id, requestBody)
                                    }
                                }
                                Logger.info(TAG, "syncCollections: pushed local collection '${local.name}' to remote")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.warn(TAG, "syncCollections: failed to push local collection '${local.name}': ${e.message}")
                    }
                }
            }

            Logger.info(TAG, "syncCollections: fetching remote collections from API")
            val response = currentApi.getCollections(isFavorite = false)
            Logger.info(TAG, "syncCollections: API response received, success=${response.isSuccessful}")
            if (!response.isSuccessful) {
                return@withContext RomMResult.Error("Failed to fetch collections: ${response.code()}")
            }

            val remoteCollections = response.body() ?: emptyList()
            Logger.info(TAG, "syncCollections: received ${remoteCollections.size} remote collections")
            val updatedLocalCollections = collectionDao.getAllCollections()

            val remoteByRommId = remoteCollections.associateBy { it.id }
            val localByRommId = updatedLocalCollections.filter { it.rommId != null }.associateBy { it.rommId }

            for (remote in remoteCollections) {
                val existing = localByRommId[remote.id]
                if (existing != null) {
                    collectionDao.updateCollection(
                        existing.copy(
                            name = remote.name,
                            description = remote.description,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    collectionDao.insertCollection(
                        CollectionEntity(
                            rommId = remote.id,
                            name = remote.name,
                            description = remote.description,
                            isUserCreated = false
                        )
                    )
                }

                val collectionId = collectionDao.getCollectionByRommId(remote.id)?.id ?: continue
                syncCollectionGames(collectionId, remote.romIds)
            }

            for (local in updatedLocalCollections) {
                if (local.rommId != null && !remoteByRommId.containsKey(local.rommId)) {
                    collectionDao.deleteCollection(local)
                }
            }

            Logger.info(TAG, "syncCollections: synced ${remoteCollections.size} collections")
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.info(TAG, "syncCollections: failed: ${e.message}")
            RomMResult.Error(e.message ?: "Failed to sync collections")
        }
    }

    private suspend fun syncCollectionGames(collectionId: Long, remoteRomIds: List<Long>) {
        val localGameIds = collectionDao.getGameIdsInCollection(collectionId).toSet()
        val remoteGameIds = remoteRomIds.mapNotNull { rommId ->
            gameDao.getByRommId(rommId)?.id
        }.toSet()

        for (gameId in remoteGameIds - localGameIds) {
            collectionDao.addGameToCollection(
                CollectionGameEntity(collectionId = collectionId, gameId = gameId)
            )
        }

        for (gameId in localGameIds - remoteGameIds) {
            collectionDao.removeGameFromCollection(collectionId, gameId)
        }
    }

    suspend fun createCollectionWithSync(name: String, description: String? = null): RomMResult<Long> {
        val entity = CollectionEntity(
            name = name,
            description = description,
            isUserCreated = true
        )
        val localId = collectionDao.insertCollection(entity)

        val currentApi = api
        if (currentApi == null || _connectionState.value !is ConnectionState.Connected) {
            return RomMResult.Success(localId)
        }

        return try {
            val response = currentApi.createCollection(
                isFavorite = false,
                collection = RomMCollectionCreate(name = name, description = description)
            )
            if (response.isSuccessful) {
                val remoteCollection = response.body()
                if (remoteCollection != null) {
                    collectionDao.updateCollection(
                        collectionDao.getCollectionById(localId)!!.copy(rommId = remoteCollection.id)
                    )
                }
            }
            RomMResult.Success(localId)
        } catch (e: Exception) {
            Logger.info(TAG, "createCollectionWithSync: remote sync failed: ${e.message}")
            RomMResult.Success(localId)
        }
    }

    suspend fun updateCollectionWithSync(collectionId: Long, name: String, description: String?): RomMResult<Unit> {
        val collection = collectionDao.getCollectionById(collectionId)
            ?: return RomMResult.Error("Collection not found")

        collectionDao.updateCollection(
            collection.copy(name = name, description = description, updatedAt = System.currentTimeMillis())
        )

        val currentApi = api
        val rommId = collection.rommId
        if (currentApi == null || rommId == null || _connectionState.value !is ConnectionState.Connected) {
            return RomMResult.Success(Unit)
        }

        return try {
            val gameIds = collectionDao.getGameIdsInCollection(collectionId)
            val romIds = gameIds.mapNotNull { gameDao.getById(it)?.rommId }
            val jsonArray = romIds.joinToString(",", "[", "]")
            val requestBody = jsonArray.toRequestBody("application/json".toMediaType())
            currentApi.updateCollection(rommId, requestBody)
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.info(TAG, "updateCollectionWithSync: remote sync failed: ${e.message}")
            RomMResult.Success(Unit)
        }
    }

    suspend fun deleteCollectionWithSync(collectionId: Long): RomMResult<Unit> {
        val collection = collectionDao.getCollectionById(collectionId)
            ?: return RomMResult.Error("Collection not found")

        collectionDao.deleteCollectionById(collectionId)

        val currentApi = api
        val rommId = collection.rommId
        if (currentApi == null || rommId == null || _connectionState.value !is ConnectionState.Connected) {
            return RomMResult.Success(Unit)
        }

        return try {
            currentApi.deleteCollection(rommId)
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.info(TAG, "deleteCollectionWithSync: remote delete failed: ${e.message}")
            RomMResult.Success(Unit)
        }
    }

    suspend fun addGameToCollectionWithSync(gameId: Long, collectionId: Long): RomMResult<Unit> {
        collectionDao.addGameToCollection(
            CollectionGameEntity(collectionId = collectionId, gameId = gameId)
        )

        val collection = collectionDao.getCollectionById(collectionId)
        val currentApi = api
        val rommId = collection?.rommId
        if (currentApi == null || rommId == null || _connectionState.value !is ConnectionState.Connected) {
            return RomMResult.Success(Unit)
        }

        return try {
            val gameIds = collectionDao.getGameIdsInCollection(collectionId)
            val romIds = gameIds.mapNotNull { gameDao.getById(it)?.rommId }
            val jsonArray = romIds.joinToString(",", "[", "]")
            val requestBody = jsonArray.toRequestBody("application/json".toMediaType())
            currentApi.updateCollection(rommId, requestBody)
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.info(TAG, "addGameToCollectionWithSync: remote sync failed: ${e.message}")
            RomMResult.Success(Unit)
        }
    }

    suspend fun removeGameFromCollectionWithSync(gameId: Long, collectionId: Long): RomMResult<Unit> {
        collectionDao.removeGameFromCollection(collectionId, gameId)

        val collection = collectionDao.getCollectionById(collectionId)
        val currentApi = api
        val rommId = collection?.rommId
        if (currentApi == null || rommId == null || _connectionState.value !is ConnectionState.Connected) {
            return RomMResult.Success(Unit)
        }

        return try {
            val gameIds = collectionDao.getGameIdsInCollection(collectionId)
            val romIds = gameIds.mapNotNull { gameDao.getById(it)?.rommId }
            val jsonArray = romIds.joinToString(",", "[", "]")
            val requestBody = jsonArray.toRequestBody("application/json".toMediaType())
            currentApi.updateCollection(rommId, requestBody)
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.info(TAG, "removeGameFromCollectionWithSync: remote sync failed: ${e.message}")
            RomMResult.Success(Unit)
        }
    }
}
