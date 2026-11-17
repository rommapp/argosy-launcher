package com.nendo.argosy.ui.quickmenu

import com.nendo.argosy.data.model.PlayDay
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.domain.model.RandomTileFilters
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.GridUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class QuickMenuOrb {
    SEARCH, RANDOM, MOST_PLAYED, TOP_UNPLAYED, RECENT, FAVORITES
}

enum class QuickMenuLayout { SEARCH, RANDOM, LIST, GRID }

val QuickMenuOrb.layout: QuickMenuLayout
    get() = when (this) {
        QuickMenuOrb.SEARCH -> QuickMenuLayout.SEARCH
        QuickMenuOrb.RANDOM -> QuickMenuLayout.RANDOM
        QuickMenuOrb.MOST_PLAYED, QuickMenuOrb.RECENT -> QuickMenuLayout.LIST
        QuickMenuOrb.TOP_UNPLAYED, QuickMenuOrb.FAVORITES -> QuickMenuLayout.GRID
    }

val QuickMenuOrb.confirmPlays: Boolean
    get() = this == QuickMenuOrb.RANDOM || this == QuickMenuOrb.RECENT

val QuickMenuOrb.hasHeaderControl: Boolean
    get() = this == QuickMenuOrb.MOST_PLAYED || this == QuickMenuOrb.FAVORITES

/**
 * Where the menu is drawn. [DUAL_SCREEN] is the bottom panel beside a presentation screen,
 * [LANDSCAPE] the single-screen sheet and [PORTRAIT] the full-height phone sheet.
 */
enum class QuickMenuFormFactor { DUAL_SCREEN, LANDSCAPE, PORTRAIT }

val QuickMenuFormFactor.gridColumns: Int
    get() = when (this) {
        QuickMenuFormFactor.DUAL_SCREEN -> ComponentDefaults.QuickMenu.gridColumnsDualScreen
        QuickMenuFormFactor.LANDSCAPE -> ComponentDefaults.QuickMenu.gridColumnsLandscape
        QuickMenuFormFactor.PORTRAIT -> ComponentDefaults.QuickMenu.gridColumnsPortrait
    }

data class QuickMenuGameUi(
    val id: Long,
    val title: String,
    val platformId: Long,
    val platformSlug: String,
    val platformName: String?,
    val coverPath: String?,
    val backdropPath: String?,
    val backdropIsScreenshotOrArt: Boolean,
    val year: Int?,
    val developer: String?,
    val genre: String?,
    val rating: Float?,
    val description: String?,
    val isDownloaded: Boolean,
    val needsInstall: Boolean,
    val isFavorite: Boolean,
    val playTimeMinutes: Int,
    val lastPlayed: Instant?,
    val addedAt: Instant,
    val titleId: String? = null
) {
    val isNeverPlayed: Boolean get() = playTimeMinutes <= 0 && lastPlayed == null
}

data class RandomPlatform(val id: Long, val name: String)

enum class RandomStop { PLAY, ROLL, DETAILS, FAVORITE, HISTORY, PLATFORM, INSTALLED }

private val RANDOM_BUTTONS = listOf(RandomStop.PLAY, RandomStop.ROLL, RandomStop.DETAILS, RandomStop.FAVORITE)

private val RANDOM_SECONDARY_BUTTONS = listOf(RandomStop.ROLL, RandomStop.DETAILS, RandomStop.FAVORITE)

val RandomStop.isButton: Boolean get() = this in RANDOM_BUTTONS

const val RANDOM_HISTORY_LIMIT = 5

/**
 * The rolls of this session, oldest first, capped at [RANDOM_HISTORY_LIMIT]. [rollIndex] is the
 * current pick; [rollCount] counts every roll made, including those that fell off the history.
 * [platformId] null rolls across every platform. [poolSize] is how many games the pool holds, null
 * until counted. [historyFocus] indexes [rolls] while [focus] is [RandomStop.HISTORY], and
 * [lastButton] is the button Up from the history row returns to.
 */
