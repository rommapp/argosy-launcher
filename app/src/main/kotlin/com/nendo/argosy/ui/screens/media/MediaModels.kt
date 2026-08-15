package com.nendo.argosy.ui.screens.media

import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.preferences.MediaDownloadQuality

enum class MediaDetailMode { MOVIE, SERIES }

/**
 * Where detail-screen focus currently lives.
 *
 * [MENU] is the persistent left rail; the other two are the pinned season and episode region beside
 * it. Each of those two is named by a rail row, and that row is a doorway rather than a stop: the
 * rail's own selection never rests on one, so walking down past the last action row arrives inside
 * the section and leaves the row holding the marker for where focus went. Coming back out lands on
 * that last action row, which is somewhere the user can act rather than a doorway that would open
 * again underneath them.
 */
enum class MediaDetailSection { MENU, SEASONS, EPISODES, CAST, SIMILAR }

/**
 * One row of the left rail, in render order.
 *
 * The first group acts on the title; the last two name a region of the content column beside it and
 * are the way into it. They share one list because they share one focus index - the rail is a single
 * vertical run - and the divider under [OPTIONS] is what separates acting on the title from
 * navigating its contents. It is also where the rail's own selection stops: a row with a [section]
 * is entered rather than stood on.
 */
enum class MediaDetailRow {
    PLAY, DOWNLOAD, FAVORITE, WATCHED, OPTIONS, SEASONS, EPISODES, CAST, SIMILAR;

    val section: MediaDetailSection?
        get() = when (this) {
            SEASONS -> MediaDetailSection.SEASONS
            EPISODES -> MediaDetailSection.EPISODES
            CAST -> MediaDetailSection.CAST
            SIMILAR -> MediaDetailSection.SIMILAR
            else -> null
        }
}

/**
 * The rail for what this title actually has.
 *
 * A series has no Watched row: a series is watched an episode at a time, and the rail acts on the
 * title, so the flag it would toggle is not the one the user is looking at. Section rows appear
 * only for regions that have something in them, so a movie with neither a cast nor anything like it
 * still ends its rail at Options with no divider under it.
 */
fun buildMediaRail(
    mode: MediaDetailMode,
    hasSeasons: Boolean,
    hasEpisodes: Boolean,
    hasCast: Boolean = false,
    hasSimilar: Boolean = false
): List<MediaDetailRow> = buildList {
    add(MediaDetailRow.PLAY)
    add(MediaDetailRow.DOWNLOAD)
    add(MediaDetailRow.FAVORITE)
    if (mode == MediaDetailMode.MOVIE) add(MediaDetailRow.WATCHED)
    add(MediaDetailRow.OPTIONS)
    if (hasSeasons) add(MediaDetailRow.SEASONS)
    if (hasEpisodes) add(MediaDetailRow.EPISODES)
    if (hasCast) add(MediaDetailRow.CAST)
    if (hasSimilar) add(MediaDetailRow.SIMILAR)
}

/**
 * One person credited on a title, as the cast rail draws them.
 *
 * [role] is the character an actor played and is absent for crew, so it is what the rail puts under
 * a name when there is one to put. [imageUrl] is empty when the server holds no portrait, which the
 * rail answers with initials rather than a gap.
 */
data class MediaCastUi(
    val personId: String,
    val name: String,
    val role: String? = null,
    val imageUrl: String = ""
)

/**
 * The last row that acts on the title, and so the far end of the rail's own vertical run. Always a
 * real row: Options is on every rail, and a movie's rail is nothing but this group.
 */
fun List<MediaDetailRow>.lastActionIndex(): Int =
    indexOfLast { it.section == null }.coerceAtLeast(0)

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

    /**
     * How many copies are being fetched right now. The chip beside the row reads from this rather
     * than from [pending] alone so it means the same thing for a film as for a series: work in
     * flight, not work already finished.
     */
    val activeCount: Int get() = pending

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

/**
 * What the detail menu is allowed to offer, resolved from the screen before the menu is built.
 */
data class MediaMenuContext(
    val canRefreshEpisodes: Boolean = false,
    val hasDownloads: Boolean = false,
    val hasLibrary: Boolean = false
)

/**
 * The focusable menu rows, in render order. Single source of truth: the modal renders these and the
 * view model indexes into them, so order and visibility cannot drift apart.
 *
 * Every row here is backed by something the media repository already does. Nothing is offered that
 * would need a server capability this client has not established.
 */
