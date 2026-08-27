package com.nendo.argosy.ui.components

import androidx.annotation.StringRes
import com.nendo.argosy.R
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
    @StringRes val labelRes: Int,
    @StringRes val supportingRes: Int
) {
    SEQUENTIAL(
        MediaTilePlayMode.SEQUENTIAL,
        R.string.ui_media_tile_mode_sequential,
        R.string.ui_media_tile_mode_sequential_supporting
    ),
    RANDOM(
        MediaTilePlayMode.RANDOM,
        R.string.ui_media_tile_mode_random,
        R.string.ui_media_tile_mode_random_supporting
    ),
    SEASON(
        MediaTilePlayMode.SEASON,
        R.string.ui_media_tile_mode_season,
        R.string.ui_media_tile_mode_season_supporting
    ),
    PLAYLIST(
        MediaTilePlayMode.PLAYLIST,
        R.string.ui_media_tile_mode_playlist,
        R.string.ui_media_tile_mode_playlist_supporting
    )
}

/**
 * One season as the setup lists it. [isLocal] is carried so the list can say which rows are already
 * on the device, which is what makes the download notice predictable rather than a surprise at the
 * end.
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
    @StringRes val confirmLabelRes: Int = R.string.ui_media_tile_notice_confirm_add_download,
    @StringRes val declineLabelRes: Int = R.string.ui_media_tile_notice_decline_cancel,
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
    val picker: EpisodePickerState = EpisodePickerState(),
    val selected: List<String> = emptyList(),
    val scopeId: String? = null,
    val notice: MediaTileNotice? = null,
    val isLoading: Boolean = false,
    @StringRes val errorRes: Int? = null
) {

    /**
     * How many positions the current step can focus. The episode step counts its own, because its
     * rows and its two actions are one run.
     */
    val rowCount: Int
        get() = when (step) {
            MediaTileStep.MODE -> MediaTileModeOption.entries.size
            MediaTileStep.SEASON -> seasons.size
            MediaTileStep.EPISODES -> picker.focusCount
        }

    val title: String get() = entry.title

    @get:StringRes
    val subtitleRes: Int
        get() = when (step) {
            MediaTileStep.MODE -> R.string.ui_media_tile_step_mode
            MediaTileStep.SEASON -> R.string.ui_media_tile_step_season
            MediaTileStep.EPISODES -> R.string.ui_media_tile_step_episodes
        }

    /**
     * The empty state for whichever list the current step shows. A season list that came back empty
     * is a series nothing has been synced for yet, which is a different answer from a series with no
     * seasons and worth saying differently.
     */
    @get:StringRes
    val emptyMessageRes: Int?
        get() = when {
            isLoading -> null
            step == MediaTileStep.SEASON && seasons.isEmpty() ->
                R.string.ui_media_tile_empty_seasons
            step == MediaTileStep.EPISODES && picker.isEmpty ->
                R.string.ui_media_tile_empty_episodes
            else -> null
        }
}
