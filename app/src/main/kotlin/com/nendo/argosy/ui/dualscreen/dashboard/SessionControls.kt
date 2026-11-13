package com.nendo.argosy.ui.dualscreen.dashboard

import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.hardware.CompanionInGameState

data class DashboardQuickState(
    val slotNumber: Int,
    val savedAtMillis: Long,
    val screenshotPath: String?
)

/**
 * What the running game lets the in-game dashboard do, published by the game's activity. The
 * quick states are the rolling quick-save ring, newest first. The disc row exists only for a game
 * that publishes it. [statesAvailable] and [achievementsAvailable] are false for a game that has
 * no save states or no achievements at all, such as a stream on a container without them.
 */
data class SessionControls(
    val quickStates: List<DashboardQuickState> = emptyList(),
    val cheatsAvailable: Boolean = false,
    val settingsAvailable: Boolean = false,
    val screenshotAvailable: Boolean = true,
    val discSwapAvailable: Boolean = false,
    val statesAvailable: Boolean = true,
    val achievementsAvailable: Boolean = true
)

fun SessionControls.offersStates(state: CompanionInGameState): Boolean =
    statesAvailable && state.quickActionsAvailable && !state.isHardcore

fun SessionControls.achievementTotal(state: CompanionInGameState, achievements: List<AchievementUi>): Int = when {
    !achievementsAvailable -> 0
    achievements.isNotEmpty() -> achievements.size
    else -> state.achievementCount
}