data class RandomState(
    val rolls: List<QuickMenuGameUi> = emptyList(),
    val rollIndex: Int = -1,
    val rollCount: Int = 0,
    val platforms: List<RandomPlatform> = emptyList(),
    val platformId: Long? = null,
    val installedOnly: Boolean = false,
    val poolSize: Int? = null,
    val focus: RandomStop = RandomStop.PLAY,
    val lastButton: RandomStop = RandomStop.PLAY,
    val historyFocus: Int = 0,
    val isResolved: Boolean = false,
    val isPoolEmpty: Boolean = false
) {
    val current: QuickMenuGameUi? get() = rolls.getOrNull(rollIndex)

    val filters: RandomTileFilters
        get() = RandomTileFilters(downloadedOnly = installedOnly, platformIds = setOfNotNull(platformId))

    val isFiltered: Boolean get() = installedOnly || platformId != null

    val platformIndex: Int get() = platforms.indexOfFirst { it.id == platformId } + 1

    val platformName: String? get() = platforms.firstOrNull { it.id == platformId }?.name

    val rollNumber: Int get() = rollCount - (rolls.lastIndex - rollIndex)

    val focusedGame: QuickMenuGameUi?
        get() = if (focus == RandomStop.HISTORY) rolls.getOrNull(historyFocus) ?: current else current

    /**
     * Whether the current pick still belongs to the pool after a Platform or Installed only change.
     */
    val pickFitsPool: Boolean
        get() {
            val game = current ?: return false
            return (!installedOnly || game.isDownloaded) && (platformId == null || game.platformId == platformId)
        }

    fun isEnabled(stop: RandomStop): Boolean = when (stop) {
        RandomStop.PLAY -> current != null && !isPoolEmpty
        RandomStop.ROLL -> isResolved && !isPoolEmpty
        RandomStop.DETAILS, RandomStop.FAVORITE -> current != null
        RandomStop.HISTORY -> rolls.isNotEmpty()
        RandomStop.PLATFORM, RandomStop.INSTALLED -> true
    }

    fun withRoll(game: QuickMenuGameUi?): RandomState {
        if (game == null) return copy(isResolved = true, isPoolEmpty = true).withFocusClamped()
        val kept = (rolls + game).takeLast(RANDOM_HISTORY_LIMIT)
        return copy(
            rolls = kept,
            rollIndex = kept.lastIndex,
            rollCount = rollCount + 1,
            isResolved = true,
            isPoolEmpty = false
        )
    }

    fun picking(index: Int): RandomState {
        if (index !in rolls.indices) return this
        val picked = copy(rollIndex = index)
        return picked.focusing(if (picked.isEnabled(RandomStop.PLAY)) RandomStop.PLAY else picked.firstEnabledStop())
    }

    fun withPlatforms(next: List<RandomPlatform>): RandomState =
        copy(platforms = next, platformId = platformId?.takeIf { id -> next.any { it.id == id } })

    fun withPlatformIndex(index: Int): RandomState {
        val wrapped = index.mod(platforms.size + 1)
        return copy(platformId = if (wrapped == 0) null else platforms[wrapped - 1].id, poolSize = null)
    }

    fun cyclingPlatform(delta: Int): RandomState = withPlatformIndex(platformIndex + delta)

    fun withInstalledOnly(on: Boolean): RandomState =
        if (on == installedOnly) this else copy(installedOnly = on, poolSize = null)

    fun focusing(stop: RandomStop): RandomState = copy(
        focus = stop,
        lastButton = if (stop.isButton) stop else lastButton,
        historyFocus = when {
            stop != RandomStop.HISTORY -> historyFocus
            focus == RandomStop.HISTORY -> historyFocus
            else -> rollIndex.coerceIn(0, (rolls.size - 1).coerceAtLeast(0))
        }
    )

    fun focusingHistory(index: Int): RandomState =
        if (index !in rolls.indices) this else focusing(RandomStop.HISTORY).copy(historyFocus = index)

    fun firstEnabledStop(): RandomStop =
        (RANDOM_BUTTONS + RandomStop.PLATFORM).first { isEnabled(it) }

    fun entered(): RandomState = focusing(firstEnabledStop())

    /**
     * Moves the cursor one stop. Null when the press goes nowhere. [portrait] puts Play on its own
     * row above the other three buttons.
     */
    fun stepped(direction: FocusStep, portrait: Boolean): RandomState? = when (focus) {
        RandomStop.PLAY, RandomStop.ROLL, RandomStop.DETAILS, RandomStop.FAVORITE -> steppedFromButton(direction, portrait)
        RandomStop.HISTORY -> when (direction) {
            FocusStep.UP -> buttonAbove()?.let { focusing(it) }
            FocusStep.DOWN -> focusing(RandomStop.PLATFORM)
            FocusStep.LEFT -> (historyFocus - 1).takeIf { it >= 0 }?.let { copy(historyFocus = it) }
            FocusStep.RIGHT -> (historyFocus + 1).takeIf { it <= rolls.lastIndex }?.let { copy(historyFocus = it) }
        }
        RandomStop.PLATFORM -> when (direction) {
            FocusStep.UP -> if (isEnabled(RandomStop.HISTORY)) focusing(RandomStop.HISTORY) else buttonAbove()?.let { focusing(it) }
            FocusStep.DOWN -> focusing(RandomStop.INSTALLED)
            FocusStep.LEFT, FocusStep.RIGHT -> null
        }
        RandomStop.INSTALLED -> when (direction) {
            FocusStep.UP -> focusing(RandomStop.PLATFORM)
            else -> null
        }
    }

    private fun steppedFromButton(direction: FocusStep, portrait: Boolean): RandomState? {
        val onPlayRow = portrait && focus == RandomStop.PLAY
        return when (direction) {
            FocusStep.UP -> if (portrait && !onPlayRow && isEnabled(RandomStop.PLAY)) focusing(RandomStop.PLAY) else null
            FocusStep.DOWN -> {
                val secondRow = if (onPlayRow) RANDOM_SECONDARY_BUTTONS.firstOrNull { isEnabled(it) } else null
                focusing(secondRow ?: if (isEnabled(RandomStop.HISTORY)) RandomStop.HISTORY else RandomStop.PLATFORM)
            }
            FocusStep.LEFT, FocusStep.RIGHT -> {
                if (onPlayRow) return null
                val row = if (portrait) RANDOM_SECONDARY_BUTTONS else RANDOM_BUTTONS
                val step = if (direction == FocusStep.LEFT) -1 else 1
                generateSequence(row.indexOf(focus) + step) { it + step }
                    .takeWhile { it in row.indices }
                    .map { row[it] }
                    .firstOrNull { isEnabled(it) }
                    ?.let { focusing(it) }
            }
        }
    }

    private fun buttonAbove(): RandomStop? =
        lastButton.takeIf { isEnabled(it) } ?: RANDOM_BUTTONS.firstOrNull { isEnabled(it) }

    private fun withFocusClamped(): RandomState = if (isEnabled(focus)) this else focusing(firstEnabledStop())

    fun replacingGame(gameId: Long, transform: (QuickMenuGameUi) -> QuickMenuGameUi): RandomState =
        copy(rolls = rolls.map { if (it.id == gameId) transform(it) else it })
}

