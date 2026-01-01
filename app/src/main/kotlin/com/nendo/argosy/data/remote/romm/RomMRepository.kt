package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.OrphanedFileDao
import com.nendo.argosy.data.local.dao.PendingSyncDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.OrphanedFileEntity
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
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
    private val platformDao: PlatformDao,
    private val pendingSyncDao: PendingSyncDao,
    private val orphanedFileDao: OrphanedFileDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val imageCacheManager: ImageCacheManager,
    private val saveSyncRepository: dagger.Lazy<com.nendo.argosy.data.repository.SaveSyncRepository>,
    private val gameRepository: dagger.Lazy<com.nendo.argosy.data.repository.GameRepository>,
    private val biosRepository: BiosRepository
) {
    private var api: RomMApi? = null
    private var baseUrl: String = ""
    private var accessToken: String? = null
    private val syncMutex = Mutex()
    private var raProgressionRefreshedThisSession = false

    private var cachedRAProgression: Map<Long, Set<String>> = emptyMap()

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
                    val earnedBadgeIds = gameProgress.earnedAchievements.map { it.id }.toSet()
                    gameProgress.romRaId!! to earnedBadgeIds
                }
        } catch (_: Exception) {
        }
    }

    fun getEarnedBadgeIds(raGameId: Long): Set<String> {
        return cachedRAProgression[raGameId] ?: emptySet()
    }

    suspend fun connect(url: String, token: String? = null): RomMResult<String> {
        _connectionState.value = ConnectionState.Connecting

        val normalizedUrl = url.trimEnd('/') + "/"
        baseUrl = normalizedUrl
        accessToken = token

        return try {
            val newApi = createApi(normalizedUrl, token)
            val response = newApi.heartbeat()

            if (response.isSuccessful) {
                api = newApi
                saveSyncRepository.get().setApi(api)
                biosRepository.setApi(api)
                val version = response.body()?.version ?: "unknown"
                _connectionState.value = ConnectionState.Connected(version)
                Logger.info(TAG, "connect: success, version=$version")
                RomMResult.Success(version)
            } else {
                Logger.info(TAG, "connect: heartbeat failed with ${response.code()}")
                _connectionState.value = ConnectionState.Failed("Server returned ${response.code()}")
                RomMResult.Error("Connection failed", response.code())
            }
        } catch (e: Exception) {
            Logger.info(TAG, "connect: exception: ${e.message}")
            _connectionState.value = ConnectionState.Failed(e.message ?: "Unknown error")
            RomMResult.Error(e.message ?: "Connection failed")
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

    private suspend fun doSyncPlatform(platformId: Long): SyncResult {
        val currentApi = api ?: return SyncResult(0, 0, 0, 0, listOf("Not connected"))

        val localPlatform = platformDao.getById(platformId)
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
        val existingById = platformDao.getById(platformId)
        val existingBySlug = if (existingById == null) platformDao.getBySlug(remote.slug) else null
        val existing = existingById ?: existingBySlug
        val platformDef = PlatformDefinitions.getBySlug(remote.slug)

        val logoUrl = remote.logoUrl?.let { buildMediaUrl(it) }
        val isAlias = PlatformDefinitions.isAlias(remote.slug)
        val normalizedName = if (isAlias) {
            PlatformDefinitions.normalizeDisplayName(remote.name)
        } else {
            platformDef?.name ?: PlatformDefinitions.normalizeDisplayName(remote.name)
        }
        val entity = PlatformEntity(
            id = platformId,
            slug = remote.slug,
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

        when {
            existing == null -> {
                platformDao.insert(entity)
            }
            existingBySlug != null && existingBySlug.id != platformId -> {
                platformDao.insert(entity)
                gameDao.migratePlatform(existingBySlug.id, platformId)
                emulatorConfigDao.migratePlatform(existingBySlug.id, platformId)
                platformDao.deleteById(existingBySlug.id)
                Logger.info(TAG, "Migrated platform ${existingBySlug.id} -> $platformId")
            }
            else -> {
                platformDao.update(entity)
            }
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

        val screenshotUrls = rom.screenshotUrls.ifEmpty {
            rom.screenshotPaths?.map { buildMediaUrl(it) } ?: emptyList()
        }

        val backgroundUrl = rom.backgroundUrls.firstOrNull()
        val cachedBackground = when {
            existing?.backgroundPath?.startsWith("/") == true -> existing.backgroundPath
            backgroundUrl != null -> {
                imageCacheManager.queueBackgroundCache(backgroundUrl, rom.id, rom.name)
                backgroundUrl
            }
            else -> null
        }

        val coverUrl = rom.coverLarge?.let { buildMediaUrl(it) }
        val cachedCover = when {
            existing?.coverPath?.startsWith("/") == true -> existing.coverPath
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

        val game = GameEntity(
            id = existing?.id ?: 0,
            platformId = platformId,
            platformSlug = platformSlug,
            title = rom.name,
            sortTitle = RomMUtils.createSortTitle(rom.name),
            localPath = existing?.localPath,
            rommId = rom.id,
            igdbId = rom.igdbId,
            source = if (existing?.localPath != null) GameSource.ROMM_SYNCED else GameSource.ROMM_REMOTE,
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
            userRating = rom.romUser?.rating ?: existing?.userRating ?: 0,
            userDifficulty = rom.romUser?.difficulty ?: existing?.userDifficulty ?: 0,
            completion = rom.romUser?.completion ?: existing?.completion ?: 0,
            status = rom.romUser?.status ?: existing?.status,
            backlogged = rom.romUser?.backlogged ?: existing?.backlogged ?: false,
            nowPlaying = rom.romUser?.nowPlaying ?: existing?.nowPlaying ?: false,
            isFavorite = existing?.isFavorite ?: false,
            isHidden = existing?.isHidden ?: false,
            // For folder-based: reset to false (extraction will set it if needed)
            // For sibling-based: preserve existing value (consolidation sets it)
            // Only preserve isMultiDisc if game was downloaded (has localPath)
            isMultiDisc = when {
                rom.isFolderMultiDisc -> existing?.isMultiDisc == true && existing.localPath != null
                shouldBeMultiDisc -> existing?.isMultiDisc ?: false
                else -> false
            },
            playCount = existing?.playCount ?: 0,
            playTimeMinutes = existing?.playTimeMinutes ?: 0,
            lastPlayed = existing?.lastPlayed,
            addedAt = existing?.addedAt ?: java.time.Instant.now(),
            achievementCount = rom.raMetadata?.achievements?.size ?: existing?.achievementCount ?: 0
        )

        val isNew = existing == null
        gameDao.insert(game)
        return isNew to game
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
        var offset = 0
        var totalFetched = 0

        while (true) {
            val romsResponse = api.getRoms(
                platformId = platform.id,
                limit = SYNC_PAGE_SIZE,
                offset = offset
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
        for (remote in remotePlatforms) {
            val numericId = remote.id
            val slug = remote.slug

            val numericPlatform = platformDao.getById(numericId)
            val legacyPlatform = platformDao.getBySlug(slug)

            if (numericPlatform != null && legacyPlatform != null && legacyPlatform.id != numericId) {
                gameDao.migratePlatform(legacyPlatform.id, numericId)
                platformDao.deleteById(legacyPlatform.id)
                Logger.info(TAG, "Migrated games and removed legacy platform: ${legacyPlatform.id} -> $numericId")
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
                RomMResult.Success(response.body()!!)
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

            val updatedGame = game.copy(
                userRating = romUser.rating,
                userDifficulty = romUser.difficulty,
                status = romUser.status,
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
                RomMResult.Success(response.body()!!)
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
                        ?: platformDao.getBySlug(remote.slug)
                    val platformDef = PlatformDefinitions.getBySlug(remote.slug)
                    val logoUrl = remote.logoUrl?.let { buildMediaUrl(it) }
                    val isAlias = PlatformDefinitions.isAlias(remote.slug)
                    val normalizedName = if (isAlias) {
                        PlatformDefinitions.normalizeDisplayName(remote.name)
                    } else {
                        platformDef?.name ?: PlatformDefinitions.normalizeDisplayName(remote.name)
                    }
                    PlatformEntity(
                        id = remote.id,
                        slug = remote.slug,
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
            .withUserCertTrust(true)
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

        pendingSyncDao.deleteByGameAndType(gameId, "RATING")

        val currentApi = api
        if (currentApi == null || _connectionState.value !is ConnectionState.Connected) {
            pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "RATING", value = rating))
            return RomMResult.Success(Unit)
        }

        return try {
            val response = currentApi.updateRomUserProps(rommId, RomMUserPropsUpdate(data = RomMUserPropsUpdateData(rating = rating)))
            if (!response.isSuccessful) {
                pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "RATING", value = rating))
            }
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "RATING", value = rating))
            RomMResult.Success(Unit)
        }
    }

    suspend fun updateUserDifficulty(gameId: Long, difficulty: Int): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")

        gameDao.updateUserDifficulty(gameId, difficulty)

        val rommId = game.rommId ?: return RomMResult.Success(Unit)

        pendingSyncDao.deleteByGameAndType(gameId, "DIFFICULTY")

        val currentApi = api
        if (currentApi == null || _connectionState.value !is ConnectionState.Connected) {
            pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "DIFFICULTY", value = difficulty))
            return RomMResult.Success(Unit)
        }

        return try {
            val response = currentApi.updateRomUserProps(rommId, RomMUserPropsUpdate(data = RomMUserPropsUpdateData(difficulty = difficulty)))
            if (!response.isSuccessful) {
                pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "DIFFICULTY", value = difficulty))
            }
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "DIFFICULTY", value = difficulty))
            RomMResult.Success(Unit)
        }
    }

    suspend fun updateUserStatus(gameId: Long, status: String?): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")

        gameDao.updateStatus(gameId, status)

        val rommId = game.rommId ?: return RomMResult.Success(Unit)

        pendingSyncDao.deleteByGameAndType(gameId, "STATUS")

        val currentApi = api
        if (currentApi == null || _connectionState.value !is ConnectionState.Connected) {
            pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "STATUS", stringValue = status))
            return RomMResult.Success(Unit)
        }

        return try {
            val response = currentApi.updateRomUserProps(
                rommId,
                RomMUserPropsUpdate(data = RomMUserPropsUpdateData(status = status))
            )
            if (!response.isSuccessful) {
                pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "STATUS", stringValue = status))
            }
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "STATUS", stringValue = status))
            RomMResult.Success(Unit)
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

    suspend fun processPendingSync(): Int {
        val currentApi = api ?: return 0
        if (_connectionState.value !is ConnectionState.Connected) return 0

        val pending = pendingSyncDao.getAll()
        var synced = 0

        val favoriteChanges = pending.filter { it.syncType == "FAVORITE" }
        val otherChanges = pending.filter { it.syncType != "FAVORITE" }

        if (favoriteChanges.isNotEmpty()) {
            try {
                val collection = getOrCreateFavoritesCollection()
                if (collection != null) {
                    val currentRemoteIds = collection.romIds.toMutableSet()

                    for (item in favoriteChanges) {
                        if (item.value == 1) {
                            currentRemoteIds.add(item.rommId)
                        } else {
                            currentRemoteIds.remove(item.rommId)
                        }
                    }

                    val result = updateFavoritesCollection(collection.id, currentRemoteIds.toList())
                    if (result != null) {
                        for (item in favoriteChanges) {
                            pendingSyncDao.delete(item.id)
                            synced++
                        }
                        parseTimestamp(result.updatedAt)?.let { userPreferencesRepository.setLastFavoritesSyncTime(it) }
                    }
                }
            } catch (e: Exception) {
                Logger.info(TAG, "processPendingSync: failed to sync favorites: ${e.message}")
            }
        }

        for (item in otherChanges) {
            try {
                val props = when (item.syncType) {
                    "RATING" -> RomMUserPropsUpdate(data = RomMUserPropsUpdateData(rating = item.value))
                    "DIFFICULTY" -> RomMUserPropsUpdate(data = RomMUserPropsUpdateData(difficulty = item.value))
                    "STATUS" -> RomMUserPropsUpdate(data = RomMUserPropsUpdateData(status = item.stringValue))
                    else -> continue
                }
                val response = currentApi.updateRomUserProps(item.rommId, props)
                if (response.isSuccessful) {
                    pendingSyncDao.delete(item.id)
                    synced++
                    if (synced < pending.size) {
                        delay(500)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return synced
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

            val pendingFavorites = pendingSyncDao.getAll().filter { it.syncType == "FAVORITE" }
            if (pendingFavorites.isNotEmpty()) {
                val currentRemoteIds = remoteRommIds.toMutableSet()
                for (item in pendingFavorites) {
                    if (item.value == 1) {
                        currentRemoteIds.add(item.rommId)
                    } else {
                        currentRemoteIds.remove(item.rommId)
                    }
                }

                val result = updateFavoritesCollection(collection.id, currentRemoteIds.toList())
                if (result != null) {
                    for (item in pendingFavorites) {
                        pendingSyncDao.delete(item.id)
                    }
                    if (currentRemoteIds.isNotEmpty()) {
                        gameDao.setFavoritesByRommIds(currentRemoteIds.toList())
                        gameDao.clearFavoritesNotInRommIds(currentRemoteIds.toList())
                    } else {
                        gameDao.clearFavoritesNotInRommIds(emptyList())
                    }
                    parseTimestamp(result.updatedAt)?.let { userPreferencesRepository.setLastFavoritesSyncTime(it) }
                    userPreferencesRepository.setLastFavoritesCheckTime(Instant.now())
                    return RomMResult.Success(Unit)
                }
            } else {
                if (remoteRommIds.isNotEmpty()) {
                    gameDao.setFavoritesByRommIds(remoteRommIds.toList())
                    gameDao.clearFavoritesNotInRommIds(remoteRommIds.toList())
                } else {
                    gameDao.clearFavoritesNotInRommIds(emptyList())
                }
                parseTimestamp(collection.updatedAt)?.let { userPreferencesRepository.setLastFavoritesSyncTime(it) }
                userPreferencesRepository.setLastFavoritesCheckTime(Instant.now())
            }

            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.info(TAG, "syncFavorites: failed: ${e.message}")
            RomMResult.Error(e.message ?: "Failed to sync favorites")
        }
    }

    suspend fun toggleFavoriteWithSync(gameId: Long, rommId: Long, isFavorite: Boolean): RomMResult<Unit> {
        gameDao.updateFavorite(gameId, isFavorite)

        pendingSyncDao.deleteByGameAndType(gameId, "FAVORITE")

        val currentApi = api
        if (currentApi == null || _connectionState.value !is ConnectionState.Connected) {
            val value = if (isFavorite) 1 else 0
            pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "FAVORITE", value = value))
            return RomMResult.Success(Unit)
        }

        return try {
            val collection = getOrCreateFavoritesCollection()
                ?: run {
                    val value = if (isFavorite) 1 else 0
                    pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "FAVORITE", value = value))
                    return RomMResult.Success(Unit)
                }

            val currentIds = collection.romIds.toMutableSet()
            if (isFavorite) {
                currentIds.add(rommId)
            } else {
                currentIds.remove(rommId)
            }

            val result = updateFavoritesCollection(collection.id, currentIds.toList())
            if (result != null) {
                parseTimestamp(result.updatedAt)?.let { userPreferencesRepository.setLastFavoritesSyncTime(it) }
                RomMResult.Success(Unit)
            } else {
                val value = if (isFavorite) 1 else 0
                pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "FAVORITE", value = value))
                RomMResult.Success(Unit)
            }
        } catch (e: Exception) {
            val value = if (isFavorite) 1 else 0
            pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "FAVORITE", value = value))
            RomMResult.Success(Unit)
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
}
