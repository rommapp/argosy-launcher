package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.SyncCoordinator
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMCollectionSyncService"
private const val FAVORITES_CHECK_DEBOUNCE_SECONDS = 30L
private const val FAVORITES_COLLECTION_NAME = "Favorites"

@Singleton
class RomMCollectionSyncService @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val gameDao: GameDao,
    private val collectionDao: CollectionDao,
    private val syncCoordinator: dagger.Lazy<SyncCoordinator>
) {
    private val api: RomMApi? get() = connectionManager.getApi()

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
        @Suppress("UNUSED_VARIABLE")
        val currentApi = api ?: return RomMResult.Error("Not connected")
        if (!connectionManager.isConnected()) {
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
        @Suppress("UNUSED_VARIABLE")
        val currentApi = api ?: return false
        if (!connectionManager.isConnected()) return false

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
        @Suppress("UNUSED_VARIABLE")
        val currentApi = api ?: return RomMResult.Error("Not connected")
        if (!connectionManager.isConnected()) {
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

    suspend fun syncCollections(): RomMResult<Unit> = withContext(Dispatchers.IO) {
        Logger.info(TAG, "syncCollections: starting")
        val currentApi = api ?: run {
            Logger.info(TAG, "syncCollections: not connected (no api)")
            return@withContext RomMResult.Error("Not connected")
        }
        if (!connectionManager.isConnected()) {
            Logger.info(TAG, "syncCollections: not connected (state=${connectionManager.connectionState.value})")
            return@withContext RomMResult.Error("Not connected")
        }

        try {
            Logger.info(TAG, "syncCollections: fetching local collections")
            val localCollections = collectionDao.getAllCollections()
            Logger.info(TAG, "syncCollections: found ${localCollections.size} local collections")

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
        if (currentApi == null || !connectionManager.isConnected()) {
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
        if (currentApi == null || rommId == null || !connectionManager.isConnected()) {
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
        if (currentApi == null || rommId == null || !connectionManager.isConnected()) {
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
        if (currentApi == null || rommId == null || !connectionManager.isConnected()) {
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
        if (currentApi == null || rommId == null || !connectionManager.isConnected()) {
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
