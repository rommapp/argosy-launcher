package com.nendo.argosy.ui.screens.player

import java.util.Locale

/**
 * The two navigable bands of player chrome. The scrubber is its own band rather than another
 * control because left and right mean "seek" there and "move between buttons" below it, and one
 * index cannot carry both meanings.
 */
enum class PlayerRow { SCRUBBER, CONTROLS }

/**
 * One button in the transport row. The list is built per frame from what the item actually offers,
 * so a movie without chapters never grows a chapter button the user can land on.
 */
enum class PlayerControl { SKIP, PLAY_PAUSE, AUDIO, SUBTITLES, CHAPTERS }

enum class PlayerOverlay { NONE, AUDIO_TRACKS, SUBTITLE_TRACKS, CHAPTERS }

/**
 * One selectable audio or subtitle stream.
 *
 * [streamIndex] is the server's own absolute index across every stream in the container and is what
 * a renegotiation is keyed on. [ordinal] is the position within streams of this kind only, which is
 * what maps onto a track group when the player selects locally instead of renegotiating; the two
 * agree only while the streams of a kind are held in the container's own order, which is why they
 * are sorted by index before an ordinal is handed out.
 */
data class PlayerTrack(
    val streamIndex: Int,
    val ordinal: Int,
    val label: String,
    val language: String? = null,
    val isTextSubtitle: Boolean = false,
    val isDefault: Boolean = false
)

data class PlayerChapter(
    val startMs: Long,
    val name: String
)

enum class PlayerSkipKind(val label: String) {
    INTRO("Skip Intro"),
    CREDITS("Skip Credits")
}

data class PlayerSkipSegment(
    val kind: PlayerSkipKind,
    val startMs: Long,
    val endMs: Long
)

/**
 * A subtitle track handed to the player as a separate file alongside the video.
 *
 * Every text subtitle the item carries is attached up front, which is what makes switching between
 * them a selection rather than a reload: the sidecar for a track is only fetched once that track is
 * selected, so attaching all of them costs nothing until one is used.
 */
data class SideloadedSubtitle(
    val streamIndex: Int,
    val url: String,
    val mimeType: String,
    val language: String?
)

/**
 * The answer to one PlaybackInfo negotiation. It expires with the transcode session behind it and is
 * never reused for a second playback.
 *
 * [isLocalFile] marks the one answer that was never negotiated: a downloaded copy plays from disk,
 * so there is no play session, no encoder to free and no server to tell. Everything that reports to
 * the server is gated on it.
 */
data class NegotiatedPlayback(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String?,
    val streamUrl: String,
    val playMethod: String,
    val isTranscode: Boolean,
    val isHls: Boolean,
    val runtimeMs: Long,
    val audioTracks: List<PlayerTrack>,
    val subtitleTracks: List<PlayerTrack>,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val sideloadedSubtitles: List<SideloadedSubtitle>,
    val isLocalFile: Boolean = false
)

/**
 * The tracks a container turned out to hold, read from the player rather than from the server.
 *
 * This is the only track list a local file can have: nothing negotiated it, so what the extractor
 * found is the whole truth. The selected ordinals are the player's own opening choice, kept so the
 * track lists open on what is actually playing rather than on nothing.
 */
data class ContainerTracks(
    val audio: List<PlayerTrack> = emptyList(),
    val subtitles: List<PlayerTrack> = emptyList(),
    val selectedAudioOrdinal: Int? = null,
    val selectedSubtitleOrdinal: Int? = null
)

sealed class PlaybackNegotiation {
    data class Ready(val playback: NegotiatedPlayback) : PlaybackNegotiation()
    data class Failed(val message: String) : PlaybackNegotiation()
}

/**
 * Everything the player draws, in one val-only object.
 *
 * [positionMs] is the committed player position; [scrubTargetMs] is where the user has walked the
 * thumb to but not yet landed. They are separate because a seek is deliberately deferred - a
 * transcode restarts its encoder at the new offset, so committing every d-pad press would stall the
 * picture once per press.
 */
data class PlayerUiState(
    val itemId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val scrubTargetMs: Long? = null,
    val isChromeVisible: Boolean = true,
    val focusRow: PlayerRow = PlayerRow.SCRUBBER,
    val controlIndex: Int = 0,
    val overlay: PlayerOverlay = PlayerOverlay.NONE,
    val overlayIndex: Int = 0,
    val audioTracks: List<PlayerTrack> = emptyList(),
    val subtitleTracks: List<PlayerTrack> = emptyList(),
    val selectedAudioStreamIndex: Int? = null,
    val selectedSubtitleStreamIndex: Int? = null,
    val burnInImageSubtitles: Boolean = false,
    val isLocalPlayback: Boolean = false,
    val subtitleNotice: String? = null,
    val chapters: List<PlayerChapter> = emptyList(),
    val activeSkip: PlayerSkipSegment? = null,
    val trickplayEnabled: Boolean = false,
    val trickplayAuthHeader: String? = null
) {
    /**
     * The transport row is derived from what this item actually offers rather than drawn with dead
     * buttons, so a focus index can never land on a control that does nothing. Skip is appended
     * rather than prepended: it comes and goes with the position, and prepending it would shift
     * every other button out from under the user's thumb each time an intro started.
     */
    val controls: List<PlayerControl>
        get() = buildList {
            add(PlayerControl.PLAY_PAUSE)
            if (audioTracks.size > 1) add(PlayerControl.AUDIO)
            if (subtitleTracks.isNotEmpty()) add(PlayerControl.SUBTITLES)
            if (chapters.isNotEmpty()) add(PlayerControl.CHAPTERS)
            if (activeSkip != null) add(PlayerControl.SKIP)
        }

    val focusedControl: PlayerControl?
        get() = controls.getOrNull(controlIndex)

    val previewPositionMs: Long
        get() = scrubTargetMs ?: positionMs

    val isScrubbing: Boolean
        get() = scrubTargetMs != null

    val progressFraction: Float
        get() = if (durationMs <= 0) 0f else (previewPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /**
     * How many rows the open overlay has, which is what wraps its selection. The subtitle list
     * carries an extra leading row for "Off", because turning subtitles off has to be reachable
     * from the same list that turns them on, and a trailing row for burn-in, which is a decision
     * about this playback and therefore belongs beside the tracks it applies to. Burn-in is drawn by
     * the server, so a file playing off the disk has no such row and no index for one.
     */
    val overlayItemCount: Int
        get() = when (overlay) {
            PlayerOverlay.AUDIO_TRACKS -> audioTracks.size
            PlayerOverlay.SUBTITLE_TRACKS -> subtitleTracks.size + subtitleExtraRows
            PlayerOverlay.CHAPTERS -> chapters.size
            PlayerOverlay.NONE -> 0
        }

    val burnInRowIndex: Int
        get() = if (isLocalPlayback) NO_ROW else subtitleTracks.size + 1

    private val subtitleExtraRows: Int
        get() = if (isLocalPlayback) SUBTITLE_OFF_ROW_ONLY else SUBTITLE_EXTRA_ROWS
}

/**
 * The "Off" row above the tracks and the burn-in row below them.
 */
private const val SUBTITLE_EXTRA_ROWS = 2

private const val SUBTITLE_OFF_ROW_ONLY = 1
private const val NO_ROW = -1

/**
 * Renders a position the way a transport bar reads it: hours only once there are hours, so a
 * 22-minute episode is not padded out to look like a feature.
 */
fun formatPlaybackTime(millis: Long): String {
    val safe = millis.coerceAtLeast(0)
    val totalSeconds = safe / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
