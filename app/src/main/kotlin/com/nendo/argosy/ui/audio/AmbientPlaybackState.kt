package com.nendo.argosy.ui.audio

import com.nendo.argosy.data.preferences.MusicRepeatMode
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicSelection

data class AmbientPlaybackState(
    val trackTitle: String? = null,
    val gameTitle: String? = null,
    val coverPath: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val art: MusicArt? = null,
    val durationMs: Long? = null,
    val overrideTitle: String? = null,
    val index: Int = 0,
    val count: Int = 0,
    val tracks: List<MusicQueueTrack> = emptyList(),
    val isPlaying: Boolean = false,
    val userPaused: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: MusicRepeatMode = MusicRepeatMode.ALL,
    val sourceId: String = MusicSelection.LAUNCHER_ID,
    val sourceLabel: String? = null
)

data class PlaybackPosition(
    val positionMs: Long,
    val durationMs: Long
)
