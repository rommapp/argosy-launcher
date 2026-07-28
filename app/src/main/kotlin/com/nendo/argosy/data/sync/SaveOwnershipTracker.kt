package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.SaveOwnershipDao
import com.nendo.argosy.data.local.entity.SaveOwnershipEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SaveClaim {
    data object Unowned : SaveClaim
    data object Mine : SaveClaim
    data class Foreign(val ownerUserId: Long) : SaveClaim
}

/**
 * Tracks which account wrote the bytes currently at a live save path.
 *
 * The save path carries no account dimension, so without this a save found on disk cannot be
 * told apart from one the signed-in account wrote. Recording happens wherever Argosy writes a
 * live save; [claim] is what lets a reader refuse to adopt another account's progress.
 */
@Singleton
class SaveOwnershipTracker @Inject constructor(
    private val saveOwnershipDao: SaveOwnershipDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun signedInUserId(): Long? = userPreferencesRepository.preferences.first().rommUserId

    suspend fun claim(savePath: String, emulatorId: String): SaveClaim {
        val record = saveOwnershipDao.get(savePath, emulatorId) ?: return SaveClaim.Unowned
        val owner = record.ownerUserId ?: return SaveClaim.Unowned
        val current = signedInUserId() ?: return SaveClaim.Unowned
        return if (owner == current) SaveClaim.Mine else SaveClaim.Foreign(owner)
    }

    suspend fun record(savePath: String, emulatorId: String, contentHash: String?) {
        val ownerUserId = signedInUserId() ?: return
        val existing = saveOwnershipDao.get(savePath, emulatorId)
        saveOwnershipDao.upsert(
            SaveOwnershipEntity(
                id = existing?.id ?: 0,
                savePath = savePath,
                emulatorId = emulatorId,
                ownerUserId = ownerUserId,
                contentHash = contentHash,
                transitionState = SaveOwnershipEntity.STATE_STABLE,
                updatedAt = Instant.now()
            )
        )
    }

    suspend fun clear(savePath: String, emulatorId: String) {
        saveOwnershipDao.delete(savePath, emulatorId)
    }
}
