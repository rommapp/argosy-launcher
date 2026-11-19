package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.components.GameVideoClip
import kotlinx.coroutines.delay

internal suspend fun scheduleHomeVideoPreview(
    state: HomeUiState,
    blocked: Boolean,
    suppressed: Boolean,
    reducedMotion: Boolean,
    alreadyPlayed: Boolean,
    clipFor: (HomeGameUi) -> GameVideoClip?,
    canStart: (Long) -> Boolean,
    deactivate: () -> Unit,
    start: (Long, GameVideoClip) -> Unit
) {
    deactivate()
    if (!state.videoWallpaperEnabled || !playsHomeVideo(state.layoutKind) ||
        blocked || suppressed || reducedMotion || alreadyPlayed
    ) return
    val game = state.focusedGame ?: return
    val clip = clipFor(game) ?: return
    delay(state.videoWallpaperDelayMs)
    if (canStart(game.id)) start(game.id, clip)
}

internal fun playsHomeVideo(kind: HomeLayoutKind): Boolean = when (kind) {
    HomeLayoutKind.CAROUSEL, HomeLayoutKind.COLUMNS -> true
    HomeLayoutKind.AUTO_GRID, HomeLayoutKind.CUSTOM_GRID -> false
}
