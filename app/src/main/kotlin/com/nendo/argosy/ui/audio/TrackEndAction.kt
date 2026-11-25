package com.nendo.argosy.ui.audio

import com.nendo.argosy.data.preferences.MusicRepeatMode

enum class TrackEndAction { REPLAY, ADVANCE, STOP }

fun trackEndAction(repeat: MusicRepeatMode, isAtLast: Boolean): TrackEndAction = when (repeat) {
    MusicRepeatMode.ONE -> TrackEndAction.REPLAY
    MusicRepeatMode.ALL -> TrackEndAction.ADVANCE
    MusicRepeatMode.OFF -> if (isAtLast) TrackEndAction.STOP else TrackEndAction.ADVANCE
}
