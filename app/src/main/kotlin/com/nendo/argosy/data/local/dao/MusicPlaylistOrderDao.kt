package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.MusicPlaylistOrderEntity

@Dao
interface MusicPlaylistOrderDao {

    @Query("SELECT * FROM music_playlist_orders WHERE ownerKey = :ownerKey AND playlistName = :playlistName")
    suspend fun get(ownerKey: Long, playlistName: String): MusicPlaylistOrderEntity?

    @Query("SELECT playlistName FROM music_playlist_orders WHERE ownerKey = :ownerKey")
    suspend fun names(ownerKey: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(order: MusicPlaylistOrderEntity)

    @Query("DELETE FROM music_playlist_orders WHERE ownerKey = :ownerKey AND playlistName = :playlistName")
    suspend fun delete(ownerKey: Long, playlistName: String)

    @Transaction
    suspend fun rename(ownerKey: Long, oldName: String, newName: String) {
        if (oldName == newName) return
        val order = get(ownerKey, oldName) ?: return
        delete(ownerKey, oldName)
        upsert(order.copy(playlistName = newName))
    }

    @Query("DELETE FROM music_playlist_orders WHERE ownerKey = :ownerKey")
    suspend fun deleteByOwner(ownerKey: Long)
}
