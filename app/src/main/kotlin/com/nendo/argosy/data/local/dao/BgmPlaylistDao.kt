package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.BgmPlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BgmPlaylistDao {

    @Query("SELECT * FROM bgm_playlist ORDER BY position ASC")
    fun observeAll(): Flow<List<BgmPlaylistEntity>>

    @Query("SELECT * FROM bgm_playlist ORDER BY position ASC")
    suspend fun getAll(): List<BgmPlaylistEntity>

    @Query("SELECT * FROM bgm_playlist WHERE filePath = :filePath")
    suspend fun getByPath(filePath: String): BgmPlaylistEntity?

    @Query("SELECT * FROM bgm_playlist WHERE id = :id")
    suspend fun getById(id: Long): BgmPlaylistEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM bgm_playlist WHERE filePath = :filePath)")
    suspend fun exists(filePath: String): Boolean

    @Query("SELECT MAX(position) FROM bgm_playlist")
    suspend fun getMaxPosition(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BgmPlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: BgmPlaylistEntity): Long

    @Query("DELETE FROM bgm_playlist WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String)

    @Query("DELETE FROM bgm_playlist WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bgm_playlist WHERE sourceEntryId = :sourceEntryId")
    suspend fun deleteBySourceEntryId(sourceEntryId: Long)

    @Transaction
    suspend fun deleteFolderSource(id: Long) {
        deleteBySourceEntryId(id)
        deleteById(id)
    }

    @Query("UPDATE bgm_playlist SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)

    @Query("""
        UPDATE bgm_playlist SET filePath = :newPrefix || SUBSTR(filePath, LENGTH(:oldPrefix) + 1)
        WHERE filePath = :oldPrefix OR filePath LIKE :oldPrefix || '/%'
    """)
    suspend fun rewritePathPrefix(oldPrefix: String, newPrefix: String)

    @Query("UPDATE bgm_playlist SET sourceEntryId = :sourceEntryId, enabled = 0 WHERE id = :id")
    suspend fun convertToDisabledSourced(id: Long, sourceEntryId: Long)

    @Query("UPDATE bgm_playlist SET gameFileId = NULL WHERE gameFileId IS NOT NULL AND gameFileId NOT IN (SELECT id FROM game_files)")
    suspend fun clearDanglingGameFileIds()

    @Query("UPDATE bgm_playlist SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Transaction
    suspend fun updatePositions(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index) }
    }
}
