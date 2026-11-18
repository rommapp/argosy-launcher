package com.nendo.argosy.data.steam

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.recordArtSource
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
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSProductInfoCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.types.AsyncJobMultiple
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dagger.hilt.android.qualifiers.ApplicationContext
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
    data class Complete(val gamesAdded: Int, val gamesUpdated: Int) : LibrarySyncState()

    /**
     * [messageRes] carries an authored, translatable sentence; it is null only when [message]
     * came from somewhere Argosy does not author (a server or SDK message), which is the one
     * case [message] is allowed to reach the screen untranslated.
     */
    data class Error(@StringRes val messageRes: Int? = null, val message: String) : LibrarySyncState()
}

private enum class SyncPhase {
    IDLE,
    PACKAGES,
    APPS
}

@Singleton
class SteamLibraryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val steamAccountDao: SteamAccountDao,
    private val steamLicenseDao: SteamLicenseDao,
    private val cachedLicenseDao: CachedLicenseDao,
    private val imageCacheManager: ImageCacheManager,
    private val steamRepository: dagger.Lazy<SteamRepository>,
    private val steamIgdbResolver: dagger.Lazy<com.nendo.argosy.data.repository.SteamIgdbResolver>,
    private val preferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    private val steamLibraryRepair: dagger.Lazy<SteamLibraryRepair>,
    private val gameArtDao: com.nendo.argosy.data.local.dao.GameArtDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private var steamApps: SteamApps? = null
    private var callbackManager: CallbackManager? = null
    private val subscriptions = mutableListOf<Closeable>()

    private val _syncState = MutableStateFlow<LibrarySyncState>(LibrarySyncState.Idle)
    val syncState: StateFlow<LibrarySyncState> = _syncState.asStateFlow()

    private val _settledSyncRuns = MutableStateFlow(0L)

    /**
     * Count of sync runs that have ended, whether they completed, failed, or were skipped
     * by the cooldown. A caller waiting for the run it triggered reads the value first and
     * then waits for it to grow.
     */
    val settledSyncRuns: StateFlow<Long> = _settledSyncRuns.asStateFlow()

    private var syncPhase = SyncPhase.IDLE
    private var picsFailures = 0
    private var phaseTotal = 0
    private val pendingPackageIds = mutableSetOf<Int>()
    private val pendingAppIds = mutableSetOf<Int>()
    private val packageToAppIds = mutableMapOf<Int, List<Int>>()
    private val packageAccessTokens = mutableMapOf<Int, Long>()
    private var gamesAdded = 0
    private var gamesUpdated = 0
    private var currentAccountId: Long? = null
    @Volatile
    private var cachedLicenses: List<License> = emptyList()
    @Volatile
    private var lastFullSyncAt: Instant? = null
    @Volatile
    private var forceSyncRequested = false
    @Volatile
    private var forceOverwriteRequested = false
    @Volatile
    private var wasForceSync = false
    @Volatile
    private var wasForceOverwrite = false
    private var enrichmentJob: kotlinx.coroutines.Job? = null

    private companion object {
        val SYNC_COOLDOWN: java.time.Duration = java.time.Duration.ofHours(24)
        const val PICS_REQUEST_TIMEOUT_MS = 60_000L
        const val PACKAGE_BATCH_SIZE = 100
        const val APP_BATCH_SIZE = 50
        const val BATCH_SPACING_MS = 500L
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
    }

    fun forceSyncWithOverwrite() {
        forceSyncRequested = true
        forceOverwriteRequested = true
        forceSync()
    }

    fun forceSync() {
        forceSyncRequested = true
        scope.launch {
            try {
                val licenses = getLicenses()
                if (licenses.isEmpty()) {
                    Log.w(TAG, "Force sync: no cached licenses available, need reconnect")
                    _syncState.value = LibrarySyncState.Error(
                        messageRes = R.string.notif_steam_sync_error_no_cached_licenses,
                        message = context.getString(R.string.notif_steam_sync_error_no_cached_licenses)
                    )
                    return@launch
                }
                Log.d(TAG, "Force sync: re-processing ${licenses.size} licenses")
                startSyncFromLicenses(licenses)
            } finally {
                _settledSyncRuns.update { it + 1 }
            }
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
        try {
            val licenses = callback.licenseList
            cachedLicenses = licenses.toList()
            persistLicensesToDb(licenses)

            if (shouldSkipSync()) {
                return
            }

            startSyncFromLicenses(licenses)
        } finally {
            _settledSyncRuns.update { it + 1 }
        }
    }

    private suspend fun startSyncFromLicenses(licenses: List<License>) {
        wasForceSync = forceSyncRequested
        wasForceOverwrite = forceOverwriteRequested
        forceOverwriteRequested = false
        syncMutex.withLock {
            _syncState.value = LibrarySyncState.SyncingLicenses

            val account = steamAccountDao.getAnyAccount()
            if (account == null) {
                Log.e(TAG, "No saved account for license storage")
                _syncState.value = LibrarySyncState.Error(
                    messageRes = R.string.notif_steam_sync_error_no_active_account,
                    message = context.getString(R.string.notif_steam_sync_error_no_active_account)
                )
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
            packageAccessTokens.clear()
            licenses.forEach { packageAccessTokens[it.packageID] = it.accessToken }
            gamesAdded = 0
            gamesUpdated = 0
            picsFailures = 0
            syncPhase = SyncPhase.PACKAGES

            requestPackageInfo(packageIds)
        }
    }

    private suspend fun requestPackageInfo(packageIds: List<Int>) {
        val apps = steamApps ?: return endSyncDisconnected()

        phaseTotal = packageIds.size
        _syncState.value = LibrarySyncState.FetchingPackages(0, phaseTotal)

        val batches = packageIds.chunked(PACKAGE_BATCH_SIZE)
        for ((index, batch) in batches.withIndex()) {
            if (steamApps !== apps) return endSyncDisconnected()
            val responses = fetchProductInfo("package", batch) {
                apps.picsGetProductInfo(emptyList(), batch.map { PICSRequest(it, packageAccessTokens[it] ?: 0L) })
            }
            responses.forEach { handlePackageInfo(it) }
            skipUnresolved("package", batch, responses.flatMap { it.unknownPackages }, pendingPackageIds)
            if (index < batches.lastIndex) delay(BATCH_SPACING_MS)
        }

        finishPackagePhase()
    }

    private suspend fun fetchProductInfo(
        kind: String,
        batch: List<Int>,
        request: () -> AsyncJobMultiple<PICSProductInfoCallback>
    ): List<PICSProductInfoCallback> = try {
        val resultSet = withTimeout(PICS_REQUEST_TIMEOUT_MS) { request().toFuture().await() }
        if (resultSet.failed || !resultSet.complete) {
            picsFailures++
            Log.w(TAG, "PICS $kind request for ${batch.size} ids ended incomplete")
        }
        resultSet.results.filterIsInstance<PICSProductInfoCallback>()
    } catch (e: TimeoutCancellationException) {
        picsFailures++
        Log.e(TAG, "PICS $kind request for ${batch.size} ids timed out after ${PICS_REQUEST_TIMEOUT_MS}ms")
        emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        picsFailures++
        Log.e(TAG, "PICS $kind request for ${batch.size} ids failed", e)
        emptyList()
    }

    private suspend fun fetchAppAccessTokens(apps: SteamApps, batch: List<Int>): Map<Int, Long> = try {
        withTimeout(PICS_REQUEST_TIMEOUT_MS) {
            apps.picsGetAccessTokens(batch, emptyList()).toFuture().await().appTokens
        }
    } catch (e: CancellationException) {
        if (e is TimeoutCancellationException) {
            Log.w(TAG, "PICS access tokens for ${batch.size} apps timed out")
            emptyMap()
        } else {
            throw e
        }
    } catch (e: Exception) {
        Log.w(TAG, "PICS access tokens for ${batch.size} apps failed", e)
        emptyMap()
    }

    private fun skipUnresolved(kind: String, batch: List<Int>, unknownIds: List<Int>, pending: MutableSet<Int>) {
        val unknown = unknownIds.filter { it in pending }
        if (unknown.isNotEmpty()) {
            pending.removeAll(unknown.toSet())
            Log.d(TAG, "Steam reports ${unknown.size} unknown $kind ids, skipping: $unknown")
        }
        val unanswered = batch.filter { it in pending }
        if (unanswered.isNotEmpty()) {
            pending.removeAll(unanswered.toSet())
            Log.w(TAG, "No PICS answer for ${unanswered.size} $kind ids, skipping them this run")
        }
    }

    private fun endSyncDisconnected() {
        Log.w(TAG, "Steam connection gone mid-sync, ending library sync")
        syncPhase = SyncPhase.IDLE
        _syncState.value = LibrarySyncState.Error(
            messageRes = R.string.notif_steam_sync_error_not_connected,
            message = context.getString(R.string.notif_steam_sync_error_not_connected)
        )
    }

    private suspend fun finishPackagePhase() {
        storeLicenses()
        if (pendingAppIds.isEmpty()) {
            syncPhase = SyncPhase.IDLE
            _syncState.value = LibrarySyncState.Complete(0, 0)
            return
        }
        syncPhase = SyncPhase.APPS
        requestAppInfo(pendingAppIds.toList())
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
                _syncState.value = LibrarySyncState.FetchingPackages(phaseTotal - pendingPackageIds.size, phaseTotal)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process package $packageId", e)
                pendingPackageIds.remove(packageId)
            }
        }
    }

    private suspend fun storeLicenses() {
        val accountId = currentAccountId ?: return

        val snapshot = packageToAppIds.toMap()
        for ((packageId, appIds) in snapshot) {
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

        Log.d(TAG, "Stored ${snapshot.size} licenses with ${pendingAppIds.size} unique app IDs")
    }

    private suspend fun requestAppInfo(appIds: List<Int>) {
        val apps = steamApps ?: return endSyncDisconnected()

        Log.d(TAG, "Requesting app info for ${appIds.size} apps")
        phaseTotal = appIds.size
        _syncState.value = LibrarySyncState.FetchingApps(0, phaseTotal)

        val batches = appIds.chunked(APP_BATCH_SIZE)
        for ((index, batch) in batches.withIndex()) {
            if (steamApps !== apps) return endSyncDisconnected()
            val tokens = fetchAppAccessTokens(apps, batch)
            val responses = fetchProductInfo("app", batch) {
                apps.picsGetProductInfo(batch.map { PICSRequest(it, tokens[it] ?: 0L) }, emptyList())
            }
            responses.forEach { handleAppInfo(it) }
            skipUnresolved("app", batch, responses.flatMap { it.unknownApps }, pendingAppIds)
            if (index < batches.lastIndex) delay(BATCH_SPACING_MS)
        }

        finishAppPhase()
    }

    private suspend fun handleAppInfo(callback: PICSProductInfoCallback) {
        ensureSteamPlatformExists()

        for ((appId, appInfo) in callback.apps) {
            try {
                val kv = appInfo.keyValues
                val common = kv["common"]
                val appType = common["type"]?.asString()?.lowercase()

                if (appType != "game") {
                    Log.d(TAG, "Skipping app $appId type=${appType ?: "none"} name=${common["name"]?.asString()}")
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
                _syncState.value = LibrarySyncState.FetchingApps(phaseTotal - pendingAppIds.size, phaseTotal)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process app $appId", e)
                pendingAppIds.remove(appId)
            }
        }
    }

    private suspend fun finishAppPhase() {
        updatePlatformGameCount()
        syncPhase = SyncPhase.IDLE
        if (picsFailures == 0) {
            lastFullSyncAt = Instant.now()
        } else {
            Log.w(TAG, "$picsFailures PICS batches failed, next license list will sync again")
        }
        _syncState.value = LibrarySyncState.Complete(gamesAdded, gamesUpdated)
        Log.d(TAG, "Library sync complete: $gamesAdded added, $gamesUpdated updated")

        if (wasForceSync) {
            gameDao.resetFailedStoreEnrichment()
        }

        val existing = enrichmentJob
        if (existing != null && existing.isActive) {
            Log.d(TAG, "Enrichment already in progress, skipping")
        } else {
            enrichmentJob = scope.launch {
                steamLibraryRepair.get().restoreSources()
                steamLibraryRepair.get().repairCovers()
                enrichIncompleteGames()
                steamIgdbResolver.get().requestResolutionForUnresolved(force = wasForceSync)
            }
        }
    }

    private suspend fun enrichIncompleteGames() {
        val steamGames = gameDao.getBySource(GameSource.STEAM)
            .filter { it.storeEnrichStatus == GameEntity.STORE_NOT_ATTEMPTED }
        if (steamGames.isEmpty()) {
            Log.d(TAG, "No games need enrichment")
            return
        }
        Log.d(TAG, "Enriching ${steamGames.size} incomplete games with Store API data")

        val cacheScreenshots = preferencesRepository.userPreferences.first().syncScreenshotsEnabled

        for (game in steamGames) {
            val steamAppId = game.steamAppId ?: continue
            try {
                val result = steamRepository.get().enrichWithStoreData(steamAppId)
                if (cacheScreenshots && result is SteamResult.Success) {
                    imageCacheManager.queueScreenshotCacheByGameId(result.data.id)
                }
                if (result is SteamResult.Success) {
                    gameDao.updateStoreEnrichStatus(game.id, GameEntity.STORE_SUCCESS)
                } else {
                    gameDao.updateStoreEnrichStatus(game.id, GameEntity.STORE_FAILED)
                }
                kotlinx.coroutines.delay(1500)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enrich ${game.title}", e)
                gameDao.updateStoreEnrichStatus(game.id, GameEntity.STORE_FAILED)
            }
        }
        Log.d(TAG, "Enrichment complete")
    }

    private suspend fun createNewGame(appId: Int, name: String, kv: KeyValue) {
        val common = kv["common"]
        val installDir = kv["config"]?.get("installdir")?.asString()?.takeIf { it.isNotBlank() }

        val libraryCapsuleUrl = "https://steamcdn-a.akamaihd.net/steam/apps/$appId/library_600x900.jpg"
        val libraryHeroUrl = "https://steamcdn-a.akamaihd.net/steam/apps/$appId/library_hero.jpg"

        val releaseDate = common["steam_release_date"]?.asString()
        val releaseYear = parseReleaseYear(releaseDate)

        val genres = common["genres"]?.children
            ?.mapNotNull { it.asString() }
            ?.map { STEAM_GENRE_MAP[it] ?: it }
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
            steamInstallDir = installDir,
            source = GameSource.STEAM,
            developer = extractAssociation(common, "developer"),
            publisher = extractAssociation(common, "publisher"),
            releaseYear = releaseYear,
            genre = genres,
            description = null,
            addedAt = Instant.now()
        )

        val insertedId = gameDao.insert(game)
        writeSteamArt(insertedId, appId.toLong(), name, libraryCapsuleUrl, libraryHeroUrl)
        Log.d(TAG, "Added game: $name (appId=$appId, dbId=$insertedId)")
    }

    private suspend fun writeSteamArt(gameId: Long, appId: Long, title: String, cover: String, hero: String) {
        recordArtSource(gameArtDao, imageCacheManager, gameId, ArtSlot.COVER, listOf(cover), title, steamAppId = appId)
        recordArtSource(gameArtDao, imageCacheManager, gameId, ArtSlot.BACKGROUND, listOf(hero), title, steamAppId = appId)
    }

    private fun extractAssociation(common: KeyValue, type: String): String? {
        return common["associations"]?.children
            ?.find { it["type"]?.asString() == type }
            ?.get("name")?.asString()
    }

    internal suspend fun updateExistingGame(existing: GameEntity, kv: KeyValue) {
        val common = kv["common"]
        val installDir = kv["config"]?.get("installdir")?.asString()?.takeIf { it.isNotBlank() }

        if (wasForceOverwrite) {
            val appId = existing.steamAppId ?: return
            val name = common["name"]?.asString()
            val libraryCapsuleUrl = "https://steamcdn-a.akamaihd.net/steam/apps/$appId/library_600x900.jpg"
            val libraryHeroUrl = "https://steamcdn-a.akamaihd.net/steam/apps/$appId/library_hero.jpg"
            val releaseDate = common["steam_release_date"]?.asString()
            val releaseYear = parseReleaseYear(releaseDate)
            val genres = common["genres"]?.children
                ?.mapNotNull { it.asString() }
                ?.map { STEAM_GENRE_MAP[it] ?: it }
                ?.joinToString(", ")

            writeSteamArt(existing.id, appId, name ?: existing.title, libraryCapsuleUrl, libraryHeroUrl)
            gameDao.updateSteamLibraryMetadata(
                gameId = existing.id,
                title = name,
                sortTitle = name?.let(::createSortTitle),
                developer = extractAssociation(common, "developer"),
                publisher = extractAssociation(common, "publisher"),
                releaseYear = releaseYear,
                genre = genres,
                installDir = installDir
            )
        } else {
            gameDao.updateSteamInstallInfo(existing.id, installDir)
        }

        Log.d(TAG, "Updated game: ${existing.title}")
    }

    fun requestLibrarySync() {
        if (syncPhase != SyncPhase.IDLE) {
            Log.d(TAG, "Sync already in progress")
            return
        }

        val apps = steamApps ?: run {
            _syncState.value = LibrarySyncState.Error(
                messageRes = R.string.notif_steam_sync_error_not_connected,
                message = context.getString(R.string.notif_steam_sync_error_not_connected)
            )
            return
        }

        Log.d(TAG, "Library sync will start when license list is received")
    }

    private suspend fun updatePlatformGameCount() {
        val count = gameDao.countByPlatform(
            LocalPlatformIds.STEAM,
            syncPreferencesRepository.getRommUserId()
        )
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
                    sortOrder = 2,
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
