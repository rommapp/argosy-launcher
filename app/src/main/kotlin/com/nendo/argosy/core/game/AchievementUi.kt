package com.nendo.argosy.core.game

import com.nendo.argosy.data.local.entity.AchievementEntity

/**
 * RetroAchievements type tokens as RomM serialises them into [AchievementEntity.type]. Compared,
 * never displayed; labels live in ui/common/AchievementTypeUi.kt.
 */
object AchievementType {
    const val PROGRESSION = "progression"
    const val WIN_CONDITION = "win_condition"
    const val MISSABLE = "missable"
}

data class AchievementUi(
    val raId: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val type: String?,
    val badgeUrl: String?,
    val isUnlocked: Boolean = false,
    val isUnlockedHardcore: Boolean = false
)

fun AchievementEntity.toAchievementUi() = AchievementUi(
    raId = raId,
    title = title,
    description = description,
    points = points,
    type = type,
    badgeUrl = if (isUnlocked) {
        cachedBadgeUrl ?: badgeUrl
    } else {
        cachedBadgeUrlLock ?: badgeUrlLock ?: cachedBadgeUrl ?: badgeUrl
    },
    isUnlocked = isUnlocked,
    isUnlockedHardcore = unlockedHardcoreAt != null
)
