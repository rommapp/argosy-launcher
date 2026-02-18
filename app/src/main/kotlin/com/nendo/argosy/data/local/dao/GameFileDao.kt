package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.GameFileEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class GameWithMissingCount(
    val gameId: Long,
    val title: String,
    val coverPath: String?,
    val platformSlug: String,
    val updateCount: Int,
    val dlcCount: Int,
    val totalSize: Long
)

@Dao
interface GameFileDao {

    @Query("SELECT * FROM game_files WHERE gameId = :gameId ORDER BY category ASC, fileName ASC")
    suspend fun getFilesForGame(gameId: Long): List<GameFileEntity>

    @Query("SELECT * FROM game_files WHERE gameId = :gameId ORDER BY category ASC, fileName ASC")
    fun observeFilesForGame(gameId: Long): Flow<List<GameFileEntity>>

    @Query("SELECT * FROM game_files WHERE gameId = :gameId AND localPath IS NULL ORDER BY category ASC, fileName ASC")
    suspend fun getMissingFiles(gameId: Long): List<GameFileEntity>

    @Query("SELECT * FROM game_files WHERE gameId = :gameId AND category = :category ORDER BY fileName ASC")
    suspend fun getFilesByCategory(gameId: Long, category: String): List<GameFileEntity>

    @Query("SELECT * FROM game_files WHERE rommFileId = :rommFileId")
    suspend fun getByRommFileId(rommFileId: Long): GameFileEntity?

    @Query("SELECT * FROM game_files WHERE id = :id")
    suspend fun getById(id: Long): GameFileEntity?

    @Query("""
        SELECT
            g.id AS gameId,
            g.title AS title,
            g.coverPath AS coverPath,
            g.platformSlug AS platformSlug,
            SUM(CASE WHEN gf.category = 'update' THEN 1 ELSE 0 END) AS updateCount,
            SUM(CASE WHEN gf.category = 'dlc' THEN 1 ELSE 0 END) AS dlcCount,
            SUM(gf.fileSize) AS totalSize
        FROM game_files gf
        INNER JOIN games g ON gf.gameId = g.id
        WHERE gf.localPath IS NULL
        GROUP BY gf.gameId
        ORDER BY g.title ASC
    """)
    suspend fun getGamesWithMissingFiles(): List<GameWithMissingCount>

    @Query("SELECT COUNT(*) FROM game_files WHERE localPath IS NULL")
    suspend fun getTotalMissingCount(): Int

    @Query("SELECT COUNT(*) FROM game_files WHERE gameId = :gameId AND localPath IS NOT NULL")
    suspend fun getDownloadedCount(gameId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: GameFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<GameFileEntity>)

    @Query("UPDATE game_files SET localPath = :localPath, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun updateLocalPath(id: Long, localPath: String?, downloadedAt: Instant?)

    @Query("UPDATE game_files SET localPath = :localPath, downloadedAt = :downloadedAt WHERE rommFileId = :rommFileId")
    suspend fun updateLocalPathByRommFileId(rommFileId: Long, localPath: String?, downloadedAt: Instant?)

    @Query("UPDATE game_files SET localPath = NULL, downloadedAt = NULL WHERE gameId = :gameId")
    suspend fun clearLocalPathsByGameId(gameId: Long)

    @Query("DELETE FROM game_files WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: Long)

    @Query("DELETE FROM game_files WHERE gameId = :gameId AND rommFileId NOT IN (:validRommFileIds)")
    suspend fun deleteInvalidFiles(gameId: Long, validRommFileIds: List<Long>)
}
