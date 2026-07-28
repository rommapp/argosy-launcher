package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Row existence means hidden. There is no flag to flip, so [hide] inserts and [unhide] deletes.
 *
 * A row with a null owner is an unattributed hide from an install that predates accounts: reads
 * count it for whoever is signed in and [unhide] clears it alongside the signed-in account's own
 * row, so signing in neither resurrects hidden roms nor leaves one that cannot be unhidden.
 */
@Dao
interface UserRomsHiddenDao {

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM user_roms_hidden
            WHERE gameId = :gameId AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        )
        """
    )
    suspend fun isHidden(ownerUserId: Long?, gameId: Long): Boolean

    @Query(
        "SELECT gameId FROM user_roms_hidden WHERE ownerUserId IS NULL OR ownerUserId IS :ownerUserId"
    )
    fun observeHidden(ownerUserId: Long?): Flow<List<Long>>

    @Query(
        "SELECT gameId FROM user_roms_hidden WHERE ownerUserId IS NULL OR ownerUserId IS :ownerUserId"
    )
    suspend fun hiddenGameIds(ownerUserId: Long?): List<Long>

    @Query(
        """
        INSERT INTO user_roms_hidden (ownerUserId, gameId)
        SELECT :ownerUserId, :gameId
        WHERE NOT EXISTS (
            SELECT 1 FROM user_roms_hidden
            WHERE gameId = :gameId AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        )
        """
    )
    suspend fun hide(ownerUserId: Long?, gameId: Long)

    @Query(
        """
        DELETE FROM user_roms_hidden
        WHERE gameId = :gameId AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        """
    )
    suspend fun unhide(ownerUserId: Long?, gameId: Long)

    /**
     * Carries a hidden choice across a multi-disc consolidation, per account: an account that
     * hid every source row hides the row they were folded into. Sources the account left visible
     * mean the merged game stays visible for that account, which is what the pre-join-table
     * "hidden only if all sources were hidden" rule meant. Must run before the sources are
     * deleted, since their rows CASCADE away with them.
     */
    @Query(
        """
        INSERT INTO user_roms_hidden (ownerUserId, gameId)
        SELECT h.ownerUserId, :targetGameId FROM user_roms_hidden h
        WHERE h.gameId IN (:sourceGameIds)
          AND NOT EXISTS (
              SELECT 1 FROM user_roms_hidden x
              WHERE x.gameId = :targetGameId AND x.ownerUserId IS h.ownerUserId
          )
        GROUP BY h.ownerUserId
        HAVING COUNT(DISTINCT h.gameId) = :sourceCount
        """
    )
    suspend fun inheritWhenAllHidden(targetGameId: Long, sourceGameIds: List<Long>, sourceCount: Int)

    @Query("DELETE FROM user_roms_hidden WHERE ownerUserId = :ownerUserId")
    suspend fun deleteForOwner(ownerUserId: Long)
}
