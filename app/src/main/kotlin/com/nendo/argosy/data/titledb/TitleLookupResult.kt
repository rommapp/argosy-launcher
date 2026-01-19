package com.nendo.argosy.data.titledb

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TitleLookupResult(
    @Json(name = "title_id") val titleId: String,
    @Json(name = "name") val name: String,
    @Json(name = "platform") val platform: String,
    @Json(name = "score") val score: Double? = null
)

@JsonClass(generateAdapter = true)
data class TitleVariantsResult(
    @Json(name = "candidates") val candidates: List<TitleLookupResult>,
    @Json(name = "best_match") val bestMatch: TitleLookupResult?
)
