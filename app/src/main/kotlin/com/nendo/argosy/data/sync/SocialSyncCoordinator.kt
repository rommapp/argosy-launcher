package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.PendingSocialSyncDao
import com.nendo.argosy.data.local.entity.PendingSocialSyncEntity
import com.nendo.argosy.data.local.entity.SocialSyncStatus
import com.nendo.argosy.data.local.entity.SocialSyncType
import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.util.Logger
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialSyncCoordinator @Inject constructor(
    private val pendingSocialSyncDao: PendingSocialSyncDao,
    private val socialService: Lazy<ArgosSocialService>
) {
    companion object {
        private const val TAG = "SocialSyncCoordinator"
        private const val SYNC_RESPONSE_TIMEOUT_MS = 30_000L
    }

    private val mutex = Mutex()

    sealed class ProcessResult {
        data object NotConnected : ProcessResult()
        data class Completed(val processed: Int, val failed: Int) : ProcessResult()
    }

    suspend fun processQueue(): ProcessResult = withContext(Dispatchers.IO) {
        val service = socialService.get()
        if (!service.isConnected()) {
            Logger.debug(TAG, "processQueue: Not connected to social, skipping")
            return@withContext ProcessResult.NotConnected
        }

        mutex.withLock {
            pendingSocialSyncDao.resetInProgress()
            pendingSocialSyncDao.deleteExhausted()

            var processed = 0
            var failed = 0

            val sessionResult = processPlaySessions(service)
            processed += sessionResult.first
            failed += sessionResult.second

            val feedResult = processFeedEvents(service)
            processed += feedResult.first
            failed += feedResult.second

            Logger.info(TAG, "processQueue: Completed | processed=$processed, failed=$failed")
            ProcessResult.Completed(processed, failed)
        }
    }

    private suspend fun processPlaySessions(service: ArgosSocialService): Pair<Int, Int> {
        val pending = pendingSocialSyncDao.getPendingByType(SocialSyncType.PLAY_SESSION, 50)
        if (pending.isEmpty()) return 0 to 0

        Logger.debug(TAG, "processPlaySessions: ${pending.size} pending")

        pending.forEach { pendingSocialSyncDao.markInProgress(it.id) }

        val payloads = pending.mapNotNull { item ->
            try {
                parsePlaySessionPayload(item)
            } catch (e: Exception) {
                Logger.warn(TAG, "processPlaySessions: Failed to parse payload id=${item.id}", e)
                pendingSocialSyncDao.markFailed(item.id, "Invalid payload: ${e.message}")
                null
            }
        }

        if (payloads.isEmpty()) return 0 to pending.size

        service.syncPlaySessions(payloads)

        val results = withTimeoutOrNull(SYNC_RESPONSE_TIMEOUT_MS) {
            var received: List<ArgosSocialService.SessionSyncResult>? = null
            service.playSessionSyncResult.collect { result ->
                received = result
                return@collect
            }
            received
        }

        if (results == null) {
            Logger.warn(TAG, "processPlaySessions: Timed out waiting for response")
            pending.forEach { pendingSocialSyncDao.markFailed(it.id, "Sync response timeout") }
            return 0 to pending.size
        }

        var processed = 0
        var failed = 0

        val resultsByStartTime = results.associateBy { it.startTime }
        for (item in pending) {
            val payload = try {
                parsePlaySessionPayload(item)
            } catch (e: Exception) {
                continue
            }

            val result = resultsByStartTime[payload.startTime]
            if (result == null) {
                pendingSocialSyncDao.markFailed(item.id, "No result from server")
                failed++
                continue
            }

            when (result.status) {
                "accepted", "skipped" -> {
                    pendingSocialSyncDao.deleteById(item.id)
                    processed++
                }
                "rejected" -> {
                    pendingSocialSyncDao.markFailed(item.id, result.reason ?: "Rejected by server")
                    failed++
                }
                else -> {
                    pendingSocialSyncDao.markFailed(item.id, "Unknown status: ${result.status}")
                    failed++
                }
            }
        }

        return processed to failed
    }

    private suspend fun processFeedEvents(service: ArgosSocialService): Pair<Int, Int> {
        val pending = pendingSocialSyncDao.getPendingByType(SocialSyncType.FEED_EVENT, 50)
        if (pending.isEmpty()) return 0 to 0

        Logger.debug(TAG, "processFeedEvents: ${pending.size} pending")

        var processed = 0
        var failed = 0

        for (item in pending) {
            if (!service.isConnected()) break

            pendingSocialSyncDao.markInProgress(item.id)

            try {
                val json = JSONObject(item.payloadJson)
                val sent = service.createFeedEvent(
                    eventType = json.getString("event_type"),
                    igdbId = if (json.has("igdb_id") && !json.isNull("igdb_id")) json.getLong("igdb_id") else null,
                    gameTitle = json.getString("game_title"),
                    data = parseJsonMap(json.getJSONObject("data")),
                    occurredAt = item.occurredAt.toString()
                )
                if (sent) {
                    pendingSocialSyncDao.deleteById(item.id)
                    processed++
                } else {
                    pendingSocialSyncDao.markFailed(item.id, "Send failed")
                    failed++
                }
            } catch (e: Exception) {
                Logger.warn(TAG, "processFeedEvents: Failed id=${item.id}", e)
                pendingSocialSyncDao.markFailed(item.id, e.message ?: "Unknown error")
                failed++
            }
        }

        return processed to failed
    }

    private fun parsePlaySessionPayload(item: PendingSocialSyncEntity): ArgosSocialService.PlaySessionPayload {
        val json = JSONObject(item.payloadJson)
        return ArgosSocialService.PlaySessionPayload(
            userId = json.getString("user_id"),
            deviceId = json.getString("device_id"),
            deviceManufacturer = json.getString("device_manufacturer"),
            deviceModel = json.getString("device_model"),
            igdbId = if (json.has("igdb_id") && !json.isNull("igdb_id")) json.getLong("igdb_id") else null,
            gameTitle = json.getString("game_title"),
            platformSlug = json.getString("platform_slug"),
            startTime = json.getString("start_time"),
            endTime = json.getString("end_time"),
            continued = json.optBoolean("continued", false),
            standbyMs = json.optLong("standby_ms", 0),
            activePlayMs = json.optLong("active_play_ms", 0)
        )
    }

    private fun parseJsonMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in json.keys()) {
            map[key] = if (json.isNull(key)) null else json.get(key)
        }
        return map
    }
}
