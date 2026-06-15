package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.strategy.ConflictAutoResolver
import com.nendo.argosy.data.sync.strategy.ReconcileAction
import com.nendo.argosy.data.sync.strategy.ReconcileOperation
import com.nendo.argosy.util.Logger
import dagger.Lazy
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of applying a single ReconcileOperation: conflicts counts pending-conflict rows written, applied counts queued uploads/marked-server-newer rows touched. */
data class ReconcileEffectOutcome(val conflicts: Int, val applied: Int) {
    companion object {
        val NONE = ReconcileEffectOutcome(conflicts = 0, applied = 0)
    }

    operator fun plus(other: ReconcileEffectOutcome) =
        ReconcileEffectOutcome(conflicts + other.conflicts, applied + other.applied)
}

/** Pure-effect dispatcher: maps a ReconcileAction (UPLOAD / DOWNLOAD / CONFLICT / NO_OP) to its side effect (queue upload, mark save_sync SERVER_NEWER, write PendingConflict). Shared between SyncCoordinator's batch flow and the pre-launch single-operation flow so both go through the same action-to-effect mapping. */
@Singleton
class ReconcileEffectApplier @Inject constructor(
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val saveSyncDao: SaveSyncDao,
    private val gameDao: GameDao,
    private val pendingConflictDao: PendingConflictDao,
    private val conflictAutoResolver: ConflictAutoResolver,
    private val saveSyncRepository: Lazy<SaveSyncRepository>,
    private val saveCacheManager: Lazy<com.nendo.argosy.data.repository.SaveCacheManager>,
    private val payloadCodec: SyncPayloadCodec
) {

    suspend fun apply(op: ReconcileOperation, sessionId: Long?): ReconcileEffectOutcome {
        return when (op.action) {
            ReconcileAction.NO_OP -> ReconcileEffectOutcome.NONE
            ReconcileAction.UPLOAD -> {
                val applied = if (queueUploadForOp(op, sessionId)) 1 else 0
                ReconcileEffectOutcome(conflicts = 0, applied = applied)
            }
            ReconcileAction.DOWNLOAD -> {
                val applied = if (markServerNewerForOp(op)) 1 else 0
                ReconcileEffectOutcome(conflicts = 0, applied = applied)
            }
            ReconcileAction.CONFLICT -> dispatchConflict(op, sessionId)
        }
    }

    private suspend fun dispatchConflict(op: ReconcileOperation, sessionId: Long?): ReconcileEffectOutcome {
        val game = gameDao.getByRommId(op.romId)
        val existing = game?.id?.let { gid ->
            val emu = op.emulator ?: ""
            if (op.slot != null) {
                saveSyncDao.getByGameEmulatorAndChannel(gid, emu, op.slot)
            } else {
                saveSyncDao.getByGameAndEmulator(gid, emu)
            }
        }
        val clientHash = existing?.localSavePath?.let { saveCacheManager.get().calculateLocalSaveHash(it) }
        val localTime = resolveLocalTimeFromEntity(existing, fallback = null)

        return when (val res = conflictAutoResolver.classify(op, clientHash)) {
            is ConflictAutoResolver.Resolution.AsIs -> persistConflict(op, game, clientHash, localTime)
            is ConflictAutoResolver.Resolution.KeepLocal -> {
                val applied = if (queueUploadForOp(op, sessionId)) 1 else 0
                Logger.info(TAG, "auto-resolved conflict romId=${op.romId} -> KeepLocal (${res.ruleId})")
                ReconcileEffectOutcome(conflicts = 0, applied = applied)
            }
            is ConflictAutoResolver.Resolution.KeepServer -> {
                val applied = if (markServerNewerForOp(op)) 1 else 0
                Logger.info(TAG, "auto-resolved conflict romId=${op.romId} -> KeepServer (${res.ruleId})")
                ReconcileEffectOutcome(conflicts = 0, applied = applied)
            }
        }
    }

    private suspend fun persistConflict(
        op: ReconcileOperation,
        game: GameEntity?,
        clientHash: String?,
        localTime: Instant?
    ): ReconcileEffectOutcome {
        if (game == null) return ReconcileEffectOutcome.NONE
        val opServerTime = op.serverUpdatedAt?.let { parseInstantOrNull(it) }
        val existing = pendingConflictDao.findByGameAndSave(game.id, op.saveId)
        val previouslyDismissedUnchanged = existing != null &&
            existing.dismissed &&
            existing.serverUpdatedAt == opServerTime &&
            existing.serverHash == op.serverContentHash
        if (previouslyDismissedUnchanged) {
            Logger.debug(TAG, "applyPlan: skipping CONFLICT for romId=${op.romId} saveId=${op.saveId} (previously dismissed, server unchanged)")
            return ReconcileEffectOutcome.NONE
        }
        pendingConflictDao.upsert(
            PendingConflictEntity(
                gameId = game.id,
                rommSaveId = op.saveId,
                fileName = op.fileName,
                slot = op.slot,
                emulator = op.emulator,
                localUpdatedAt = localTime,
                serverUpdatedAt = opServerTime,
                localHash = clientHash,
                serverHash = op.serverContentHash,
                reason = op.reason
            )
        )
        return ReconcileEffectOutcome(conflicts = 1, applied = 0)
    }

    private suspend fun queueUploadForOp(op: ReconcileOperation, sessionId: Long?): Boolean {
        val game = gameDao.getByRommId(op.romId) ?: run {
            Logger.debug(TAG, "applyPlan UPLOAD: no local game for romId=${op.romId}, skipping")
            return false
        }
        val emulatorId = canonicalEmulatorId(op.emulator, game) ?: run {
            Logger.debug(TAG, "applyPlan UPLOAD: no canonical emulator for romId=${op.romId} (op.emulator=${op.emulator}), skipping")
            return false
        }
        val payload = SaveFilePayload(emulatorId = emulatorId, channelName = op.slot, source = QueueSource.NEGOTIATE)
        pendingSyncQueueDao.deleteByGameAndType(game.id, SyncType.SAVE_FILE)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = game.id,
                rommId = op.romId,
                syncType = SyncType.SAVE_FILE,
                priority = SyncPriority.SAVE_FILE,
                payloadJson = payloadCodec.encode(payload),
                sessionId = sessionId
            )
        )
        return true
    }

    private suspend fun markServerNewerForOp(op: ReconcileOperation): Boolean {
        val game = gameDao.getByRommId(op.romId) ?: run {
            Logger.debug(TAG, "applyPlan DOWNLOAD: no local game for romId=${op.romId}, skipping")
            return false
        }
        if (game.localPath == null) {
            Logger.debug(TAG, "applyPlan DOWNLOAD: game ${game.id} has no local ROM, skipping")
            return false
        }
        val emulatorId = canonicalEmulatorId(op.emulator, game) ?: run {
            Logger.debug(TAG, "applyPlan DOWNLOAD: no canonical emulator for gameId=${game.id} (op.emulator=${op.emulator}), skipping")
            return false
        }
        val existing = if (op.slot != null) {
            saveSyncDao.getByGameEmulatorAndChannel(game.id, emulatorId, op.slot)
        } else {
            saveSyncDao.getByGameAndEmulator(game.id, emulatorId)
        }
        val serverTime = op.serverUpdatedAt?.let { parseInstantOrNull(it) }
        saveSyncDao.upsert(
            SaveSyncEntity(
                id = existing?.id ?: 0,
                gameId = game.id,
                rommId = op.romId,
                emulatorId = emulatorId,
                channelName = op.slot,
                rommSaveId = op.saveId,
                localSavePath = existing?.localSavePath,
                localUpdatedAt = existing?.localUpdatedAt,
                serverUpdatedAt = serverTime,
                lastSyncedAt = existing?.lastSyncedAt,
                syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER,
                lastUploadedHash = existing?.lastUploadedHash,
                localContentHash = existing?.localContentHash,
                lastSyncDeviceId = existing?.lastSyncDeviceId,
                lastSyncDeviceName = existing?.lastSyncDeviceName
            )
        )
        return true
    }

    private fun resolveLocalTimeFromEntity(existing: SaveSyncEntity?, fallback: Instant?): Instant? {
        if (existing == null) return fallback
        val fileMtime = existing.localSavePath?.let { path ->
            val f = File(path)
            if (f.exists()) Instant.ofEpochMilli(f.lastModified()) else null
        }
        return when (existing.syncStatus) {
            SaveSyncEntity.STATUS_SYNCED, SaveSyncEntity.STATUS_SERVER_NEWER ->
                existing.serverUpdatedAt ?: fileMtime ?: existing.localUpdatedAt ?: fallback
            else -> fileMtime ?: existing.localUpdatedAt ?: fallback
        }
    }

    private fun parseInstantOrNull(value: String): Instant? = try {
        Instant.parse(value)
    } catch (_: Exception) {
        null
    }

    private suspend fun canonicalEmulatorId(raw: String?, game: GameEntity): String? {
        return saveSyncRepository.get().resolveEmulatorForGame(game)
            ?: raw?.takeUnless { it.isBlank() || it == "default" }
    }

    companion object {
        private const val TAG = "ReconcileEffectApplier"
    }
}
