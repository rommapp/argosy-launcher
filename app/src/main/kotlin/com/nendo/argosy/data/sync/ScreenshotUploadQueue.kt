package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.repository.ScreenshotUploader
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotUploadQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val payloadCodec: SyncPayloadCodec,
    private val uploader: ScreenshotUploader,
    private val ledger: ScreenshotUploadLedger,
    private val notices: ScreenshotUploadNotices
) {
    sealed interface Outcome {
        data object Uploaded : Outcome
        data class Dropped(val reason: String) : Outcome
        data class Deferred(val reason: String) : Outcome
        data class Rejected(val reason: String) : Outcome
    }

    @Volatile private var swept = false

    suspend fun upload(item: PendingSyncQueueEntity, game: GameEntity?): Outcome {
        val payload = payloadCodec.decodeScreenshot(item.payloadJson)
            ?: return Outcome.Rejected("Unreadable screenshot payload")
        val copy = File(payload.localPath)
        if (!copy.exists()) return Outcome.Dropped("Upload copy missing")
        if (!item.targetsRomOf(game)) {
            PendingScreenshotStore.discard(context, copy)
            return Outcome.Dropped("Row rommId=${item.rommId} no longer matches game rommId=${game?.rommId}")
        }
        return when (val result = uploader.upload(copy, item.rommId)) {
            ScreenshotUploader.Result.Success -> {
                remember(item, payload)
                PendingScreenshotStore.discard(context, copy)
                Outcome.Uploaded
            }
            ScreenshotUploader.Result.NotSupported -> {
                PendingScreenshotStore.discard(context, copy)
                Outcome.Dropped("Server does not support screenshot upload")
            }
            ScreenshotUploader.Result.NotConnected -> Outcome.Deferred("Not connected")
            is ScreenshotUploader.Result.Failed -> Outcome.Deferred(result.reason)
            is ScreenshotUploader.Result.Rejected -> {
                remember(item, payload)
                PendingScreenshotStore.discard(context, copy)
                Outcome.Rejected(result.reason)
            }
        }
    }

    fun announce(uploaded: Int, rejected: Int) = notices.drainFinished(uploaded, rejected)

    /**
     * Once per process: returns FAILED screenshot rows to PENDING and deletes pending copies older
     * than [SWEEP_MIN_AGE_MS] that no row points at.
     */
    suspend fun tidyOnce(now: Long = System.currentTimeMillis()) {
        if (swept) return
        swept = true
        val revived = pendingSyncQueueDao.reviveFailedBySyncType(SyncType.SCREENSHOT)
        if (revived > 0) Logger.info(TAG, "tidyOnce: returned $revived failed screenshot uploads to the queue")

        val root = PendingScreenshotStore.root(context)
        val entries = root.listFiles() ?: return
        val referenced = pendingSyncQueueDao.getBySyncType(SyncType.SCREENSHOT)
            .mapNotNull { payloadCodec.decodeScreenshot(it.payloadJson)?.localPath }
            .toSet()
        var removed = 0
        for (entry in entries) {
            if (now - entry.lastModified() < SWEEP_MIN_AGE_MS) continue
            val prefix = entry.absolutePath + File.separator
            val inUse = if (entry.isDirectory) {
                referenced.any { it.startsWith(prefix) }
            } else {
                entry.absolutePath in referenced
            }
            if (!inUse && entry.deleteRecursively()) removed++
        }
        if (removed > 0) Logger.info(TAG, "tidyOnce: removed $removed pending screenshot copies no row points at")
    }

    private fun remember(item: PendingSyncQueueEntity, payload: ScreenshotPayload) {
        val source = payload.sourcePath ?: return
        val modifiedAt = payload.sourceModifiedAt ?: return
        ledger.record(item.ownerUserId, item.rommId, source, modifiedAt)
    }

    private companion object {
        const val TAG = "ScreenshotUploadQueue"
        const val SWEEP_MIN_AGE_MS = 10 * 60_000L
    }
}
