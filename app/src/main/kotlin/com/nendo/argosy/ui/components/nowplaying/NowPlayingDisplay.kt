package com.nendo.argosy.ui.components.nowplaying

import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.audio.PlaybackPosition
import java.util.Locale

val AmbientPlaybackState.isAudible: Boolean get() = isPlaying && !userPaused

val AmbientPlaybackState.hasQueue: Boolean get() = count > 0 || overrideTitle != null

val AmbientPlaybackState.displayArt: MusicArt?
    get() = if (overrideTitle != null) null else art ?: coverPath?.let { MusicArt.Local(it) }

val AmbientPlaybackState.displayTitle: String? get() = overrideTitle ?: trackTitle

val AmbientPlaybackState.displayArtist: String?
    get() = if (overrideTitle != null) null else artist ?: gameTitle

fun AmbientPlaybackState.resolvedDurationMs(position: PlaybackPosition?): Long =
    position?.durationMs?.takeIf { it > 0L } ?: durationMs ?: 0L

fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
