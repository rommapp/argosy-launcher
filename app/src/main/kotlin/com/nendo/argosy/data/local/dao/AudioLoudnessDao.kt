package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.AudioLoudnessEntity

@Dao
interface AudioLoudnessDao {

    @Query("SELECT * FROM audio_loudness WHERE filePath = :filePath")
    suspend fun get(filePath: String): AudioLoudnessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AudioLoudnessEntity)

    @Query("DELETE FROM audio_loudness WHERE filePath = :filePath")
    suspend fun delete(filePath: String)
}
