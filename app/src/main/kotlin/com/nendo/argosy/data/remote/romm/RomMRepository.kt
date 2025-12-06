package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncDao
import com.nendo.argosy.data.local.dao.PlatformDao
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
import android.util.Log
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMRepository"

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
    private val platformDao: PlatformDao,
    private val pendingSyncDao: PendingSyncDao,
    private val imageCacheManager: ImageCacheManager
) {
    private var api: RomMApi? = null
    private var baseUrl: String = ""
    private var accessToken: String? = null
    private val syncMutex = Mutex()

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
        Log.d(TAG, "initialize() called")

        val gameCount = gameDao.countAll()
        Log.d(TAG, "initialize: DB has $gameCount games total")

        val prefs = userPreferencesRepository.preferences.first()
        Log.d(TAG, "initialize: baseUrl=${prefs.rommBaseUrl?.take(30)}, hasToken=${prefs.rommToken != null}")
        if (!prefs.rommBaseUrl.isNullOrBlank()) {
            val result = connect(prefs.rommBaseUrl, prefs.rommToken)
            Log.d(TAG, "initialize connect result: $result")
        } else {
            Log.d(TAG, "initialize: no baseUrl, skipping connect")
        }
    }

    suspend fun connect(url: String, token: String? = null): RomMResult<String> {
        Log.d(TAG, "connect: url=${url.take(30)}, hasToken=${token != null}")
        _connectionState.value = ConnectionState.Connecting

        val normalizedUrl = url.trimEnd('/') + "/"
        baseUrl = normalizedUrl
        accessToken = token

        return try {
            api = createApi(normalizedUrl, token)
            val response = api!!.heartbeat()

            if (response.isSuccessful) {
                val version = response.body()?.version ?: "unknown"
                Log.d(TAG, "connect SUCCESS: version=$version")
                _connectionState.value = ConnectionState.Connected(version)
                RomMResult.Success(version)
            } else {
                Log.d(TAG, "connect FAILED: code=${response.code()}")
                _connectionState.value = ConnectionState.Failed("Server returned ${response.code()}")
                RomMResult.Error("Connection failed", response.code())
            }
        } catch (e: Exception) {
            Log.d(TAG, "connect EXCEPTION: ${e.message}")
            _connectionState.value = ConnectionState.Failed(e.message ?: "Unknown error")
            RomMResult.Error(e.message ?: "Connection failed")
        }
    }

    suspend fun login(username: String, password: String): RomMResult<String> {
        val currentApi = api ?: return RomMResult.Error("Not connected")

        return try {
            Log.d(TAG, "login: attempting login for user=$username")
            val scope = "me.read platforms.read roms.read assets.read roms.user.read roms.user.write"
            val response = currentApi.login(username, password, scope)
            Log.d(TAG, "login: response code=${response.code()}, successful=${response.isSuccessful}")
            if (response.isSuccessful) {
                val token = response.body()?.accessToken
                if (token == null) {
                    Log.e(TAG, "login: no token in response body")
                    return RomMResult.Error("No token received")
                }

                Log.d(TAG, "login: got token, recreating API with auth")
                accessToken = token
                api = createApi(baseUrl, token)

                userPreferencesRepository.setRomMCredentials(baseUrl, token, username)
                RomMResult.Success(token)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "login: failed, error=$errorBody")
                RomMResult.Error("Login failed", response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "login: exception", e)
            RomMResult.Error(e.message ?: "Login failed")
        }
    }

    suspend fun syncLibrary(
        onProgress: ((current: Int, total: Int, platformName: String) -> Unit)? = null
    ): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            Log.d(TAG, "syncLibrary: sync already in progress, skipping")
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

        _syncProgress.value = SyncProgress(isSyncing = true)

        try {
            val initialCount = gameDao.countAll()
            Log.d(TAG, "syncLibrary: STARTING sync - initial game count in DB: $initialCount")
            Log.d(TAG, "syncLibrary: fetching platforms...")
            val platformsResponse = currentApi.getPlatforms()
            Log.d(TAG, "syncLibrary: platforms response code=${platformsResponse.code()}, successful=${platformsResponse.isSuccessful}")

            if (!platformsResponse.isSuccessful) {
                val errorBody = platformsResponse.errorBody()?.string()
                Log.e(TAG, "syncLibrary: failed to fetch platforms, error=$errorBody")
                val errorMsg = when (platformsResponse.code()) {
                    401, 403 -> "Authentication failed - token may be invalid or missing permissions"
                    else -> "Failed to fetch platforms: ${platformsResponse.code()}"
                }
                return SyncResult(0, 0, 0, 0, listOf(errorMsg))
            }

            val platforms = platformsResponse.body()
            if (platforms.isNullOrEmpty()) {
                Log.e(TAG, "syncLibrary: platforms response body is null or empty")
                return SyncResult(0, 0, 0, 0, listOf("No platforms returned from server"))
            }
            Log.d(TAG, "syncLibrary: got ${platforms.size} platforms")
            _syncProgress.value = _syncProgress.value.copy(platformsTotal = platforms.size)

            for ((index, platform) in platforms.withIndex()) {
                onProgress?.invoke(index + 1, platforms.size, platform.name)

                _syncProgress.value = _syncProgress.value.copy(
                    currentPlatform = platform.name,
                    platformsDone = index
                )

                syncPlatform(platform)

                val limit = 100
                var offset = 0
                var totalFetched = 0
                var platformAdded = 0
                var platformUpdated = 0
                var platformSkipped = 0

                while (true) {
                    Log.d(TAG, "syncLibrary: fetching ROMs for ${platform.name} (id=${platform.id}), offset=$offset, limit=$limit")
                    val romsResponse = currentApi.getRoms(
                        platformId = platform.id,
                        limit = limit,
                        offset = offset
                    )

                    if (!romsResponse.isSuccessful) {
                        val errorBody = romsResponse.errorBody()?.string()
                        Log.e(TAG, "syncLibrary: failed to fetch ROMs: $errorBody")
                        errors.add("Failed to fetch ROMs for ${platform.name}: ${romsResponse.code()}")
                        break
                    }

                    val romsPage = romsResponse.body()
                    if (romsPage == null || romsPage.items.isEmpty()) {
                        break
                    }

                    totalFetched += romsPage.items.size
                    _syncProgress.value = _syncProgress.value.copy(
                        gamesTotal = romsPage.total,
                        gamesDone = totalFetched
                    )

                    for (rom in romsPage.items) {
                        if (rom.igdbId == null) {
                            platformSkipped++
                            continue
                        }
                        if (!shouldSyncRom(rom, filters)) {
                            platformSkipped++
                            continue
                        }
                        seenRommIds.add(rom.id)
                        try {
                            val result = syncRom(rom, platform.slug)
                            if (result.first) {
                                gamesAdded++
                                platformAdded++
                            } else {
                                gamesUpdated++
                                platformUpdated++
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "syncLibrary: failed to sync ROM ${rom.name}: ${e.message}")
                        }
                    }

                    if (totalFetched >= romsPage.total) break
                    offset += limit
                }

                val dbCount = gameDao.countByPlatform(platform.slug)
                Log.d(TAG, "syncLibrary: ${platform.slug} done - fetched=$totalFetched, added=$platformAdded, updated=$platformUpdated, skipped=$platformSkipped, dbCount=$dbCount")
                platformsSynced++
            }

            val totalGames = gameDao.countAll()
            Log.d(TAG, "syncLibrary: TOTAL games in DB: $totalGames")

            platforms.forEach { platform ->
                val count = gameDao.countByPlatform(platform.slug)
                Log.d(TAG, "syncLibrary: platform ${platform.slug} has $count games")
                platformDao.updateGameCount(platform.slug, count)
            }

            if (filters.deleteOrphans) {
                gamesDeleted = deleteOrphanedGames(seenRommIds)
            }

            userPreferencesRepository.setLastRommSyncTime(Instant.now())
            Log.d(TAG, "syncLibrary: recorded sync time, added=$gamesAdded, updated=$gamesUpdated, deleted=$gamesDeleted")

        } catch (e: Exception) {
            Log.e(TAG, "syncLibrary: exception during sync", e)
            errors.add(e.message ?: "Sync failed")
        } finally {
            _syncProgress.value = SyncProgress(isSyncing = false)
        }

        return SyncResult(platformsSynced, gamesAdded, gamesUpdated, gamesDeleted, errors)
    }

    private suspend fun syncPlatform(remote: RomMPlatform) {
        val existing = platformDao.getById(remote.slug)
        val platformDef = PlatformDefinitions.getById(remote.slug)

        val entity = PlatformEntity(
            id = remote.slug,
            name = platformDef?.name ?: remote.name,
            shortName = platformDef?.shortName ?: remote.name,
            romExtensions = platformDef?.extensions?.joinToString(",") ?: "",
            gameCount = remote.romCount,
            isVisible = existing?.isVisible ?: true,
            logoPath = existing?.logoPath,
            sortOrder = platformDef?.sortOrder ?: existing?.sortOrder ?: 0,
            lastScanned = existing?.lastScanned
        )

        if (existing == null) {
            platformDao.insert(entity)
        } else {
            platformDao.update(entity)
        }
    }

    private suspend fun syncRom(rom: RomMRom, platformSlug: String): Pair<Boolean, GameEntity> {
        val existing = gameDao.getByRommId(rom.id)
        if (existing != null) {
            if (existing.rommId != rom.id) {
                Log.e(TAG, "syncRom: BUG! getByRommId(${rom.id}) returned record with WRONG rommId=${existing.rommId}!")
            }
            Log.d(TAG, "syncRom: FOUND existing for rommId=${rom.id}: dbId=${existing.id}, dbRommId=${existing.rommId}, platform=${existing.platformId}")
        }

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

        val game = GameEntity(
            id = existing?.id ?: 0,
            platformId = platformSlug,
            title = rom.name,
            sortTitle = createSortTitle(rom.name),
            localPath = existing?.localPath,
            rommId = rom.id,
            igdbId = rom.igdbId,
            source = if (existing?.localPath != null) GameSource.ROMM_SYNCED else GameSource.ROMM_REMOTE,
            coverPath = rom.coverLarge?.let { buildMediaUrl(it) },
            backgroundPath = cachedBackground,
            screenshotPaths = screenshotUrls.joinToString(","),
            description = rom.summary,
            releaseYear = rom.firstReleaseDateMillis?.let {
                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).year
            },
            genre = rom.genres?.firstOrNull(),
            developer = rom.companies?.firstOrNull(),
            regions = rom.regions?.joinToString(","),
            languages = rom.languages?.joinToString(","),
            gameModes = rom.metadatum?.gameModes?.joinToString(","),
            franchises = rom.metadatum?.franchises?.joinToString(","),
            userRating = rom.romUser?.rating ?: existing?.userRating ?: 0,
            userDifficulty = rom.romUser?.difficulty ?: existing?.userDifficulty ?: 0,
            isFavorite = existing?.isFavorite ?: false,
            isHidden = existing?.isHidden ?: false,
            playCount = existing?.playCount ?: 0,
            playTimeMinutes = existing?.playTimeMinutes ?: 0,
            lastPlayed = existing?.lastPlayed,
            addedAt = existing?.addedAt ?: java.time.Instant.now()
        )

        val isNew = existing == null
        try {
            val insertedId = gameDao.insert(game)
            if (insertedId == -1L) {
                Log.e(TAG, "syncRom: INSERT FAILED for ${rom.name} (rommId=${rom.id}, igdbId=${rom.igdbId})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncRom: DB ERROR for ${rom.name}: ${e.message}", e)
            throw e
        }
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
        if (!passesRegionFilter(rom, filters)) return false
        if (!passesRevisionFilter(rom, filters)) return false
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

        if (filters.excludeBeta) {
            if (revision.contains("beta") || name.contains("(beta)")) return false
        }
        if (filters.excludePrototype) {
            if (revision.contains("proto") || name.contains("(proto)")) return false
        }
        if (filters.excludeDemo) {
            if (revision.contains("demo") || name.contains("(demo)") || name.contains("(sample)")) return false
        }

        return true
    }

    private suspend fun deleteOrphanedGames(seenRommIds: Set<Long>): Int {
        val remoteOnlyGames = gameDao.getBySource(GameSource.ROMM_REMOTE)
        var deleted = 0

        for (game in remoteOnlyGames) {
            val rommId = game.rommId ?: continue
            if (rommId !in seenRommIds && game.localPath == null) {
                gameDao.delete(game.id)
                deleted++
                Log.d(TAG, "deleteOrphanedGames: deleted orphan ${game.title} (rommId=$rommId)")
            }
        }

        Log.d(TAG, "deleteOrphanedGames: removed $deleted orphaned games")
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

    suspend fun downloadRom(romId: Long, fileName: String): RomMResult<okhttp3.ResponseBody> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            Log.d(TAG, "downloadRom: starting download for romId=$romId, fileName=$fileName")
            val response = currentApi.downloadRom(romId, fileName)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Log.d(TAG, "downloadRom: success, size=${body.contentLength()}")
                    RomMResult.Success(body)
                } else {
                    RomMResult.Error("Empty response body")
                }
            } else {
                Log.e(TAG, "downloadRom: failed with code=${response.code()}")
                RomMResult.Error("Download failed", response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadRom: exception", e)
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
        val rommId = game.rommId ?: return RomMResult.Error("Not a RomM game")

        gameDao.updateUserRating(gameId, rating)
        pendingSyncDao.deleteByGameAndType(gameId, "RATING")

        val currentApi = api
        if (currentApi == null || _connectionState.value !is ConnectionState.Connected) {
            pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "RATING", value = rating))
            return RomMResult.Success(Unit)
        }

        return try {
            val response = currentApi.updateRomUserProps(rommId, RomMUserPropsUpdate(rating = rating))
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
        val rommId = game.rommId ?: return RomMResult.Error("Not a RomM game")

        gameDao.updateUserDifficulty(gameId, difficulty)
        pendingSyncDao.deleteByGameAndType(gameId, "DIFFICULTY")

        val currentApi = api
        if (currentApi == null || _connectionState.value !is ConnectionState.Connected) {
            pendingSyncDao.insert(PendingSyncEntity(gameId = gameId, rommId = rommId, syncType = "DIFFICULTY", value = difficulty))
            return RomMResult.Success(Unit)
        }

        return try {
            val response = currentApi.updateRomUserProps(rommId, RomMUserPropsUpdate(difficulty = difficulty))
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
                    "RATING" -> RomMUserPropsUpdate(rating = item.value)
                    "DIFFICULTY" -> RomMUserPropsUpdate(difficulty = item.value)
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
            } catch (e: Exception) {
                Log.d(TAG, "processPendingSync: failed to sync item ${item.id}: ${e.message}")
            }
        }
        return synced
    }
}
