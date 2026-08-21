package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.StateOwnershipDao
import com.nendo.argosy.data.local.entity.StateOwnershipEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface StateClaim {
    data object Unowned : StateClaim
    data object Mine : StateClaim
    data class Foreign(val ownerUserId: Long) : StateClaim
}

/**
 * Tracks which account wrote the bytes currently at a live save-state path.
 *
 * The peer of [SaveOwnershipTracker], and deliberately the same shape: recording happens wherever
 * Argosy writes a live state file, and [claim] is what lets session-end discovery refuse to adopt
 * and upload another account's state.
 *
 * The same row carries the per-artifact transition state an account switch advances through, so
 * this class is also the only writer of that state.
 */
@Singleton
class StateOwnershipTracker @Inject constructor(
    private val stateOwnershipDao: StateOwnershipDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun signedInUserId(): Long? = userPreferencesRepository.preferences.first().rommUserId

    suspend fun claim(statePath: String, emulatorId: String): StateClaim {
        val record = stateOwnershipDao.get(statePath, emulatorId) ?: return StateClaim.Unowned
        val owner = record.ownerUserId ?: return StateClaim.Unowned
        val current = signedInUserId() ?: return StateClaim.Unowned
        return if (owner == current) StateClaim.Mine else StateClaim.Foreign(owner)
    }

    /**
     * Records the signed-in account as the owner of the bytes now at [statePath].
     *
     * A row an account switch is part-way through is refreshed but never reset: the archive and
     * placement steps both write live bytes and would otherwise stamp the row back to stable,
     * erasing the only record of how far the switch had got.
     */
    suspend fun record(
        statePath: String,
        emulatorId: String,
        contentHash: String?,
        gameId: Long? = null,
        slotNumber: Int? = null,
        channelName: String? = null,
        coreId: String? = null,
        ownerUserIdOverride: Long? = null
    ) {
        val ownerUserId = ownerUserIdOverride ?: signedInUserId() ?: return
        val existing = stateOwnershipDao.get(statePath, emulatorId)
        if (existing != null && existing.transitionState != StateOwnershipEntity.STATE_STABLE) {
            stateOwnershipDao.upsert(
                existing.copy(
                    contentHash = contentHash,
                    gameId = gameId ?: existing.gameId,
                    slotNumber = slotNumber ?: existing.slotNumber,
                    channelName = channelName ?: existing.channelName,
                    coreId = coreId ?: existing.coreId,
                    updatedAt = Instant.now()
                )
            )
            return
        }
        stateOwnershipDao.upsert(
            StateOwnershipEntity(
                id = existing?.id ?: 0,
                statePath = statePath,
                emulatorId = emulatorId,
                ownerUserId = ownerUserId,
                contentHash = contentHash,
                transitionState = StateOwnershipEntity.STATE_STABLE,
                updatedAt = Instant.now(),
                gameId = gameId ?: existing?.gameId,
                slotNumber = slotNumber ?: existing?.slotNumber ?: 0,
                channelName = channelName ?: existing?.channelName,
                coreId = coreId ?: existing?.coreId,
                pendingOwnerUserId = null,
                archivedCacheId = null,
                incomingCacheId = null,
                needsSync = false
            )
        )
    }

    suspend fun clearForGame(gameId: Long) = stateOwnershipDao.deleteByGame(gameId)

    suspend fun clear(statePath: String, emulatorId: String) {
        stateOwnershipDao.delete(statePath, emulatorId)
    }

    suspend fun ownedBy(ownerUserId: Long): List<StateOwnershipEntity> =
        stateOwnershipDao.getByOwner(ownerUserId)

    suspend fun inTransition(): List<StateOwnershipEntity> = stateOwnershipDao.getInTransition()

    suspend fun advance(row: StateOwnershipEntity): StateOwnershipEntity {
        stateOwnershipDao.upsert(row.copy(updatedAt = Instant.now()))
        return stateOwnershipDao.get(row.statePath, row.emulatorId) ?: row
    }
}
