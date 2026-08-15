package com.nendo.argosy.ui.components

import com.nendo.argosy.data.local.entity.MediaTilePlayMode

/**
 * Which question the media tile setup is currently asking.
 *
 * A movie never reaches any of these: it stands for itself, so there is nothing to configure and the
 * flow goes straight to the download notice or straight onto the page.
 */
enum class MediaTileStep { MODE, SEASON, EPISODES }

/**
 * What a series tile can be told to play. The wording says what the tile will do rather than naming
 * the mode, because the mode is an implementation word and the behaviour is the thing being chosen.
 */
enum class MediaTileModeOption(
    val mode: MediaTilePlayMode,
    val label: String,
    val supporting: String
) {
    SEQUENTIAL(
        MediaTilePlayMode.SEQUENTIAL,
        "Next episode",
        "Follows where you are up to, wherever you watch it"
    ),
    RANDOM(
        MediaTilePlayMode.RANDOM,
        "Random episode",
        "Picks one and keeps it until you have watched it"
    ),
    SEASON(
        MediaTilePlayMode.SEASON,
        "One season",
        "Works through a single season"
    ),
    PLAYLIST(
        MediaTilePlayMode.PLAYLIST,
        "Chosen episodes",
        "Plays the episodes you pick, in the order you pick them"
    )
}

/**
 * One season or one episode as the setup lists it. [isLocal] is carried so the list can say which
 * rows are already on the device, which is what makes the download notice predictable rather than a
 * surprise at the end.
 */
data class MediaTileOption(
    val itemId: String,
    val label: String,
    val supporting: String? = null,
    val isLocal: Boolean = false
)

/**
 * What the reader is told before anything is fetched.
 *
 * [warning] carries the size only for a batch large enough to be worth warning about, and says so in
 * approximate terms on purpose: a queued row starts with no size at all and the figure is a bitrate
 * estimate until the server has been asked, so stating it exactly would be stating something untrue.
 */
/**
 * The question asked between choosing what a tile plays and placing it.
 *
 * [placesOnDecline] marks the download as an offer rather than a requirement: declining still
 * places the tile.
 */
data class MediaTileNotice(
    val message: String,
    val warning: String? = null,
    val downloadIds: List<String> = emptyList(),
    val buttonIndex: Int = 0,
    val confirmLabel: String = "Add and download",
    val declineLabel: String = "Cancel",
    val placesOnDecline: Boolean = false
)

/**
 * The media half of the tile picker, from the moment a title is chosen to the moment a tile is
 * placed.
 *
 * Held as one value with a step rather than as four separate flags: the questions are asked in
 * sequence, only one is ever on screen, and back means "the previous question" rather than "close",
 * which is only expressible if the sequence is a single piece of state.
 */
data class MediaTileSetup(
    val entry: TilePickerEntry,
    val step: MediaTileStep = MediaTileStep.MODE,
    val focusIndex: Int = 0,
    val mode: MediaTilePlayMode? = null,
    val seasons: List<MediaTileOption> = emptyList(),
    val episodes: List<MediaTileOption> = emptyList(),
    val selected: List<String> = emptyList(),
    val scopeId: String? = null,
    val notice: MediaTileNotice? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {

    /**
     * How many rows the current step can focus. The episode step carries one row past its list, which
     * is the row that commits the choice - a chooser whose only way out is a button the d-pad cannot
     * reach is a chooser a controller cannot finish.
     */
    val rowCount: Int
        get() = when (step) {
            MediaTileStep.MODE -> MediaTileModeOption.entries.size
            MediaTileStep.SEASON -> seasons.size
            MediaTileStep.EPISODES -> episodes.size + 1
        }

    val isCommitRowFocused: Boolean
        get() = step == MediaTileStep.EPISODES && focusIndex >= episodes.size

    val title: String get() = entry.title

    val subtitle: String?
        get() = when (step) {
            MediaTileStep.MODE -> "What should this tile play?"
            MediaTileStep.SEASON -> "Choose a season"
            MediaTileStep.EPISODES -> selectionLabel
        }

    private val selectionLabel: String
        get() = when (selected.size) {
            0 -> "Choose episodes"
            1 -> "1 episode chosen"
            else -> "${selected.size} episodes chosen"
        }

    val commitLabel: String
        get() = if (selected.isEmpty()) "Choose at least one episode" else "Use these episodes"

    val isCommitEnabled: Boolean get() = selected.isNotEmpty()

    /**
     * The empty state for whichever list the current step shows. A season list that came back empty
     * is a series nothing has been synced for yet, which is a different answer from a series with no
     * seasons and worth saying differently.
     */
    val emptyMessage: String?
        get() = when {
            isLoading -> null
            step == MediaTileStep.SEASON && seasons.isEmpty() ->
                "No seasons have been synced for this series yet"
            step == MediaTileStep.EPISODES && episodes.isEmpty() ->
                "No episodes have been synced for this series yet"
            else -> null
        }
}
