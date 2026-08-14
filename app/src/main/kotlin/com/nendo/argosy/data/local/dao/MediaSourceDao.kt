package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.MediaSourceEntity

/**
 * Every read is scoped to a media account, matching the items the sources belong to.
 */
@Dao
interface MediaSourceDao {

    @Query(
        "SELECT * FROM media_sources WHERE ownerUserId = :ownerUserId AND itemId = :itemId " +
            "ORDER BY mediaSourceId ASC"
    )
    suspend fun getByItem(ownerUserId: String, itemId: String): List<MediaSourceEntity>

    /**
     * Replaces what was known about the same version rather than adding a second answer for it. The
     * unique index is on the version, so a re-negotiation of one version leaves the others alone.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: MediaSourceEntity)

    @Query("DELETE FROM media_sources WHERE ownerUserId != :ownerUserId")
    suspend fun deleteOtherOwners(ownerUserId: String)
}
