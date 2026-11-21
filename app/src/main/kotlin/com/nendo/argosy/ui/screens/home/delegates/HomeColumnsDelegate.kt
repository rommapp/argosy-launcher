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
import com.nendo.argosy.domain.usecase.home.homeColumnGameLimit
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
import kotlinx.coroutines.delay
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

private data class ColumnSourceQuery(val showAllGames: Boolean, val loadsPlatforms: Boolean) {
    val limit: Int get() = homeColumnGameLimit(showAllGames)
    val platformLimit: Int get() = if (showAllGames) limit else PLATFORM_CANDIDATE_LIMIT
    val genreLimit: Int get() = if (showAllGames) limit else GENRE_CANDIDATE_LIMIT
}

private fun ColumnsConfig.sourceQuery(): ColumnSourceQuery =
    ColumnSourceQuery(showAllGames = showAllGames, loadsPlatforms = showPlatformColumns)

private data class ColumnGameSources(
    val query: ColumnSourceQuery,
    val sources: HomeColumnSources,
    val favoriteCount: Int,
    val platformCounts: Map<Long, Int>,
    val collectionCounts: Map<String, Int>
)

private data class ResolvedGameSources(
    val games: ColumnGameSources,
    val friends: List<HomeFriendsPlayingGame>,
    val resolved: Map<Long, HomeGameUi>
)

private const val TAG = "HomeColumns"

private data class ProgressKey(val gameId: Long, val lastPlayedAt: Long?)

private data class ColumnMediaRows(
    val rows: List<HomeMediaRow>,
    val items: Map<HomeMediaColumnKind, List<HomeMediaUi>>
)

