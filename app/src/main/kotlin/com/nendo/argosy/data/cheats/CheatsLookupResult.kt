package com.nendo.argosy.data.cheats

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CheatsLookupResult(
    @Json(name = "game_id") val gameId: Int,
    @Json(name = "game_name") val gameName: String,
    @Json(name = "platform") val platform: String,
    @Json(name = "cheats") val cheats: List<CheatItem>,
    @Json(name = "score") val score: Double? = null
)

@JsonClass(generateAdapter = true)
data class CheatItem(
    @Json(name = "index") val index: Int,
    @Json(name = "description") val description: String,
    @Json(name = "code") val code: String
)
