package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Watch state, scoped to a media account on every read: the position and the played flag belong to
 * one user of the server, and reading them unscoped hands one account another's progress.
 */
@Dao
interface MediaUserDataDao {

    @Query("SELECT * FROM media_user_data WHERE ownerUserId = :ownerUserId AND itemId = :itemId LIMIT 1")
    suspend fun getByItem(ownerUserId: String, itemId: String): MediaUserDataEntity?

    @Query("SELECT * FROM media_user_data WHERE ownerUserId = :ownerUserId AND itemId = :itemId LIMIT 1")
    fun observeByItem(ownerUserId: String, itemId: String): Flow<MediaUserDataEntity?>

    @Query("SELECT * FROM media_user_data WHERE ownerUserId = :ownerUserId AND itemId IN (:itemIds)")
    suspend fun getByItems(ownerUserId: String, itemIds: List<String>): List<MediaUserDataEntity>

    @Query(
        "SELECT * FROM media_user_data WHERE ownerUserId = :ownerUserId AND isFavorite = 1 " +
            "ORDER BY updatedAt DESC"
    )
    fun observeFavorites(ownerUserId: String): Flow<List<MediaUserDataEntity>>

    @Query(
        "SELECT * FROM media_user_data WHERE ownerUserId = :ownerUserId AND needsSync = 1 " +
            "ORDER BY updatedAt ASC"
    )
    suspend fun getNeedingSync(ownerUserId: String): List<MediaUserDataEntity>

    @Query("SELECT COUNT(*) FROM media_user_data WHERE ownerUserId = :ownerUserId AND needsSync = 1")
    fun observeNeedingSyncCount(ownerUserId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userData: MediaUserDataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(userData: List<MediaUserDataEntity>)

    @Query(
        "UPDATE media_user_data SET needsSync = 0 WHERE ownerUserId = :ownerUserId AND itemId = :itemId " +
            "AND updatedAt <= :reportedAt"
    )
    suspend fun clearNeedsSync(ownerUserId: String, itemId: String, reportedAt: Instant)

    /**
     * Records where playback was left. Creates the row when the item has never been played, so a
     * position taken offline is stored whether or not the server has ever described this item.
     */
    @Transaction
    suspend fun recordPosition(
        ownerUserId: String,
        itemId: String,
        positionTicks: Long,
        playedPercentage: Double?,
        played: Boolean,
        updatedAt: Instant
    ) {
        val existing = getByItem(ownerUserId, itemId)
        val playCount = if (played && existing?.played != true) {
            (existing?.playCount ?: 0) + 1
        } else {
            existing?.playCount ?: 0
        }
        insert(
            existing?.copy(
                playbackPositionTicks = positionTicks,
                playedPercentage = playedPercentage,
                played = played,
                playCount = playCount,
                lastPlayedAt = updatedAt,
                needsSync = true,
                updatedAt = updatedAt
            ) ?: MediaUserDataEntity(
                ownerUserId = ownerUserId,
                itemId = itemId,
                playbackPositionTicks = positionTicks,
                playedPercentage = playedPercentage,
                played = played,
                playCount = playCount,
                lastPlayedAt = updatedAt,
                needsSync = true,
                updatedAt = updatedAt
            )
        )
    }

    @Transaction
    suspend fun setFavorite(
        ownerUserId: String,
        itemId: String,
        isFavorite: Boolean,
        updatedAt: Instant
    ) {
        val existing = getByItem(ownerUserId, itemId)
        insert(
            existing?.copy(
                isFavorite = isFavorite,
                needsSync = true,
                updatedAt = updatedAt
            ) ?: MediaUserDataEntity(
                ownerUserId = ownerUserId,
                itemId = itemId,
                isFavorite = isFavorite,
                needsSync = true,
                updatedAt = updatedAt
            )
        )
    }

    /**
     * Takes the server's version of one item's watch state. A row still carrying an unreported
     * local write is left alone: the server is authority only for state it has already been told
     * about, and overwriting here would silently discard a position recorded offline.
     */
    @Transaction
    suspend fun applyServerState(userData: MediaUserDataEntity) {
        val existing = getByItem(userData.ownerUserId, userData.itemId)
        if (existing?.needsSync == true) return
        insert(userData.copy(id = existing?.id ?: 0, needsSync = false))
    }

    @Query("DELETE FROM media_user_data WHERE ownerUserId = :ownerUserId AND itemId = :itemId")
    suspend fun deleteByItem(ownerUserId: String, itemId: String)

    @Query("DELETE FROM media_user_data WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)

    @Query("DELETE FROM media_user_data WHERE ownerUserId != :ownerUserId")
    suspend fun deleteOtherOwners(ownerUserId: String)
}