fun buildMediaMenu(ctx: MediaMenuContext): List<MediaMenuAction> = buildList {
    add(MediaMenuAction.ToggleWatched)
    add(MediaMenuAction.ToggleFavorite)
    add(MediaMenuAction.Download)
    if (ctx.hasDownloads) add(MediaMenuAction.RemoveDownloads)
    if (ctx.canRefreshEpisodes) add(MediaMenuAction.RefreshSeries)
    if (ctx.hasLibrary) add(MediaMenuAction.GoToLibrary)
}

sealed class MediaMenuAction {
    data object ToggleWatched : MediaMenuAction()
    data object ToggleFavorite : MediaMenuAction()
    data object Download : MediaMenuAction()
    data object RemoveDownloads : MediaMenuAction()
    data object RefreshSeries : MediaMenuAction()
    data object GoToLibrary : MediaMenuAction()
}

/**
 * The open menu, including what it acts on.
 *
 * The target is captured when the menu opens rather than read back from the screen while it is
 * open: the menu is raised over whatever was focused, and an episode list that refreshes underneath
 * it must not silently move which episode a confirm marks watched.
 */
data class MediaMenuState(
    val targetItemId: String,
    val title: String,
    val subtitle: String? = null,
    val actions: List<MediaMenuAction> = emptyList(),
    val focusedIndex: Int = 0,
    val targetPlayed: Boolean = false,
    val targetIsFavorite: Boolean = false,
    val isBusy: Boolean = false
) {
    val focusedAction: MediaMenuAction? get() = actions.getOrNull(focusedIndex)
}

/**
 * How strongly the artwork behind a media screen is drawn, as the user set it.
 *
 * These are the Home Screen background controls read at a second consumption site rather than a
 * second set of knobs: one slider moves every screen that draws artwork, which is the only way a
 * setting the user tuned on Home can be right here too. Percentages are kept as the settings screen
 * states them and converted where they are applied, so a value read back matches the slider.
 *
 * Held apart from [MediaDetailUiState] deliberately: opening a title replaces that state wholesale,
 * and preferences that arrived before the title must not be thrown away by it.
 */
