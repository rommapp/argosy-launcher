package com.nendo.argosy.data.sync

import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.emulator.VersionValidationResult
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.dao.StateOwnershipDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.local.entity.StateOwnershipEntity
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AccountSwitchState"

/**
 * The per-artifact half of an account switch for save states, peer of
 * [AccountSwitchArtifactService] and driven through the same
 * STABLE -> RECLAIMING -> RECLAIMED -> CLEARED -> APPLYING -> STABLE machine, persisted on the
 * ownership row as each step completes.
 *
 * The invariant is the save side's: the live bytes are not removed until an archive of them has
 * been read back and hash-matched, and they are re-hashed immediately before removal so a state
 * written since the archive is left alone rather than lost.
 *
 * Three things differ from a save and are handled rather than papered over. One game has N
 * artifacts, not one, because states are per slot per channel per core, which is why an artifact
 * here is a slot and not a game. Each state carries a `.png` sidecar that travels with it in both
 * directions. And an incoming state whose core no longer matches is refused placement: an
 * unloadable state on disk is worse than an empty slot, so it stays cleared and flagged.
 */
@Singleton
class AccountSwitchStateService @Inject constructor(
    private val gameDao: GameDao,
    private val stateCacheDao: StateCacheDao,
    private val stateOwnershipDao: StateOwnershipDao,
    private val stateCacheManager: StateCacheManager,
    private val coreVersionExtractor: CoreVersionExtractor,
    private val emulatorResolver: EmulatorResolver
) {
    enum class TeardownResult { RECLAIMED, ABORTED, NOTHING_TO_DO }

    enum class PlacementResult { PLACED, NEEDS_SYNC, NOTHING_TO_PLACE, FAILED }

    /**
     * Archives, verifies, re-hashes and removes one outgoing state. Returns ABORTED when the live
     * bytes could not be proven identical to the archive, in which case they stay on disk.
     */
    suspend fun tearDown(
        row: StateOwnershipEntity,
        fromUserId: Long,
        toUserId: Long
    ): TeardownResult = withContext(Dispatchers.IO) {
        val gameId = row.gameId
        if (gameId == null) {
            Logger.warn(
                TAG,
                "Ownership row has no game attribution; leaving its bytes alone | path=${row.statePath}, emulator=${row.emulatorId}"
            )
            return@withContext TeardownResult.NOTHING_TO_DO
        }
        val game = gameDao.getById(gameId) ?: run {
            Logger.warn(TAG, "Game $gameId is gone; leaving its state bytes alone | path=${row.statePath}")
            return@withContext TeardownResult.NOTHING_TO_DO
        }

        if (!File(row.statePath).exists()) {
            Logger.debug(TAG, "Nothing on disk for game $gameId slot ${row.slotNumber}; the slot is already free")
            persist(
                row.copy(
                    transitionState = StateOwnershipEntity.STATE_CLEARED,
                    ownerUserId = null,
                    contentHash = null,
                    pendingOwnerUserId = toUserId
                )
            )
            return@withContext TeardownResult.NOTHING_TO_DO
        }

        val liveHash = stateCacheManager.calculateLiveStateHash(row.statePath)
        if (liveHash == null) {
            Logger.warn(TAG, "Could not hash live state for game $gameId; leaving it alone | path=${row.statePath}")
            return@withContext TeardownResult.ABORTED
        }

        var current = persist(
            row.copy(
                transitionState = StateOwnershipEntity.STATE_RECLAIMING,
                contentHash = liveHash,
                pendingOwnerUserId = toUserId
            )
        )

        val archiveId = archive(current, game, fromUserId)
        if (archiveId == null) {
            restoreStable(current, fromUserId)
            return@withContext TeardownResult.ABORTED
        }

        if (!stateCacheManager.verifyCachedState(archiveId, liveHash)) {
            Logger.error(
                TAG,
                "State archive for game $gameId slot ${row.slotNumber} did not verify; refusing to remove live state"
            )
            restoreStable(current, fromUserId)
            return@withContext TeardownResult.ABORTED
        }

        current = persist(
            current.copy(
                transitionState = StateOwnershipEntity.STATE_RECLAIMED,
                archivedCacheId = archiveId
            )
        )

        if (!removeVerified(current)) {
            restoreStable(current, fromUserId)
            return@withContext TeardownResult.ABORTED
        }

        persist(
            current.copy(
                transitionState = StateOwnershipEntity.STATE_CLEARED,
                ownerUserId = null,
                contentHash = null
            )
        )
        TeardownResult.RECLAIMED
    }

    /**
     * Redoes the removal half for a state that is already RECLAIMED. Recovery entry point: the
     * archive exists, so the only outstanding work is the re-hash and the delete.
     */
    suspend fun resumeRemoval(
        row: StateOwnershipEntity,
        fromUserId: Long
    ): TeardownResult = withContext(Dispatchers.IO) {
        val archiveId = row.archivedCacheId
        if (archiveId == null || !stateCacheManager.verifyCachedState(archiveId, row.contentHash)) {
            Logger.warn(TAG, "Resumed state has no verified archive; re-archiving | game=${row.gameId}")
            return@withContext tearDown(
                row.copy(transitionState = StateOwnershipEntity.STATE_RECLAIMING),
                fromUserId,
                row.pendingOwnerUserId ?: fromUserId
            )
        }
        if (!removeVerified(row)) {
            restoreStable(row, fromUserId)
            return@withContext TeardownResult.ABORTED
        }
        persist(
            row.copy(
                transitionState = StateOwnershipEntity.STATE_CLEARED,
                ownerUserId = null,
                contentHash = null
            )
        )
        TeardownResult.RECLAIMED
    }

    /**
     * Writes the incoming account's cached state, and its screenshot, into the live slot.
     *
     * Reads only the local cache, so it works with no server reachable. A slot whose newest local
     * copy is known to be behind the server, or whose core no longer matches, is left empty and
     * flagged rather than filled: an empty slot is recovered by a sync, an overwritten or
     * unloadable one is not.
     */
    suspend fun place(
        artifact: StateSwitchArtifact,
        toUserId: Long
    ): PlacementResult = withContext(Dispatchers.IO) {
        val priorRow = artifact.ownershipId?.let { stateOwnershipDao.getById(it) }
        val game = gameDao.getById(artifact.gameId) ?: run {
            priorRow?.let { stateOwnershipDao.delete(it.statePath, it.emulatorId) }
            return@withContext PlacementResult.NOTHING_TO_PLACE
        }

        val cache = artifact.cacheId?.let { stateCacheDao.getById(it) }
        if (cache == null) {
            priorRow?.let { stateOwnershipDao.delete(it.statePath, it.emulatorId) }
            return@withContext PlacementResult.NOTHING_TO_PLACE
        }

        val targetPath = resolveTargetPath(game, cache)
        if (targetPath == null) {
            Logger.warn(TAG, "Cannot resolve a state location for game ${artifact.gameId} slot ${cache.slotNumber}")
            markNeedsSync(priorRow, artifact, null, toUserId)
            return@withContext PlacementResult.NEEDS_SYNC
        }

        if (priorRow != null && priorRow.statePath != targetPath) {
            stateOwnershipDao.delete(priorRow.statePath, priorRow.emulatorId)
        }
        val existingRow = priorRow?.takeIf { it.statePath == targetPath }
            ?: stateOwnershipDao.get(targetPath, cache.emulatorId)

        if (heldByAnotherAccount(existingRow, toUserId)) {
            Logger.warn(
                TAG,
                "Refusing to place over a state still owned by user ${existingRow?.ownerUserId} | game=${artifact.gameId}, path=$targetPath"
            )
            return@withContext PlacementResult.FAILED
        }

        if (!isPlaceable(cache)) {
            Logger.info(
                TAG,
                "Leaving game ${artifact.gameId} slot ${cache.slotNumber} empty for user $toUserId: cached state is known to be behind the server"
            )
            markNeedsSync(existingRow, artifact, targetPath, toUserId)
            return@withContext PlacementResult.NEEDS_SYNC
        }

        if (coreRejects(game, cache)) {
            Logger.info(
                TAG,
                "Leaving game ${artifact.gameId} slot ${cache.slotNumber} empty for user $toUserId: cached state was written by a different core build"
            )
            markNeedsSync(existingRow, artifact, targetPath, toUserId)
            return@withContext PlacementResult.NEEDS_SYNC
        }

        val applying = persist(
            (existingRow ?: newRow(artifact, targetPath)).copy(
                statePath = targetPath,
                emulatorId = cache.emulatorId,
                gameId = artifact.gameId,
                slotNumber = cache.slotNumber,
                channelName = cache.channelName,
                coreId = cache.coreId,
                transitionState = StateOwnershipEntity.STATE_APPLYING,
                pendingOwnerUserId = toUserId,
                incomingCacheId = cache.id,
                needsSync = false
            )
        )

        if (!stateCacheManager.restoreState(cache.id, targetPath)) {
            Logger.error(TAG, "Failed to place state cache ${cache.id} for game ${artifact.gameId} at $targetPath")
            markNeedsSync(applying, artifact, targetPath, toUserId)
            return@withContext PlacementResult.FAILED
        }
        stateCacheManager.restoreStateScreenshot(cache.id, targetPath)

        persist(
            applying.copy(
                ownerUserId = toUserId,
                contentHash = stateCacheManager.calculateLiveStateHash(targetPath),
                transitionState = StateOwnershipEntity.STATE_STABLE,
                pendingOwnerUserId = null,
                archivedCacheId = null,
                incomingCacheId = null,
                needsSync = false
            )
        )
        PlacementResult.PLACED
    }

    /**
     * States the outgoing account still holds on disk, plus anything a previous attempt left
     * part-way through. Rows already past removal are handled by the placement pass instead.
     */
    suspend fun outgoingRows(fromUserId: Long?): List<StateOwnershipEntity> {
        val owned = fromUserId?.let { stateOwnershipDao.getByOwner(it) } ?: emptyList()
        val interrupted = stateOwnershipDao.getInTransition().filter {
            it.transitionState == StateOwnershipEntity.STATE_RECLAIMING ||
                it.transitionState == StateOwnershipEntity.STATE_RECLAIMED
        }
        return (owned + interrupted)
            .filter { it.transitionState != StateOwnershipEntity.STATE_CLEARED }
            .distinctBy { it.id }
    }

    /**
     * Every slot the incoming account should end up owning: the newest cached state per slot they
     * hold, plus any ownership row a previous attempt left cleared or mid-placement so it is
     * resolved rather than stranded.
     */
    suspend fun incomingArtifacts(toUserId: Long): List<StateSwitchArtifact> {
        val fromCache = stateCacheDao.getGameIdsForOwner(toUserId).flatMap { gameId ->
            stateCacheDao.getAllForOwnerAndGame(gameId, toUserId)
                .distinctBy { slotKey(it.emulatorId, it.slotNumber, it.channelName, it.coreId) }
                .map { cache ->
                    StateSwitchArtifact(
                        gameId = gameId,
                        emulatorId = cache.emulatorId,
                        slotNumber = cache.slotNumber,
                        channelName = cache.channelName,
                        coreId = cache.coreId,
                        cacheId = cache.id,
                        ownershipId = null,
                        statePath = null
                    )
                }
        }

        val covered = fromCache.map {
            it.gameId to slotKey(it.emulatorId, it.slotNumber, it.channelName, it.coreId)
        }.toSet()

        val leftovers = stateOwnershipDao.getInTransition()
            .filter {
                it.transitionState == StateOwnershipEntity.STATE_CLEARED ||
                    it.transitionState == StateOwnershipEntity.STATE_APPLYING
            }
            .mapNotNull { artifactFor(it) }
            .filter { (it.gameId to slotKey(it.emulatorId, it.slotNumber, it.channelName, it.coreId)) !in covered }

        return matchOwnership(fromCache) + leftovers
    }

    fun artifactFor(row: StateOwnershipEntity): StateSwitchArtifact? {
        val gameId = row.gameId ?: return null
        return StateSwitchArtifact(
            gameId = gameId,
            emulatorId = row.emulatorId,
            slotNumber = row.slotNumber,
            channelName = row.channelName,
            coreId = row.coreId,
            cacheId = null,
            ownershipId = row.id,
            statePath = row.statePath
        )
    }

    private suspend fun matchOwnership(artifacts: List<StateSwitchArtifact>): List<StateSwitchArtifact> {
        if (artifacts.isEmpty()) return artifacts
        val byGame = artifacts.groupBy { it.gameId }
        return byGame.flatMap { (gameId, slots) ->
            val rows = stateOwnershipDao.getByGame(gameId)
            slots.map { artifact ->
                val match = rows.firstOrNull {
                    it.emulatorId == artifact.emulatorId &&
                        it.slotNumber == artifact.slotNumber &&
                        it.channelName == artifact.channelName &&
                        it.coreId == artifact.coreId
                }
                if (match == null) artifact else artifact.copy(ownershipId = match.id, statePath = match.statePath)
            }
        }
    }

    private fun slotKey(emulatorId: String, slotNumber: Int, channelName: String?, coreId: String?): String =
        "$emulatorId|$slotNumber|${channelName ?: ""}|${coreId ?: ""}"

    /**
     * Whether a cached state is safe to write into a live slot.
     *
     * The save side gates on `serverCurrentAtSync`; states carry the same knowledge in their sync
     * status. SERVER_NEWER is the recorded "the server holds something later than this copy", and
     * placing it would overwrite the slot with a copy nobody chose. Everything else is either the
     * account's current progress or has never been near a server at all.
     */
    private fun isPlaceable(cache: StateCacheEntity): Boolean =
        cache.syncStatus != StateCacheEntity.STATUS_SERVER_NEWER

    /**
     * A slot whose teardown aborted still holds the outgoing account's only copy of those bytes,
     * because aborting is what happens when the live state could not be proven identical to the
     * archive. Writing the incoming account's state over it would destroy the very artifact the
     * abort existed to protect, so the slot is skipped and the switch reports it as failed.
     */
    private fun heldByAnotherAccount(row: StateOwnershipEntity?, toUserId: Long): Boolean {
        if (row == null) return false
        if (row.transitionState != StateOwnershipEntity.STATE_STABLE) return false
        val owner = row.ownerUserId ?: return false
        return owner != toUserId && File(row.statePath).exists()
    }

    private suspend fun coreRejects(game: GameEntity, cache: StateCacheEntity): Boolean {
        val currentCoreId = coreVersionExtractor.getCoreIdForEmulator(cache.emulatorId, game.platformSlug)
        val currentVersion = if (currentCoreId != null && cache.emulatorId.startsWith("retroarch")) {
            val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
                game.id,
                game.platformId,
                game.platformSlug
            )
            emulatorPackage?.let { coreVersionExtractor.getRetroArchCoreVersion(currentCoreId, it) }
        } else {
            null
        }
        val validation = stateCacheManager.validateCoreVersion(cache.id, currentCoreId, currentVersion)
        return validation is VersionValidationResult.Mismatch
    }

    private suspend fun archive(
        row: StateOwnershipEntity,
        game: GameEntity,
        fromUserId: Long
    ): Long? {
        val existing = stateCacheDao.getBySlotAndCore(
            gameId = game.id,
            emulatorId = row.emulatorId,
            slotNumber = row.slotNumber,
            channelName = row.channelName,
            coreId = row.coreId,
            ownerUserId = fromUserId
        )
        val cacheId = stateCacheManager.cacheState(
            gameId = game.id,
            platformSlug = game.platformSlug,
            emulatorId = row.emulatorId,
            slotNumber = row.slotNumber,
            statePath = row.statePath,
            coreId = row.coreId,
            coreVersion = existing?.coreVersion,
            channelName = row.channelName,
            isLocked = row.channelName != null,
            ownerUserIdOverride = fromUserId
        )
        if (cacheId == null || cacheId == 0L) {
            Logger.error(
                TAG,
                "Could not archive game ${game.id} slot ${row.slotNumber} before removal | path=${row.statePath}"
            )
            return null
        }
        stateCacheManager.reassignCacheOwner(cacheId, fromUserId)
        stateCacheManager.markForUpload(cacheId)
        return cacheId
    }

    /**
     * Deletes the state and its screenshot, but only after re-hashing the state against the
     * archive.
     *
     * The window between the archive and this call is exactly where an emulator Argosy did not
     * launch can write its own state, and that write would otherwise be deleted with no copy
     * anywhere. A mismatch aborts the artifact instead.
     */
    private suspend fun removeVerified(row: StateOwnershipEntity): Boolean {
        val expected = row.contentHash
        val liveHash = stateCacheManager.calculateLiveStateHash(row.statePath)
        if (expected == null || liveHash == null || expected != liveHash) {
            Logger.warn(
                TAG,
                "Live state changed since the archive; aborting this artifact | game=${row.gameId}, path=${row.statePath}, archived=$expected, live=$liveHash"
            )
            return false
        }
        val removed = stateCacheManager.deleteLiveState(row.statePath)
        if (!removed) {
            Logger.error(TAG, "Could not delete live state | game=${row.gameId}, path=${row.statePath}")
        }
        return removed
    }

    private suspend fun resolveTargetPath(game: GameEntity, cache: StateCacheEntity): String? {
        val romPath = game.localPath ?: return null
        val config = StatePathRegistry.getConfig(cache.emulatorId) ?: return null
        return stateCacheManager.buildStateTargetPath(
            config = config,
            platformId = game.platformSlug,
            romBaseName = File(romPath).nameWithoutExtension,
            slotNumber = cache.slotNumber,
            emulatorId = cache.emulatorId,
            coreName = cache.coreId,
            romPath = romPath,
            gameId = game.id
        )
    }

    private suspend fun markNeedsSync(
        row: StateOwnershipEntity?,
        artifact: StateSwitchArtifact,
        targetPath: String?,
        toUserId: Long
    ) {
        val base = row ?: targetPath?.let { newRow(artifact, it) } ?: return
        persist(
            base.copy(
                gameId = artifact.gameId,
                ownerUserId = toUserId,
                contentHash = null,
                transitionState = StateOwnershipEntity.STATE_CLEARED,
                pendingOwnerUserId = null,
                incomingCacheId = null,
                needsSync = true
            )
        )
    }

    private suspend fun restoreStable(row: StateOwnershipEntity, fromUserId: Long) {
        persist(
            row.copy(
                ownerUserId = fromUserId,
                transitionState = StateOwnershipEntity.STATE_STABLE,
                pendingOwnerUserId = null,
                needsSync = false
            )
        )
    }

    private fun newRow(artifact: StateSwitchArtifact, statePath: String) = StateOwnershipEntity(
        statePath = statePath,
        emulatorId = artifact.emulatorId,
        ownerUserId = null,
        contentHash = null,
        updatedAt = Instant.now(),
        gameId = artifact.gameId,
        slotNumber = artifact.slotNumber,
        channelName = artifact.channelName,
        coreId = artifact.coreId
    )

    /**
     * Writes the row, resolving its id from the path key first.
     *
     * The table is uniquely keyed on (statePath, emulatorId) while the primary key is the row id,
     * so an upsert carrying id 0 for a path that already has a row updates nothing at all. Every
     * state advance has to land, or recovery reads a state the disk has already moved past.
     */
    private suspend fun persist(row: StateOwnershipEntity): StateOwnershipEntity {
        val resolvedId = row.id.takeIf { it != 0L }
            ?: stateOwnershipDao.get(row.statePath, row.emulatorId)?.id
            ?: 0L
        val stamped = row.copy(id = resolvedId, updatedAt = Instant.now())
        stateOwnershipDao.upsert(stamped)
        return stateOwnershipDao.get(stamped.statePath, stamped.emulatorId) ?: stamped
    }
}