fun defaultInstalledOnly(isOnline: Boolean, isMetered: Boolean): Boolean = !isOnline || isMetered

enum class MostPlayedRange { ALL_TIME, LAST_30_DAYS }

data class PlayTally(
    val gameId: Long,
    val minutes: Long,
    val lastPlayed: Instant?,
    val title: String
)

data class RankedTally(val tally: PlayTally, val rank: Int, val fraction: Float)

/**
 * Most played first, ties broken by the most recent play and then by title. Games with no time
 * are left out; [fraction] is each game's time against the first.
 */
fun rankPlayTallies(tallies: List<PlayTally>, limit: Int): List<RankedTally> {
    val ordered = tallies
        .filter { it.minutes > 0 }
        .sortedWith(
            compareByDescending<PlayTally> { it.minutes }
                .thenByDescending { it.lastPlayed ?: Instant.EPOCH }
                .thenBy { it.title.lowercase() }
        )
        .take(limit)
    val top = ordered.firstOrNull()?.minutes ?: return emptyList()
    return ordered.mapIndexed { index, tally ->
        RankedTally(tally = tally, rank = index + 1, fraction = tally.minutes.toFloat() / top)
    }
}

data class RankedGameUi(
    val game: QuickMenuGameUi,
    val rank: Int,
    val minutes: Long,
    val fraction: Float
)

