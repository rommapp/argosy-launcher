package com.nendo.argosy.ui.screens.home.delegates

import android.util.Log
import kotlinx.coroutines.CancellationException
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.data.repository.GamePlayActivityCalculator
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.PlayStatsRepository
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.domain.model.ColumnsConfig
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.domain.model.HomeColumn
import com.nendo.argosy.domain.model.HomeColumnLabel
import com.nendo.argosy.domain.model.HomeColumnSources
import com.nendo.argosy.domain.model.HomeColumnTheme
import com.nendo.argosy.domain.model.HomeFriendsPlayingGame
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeMediaColumnKind
import com.nendo.argosy.domain.model.HomeMediaRow
import com.nendo.argosy.domain.model.HomePinnedCollection
import com.nendo.argosy.domain.model.HomePlatformGames
import com.nendo.argosy.domain.model.HomePlayedGame
import com.nendo.argosy.domain.model.PinnedCollection
import com.nendo.argosy.domain.usecase.collection.GetGamesForPinnedCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.GetPinnedCollectionsUseCase
import com.nendo.argosy.domain.usecase.home.ALMOST_THERE_MAX_REMAINING_SEC
import com.nendo.argosy.domain.usecase.home.BuildHomeColumnsUseCase
import com.nendo.argosy.domain.usecase.home.HOME_COLUMN_MAX_GAMES
import com.nendo.argosy.ui.screens.home.ColumnsGameProgress
import com.nendo.argosy.ui.screens.home.ColumnsState
import com.nendo.argosy.ui.screens.home.HomeColumnUi
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.home.HomeMediaUi
import com.nendo.argosy.ui.screens.home.HomeRowItem
import com.nendo.argosy.ui.screens.home.HomeUiState
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val JUST_ADDED_DAYS = 7L
private const val GENRE_CANDIDATE_LIMIT = 200
private const val PLATFORM_CANDIDATE_LIMIT = HOME_COLUMN_MAX_GAMES * 2

private val SETTLED_STATUSES = CompletionStatus.entries
    .filter { it != CompletionStatus.INCOMPLETE }
    .map { it.apiValue }
    .toSet()

private const val ALL_GAMES = Int.MAX_VALUE

private data class ColumnSourceQuery(val loadsPlatforms: Boolean)

private fun ColumnsConfig.sourceQuery(): ColumnSourceQuery = ColumnSourceQuery(loadsPlatforms = showPlatformColumns)

private data class ColumnGameSources(
    val sources: HomeColumnSources,
    val pins: List<PinnedCollection>,
    val favoriteCount: Int,
    val platformCounts: Map<Long, Int>,
    val collectionCounts: Map<String, Int>
)

private data class ResolvedGameSources(
    val games: ColumnGameSources,
    val friends: List<HomeFriendsPlayingGame>,
    val resolved: Map<Long, HomeGameUi>,
    val fullColumns: Set<String>
)

private typealias FullColumnSources = (HomeColumnSources) -> HomeColumnSources

private const val TAG = "HomeColumns"

private data class ProgressKey(val gameId: Long, val lastPlayedAt: Long?)

private data class ColumnMediaRows(
    val rows: List<HomeMediaRow>,
    val items: Map<HomeMediaColumnKind, List<HomeMediaUi>>
)

/**
 * Owns the Columns loop and its cursor. Columns load [HOME_COLUMN_MAX_GAMES] games; with every game
 * shown, a column fetches in full once focus settles on it. A library change forgets those fetches
 * except the focused column's, which fetches again so the cursor keeps its place.
 */