/**
 * Owns the Columns loop: builds it from the repositories and the media state already on screen,
 * and moves its cursor. Inactive while another layout is selected.
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
    private var scope: CoroutineScope? = null
    private var read: () -> ColumnsState = { ColumnsState() }
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
        write = { transform -> update { it.copy(columns = transform(it.columns)) } }

        val active = state.map { it.layoutKind == HomeLayoutKind.COLUMNS }.distinctUntilChanged()
        val config = state.map { it.columnsConfig }.distinctUntilChanged()
        val sourceQuery = combine(active, config) { on, columns -> columns.sourceQuery().takeIf { on } }
            .distinctUntilChanged()
        val gameSources = sourceQuery.flatMapLatest { query ->
            if (query == null) {
                flowOf<ColumnGameSources?>(null)
            } else {
                combine(
                    gameRepository.observeRecentlyPlayed(query.limit),
                    platformRepository.observePlatformsWithGames(),
                    getPinnedCollectionsUseCase(),
                    refreshes
                ) { recent, platforms, pinned, _ -> Triple(recent, platforms, pinned) }
                    .mapLatest { (recent, platforms, pinned) -> loadGameSources(query, recent, platforms, pinned) }
            }
        }
        val friendActivity = combine(active, state.map { it.friendsActivity }.distinctUntilChanged()) { on, activity ->
            if (on) activity else emptyMap()
        }.distinctUntilChanged()
        val media = state.map { mediaRows(it) }.distinctUntilChanged()

        combine(gameSources, friendActivity) { games, activity -> games?.let { it to activity } }
            .mapLatest { pair ->
                pair?.let { (games, activity) -> resolve(games, activity) }
            }
            .filterNotNull()
            .let { resolved ->
                combine(resolved, media, config) { games, rows, columns -> columnsFor(games, rows, columns) }
            }
            .flowOn(Dispatchers.Default)
            .onEach { columns -> write { it.rebuiltWith(columns) } }
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

    fun jumpRun(direction: Int): Boolean = moveCursor { it.jumpedRun(direction) }

    fun reset(): Boolean = moveCursor { it.reset() }

    fun setSlotCount(slots: Int) = write { it.withSlotCount(slots) }

    private fun moveCursor(transform: (ColumnsState) -> ColumnsState?): Boolean {
        val next = transform(read()) ?: return false
        write { current -> transform(current) ?: current }
        extractFocusedGradients(next)
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
        val installedOnly = preferencesRepository.userPreferences.first().installedOnlyHome
        suspend fun List<GameEntity>.shown(): List<GameEntity> =
            if (installedOnly) filter { downloadFileStatusRepository.isContentAvailable(it) } else this

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val againWindow = BuildHomeColumnsUseCase.playItAgainWindow(LocalDate.now(zone), zone)
        val playItAgain = playedGames(
            playStatsRepository.activeMsPerGameInRange(againWindow.start, againWindow.end, query.limit)
        )
        val genreWindow = BuildHomeColumnsUseCase.mostPlayedGenreWindow(now)
        val genrePlay = playedGames(
            playStatsRepository.activeMsPerGameInRange(genreWindow.start, genreWindow.end, query.genreLimit)
        )
        val favorites = gameRepository.getFavorites().shown()
        val orderedPins = pinned.sortedByDescending { it.displayOrder }
        val pinnedGames = orderedPins.map { pin ->
            HomePinnedCollection(
                collectionId = pin.id,
                name = pin.displayName,
                games = getGamesForPinnedCollectionUseCase(pin).first().shown()
            )
        }
        val platformGames = if (!query.loadsPlatforms) {
            emptyList()
        } else {
            platforms.map { platform ->
                HomePlatformGames(
                    platformId = platform.id,
                    name = platform.getDisplayName(),
                    games = gameRepository.getPlatformLeadOrdered(platform.id, query.platformLimit).shown()
                )
            }
        }
        val shownPlayItAgain = playItAgain.map { it.game }.shown().map { it.id }.toSet()
        val shownGenre = genrePlay.map { it.game }.shown().map { it.id }.toSet()
        ColumnGameSources(
            query = query,
            sources = HomeColumnSources(
                recent = recent.shown(),
                playItAgain = playItAgain.filter { it.game.id in shownPlayItAgain }.map { it.game },
                almostThere = gameRepository.getAlmostThere(ALMOST_THERE_MAX_REMAINING_SEC, query.limit)
                    .shown(),
                newlyAdded = gameRepository.getNewlyAdded(
                    threshold = now.minus(JUST_ADDED_DAYS, ChronoUnit.DAYS),
                    installedOnly = installedOnly,
                    limit = query.limit
                ),
                gatheringDust = gameRepository.getGatheringDust(
                    BuildHomeColumnsUseCase.gatheringDustCutoff(now),
                    query.limit
                ).shown(),
                favorites = favorites,
                pinnedCollections = pinnedGames,
                genrePlay = genrePlay.filter { it.game.id in shownGenre },
                platforms = platformGames
            ),
            favoriteCount = favorites.size,
            platformCounts = platforms.associate { it.id to it.gameCount },
            collectionCounts = orderedPins.associate { it.displayName to it.gameCount }
        )
    }

    private suspend fun playedGames(activeMs: Map<Long, Long>): List<HomePlayedGame> {
        if (activeMs.isEmpty()) return emptyList()
        val games = gameRepository.getByIds(activeMs.keys.toList()).associateBy { it.id }
        return activeMs.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, ms) -> games[id]?.let { HomePlayedGame(it, ms) } }
    }

    private suspend fun resolve(
        games: ColumnGameSources,
        activity: Map<Int, List<FriendActivity>>
    ): ResolvedGameSources {
        val friends = friendsPlaying(activity)
        val sources = games.sources
        val ids = (
            sources.recent + sources.playItAgain + sources.almostThere + sources.newlyAdded +
                sources.gatheringDust + sources.favorites + friends.map { it.game } +
                sources.pinnedCollections.flatMap { it.games.take(games.query.limit) } +
                sources.genrePlay.map { it.game } +
                sources.platforms.flatMap { it.games }
            ).map { it.id }.distinct()
        return ResolvedGameSources(games, friends, libraryDelegate.resolveTileGames(ids))
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
        return buildHomeColumns(sources, config).mapNotNull { column ->
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
