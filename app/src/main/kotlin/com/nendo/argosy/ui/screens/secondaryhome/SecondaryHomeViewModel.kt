package com.nendo.argosy.ui.screens.secondaryhome

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.ui.util.GridUtils
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val NEW_GAME_THRESHOLD_HOURS = 24L
private const val RECENT_PLAYED_THRESHOLD_HOURS = 4L
private const val RECENT_GAMES_LIMIT = 50

sealed class HomeSection(val title: String) {
    data object Recent : HomeSection("Recent")
    data object Favorites : HomeSection("Favorites")
    data class Platform(val name: String, val id: Long, val slug: String) : HomeSection(name)
}

data class SecondaryGameUi(
    val id: Long,
    val title: String,
    val coverPath: String?,
    val platformSlug: String,
    val isPlayable: Boolean,
    val downloadProgress: Float? = null
)

data class SecondaryAppUi(
    val packageName: String,
    val label: String
)

data class SecondaryHomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val currentSectionIndex: Int = 0,
    val games: List<SecondaryGameUi> = emptyList(),
    val homeApps: List<SecondaryAppUi> = emptyList(),
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val screenWidthDp: Int = 0,
    val isLoading: Boolean = true,
    val focusedGameIndex: Int = 0,
    val isHoldingFocusedGame: Boolean = false
) {
    val currentSection: HomeSection?
        get() = sections.getOrNull(currentSectionIndex)

    val focusedGame: SecondaryGameUi?
        get() = games.getOrNull(focusedGameIndex)

    val columnsCount: Int
        get() = GridUtils.getGameGridColumns(gridDensity, screenWidthDp)

    val gridSpacingDp: Int
        get() = GridUtils.getGridSpacingDp(gridDensity)
}

