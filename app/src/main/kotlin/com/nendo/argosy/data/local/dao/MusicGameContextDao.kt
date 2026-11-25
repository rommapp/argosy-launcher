package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.MusicGameContextEntity
import com.nendo.argosy.data.local.entity.MusicGameContextTagEntity

private const val INSERT_IGNORED = -1L

data class MusicGameKeyRow(
    val gameKey: String,
    val gameId: Long,
    val sessionStartTime: Long
)

@Dao
interface MusicGameContextDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(context: MusicGameContextEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<MusicGameContextTagEntity>)

    @Query(
        "SELECT id FROM music_game_contexts " +
            "WHERE ownerKey = :ownerKey AND gameId = :gameId AND sessionStartTime = :sessionStartTime"
    )
    suspend fun idFor(ownerKey: Long, gameId: Long, sessionStartTime: Long): Long?

    /**
     * The context row for the context's (ownerKey, gameId, sessionStartTime), inserting it and
     * [tags] when none exists yet. Returns its id.
     */
    @Transaction
    suspend fun snapshot(context: MusicGameContextEntity, tags: List<MusicGameContextTagEntity>): Long {
        val inserted = insertIgnore(context)
        if (inserted != INSERT_IGNORED) {
            if (tags.isNotEmpty()) insertTags(tags.map { it.copy(contextId = inserted) })
            return inserted
        }
        return idFor(context.ownerKey, context.gameId, context.sessionStartTime) ?: INSERT_IGNORED
    }

    @Query(
        "SELECT gameKey, gameId, MAX(sessionStartTime) AS sessionStartTime FROM music_game_contexts " +
            "WHERE ownerKey = :ownerKey AND gameKey IN (:gameKeys) GROUP BY gameKey"
    )
    suspend fun latestGameIds(ownerKey: Long, gameKeys: List<String>): List<MusicGameKeyRow>

    @Query("SELECT * FROM music_game_context_tags WHERE ownerKey = :ownerKey")
    suspend fun tags(ownerKey: Long): List<MusicGameContextTagEntity>

    @Query("DELETE FROM music_game_context_tags WHERE ownerKey = :ownerKey")
    suspend fun deleteTags(ownerKey: Long)

    @Query("DELETE FROM music_game_contexts WHERE ownerKey = :ownerKey")
    suspend fun deleteContexts(ownerKey: Long)

    @Transaction
    suspend fun deleteByOwner(ownerKey: Long) {
        deleteTags(ownerKey)
        deleteContexts(ownerKey)
    }
}
