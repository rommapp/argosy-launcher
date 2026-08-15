package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Every read is scoped to a media account: an item row exists per account that can see it, so an
 * unscoped read mixes two users' libraries and their download state together.
 */
@Dao
interface MediaItemDao {

    @Query(
        "SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND libraryId = :libraryId " +
            "AND itemType = :itemType ORDER BY sortName ASC"
    )
    fun observeByLibrary(
        ownerUserId: String,
        libraryId: String,
        itemType: String
    ): Flow<List<MediaItemEntity>>

    @Query(
        "SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND libraryId = :libraryId " +
            "AND itemType = :itemType ORDER BY sortName ASC"
    )
    suspend fun getByLibrary(
        ownerUserId: String,
        libraryId: String,
        itemType: String
    ): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND itemId = :itemId LIMIT 1")
    suspend fun getByItemId(ownerUserId: String, itemId: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND itemId = :itemId LIMIT 1")
    fun observeByItemId(ownerUserId: String, itemId: String): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND itemId IN (:itemIds)")
    suspend fun getByItemIds(ownerUserId: String, itemIds: List<String>): List<MediaItemEntity>

    /**
     * The children one level below a series or a season, in the order the server numbers them. A
     * child whose number is missing sorts last rather than first, so an unnumbered special does not
     * displace episode one.
     */
    @Query(
        "SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND parentId = :parentId " +
            "ORDER BY indexNumber IS NULL, indexNumber ASC, sortName ASC"
    )
    fun observeByParent(ownerUserId: String, parentId: String): Flow<List<MediaItemEntity>>

    @Query(
        "SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND parentId = :parentId " +
            "ORDER BY indexNumber IS NULL, indexNumber ASC, sortName ASC"
    )
    suspend fun getByParent(ownerUserId: String, parentId: String): List<MediaItemEntity>

    @Query(
        "SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND seriesId = :seriesId " +
            "AND itemType = :itemType " +
            "ORDER BY parentIndexNumber IS NULL, parentIndexNumber ASC, " +
            "indexNumber IS NULL, indexNumber ASC"
    )
    suspend fun getBySeries(
        ownerUserId: String,
        seriesId: String,
        itemType: String
    ): List<MediaItemEntity>

    /**
     * The kinds to match are named by the caller rather than left open, because the limit is applied
     * by SQLite before any caller-side filtering could run: a query that matched enough episodes
     * would fill the limit with them and answer with none of the films it also matched.
     */
    @Query(
        "SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND name LIKE '%' || :query || '%' " +
            "AND itemType IN (:itemTypes) ORDER BY sortName ASC LIMIT :limit"
    )
    suspend fun search(
        ownerUserId: String,
        query: String,
        itemTypes: List<String>,
        limit: Int
    ): List<MediaItemEntity>

    @Query(
        "SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND localPath IS NOT NULL " +
            "ORDER BY downloadedAt DESC"
    )
    fun observeDownloaded(ownerUserId: String): Flow<List<MediaItemEntity>>

    @Query(
        "SELECT * FROM media_items WHERE ownerUserId = :ownerUserId AND localPath IS NOT NULL " +
            "ORDER BY downloadedAt DESC"
    )
    suspend fun getDownloaded(ownerUserId: String): List<MediaItemEntity>

    @Query(
        "SELECT COUNT(*) FROM media_items WHERE ownerUserId = :ownerUserId AND libraryId = :libraryId " +
            "AND itemType = :itemType"
    )
    suspend fun countByLibrary(ownerUserId: String, libraryId: String, itemType: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Query(
        "UPDATE media_items SET localPath = :localPath, downloadQuality = :quality, " +
            "downloadedBytes = :bytes, downloadedAt = :downloadedAt " +
            "WHERE ownerUserId = :ownerUserId AND itemId = :itemId"
    )
    suspend fun markDownloaded(
        ownerUserId: String,
        itemId: String,
        localPath: String,
        quality: String,
        bytes: Long,
        downloadedAt: Instant
    )

    /**
     * Forgets the downloaded copy without touching the item itself. For a file the user deleted or
     * a download that was replaced at another quality; a volume that is merely unplugged keeps its
     * path, since unreadable is not absent.
     */
    @Query(
        "UPDATE media_items SET localPath = NULL, downloadQuality = NULL, downloadedBytes = NULL, " +
            "downloadedAt = NULL WHERE ownerUserId = :ownerUserId AND itemId = :itemId"
    )
    suspend fun clearDownloaded(ownerUserId: String, itemId: String)

    @Query("UPDATE media_items SET localPath = :localPath WHERE ownerUserId = :ownerUserId AND itemId = :itemId")
    suspend fun updateLocalPath(ownerUserId: String, itemId: String, localPath: String)

    @Query("DELETE FROM media_items WHERE ownerUserId = :ownerUserId AND itemId IN (:itemIds)")
    suspend fun deleteByItemIds(ownerUserId: String, itemIds: List<String>)

    @Query("DELETE FROM media_items WHERE ownerUserId = :ownerUserId AND parentId = :parentId")
    suspend fun deleteByParent(ownerUserId: String, parentId: String)

    @Query("DELETE FROM media_items WHERE ownerUserId = :ownerUserId AND libraryId = :libraryId")
    suspend fun deleteByLibrary(ownerUserId: String, libraryId: String)

    @Query("DELETE FROM media_items WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)

    @Query("SELECT localPath FROM media_items WHERE ownerUserId != :ownerUserId AND localPath IS NOT NULL")
    suspend fun otherOwnerLocalPaths(ownerUserId: String): List<String>

    @Query("DELETE FROM media_items WHERE ownerUserId != :ownerUserId")
    suspend fun deleteOtherOwners(ownerUserId: String)
}
