package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RomMClientSaveState(
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "slot") val slot: String? = null,
    @Json(name = "emulator") val emulator: String? = null,
    @Json(name = "content_hash") val contentHash: String? = null,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long
)

@JsonClass(generateAdapter = true)
data class RomMSyncNegotiatePayload(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "saves") val saves: List<RomMClientSaveState>
)

@JsonClass(generateAdapter = true)
data class RomMSyncOperation(
    @Json(name = "action") val action: String,
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "save_id") val saveId: Long? = null,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "slot") val slot: String? = null,
    @Json(name = "emulator") val emulator: String? = null,
    @Json(name = "reason") val reason: String,
    @Json(name = "server_updated_at") val serverUpdatedAt: String? = null,
    @Json(name = "server_content_hash") val serverContentHash: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMSyncNegotiateResponse(
    @Json(name = "session_id") val sessionId: Long,
    @Json(name = "operations") val operations: List<RomMSyncOperation>,
    @Json(name = "total_upload") val totalUpload: Int = 0,
    @Json(name = "total_download") val totalDownload: Int = 0,
    @Json(name = "total_conflict") val totalConflict: Int = 0,
    @Json(name = "total_no_op") val totalNoOp: Int = 0
)

@JsonClass(generateAdapter = true)
data class RomMSyncCompletePayload(
    @Json(name = "operations_completed") val operationsCompleted: Int = 0,
    @Json(name = "operations_failed") val operationsFailed: Int = 0,
    @Json(name = "play_sessions") val playSessions: List<RomMPlaySessionEntry>? = null
)

@JsonClass(generateAdapter = true)
data class RomMSyncSession(
    @Json(name = "id") val id: Long,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "user_id") val userId: Long,
    @Json(name = "status") val status: String,
    @Json(name = "initiated_at") val initiatedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "operations_planned") val operationsPlanned: Int = 0,
    @Json(name = "operations_completed") val operationsCompleted: Int = 0,
    @Json(name = "operations_failed") val operationsFailed: Int = 0,
    @Json(name = "error_message") val errorMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMSyncCompleteResponse(
    @Json(name = "session") val session: RomMSyncSession,
    @Json(name = "play_session_ingest") val playSessionIngest: RomMPlaySessionIngestResponse? = null
)
