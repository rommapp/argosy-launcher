package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.MediaLibraryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Every read is scoped to a media account. Library visibility is the server's decision per user, so
 * an unscoped read would show one account the libraries another is entitled to.
 */
@Dao
interface MediaLibraryDao {

    @Query(
        "SELECT * FROM media_libraries WHERE ownerUserId = :ownerUserId " +
            "ORDER BY displayOrder ASC, name ASC"
    )
    fun observeLibraries(ownerUserId: String): Flow<List<MediaLibraryEntity>>

    @Query(
        "SELECT * FROM media_libraries WHERE ownerUserId = :ownerUserId " +
            "ORDER BY displayOrder ASC, name ASC"
    )
    suspend fun getLibraries(ownerUserId: String): List<MediaLibraryEntity>

    @Query("SELECT * FROM media_libraries WHERE ownerUserId = :ownerUserId AND libraryId = :libraryId LIMIT 1")
    suspend fun getByLibraryId(ownerUserId: String, libraryId: String): MediaLibraryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(library: MediaLibraryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(libraries: List<MediaLibraryEntity>)

    @Query(
        "UPDATE media_libraries SET itemCount = :itemCount, lastSyncedAt = :syncedAt " +
            "WHERE ownerUserId = :ownerUserId AND libraryId = :libraryId"
    )
    suspend fun markSynced(ownerUserId: String, libraryId: String, itemCount: Int, syncedAt: Instant)

    /**
     * Drops libraries the account can no longer see. Scoped to the acting account so a sync for one
     * user never removes another user's rows, and it removes only what the server did not list.
     */
    @Query("DELETE FROM media_libraries WHERE ownerUserId = :ownerUserId AND libraryId NOT IN (:libraryIds)")
    suspend fun deleteMissing(ownerUserId: String, libraryIds: List<String>)

    @Query("DELETE FROM media_libraries WHERE ownerUserId = :ownerUserId AND libraryId = :libraryId")
    suspend fun deleteByLibraryId(ownerUserId: String, libraryId: String)

    @Query("DELETE FROM media_libraries WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)

    @Query("DELETE FROM media_libraries WHERE ownerUserId != :ownerUserId")
    suspend fun deleteOtherOwners(ownerUserId: String)

    @Query("SELECT COUNT(*) FROM media_libraries WHERE ownerUserId != :ownerUserId")
    suspend fun countOtherOwners(ownerUserId: String): Int
}
