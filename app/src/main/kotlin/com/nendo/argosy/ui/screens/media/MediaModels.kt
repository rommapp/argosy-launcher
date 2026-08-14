package com.nendo.argosy.ui.screens.media

import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.preferences.MediaDownloadQuality

enum class MediaDetailMode { MOVIE, SERIES }

/**
 * Where detail-screen focus currently lives. Up and Down move between sections; Left and Right move
 * within one, so the action row reads horizontally while the episode list reads vertically.
 */
enum class MediaDetailSection { ACTIONS, SEASONS, EPISODES }

enum class MediaDetailAction { PLAY, DOWNLOAD, FAVORITE, WATCHED }

/**
 * How much of a series is on this device.
 *
 * A series is downloaded in part far more often than in whole, so there is no downloaded flag to
 * read: [downloaded] counts the episodes that have a file, [known] counts the episodes this device
 * has heard of at all - episodes arrive a season at a time - and [pending] counts the ones queued or
 * in flight. A caller that wants a yes-or-no answer has to say which of those it means.
 *
 * [unavailable] is how many of the [downloaded] sit on storage that is not connected. It is reported
 * beside the count rather than taken out of it: unplugging a card does not un-download anything, and
 * a series that read "8 of 24" yesterday still has eight copies today.
 */
data class MediaDownloadSummary(
    val downloaded: Int = 0,
    val known: Int = 0,
    val pending: Int = 0,
    val unavailable: Int = 0
) {
    val isComplete: Boolean get() = known > 0 && downloaded >= known
    val isPartial: Boolean get() = downloaded > 0 && !isComplete
    val hasAny: Boolean get() = downloaded > 0 || pending > 0

    val label: String
        get() = when {
            known == 0 -> "Download"
            isComplete && pending == 0 -> "Downloaded$availabilitySuffix"
            pending > 0 -> "$downloaded of $known, $pending queued$availabilitySuffix"
            downloaded > 0 -> "$downloaded of $known$availabilitySuffix"
            else -> "Download"
        }

    private val availabilitySuffix: String
        get() = when {
            unavailable == 0 -> ""
            known == 1 -> " - not connected"
            else -> " - $unavailable not connected"
        }
}

/**
 * Which titles a download choice applies to.
 */
enum class MediaDownloadScope { THIS_ITEM, SEASON, NEXT_FIVE, NEXT_TEN, REMOVE }

enum class MediaDownloadStep { SCOPE, QUALITY, CONFIRM }

data class MediaDownloadOption(
    val scope: MediaDownloadScope? = null,
    val quality: MediaDownloadQuality? = null,
    val label: String,
    val supporting: String? = null,
    val enabled: Boolean = true
)

/**
 * The download choice, in the order it is asked: what to fetch, then at which quality.
 *
 * [targets] is resolved before the quality is asked so the size estimate on each quality is the size
 * of the whole batch rather than of one episode, and [totalRuntimeTicks] is what that estimate is
 * computed from.
 */
data class MediaDownloadPrompt(
    val step: MediaDownloadStep,
    val title: String,
    val subtitle: String? = null,
    val options: List<MediaDownloadOption> = emptyList(),
    val focusedIndex: Int = 0,
    val targets: List<String> = emptyList(),
    val totalRuntimeTicks: Long = 0,
    val note: String? = null,
    val warning: String? = null
) {
    val focusedOption: MediaDownloadOption? get() = options.getOrNull(focusedIndex)
}

data class MediaLibraryUi(
    val libraryId: String,
    val name: String,
    val isSeriesLibrary: Boolean,
    val itemCount: Int
)

data class MediaSeasonUi(
    val itemId: String,
    val name: String,
    val seasonNumber: Int?,
    val episodeCount: Int?
)

/**
 * One movie, series, or episode as the media screens draw it. [progressFraction] is zero for
 * anything unwatched and one for anything finished, so a tile can render a bar without knowing
 * whether the item carries a runtime.
 */