data class MediaBackdropSettings(
    val blur: Int = DEFAULT_BACKDROP_BLUR,
    val saturation: Int = FULL_PERCENT,
    val opacity: Int = FULL_PERCENT
)

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
    val libraryId: String? = null,
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
    val siblingItemIds: List<String> = emptyList(),
    val currentItemIndex: Int = -1,
    val mode: MediaDetailMode = MediaDetailMode.MOVIE,
    val rows: List<MediaDetailRow> = emptyList(),
    val section: MediaDetailSection = MediaDetailSection.MENU,
    val rowIndex: Int = 0,
    val seasons: List<MediaSeasonUi> = emptyList(),
    val seasonIndex: Int = 0,
    val episodes: List<MediaItemUi> = emptyList(),
    val episodeIndex: Int = 0,
    val cast: List<MediaCastUi> = emptyList(),
    val castIndex: Int = 0,
    val similar: List<MediaItemUi> = emptyList(),
    val similarIndex: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingEpisodes: Boolean = false,
    val errorMessage: String? = null,
    val episodesErrorMessage: String? = null,
    val resumePrompt: MediaResumePrompt? = null,
    val downloadSummary: MediaDownloadSummary = MediaDownloadSummary(),
    val downloadPrompt: MediaDownloadPrompt? = null,
    val menu: MediaMenuState? = null
) {
    val focusedRow: MediaDetailRow? get() = rows.getOrNull(rowIndex)
    val selectedSeason: MediaSeasonUi? get() = seasons.getOrNull(seasonIndex)
    val focusedEpisode: MediaItemUi? get() = episodes.getOrNull(episodeIndex)
    val focusedSimilar: MediaItemUi? get() = similar.getOrNull(similarIndex)
    val hasSeasons: Boolean get() = mode == MediaDetailMode.SERIES && seasons.isNotEmpty()

    val lastActionIndex: Int get() = rows.lastActionIndex()

    /**
     * Whether there is another title beside this one in the run the shoulder buttons walk. The run
     * has a first and a last title rather than being a ring, so both ends answer false and the press
     * that reaches one is refused instead of landing back where it started.
     */
    val hasPreviousTitle: Boolean get() = currentItemIndex > 0
    val hasNextTitle: Boolean get() = currentItemIndex >= 0 && currentItemIndex < siblingItemIds.size - 1
    val hasSiblingTitles: Boolean get() = hasPreviousTitle || hasNextTitle

    /**
     * The region focus arrives in when it leaves the rail, or null when this title has none. A movie
     * answers null, which is what makes every crossing out of its rail a refusal.
     */
    val contentEntrySection: MediaDetailSection?
        get() = when {
            hasSeasons -> MediaDetailSection.SEASONS
            episodes.isNotEmpty() -> MediaDetailSection.EPISODES
            cast.isNotEmpty() -> MediaDetailSection.CAST
            similar.isNotEmpty() -> MediaDetailSection.SIMILAR
            else -> null
        }

    /**
     * The regions this title has, in the order focus walks down through them. The rail's section
     * rows read from the same list, so a region can never be reachable one way and not the other.
     */
    val contentSections: List<MediaDetailSection>
        get() = buildList {
            if (hasSeasons) add(MediaDetailSection.SEASONS)
            if (episodes.isNotEmpty()) add(MediaDetailSection.EPISODES)
            if (cast.isNotEmpty()) add(MediaDetailSection.CAST)
            if (similar.isNotEmpty()) add(MediaDetailSection.SIMILAR)
        }

    fun sectionAfter(section: MediaDetailSection): MediaDetailSection? {
        val order = contentSections
        return order.getOrNull(order.indexOf(section) + 1)
    }

    fun sectionBefore(section: MediaDetailSection): MediaDetailSection? {
        val order = contentSections
        val index = order.indexOf(section)
        return if (index > 0) order[index - 1] else null
    }

    fun rowIndexOf(row: MediaDetailRow): Int? = rows.indexOf(row).takeIf { it >= 0 }

    /**
     * Whether the expanded header has stood aside. The season and episode region is pinned, so the
     * header is what yields to it: it is full height while the rail holds focus and collapses to its
     * sticky form for as long as focus is in the region below.
     */
    val isHeaderCollapsed: Boolean get() = section != MediaDetailSection.MENU

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

/**
 * Rebuilds the rail for the content that has arrived and keeps focus on something that still exists.
 *
 * Seasons and episodes reach the screen after it has drawn, so the rail grows a row at a time; a
 * season change empties the episodes and takes one away again. Applied to every write that can move
 * either, so the selected row and the open region can never outlive what they point at.
 *
 * It is also where the rail's selection is re-tied to the open region: the row that names the region
 * while one is open, and never past the last action row once none is.
 */
fun MediaDetailUiState.withRail(): MediaDetailUiState {
    val rebuilt = buildMediaRail(
        mode = mode,
        hasSeasons = hasSeasons,
        hasEpisodes = episodes.isNotEmpty(),
        hasCast = cast.isNotEmpty(),
        hasSimilar = similar.isNotEmpty()
    )
    val openSection = when (section) {
        MediaDetailSection.EPISODES ->
            if (episodes.isNotEmpty()) section
            else if (hasSeasons) MediaDetailSection.SEASONS
            else MediaDetailSection.MENU
        MediaDetailSection.SEASONS -> if (hasSeasons) section else MediaDetailSection.MENU
        MediaDetailSection.CAST -> if (cast.isNotEmpty()) section else MediaDetailSection.MENU
        MediaDetailSection.SIMILAR -> if (similar.isNotEmpty()) section else MediaDetailSection.MENU
        MediaDetailSection.MENU -> section
    }
    val anchoredIndex = when (openSection) {
        MediaDetailSection.SEASONS -> rebuilt.indexOf(MediaDetailRow.SEASONS)
        MediaDetailSection.EPISODES -> rebuilt.indexOf(MediaDetailRow.EPISODES)
        MediaDetailSection.CAST -> rebuilt.indexOf(MediaDetailRow.CAST)
        MediaDetailSection.SIMILAR -> rebuilt.indexOf(MediaDetailRow.SIMILAR)
        MediaDetailSection.MENU -> rowIndex.coerceAtMost(rebuilt.lastActionIndex())
    }.coerceIn(0, rebuilt.lastIndex.coerceAtLeast(0))
    if (rebuilt == rows && openSection == section && anchoredIndex == rowIndex) return this
    return copy(rows = rebuilt, rowIndex = anchoredIndex, section = openSection)
}

const val DEFAULT_GRID_COLUMNS = 5

const val FULL_PERCENT = 100

/**
 * Matches the stored default for the background blur, so the first frame drawn before preferences
 * arrive is the one they are about to confirm rather than a sharp image that then softens.
 */
const val DEFAULT_BACKDROP_BLUR = 40
