package com.nendo.argosy.ui.screens.player

import com.nendo.argosy.data.media.MediaAvailability
import java.util.Locale

/**
 * The two navigable bands of player chrome. The scrubber is its own band rather than another
 * control because left and right mean "seek" there and "move between buttons" below it, and one
 * index cannot carry both meanings.
 */
enum class PlayerRow { SCRUBBER, CONTROLS }

/**
 * The bands the transport row is read in: moving through the film, choosing what is heard and seen,
 * acting on the item itself, and the one button that only exists while an intro or the credits are
 * on screen. A change of band draws a divider, so a row walked with one thumb still has structure.
 */
enum class PlayerControlGroup { TRANSPORT, CONTENT, ITEM, PROMPT }

/**
 * One button in the transport row. The list is built per frame from what the item actually offers,
 * so a movie without chapters never grows a chapter button the user can land on.
 */
enum class PlayerControl(val group: PlayerControlGroup) {
    SKIP_BACK(PlayerControlGroup.TRANSPORT),
    PLAY_PAUSE(PlayerControlGroup.TRANSPORT),
    SKIP_FORWARD(PlayerControlGroup.TRANSPORT),
    AUDIO(PlayerControlGroup.CONTENT),
    SUBTITLES(PlayerControlGroup.CONTENT),
    CHAPTERS(PlayerControlGroup.CONTENT),
    QUALITY(PlayerControlGroup.CONTENT),
    NEXT_EPISODE(PlayerControlGroup.ITEM),
    MARK_WATCHED(PlayerControlGroup.ITEM),
    CLOSE(PlayerControlGroup.ITEM),
    SKIP(PlayerControlGroup.PROMPT)
}

enum class PlayerOverlay { NONE, AUDIO_TRACKS, SUBTITLE_TRACKS, CHAPTERS, QUALITY }

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

/**
 * The episode that follows the one playing, when the library already holds it.
 *
 * Resolved from what has been stored rather than asked of the server, so an episode whose season was
 * never synced is simply not offered - which is the honest answer, since it could not be played
 * either.
 */
data class PlayerNextEpisode(
    val itemId: String,
    val label: String
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
 *
 * [localCopy] is what became of the downloaded copy this playback would have preferred. On a stream
 * it is how the fallback is explained to the viewer: bandwidth is being spent on a title they had
 * already stored, and which of the two reasons applies changes what they can do about it.
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
    val isLocalFile: Boolean = false,
    val localCopy: MediaAvailability = MediaAvailability.NOT_DOWNLOADED
) {
    /**
     * Whether zero on this stream's own clock is the position it was negotiated for.
     *
     * A progressive transcode is handed over already cut, so it is. An HLS transcode is not: the
     * playlist spans the whole item however far in the encoder was started, so it is addressed in
     * item time exactly like a direct play, and treating it as cut puts the picture back at the
     * beginning while the position it is credited with counts up from the resume point.
     */
    val startsAtNegotiatedOffset: Boolean
        get() = isTranscode && !isHls
}

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
    val playbackNotice: String? = null,
    val subtitleNotice: String? = null,
    val chapters: List<PlayerChapter> = emptyList(),
    val activeSkip: PlayerSkipSegment? = null,
    val trickplay: PlayerTrickplay? = null,
    val trickplayAuthHeader: String? = null,
    val isWatched: Boolean = false,
    val nextEpisode: PlayerNextEpisode? = null,
    val autoplayCountdownSeconds: Int? = null,
    val streamingQuality: com.nendo.argosy.data.preferences.MediaStreamingQuality? = null
) {
    /**
     * The transport row is derived from what this item actually offers rather than drawn with dead
     * buttons, so a focus index can never land on a control that does nothing. Skip is appended
     * rather than prepended: it comes and goes with the position, and prepending it would shift
     * every other button out from under the user's thumb each time an intro started.
     */
    val controls: List<PlayerControl>
        get() = buildList {
            add(PlayerControl.SKIP_BACK)
            add(PlayerControl.PLAY_PAUSE)
            add(PlayerControl.SKIP_FORWARD)
            if (audioTracks.size > 1) add(PlayerControl.AUDIO)
            if (subtitleTracks.isNotEmpty()) add(PlayerControl.SUBTITLES)
            if (chapters.isNotEmpty()) add(PlayerControl.CHAPTERS)
            if (!isLocalPlayback) add(PlayerControl.QUALITY)
            if (nextEpisode != null) add(PlayerControl.NEXT_EPISODE)
            add(PlayerControl.MARK_WATCHED)
            add(PlayerControl.CLOSE)
            if (activeSkip != null) add(PlayerControl.SKIP)
        }

    /**
     * Where the highlight actually sits, which is not always where it was put. The row loses a button
     * whenever an intro ends or a track list turns out to be empty, and a stored index left pointing
     * past the end would draw no highlight anywhere and answer a press with nothing.
     */
    val focusedControlIndex: Int
        get() = if (controls.isEmpty()) 0 else controlIndex.coerceIn(0, controls.lastIndex)

    val focusedControl: PlayerControl?
        get() = controls.getOrNull(focusedControlIndex)

    /**
     * The one button the row always has, and therefore the only fixed point a viewer can navigate
     * from.
     */
    val playPauseIndex: Int
        get() = controls.indexOf(PlayerControl.PLAY_PAUSE).coerceAtLeast(0)

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
            PlayerOverlay.QUALITY ->
                com.nendo.argosy.data.preferences.MediaStreamingQuality.entries.size
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

/**
 * How far one press moves the position, on the scrubber and on the two skip buttons alike. The
 * seconds are the stated figure and the milliseconds are derived from them, so the number a button
 * announces and the number it actually moves by cannot come apart.
 */
const val PLAYER_SEEK_STEP_SECONDS = 10L
const val PLAYER_SEEK_STEP_MS = PLAYER_SEEK_STEP_SECONDS * MILLIS_PER_SECOND
