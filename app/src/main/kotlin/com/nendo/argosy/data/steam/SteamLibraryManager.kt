package com.nendo.argosy.data.steam

import android.util.Log
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SteamRepository
import com.nendo.argosy.data.repository.SteamResult
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.SteamAccountDao
import com.nendo.argosy.data.local.dao.CachedLicenseDao
import com.nendo.argosy.data.local.dao.SteamLicenseDao
import com.nendo.argosy.data.local.entity.CachedLicenseEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.SteamLicenseEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSProductInfoCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SteamLibraryManager"
private const val STEAM_PLATFORM_SLUG = "steam"

sealed class LibrarySyncState {
    data object Idle : LibrarySyncState()
    data object SyncingLicenses : LibrarySyncState()
    data class FetchingPackages(val current: Int, val total: Int) : LibrarySyncState()
    data class FetchingApps(val current: Int, val total: Int) : LibrarySyncState()
    data class FetchingProtonDbRatings(val current: Int, val total: Int) : LibrarySyncState()
    data class Complete(val gamesAdded: Int, val gamesUpdated: Int) : LibrarySyncState()
    data class Error(val message: String) : LibrarySyncState()
}

private enum class SyncPhase {
    IDLE,
    PACKAGES,
    APPS
}

@Singleton
class SteamLibraryManager @Inject constructor(
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val steamAccountDao: SteamAccountDao,
    private val steamLicenseDao: SteamLicenseDao,
    private val cachedLicenseDao: CachedLicenseDao,
    private val imageCacheManager: ImageCacheManager,
    private val drmHazardManager: DrmHazardManager,
    private val steamRepository: dagger.Lazy<SteamRepository>,
    private val preferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private var steamApps: SteamApps? = null
    private var callbackManager: CallbackManager? = null
    private val subscriptions = mutableListOf<Closeable>()

    private val _syncState = MutableStateFlow<LibrarySyncState>(LibrarySyncState.Idle)
    val syncState: StateFlow<LibrarySyncState> = _syncState.asStateFlow()

    private var syncPhase = SyncPhase.IDLE
    private val pendingPackageIds = mutableSetOf<Int>()
    private val pendingAppIds = mutableSetOf<Int>()
    private val packageToAppIds = mutableMapOf<Int, List<Int>>()
    private var gamesAdded = 0
    private var gamesUpdated = 0
    private var currentAccountId: Long? = null
    @Volatile
    private var cachedLicenses: List<License> = emptyList()
    @Volatile
    private var lastFullSyncAt: Instant? = null
    @Volatile
    private var forceSyncRequested = false

    private companion object {
        val SYNC_COOLDOWN: java.time.Duration = java.time.Duration.ofHours(24)
    }

    fun initialize(apps: SteamApps, cm: CallbackManager) {
        steamApps = apps
        callbackManager = cm
        registerCallbacks(cm)
        Log.d(TAG, "SteamLibraryManager initialized")
    }

    private fun registerCallbacks(cm: CallbackManager) {
        subscriptions.forEach { it.close() }
        subscriptions.clear()

        subscriptions += cm.subscribe(LicenseListCallback::class.java) { callback ->
            Log.d(TAG, "Received license list: ${callback.licenseList.size} licenses")
            scope.launch {
                handleLicenseList(callback)
            }
        }

        subscriptions += cm.subscribe(PICSProductInfoCallback::class.java) { callback ->
            scope.launch {
                handleProductInfo(callback)
            }
        }
    }

    fun forceSync() {
        forceSyncRequested = true
        scope.launch {
            val licenses = cachedLicenses
            if (licenses.isEmpty()) {
                Log.w(TAG, "Force sync: no cached licenses available, need reconnect")
                return@launch
            }
            Log.d(TAG, "Force sync: re-processing ${licenses.size} licenses")
            startSyncFromLicenses(licenses)
        }
    }

    private fun shouldSkipSync(): Boolean {
        if (forceSyncRequested) {
            forceSyncRequested = false
            return false
        }
        val lastSync = lastFullSyncAt ?: return false
        val elapsed = java.time.Duration.between(lastSync, Instant.now())
        if (elapsed < SYNC_COOLDOWN) {
            Log.d(TAG, "Skipping library sync (last sync ${elapsed.toMinutes()}m ago)")
            return true
        }
        return false
    }

    private suspend fun handleLicenseList(callback: LicenseListCallback) {
        val licenses = callback.licenseList
        cachedLicenses = licenses.toList()
        persistLicensesToDb(licenses)

        if (shouldSkipSync()) {
            return
        }

        startSyncFromLicenses(licenses)
    }

    private suspend fun startSyncFromLicenses(licenses: List<License>) {
        syncMutex.withLock {
            _syncState.value = LibrarySyncState.SyncingLicenses

            val account = steamAccountDao.getAnyAccount()
            if (account == null) {
                Log.e(TAG, "No saved account for license storage")
                _syncState.value = LibrarySyncState.Error("No active account")
                return
            }
            currentAccountId = account.id

            val packageIds = licenses.map { it.packageID }.filter { it != 0 }

            Log.d(TAG, "Processing ${packageIds.size} packages (excluding package 0)")

            if (packageIds.isEmpty()) {
                _syncState.value = LibrarySyncState.Complete(0, 0)
                return
            }

            pendingPackageIds.clear()
            pendingPackageIds.addAll(packageIds)
            pendingAppIds.clear()
            packageToAppIds.clear()
            gamesAdded = 0
            gamesUpdated = 0
            syncPhase = SyncPhase.PACKAGES

            requestPackageInfo(packageIds)
        }
    }

    private suspend fun requestPackageInfo(packageIds: List<Int>) {
        val apps = steamApps ?: return

        _syncState.value = LibrarySyncState.FetchingPackages(0, packageIds.size)

        val batchSize = 100
        val batches = packageIds.chunked(batchSize)
        batches.forEachIndexed { index, batch ->
            try {
                val packageRequests = batch.map { PICSRequest(it) }
                apps.picsGetProductInfo(emptyList(), packageRequests)
                Log.d(TAG, "Requested PICS info for ${batch.size} packages")
                if (index < batches.size - 1) kotlinx.coroutines.delay(500)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request package PICS info", e)
            }
        }
    }

    private suspend fun handleProductInfo(callback: PICSProductInfoCallback) {
        when (syncPhase) {
            SyncPhase.PACKAGES -> handlePackageInfo(callback)
            SyncPhase.APPS -> handleAppInfo(callback)
            SyncPhase.IDLE -> {}
        }
    }

    private suspend fun handlePackageInfo(callback: PICSProductInfoCallback) {
        for ((packageId, packageInfo) in callback.packages) {
            try {
                val kv = packageInfo.keyValues
                val appIds = kv["appids"]?.children
                    ?.mapNotNull { it.asInteger() }
                    ?.filter { it > 0 }
                    ?: emptyList()

                if (appIds.isNotEmpty()) {
                    packageToAppIds[packageId] = appIds
                    pendingAppIds.addAll(appIds)
                }

                pendingPackageIds.remove(packageId)

                val processed = packageToAppIds.size
                val total = processed + pendingPackageIds.size
                _syncState.value = LibrarySyncState.FetchingPackages(processed, total)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process package $packageId", e)
                pendingPackageIds.remove(packageId)
            }
        }

        if (pendingPackageIds.isEmpty()) {
            storeLicenses()
            if (pendingAppIds.isNotEmpty()) {
                syncPhase = SyncPhase.APPS
                requestAppInfo(pendingAppIds.toList())
            } else {
                syncPhase = SyncPhase.IDLE
                _syncState.value = LibrarySyncState.Complete(0, 0)
            }
        }
    }

    private suspend fun storeLicenses() {
        val accountId = currentAccountId ?: return

        for ((packageId, appIds) in packageToAppIds) {
            try {
                steamLicenseDao.insertOrUpdate(
                    SteamLicenseEntity(
                        accountId = accountId,
                        packageId = packageId,
                        appIds = appIds.joinToString(","),
                        licenseType = 0,
                        createdAt = Instant.now()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store license for package $packageId", e)
            }
        }

        Log.d(TAG, "Stored ${packageToAppIds.size} licenses with ${pendingAppIds.size} unique app IDs")
    }

    private suspend fun requestAppInfo(appIds: List<Int>) {
        val apps = steamApps ?: return

        Log.d(TAG, "Requesting app info for ${appIds.size} apps")
        _syncState.value = LibrarySyncState.FetchingApps(0, appIds.size)

        val batchSize = 50
        val batches = appIds.chunked(batchSize)
        batches.forEachIndexed { index, batch ->
            try {
                val appRequests = batch.map { PICSRequest(it) }
                apps.picsGetProductInfo(appRequests, emptyList())
                Log.d(TAG, "Requested PICS info for ${batch.size} apps")
                if (index < batches.size - 1) kotlinx.coroutines.delay(500)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request app PICS info", e)
            }
        }
    }

    private suspend fun handleAppInfo(callback: PICSProductInfoCallback) {
        ensureSteamPlatformExists()

        for ((appId, appInfo) in callback.apps) {
            try {
                val kv = appInfo.keyValues
                val common = kv["common"]
                val appType = common["type"]?.asString()?.lowercase()

                if (appType != "game") {
                    pendingAppIds.remove(appId)
                    continue
                }

                val name = common["name"]?.asString() ?: "Unknown Game"
                val existing = gameDao.getBySteamAppId(appId.toLong())

                if (existing != null) {
                    updateExistingGame(existing, kv)
                    gamesUpdated++
                } else {
                    createNewGame(appId, name, kv)
                    gamesAdded++
                }

                pendingAppIds.remove(appId)

                val processed = gamesAdded + gamesUpdated
                val total = processed + pendingAppIds.size
                _syncState.value = LibrarySyncState.FetchingApps(processed, total)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process app $appId", e)
                pendingAppIds.remove(appId)
            }
        }

        if (pendingAppIds.isEmpty()) {
            updatePlatformGameCount()
            syncPhase = SyncPhase.IDLE
            lastFullSyncAt = Instant.now()
            _syncState.value = LibrarySyncState.Complete(gamesAdded, gamesUpdated)
            Log.d(TAG, "Library sync complete: $gamesAdded added, $gamesUpdated updated")

            // Enrich new games with Store API data (descriptions, screenshots) in background
            if (gamesAdded > 0) {
                scope.launch { enrichNewGames() }
            }
        }
    }

    private suspend fun enrichNewGames() {
        val steamGames = gameDao.getBySource(GameSource.STEAM)
            .filter { it.description == null }
        Log.d(TAG, "Enriching ${steamGames.size} games with Store API data")

        val cacheScreenshots = preferencesRepository.userPreferences.first().syncScreenshotsEnabled

        for (game in steamGames) {
            val steamAppId = game.steamAppId ?: continue
            try {
                val result = steamRepository.get().enrichWithStoreData(steamAppId)
                if (cacheScreenshots && result is SteamResult.Success) {
                    val enrichedGame = result.data
                    val screenshotUrls = enrichedGame.screenshotPaths
                        ?.split(",")
                        ?.filter { it.startsWith("http") }
                        ?: emptyList()
                    if (screenshotUrls.isNotEmpty()) {
                        imageCacheManager.queueScreenshotCacheByGameId(enrichedGame.id, screenshotUrls)
                    }
                }
                kotlinx.coroutines.delay(1500)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enrich ${game.title}", e)
            }
        }
        Log.d(TAG, "Enrichment complete")
    }

    private suspend fun createNewGame(appId: Int, name: String, kv: KeyValue) {
        val common = kv["common"]

        val libraryCapsuleUrl = "https://steamcdn-a.akamaihd.net/steam/apps/$appId/library_600x900.jpg"
        val libraryHeroUrl = "https://steamcdn-a.akamaihd.net/steam/apps/$appId/library_hero.jpg"

        val releaseDate = common["steam_release_date"]?.asString()
        val releaseYear = parseReleaseYear(releaseDate)

        val genres = common["genres"]?.children
            ?.mapNotNull { it.asString() }
            ?.joinToString(", ")

        val game = GameEntity(
            platformId = LocalPlatformIds.STEAM,
            platformSlug = STEAM_PLATFORM_SLUG,
            title = name,
            sortTitle = createSortTitle(name),
            localPath = null,
            rommId = null,
            igdbId = null,
            steamAppId = appId.toLong(),
            steamLauncher = "native",
            source = GameSource.STEAM,
            coverPath = libraryCapsuleUrl,
            backgroundPath = libraryHeroUrl,
            developer = extractAssociation(common, "developer"),
            publisher = extractAssociation(common, "publisher"),
            releaseYear = releaseYear,
            genre = genres,
            description = null,
            addedAt = Instant.now()
        )

        val insertedId = gameDao.insert(game)
        imageCacheManager.queueCoverCacheByGameId(libraryCapsuleUrl, insertedId)
        Log.d(TAG, "Added game: $name (appId=$appId, dbId=$insertedId)")
    }

    private fun extractAssociation(common: KeyValue, type: String): String? {
        return common["associations"]?.children
            ?.find { it["type"]?.asString() == type }
            ?.get("name")?.asString()
    }

    private suspend fun updateExistingGame(existing: GameEntity, kv: KeyValue) {
        val updated = existing.copy(
            steamLauncher = existing.steamLauncher ?: "native"
        )

        if (updated != existing) {
            gameDao.update(updated)
            Log.d(TAG, "Updated game: ${existing.title}")
        }
    }

    fun requestLibrarySync() {
        if (syncPhase != SyncPhase.IDLE) {
            Log.d(TAG, "Sync already in progress")
            return
        }

        val apps = steamApps ?: run {
            _syncState.value = LibrarySyncState.Error("Not connected to Steam")
            return
        }

        Log.d(TAG, "Library sync will start when license list is received")
    }

    private suspend fun updatePlatformGameCount() {
        val count = gameDao.countByPlatform(LocalPlatformIds.STEAM)
        platformDao.updateGameCount(LocalPlatformIds.STEAM, count)
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

    private fun parseReleaseYear(dateString: String?): Int? {
        if (dateString.isNullOrBlank()) return null
        return try {
            val timestamp = dateString.toLongOrNull() ?: return null
            val instant = Instant.ofEpochSecond(timestamp)
            java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()).year
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun ensureSteamPlatformExists() {
        val existing = platformDao.getById(LocalPlatformIds.STEAM)
        if (existing == null) {
            Log.d(TAG, "Creating Steam platform")
            platformDao.insert(
                PlatformEntity(
                    id = LocalPlatformIds.STEAM,
                    slug = STEAM_PLATFORM_SLUG,
                    name = "Steam",
                    shortName = "Steam",
                    sortOrder = 130,
                    isVisible = true,
                    romExtensions = "",
                    gameCount = 0
                )
            )
        }
    }

    suspend fun getLicenses(): List<License> {
        if (cachedLicenses.isNotEmpty()) return cachedLicenses
        val fromDb = cachedLicenseDao.getAll().mapNotNull {
            LicenseSerializer.deserialize(it.licenseJson)
        }
        if (fromDb.isNotEmpty()) {
            cachedLicenses = fromDb
            Log.d(TAG, "Loaded ${fromDb.size} licenses from DB cache")
        }
        return cachedLicenses
    }

    private suspend fun persistLicensesToDb(licenses: List<License>) {
        try {
            cachedLicenseDao.deleteAll()
            val entities = licenses.mapNotNull { license ->
                val json = LicenseSerializer.serialize(license)
                if (json.isNotEmpty()) CachedLicenseEntity(licenseJson = json) else null
            }
            cachedLicenseDao.insertAll(entities)
            Log.d(TAG, "Persisted ${entities.size} licenses to DB cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist licenses: ${e.message}")
        }
    }

    fun cleanup() {
        subscriptions.forEach { it.close() }
        subscriptions.clear()
        steamApps = null
        callbackManager = null
        syncPhase = SyncPhase.IDLE
    }

    suspend fun resetLibrary(): Int {
        syncMutex.withLock {
            val steamGames = gameDao.getBySource(GameSource.STEAM)
            val count = steamGames.size

            for (game in steamGames) {
                gameDao.delete(game.id)
            }

            steamLicenseDao.deleteAll()

            platformDao.updateGameCount(LocalPlatformIds.STEAM, 0)

            Log.d(TAG, "Reset Steam library: deleted $count games")
            return count
        }
    }
}
