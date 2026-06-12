package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMPlaySessionIngestPayload
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMPlaySessionUploader"
private const val MAX_BATCH_SIZE = 100

@Singleton
class RomMPlaySessionUploader @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val gameDao: GameDao
) {
    val canUpload: Boolean
        get() = connectionManager.getCapabilities().supportsPlaySessionIngest &&
            connectionManager.isConnected()

    suspend fun upload(sessions: List<PlaySessionEntity>): UploadResult {
        if (sessions.isEmpty()) return UploadResult.Success(0)
        if (!canUpload) return UploadResult.Skipped("Capability or connection unavailable")

        val api = connectionManager.getApi() ?: return UploadResult.Skipped("No api")
        val deviceId = connectionManager.getDeviceId()

        val entries = sessions.mapNotNull { PlaySessionMapper.toRomMEntry(it, gameDao) }
        if (entries.isEmpty()) {
            Logger.debug(TAG, "upload: all ${sessions.size} sessions filtered out (degenerate or unmapped)")
            return UploadResult.Success(0)
        }

        var uploaded = 0
        for (batch in entries.chunked(MAX_BATCH_SIZE)) {
            val payload = RomMPlaySessionIngestPayload(deviceId = deviceId, sessions = batch)
            val response = try {
                api.ingestPlaySessions(payload)
            } catch (e: Exception) {
                Logger.error(TAG, "upload: ingest failed", e)
                return UploadResult.Error(e.message ?: "Network failure")
            }

            if (response.isSuccessful) {
                uploaded += response.body()?.createdCount ?: 0
            } else {
                val msg = "Ingest returned ${response.code()}"
                Logger.error(TAG, "upload: $msg")
                return UploadResult.Error(msg)
            }
        }
        Logger.info(TAG, "upload: ingested $uploaded play sessions (of ${entries.size} mapped)")
        return UploadResult.Success(uploaded)
    }

    sealed class UploadResult {
        data class Success(val ingested: Int) : UploadResult()
        data class Skipped(val reason: String) : UploadResult()
        data class Error(val message: String) : UploadResult()
    }
}
