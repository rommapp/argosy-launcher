package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.MediaStreamEntity
import kotlinx.coroutines.flow.Flow

/**
 * Every read is scoped to a media account, matching the items the tracks belong to.
 */
@Dao
interface MediaStreamDao {

    @Query(
        "SELECT * FROM media_streams WHERE ownerUserId = :ownerUserId AND itemId = :itemId " +
            "ORDER BY mediaSourceId ASC, streamIndex ASC"
    )
    suspend fun getByItem(ownerUserId: String, itemId: String): List<MediaStreamEntity>

    @Query(
        "SELECT * FROM media_streams WHERE ownerUserId = :ownerUserId AND itemId = :itemId " +
            "ORDER BY mediaSourceId ASC, streamIndex ASC"
    )
    fun observeByItem(ownerUserId: String, itemId: String): Flow<List<MediaStreamEntity>>

    @Query(
        "SELECT * FROM media_streams WHERE ownerUserId = :ownerUserId AND itemId = :itemId " +
            "AND mediaSourceId = :mediaSourceId AND streamType = :streamType ORDER BY streamIndex ASC"
    )
    suspend fun getByType(
        ownerUserId: String,
        itemId: String,
        mediaSourceId: String,
        streamType: String
    ): List<MediaStreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(streams: List<MediaStreamEntity>)

    @Query("DELETE FROM media_streams WHERE ownerUserId = :ownerUserId AND itemId = :itemId")
    suspend fun deleteByItem(ownerUserId: String, itemId: String)

    @Query(
        "DELETE FROM media_streams WHERE ownerUserId = :ownerUserId AND itemId = :itemId " +
            "AND mediaSourceId = :mediaSourceId"
    )
    suspend fun deleteBySource(ownerUserId: String, itemId: String, mediaSourceId: String)

    /**
     * Drops tracks whose item is gone. There is no foreign key to media_items on purpose, so the
     * sweep is explicit rather than a cascade that would also fire whenever an item row is merely
     * rewritten.
     */
    @Query(
        "DELETE FROM media_streams WHERE ownerUserId = :ownerUserId AND itemId NOT IN " +
            "(SELECT itemId FROM media_items WHERE ownerUserId = :ownerUserId)"
    )
    suspend fun deleteOrphaned(ownerUserId: String)

    @Query("DELETE FROM media_streams WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)

    @Query("DELETE FROM media_streams WHERE ownerUserId != :ownerUserId")
    suspend fun deleteOtherOwners(ownerUserId: String)
}