data class MediaItemUi(
    val itemId: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val thumbUrl: String,
    val overview: String? = null,
    val year: Int? = null,
    val runtimeLabel: String? = null,
    val communityRating: Float? = null,
    val officialRating: String? = null,
    val genres: String? = null,
    val studios: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val childCount: Int? = null,
    val isSeries: Boolean = false,
    val availability: MediaAvailability = MediaAvailability.NOT_DOWNLOADED,
    val resumeTicks: Long = 0,
    val runTimeTicks: Long? = null,
    val played: Boolean = false,
    val isFavorite: Boolean = false,
    val progressFraction: Float = 0f
) {
    val hasResumePosition: Boolean get() = resumeTicks > 0 && !played

    val isPlayable: Boolean get() = !isSeries

    val isDownloaded: Boolean get() = availability.hasLocalCopy

    val episodeLabel: String?
        get() {
            val season = seasonNumber ?: return null
            val episode = episodeNumber ?: return null
            return "S$season E$episode"
        }
}

/**
 * What the resume prompt needs to draw itself. Deliberately free of any screen's state so a Home
 * tile can raise the same prompt the detail screen raises.
 */
data class MediaResumePrompt(
    val itemId: String,
    val title: String,
    val subtitle: String? = null,
    val resumeTicks: Long = 0
)

data class MediaLibraryUiState(
    val libraries: List<MediaLibraryUi> = emptyList(),
    val selectedLibraryIndex: Int = 0,
    val items: List<MediaItemUi> = emptyList(),
    val focusedIndex: Int = 0,
    val columnsCount: Int = DEFAULT_GRID_COLUMNS,
    val isSignedIn: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val refreshLabel: String? = null,
    val errorMessage: String? = null,
    val resumePrompt: MediaResumePrompt? = null
) {
    val selectedLibrary: MediaLibraryUi? get() = libraries.getOrNull(selectedLibraryIndex)
    val focusedItem: MediaItemUi? get() = items.getOrNull(focusedIndex)
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && items.isEmpty()
}

data class MediaDetailUiState(
    val item: MediaItemUi? = null,
    val mode: MediaDetailMode = MediaDetailMode.MOVIE,
    val actions: List<MediaDetailAction> = emptyList(),
    val section: MediaDetailSection = MediaDetailSection.ACTIONS,
    val actionIndex: Int = 0,
    val seasons: List<MediaSeasonUi> = emptyList(),
    val seasonIndex: Int = 0,
    val episodes: List<MediaItemUi> = emptyList(),
    val episodeIndex: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingEpisodes: Boolean = false,
    val errorMessage: String? = null,
    val episodesErrorMessage: String? = null,
    val resumePrompt: MediaResumePrompt? = null,
    val downloadSummary: MediaDownloadSummary = MediaDownloadSummary(),
    val downloadPrompt: MediaDownloadPrompt? = null
) {
    val focusedAction: MediaDetailAction? get() = actions.getOrNull(actionIndex)
    val selectedSeason: MediaSeasonUi? get() = seasons.getOrNull(seasonIndex)
    val focusedEpisode: MediaItemUi? get() = episodes.getOrNull(episodeIndex)
    val hasSeasons: Boolean get() = mode == MediaDetailMode.SERIES && seasons.isNotEmpty()

    /**
     * What a plain confirm on the play action starts. For a series that is the episode the user is
     * up to, which is why the play action can be resumable even though the series itself is not.
     */
    val playTarget: MediaItemUi?
        get() = when (mode) {
            MediaDetailMode.MOVIE -> item
            MediaDetailMode.SERIES -> episodes.firstOrNull { it.hasResumePosition }
                ?: episodes.firstOrNull { !it.played }
                ?: episodes.firstOrNull()
        }
}

const val DEFAULT_GRID_COLUMNS = 5