data class MostPlayedState(
    val range: MostPlayedRange = MostPlayedRange.ALL_TIME,
    val rows: List<RankedGameUi> = emptyList(),
    val isLoaded: Boolean = false
)

enum class RecentGroup { TODAY, YESTERDAY, THIS_WEEK, EARLIER }

/**
 * [lastSessionMinutes] is the length of the game's latest session, null when none was long enough
 * to show.
 */
data class RecentRowUi(
    val game: QuickMenuGameUi,
    val group: RecentGroup,
    val isRunning: Boolean,
    val lastSessionMinutes: Long? = null
)

data class RecentState(
    val rows: List<RecentRowUi> = emptyList(),
    val isLoaded: Boolean = false
)

private const val DAYS_IN_WEEK = 7L

fun recentGroupOf(lastPlayed: Instant?, today: LocalDate, zone: ZoneId): RecentGroup {
    val day = lastPlayed?.atZone(zone)?.toLocalDate() ?: return RecentGroup.EARLIER
    return when {
        !day.isBefore(today) -> RecentGroup.TODAY
        day == today.minusDays(1) -> RecentGroup.YESTERDAY
        day.isAfter(today.minusDays(DAYS_IN_WEEK)) -> RecentGroup.THIS_WEEK
        else -> RecentGroup.EARLIER
    }
}

/**
 * Running games first in their Resume order and grouped under today, then the rest by last play,
 * newest first. [sessionMinutes] carries each game's latest session length.
 */
fun orderRecent(
    games: List<QuickMenuGameUi>,
    runningOrder: Map<Long, Int>,
    today: LocalDate,
    zone: ZoneId,
    sessionMinutes: Map<Long, Long> = emptyMap()
): List<RecentRowUi> {
    val running = games
        .filter { it.id in runningOrder }
        .sortedBy { runningOrder.getValue(it.id) }
        .map { RecentRowUi(game = it, group = RecentGroup.TODAY, isRunning = true, lastSessionMinutes = sessionMinutes[it.id]) }
    val rest = games
        .filterNot { it.id in runningOrder }
        .sortedByDescending { it.lastPlayed ?: Instant.EPOCH }
        .map {
            RecentRowUi(
                game = it,
                group = recentGroupOf(it.lastPlayed, today, zone),
                isRunning = false,
                lastSessionMinutes = sessionMinutes[it.id]
            )
        }
    return running + rest
}

data class TopNewReason(val genre: String?, val platformName: String?)

data class TopNewTileUi(val game: QuickMenuGameUi, val reason: TopNewReason?)

/**
 * [dismissedIds] are the games marked Not interested while the menu is open. They stay in place,
 * dimmed, and are stored when the menu closes.
 */
data class TopNewState(
    val tiles: List<TopNewTileUi> = emptyList(),
    val dismissedIds: Set<Long> = emptySet(),
    val isLoaded: Boolean = false
) {
    fun togglingDismissal(gameId: Long): TopNewState =
        copy(dismissedIds = if (gameId in dismissedIds) dismissedIds - gameId else dismissedIds + gameId)
}

