package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RomMSave(
    @Json(name = "id") val id: Long,
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "user_id") val userId: Long,
    @Json(name = "emulator") val emulator: String?,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "file_name_no_tags") val fileNameNoTags: String? = null,
    @Json(name = "file_name_no_ext") val fileNameNoExt: String? = null,
    @Json(name = "file_extension") val fileExtension: String? = null,
    @Json(name = "file_path") val filePath: String? = null,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long = 0,
    @Json(name = "download_path") val downloadPath: String? = null,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "device_syncs") val deviceSyncs: List<RomMDeviceSync>? = null
)

@JsonClass(generateAdapter = true)
data class RomMSaveListResponse(
    @Json(name = "items") val items: List<RomMSave>? = null,
    @Json(name = "total") val total: Int? = null
)
