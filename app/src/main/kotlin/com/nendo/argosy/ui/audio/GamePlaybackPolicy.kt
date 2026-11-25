package com.nendo.argosy.ui.audio

/**
 * What the music player knows about the running game session, read once when the session opens.
 * [externalQuickPanelEnabled] is the user's "quick menu over external emulators" choice, already
 * false for a session the quick panel can never open over.
 */
data class GameSessionAudio(
    val isBuiltIn: Boolean,
    val canDrawOverlays: Boolean,
    val externalQuickPanelEnabled: Boolean,
    val accessibilityServiceEnabled: Boolean,
    val musicVolumePercent: Int
)

object GamePlaybackPolicy {

    const val LAUNCH_HOLD_WINDOW_MS = 60_000L

    /**
     * Whether music keeps playing while [session] runs. The launcher playlist and a game theme
     * preview always yield to the game. Other music keeps playing over a built-in session, and over
     * an external one only when the quick panel can open there to pause or skip it.
     */
    fun keepPlayingDuringGame(
        sourceIsLauncher: Boolean,
        themePreviewActive: Boolean,
        session: GameSessionAudio
    ): Boolean {
        if (sourceIsLauncher || themePreviewActive) return false
        if (session.isBuiltIn) return true
        return session.canDrawOverlays &&
            session.externalQuickPanelEnabled &&
            session.accessibilityServiceEnabled
    }

    fun duckedVolume(musicVolume: Float, duckPercent: Int): Float =
        (musicVolume.coerceIn(0f, 1f) * duckPercent.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)

    fun companionInputMayResume(session: GameSessionAudio?, keepsPlaying: Boolean): Boolean =
        session == null || keepsPlaying

    /**
     * Whether music the launcher faded on its way out, at [heldAtMs], comes back when the game's
     * session opens at [nowMs]. A hold older than [LAUNCH_HOLD_WINDOW_MS] belongs to something
     * other than this launch.
     */
    fun resumesAtSessionStart(keepsPlaying: Boolean, heldAtMs: Long?, nowMs: Long): Boolean {
        if (!keepsPlaying || heldAtMs == null) return false
        val age = nowMs - heldAtMs
        return age in 0..LAUNCH_HOLD_WINDOW_MS
    }
}