enum class FavoritesSort { RECENTLY_ADDED, TITLE, PLATFORM, LAST_PLAYED }

fun sortFavorites(games: List<QuickMenuGameUi>, sort: FavoritesSort): List<QuickMenuGameUi> = when (sort) {
    FavoritesSort.RECENTLY_ADDED -> games.sortedByDescending { it.addedAt }
    FavoritesSort.TITLE -> games.sortedBy { it.title.lowercase() }
    FavoritesSort.PLATFORM -> games.sortedWith(
        compareBy<QuickMenuGameUi> { it.platformName?.lowercase().orEmpty() }.thenBy { it.title.lowercase() }
    )
    FavoritesSort.LAST_PLAYED -> games.sortedByDescending { it.lastPlayed ?: Instant.EPOCH }
}

/**
 * [removedIds] are the games unfavorited while the menu is open. Their tiles stay where they are,
 * marked removed, until the menu closes; unfavoriting again restores one.
 */
data class FavoritesState(
    val games: List<QuickMenuGameUi> = emptyList(),
    val removedIds: Set<Long> = emptySet(),
    val sort: FavoritesSort = FavoritesSort.RECENTLY_ADDED,
    val isLoaded: Boolean = false
) {
    val count: Int get() = games.count { it.id !in removedIds }

    fun markingFavorite(gameId: Long, isFavorite: Boolean): FavoritesState =
        copy(removedIds = if (isFavorite) removedIds - gameId else removedIds + gameId)

    fun sortedBy(next: FavoritesSort): FavoritesState = copy(sort = next, games = sortFavorites(games, next))

    fun cyclingSort(delta: Int = 1): FavoritesState =
        sortedBy(FavoritesSort.entries[(sort.ordinal + delta).mod(FavoritesSort.entries.size)])
}

/**
 * One remembered query on the Search tab. [coverPaths] are the covers of its first results, for
 * rows that show them inline.
 */
data class RecentSearchUi(val query: String, val resultCount: Int, val coverPaths: List<String?>)

/**
 * The Search tab's landing. With no [recentSearches] it lists [recentlyAdded] instead. Focus index
 * 0 is the search field and index n is row n - 1.
 */
data class SearchTabState(
    val recentSearches: List<RecentSearchUi> = emptyList(),
    val recentlyAdded: List<QuickMenuGameUi> = emptyList(),
    val isLoaded: Boolean = false
) {
    val showsRecentlyAdded: Boolean get() = isLoaded && recentSearches.isEmpty()

    val rowCount: Int get() = if (showsRecentlyAdded) recentlyAdded.size else recentSearches.size
}

/**
 * The live results of the focused recent search, drawn on the presentation screen. [games] is
 * null while the query runs.
 */
data class SearchResultsPreview(val query: String, val games: List<QuickMenuGameUi>?)

const val SEARCH_FIELD_INDEX = 0

const val PLAY_STRIP_WEEKS = 12

/**
 * [weeklyMinutes] runs oldest first over [PLAY_STRIP_WEEKS] weeks starting [firstWeekStart];
 * [busiestWeek] indexes it and is null when nothing was played.
 */
data class QuickMenuPlayStats(
    val totalMinutes: Long,
    val sessionCount: Int,
    val lastPlayed: Instant?,
    val weeklyMinutes: List<Long>,
    val busiestWeek: Int?,
    val firstWeekStart: LocalDate
) {
    val busiestWeekStart: LocalDate?
        get() = busiestWeek?.let { firstWeekStart.plusDays(it * DAYS_IN_WEEK) }

    val playedWeeks: Int get() = weeklyMinutes.count { it > 0 }
}

private const val MS_PER_MINUTE = 60_000L

const val PLAY_STRIP_MIN_WEEKS = 2

