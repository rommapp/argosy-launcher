package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.RommMusicIndexStateEntity
import com.nendo.argosy.data.local.entity.RommMusicTrackEntity

private const val INSERT_CHUNK = 500

@Dao
interface RommMusicIndexDao {

    @Query("SELECT * FROM romm_music_tracks WHERE ownerUserId = :ownerUserId")
    suspend fun tracks(ownerUserId: Long): List<RommMusicTrackEntity>

    @Query("SELECT * FROM romm_music_tracks WHERE ownerUserId = :ownerUserId AND romId = :romId")
    suspend fun tracksForRom(ownerUserId: Long, romId: Long): List<RommMusicTrackEntity>

    @Query("SELECT * FROM romm_music_index_state WHERE ownerUserId = :ownerUserId")
    suspend fun state(ownerUserId: Long): RommMusicIndexStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(rows: List<RommMusicTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: RommMusicIndexStateEntity)

    @Query("DELETE FROM romm_music_tracks WHERE ownerUserId = :ownerUserId")
    suspend fun deleteTracks(ownerUserId: Long)

    @Query("DELETE FROM romm_music_index_state WHERE ownerUserId = :ownerUserId")
    suspend fun deleteState(ownerUserId: Long)

    @Transaction
    suspend fun replaceForOwner(ownerUserId: Long, rows: List<RommMusicTrackEntity>, fetchedAt: Long) {
        deleteTracks(ownerUserId)
        rows.chunked(INSERT_CHUNK).forEach { insertTracks(it) }
        upsertState(RommMusicIndexStateEntity(ownerUserId, fetchedAt, rows.size))
    }

    @Transaction
    suspend fun deleteByOwner(ownerUserId: Long) {
        deleteTracks(ownerUserId)
        deleteState(ownerUserId)
    }
}
