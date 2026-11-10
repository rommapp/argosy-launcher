package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.PlayRecordDeletionDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.cutoffsFor
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlaySessionBackfill"
private const val BATCH_SIZE = 100

/**
 * Uploads every play session RomM has not acknowledged for the signed-in account, in batches,
 * and answers how the batches fared. Runs by itself once per account and device after the
 * first RomM connect, then pulls the other devices' sessions down; callers run it on demand
 * after that.
 */
@Singleton
class PlaySessionBackfill @Inject constructor(
    private val playSessionDao: PlaySessionDao,
    private val playRecordDeletionDao: PlayRecordDeletionDao,
    private val uploader: RomMPlaySessionUploader,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val connectionManager: RomMConnectionManager,
    private val pull: PlaySessionPull
) {
    data class Summary(
        val sent: Int,
        val duplicates: Int,
        val failed: Int,
        val stoppedBy: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runMutex = Mutex()

    init {
        if (!com.nendo.argosy.libretro.slot.GameSlots.isSlotProcess) startMainProcessWork()
    }

    private fun startMainProcessWork() {
        scope.launch {
            connectionManager.connectionState.collect { state ->
                if (state is ConnectionState.Connected) runOnceAfterConnect()
            }
        }
    }

    suspend fun run(): Summary = runMutex.withLock {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val relinked = playSessionDao.relinkOrphans(ownerUserId)
        if (relinked > 0) Logger.info(TAG, "run: relinked $relinked sessions to current game rows")
        val cutoffs = playRecordDeletionDao.cutoffsFor(ownerUserId)
        var afterId = 0L
        var sent = 0
        var duplicates = 0
        var failed = 0
        while (true) {
            val batch = playSessionDao.getPendingForRomM(ownerUserId, afterId, BATCH_SIZE)
            if (batch.isEmpty()) break
            afterId = batch.last().session.id
            val sendable = batch.filterNot { cutoffs.blocks(it.session.gameId, it.session.startTime) }
            if (sendable.isEmpty()) continue
            when (val result = uploader.upload(sendable)) {
                is RomMPlaySessionUploader.UploadResult.Success -> {
                    sent += result.sent
                    duplicates += result.duplicates
                    failed += result.failed
                }
                is RomMPlaySessionUploader.UploadResult.Skipped -> {
                    Logger.debug(TAG, "run: skipped | ${result.reason}")
                    return Summary(sent, duplicates, failed, result.reason)
                }
                is RomMPlaySessionUploader.UploadResult.Error -> {
                    Logger.warn(TAG, "run: stopped | ${result.message}")
                    return Summary(sent, duplicates, failed, result.message)
                }
            }
        }
        syncPreferencesRepository.setRommPlaySessionLastUpload(Instant.now())
        Logger.info(TAG, "run: sent=$sent duplicates=$duplicates failed=$failed")
        Summary(sent, duplicates, failed)
    }

    suspend fun uploadThenPull(): Summary {
        val summary = run()
        if (summary.stoppedBy != null) return summary
        val pulled = pull.run()
        Logger.info(TAG, "pull after upload | added=${pulled.added} devices=${pulled.devices} stoppedBy=${pulled.stoppedBy}")
        return summary
    }

    internal suspend fun runOnceAfterConnect() {
        val userId = syncPreferencesRepository.getRommUserId() ?: return
        val deviceId = connectionManager.getDeviceId() ?: return
        val scopeKey = backfillScopeKey(userId, deviceId)
        if (syncPreferencesRepository.getRommPlaySessionBackfillDone() == scopeKey) return
        if (!uploader.canUpload) return
        val summary = run()
        if (summary.stoppedBy != null) return
        syncPreferencesRepository.setRommPlaySessionBackfillDone(scopeKey)
        Logger.info(TAG, "backfill complete | user=$userId sent=${summary.sent} duplicates=${summary.duplicates} failed=${summary.failed}")
        val pulled = pull.run()
        Logger.info(TAG, "first pull | added=${pulled.added} devices=${pulled.devices} stoppedBy=${pulled.stoppedBy}")
    }

    private fun backfillScopeKey(userId: Long, deviceId: String): String = "$userId:$deviceId"
}
