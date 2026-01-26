package com.nendo.argosy.data.remote.ra

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RALoginResponse(
    @Json(name = "Success") val success: Boolean,
    @Json(name = "User") val user: String? = null,
    @Json(name = "Token") val token: String? = null,
    @Json(name = "Score") val score: Long? = null,
    @Json(name = "SoftcoreScore") val softcoreScore: Long? = null,
    @Json(name = "Messages") val messages: Int? = null,
    @Json(name = "Error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class RAAwardResponse(
    @Json(name = "Success") val success: Boolean,
    @Json(name = "Score") val score: Long? = null,
    @Json(name = "SoftcoreScore") val softcoreScore: Long? = null,
    @Json(name = "AchievementID") val achievementId: Long? = null,
    @Json(name = "AchievementsRemaining") val achievementsRemaining: Int? = null,
    @Json(name = "Error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class RABaseResponse(
    @Json(name = "Success") val success: Boolean,
    @Json(name = "Error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class RAGameInfoResponse(
    @Json(name = "Success") val success: Boolean,
    @Json(name = "PatchData") val patchData: RAPatchData? = null,
    @Json(name = "Error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class RAPatchData(
    @Json(name = "ID") val id: Long,
    @Json(name = "Title") val title: String? = null,
    @Json(name = "ConsoleID") val consoleId: Int? = null,
    @Json(name = "ImageIcon") val imageIcon: String? = null,
    @Json(name = "RichPresencePatch") val richPresencePatch: String? = null,
    @Json(name = "Achievements") val achievements: List<RAAchievementPatch>? = null
)

@JsonClass(generateAdapter = true)
data class RAAchievementPatch(
    @Json(name = "ID") val id: Long,
    @Json(name = "MemAddr") val memAddr: String,
    @Json(name = "Title") val title: String,
    @Json(name = "Description") val description: String? = null,
    @Json(name = "Points") val points: Int,
    @Json(name = "Author") val author: String? = null,
    @Json(name = "BadgeName") val badgeName: String? = null,
    @Json(name = "Flags") val flags: Int? = null,
    @Json(name = "Type") val type: String? = null
)

@JsonClass(generateAdapter = true)
data class RAStartSessionResponse(
    @Json(name = "Success") val success: Boolean,
    @Json(name = "HardcoreUnlocks") val hardcoreUnlocks: List<Long>? = null,
    @Json(name = "Unlocks") val unlocks: List<Long>? = null,
    @Json(name = "ServerNow") val serverNow: Long? = null,
    @Json(name = "Error") val error: String? = null
)

data class RACredentials(
    val username: String,
    val token: String
)
