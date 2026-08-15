package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.SaveChannelEntity

/**
 * Every read is scoped to an account, and a pre-account row carries a null owner and counts for
 * whoever is signed in - the same treatment the save cache gives its own rows.
 */
@Dao
interface SaveChannelDao {

    @Query(
        "SELECT * FROM save_channels WHERE gameId = :gameId " +
            "AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId) ORDER BY createdAt ASC"
    )
    suspend fun getForGame(gameId: Long, ownerUserId: Long?): List<SaveChannelEntity>

    @Query(
        "SELECT channelName FROM save_channels WHERE gameId = :gameId " +
            "AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId) AND isActive = 1 LIMIT 1"
    )
    suspend fun getActiveChannel(gameId: Long, ownerUserId: Long?): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SaveChannelEntity): Long

    @Query(
        "DELETE FROM save_channels WHERE gameId = :gameId AND channelName = :channelName " +
            "AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)"
    )
    suspend fun delete(gameId: Long, channelName: String, ownerUserId: Long?)

    @Query(
        "UPDATE save_channels SET isActive = 0 WHERE gameId = :gameId " +
            "AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)"
    )
    suspend fun clearActive(gameId: Long, ownerUserId: Long?)

    @Query(
        "UPDATE save_channels SET isActive = 1 WHERE gameId = :gameId AND channelName = :channelName " +
            "AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)"
    )
    suspend fun markActive(gameId: Long, channelName: String, ownerUserId: Long?)

    /**
     * Records a slot and makes it the one this game saves into, in one step. Registering without
     * selecting would leave the slot listed but not the destination, which is the half-created state
     * this table exists to remove.
     */
    @Transaction
    suspend fun registerAndActivate(gameId: Long, channelName: String, ownerUserId: Long?) {
        insert(
            SaveChannelEntity(
                ownerUserId = ownerUserId,
                gameId = gameId,
                channelName = channelName
            )
        )
        clearActive(gameId, ownerUserId)
        markActive(gameId, channelName, ownerUserId)
    }

    @Transaction
    suspend fun setActive(gameId: Long, channelName: String?, ownerUserId: Long?) {
        clearActive(gameId, ownerUserId)
        if (channelName != null) markActive(gameId, channelName, ownerUserId)
    }
}
