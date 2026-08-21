package com.nendo.argosy.domain.usecase.state

import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.emulator.VersionValidationResult
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.remote.romm.RomMState
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.domain.model.UnifiedStateEntry
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

private const val TAG = "GetUnifiedStates"

class GetUnifiedStatesUseCase @Inject constructor(
    private val stateCacheManager: StateCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val coreVersionExtractor: CoreVersionExtractor,
    private val emulatorResolver: EmulatorResolver
) {
    suspend operator fun invoke(
        gameId: Long,
        emulatorId: String? = null,
        channelName: String? = null,
        currentCoreId: String? = null,
        currentCoreVersion: String? = null
    ): List<UnifiedStateEntry> {
        val game = gameDao.getById(gameId) ?: return emptyList()
        val rommId = game.rommId

        val effectiveEmulatorId = emulatorId
            ?: emulatorResolver.getEmulatorIdForGame(gameId, game.platformId, game.platformSlug)
        if (effectiveEmulatorId == null) {
            Log.w(TAG, "No emulator resolved for gameId=$gameId (${game.platformSlug}), no states")
            return emptyList()
        }

        val remoteOnlyStates = if (rommId != null) {
            syncServerStates(rommId, gameId, game.platformSlug, effectiveEmulatorId, channelName, currentCoreId)
        } else emptyList()

        val localStates = if (channelName != null) {
            stateCacheManager.getStatesForChannel(gameId, channelName)
        } else {
            stateCacheManager.getDefaultChannelStates(gameId)
        }

        val config = StatePathRegistry.getConfig(effectiveEmulatorId)
        val maxSlots = config?.maxSlots ?: 10

        return buildSlotList(
            localStates = localStates,
            remoteOnlyStates = remoteOnlyStates,
            maxSlots = maxSlots,
            channelName = channelName,
            currentCoreId = currentCoreId,
            currentCoreVersion = currentCoreVersion
        )
    }

    private suspend fun syncServerStates(
        rommId: Long,
        gameId: Long,
        platformSlug: String,
        emulatorId: String,
        channelName: String?,
        coreId: String?
    ): List<RomMState> {
        val api = saveSyncRepository.getApi() ?: return emptyList()

        saveSyncRepository.checkSavesForGame(gameId, rommId)

        val serverStates = stateCacheManager.checkServerStates(rommId, api)

        val channelMatched = serverStates.filter { serverState ->
            val parsed = stateCacheManager.parseStateFileName(serverState.fileName)
            channelName == null || parsed.channelName == channelName
        }

        channelMatched
            .groupBy { stateCacheManager.parseStateFileName(it.fileName).slotNumber }
            .forEach { (_, states) ->
                if (states.size <= 1) return@forEach
                val canonical = states.maxByOrNull {
                    stateCacheManager.parseStateFileTimestamp(it.fileName) ?: Instant.EPOCH
                } ?: return@forEach
                states.filter { it.id != canonical.id }
                    .forEach { stateCacheManager.tombstoneServerState(gameId, it.id) }
            }

        stateCacheManager.pruneStateTombstones(gameId, serverStates.map { it.id })
        val tombstoned = stateCacheManager.getStateTombstones(gameId)

        stateCacheManager.getByGameAndEmulator(gameId, emulatorId)
            .filter { it.rommSaveId != null && it.rommSaveId in tombstoned }
            .forEach { stateCacheManager.clearServerLink(it.id) }

        val localByRommId = stateCacheManager.getByGameAndEmulator(gameId, emulatorId)
            .filter { it.rommSaveId != null }.associateBy { it.rommSaveId }

        val toFetch = channelMatched.filter { serverState ->
            serverState.id !in tombstoned && localByRommId[serverState.id] == null
        }

        if (toFetch.isNotEmpty()) {
            val gate = Semaphore(MAX_PARALLEL_STATE_DOWNLOADS)
            coroutineScope {
                toFetch.map { serverState ->
                    async {
                        gate.withPermit {
                            stateCacheManager.downloadStateFromRomM(
                                rommStateId = serverState.id,
                                fileName = serverState.fileName,
                                api = api,
                                gameId = gameId,
                                platformSlug = platformSlug,
                                emulatorId = emulatorId,
                                coreId = coreId,
                                serverState = serverState
                            )
                        }
                    }
                }.awaitAll()
            }
        }

        return channelMatched.filter { it.id in tombstoned }
    }

    companion object {
        private const val MAX_PARALLEL_STATE_DOWNLOADS = 4
    }

    private fun buildSlotList(
        localStates: List<StateCacheEntity>,
        remoteOnlyStates: List<RomMState>,
        maxSlots: Int,
        channelName: String?,
        currentCoreId: String?,
        currentCoreVersion: String?
    ): List<UnifiedStateEntry> {
        val statesBySlot = localStates.associateBy { it.slotNumber }
        val remoteBySlot = remoteOnlyStates
            .groupBy { stateCacheManager.parseStateFileName(it.fileName).slotNumber }
            .mapValues { (_, states) ->
                states.maxByOrNull { stateCacheManager.parseStateFileTimestamp(it.fileName) ?: Instant.EPOCH }!!
            }
            .filterKeys { statesBySlot[it] == null }
        val result = mutableListOf<UnifiedStateEntry>()

        result.add(slotEntry(-1, statesBySlot, remoteBySlot, channelName, currentCoreId, currentCoreVersion))

        val slotsToShow = if (maxSlots < 0) {
            (localStates.map { it.slotNumber } + remoteBySlot.keys)
                .filter { it >= 0 }.maxOrNull()?.plus(1) ?: 10
        } else {
            maxSlots
        }

        for (slot in 0 until slotsToShow) {
            result.add(slotEntry(slot, statesBySlot, remoteBySlot, channelName, currentCoreId, currentCoreVersion))
        }

        return result
    }

    private fun slotEntry(
        slot: Int,
        statesBySlot: Map<Int, StateCacheEntity>,
        remoteBySlot: Map<Int, RomMState>,
        channelName: String?,
        currentCoreId: String?,
        currentCoreVersion: String?
    ): UnifiedStateEntry {
        statesBySlot[slot]?.let {
            return createEntry(it, channelName, currentCoreId, currentCoreVersion)
        }
        remoteBySlot[slot]?.let {
            return createServerOnlyEntry(it, slot, channelName)
        }
        return UnifiedStateEntry.empty(slot)
    }

    private fun createServerOnlyEntry(
        state: RomMState,
        slot: Int,
        channelName: String?
    ): UnifiedStateEntry {
        val parsed = stateCacheManager.parseStateFileName(state.fileName)
        return UnifiedStateEntry(
            localCacheId = null,
            serverStateId = state.id,
            slotNumber = slot,
            timestamp = stateCacheManager.parseServerStateTimestamp(state.updatedAt) ?: Instant.EPOCH,
            size = state.fileSizeBytes,
            channelName = channelName ?: parsed.channelName,
            coreId = null,
            coreVersion = null,
            screenshotPath = null,
            source = UnifiedStateEntry.Source.SERVER,
            isActive = false,
            isLocked = false,
            versionStatus = UnifiedStateEntry.VersionStatus.UNKNOWN,
            syncStatus = UnifiedStateEntry.SyncStatus.SERVER_ONLY
        )
    }

    private fun createEntry(
        cache: StateCacheEntity,
        channelName: String?,
        currentCoreId: String?,
        currentCoreVersion: String?
    ): UnifiedStateEntry {
        val versionStatus = determineVersionStatus(
            cache.coreId,
            cache.coreVersion,
            currentCoreId,
            currentCoreVersion
        )

        val syncStatus = when {
            cache.rommSaveId != null && cache.syncStatus == StateCacheEntity.STATUS_SYNCED ->
                UnifiedStateEntry.SyncStatus.SYNCED
            cache.syncStatus == StateCacheEntity.STATUS_PENDING_UPLOAD ||
            cache.syncStatus == StateCacheEntity.STATUS_LOCAL_NEWER ->
                UnifiedStateEntry.SyncStatus.PENDING_UPLOAD
            else ->
                UnifiedStateEntry.SyncStatus.LOCAL_ONLY
        }

        return UnifiedStateEntry(
            localCacheId = cache.id,
            serverStateId = cache.rommSaveId,
            slotNumber = cache.slotNumber,
            timestamp = cache.cachedAt,
            size = cache.stateSize,
            channelName = channelName,
            coreId = cache.coreId,
            coreVersion = cache.coreVersion,
            screenshotPath = stateCacheManager.getScreenshotPath(cache),
            source = if (cache.rommSaveId != null) UnifiedStateEntry.Source.BOTH else UnifiedStateEntry.Source.LOCAL,
            isActive = false,
            isLocked = cache.isLocked,
            versionStatus = versionStatus,
            syncStatus = syncStatus
        )
    }

    private fun determineVersionStatus(
        savedCoreId: String?,
        savedVersion: String?,
        currentCoreId: String?,
        currentVersion: String?
    ): UnifiedStateEntry.VersionStatus {
        if (savedCoreId != null && currentCoreId != null && savedCoreId != currentCoreId) {
            return UnifiedStateEntry.VersionStatus.MISMATCH
        }

        return when (coreVersionExtractor.validateVersion(savedVersion, currentVersion)) {
            is VersionValidationResult.Compatible -> UnifiedStateEntry.VersionStatus.COMPATIBLE
            is VersionValidationResult.Mismatch -> UnifiedStateEntry.VersionStatus.MISMATCH
            is VersionValidationResult.Unknown -> UnifiedStateEntry.VersionStatus.UNKNOWN
        }
    }

}
