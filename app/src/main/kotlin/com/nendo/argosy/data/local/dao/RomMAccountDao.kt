package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RomMAccountDao {

    @Query("SELECT * FROM romm_accounts ORDER BY lastLoginAt DESC")
    suspend fun getAll(): List<RomMAccountEntity>

    @Query("SELECT * FROM romm_accounts ORDER BY lastLoginAt DESC")
    fun observeAll(): Flow<List<RomMAccountEntity>>

    @Query("SELECT * FROM romm_accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): RomMAccountEntity?

    @Query("SELECT * FROM romm_accounts WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<RomMAccountEntity?>

    @Query("SELECT * FROM romm_accounts WHERE id = :id")
    suspend fun getById(id: Long): RomMAccountEntity?

    @Query("SELECT * FROM romm_accounts WHERE rommUserId = :rommUserId LIMIT 1")
    suspend fun getByRommUserId(rommUserId: Long): RomMAccountEntity?

    @Query("SELECT COUNT(*) FROM romm_accounts")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(account: RomMAccountEntity): Long

    @Query("UPDATE romm_accounts SET isActive = 0")
    suspend fun deactivateAll()

    /**
     * Single-active invariant. Callers must never sequence deactivateAll and the activation
     * themselves; a crash between the two leaves the device with no active account.
     */
    @Transaction
    suspend fun setActive(id: Long) {
        deactivateAll()
        markActive(id)
    }

    @Query("UPDATE romm_accounts SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    @Query("UPDATE romm_accounts SET token = :token, deviceId = :deviceId, deviceClientVersion = :clientVersion, lastLoginAt = :lastLoginAt WHERE id = :id")
    suspend fun updateCredentials(
        id: Long,
        token: String,
        deviceId: String?,
        clientVersion: String?,
        lastLoginAt: java.time.Instant
    )

    @Query("UPDATE romm_accounts SET deviceId = :deviceId, deviceClientVersion = :clientVersion WHERE id = :id")
    suspend fun updateDevice(id: Long, deviceId: String?, clientVersion: String?)

    @Query("DELETE FROM romm_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
