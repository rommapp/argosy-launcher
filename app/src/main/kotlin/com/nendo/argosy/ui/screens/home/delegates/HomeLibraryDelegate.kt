package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.domain.model.PinnedCollection
import com.nendo.argosy.domain.usecase.collection.GetGamesForPinnedCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.GetPinnedCollectionsUseCase
import com.nendo.argosy.domain.usecase.recommendation.GenerateRecommendationsUseCase
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.common.GameGradientRequest
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.home.HomePlatformUi
import com.nendo.argosy.ui.screens.home.HomeRow
import com.nendo.argosy.ui.screens.home.HomeRowItem
import com.nendo.argosy.ui.screens.home.toHomePlatformUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

private const val PLATFORM_GAMES_LIMIT = 20
private const val MAX_DISPLAYED_RECOMMENDATIONS = 8
private const val RECOMMENDATION_PENALTY = 0.9f
private val EXCLUDED_RECOMMENDATION_STATUSES = setOf(
    CompletionStatus.FINISHED.apiValue,
    CompletionStatus.COMPLETED_100.apiValue,
    CompletionStatus.NEVER_PLAYING.apiValue
)
private const val RECENT_GAMES_LIMIT = 10
private const val RECENT_GAMES_CANDIDATE_POOL = 40
private const val NEW_GAME_THRESHOLD_HOURS = 24L
private const val RECENT_PLAYED_THRESHOLD_HOURS = 4L

data class LibraryState(
    val platforms: List<HomePlatformUi> = emptyList(),
    val platformItems: List<HomeRowItem> = emptyList(),
    val recentGames: List<HomeGameUi> = emptyList(),
    val favoriteGames: List<HomeGameUi> = emptyList(),
    val recommendedGames: List<HomeGameUi> = emptyList(),
    val androidGames: List<HomeGameUi> = emptyList(),
    val steamGames: List<HomeGameUi> = emptyList(),
    val pinnedCollections: List<PinnedCollection> = emptyList(),
    val pinnedGames: Map<Long, List<HomeGameUi>> = emptyMap(),
    val pinnedGamesLoading: Set<Long> = emptySet(),
    val repairedCoverPaths: Map<Long, String> = emptyMap()
)

private data class RecentGamesCache(
    val games: List<HomeGameUi>?,
    val version: Long
)

class HomeLibraryDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val generateRecommendationsUseCase: GenerateRecommendationsUseCase,
    private val getPinnedCollectionsUseCase: GetPinnedCollectionsUseCase,
    private val getGamesForPinnedCollectionUseCase: GetGamesForPinnedCollectionUseCase,
    private val gradientExtractionDelegate: GradientExtractionDelegate,
    private val emulatorDetector: EmulatorDetector,
    private val notificationManager: NotificationManager,
    private val repairImageCacheUseCase: com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
) {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val recentGamesCache = AtomicReference(RecentGamesCache(null, 0L))
    private var cachedPlatformDisplayNames: Map<Long, String> = emptyMap()
    private val pendingCoverRepairs = mutableSetOf<Long>()

    fun loadInitialData(scope: CoroutineScope, onStartRowResolved: (HomeRow) -> Unit) {
        scope.launch {
            gameRepository.awaitStorageReady()
            val allPlatforms = platformRepository.getPlatformsWithGames()
            val platforms = allPlatforms.filter { it.id != LocalPlatformIds.STEAM && it.id != LocalPlatformIds.ANDROID }
            cachedPlatformDisplayNames = allPlatforms.associate { it.id to it.getDisplayName() }
            var favorites = gameRepository.getFavorites()
            val androidGames = gameRepository.getByPlatformSorted(LocalPlatformIds.ANDROID, limit = PLATFORM_GAMES_LIMIT)
            val steamGames = gameRepository.getByPlatformSorted(LocalPlatformIds.STEAM, limit = PLATFORM_GAMES_LIMIT)

            val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
            var recentlyPlayed = gameRepository.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
            var newlyAdded = gameRepository.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
            var allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }

            val allDisplayed = (favorites + allCandidates).distinctBy { it.id }
            if (discoverGamesIfNeeded(allDisplayed)) {
                favorites = gameRepository.getFavorites()
                recentlyPlayed = gameRepository.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
                newlyAdded = gameRepository.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
                allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }
            }

            val playableGames = filterPlayable(allCandidates)
            val sortedRecent = sortRecentGamesWithNewPriority(playableGames)
            val validatedRecent = sortedRecent.take(RECENT_GAMES_LIMIT).map { it.toUi() }
            recentGamesCache.set(RecentGamesCache(validatedRecent, recentGamesCache.get().version))

            val platformUis = platforms.map { it.toHomePlatformUi(emulatorDetector) }
            val favoriteUis = favorites.map { it.toUi() }
            val androidGameUis = androidGames.map { it.toUi() }
            val steamGameUis = steamGames.map { it.toUi() }

            val startRow = when {
                validatedRecent.isNotEmpty() -> HomeRow.Continue
                favoriteUis.isNotEmpty() -> HomeRow.Favorites
                androidGameUis.isNotEmpty() -> HomeRow.Android
                steamGameUis.isNotEmpty() -> HomeRow.Steam
                platformUis.isNotEmpty() -> HomeRow.Platform(0)
                else -> HomeRow.Continue
            }

            _state.update {
                it.copy(
                    platforms = platformUis,
                    recentGames = validatedRecent,
                    favoriteGames = favoriteUis,
                    androidGames = androidGameUis,
                    steamGames = steamGameUis
                )
            }

            onStartRowResolved(startRow)

            if (startRow is HomeRow.Platform) {
                val platform = platforms.getOrNull(startRow.index)
                if (platform != null) {
                    loadGamesForPlatformInternal(platform.id, startRow.index)
                }
            }

            loadRecommendations()
        }
    }

    fun observePlatformChanges(scope: CoroutineScope, onPlatformsChanged: (List<HomePlatformUi>, List<HomePlatformUi>) -> Unit) {
        scope.launch {
            platformRepository.observePlatformsWithGames().collect { platforms ->
                cachedPlatformDisplayNames = platforms.associate { it.id to it.getDisplayName() }
                val currentPlatforms = _state.value.platforms
                val newPlatformUis = platforms.map { it.toHomePlatformUi(emulatorDetector) }
                onPlatformsChanged(currentPlatforms, newPlatformUis)
                _state.update { it.copy(platforms = newPlatformUis) }
            }
        }
    }

    fun observeRecentlyPlayedChanges(scope: CoroutineScope, onRecentGamesUpdated: (List<HomeGameUi>) -> Unit) {
        scope.launch {
            gameRepository.awaitStorageReady()
            val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
            gameRepository.observeRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL).collect { recentlyPlayed ->
                val newlyAdded = gameRepository.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
                val allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }

                val playableGames = filterPlayable(allCandidates)
                val sorted = sortRecentGamesWithNewPriority(playableGames)
                val validated = sorted.take(RECENT_GAMES_LIMIT).map { it.toUi() }

                recentGamesCache.set(RecentGamesCache(validated, recentGamesCache.get().version))
                _state.update { it.copy(recentGames = validated) }
                onRecentGamesUpdated(validated)
            }
        }
    }

    fun observePinnedCollections(scope: CoroutineScope) {
        scope.launch {
            getPinnedCollectionsUseCase().collect { pinnedList ->
                val allPinIds = pinnedList.map { it.id }.toSet()
                _state.update { it.copy(pinnedCollections = pinnedList, pinnedGamesLoading = allPinIds) }

                pinnedList.forEach { pinned ->
                    launch { prefetchGamesForPinnedCollection(pinned) }
                }
            }
        }
    }

    suspend fun loadRecentGames() {
        val currentCache = recentGamesCache.get()
        val startVersion = currentCache.version

        val gameUis = if (currentCache.games != null) {
            currentCache.games
        } else {
            val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
            var recentlyPlayed = gameRepository.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
            var newlyAdded = gameRepository.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
            var allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }

            if (discoverGamesIfNeeded(allCandidates)) {
                recentlyPlayed = gameRepository.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
                newlyAdded = gameRepository.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
                allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }
            }

            val playableGames = filterPlayable(allCandidates)
            val sorted = sortRecentGamesWithNewPriority(playableGames)
            val validated = sorted.take(RECENT_GAMES_LIMIT).map { it.toUi() }

            recentGamesCache.compareAndSet(
                RecentGamesCache(null, startVersion),
                RecentGamesCache(validated, startVersion)
            )
            validated
        }

        _state.update { it.copy(recentGames = gameUis) }
    }

    suspend fun loadFavorites() {
        var games = gameRepository.getFavorites()
        if (discoverGamesIfNeeded(games)) {
            games = gameRepository.getFavorites()
        }
        val gameUis = games.map { it.toUi() }
        _state.update { it.copy(favoriteGames = gameUis) }
    }

    suspend fun loadRecommendations() {
        val prefs = preferencesRepository.preferences.first()
        val storedIds = prefs.recommendedGameIds

        if (storedIds.isNotEmpty()) {
            val games = gameRepository.getByIds(storedIds)
            val orderedGames = storedIds.mapNotNull { id -> games.find { it.id == id } }

            val displayedGames = orderedGames
                .filter { it.status !in EXCLUDED_RECOMMENDATION_STATUSES }
                .take(MAX_DISPLAYED_RECOMMENDATIONS)

            applyPenaltiesToDisplayed(displayedGames.map { it.id })

            val gameUis = displayedGames.map { it.toUi() }
            _state.update { it.copy(recommendedGames = gameUis) }
        }
    }

    fun regenerateRecommendations(scope: CoroutineScope) {
        scope.launch {
            val ids = generateRecommendationsUseCase(forceRegenerate = true)
            if (ids.isNotEmpty()) {
                val games = gameRepository.getByIds(ids)
                val orderedGames = ids.mapNotNull { id -> games.find { it.id == id } }

                val displayedGames = orderedGames
                    .filter { it.status !in EXCLUDED_RECOMMENDATION_STATUSES }
                    .take(MAX_DISPLAYED_RECOMMENDATIONS)

                applyPenaltiesToDisplayed(displayedGames.map { it.id })

                val gameUis = displayedGames.map { it.toUi() }
                _state.update { it.copy(recommendedGames = gameUis) }
                notificationManager.showSuccess("Recommendations updated")
            } else {
                _state.update { it.copy(recommendedGames = emptyList()) }
                notificationManager.showError("Not enough games to generate recommendations")
            }
        }
    }

    suspend fun refreshRecommendationsIfNeeded() {
        val prefs = preferencesRepository.preferences.first()
        val lastGen = prefs.lastRecommendationGeneration

        val shouldGenerate = if (lastGen == null) {
            true
        } else {
            val lastGenWeek = lastGen.atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
            val currentWeek = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
            currentWeek.isAfter(lastGenWeek)
        }

        if (shouldGenerate) {
            val ids = generateRecommendationsUseCase()
            if (ids.isNotEmpty()) {
                val games = gameRepository.getByIds(ids)
                val orderedGames = ids.mapNotNull { id -> games.find { it.id == id } }
                _state.update { it.copy(recommendedGames = orderedGames.map { g -> g.toUi() }) }
            }
        }
    }

    suspend fun loadPlatforms() {
        val allPlatforms = platformRepository.getPlatformsWithGames()
        val platforms = allPlatforms.filter { it.id != LocalPlatformIds.STEAM && it.id != LocalPlatformIds.ANDROID }
        cachedPlatformDisplayNames = allPlatforms.associate { it.id to it.getDisplayName() }
        val platformUis = platforms.map { it.toHomePlatformUi(emulatorDetector) }
        val androidGames = gameRepository.getByPlatformSorted(LocalPlatformIds.ANDROID, limit = PLATFORM_GAMES_LIMIT)
        val androidGameUis = androidGames.map { it.toUi() }
        val steamGames = gameRepository.getByPlatformSorted(LocalPlatformIds.STEAM, limit = PLATFORM_GAMES_LIMIT)
        val steamGameUis = steamGames.map { it.toUi() }
        _state.update {
            it.copy(
                platforms = platformUis,
                androidGames = androidGameUis,
                steamGames = steamGameUis
            )
        }
    }

    suspend fun loadGamesForPlatformInternal(platformId: Long, platformIndex: Int) {
        var games = gameRepository.getByPlatformSorted(platformId, limit = PLATFORM_GAMES_LIMIT)
        if (discoverGamesIfNeeded(games)) {
            games = gameRepository.getByPlatformSorted(platformId, limit = PLATFORM_GAMES_LIMIT)
        }
        val platform = _state.value.platforms.getOrNull(platformIndex)
        val gameItems: List<HomeRowItem> = games.map { HomeRowItem.Game(it.toUi()) }
        val items: List<HomeRowItem> = if (platform != null) {
            gameItems + HomeRowItem.ViewAll(
                platformId = platform.id,
                platformName = platform.name,
                logoPath = platform.logoPath
            )
        } else {
            gameItems
        }
        _state.update { it.copy(platformItems = items) }
    }

    suspend fun loadGamesForPinnedCollection(pinId: Long) {
        val pinned = _state.value.pinnedCollections.find { it.id == pinId } ?: return
        var games = getGamesForPinnedCollectionUseCase(pinned).first()
        if (discoverGamesIfNeeded(games)) {
            games = getGamesForPinnedCollectionUseCase(pinned).first()
        }
        val gameUis = games.map { it.toUi() }
        _state.update { state ->
            state.copy(
                pinnedGames = state.pinnedGames + (pinId to gameUis),
                pinnedGamesLoading = state.pinnedGamesLoading - pinId
            )
        }
    }

    suspend fun refreshCurrentRow(currentRow: HomeRow, focusedGameId: Long?): RefreshResult {
        return when (currentRow) {
            HomeRow.Favorites -> {
                val games = gameRepository.getFavorites()
                val gameUis = games.map { it.toUi() }
                _state.update { it.copy(favoriteGames = gameUis) }
                RefreshResult(gameUis.map { it.id }, isEmpty = gameUis.isEmpty())
            }
            HomeRow.Continue -> {
                invalidateRecentGamesCache()
                val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
                val recentlyPlayed = gameRepository.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
                val newlyAdded = gameRepository.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
                val allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }

                val playableGames = filterPlayable(allCandidates)
                val sorted = sortRecentGamesWithNewPriority(playableGames)
                val validated = sorted.take(RECENT_GAMES_LIMIT).map { it.toUi() }

                val currentCache = recentGamesCache.get()
                recentGamesCache.compareAndSet(
                    RecentGamesCache(null, currentCache.version),
                    RecentGamesCache(validated, currentCache.version)
                )

                _state.update { it.copy(recentGames = validated) }
                RefreshResult(validated.map { it.id }, isEmpty = validated.isEmpty())
            }
            is HomeRow.Platform -> {
                val platform = _state.value.platforms.getOrNull(currentRow.index) ?: return RefreshResult(emptyList())
                val games = gameRepository.getByPlatformSorted(platform.id, limit = PLATFORM_GAMES_LIMIT)
                val gameItems: List<HomeRowItem> = games.map { HomeRowItem.Game(it.toUi()) }
                val items: List<HomeRowItem> = gameItems + HomeRowItem.ViewAll(
                    platformId = platform.id,
                    platformName = platform.name,
                    logoPath = platform.logoPath
                )
                _state.update { it.copy(platformItems = items) }
                RefreshResult(items.mapNotNull { (it as? HomeRowItem.Game)?.game?.id })
            }
            HomeRow.Recommendations -> {
                loadRecommendations()
                RefreshResult(_state.value.recommendedGames.map { it.id })
            }
            HomeRow.Android -> {
                val games = gameRepository.getByPlatformSorted(LocalPlatformIds.ANDROID, limit = PLATFORM_GAMES_LIMIT)
                val gameUis = games.map { it.toUi() }
                _state.update { it.copy(androidGames = gameUis) }
                RefreshResult(gameUis.map { it.id }, isEmpty = gameUis.isEmpty())
            }
            HomeRow.Steam -> {
                val games = gameRepository.getByPlatformSorted(LocalPlatformIds.STEAM, limit = PLATFORM_GAMES_LIMIT)
                val gameUis = games.map { it.toUi() }
                _state.update { it.copy(steamGames = gameUis) }
                RefreshResult(gameUis.map { it.id }, isEmpty = gameUis.isEmpty())
            }
            is HomeRow.PinnedRegular -> {
                loadGamesForPinnedCollection(currentRow.pinId)
                RefreshResult((_state.value.pinnedGames[currentRow.pinId] ?: emptyList()).map { it.id })
            }
            is HomeRow.PinnedVirtual -> {
                loadGamesForPinnedCollection(currentRow.pinId)
                RefreshResult((_state.value.pinnedGames[currentRow.pinId] ?: emptyList()).map { it.id })
            }
        }
    }

    fun updateAchievementCounts(gameId: Long, total: Int, earned: Int) {
        _state.update { state ->
            state.copy(
                recentGames = state.recentGames.map {
                    if (it.id == gameId) it.copy(achievementCount = total, earnedAchievementCount = earned) else it
                },
                favoriteGames = state.favoriteGames.map {
                    if (it.id == gameId) it.copy(achievementCount = total, earnedAchievementCount = earned) else it
                },
                recommendedGames = state.recommendedGames.map {
                    if (it.id == gameId) it.copy(achievementCount = total, earnedAchievementCount = earned) else it
                },
                androidGames = state.androidGames.map {
                    if (it.id == gameId) it.copy(achievementCount = total, earnedAchievementCount = earned) else it
                },
                steamGames = state.steamGames.map {
                    if (it.id == gameId) it.copy(achievementCount = total, earnedAchievementCount = earned) else it
                },
                platformItems = state.platformItems.map { item ->
                    when (item) {
                        is HomeRowItem.Game -> if (item.game.id == gameId) {
                            HomeRowItem.Game(item.game.copy(achievementCount = total, earnedAchievementCount = earned))
                        } else item
                        is HomeRowItem.ViewAll -> item
                    }
                }
            )
        }
    }

    fun extractGradientsForVisibleGames(scope: CoroutineScope, currentItems: List<HomeRowItem>, focusedIndex: Int) {
        val games = currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game }
        if (games.isEmpty()) return
        val requests = games.map { GameGradientRequest(it.id, it.coverPath) }
        gradientExtractionDelegate.extractForVisibleGames(scope, requests, focusedIndex)
    }

    fun extractGradientForGame(scope: CoroutineScope, gameId: Long, coverPath: String, isFocused: Boolean) {
        gradientExtractionDelegate.extractForGame(scope, gameId, coverPath, prioritize = isFocused)
    }

    fun repairCoverImage(scope: CoroutineScope, gameId: Long, failedPath: String) {
        if (pendingCoverRepairs.contains(gameId)) return
        pendingCoverRepairs.add(gameId)

        scope.launch {
            val repairedUrl = repairImageCacheUseCase.repairCover(gameId, failedPath)
            if (repairedUrl != null) {
                _state.update { state ->
                    state.copy(repairedCoverPaths = state.repairedCoverPaths + (gameId to repairedUrl))
                }
            }
            pendingCoverRepairs.remove(gameId)
        }
    }

    fun invalidateRecentGamesCache() {
        recentGamesCache.updateAndGet { RecentGamesCache(null, it.version + 1) }
    }

    private suspend fun prefetchGamesForPinnedCollection(pinned: PinnedCollection) {
        val games = getGamesForPinnedCollectionUseCase(pinned).first()
        val gameUis = games.map { it.toUi() }
        _state.update { state ->
            state.copy(
                pinnedGames = state.pinnedGames + (pinned.id to gameUis),
                pinnedGamesLoading = state.pinnedGamesLoading - pinned.id
            )
        }
    }

    private suspend fun applyPenaltiesToDisplayed(displayedIds: List<Long>) {
        val prefs = preferencesRepository.preferences.first()
        val penalties = prefs.recommendationPenalties.toMutableMap()
        var updated = false

        for (id in displayedIds) {
            val current = penalties[id] ?: 0f
            if (current < RECOMMENDATION_PENALTY) {
                penalties[id] = RECOMMENDATION_PENALTY
                updated = true
            }
        }

        if (updated) {
            val weekKey = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
                .toString()
            preferencesRepository.setRecommendationPenalties(penalties, weekKey)
        }
    }

    private suspend fun discoverGamesIfNeeded(games: List<GameEntity>): Boolean {
        val gamesNeedingDiscovery = games.filter { game ->
            game.source != GameSource.STEAM &&
            game.source != GameSource.ANDROID_APP &&
            game.rommId != null &&
            (game.localPath == null || !File(game.localPath).exists())
        }
        if (gamesNeedingDiscovery.isEmpty()) return false
        withContext(Dispatchers.IO) {
            gamesNeedingDiscovery.take(20).forEach { game ->
                gameRepository.validateAndDiscoverGame(game.id)
            }
        }
        return true
    }

    private fun filterPlayable(candidates: List<GameEntity>): List<GameEntity> {
        return candidates.filter { game ->
            when {
                game.source == GameSource.STEAM -> true
                game.source == GameSource.ANDROID_APP -> true
                game.localPath != null -> File(game.localPath).exists()
                else -> false
            }
        }
    }

    private fun sortRecentGamesWithNewPriority(games: List<GameEntity>): List<GameEntity> {
        val now = Instant.now()
        val newThreshold = now.minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
        val recentPlayedThreshold = now.minus(RECENT_PLAYED_THRESHOLD_HOURS, ChronoUnit.HOURS)

        return games.sortedWith(
            compareBy<GameEntity> { game ->
                val isNew = game.addedAt.isAfter(newThreshold) && game.lastPlayed == null
                val playedRecently = game.lastPlayed?.isAfter(recentPlayedThreshold) == true
                when {
                    playedRecently -> 0
                    isNew -> 1
                    else -> 2
                }
            }.thenByDescending { game ->
                game.lastPlayed?.toEpochMilli() ?: game.addedAt.toEpochMilli()
            }
        )
    }

    private fun GameEntity.toUi(): HomeGameUi {
        val firstScreenshot = screenshotPaths?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() }
        val effectiveBackground = backgroundPath ?: firstScreenshot ?: coverPath
        val newThreshold = Instant.now().minus(24, ChronoUnit.HOURS)
        return HomeGameUi(
            id = id,
            title = title,
            platformId = platformId,
            platformSlug = platformSlug,
            platformDisplayName = cachedPlatformDisplayNames[platformId] ?: platformSlug,
            coverPath = coverPath,
            gradientColors = gradientExtractionDelegate.getGradient(id),
            backgroundPath = effectiveBackground,
            developer = developer,
            releaseYear = releaseYear,
            genre = genre,
            isFavorite = isFavorite,
            isDownloaded = localPath != null || source == GameSource.STEAM || source == GameSource.ANDROID_APP,
            isRommGame = rommId != null,
            rating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty,
            achievementCount = achievementCount,
            earnedAchievementCount = earnedAchievementCount,
            isAndroidApp = source == GameSource.ANDROID_APP || platformSlug == "android",
            packageName = packageName,
            needsInstall = platformSlug == "android" && localPath != null && packageName == null && source != GameSource.ANDROID_APP,
            youtubeVideoId = youtubeVideoId,
            isNew = addedAt.isAfter(newThreshold) && lastPlayed == null
        )
    }
}

data class RefreshResult(
    val gameIds: List<Long>,
    val isEmpty: Boolean = false
)
