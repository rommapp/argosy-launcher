package com.nendo.argosy.ui.quickmenu

import android.content.Context
import com.nendo.argosy.data.emulator.background.RunningGames
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.QuickMenuPreferencesRepository
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.data.repository.GameActivityRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.PlayStatsRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.domain.model.RandomTileFilters
import com.nendo.argosy.domain.usecase.quickmenu.GetTopUnplayedUseCase
import com.nendo.argosy.ui.common.displayTitleId
import com.nendo.argosy.ui.common.needsAndroidInstall
import com.nendo.argosy.ui.common.resolveDownloaded
import com.nendo.argosy.ui.common.toRunningGamesUi
import com.nendo.argosy.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val LIST_LIMIT = 20
private const val RECENT_WINDOW_DAYS = 30L
private const val MINUTES_PER_MS = 60_000L
private const val DAYS_PER_WEEK = 7
private const val RECENT_SEARCH_LIMIT = 8
private const val RECENT_SEARCH_COVERS = 3
private const val RECENTLY_ADDED_LIMIT = 5
private const val SEARCH_PREVIEW_LIMIT = 24

/**
 * Reads every quick menu section from local repositories. Nothing here reaches the network.
 */
class QuickMenuLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val getTopUnplayedUseCase: GetTopUnplayedUseCase,
    private val downloadStatus: DownloadFileStatusRepository,
    private val playStatsRepository: PlayStatsRepository,
    private val gameActivityRepository: GameActivityRepository,
    private val stateCacheManager: StateCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val quickMenuPreferences: QuickMenuPreferencesRepository,
    private val displayPreferences: DisplayPreferencesRepository,
    private val appPreferences: AppPreferencesRepository,
    private val runningGames: RunningGames
) {
    private val platformNames = ConcurrentHashMap<Long, String>()

    suspend fun gridDensity(): GridDensity = withContext(Dispatchers.IO) {
        displayPreferences.preferences.first().gridDensity
    }

    fun startsInstalledOnly(): Boolean =
        defaultInstalledOnly(NetworkUtils.isOnline(context), NetworkUtils.isMetered(context))

    suspend fun randomPlatforms(): List<RandomPlatform> = withContext(Dispatchers.IO) {
        platformRepository.getPlatformsWithGames().map { RandomPlatform(id = it.id, name = it.shortName) }
    }

    suspend fun roll(filters: RandomTileFilters, excludeGameId: Long?): QuickMenuGameUi? = withContext(Dispatchers.IO) {
        gameRepository.pickRandomGame(filters, excludeGameId)?.toUi()
    }

    suspend fun poolSize(filters: RandomTileFilters): Int = withContext(Dispatchers.IO) {
        gameRepository.countRandomCandidates(filters)
    }

    suspend fun mostPlayed(range: MostPlayedRange): List<RankedGameUi> = withContext(Dispatchers.IO) {
        val entities: List<GameEntity>
        val tallies: List<PlayTally>
        when (range) {
            MostPlayedRange.ALL_TIME -> {
                entities = gameRepository.getPlayedGames()
                tallies = entities.map { PlayTally(it.id, it.playTimeMinutes.toLong(), it.lastPlayed, it.title) }
            }
            MostPlayedRange.LAST_30_DAYS -> {
                val since = Instant.now().minus(Duration.ofDays(RECENT_WINDOW_DAYS))
                val activeMs = playStatsRepository.activeMsPerGameSince(since)
                entities = gameRepository.getByIdsBatched(activeMs.keys.toList())
                tallies = entities.map {
                    PlayTally(it.id, (activeMs[it.id] ?: 0L) / MINUTES_PER_MS, it.lastPlayed, it.title)
                }
            }
        }
        val byId = entities.associateBy { it.id }
        rankPlayTallies(tallies, LIST_LIMIT).mapNotNull { ranked ->
            val entity = byId[ranked.tally.gameId] ?: return@mapNotNull null
            RankedGameUi(
                game = entity.toUi(),
                rank = ranked.rank,
                minutes = ranked.tally.minutes,
                fraction = ranked.fraction
            )
        }
    }

    suspend fun topNew(): List<TopNewTileUi> = withContext(Dispatchers.IO) {
        val excluded = quickMenuPreferences.getNotInterestedGameIds()
        getTopUnplayedUseCase(LIST_LIMIT, excluded).first().map { pick ->
            val reason = TopNewReason(
                genre = pick.matchedGenre,
                platformName = pick.matchedPlatformId?.let { platformName(it) }
            ).takeIf { it.genre != null || it.platformName != null }
            TopNewTileUi(game = pick.game.toUi(), reason = reason)
        }
    }

    suspend fun saveNotInterested(gameIds: Set<Long>) = withContext(Dispatchers.IO) {
        quickMenuPreferences.addNotInterested(gameIds)
    }

    suspend fun recent(): List<RecentRowUi> = withContext(Dispatchers.IO) {
        val recentGames = gameRepository.getRecentlyPlayed(LIST_LIMIT)
        val runningOrder = runningGames.state.value.toRunningGamesUi().games.associate { it.gameId to it.order }
        val listedIds = recentGames.map { it.id }.toSet()
        val missingRunning = runningOrder.keys.filterNot { it in listedIds }.mapNotNull { gameRepository.getById(it) }
        val games = missingRunning + recentGames
        val sessionMinutes = playStatsRepository.lastSessionActiveMs(games.map { it.id })
            .mapValues { it.value / MINUTES_PER_MS }
        val zone = ZoneId.systemDefault()
        orderRecent(
            games = games.map { it.toUi() },
            runningOrder = runningOrder,
            today = LocalDate.now(zone),
            zone = zone,
            sessionMinutes = sessionMinutes
        )
    }

    suspend fun favorites(sort: FavoritesSort): List<QuickMenuGameUi> = withContext(Dispatchers.IO) {
        sortFavorites(gameRepository.getFavorites().map { it.toUi() }, sort)
    }

    suspend fun searchTab(): SearchTabState = withContext(Dispatchers.IO) {
        val queries = appPreferences.preferences.first().libraryRecentSearches.take(RECENT_SEARCH_LIMIT)
        val searches = queries.map { query ->
            val hits = gameRepository.search(query).first()
            val shown = hits.take(RECENT_SEARCH_COVERS)
            val art = gameRepository.getArt(shown.map { it.id })
            RecentSearchUi(query = query, resultCount = hits.size, coverPaths = shown.map { art[it.id]?.coverPath })
        }
        val recentlyAdded = if (searches.isEmpty()) {
            gameRepository.getNewlyAdded(Instant.EPOCH, installedOnly = false, limit = RECENTLY_ADDED_LIMIT).map { it.toUi() }
        } else {
            emptyList()
        }
        SearchTabState(recentSearches = searches, recentlyAdded = recentlyAdded, isLoaded = true)
    }

    suspend fun searchResults(query: String): List<QuickMenuGameUi> = withContext(Dispatchers.IO) {
        gameRepository.search(query).first().take(SEARCH_PREVIEW_LIMIT).map { it.toUi() }
    }

    suspend fun extras(section: QuickMenuOrb, gameId: Long): QuickMenuPreviewExtras? = withContext(Dispatchers.IO) {
        when (section) {
            QuickMenuOrb.MOST_PLAYED -> QuickMenuPreviewExtras(gameId = gameId, stats = playStats(gameId))
            QuickMenuOrb.RECENT -> QuickMenuPreviewExtras(
                gameId = gameId,
                screenshotPath = lastFrame(gameId),
                saveStatus = saveStatus(gameId)
            )
            else -> null
        }
    }

    private suspend fun playStats(gameId: Long): QuickMenuPlayStats? {
        val snapshot = gameActivityRepository.load(gameId, PLAY_STRIP_WEEKS * DAYS_PER_WEEK) ?: return null
        val weeks = weeklyMinutes(snapshot.days)
        return QuickMenuPlayStats(
            totalMinutes = snapshot.totalActiveMs / MINUTES_PER_MS,
            sessionCount = snapshot.sessionCount,
            lastPlayed = snapshot.lastPlayed,
            weeklyMinutes = weeks,
            busiestWeek = busiestWeekOf(weeks),
            firstWeekStart = snapshot.days.firstOrNull()?.date ?: LocalDate.now()
        )
    }

    private suspend fun lastFrame(gameId: Long): String? {
        val running = runningGames.state.value.toRunningGamesUi().markFor(gameId)?.snapshotPath
        if (running != null) return running
        val newest = stateCacheManager.getStatesForGameOnce(gameId)
            .filter { it.screenshotPath != null }
            .maxByOrNull { it.cachedAt }
            ?: return null
        return stateCacheManager.getScreenshotPath(newest)
    }

    private suspend fun saveStatus(gameId: Long): SaveStatusUi? {
        val newest = saveSyncRepository.getSaveRowsForGame(gameId)
            .mapNotNull { row -> (row.localUpdatedAt ?: row.lastSyncedAt)?.let { row to it } }
            .maxByOrNull { it.second }
            ?: return null
        val state = when (newest.first.syncStatus) {
            SaveSyncEntity.STATUS_SYNCED -> SaveSyncState.SYNCED
            SaveSyncEntity.STATUS_LOCAL_NEWER, SaveSyncEntity.STATUS_PENDING_UPLOAD -> SaveSyncState.PENDING
            else -> SaveSyncState.LOCAL_ONLY
        }
        return SaveStatusUi(savedAt = newest.second, state = state)
    }

    private suspend fun platformName(platformId: Long): String? {
        platformNames[platformId]?.let { return it }
        val name = platformRepository.getById(platformId)?.shortName ?: return null
        platformNames[platformId] = name
        return name
    }

    private suspend fun GameEntity.toUi(): QuickMenuGameUi {
        val art = gameRepository.getArt(id)
        val firstScreenshot = gameRepository.getScreenshots(id).firstOrNull()?.sourceUrl
        return QuickMenuGameUi(
            id = id,
            title = title,
            platformId = platformId,
            platformSlug = platformSlug,
            platformName = platformName(platformId),
            coverPath = art.coverPath,
            backdropPath = art.backgroundPath ?: firstScreenshot ?: art.coverPath,
            backdropIsScreenshotOrArt = art.backgroundPath != null || firstScreenshot != null,
            year = releaseYear,
            developer = developer,
            genre = genre,
            rating = rating,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            isDownloaded = resolveDownloaded(downloadStatus),
            needsInstall = needsAndroidInstall,
            isFavorite = isFavorite,
            playTimeMinutes = playTimeMinutes,
            lastPlayed = lastPlayed,
            addedAt = addedAt,
            titleId = displayTitleId
        )
    }
}