class HomeColumnsDelegate @Inject constructor(
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val playStatsRepository: PlayStatsRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val downloadFileStatusRepository: DownloadFileStatusRepository,
    private val getPinnedCollectionsUseCase: GetPinnedCollectionsUseCase,
    private val getGamesForPinnedCollectionUseCase: GetGamesForPinnedCollectionUseCase,
    private val libraryDelegate: HomeLibraryDelegate,
    private val buildHomeColumns: BuildHomeColumnsUseCase
) {
    private val refreshes = MutableStateFlow(0L)
    private val progressCache = ConcurrentHashMap<ProgressKey, ColumnsGameProgress>()
    private val fullColumns = MutableStateFlow<Map<String, FullColumnSources>>(emptyMap())
    private val fullFetches = ConcurrentHashMap<String, Job>()
    private var latestSources: ColumnGameSources? = null
    private var settling: Job? = null
    private var settlingColumnId: String? = null
    private var scope: CoroutineScope? = null
    private var read: () -> ColumnsState = { ColumnsState() }
    private var showsAllGames: () -> Boolean = { false }
    private var write: ((ColumnsState) -> ColumnsState) -> Unit = {}

    fun refresh() {
        progressCache.clear()
        refreshes.update { it + 1 }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(
        scope: CoroutineScope,
        state: StateFlow<HomeUiState>,
        update: ((HomeUiState) -> HomeUiState) -> Unit
    ) {
        this.scope = scope
        read = { state.value.columns }
        showsAllGames = { state.value.columnsConfig.showAllGames }
        write = { transform -> update { it.copy(columns = transform(it.columns)) } }
        forgetFullColumns(keep = null)

        val active = state.map { it.layoutKind == HomeLayoutKind.COLUMNS }.distinctUntilChanged()
        val config = state.map { it.columnsConfig }.distinctUntilChanged()
        val sourceQuery = combine(active, config) { on, columns -> columns.sourceQuery().takeIf { on } }
            .distinctUntilChanged()
        val gameSources = sourceQuery.flatMapLatest { query ->
            if (query == null) {
                flowOf<ColumnGameSources?>(null)
            } else {
                combine(
                    gameRepository.observeRecentlyPlayed(HOME_COLUMN_MAX_GAMES),
                    platformRepository.observePlatformsWithGames(),
                    getPinnedCollectionsUseCase(),
                    refreshes
                ) { recent, platforms, pinned, _ -> Triple(recent, platforms, pinned) }
                    .mapLatest { (recent, platforms, pinned) -> loadGameSources(query, recent, platforms, pinned) }
            }
        }.onEach { sources ->
            latestSources = sources
            forgetFullColumns(keep = read().focusedColumn)
        }
        val friendActivity = combine(active, state.map { it.friendsActivity }.distinctUntilChanged()) { on, activity ->
            if (on) activity else emptyMap()
        }.distinctUntilChanged()
        val media = state.map { mediaRows(it) }.distinctUntilChanged()

        combine(gameSources, friendActivity, fullColumns) { games, activity, full ->
            games?.let { Triple(it, activity, full) }
        }
            .mapLatest { loaded ->
                loaded?.let { (games, activity, full) -> resolve(games, activity, full) }
            }
            .filterNotNull()
            .let { resolved ->
                combine(resolved, media, config) { games, rows, columns -> columnsFor(games, rows, columns) }
            }
            .flowOn(Dispatchers.Default)
            .onEach { columns ->
                write { it.rebuiltWith(columns) }
                fetchFocusedColumnOnceSettled()
            }
            .launchIn(scope)

        state.map { focusedProgressKey(it) }
            .distinctUntilChanged()
            .mapLatest { key -> key?.let { loadProgressOrNull(it) } }
            .filterNotNull()
            .onEach { progress -> write { it.copy(progress = progress) } }
            .launchIn(scope)
    }

    private fun focusedProgressKey(state: HomeUiState): ProgressKey? {
        if (state.layoutKind != HomeLayoutKind.COLUMNS) return null
        val game = (state.columns.focusedItem as? HomeRowItem.Game)?.game ?: return null
        return ProgressKey(game.id, game.lastPlayedAt)
    }

    private suspend fun loadProgressOrNull(key: ProgressKey): ColumnsGameProgress? =
        try {
            progressFor(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Play activity for game ${key.gameId} failed to load", e)
            null
        }

    private suspend fun progressFor(key: ProgressKey): ColumnsGameProgress {
        progressCache[key]?.let { return it }
        delay(ComponentDefaults.HomeColumns.progressLoadDebounceMs.toLong())
        val activity = playStatsRepository.gamePlayActivity(key.gameId)
        return ColumnsGameProgress(
            gameId = key.gameId,
            weeks = GamePlayActivityCalculator.normalized(activity.weekActiveMs),
            lastPlayedAt = activity.lastPlayedAt?.toEpochMilli(),
            lastPlayedDevice = activity.lastPlayedDevice
        ).also { progressCache[key] = it }
    }

    fun move(delta: Int): Boolean = moveCursor { it.movedBy(delta) }

    fun step(delta: Int): Boolean = moveCursor { it.steppedBy(delta) }

    private fun fetchFocusedColumnOnceSettled() {
        val owner = scope ?: return
        val column = read().focusedColumn ?: return
        if (column.id == settlingColumnId && settling?.isActive == true) return
        settling?.cancel()
        settlingColumnId = column.id
        settling = owner.launch {
            delay(ComponentDefaults.HomeColumns.progressLoadDebounceMs.toLong())
            requestFullColumn(column)
        }
    }

    private fun requestFullColumn(column: HomeColumnUi) {
        if (!showsAllGames()) return
        if (column.id in fullColumns.value || fullFetches[column.id]?.isActive == true) return
        fetchFullColumn(column)
    }

    private fun fetchFullColumn(column: HomeColumnUi) {
        val owner = scope ?: return
        val pins = latestSources?.pins ?: return
        fullFetches.remove(column.id)?.cancel()
        fullFetches[column.id] = owner.launch {
            val full = try {
                fullColumnSources(column, pins)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Column ${column.id} failed to load in full", e)
                return@launch
            }
            fullColumns.update { it + (column.id to full) }
        }
    }

    private fun forgetFullColumns(keep: HomeColumnUi?) {
        val kept = keep?.takeIf { it.id in fullColumns.value || fullFetches[it.id]?.isActive == true }
        fullFetches.keys.filter { it != kept?.id }.forEach { id -> fullFetches.remove(id)?.cancel() }
        fullColumns.update { full -> full.filterKeys { it == kept?.id } }
        kept?.let { fetchFullColumn(it) }
    }

    fun jumpRun(direction: Int): Boolean = moveCursor { it.jumpedRun(direction) }

    fun reset(): Boolean = moveCursor { it.reset() }

    fun setSlotCount(slots: Int) = write { it.withSlotCount(slots) }

    private fun moveCursor(transform: (ColumnsState) -> ColumnsState?): Boolean {
        val next = transform(read()) ?: return false
        write { current -> transform(current) ?: current }
        extractFocusedGradients(next)
        fetchFocusedColumnOnceSettled()
        return true
    }

    private fun extractFocusedGradients(state: ColumnsState) {
        val owner = scope ?: return
        val column = state.focusedColumn ?: return
        libraryDelegate.extractGradientsForVisibleGames(owner, column.items, state.itemIndexOf(column))
    }

    private suspend fun loadGameSources(
        query: ColumnSourceQuery,
        recent: List<GameEntity>,
        platforms: List<PlatformEntity>,
        pinned: List<PinnedCollection>
    ): ColumnGameSources = withContext(Dispatchers.IO) {
        val reader = sourceReader()
        val limit = HOME_COLUMN_MAX_GAMES
        val (favorites, favoriteCount) = reader.favorites(limit)
        val orderedPins = pinned.sortedByDescending { it.displayOrder }
        val platformGames = if (!query.loadsPlatforms) {
            emptyList()
        } else {
            platforms.map { platform ->
                HomePlatformGames(
                    platformId = platform.id,
                    name = platform.getDisplayName(),
                    games = reader.platform(platform.id, PLATFORM_CANDIDATE_LIMIT)
                )
            }
        }
        ColumnGameSources(
            sources = HomeColumnSources(
                recent = reader.shown(recent),
                playItAgain = reader.playItAgain(limit),
                almostThere = reader.almostThere(limit),
                newlyAdded = reader.newlyAdded(limit),
                gatheringDust = reader.gatheringDust(limit),
                favorites = favorites,
                pinnedCollections = orderedPins.map { pin ->
                    HomePinnedCollection(collectionId = pin.id, name = pin.displayName, games = reader.pinned(pin, limit))
                },
                genrePlay = reader.genrePlay(GENRE_CANDIDATE_LIMIT),
                platforms = platformGames
            ),
            pins = orderedPins,
            favoriteCount = favoriteCount,
            platformCounts = platforms.associate { it.id to it.gameCount },
            collectionCounts = orderedPins.associate { it.displayName to it.gameCount }
        )
    }

    private suspend fun fullColumnSources(
        column: HomeColumnUi,
        pins: List<PinnedCollection>
    ): FullColumnSources = withContext(Dispatchers.IO) {
        val reader = sourceReader()
        val unchanged: FullColumnSources = { it }
        when (val label = column.label) {
            is HomeColumnLabel.Media -> unchanged
            is HomeColumnLabel.Platform -> {
                val platformId = BuildHomeColumnsUseCase.platformIdOf(column.id) ?: return@withContext unchanged
                val games = reader.platform(platformId, ALL_GAMES)
                val full: FullColumnSources = { sources ->
                    sources.copy(platforms = sources.platforms.map { if (it.platformId == platformId) it.copy(games = games) else it })
                }
                full
            }
            is HomeColumnLabel.Theme -> fullThemeSources(label.theme, column.id, pins, reader) ?: unchanged
        }
    }

    private suspend fun fullThemeSources(
        theme: HomeColumnTheme,
        columnId: String,
        pins: List<PinnedCollection>,
        reader: ColumnSourceReader
    ): FullColumnSources? = when (theme) {
        HomeColumnTheme.CONTINUE -> reader.shown(gameRepository.getRecentlyPlayed(ALL_GAMES)).let { games ->
            { sources -> sources.copy(recent = games) }
        }
        HomeColumnTheme.PLAY_IT_AGAIN -> reader.playItAgain(ALL_GAMES).let { games ->
            { sources -> sources.copy(playItAgain = games) }
        }
        HomeColumnTheme.ALMOST_THERE -> reader.almostThere(ALL_GAMES).let { games ->
            { sources -> sources.copy(almostThere = games) }
        }
        HomeColumnTheme.JUST_ADDED -> reader.newlyAdded(ALL_GAMES).let { games ->
            { sources -> sources.copy(newlyAdded = games) }
        }
        HomeColumnTheme.GATHERING_DUST -> reader.gatheringDust(ALL_GAMES).let { games ->
            { sources -> sources.copy(gatheringDust = games) }
        }
        HomeColumnTheme.FAVORITES -> reader.favorites(ALL_GAMES).first.let { games ->
            { sources -> sources.copy(favorites = games) }
        }
        HomeColumnTheme.MOST_PLAYED_GENRE -> reader.genrePlay(ALL_GAMES).let { played ->
            { sources -> sources.copy(genrePlay = played) }
        }
        HomeColumnTheme.PINNED_COLLECTION -> {
            val pinId = BuildHomeColumnsUseCase.collectionIdOf(columnId)
            pins.firstOrNull { it.id == pinId }?.let { pin ->
                val games = reader.pinned(pin, ALL_GAMES)
                val full: FullColumnSources = { sources ->
                    sources.copy(
                        pinnedCollections = sources.pinnedCollections.map {
                            if (it.collectionId == pin.id) it.copy(games = games) else it
                        }
                    )
                }
                full
            }
        }
        HomeColumnTheme.FRIENDS_PLAYING -> null
    }

    private suspend fun sourceReader(): ColumnSourceReader =
        ColumnSourceReader(preferencesRepository.userPreferences.first().installedOnlyHome, Instant.now())

    private inner class ColumnSourceReader(private val installedOnly: Boolean, private val now: Instant) {
        private val zone = ZoneId.systemDefault()

        suspend fun shown(games: List<GameEntity>): List<GameEntity> =
            if (installedOnly) games.filter { downloadFileStatusRepository.isContentAvailable(it) } else games

        suspend fun playItAgain(limit: Int): List<GameEntity> {
            val window = BuildHomeColumnsUseCase.playItAgainWindow(LocalDate.now(zone), zone)
            return shown(playedGames(playStatsRepository.activeMsPerGameInRange(window.start, window.end, limit)).map { it.game })
        }

        suspend fun genrePlay(limit: Int): List<HomePlayedGame> {
            val window = BuildHomeColumnsUseCase.mostPlayedGenreWindow(now)
            val played = playedGames(playStatsRepository.activeMsPerGameInRange(window.start, window.end, limit))
            val shownIds = shown(played.map { it.game }).map { it.id }.toSet()
            return played.filter { it.game.id in shownIds }
        }

        suspend fun almostThere(limit: Int): List<GameEntity> =
            shown(gameRepository.getAlmostThere(ALMOST_THERE_MAX_REMAINING_SEC, limit))

        suspend fun newlyAdded(limit: Int): List<GameEntity> = gameRepository.getNewlyAdded(
            threshold = now.minus(JUST_ADDED_DAYS, ChronoUnit.DAYS),
            installedOnly = installedOnly,
            limit = limit
        )

        suspend fun gatheringDust(limit: Int): List<GameEntity> =
            shown(gameRepository.getGatheringDust(BuildHomeColumnsUseCase.gatheringDustCutoff(now), limit))

        suspend fun favorites(limit: Int): Pair<List<GameEntity>, Int> {
            if (installedOnly) return shown(gameRepository.getFavorites()).let { it.take(limit) to it.size }
            return gameRepository.getFavoritesPage(limit)
        }

        suspend fun pinned(pin: PinnedCollection, limit: Int): List<GameEntity> =
            if (installedOnly) {
                shown(getGamesForPinnedCollectionUseCase(pin).first()).take(limit)
            } else {
                getGamesForPinnedCollectionUseCase(pin, limit).first()
            }

        suspend fun platform(platformId: Long, limit: Int): List<GameEntity> =
            shown(gameRepository.getPlatformLeadOrdered(platformId, limit))
    }

    private suspend fun playedGames(activeMs: Map<Long, Long>): List<HomePlayedGame> {
        if (activeMs.isEmpty()) return emptyList()
        val games = gameRepository.getByIds(activeMs.keys.toList()).associateBy { it.id }
        return activeMs.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, ms) -> games[id]?.let { HomePlayedGame(it, ms) } }
    }

    private suspend fun resolve(
        loaded: ColumnGameSources,
        activity: Map<Int, List<FriendActivity>>,
        full: Map<String, FullColumnSources>
    ): ResolvedGameSources {
        val friends = friendsPlaying(activity)
        val games = loaded.copy(sources = full.values.fold(loaded.sources) { sources, apply -> apply(sources) })
        val sources = games.sources
        val ids = (
            sources.recent + sources.playItAgain + sources.almostThere + sources.newlyAdded +
                sources.gatheringDust + sources.favorites + friends.map { it.game } +
                sources.pinnedCollections.flatMap { it.games } +
                sources.genrePlay.map { it.game } +
                sources.platforms.flatMap { it.games }
            ).map { it.id }.distinct()
        return ResolvedGameSources(games, friends, libraryDelegate.resolveTileGames(ids), full.keys)
    }

    private suspend fun friendsPlaying(activity: Map<Int, List<FriendActivity>>): List<HomeFriendsPlayingGame> {
        if (activity.isEmpty()) return emptyList()
        val games = gameRepository.getByIgdbIds(activity.keys.map { it.toLong() })
        return games.mapNotNull { game ->
            val friends = game.igdbId?.let { activity[it.toInt()] }.orEmpty()
            if (friends.isEmpty()) return@mapNotNull null
            HomeFriendsPlayingGame(
                game = game,
                playingNow = friends.any { it.playingNow },
                lastPlayedAt = friends.mapNotNull { it.lastPlayedAtMillis }.maxOrNull()?.let(Instant::ofEpochMilli)
            )
        }
    }

    private fun mediaRows(state: HomeUiState): ColumnMediaRows {
        if (state.layoutKind != HomeLayoutKind.COLUMNS || !state.isMediaSignedIn) {
            return ColumnMediaRows(emptyList(), emptyMap())
        }
        val items = buildMap {
            if (state.showContinueWatchingRow) put(HomeMediaColumnKind.CONTINUE_WATCHING, state.continueWatchingMedia)
            if (state.showNextUpRow) put(HomeMediaColumnKind.NEXT_UP, state.nextUpMedia)
        }
        val rows = items.map { (kind, media) -> HomeMediaRow(kind.name, kind, media.map { it.itemId }) }
        return ColumnMediaRows(rows, items)
    }

    private fun columnsFor(
        games: ResolvedGameSources,
        media: ColumnMediaRows,
        config: ColumnsConfig
    ): List<HomeColumnUi> {
        val sources = games.games.sources.copy(friendsPlaying = games.friends, media = media.rows)
        val liveFriends = games.friends.filter { it.playingNow }.map { it.game.id }.toSet()
        return buildHomeColumns(sources, config, games.fullColumns).mapNotNull { column ->
            val items = itemsFor(column, games.resolved, media)
            if (items.isEmpty()) return@mapNotNull null
            HomeColumnUi(
                id = column.id,
                label = column.label,
                items = items,
                count = countFor(column, items, games.games, liveFriends)
            )
        }
    }

    private fun itemsFor(
        column: HomeColumn,
        resolved: Map<Long, HomeGameUi>,
        media: ColumnMediaRows
    ): List<HomeRowItem> = when (val label = column.label) {
        is HomeColumnLabel.Media -> {
            val byId = media.items[label.mediaKind].orEmpty().associateBy { it.itemId }
            column.mediaItemIds.mapNotNull { byId[it] }.map { HomeRowItem.Media(it) }
        }
        is HomeColumnLabel.Theme, is HomeColumnLabel.Platform ->
            column.games.mapNotNull { resolved[it.id] }.map { HomeRowItem.Game(it) }
    }

    private fun countFor(
        column: HomeColumn,
        items: List<HomeRowItem>,
        games: ColumnGameSources,
        liveFriends: Set<Long>
    ): Int = when (val label = column.label) {
        is HomeColumnLabel.Theme -> when (label.theme) {
            HomeColumnTheme.CONTINUE -> items.count { item ->
                (item as? HomeRowItem.Game)?.game?.status !in SETTLED_STATUSES
            }
            HomeColumnTheme.FRIENDS_PLAYING -> column.games.count { it.id in liveFriends }
            HomeColumnTheme.FAVORITES -> games.favoriteCount
            HomeColumnTheme.PINNED_COLLECTION ->
                label.subject?.let { games.collectionCounts[it] } ?: items.size
            HomeColumnTheme.PLAY_IT_AGAIN,
            HomeColumnTheme.ALMOST_THERE,
            HomeColumnTheme.JUST_ADDED,
            HomeColumnTheme.GATHERING_DUST,
            HomeColumnTheme.MOST_PLAYED_GENRE -> items.size
        }
        is HomeColumnLabel.Platform ->
            column.games.firstOrNull()?.platformId?.let { games.platformCounts[it] } ?: items.size
        is HomeColumnLabel.Media -> items.size
    }
}
