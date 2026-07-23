package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface QuayPassEncounterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(encounter: QuayPassEncounterEntity)

    @Query("SELECT * FROM quaypass_encounters ORDER BY encounteredAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<QuayPassEncounterEntity>

    @Query("SELECT * FROM quaypass_encounters ORDER BY encounteredAt DESC")
    fun observeAll(): Flow<List<QuayPassEncounterEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM quaypass_encounters WHERE seenByUser = 0)")
    fun observeHasUnseen(): Flow<Boolean>

    @Query("SELECT encounteredAt FROM quaypass_encounters WHERE credentialFingerprint = :fingerprint")
    suspend fun lastSeenAt(fingerprint: String): Instant?

    @Query("UPDATE quaypass_encounters SET seenByUser = 1 WHERE seenByUser = 0")
    suspend fun markAllSeen()

    @Query("UPDATE quaypass_encounters SET seenByUser = 1 WHERE credentialFingerprint = :fingerprint")
    suspend fun markSeen(fingerprint: String)

    @Query("SELECT * FROM quaypass_encounters WHERE reported = 0 AND accountId IS NOT NULL")
    suspend fun unreported(): List<QuayPassEncounterEntity>

    @Query("UPDATE quaypass_encounters SET reported = 1 WHERE credentialFingerprint = :fingerprint")
    suspend fun markReported(fingerprint: String)

    @Query("DELETE FROM quaypass_encounters WHERE credentialFingerprint = :fingerprint")
    suspend fun delete(fingerprint: String)

    @Query("DELETE FROM quaypass_encounters")
    suspend fun clear()
}
