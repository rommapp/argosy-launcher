package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nendo.argosy.data.local.entity.HomeTileEntity
import com.nendo.argosy.data.local.entity.HomeTileEpisodeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Every read is scoped to an account. A pre-account tile carries a null owner and belongs to
 * whoever is signed in, so the scope is matched with IS NULL rather than being left out.
 */
@Dao
interface HomeTileDao {

    @Query(
        "SELECT * FROM home_tiles WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) " +
            "ORDER BY pageIndex ASC, rowIndex ASC, columnIndex ASC"
    )
    fun observeTiles(ownerUserId: Long?): Flow<List<HomeTileEntity>>

    @Query(
        "SELECT * FROM home_tiles WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) " +
            "AND pageIndex = :pageIndex ORDER BY rowIndex ASC, columnIndex ASC"
    )
    suspend fun getPage(ownerUserId: Long?, pageIndex: Int): List<HomeTileEntity>

    @Query(
        "SELECT MAX(pageIndex) FROM home_tiles WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL)"
    )
    suspend fun getMaxPageIndex(ownerUserId: Long?): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tile: HomeTileEntity): Long

    @Update
    suspend fun update(tile: HomeTileEntity)

    @Query("DELETE FROM home_tiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM home_tile_episodes WHERE tileId = :tileId ORDER BY orderIndex ASC")
    suspend fun getEpisodes(tileId: Long): List<HomeTileEpisodeEntity>

    @Query("SELECT * FROM home_tile_episodes ORDER BY tileId ASC, orderIndex ASC")
    fun observeAllEpisodes(): Flow<List<HomeTileEpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(rows: List<HomeTileEpisodeEntity>)

    @Query("DELETE FROM home_tile_episodes WHERE tileId = :tileId")
    suspend fun deleteEpisodesForTile(tileId: Long)

    /**
     * Clears the runs belonging to a whole page. Deleting the page alone would leave its chosen
     * episodes behind with nothing referring to them, and the next tile to be handed one of those
     * ids would inherit a run it was never given.
     */
    @Query(
        "DELETE FROM home_tile_episodes WHERE tileId IN (SELECT id FROM home_tiles " +
            "WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) AND pageIndex = :pageIndex)"
    )
    suspend fun deleteEpisodesForPage(ownerUserId: Long?, pageIndex: Int)

    /**
     * Replaces a tile's chosen episodes as one unit, so a tile can never be caught showing both the
     * run it had and the run it was just given.
     */
    @Transaction
    suspend fun replaceEpisodes(tileId: Long, rows: List<HomeTileEpisodeEntity>) {
        deleteEpisodesForTile(tileId)
        if (rows.isNotEmpty()) insertEpisodes(rows)
    }

    /**
     * Removes a tile and whatever run was chosen for it together, so a later tile reusing the id
     * cannot inherit episodes it was never given.
     */
    @Transaction
    suspend fun deleteTileWithEpisodes(id: Long) {
        deleteEpisodesForTile(id)
        deleteById(id)
    }

    @Query(
        "DELETE FROM home_tiles WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) " +
            "AND pageIndex = :pageIndex"
    )
    suspend fun deletePage(ownerUserId: Long?, pageIndex: Int)

    /**
     * Closes the gap a removed page leaves. Without it the pages after it keep their old numbers
     * and the grid shows an empty page where the removed one used to be.
     */
    @Query(
        "UPDATE home_tiles SET pageIndex = pageIndex - 1 " +
            "WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) AND pageIndex > :removedPage"
    )
    suspend fun shiftPagesDown(ownerUserId: Long?, removedPage: Int)

    @Query("DELETE FROM home_tiles WHERE targetType = 'GAME' AND gameId NOT IN (SELECT id FROM games)")
    suspend fun deleteTilesForMissingGames()
}
