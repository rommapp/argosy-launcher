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

@JsonClass(generateAdapter = true)
data class RomMPlaySessionIngestResponse(
    @Json(name = "results") val results: List<RomMPlaySessionIngestResult> = emptyList(),
    @Json(name = "created_count") val createdCount: Int = 0,
    @Json(name = "skipped_count") val skippedCount: Int = 0
)
