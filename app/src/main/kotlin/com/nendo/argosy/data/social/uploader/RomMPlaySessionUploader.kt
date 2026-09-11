package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.PendingRomMPlaySession
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMPlaySessionIngestPayload
import com.nendo.argosy.data.remote.romm.RomMPlaySessionIngestResult
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMPlaySessionUploader"
private const val MAX_BATCH_SIZE = 100
private const val ERROR_BODY_LOG_LIMIT = 2000
private const val STATUS_CREATED = "created"
private const val STATUS_DUPLICATE = "duplicate"

/**
 * Sends play sessions to the RomM ingest and stamps each accepted row with the id the server
 * answered, so the row never travels again. A batch the server rejects outright leaves every
 * row in it untouched; a row the server refuses individually stays unstamped for the next run.
 */
@Singleton
class RomMPlaySessionUploader @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val playSessionDao: PlaySessionDao
) {
    val canUpload: Boolean
        get() = connectionManager.getCapabilities().supportsPlaySessionIngest &&
            connectionManager.isConnected()

    suspend fun upload(pending: List<PendingRomMPlaySession>): UploadResult {
        if (pending.isEmpty()) return UploadResult.Success(0, 0, 0)
        if (!canUpload) return UploadResult.Skipped("Capability or connection unavailable")

        val api = connectionManager.getApi() ?: return UploadResult.Skipped("No api")
        val deviceId = connectionManager.getDeviceId() ?: return UploadResult.Skipped("No device id")

        var sent = 0
        var duplicates = 0
        var failed = 0
        for (batch in pending.chunked(MAX_BATCH_SIZE)) {
            val rows = ArrayList<PlaySessionEntity>(batch.size)
            val entries = batch.mapNotNull { row ->
                PlaySessionMapper.toRomMEntry(row.session, row.rommId)?.also { rows.add(row.session) }
            }
            if (entries.isEmpty()) continue

            val payload = RomMPlaySessionIngestPayload(deviceId = deviceId, sessions = entries)
            val response = try {
                api.ingestPlaySessions(payload)
            } catch (e: Exception) {
                Logger.error(TAG, "upload: ingest failed", e)
                return UploadResult.Error(e.message ?: "Network failure")
            }

            if (!response.isSuccessful) {
                val detail = try {
                    response.errorBody()?.string()?.take(ERROR_BODY_LOG_LIMIT)
                } catch (_: Exception) {
                    null
                }
                val msg = "Ingest returned ${response.code()}"
                Logger.error(TAG, "upload: $msg${detail?.let { " | $it" } ?: ""}")
                return UploadResult.Error(msg)
            }

            val outcome = recordResults(rows, response.body()?.results.orEmpty())
            sent += outcome.sent
            duplicates += outcome.duplicates
            failed += outcome.failed
        }
        Logger.info(TAG, "upload: sent=$sent duplicates=$duplicates failed=$failed of ${pending.size} pending")
        return UploadResult.Success(sent, duplicates, failed)
    }

    private suspend fun recordResults(
        rows: List<PlaySessionEntity>,
        results: List<RomMPlaySessionIngestResult>
    ): BatchOutcome {
        var sent = 0
        var duplicates = 0
        var acknowledged = 0
        for (result in results) {
            val row = result.index?.let { rows.getOrNull(it) } ?: continue
            when (result.status) {
                STATUS_CREATED -> {
                    val id = result.id ?: continue
                    playSessionDao.setRommSessionId(row.id, id)
                    sent++
                    acknowledged++
                }
                STATUS_DUPLICATE -> {
                    playSessionDao.setRommSessionId(
                        row.id,
                        result.id ?: PlaySessionEntity.ROMM_SESSION_ID_UNKNOWN
                    )
                    duplicates++
                    acknowledged++
                }
                else -> Logger.debug(TAG, "upload: row ${row.id} refused | ${result.status} ${result.detail}")
            }
        }
        return BatchOutcome(sent, duplicates, failed = rows.size - acknowledged)
    }

    private data class BatchOutcome(val sent: Int, val duplicates: Int, val failed: Int)

    sealed class UploadResult {
        data class Success(val sent: Int, val duplicates: Int, val failed: Int) : UploadResult()
        data class Skipped(val reason: String) : UploadResult()
        data class Error(val message: String) : UploadResult()
    }
}
