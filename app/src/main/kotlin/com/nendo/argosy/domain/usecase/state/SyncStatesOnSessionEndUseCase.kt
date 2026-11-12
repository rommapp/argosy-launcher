package com.nendo.argosy.domain.usecase.state

import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.emulator.StateSerialResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.preferences.AccountSwitchMarkerStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.DiscoveredState
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.StateClaim
import com.nendo.argosy.data.sync.StateOwnershipTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SyncStatesOnSessionEnd"

sealed class StateSyncResult {
    data class Cached(val count: Int, val queued: Int = 0) : StateSyncResult()
    data object NoStatesFound : StateSyncResult()
    data object NotConfigured : StateSyncResult()
    data class Error(val reason: StateSyncFailureReason) : StateSyncResult()
}

/**
 * Why [SyncStatesOnSessionEndUseCase] could not cache a game's states after a session.
 */
sealed class StateSyncFailureReason {
    data object GameNotFound : StateSyncFailureReason()
    data object NoLocalPath : StateSyncFailureReason()
}

class SyncStatesOnSessionEndUseCase @Inject constructor(
    private val stateCacheManager: StateCacheManager,
    private val gameDao: GameDao,
    private val activeSaveRepository: ActiveSaveRepository,
    private val coreVersionExtractor: CoreVersionExtractor,
    private val preferencesRepository: UserPreferencesRepository,
    private val stateOwnershipTracker: StateOwnershipTracker,
    private val emulatorResolver: EmulatorResolver,
    private val accountSwitchMarkerStore: AccountSwitchMarkerStore,
    private val stateSerialResolver: StateSerialResolver,
    private val fileAccessLayer: com.nendo.argosy.data.storage.FileAccessLayer
) {
    suspend operator fun invoke(
        gameId: Long,
        emulatorPackage: String,
        queueUploads: Boolean = true
    ): StateSyncResult = sync(gameId, emulatorPackage, queueUploads, skipKnownContent = false)

    /**
     * Adopts states written outside an Argosy session, which only happens with Secure Saves off.
     * A slot matching the cached state for the active channel is left alone, and a file older than
     * the server's copy is backed up as a state of its own.
     */
    suspend fun adoptOffSessionStates(
        gameId: Long,
        emulatorPackage: String,
        queueUploads: Boolean
    ): StateSyncResult {
        if (preferencesRepository.userPreferences.first().secureSaves) return StateSyncResult.NotConfigured
        if (accountSwitchMarkerStore.isSwitching()) {
            Log.i(TAG, "Account switch in progress, not adopting on-disk states")
            return StateSyncResult.NotConfigured
        }
        return sync(gameId, emulatorPackage, queueUploads, skipKnownContent = true)
    }

    /**
     * [adoptOffSessionStates] for every downloaded game with a server copy, for the periodic
     * reconcile that already sweeps saves the same way.
     */
    suspend fun adoptOffSessionStatesForDownloadedGames(): Int {
        if (preferencesRepository.userPreferences.first().secureSaves) return 0
        var adopted = 0
        for (game in gameDao.getByIdsChunked(gameDao.getDownloadedRommGameIds())) {
            val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
                game.id,
                game.platformId,
                game.platformSlug
            ) ?: continue
            val result = adoptOffSessionStates(game.id, emulatorPackage, queueUploads = true)
            if (result is StateSyncResult.Cached) adopted += result.count
        }
        return adopted
    }

    private suspend fun sync(
        gameId: Long,
        sessionPackage: String,
        queueUploads: Boolean,
        skipKnownContent: Boolean
    ): StateSyncResult {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.stateCacheEnabled) {
            Log.d(TAG, "State caching disabled")
            return StateSyncResult.NotConfigured
        }

        val game = gameDao.getById(gameId)
        if (game == null) {
            Log.w(TAG, "Game not found: $gameId")
            return StateSyncResult.Error(StateSyncFailureReason.GameNotFound)
        }

        val romPath = game.localPath
        if (romPath == null) {
            Log.w(TAG, "Game has no local path: $gameId")
            return StateSyncResult.Error(StateSyncFailureReason.NoLocalPath)
        }

        val sessionEmulator = emulatorResolver.resolveSessionEmulator(
            game.id,
            game.platformId,
            game.platformSlug,
            sessionPackage
        )
        if (sessionEmulator == null) {
            Log.w(TAG, "Unknown emulator: $sessionPackage")
            return StateSyncResult.NotConfigured
        }
        val emulatorId = sessionEmulator.emulatorId
        val emulatorPackage = sessionEmulator.packageName

        val config = StatePathRegistry.getConfig(emulatorId)
        if (config == null) {
            Log.d(TAG, "No state config for emulator: $emulatorId")
            return StateSyncResult.NotConfigured
        }

        val coreId = coreVersionExtractor.getCoreIdForEmulator(emulatorId, game.platformSlug)
        val coreVersion = if (coreId != null && emulatorId.startsWith("retroarch")) {
            coreVersionExtractor.getRetroArchCoreVersion(coreId, emulatorPackage)
        } else {
            null
        }

        val serials = stateSerialResolver.serialsFor(gameId, emulatorId)
        stateCacheManager.detachForeignSerialStates(gameId, serials)

        val discoveredStates = stateCacheManager.discoverStatesForGame(
            gameId = gameId,
            emulatorId = emulatorId,
            romPath = romPath,
            platformId = game.platformSlug,
            serials = serials,
            emulatorPackage = emulatorPackage,
            coreName = coreId
        )

        if (discoveredStates.isEmpty()) {
            Log.d(TAG, "No states found for game $gameId")
            return StateSyncResult.NoStatesFound
        }
        withContext(Dispatchers.IO) {
            fileAccessLayer.prepareSaveAccess(*discoveredStates.map { it.file.absolutePath }.toTypedArray())
        }

        var cachedCount = 0
        val channelName = activeSaveRepository.getActiveChannel(gameId)

        for (state in discoveredStates) {
            val claim = stateOwnershipTracker.claim(state.file.absolutePath, emulatorId)
            if (claim is StateClaim.Foreign) {
                Log.i(
                    TAG,
                    "Slot ${state.slotNumber} on disk belongs to user ${claim.ownerUserId}, not adopting | path=${state.file.absolutePath}"
                )
                continue
            }

            val existingCache = stateCacheManager.getStateBySlot(
                gameId = gameId,
                emulatorId = emulatorId,
                slotNumber = state.slotNumber,
                channelName = channelName
            )

            val screenshotFile = config.slotPattern.screenshotFile(state.file)
            val screenshotMissing = existingCache?.screenshotPath == null && screenshotFile.exists()

            val isNewer = existingCache != null && state.lastModified.isAfter(existingCache.cachedAt)
            Log.d(TAG, "Slot ${state.slotNumber}: fileModified=${state.lastModified}, cachedAt=${existingCache?.cachedAt}, isNewer=$isNewer, screenshotMissing=$screenshotMissing")

            val contentKnown = skipKnownContent && existingCache != null && isNewer &&
                !screenshotMissing && stateCacheManager.hasSameContent(existingCache, state.file)
            val shouldCache = !contentKnown && (existingCache == null || isNewer || screenshotMissing)

            if (shouldCache && skipKnownContent && existingCache != null && staleOnServer(existingCache, state)) {
                Log.i(
                    TAG,
                    "Slot ${state.slotNumber} has a newer server state; uploading the on-disk one as its own state"
                )
                stateCacheManager.clearServerLink(existingCache.id)
            }

            if (shouldCache) {
                val cacheId = stateCacheManager.cacheState(
                    gameId = gameId,
                    platformSlug = game.platformSlug,
                    emulatorId = emulatorId,
                    slotNumber = state.slotNumber,
                    statePath = state.file.absolutePath,
                    coreId = coreId,
                    coreVersion = coreVersion,
                    channelName = channelName,
                    isLocked = channelName != null
                )
                if (cacheId != null) {
                    cachedCount++
                    Log.d(TAG, "Cached state slot ${state.slotNumber} for game $gameId")
                    stateCacheManager.markForUpload(cacheId)
                }
            }
        }

        Log.d(TAG, "Cached $cachedCount states for game $gameId")

        val queuedCount = if (queueUploads && prefs.saveSyncEnabled && game.rommId != null) {
            queueStatesForUpload(gameId, game.rommId, emulatorId)
        } else {
            Log.d(TAG, "State cloud sync skipped: queueUploads=$queueUploads, saveSyncEnabled=${prefs.saveSyncEnabled}, rommId=${game.rommId}")
            0
        }

        return StateSyncResult.Cached(cachedCount, queuedCount)
    }

    private fun staleOnServer(existing: StateCacheEntity, discovered: DiscoveredState): Boolean {
        val serverUpdatedAt = existing.serverUpdatedAt ?: return false
        return existing.rommSaveId != null && serverUpdatedAt.isAfter(discovered.lastModified)
    }

    private suspend fun queueStatesForUpload(gameId: Long, rommId: Long, emulatorId: String): Int {
        val pendingStates = stateCacheManager.getByGameAndEmulator(gameId, emulatorId)
            .filter { it.syncStatus != StateCacheEntity.STATUS_SYNCED || it.rommSaveId == null }

        if (pendingStates.isEmpty()) {
            Log.d(TAG, "[StateSync] QUEUE gameId=$gameId | No states to queue")
            return 0
        }

        Log.d(TAG, "[StateSync] QUEUE gameId=$gameId | Queueing ${pendingStates.size} states for upload")

        var queuedCount = 0
        for (state in pendingStates) {
            val queued = stateCacheManager.queueStateForUpload(
                stateCacheId = state.id,
                gameId = gameId,
                rommId = rommId,
                emulatorId = emulatorId
            )
            if (queued) {
                queuedCount++
            }
        }

        Log.d(TAG, "[StateSync] QUEUE gameId=$gameId | Queued $queuedCount states")

        if (queuedCount > 0) {
            stateCacheManager.processPendingStateUploads()
        }

        return queuedCount
    }
}
