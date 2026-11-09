package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.repository.ScreenshotUploader
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.sync.PendingScreenshotStore
import com.nendo.argosy.data.sync.ScreenshotPayload
import com.nendo.argosy.data.sync.ScreenshotUploadLedger
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queues every image in [ScreenshotFolderRegistry.folders] whose modification time falls inside a
 * play session for upload against that session's game. Returns how many rows it queued.
 */
@Singleton
class SessionScreenshotScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val payloadCodec: SyncPayloadCodec,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val connectionManager: dagger.Lazy<RomMConnectionManager>,
    private val fileAccessLayer: FileAccessLayer,
    private val folderRegistry: ScreenshotFolderRegistry,
    private val ledger: ScreenshotUploadLedger
) {
    suspend fun queueSessionScreenshots(gameId: Long, sessionStart: Instant, sessionEnd: Instant): Int =
        withContext(Dispatchers.IO) {
            val prefs = syncPreferencesRepository.preferences.first()
            if (!prefs.uploadScreenshotsEnabled) return@withContext 0
            val connection = connectionManager.get()
            if (connection.isConnected() && !connection.getCapabilities().supportsScreenshotUpload) {
                return@withContext 0
            }
            val rommId = gameDao.getById(gameId)?.rommId ?: return@withContext 0
            val ownerUserId = prefs.rommUserId

            val window = sessionStart.toEpochMilli()..sessionEnd.toEpochMilli()
            val candidates = folderRegistry.folders().flatMap { imagesIn(it, window) }
            if (candidates.isEmpty()) return@withContext 0

            val alreadyQueued = pendingSyncQueueDao.getByGameId(gameId)
                .filter { it.syncType == SyncType.SCREENSHOT }
                .mapNotNull { payloadCodec.decodeScreenshot(it.payloadJson)?.sourcePath }
                .toSet()

            var queued = 0
            for (source in candidates) {
                if (source.path in alreadyQueued) continue
                if (ledger.contains(ownerUserId, rommId, source.path, source.lastModified)) continue
                val copy = copyToPending(source) ?: continue
                pendingSyncQueueDao.insert(
                    PendingSyncQueueEntity(
                        gameId = gameId,
                        rommId = rommId,
                        syncType = SyncType.SCREENSHOT,
                        priority = SyncPriority.PROPERTY,
                        payloadJson = payloadCodec.encode(
                            ScreenshotPayload(
                                localPath = copy.absolutePath,
                                sourcePath = source.path,
                                sourceModifiedAt = source.lastModified
                            )
                        ),
                        ownerUserId = ownerUserId
                    )
                )
                queued++
            }
            Logger.info(TAG, "Queued $queued of ${candidates.size} session screenshots for gameId=$gameId rommId=$rommId")
            queued
        }

    private fun imagesIn(folder: String, window: LongRange): List<FileInfo> {
        val entries = fileAccessLayer.listFiles(folder) ?: return emptyList()
        return entries.filter { entry ->
            entry.isFile &&
                entry.size > 0 &&
                entry.lastModified in window &&
                entry.extension.lowercase() in ScreenshotUploader.MIME_BY_EXTENSION
        }
    }

    private fun copyToPending(source: FileInfo): File? {
        val slot = PendingScreenshotStore.slotFor(context, source.path, source.lastModified)
        val target = File(slot, source.name)
        return try {
            slot.mkdirs()
            val copied = fileAccessLayer.getInputStream(source.path)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (copied == null || target.length() <= 0) {
                Logger.warn(TAG, "Could not read screenshot ${source.path}; a later scan retries it")
                PendingScreenshotStore.discard(context, target)
                null
            } else {
                target
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "Could not copy screenshot ${source.path}; a later scan retries it", e)
            PendingScreenshotStore.discard(context, target)
            null
        }
    }

    private companion object {
        const val TAG = "SessionScreenshotScanner"
    }
}