@HiltViewModel
class SecondaryHomeViewModel @Inject constructor(
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val appsRepository: AppsRepository,
    private val preferencesRepository: UserPreferencesRepository?,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val downloadManager: DownloadManager?,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecondaryHomeUiState())
    val uiState: StateFlow<SecondaryHomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        if (downloadManager != null) {
            observeDownloads()
        }
        // Only observe DataStore flows if preferencesRepository is available (main process)
        // Companion process passes null to avoid cross-process DataStore conflicts
        if (preferencesRepository != null) {
            observeSecondaryHomeApps()
            observeGridDensity()
        }
    }

    private fun observeGridDensity() {
        val repo = preferencesRepository ?: return
        viewModelScope.launch {
            repo.userPreferences.collect { prefs ->
                _uiState.update { it.copy(gridDensity = prefs.gridDensity) }
            }
        }
    }

    fun setScreenWidth(widthDp: Int) {
        _uiState.update { it.copy(screenWidthDp = widthDp) }
    }

    private fun observeDownloads() {
        val dm = downloadManager ?: return
        viewModelScope.launch {
            var previouslyDownloading = emptySet<Long>()

            dm.state.collect { downloadState ->
                val downloadsByGameId = (downloadState.activeDownloads + downloadState.queue)
                    .associateBy { it.gameId }
                val currentlyDownloading = downloadsByGameId.keys

                val completedDownloads = previouslyDownloading - currentlyDownloading
                previouslyDownloading = currentlyDownloading

                if (completedDownloads.isNotEmpty()) {
                    // Reload from database to get fresh data and proper sorting
                    loadGamesForCurrentSection()
                } else {
                    // Just update progress for in-flight downloads
                    _uiState.update { state ->
                        state.copy(
                            games = state.games.map { game ->
                                val download = downloadsByGameId[game.id]
                                game.copy(downloadProgress = download?.progressPercent)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun observeSecondaryHomeApps() {
        val repo = preferencesRepository ?: return
        viewModelScope.launch {
            repo.preferences.collect { prefs ->
                val secondaryHomeApps = prefs.secondaryHomeApps
                val installedApps = appsRepository.getInstalledApps(includeSystemApps = true)
                val homeApps = installedApps
                    .filter { it.packageName in secondaryHomeApps }
                    .map { SecondaryAppUi(it.packageName, it.label) }

                _uiState.update { it.copy(homeApps = homeApps) }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            val sections = buildSections()
            val homeApps = loadHomeApps()

            _uiState.update {
                it.copy(
                    sections = sections,
                    homeApps = homeApps,
                    isLoading = false
                )
            }

            loadGamesForCurrentSection()
        }
    }

    private suspend fun buildSections(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()

        val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
        val recentGames = gameDao.getRecentlyPlayed(limit = 1)
        val newGames = gameDao.getNewlyAddedPlayable(newThreshold, 1)
        if (recentGames.isNotEmpty() || newGames.isNotEmpty()) {
            sections.add(HomeSection.Recent)
        }

        val favorites = gameDao.getFavorites()
        if (favorites.isNotEmpty()) {
            sections.add(HomeSection.Favorites)
        }

        val platforms = platformDao.getPlatformsWithGames()
        platforms.forEach { platform ->
            sections.add(HomeSection.Platform(platform.name, platform.id, platform.slug))
        }

        return sections
    }

    private suspend fun loadHomeApps(): List<SecondaryAppUi> {
        val repo = preferencesRepository ?: return emptyList()
        val prefs = repo.preferences.first()
        val secondaryHomeApps = prefs.secondaryHomeApps

        if (secondaryHomeApps.isEmpty()) return emptyList()

        val installedApps = appsRepository.getInstalledApps(includeSystemApps = true)
        return installedApps
            .filter { it.packageName in secondaryHomeApps }
            .map { SecondaryAppUi(it.packageName, it.label) }
    }

    fun setHomeApps(packageNames: List<String>) {
        viewModelScope.launch {
            val installedApps = appsRepository.getInstalledApps(includeSystemApps = true)
            val homeApps = installedApps
                .filter { it.packageName in packageNames }
                .map { SecondaryAppUi(it.packageName, it.label) }
            _uiState.update { it.copy(homeApps = homeApps) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val sections = buildSections()
            _uiState.update { it.copy(sections = sections) }
            loadGamesForCurrentSection()
        }
    }

    private fun loadGamesForCurrentSection() {
        viewModelScope.launch {
            val section = _uiState.value.currentSection ?: return@launch

            val games = when (section) {
                is HomeSection.Recent -> {
                    val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
                    val recentlyPlayed = gameDao.getRecentlyPlayed(limit = RECENT_GAMES_LIMIT)
                    val newlyAdded = gameDao.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_LIMIT)
                    val allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }
                    sortRecentGamesWithNewPriority(allCandidates)
                        .take(RECENT_GAMES_LIMIT)
                        .map { it.toUi() }
                }
                is HomeSection.Favorites -> {
                    gameDao.getFavorites().map { it.toUi() }
                }
                is HomeSection.Platform -> {
                    gameDao.getByPlatformSorted(section.id, limit = 200)
                        .filter { !it.isHidden }
                        .map { it.toUi() }
                }
            }

            _uiState.update { it.copy(games = games) }
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

    fun nextSection() {
        val state = _uiState.value
        if (state.sections.isEmpty()) return

        val newIndex = (state.currentSectionIndex + 1) % state.sections.size
        _uiState.update { it.copy(currentSectionIndex = newIndex, focusedGameIndex = 0) }
        loadGamesForCurrentSection()
    }

    fun previousSection() {
        val state = _uiState.value
        if (state.sections.isEmpty()) return

        val newIndex = if (state.currentSectionIndex <= 0) {
            state.sections.size - 1
        } else {
            state.currentSectionIndex - 1
        }
        _uiState.update { it.copy(currentSectionIndex = newIndex, focusedGameIndex = 0) }
        loadGamesForCurrentSection()
    }

    fun getGameDetailIntent(gameId: Long): Pair<Intent, android.os.Bundle?> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("argosy://game/$gameId")
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val options = displayAffinityHelper.getActivityOptions(forEmulator = true)
        return intent to options
    }

    fun getAppLaunchIntent(packageName: String): Pair<Intent?, android.os.Bundle?> {
        val intent = appsRepository.getLaunchIntent(packageName)
        val options = displayAffinityHelper.getActivityOptions(forEmulator = false)
        return intent to options
    }

    fun getAppsScreenIntent(): Pair<Intent, android.os.Bundle?> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("argosy://apps")
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val options = displayAffinityHelper.getActivityOptions(forEmulator = true)
        return intent to options
    }

    fun launchGame(gameId: Long): Pair<Intent?, android.os.Bundle?> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("argosy://play/$gameId")
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val options = displayAffinityHelper.getActivityOptions(forEmulator = true)
        return intent to options
    }

    fun startDownload(gameId: Long) {
        val dm = downloadManager ?: return
        viewModelScope.launch {
            // Try to resume if there's a paused/waiting download first
            dm.resumeDownload(gameId)

            val game = gameDao.getById(gameId) ?: return@launch
            val rommId = game.rommId ?: return@launch
            val fileName = game.rommFileName ?: game.title

            dm.enqueueDownload(
                gameId = gameId,
                rommId = rommId,
                fileName = fileName,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                coverPath = game.coverPath,
                expectedSizeBytes = game.fileSizeBytes ?: 0
            )
        }
    }

    fun moveFocusUp() {
        val state = _uiState.value
        if (state.games.isEmpty()) return
        val newIndex = (state.focusedGameIndex - state.columnsCount).coerceAtLeast(0)
        _uiState.update { it.copy(focusedGameIndex = newIndex) }
    }

    fun moveFocusDown() {
        val state = _uiState.value
        if (state.games.isEmpty()) return
        val newIndex = (state.focusedGameIndex + state.columnsCount).coerceAtMost(state.games.size - 1)
        _uiState.update { it.copy(focusedGameIndex = newIndex) }
    }

    fun moveFocusLeft() {
        val state = _uiState.value
        if (state.games.isEmpty()) return
        val currentCol = state.focusedGameIndex % state.columnsCount
        if (currentCol > 0) {
            _uiState.update { it.copy(focusedGameIndex = state.focusedGameIndex - 1) }
        }
    }

    fun moveFocusRight() {
        val state = _uiState.value
        if (state.games.isEmpty()) return
        val currentCol = state.focusedGameIndex % state.columnsCount
        if (currentCol < state.columnsCount - 1 && state.focusedGameIndex < state.games.size - 1) {
            _uiState.update { it.copy(focusedGameIndex = state.focusedGameIndex + 1) }
        }
    }

    fun selectFocusedGame(): Pair<Intent, android.os.Bundle?>? {
        val game = _uiState.value.focusedGame ?: return null
        return getGameDetailIntent(game.id)
    }

    fun launchFocusedGame(): Pair<Intent?, android.os.Bundle?>? {
        val game = _uiState.value.focusedGame ?: return null
        return if (game.isPlayable) {
            launchGame(game.id)
        } else {
            startDownload(game.id)
            null
        }
    }

    fun setFocusedGameIndex(index: Int) {
        _uiState.update { it.copy(focusedGameIndex = index.coerceIn(0, it.games.size - 1)) }
    }

    fun startHoldingFocusedGame() {
        _uiState.update { it.copy(isHoldingFocusedGame = true) }
    }

    fun stopHoldingFocusedGame() {
        _uiState.update { it.copy(isHoldingFocusedGame = false) }
    }

    private fun GameEntity.toUi() = SecondaryGameUi(
        id = id,
        title = title,
        coverPath = coverPath,
        platformSlug = platformSlug,
        isPlayable = localPath != null
    )
}
