package com.nendo.argosy.domain.model

/**
 * One RetroAchievements community comment. [submitted] is ISO 8601 in UTC.
 */
data class AchievementComment(
    val user: String,
    val submitted: String,
    val text: String
)

sealed interface AchievementComments {
    data class Loaded(val comments: List<AchievementComment>) : AchievementComments
    data object NotSignedIn : AchievementComments
    data object NeedsKey : AchievementComments
    data object KeyRejected : AchievementComments
    data object Unavailable : AchievementComments
}