fun weeklyMinutes(days: List<PlayDay>): List<Long> =
    days.takeLast(PLAY_STRIP_WEEKS * DAYS_IN_WEEK.toInt())
        .chunked(DAYS_IN_WEEK.toInt())
        .map { week -> week.sumOf { it.activeMs } / MS_PER_MINUTE }

fun busiestWeekOf(weeks: List<Long>): Int? {
    val peak = weeks.maxOrNull() ?: return null
    return if (peak <= 0L) null else weeks.indexOf(peak)
}

enum class SaveSyncState { SYNCED, PENDING, LOCAL_ONLY }

data class SaveStatusUi(val savedAt: Instant, val state: SaveSyncState)

data class QuickMenuPreviewExtras(
    val gameId: Long,
    val stats: QuickMenuPlayStats? = null,
    val screenshotPath: String? = null,
    val saveStatus: SaveStatusUi? = null
)

data class QuickMenuPreview(
    val section: QuickMenuOrb,
    val game: QuickMenuGameUi,
    val rank: Int? = null,
    val reason: TopNewReason? = null,
    val isRunning: Boolean = false,
    val isRemoved: Boolean = false,
    val extras: QuickMenuPreviewExtras? = null
)

enum class QuickMenuPicker { PLATFORM, RANKING, SORT }

enum class QuickMenuGameAction { PRIMARY, FAVORITE, DETAILS, NOT_INTERESTED }

/**
 * The game actions menu a long press opens on [game], with [focusIndex] indexing [actions].
 */
data class GameActionsMenuState(
    val game: QuickMenuGameUi,
    val section: QuickMenuOrb,
    val actions: List<QuickMenuGameAction>,
    val focusIndex: Int = 0
) {
    val focusedAction: QuickMenuGameAction? get() = actions.getOrNull(focusIndex)
}

fun gameActionsFor(section: QuickMenuOrb): List<QuickMenuGameAction> = buildList {
    add(QuickMenuGameAction.PRIMARY)
    add(QuickMenuGameAction.FAVORITE)
    add(QuickMenuGameAction.DETAILS)
    if (section == QuickMenuOrb.TOP_UNPLAYED) add(QuickMenuGameAction.NOT_INTERESTED)
}

/**
 * [headerFocused] holds the sections whose header control has the cursor. [isTouchMode] holds
 * while the last input was a tap; it hides the cursor, glyphs and guide bar.
 */
