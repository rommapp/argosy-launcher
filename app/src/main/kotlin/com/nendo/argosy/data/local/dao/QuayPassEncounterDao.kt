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
     * Atomic per-user claim. Collapses to one row per peer account (StreetPass
     * style): within the cooldown window a re-meet is dismissed and the earliest
     * entry is left untouched; a later meet updates that user's single row to the
     * newest card and increments [QuayPassEncounterEntity.meetCount], swapping the
     * stored row in place when the peer's credential (and fingerprint) rotated.
     * Runs in one transaction, so mutual StreetPass and a capture-restart-replay
     * both collapse to a single credit, and the persisted row is the cooldown, so
     * it survives process death with no background work. Returns whether claimed.
     */
    @Transaction
    suspend fun claimEncounter(
        encounter: QuayPassEncounterEntity,
        cooldownCutoff: Instant
    ): Boolean {
        val existing = encounter.accountId?.let { getByAccountId(it) }
        if (existing != null && existing.encounteredAt.isAfter(cooldownCutoff)) return false
        if (existing != null && existing.credentialFingerprint != encounter.credentialFingerprint) {
            delete(existing.credentialFingerprint)
        }
        upsert(encounter.copy(meetCount = (existing?.meetCount ?: 0) + 1))
        return true
    }

    @Query("SELECT * FROM quaypass_encounters WHERE accountId = :accountId ORDER BY encounteredAt DESC LIMIT 1")
    suspend fun getByAccountId(accountId: String): QuayPassEncounterEntity?

    @Query("SELECT * FROM quaypass_encounters ORDER BY encounteredAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<QuayPassEncounterEntity>

    @Query("SELECT * FROM quaypass_encounters ORDER BY encounteredAt DESC")
    fun observeAll(): Flow<List<QuayPassEncounterEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM quaypass_encounters WHERE seenByUser = 0)")
    fun observeHasUnseen(): Flow<Boolean>

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
