package com.nendo.argosy.domain.model

/**
 * What Argosy knows about the signed-in RetroAchievements account from its own cache: the points
 * and unlocks it has recorded, not the site-wide totals, which no client endpoint reports.
 */
data class RaAccountSummary(
    val username: String,
    val points: Int,
    val unlocks: Int,
    val latestTitle: String?,
    val latestGameId: Long?
)
