package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface QuayPassEncounterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(encounter: QuayPassEncounterEntity)

    /**
     * Atomic, persistent cooldown claim: records the encounter only when the peer
     * has no encounter newer than [cooldownCutoff], returning whether it was
     * claimed. Runs in one transaction so two simultaneous passes with the same
     * peer (mutual StreetPass, or a replay right after an app restart) collapse to
     * a single credit; the persisted row is the cooldown, so it survives process
     * death without any background work.
     */
    @Transaction
    suspend fun claimEncounter(
        encounter: QuayPassEncounterEntity,
        cooldownCutoff: Instant
    ): Boolean {
        val last = lastSeenAt(encounter.credentialFingerprint)
        if (last != null && last.isAfter(cooldownCutoff)) return false
        upsert(encounter)
        return true
    }

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
