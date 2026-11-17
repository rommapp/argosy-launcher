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

@Dao
interface HomeTileDao {

    @Query(
        "SELECT * FROM home_tiles WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) " +
            "AND gridKind = :gridKind ORDER BY pageIndex ASC, rowIndex ASC, columnIndex ASC"
    )
    fun observeTiles(ownerUserId: Long?, gridKind: String): Flow<List<HomeTileEntity>>

    @Query(
        "SELECT * FROM home_tiles WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) " +
            "AND gridKind = :gridKind AND pageIndex = :pageIndex ORDER BY rowIndex ASC, columnIndex ASC"
    )
    suspend fun getPage(ownerUserId: Long?, gridKind: String, pageIndex: Int): List<HomeTileEntity>

    @Query(
        "SELECT MAX(pageIndex) FROM home_tiles WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) " +
            "AND gridKind = :gridKind"
    )
    suspend fun getMaxPageIndex(ownerUserId: Long?, gridKind: String): Int?

    @Query("SELECT * FROM home_tiles WHERE id = :id")
    suspend fun getById(id: Long): HomeTileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tile: HomeTileEntity): Long

    @Update
    suspend fun update(tile: HomeTileEntity)

    @Update
    suspend fun updateAll(tiles: List<HomeTileEntity>)

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

    @Query(
        "DELETE FROM home_tile_episodes WHERE tileId IN (SELECT id FROM home_tiles " +
            "WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) AND gridKind = :gridKind " +
            "AND pageIndex = :pageIndex)"
    )
    suspend fun deleteEpisodesForPage(ownerUserId: Long?, gridKind: String, pageIndex: Int)

    @Transaction
    suspend fun replaceEpisodes(tileId: Long, rows: List<HomeTileEpisodeEntity>) {
        deleteEpisodesForTile(tileId)
        if (rows.isNotEmpty()) insertEpisodes(rows)
    }

    @Transaction
    suspend fun deleteTileWithEpisodes(id: Long) {
        deleteEpisodesForTile(id)
        deleteById(id)
    }

    @Query(
        "DELETE FROM home_tiles WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) " +
            "AND gridKind = :gridKind AND pageIndex = :pageIndex"
    )
    suspend fun deletePage(ownerUserId: Long?, gridKind: String, pageIndex: Int)

    @Query(
        "UPDATE home_tiles SET pageIndex = pageIndex - 1 " +
            "WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) AND gridKind = :gridKind " +
            "AND pageIndex > :removedPage"
    )
    suspend fun shiftPagesDown(ownerUserId: Long?, gridKind: String, removedPage: Int)

    @Query("DELETE FROM home_tiles WHERE targetType = 'GAME' AND gameId NOT IN (SELECT id FROM games)")
    suspend fun deleteTilesForMissingGames()

    @Query("SELECT * FROM home_tiles WHERE parentTileId = :folderId ORDER BY columnIndex ASC, id ASC")
    suspend fun getFolderMembers(folderId: Long): List<HomeTileEntity>

    @Query(
        "DELETE FROM home_tile_episodes WHERE tileId IN (SELECT id FROM home_tiles WHERE parentTileId IN " +
            "(SELECT id FROM home_tiles WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) " +
            "AND gridKind = :gridKind AND pageIndex = :pageIndex))"
    )
    suspend fun deleteFolderMemberEpisodesForPage(ownerUserId: Long?, gridKind: String, pageIndex: Int)

    @Query(
        "DELETE FROM home_tiles WHERE parentTileId IN (SELECT id FROM home_tiles " +
            "WHERE (ownerUserId = :ownerUserId OR ownerUserId IS NULL) AND gridKind = :gridKind " +
            "AND pageIndex = :pageIndex)"
    )
    suspend fun deleteFolderMembersForPage(ownerUserId: Long?, gridKind: String, pageIndex: Int)
}
