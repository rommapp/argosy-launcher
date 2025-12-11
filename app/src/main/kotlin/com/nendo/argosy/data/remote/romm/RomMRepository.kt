package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.PendingSyncDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.RegionFilterMode
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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_PAGE_SIZE = 100

sealed class RomMResult<out T> {
    data class Success<T>(val data: T) : RomMResult<T>()
    data class Error(val message: String, val code: Int? = null) : RomMResult<Nothing>()
}

data class SyncProgress(
    val isSyncing: Boolean = false,
    val currentPlatform: String = "",
    val platformsTotal: Int = 0,
    val platformsDone: Int = 0,
    val gamesTotal: Int = 0,
    val gamesDone: Int = 0
)

data class SyncResult(
    val platformsSynced: Int,
    val gamesAdded: Int,
    val gamesUpdated: Int,
    val gamesDeleted: Int,
    val errors: List<String>
)

@Singleton
class RomMRepository @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val platformDao: PlatformDao,
    private val pendingSyncDao: PendingSyncDao,
    private val imageCacheManager: ImageCacheManager,
    private val saveSyncRepository: dagger.Lazy<com.nendo.argosy.data.repository.SaveSyncRepository>
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
        if (!prefs.rommBaseUrl.isNullOrBlank()) {
            val result = connect(prefs.rommBaseUrl, prefs.rommToken)
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
            api = createApi(normalizedUrl, token)
            saveSyncRepository.get().setApi(api)
            val response = api!!.heartbeat()

            if (response.isSuccessful) {
                val version = response.body()?.version ?: "unknown"
                _connectionState.value = ConnectionState.Connected(version)
                RomMResult.Success(version)
            } else {
                _connectionState.value = ConnectionState.Failed("Server returned ${response.code()}")
                RomMResult.Error("Connection failed", response.code())
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Failed(e.message ?: "Unknown error")
            RomMResult.Error(e.message ?: "Connection failed")
        }
    }

    suspend fun login(username: String, password: String): RomMResult<String> {
        val currentApi = api ?: return RomMResult.Error("Not connected")

        return try {
            val scope = "me.read me.write platforms.read roms.read assets.read roms.user.read roms.user.write"
            val response = currentApi.login(username, password, scope)
            if (response.isSuccessful) {
                val token = response.body()?.accessToken
                    ?: return RomMResult.Error("No token received")

                accessToken = token
                api = createApi(baseUrl, token)
                saveSyncRepository.get().setApi(api)

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
            _syncProgress.value = _syncProgress.value.copy(platformsTotal = platforms.size)

            for ((index, platform) in platforms.withIndex()) {
                onProgress?.invoke(index + 1, platforms.size, platform.name)

                _syncProgress.value = _syncProgress.value.copy(
                    currentPlatform = platform.name,
                    platformsDone = index
                )

                syncPlatform(platform)

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

            platforms.forEach { platform ->
                val count = gameDao.countByPlatform(platform.slug)
                platformDao.updateGameCount(platform.slug, count)
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

        return SyncResult(platformsSynced, gamesAdded, gamesUpdated, gamesDeleted, errors)
    }

    private suspend fun syncPlatform(remote: RomMPlatform) {
        val existing = platformDao.getById(remote.slug)
        val platformDef = PlatformDefinitions.getById(remote.slug)

        val logoUrl = remote.logoUrl?.let { buildMediaUrl(it) }
        val entity = PlatformEntity(
            id = remote.slug,
            name = platformDef?.name ?: remote.name,
            shortName = platformDef?.shortName ?: remote.name,
            romExtensions = platformDef?.extensions?.joinToString(",") ?: "",
            gameCount = remote.romCount,
            isVisible = existing?.isVisible ?: true,
            logoPath = logoUrl ?: existing?.logoPath,
            sortOrder = platformDef?.sortOrder ?: existing?.sortOrder ?: 0,
            lastScanned = existing?.lastScanned
        )

        if (existing == null) {
            platformDao.insert(entity)
        } else {
            platformDao.update(entity)
        }

        // Queue logo for caching with black background removal
        if (logoUrl != null && logoUrl.startsWith("http")) {
            imageCacheManager.queuePlatformLogoCache(remote.slug, logoUrl)
        }
    }

    private suspend fun syncRom(rom: RomMRom, platformSlug: String): Pair<Boolean, GameEntity> {
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

        val game = GameEntity(
            id = existing?.id ?: 0,
            platformId = platformSlug,
            title = rom.name,
            sortTitle = createSortTitle(rom.name),
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
            backlogged = rom.romUser?.backlogged ?: existing?.backlogged ?: false,
            nowPlaying = rom.romUser?.nowPlaying ?: existing?.nowPlaying ?: false,
            isFavorite = existing?.isFavorite ?: false,
            isHidden = existing?.isHidden ?: false,
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

    private fun createSortTitle(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.startsWith("the ") -> title.drop(4)
            lower.startsWith("a ") -> title.drop(2)
            lower.startsWith("an ") -> title.drop(3)
            else -> title
        }.lowercase()
    }

    private fun shouldSyncRom(rom: RomMRom, filters: SyncFilterPreferences): Boolean {
        if (!passesExtensionFilter(rom)) return false
        if (!passesBadDumpFilter(rom)) return false
        if (!passesRegionFilter(rom, filters)) return false
        if (!passesRevisionFilter(rom, filters)) return false
        return true
    }

    private fun passesExtensionFilter(rom: RomMRom): Boolean {
        val fileName = rom.fileName ?: return true
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return true

        val platformDef = PlatformDefinitions.getById(rom.platformSlug) ?: return true
        if (platformDef.extensions.isEmpty()) return true

        return extension in platformDef.extensions
    }

    private fun passesBadDumpFilter(rom: RomMRom): Boolean {
        val name = rom.name.lowercase()
        val fileName = rom.fileName?.lowercase() ?: ""
        if (BAD_DUMP_REGEX.containsMatchIn(name) || BAD_DUMP_REGEX.containsMatchIn(fileName)) return false
        return true
    }

    private fun passesRegionFilter(rom: RomMRom, filters: SyncFilterPreferences): Boolean {
        val romRegions = rom.regions
        if (romRegions.isNullOrEmpty()) return true

        val matchesEnabled = romRegions.any { region ->
            filters.enabledRegions.any { enabled ->
                region.equals(enabled, ignoreCase = true)
            }
        }

        return when (filters.regionMode) {
            RegionFilterMode.INCLUDE -> matchesEnabled
            RegionFilterMode.EXCLUDE -> !matchesEnabled
        }
    }

    private fun passesRevisionFilter(rom: RomMRom, filters: SyncFilterPreferences): Boolean {
        val revision = rom.revision?.lowercase() ?: ""
        val name = rom.name.lowercase()
        val tags = rom.tags?.map { it.lowercase() } ?: emptyList()

        if (filters.excludeBeta) {
            if (revision.contains("beta") || name.contains("(beta)")) return false
        }
        if (filters.excludePrototype) {
            if (revision.contains("proto") || name.contains("(proto)")) return false
        }
        if (filters.excludeDemo) {
            if (revision.contains("demo") || name.contains("(demo)") || name.contains("(sample)")) return false
        }
        if (filters.excludeHack) {
            if (isHack(name, revision, tags)) return false
        }

        return true
    }

    private fun isHack(name: String, revision: String, tags: List<String>): Boolean {
        if (revision.contains("hack")) return true
        if (tags.any { it.contains("hack") }) return true
        if (NO_INTRO_HACK_REGEX.containsMatchIn(name)) return true
        if (HACK_BRACKET_REGEX.containsMatchIn(name)) return true
        if (HACK_PAREN_REGEX.containsMatchIn(name)) return true
        return false
    }

    companion object {
        // Matches No-Intro style hack tags: [h], [h1], [hC], [h M], etc.
        private val NO_INTRO_HACK_REGEX = Regex("\\[h[0-9a-z ]*\\]")
        // Matches [hack], [some hack], etc. - but not game titles like ".hack" since that's outside brackets
        private val HACK_BRACKET_REGEX = Regex("\\[.*\\bhack\\b.*\\]")
        // Matches (hack), (undub hack), etc. - but not ".hack (USA)" since "(USA)" doesn't contain "hack"
        private val HACK_PAREN_REGEX = Regex("\\(.*\\bhack\\b.*\\)")
        // Matches bad dumps: [b], [b1], [o], [o1] (overdumps), [!p] (pending), [t] (trained), [f] (fixed)
        private val BAD_DUMP_REGEX = Regex("\\[[boftpBOFTP][0-9]*\\]")
    }

    private data class PlatformSyncResult(
        val added: Int,
        val updated: Int,
        val seenIds: Set<Long>,
        val multiDiscGroups: List<MultiDiscGroup>,
        val error: String? = null
    )

    data class MultiDiscGroup(
        val primaryRommId: Long,
        val siblingRommIds: List<Long>,
        val platformSlug: String
    )

    private suspend fun syncPlatformRoms(
        api: RomMApi,
        platform: RomMPlatform,
        filters: SyncFilterPreferences
    ): PlatformSyncResult {
        var added = 0
        var updated = 0
        val seenIds = mutableSetOf<Long>()
        val seenIgdbIds = mutableMapOf<Long, Long>()
        val syncedRomsWithRA = mutableSetOf<Long>()
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
                return PlatformSyncResult(added, updated, seenIds, multiDiscGroups,
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
                if (rom.igdbId == null || !shouldSyncRom(rom, filters)) continue

                val existingRomId = seenIgdbIds[rom.igdbId]
                if (existingRomId != null) {
                    val existingHasRA = syncedRomsWithRA.contains(existingRomId)
                    val newHasRA = rom.raId != null || rom.raMetadata?.achievements?.isNotEmpty() == true
                    if (!existingHasRA && newHasRA) {
                        seenIds.remove(existingRomId)
                        seenIgdbIds[rom.igdbId] = rom.id
                        seenIds.add(rom.id)
                        if (newHasRA) syncedRomsWithRA.add(rom.id)
                        try {
                            syncRom(rom, platform.slug)
                            updated++
                        } catch (_: Exception) {}
                    }
                    continue
                }

                seenIgdbIds[rom.igdbId] = rom.id
                seenIds.add(rom.id)
                val hasRA = rom.raId != null || rom.raMetadata?.achievements?.isNotEmpty() == true
                if (hasRA) syncedRomsWithRA.add(rom.id)

                try {
                    val (isNew, _) = syncRom(rom, platform.slug)
                    if (isNew) added++ else updated++

                    if (rom.hasDiscSiblings && rom.id !in processedDiscIds) {
                        val discSiblings = rom.siblings?.filter { it.isDiscVariant } ?: emptyList()
                        val siblingIds = discSiblings.map { it.id }

                        processedDiscIds.add(rom.id)
                        processedDiscIds.addAll(siblingIds)
                        seenIds.addAll(siblingIds)

                        multiDiscGroups.add(MultiDiscGroup(
                            primaryRommId = rom.id,
                            siblingRommIds = siblingIds,
                            platformSlug = platform.slug
                        ))
                    }
                } catch (_: Exception) {
                }
            }

            if (totalFetched >= romsPage.total) break
            offset += SYNC_PAGE_SIZE
        }

        return PlatformSyncResult(added, updated, seenIds, multiDiscGroups)
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
            } catch (_: Exception) {
                null
            }

            discsToInsert.add(GameDiscEntity(
                id = existingDisc?.id ?: 0,
                gameId = primaryGame.id,
                discNumber = romData?.discNumber ?: (discsToInsert.size + existingDiscs.size + 1),
                rommId = rommId,
                fileName = romData?.fileName ?: "Disc",
                localPath = localPath,
                fileSize = romData?.fileSize ?: 0
            ))
        }

        if (discsToInsert.isNotEmpty()) {
            gameDiscDao.insertAll(discsToInsert)
        }

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

            val platformDef = PlatformDefinitions.getById(game.platformId) ?: continue
            if (platformDef.extensions.isEmpty()) continue

            if (extension !in platformDef.extensions) {
                try {
                    val file = java.io.File(localPath)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
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
                game.localPath?.let { path ->
                    try {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
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
                game.localPath?.let { path ->
                    try {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
                gameDao.delete(game.id)
                deleted++
            }
        }

        return deleted
    }

    private suspend fun deleteOrphanedGames(seenRommIds: Set<Long>): Int {
        var deleted = 0

        val remoteGames = gameDao.getBySource(GameSource.ROMM_REMOTE)
        for (game in remoteGames) {
            val rommId = game.rommId ?: continue
            if (rommId !in seenRommIds) {
                gameDao.delete(game.id)
                deleted++
            }
        }

        val syncedGames = gameDao.getBySource(GameSource.ROMM_SYNCED)
        for (game in syncedGames) {
            val rommId = game.rommId ?: continue
            if (rommId !in seenRommIds) {
                game.localPath?.let { path ->
                    try {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {
                    }
                }
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

    data class DownloadResponse(
        val body: okhttp3.ResponseBody,
        val isPartialContent: Boolean
    )

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

    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

    fun disconnect() {
        api = null
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

    suspend fun checkConnection() {
        val currentApi = api ?: return
        try {
            val response = currentApi.heartbeat()
            if (response.isSuccessful) {
                val version = response.body()?.version ?: "unknown"
                _connectionState.value = ConnectionState.Connected(version)
            } else {
                _connectionState.value = ConnectionState.Disconnected
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    suspend fun processPendingSync(): Int {
        val currentApi = api ?: return 0
        if (_connectionState.value !is ConnectionState.Connected) return 0

        val pending = pendingSyncDao.getAll()
        var synced = 0

        for (item in pending) {
            try {
                val props = when (item.syncType) {
                    "RATING" -> RomMUserPropsUpdate(data = RomMUserPropsUpdateData(rating = item.value))
                    "DIFFICULTY" -> RomMUserPropsUpdate(data = RomMUserPropsUpdateData(difficulty = item.value))
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
}
