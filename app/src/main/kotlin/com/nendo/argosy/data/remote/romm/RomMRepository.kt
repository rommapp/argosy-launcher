package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RomMRepository @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val apiClient: RomMApiClient,
    private val librarySyncService: RomMLibrarySyncService,
    private val collectionSyncService: RomMCollectionSyncService,
    private val userPropertyService: RomMUserPropertyService,
    private val achievementService: RomMAchievementService,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    val syncProgress: StateFlow<SyncProgress> get() = librarySyncService.syncProgress

    val connectionState: StateFlow<ConnectionState> get() = connectionManager.connectionState

    fun isConnected(): Boolean = connectionManager.isConnected()

    fun isVersionAtLeast(minVersion: String): Boolean = connectionManager.isVersionAtLeast(minVersion)

    // --- Connection ---

    suspend fun initialize() {
        connectionManager.initialize()
        if (connectionManager.isConnected()) {
            val prefs = userPreferencesRepository.preferences.first()
            if (prefs.rommToken != null) {
                achievementService.refreshRAProgressionOnStartup()
            }
        }
        librarySyncService.populateVirtualCollectionsIfNeeded()
    }

    fun onAppResumed() {
        achievementService.onAppResumed()
    }

    suspend fun connect(url: String, token: String? = null): RomMResult<String> =
        connectionManager.connect(url, token)

    suspend fun login(username: String, password: String): RomMResult<String> =
        connectionManager.login(username, password)

    fun disconnect() = connectionManager.disconnect()

    suspend fun checkConnection(retryCount: Int = 2) = connectionManager.checkConnection(retryCount)

    // --- API Client ---

    fun buildMediaUrlPublic(path: String): String = apiClient.buildMediaUrl(path)

    suspend fun getRom(romId: Long): RomMResult<RomMRom> = apiClient.getRom(romId)

    suspend fun downloadRom(
        romId: Long,
        fileName: String,
        rangeHeader: String? = null
    ): RomMResult<DownloadResponse> = apiClient.downloadRom(romId, fileName, rangeHeader)

    suspend fun getCurrentUser(): RomMResult<RomMUser> = apiClient.getCurrentUser()

    suspend fun getLibrarySummary(): RomMResult<Pair<Int, Int>> = apiClient.getLibrarySummary()

    suspend fun fetchAndStorePlatforms(
        defaultSyncEnabled: Boolean = true
    ): RomMResult<List<PlatformEntity>> = apiClient.fetchAndStorePlatforms(defaultSyncEnabled)

    suspend fun updateRomUserProps(
        rommId: Long,
        userRating: Int? = null,
        userDifficulty: Int? = null,
        userStatus: String? = null
    ): Boolean = apiClient.updateRomUserProps(rommId, userRating, userDifficulty, userStatus)

    // --- Library Sync ---

    suspend fun syncLibrary(
        onProgress: ((current: Int, total: Int, platformName: String) -> Unit)? = null
    ): SyncResult = librarySyncService.syncLibrary(onProgress)

    suspend fun syncPlatform(platformId: Long): SyncResult = librarySyncService.syncPlatform(platformId)

    suspend fun syncPlatformsOnly(): Result<Int> = librarySyncService.syncPlatformsOnly()

    // --- Collections ---

    suspend fun syncCollections(): RomMResult<Unit> = collectionSyncService.syncCollections()

    suspend fun syncFavorites(): RomMResult<Unit> = collectionSyncService.syncFavorites()

    suspend fun refreshFavoritesIfNeeded(): RomMResult<Unit> = collectionSyncService.refreshFavoritesIfNeeded()

    suspend fun toggleFavoriteWithSync(gameId: Long, rommId: Long, isFavorite: Boolean): RomMResult<Unit> =
        collectionSyncService.toggleFavoriteWithSync(gameId, rommId, isFavorite)

    suspend fun syncFavorite(rommId: Long, isFavorite: Boolean): Boolean =
        collectionSyncService.syncFavorite(rommId, isFavorite)

    suspend fun createCollectionWithSync(name: String, description: String? = null): RomMResult<Long> =
        collectionSyncService.createCollectionWithSync(name, description)

    suspend fun updateCollectionWithSync(collectionId: Long, name: String, description: String?): RomMResult<Unit> =
        collectionSyncService.updateCollectionWithSync(collectionId, name, description)

    suspend fun deleteCollectionWithSync(collectionId: Long): RomMResult<Unit> =
        collectionSyncService.deleteCollectionWithSync(collectionId)

    suspend fun addGameToCollectionWithSync(gameId: Long, collectionId: Long): RomMResult<Unit> =
        collectionSyncService.addGameToCollectionWithSync(gameId, collectionId)

    suspend fun removeGameFromCollectionWithSync(gameId: Long, collectionId: Long): RomMResult<Unit> =
        collectionSyncService.removeGameFromCollectionWithSync(gameId, collectionId)

    // --- User Properties ---

    suspend fun updateUserRating(gameId: Long, rating: Int): RomMResult<Unit> =
        userPropertyService.updateUserRating(gameId, rating)

    suspend fun updateUserDifficulty(gameId: Long, difficulty: Int): RomMResult<Unit> =
        userPropertyService.updateUserDifficulty(gameId, difficulty)

    suspend fun updateUserStatus(gameId: Long, status: String?): RomMResult<Unit> =
        userPropertyService.updateUserStatus(gameId, status)

    suspend fun refreshUserProps(gameId: Long): RomMResult<Unit> =
        userPropertyService.refreshUserProps(gameId)

    suspend fun refreshGameData(gameId: Long): RomMResult<Unit> =
        userPropertyService.refreshGameData(gameId)

    // --- Achievements ---

    fun getEarnedBadgeIds(raGameId: Long): Set<String> = achievementService.getEarnedBadgeIds(raGameId)

    fun getEarnedAchievements(raGameId: Long): List<RomMEarnedAchievement> =
        achievementService.getEarnedAchievements(raGameId)

    suspend fun refreshRAProgressionIfNeeded(): RomMResult<Unit> =
        achievementService.refreshRAProgressionIfNeeded()
}
