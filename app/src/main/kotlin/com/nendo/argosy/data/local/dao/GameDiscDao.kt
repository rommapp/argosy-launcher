package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.GameDiscEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDiscDao {

    @Query("SELECT * FROM game_discs WHERE gameId = :gameId ORDER BY discNumber ASC")
    suspend fun getDiscsForGame(gameId: Long): List<GameDiscEntity>

    @Query("SELECT * FROM game_discs WHERE gameId = :gameId ORDER BY discNumber ASC")
    fun observeDiscsForGame(gameId: Long): Flow<List<GameDiscEntity>>

    @Query("SELECT * FROM game_discs WHERE rommId = :rommId")
    suspend fun getByRommId(rommId: Long): GameDiscEntity?

    @Query("SELECT * FROM game_discs WHERE id = :id")
    suspend fun getById(id: Long): GameDiscEntity?

    @Query("SELECT * FROM game_discs WHERE gameId = :gameId AND localPath IS NULL")
    suspend fun getMissingDiscs(gameId: Long): List<GameDiscEntity>

    @Query("""
        SELECT g.platformId AS platformId, gd.localPath AS localPath
        FROM game_discs gd
        INNER JOIN games g ON gd.gameId = g.id
        WHERE gd.localPath IS NOT NULL
    """)
    suspend fun getAllLocalPathsWithPlatform(): List<PlatformLocalPath>

    @Query("SELECT COUNT(*) FROM game_discs WHERE gameId = :gameId AND localPath IS NOT NULL")
    suspend fun getDownloadedDiscCount(gameId: Long): Int

    @Query("SELECT COUNT(*) FROM game_discs WHERE gameId = :gameId")
    suspend fun getTotalDiscCount(gameId: Long): Int

    @Query("SELECT SUM(fileSize) FROM game_discs WHERE gameId = :gameId")
    suspend fun getTotalFileSize(gameId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(disc: GameDiscEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(discs: List<GameDiscEntity>)

    @Update
    suspend fun update(disc: GameDiscEntity)

    @Query("UPDATE game_discs SET localPath = :localPath WHERE id = :id")
    suspend fun updateLocalPath(id: Long, localPath: String?)

    @Query("UPDATE game_discs SET localPath = :localPath WHERE rommId = :rommId")
    suspend fun updateLocalPathByRommId(rommId: Long, localPath: String?)

    @Query("DELETE FROM game_discs WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: Long)

    @Query("DELETE FROM game_discs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM game_discs WHERE gameId = :gameId AND rommId NOT IN (:validRommIds)")
    suspend fun deleteInvalidDiscs(gameId: Long, validRommIds: List<Long>)

    @Query("""
        SELECT gd.* FROM game_discs gd
        INNER JOIN games g ON gd.gameId = g.id
        WHERE g.platformId = :platformId AND gd.localPath IS NOT NULL
    """)
    suspend fun getDiscsWithLocalPathByPlatform(platformId: Long): List<GameDiscEntity>

    @Query("UPDATE game_discs SET localPath = NULL WHERE id = :discId")
    suspend fun clearLocalPath(discId: Long)
}
