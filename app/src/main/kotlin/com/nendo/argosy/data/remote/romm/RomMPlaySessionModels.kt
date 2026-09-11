package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RomMPlaySessionEntry(
    @Json(name = "rom_id") val romId: Long? = null,
    @Json(name = "save_slot") val saveSlot: String? = null,
    @Json(name = "start_time") val startTime: String,
    @Json(name = "end_time") val endTime: String,
    @Json(name = "duration_ms") val durationMs: Long
)

/**
 * Says a device is playing a game right now. Both fields must resolve server-side or the call is
 * refused: an unregistered device answers 404 rather than recording anonymous activity.
 */
@JsonClass(generateAdapter = true)
data class RomMActivityHeartbeatPayload(
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class RomMPlaySessionIngestPayload(
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "sessions") val sessions: List<RomMPlaySessionEntry>
)

@JsonClass(generateAdapter = true)
data class RomMPlaySessionIngestResult(
    @Json(name = "index") val index: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "id") val id: Long? = null,
    @Json(name = "detail") val detail: String? = null
)

/**
 * One session as RomM holds it. [id] is the dedup key for anything pulled back; the server
 * scopes GET to the calling device unless a device id is passed, so [deviceId] is always the
 * device that was asked for.
 */
@JsonClass(generateAdapter = true)
data class RomMPlaySession(
    @Json(name = "id") val id: Long,
    @Json(name = "user_id") val userId: Long? = null,
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "rom_id") val romId: Long? = null,
    @Json(name = "sync_session_id") val syncSessionId: Long? = null,
    @Json(name = "save_slot") val saveSlot: String? = null,
    @Json(name = "start_time") val startTime: String,
    @Json(name = "end_time") val endTime: String,
    @Json(name = "duration_ms") val durationMs: Long? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMPlaySessionIngestResponse(
    @Json(name = "results") val results: List<RomMPlaySessionIngestResult> = emptyList(),
    @Json(name = "created_count") val createdCount: Int = 0,
    @Json(name = "skipped_count") val skippedCount: Int = 0
)
