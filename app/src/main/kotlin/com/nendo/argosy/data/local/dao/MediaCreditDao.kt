package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.MediaCreditEntity

/**
 * Every read is scoped to a media account, for the same reason item reads are: two accounts can see
 * the same title, and the credits stored against it belong to whichever account synced them.
 */
@Dao
interface MediaCreditDao {

    @Query(
        "SELECT * FROM media_credits WHERE ownerUserId = :ownerUserId AND itemId = :itemId " +
            "ORDER BY sortOrder ASC"
    )
    suspend fun getForItem(ownerUserId: String, itemId: String): List<MediaCreditEntity>

    /**
     * The ids of other titles crediting any of [personIds] in the same role.
     *
     * Excludes [excludeItemId] so a title is never offered as being like itself.
     */
    @Query(
        "SELECT DISTINCT itemId FROM media_credits WHERE ownerUserId = :ownerUserId " +
            "AND personId IN (:personIds) AND personType = :personType AND itemId != :excludeItemId " +
            "LIMIT :limit"
    )
    suspend fun getItemIdsSharingPeople(
        ownerUserId: String,
        personIds: List<String>,
        personType: String,
        excludeItemId: String,
        limit: Int
    ): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(credits: List<MediaCreditEntity>)

    @Query("DELETE FROM media_credits WHERE ownerUserId = :ownerUserId AND itemId = :itemId")
    suspend fun deleteForItem(ownerUserId: String, itemId: String)

    /**
     * Replaces a title's credits as one unit, so a re-sync can never leave a title showing both the
     * cast it had and the cast it has.
     */
    @Transaction
    suspend fun replaceForItem(ownerUserId: String, itemId: String, credits: List<MediaCreditEntity>) {
        deleteForItem(ownerUserId, itemId)
        if (credits.isNotEmpty()) insertAll(credits)
    }

    @Query("DELETE FROM media_credits WHERE ownerUserId = :ownerUserId")
    suspend fun deleteForOwner(ownerUserId: String)
}
