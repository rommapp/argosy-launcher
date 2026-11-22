package com.nendo.argosy.data.remote.ra

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

/**
 * `API_GetUserSummary` subset. [richPresenceMsgDate] is `YYYY-MM-DD HH:MM:SS` in UTC.
 */
@JsonClass(generateAdapter = true)
data class RAWebUserSummary(
    @Json(name = "RichPresenceMsg") val richPresenceMsg: String? = null,
    @Json(name = "RichPresenceMsgDate") val richPresenceMsgDate: String? = null,
    @Json(name = "LastGameID") val lastGameId: Long? = null
)

/**
 * One achievement of `API_GetGameInfoAndUserProgress`. The two dates are `YYYY-MM-DD HH:MM:SS` in
 * UTC and absent while the user has not earned the achievement in that mode.
 */
@JsonClass(generateAdapter = true)
data class RAWebAchievementProgress(
    @Json(name = "ID") val id: Long,
    @Json(name = "BadgeName") val badgeName: String? = null,
    @Json(name = "DateEarned") val dateEarned: String? = null,
    @Json(name = "DateEarnedHardcore") val dateEarnedHardcore: String? = null
)

/**
 * `API_GetGameInfoAndUserProgress` subset, keyed by achievement id. [highestAwardDate] is ISO 8601
 * with an offset.
 */
@JsonClass(generateAdapter = true)
data class RAWebGameProgress(
    @Json(name = "ID") val id: Long? = null,
    @Json(name = "Achievements") val achievements: RAWebAchievements = RAWebAchievements(),
    @Json(name = "HighestAwardKind") val highestAwardKind: String? = null,
    @Json(name = "HighestAwardDate") val highestAwardDate: String? = null
)

@JsonClass(generateAdapter = true)
data class RAWebCompletionPage(
    @Json(name = "Count") val count: Int = 0,
    @Json(name = "Total") val total: Int = 0,
    @Json(name = "Results") val results: List<RAWebCompletionEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RAWebCommentsPage(
    @Json(name = "Count") val count: Int = 0,
    @Json(name = "Total") val total: Int = 0,
    @Json(name = "Results") val results: List<RAWebComment> = emptyList()
)

/**
 * One comment of `API_GetComments`. [submitted] is ISO 8601 in UTC.
 */
@JsonClass(generateAdapter = true)
data class RAWebComment(
    @Json(name = "User") val user: String? = null,
    @Json(name = "Submitted") val submitted: String? = null,
    @Json(name = "CommentText") val commentText: String? = null
)

/**
 * One game of `API_GetUserCompletionProgress`. [highestAwardKind] is one of `beaten-softcore`,
 * `beaten-hardcore`, `completed`, `mastered`; the dates are ISO 8601 with an offset.
 */
@JsonClass(generateAdapter = true)
data class RAWebCompletionEntry(
    @Json(name = "GameID") val gameId: Long,
    @Json(name = "MaxPossible") val maxPossible: Int? = null,
    @Json(name = "NumAwarded") val numAwarded: Int? = null,
    @Json(name = "NumAwardedHardcore") val numAwardedHardcore: Int? = null,
    @Json(name = "MostRecentAwardedDate") val mostRecentAwardedDate: String? = null,
    @Json(name = "HighestAwardKind") val highestAwardKind: String? = null,
    @Json(name = "HighestAwardDate") val highestAwardDate: String? = null
)

/**
 * One unlock of `API_GetAchievementsEarnedBetween`. [date] is `YYYY-MM-DD HH:MM:SS` in UTC.
 * [isHardcore] reads [hardcoreMode] as a number, a boolean or a string.
 */
@JsonClass(generateAdapter = true)
data class RAWebEarnedAchievement(
    @Json(name = "Date") val date: String? = null,
    @Json(name = "HardcoreMode") val hardcoreMode: Any? = null,
    @Json(name = "AchievementID") val achievementId: Long? = null,
    @Json(name = "Title") val title: String? = null,
    @Json(name = "Description") val description: String? = null,
    @Json(name = "BadgeName") val badgeName: String? = null,
    @Json(name = "Points") val points: Int? = null,
    @Json(name = "Type") val type: String? = null,
    @Json(name = "GameID") val gameId: Long? = null,
    @Json(name = "GameTitle") val gameTitle: String? = null
) {
    val isHardcore: Boolean
        get() = when (val mode = hardcoreMode) {
            is Boolean -> mode
            is Number -> mode.toInt() == 1
            is String -> mode == "1" || mode.equals("true", ignoreCase = true)
            else -> false
        }
}

/**
 * A game's achievements keyed by achievement id; an empty JSON array reads as none.
 */
data class RAWebAchievements(val byId: Map<String, RAWebAchievementProgress> = emptyMap())

@JvmSuppressWildcards
object RAWebAchievementMapAdapter {

    @FromJson
    fun fromJson(
        reader: JsonReader,
        delegate: JsonAdapter<Map<String, RAWebAchievementProgress>>
    ): RAWebAchievements {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return RAWebAchievements()
        }
        return RAWebAchievements(delegate.fromJson(reader).orEmpty())
    }

    @ToJson
    fun toJson(
        writer: JsonWriter,
        value: RAWebAchievements,
        delegate: JsonAdapter<Map<String, RAWebAchievementProgress>>
    ) {
        delegate.toJson(writer, value.byId)
    }
}
