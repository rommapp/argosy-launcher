package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeLayoutKind
import kotlinx.coroutines.delay

internal suspend fun scheduleHomeVideoPreview(
    state: HomeUiState,
    blocked: Boolean,
    suppressed: Boolean,
    alreadyPlayed: Boolean,
    canStart: (Long) -> Boolean,
    deactivate: () -> Unit,
    start: (Long, String) -> Unit
) {
    deactivate()
    if (!state.videoWallpaperEnabled || state.layoutKind != HomeLayoutKind.CAROUSEL ||
        blocked || suppressed || alreadyPlayed
    ) return
    val game = state.focusedGame ?: return
    val videoId = game.youtubeVideoId ?: return
    delay(state.videoWallpaperDelayMs)
    if (canStart(game.id)) start(game.id, videoId)
}
