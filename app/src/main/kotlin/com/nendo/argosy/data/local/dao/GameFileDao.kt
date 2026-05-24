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

data class MissingGameFile(
    val fileId: Long,
    val gameId: Long,
    val fileName: String,
    val category: String,
    val gameTitle: String,
    val rommFileName: String?,
    val platformSlug: String
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

    @Query("""
        SELECT
            gf.id AS fileId,
            gf.gameId AS gameId,
            gf.fileName AS fileName,
            gf.category AS category,
            g.title AS gameTitle,
            g.rommFileName AS rommFileName,
            g.platformSlug AS platformSlug
        FROM game_files gf
        INNER JOIN games g ON gf.gameId = g.id
        WHERE gf.localPath IS NULL
    """)
    suspend fun getMissingFilesWithGameInfo(): List<MissingGameFile>

    @Query("SELECT COUNT(*) FROM game_files WHERE gameId = :gameId AND localPath IS NOT NULL")
    suspend fun getDownloadedCount(gameId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: GameFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<GameFileEntity>)

    @Query("UPDATE game_files SET localPath = :localPath, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun updateLocalPath(id: Long, localPath: String?, downloadedAt: Instant?)

    @Query("UPDATE game_files SET romHashPrefix = :hash WHERE id = :id")
    suspend fun updateRomHashPrefix(id: Long, hash: String?)

    @Query("UPDATE game_files SET localPath = :localPath, downloadedAt = :downloadedAt WHERE rommFileId = :rommFileId")
    suspend fun updateLocalPathByRommFileId(rommFileId: Long, localPath: String?, downloadedAt: Instant?)

    @Query("UPDATE game_files SET localPath = NULL, downloadedAt = NULL WHERE gameId = :gameId")
    suspend fun clearLocalPathsByGameId(gameId: Long)

    @Query("DELETE FROM game_files WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM game_files WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: Long)

    @Query("DELETE FROM game_files WHERE gameId = :gameId AND rommFileId NOT IN (:validRommFileIds)")
    suspend fun deleteInvalidFiles(gameId: Long, validRommFileIds: List<Long>)

    @Query("""
        SELECT gf.* FROM game_files gf
        INNER JOIN games g ON gf.gameId = g.id
        WHERE g.platformId = :platformId AND gf.localPath IS NOT NULL
    """)
    suspend fun getFilesWithLocalPathByPlatform(platformId: Long): List<GameFileEntity>

    @Query("UPDATE game_files SET localPath = NULL, downloadedAt = NULL WHERE id = :fileId")
    suspend fun clearLocalPath(fileId: Long)

    @Query("UPDATE game_files SET localPath = :newPath WHERE localPath = :oldPath")
    suspend fun updateLocalPathByOldPath(oldPath: String, newPath: String)

    @Query("SELECT * FROM game_files WHERE gameId = :gameId AND isLaunchTarget = 1 ORDER BY category ASC, fileName ASC")
    suspend fun getVariantsForGame(gameId: Long): List<GameFileEntity>

    @Query("SELECT * FROM game_files WHERE gameId = :gameId AND isLaunchTarget = 1 ORDER BY category ASC, fileName ASC")
    fun observeVariantsForGame(gameId: Long): Flow<List<GameFileEntity>>

    @Query("SELECT * FROM game_files WHERE localPath = :localPath LIMIT 1")
    suspend fun getByLocalPath(localPath: String): GameFileEntity?
}
