package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RomMDeviceRegistration(
    @Json(name = "name") val name: String,
    @Json(name = "platform") val platform: String = "android",
    @Json(name = "client") val client: String = "argosy",
    @Json(name = "client_version") val clientVersion: String,
    @Json(name = "hostname") val hostname: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMDevice(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String?,
    @Json(name = "platform") val platform: String?,
    @Json(name = "client") val client: String?,
    @Json(name = "client_version") val clientVersion: String?,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMDeviceRegistrationResponse(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMDeviceSync(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_name") val deviceName: String? = null,
    @Json(name = "last_synced_at") val lastSyncedAt: String? = null,
    @Json(name = "is_untracked") val isUntracked: Boolean = false,
    @Json(name = "is_current") val isCurrent: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMDeviceIdRequest(
    @Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class RomMSaveConflictResponse(
    @Json(name = "detail") val detail: RomMSaveConflictDetail? = null
)

@JsonClass(generateAdapter = true)
data class RomMSaveConflictDetail(
    @Json(name = "error") val error: String,
    @Json(name = "message") val message: String,
    @Json(name = "save_id") val saveId: Long,
    @Json(name = "current_save_time") val currentSaveTime: String,
    @Json(name = "device_sync_time") val deviceSyncTime: String
)