data class QuickMenuUiState(
    val isVisible: Boolean = false,
    val selectedOrb: QuickMenuOrb = QuickMenuOrb.RECENT,
    val focusIndices: Map<QuickMenuOrb, Int> = emptyMap(),
    val headerFocused: Set<QuickMenuOrb> = emptySet(),
    val formFactor: QuickMenuFormFactor = QuickMenuFormFactor.LANDSCAPE,
    val isTouchMode: Boolean = false,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val random: RandomState = RandomState(),
    val mostPlayed: MostPlayedState = MostPlayedState(),
    val topNew: TopNewState = TopNewState(),
    val recent: RecentState = RecentState(),
    val favorites: FavoritesState = FavoritesState(),
    val search: SearchTabState = SearchTabState(),
    val searchResults: SearchResultsPreview? = null,
    val extras: QuickMenuPreviewExtras? = null,
    val picker: QuickMenuPicker? = null,
    val actionsMenu: GameActionsMenuState? = null
) {
    fun focusIndexOf(orb: QuickMenuOrb): Int = focusIndices[orb] ?: 0

    val gridColumns: Int get() = formFactor.gridColumns

    val gridSpacingDp: Int get() = GridUtils.getGridSpacingDp(gridDensity)

    val presentationColumns: Int get() = GridUtils.getPresentationGameColumns(gridDensity)

    val focusIndex: Int get() = focusIndexOf(selectedOrb)

    val isHeaderFocused: Boolean get() = selectedOrb in headerFocused

    val showsCursor: Boolean get() = !isTouchMode

    fun gamesOf(orb: QuickMenuOrb): List<QuickMenuGameUi> = when (orb) {
        QuickMenuOrb.SEARCH -> if (search.showsRecentlyAdded) search.recentlyAdded else emptyList()
        QuickMenuOrb.RANDOM -> listOfNotNull(random.current)
        QuickMenuOrb.MOST_PLAYED -> mostPlayed.rows.map { it.game }
        QuickMenuOrb.TOP_UNPLAYED -> topNew.tiles.map { it.game }
        QuickMenuOrb.RECENT -> recent.rows.map { it.game }
        QuickMenuOrb.FAVORITES -> favorites.games
    }

    fun focusedGame(orb: QuickMenuOrb = selectedOrb): QuickMenuGameUi? = when (orb) {
        QuickMenuOrb.RANDOM -> random.focusedGame
        QuickMenuOrb.SEARCH -> gamesOf(orb).getOrNull(focusIndexOf(orb) - 1)
        else -> gamesOf(orb).getOrNull(focusIndexOf(orb))
    }

    /**
     * The game an X or Y press acts on: none while a header control holds the cursor, and the
     * current pick in Random from any stop.
     */
    fun actionGame(): QuickMenuGameUi? = when {
        selectedOrb == QuickMenuOrb.RANDOM -> random.current
        isHeaderFocused -> null
        else -> focusedGame()
    }

    val focusedSearch: RecentSearchUi?
        get() = if (search.showsRecentlyAdded) null else search.recentSearches.getOrNull(focusIndexOf(QuickMenuOrb.SEARCH) - 1)

    val hasRunningGame: Boolean get() = recent.rows.any { it.isRunning }

    val preview: QuickMenuPreview?
        get() {
            val game = focusedGame() ?: return null
            val index = focusIndex
            return QuickMenuPreview(
                section = selectedOrb,
                game = game,
                rank = mostPlayed.rows.getOrNull(index)?.rank?.takeIf { selectedOrb == QuickMenuOrb.MOST_PLAYED },
                reason = topNew.tiles.getOrNull(index)?.reason?.takeIf { selectedOrb == QuickMenuOrb.TOP_UNPLAYED },
                isRunning = selectedOrb == QuickMenuOrb.RECENT && recent.rows.getOrNull(index)?.isRunning == true,
                isRemoved = when (selectedOrb) {
                    QuickMenuOrb.FAVORITES -> game.id in favorites.removedIds
                    QuickMenuOrb.TOP_UNPLAYED -> game.id in topNew.dismissedIds
                    else -> false
                },
                extras = extras?.takeIf { it.gameId == game.id }
            )
        }

    /**
     * The state after a d-pad press inside the selected section, or null when the press goes
     * nowhere.
     */
    fun stepped(direction: FocusStep): QuickMenuUiState? {
        val orb = selectedOrb
        val horizontal = direction == FocusStep.LEFT || direction == FocusStep.RIGHT
        return when (orb.layout) {
            QuickMenuLayout.RANDOM ->
                random.stepped(direction, formFactor == QuickMenuFormFactor.PORTRAIT)?.let { copy(random = it) }
            QuickMenuLayout.SEARCH -> {
                val index = focusIndexOf(orb)
                when (direction) {
                    FocusStep.UP -> (index - 1).takeIf { it >= SEARCH_FIELD_INDEX }?.let { withFocusIndex(orb, it) }
                    FocusStep.DOWN -> (index + 1).takeIf { it <= search.rowCount }?.let { withFocusIndex(orb, it) }
                    FocusStep.LEFT, FocusStep.RIGHT -> null
                }
            }
            QuickMenuLayout.LIST, QuickMenuLayout.GRID -> {
                val columns = if (orb.layout == QuickMenuLayout.GRID) gridColumns else 1
                val size = gamesOf(orb).size
                val index = focusIndexOf(orb)
                when {
                    isHeaderFocused ->
                        if (direction == FocusStep.DOWN && size > 0) withFocusIndex(orb, (index % columns).coerceAtMost(size - 1)) else null
                    size == 0 || (horizontal && orb.layout == QuickMenuLayout.LIST) -> null
                    else -> when (val next = stepFocus(index, size, columns, direction)) {
                        null -> if (orb.hasHeaderControl) withHeaderFocus(orb) else null
                        index -> null
                        else -> withFocusIndex(orb, next)
                    }
                }
            }
        }
    }

    fun withFocusIndex(orb: QuickMenuOrb, index: Int): QuickMenuUiState =
        copy(focusIndices = focusIndices + (orb to index), headerFocused = headerFocused - orb)

    fun withHeaderFocus(orb: QuickMenuOrb): QuickMenuUiState = copy(headerFocused = headerFocused + orb)

    /**
     * Applies [transform] to one section and keeps that section's focus on the game it held, or
     * on the nearest index when the game is gone.
     */
    fun replacingSection(orb: QuickMenuOrb, transform: (QuickMenuUiState) -> QuickMenuUiState): QuickMenuUiState {
        val previousId = focusedGame(orb)?.id
        val previousIndex = focusIndexOf(orb)
        val wasHeader = orb in headerFocused
        val next = transform(this)
        val reconciled = next.copy(
            focusIndices = next.focusIndices + (orb to reconcileFocus(previousId, previousIndex, next.gamesOf(orb).map { it.id }))
        )
        return if (wasHeader || (orb.hasHeaderControl && next.gamesOf(orb).isEmpty())) reconciled.withHeaderFocus(orb) else reconciled
    }

    /**
     * Switches to [orb] the way the bumpers do: Search lands on its field and Random on its first
     * enabled button. Nothing opens.
     */
    fun entering(orb: QuickMenuOrb): QuickMenuUiState = when (orb) {
        QuickMenuOrb.SEARCH -> copy(selectedOrb = orb).withFocusIndex(orb, SEARCH_FIELD_INDEX)
        QuickMenuOrb.RANDOM -> copy(selectedOrb = orb, random = random.entered())
        else -> copy(selectedOrb = orb)
    }

    fun movingSection(delta: Int): QuickMenuUiState {
        val entries = QuickMenuOrb.entries
        return entering(entries[(entries.indexOf(selectedOrb) + delta).mod(entries.size)])
    }

    /**
     * The state a menu opens into: on the section it was left on, or on Recent for the first open
     * of a session and whenever it was left on Search.
     */
    fun opened(firstOpen: Boolean): QuickMenuUiState {
        val orb = if (firstOpen || selectedOrb == QuickMenuOrb.SEARCH) QuickMenuOrb.RECENT else selectedOrb
        return entering(orb).copy(isVisible = true, picker = null, actionsMenu = null)
    }
}

fun reconcileFocus(previousId: Long?, previousIndex: Int, ids: List<Long>): Int {
    if (ids.isEmpty()) return 0
    val byId = previousId?.let { ids.indexOf(it) } ?: -1
    return if (byId >= 0) byId else previousIndex.coerceIn(0, ids.lastIndex)
}

/**
 * The next index for a d-pad press on a list or grid of [size] items, or null when Up leaves the
 * top row. Left and Right wrap across the whole grid.
 */
fun stepFocus(index: Int, size: Int, columns: Int, direction: FocusStep): Int? {
    if (size == 0) return if (direction == FocusStep.UP) null else 0
    return when (direction) {
        FocusStep.UP -> (index - columns).takeIf { it >= 0 }
        FocusStep.DOWN -> when {
            index + columns <= size - 1 -> index + columns
            index / columns < (size - 1) / columns -> size - 1
            else -> index
        }
        FocusStep.LEFT -> (index - 1).mod(size)
        FocusStep.RIGHT -> (index + 1).mod(size)
    }
}

enum class FocusStep { UP, DOWN, LEFT, RIGHT }
