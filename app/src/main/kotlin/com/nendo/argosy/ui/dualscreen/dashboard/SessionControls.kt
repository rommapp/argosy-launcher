package com.nendo.argosy.ui.dualscreen.dashboard

data class DashboardQuickState(
    val slotNumber: Int,
    val savedAtMillis: Long,
    val screenshotPath: String?
)

/**
 * What the running game lets the in-game dashboard do, published by the game's activity. The
 * quick states are the rolling quick-save ring, newest first. The disc and mute rows exist only
 * for a game that publishes them.
 */
data class SessionControls(
    val quickStates: List<DashboardQuickState> = emptyList(),
    val cheatsAvailable: Boolean = false,
    val settingsAvailable: Boolean = false,
    val screenshotAvailable: Boolean = true,
    val discSwapAvailable: Boolean = false,
    val muteAvailable: Boolean = false,
    val isMuted: Boolean = false
)
