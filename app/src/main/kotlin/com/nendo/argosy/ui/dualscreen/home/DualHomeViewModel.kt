/**
 * DUAL-SCREEN COMPONENT - Lower display ViewModel.
 * Runs in :companion process (SecondaryHomeActivity).
 * Manages game carousel state and platform switching.
 */
package com.nendo.argosy.ui.dualscreen.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val NEW_GAME_THRESHOLD_HOURS = 24L
private const val RECENT_PLAYED_THRESHOLD_HOURS = 4L
private const val RECENT_GAMES_LIMIT = 30
private const val PLATFORM_GAMES_LIMIT = 50

sealed class DualHomeSection(val title: String) {
    data object Recent : DualHomeSection("Continue Playing")
    data object Favorites : DualHomeSection("Favorites")
    data class Platform(
        val id: Long,
        val slug: String,
        val name: String,
        val displayName: String,
        val logoPath: String?
    ) : DualHomeSection(displayName)
}

enum class DualHomeFocusZone { CAROUSEL, APP_BAR }

data class DualHomeUiState(
    val sections: List<DualHomeSection> = emptyList(),
    val currentSectionIndex: Int = 0,
    val games: List<DualHomeGameUi> = emptyList(),
    val selectedIndex: Int = 0,
    val isLoading: Boolean = true,
    val focusZone: DualHomeFocusZone = DualHomeFocusZone.CAROUSEL,
    val appBarIndex: Int = 0
) {
    val currentSection: DualHomeSection?
        get() = sections.getOrNull(currentSectionIndex)

    val totalCount: Int
        get() = games.size

    val platformName: String
        get() = currentSection?.title ?: ""

    val selectedGame: DualHomeGameUi?
        get() = games.getOrNull(selectedIndex)
}

class DualHomeViewModel(
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualHomeUiState())
    val uiState: StateFlow<DualHomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val sections = buildSections()
            _uiState.update {
                it.copy(
                    sections = sections,
                    isLoading = false
                )
            }
            loadGamesForCurrentSection()
        }
    }

    private suspend fun buildSections(): List<DualHomeSection> {
        val sections = mutableListOf<DualHomeSection>()

        val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
        val recentGames = gameDao.getRecentlyPlayed(limit = 1)
        val newGames = gameDao.getNewlyAddedPlayable(newThreshold, 1)
        if (recentGames.isNotEmpty() || newGames.isNotEmpty()) {
            sections.add(DualHomeSection.Recent)
        }

        val favorites = gameDao.getFavorites()
        if (favorites.isNotEmpty()) {
            sections.add(DualHomeSection.Favorites)
        }

        val platforms = platformDao.getPlatformsWithGames()
            .filter { it.id != LocalPlatformIds.STEAM && it.id != LocalPlatformIds.ANDROID }
        platforms.forEach { platform ->
            sections.add(
                DualHomeSection.Platform(
                    id = platform.id,
                    slug = platform.slug,
                    name = platform.name,
                    displayName = platform.getDisplayName(),
                    logoPath = platform.logoPath
                )
            )
        }

        return sections
    }

    private fun loadGamesForCurrentSection() {
        viewModelScope.launch {
            val section = _uiState.value.currentSection ?: return@launch

            val games = when (section) {
                is DualHomeSection.Recent -> {
                    val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
                    val recentlyPlayed = gameDao.getRecentlyPlayed(limit = RECENT_GAMES_LIMIT)
                    val newlyAdded = gameDao.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_LIMIT)
                    val allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }

                    val playable = allCandidates.filter { game ->
                        when {
                            game.source == GameSource.STEAM -> true
                            game.source == GameSource.ANDROID_APP -> true
                            game.localPath != null -> File(game.localPath).exists()
                            else -> false
                        }
                    }

                    sortRecentGamesWithNewPriority(playable)
                        .take(RECENT_GAMES_LIMIT)
                        .map { it.toUi() }
                }
                is DualHomeSection.Favorites -> {
                    gameDao.getFavorites().map { it.toUi() }
                }
                is DualHomeSection.Platform -> {
                    gameDao.getByPlatformSorted(section.id, limit = PLATFORM_GAMES_LIMIT)
                        .filter { !it.isHidden }
                        .map { it.toUi() }
                }
            }

            _uiState.update {
                it.copy(games = games)
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

    fun refresh() {
        viewModelScope.launch {
            val sections = buildSections()
            _uiState.update { it.copy(sections = sections) }
            loadGamesForCurrentSection()
        }
    }

    fun nextSection() {
        val state = _uiState.value
        if (state.sections.isEmpty()) return

        val newIndex = (state.currentSectionIndex + 1) % state.sections.size
        _uiState.update { it.copy(currentSectionIndex = newIndex, selectedIndex = 0) }
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
        _uiState.update { it.copy(currentSectionIndex = newIndex, selectedIndex = 0) }
        loadGamesForCurrentSection()
    }

    fun selectNext() {
        val state = _uiState.value
        if (state.games.isEmpty()) return
        val newIndex = (state.selectedIndex + 1).coerceAtMost(state.games.size - 1)
        _uiState.update { it.copy(selectedIndex = newIndex) }
    }

    fun selectPrevious() {
        val state = _uiState.value
        if (state.games.isEmpty()) return
        val newIndex = (state.selectedIndex - 1).coerceAtLeast(0)
        _uiState.update { it.copy(selectedIndex = newIndex) }
    }

    fun setSelectedIndex(index: Int) {
        _uiState.update { it.copy(selectedIndex = index.coerceIn(0, maxOf(0, it.games.size - 1))) }
    }

    fun focusAppBar(appCount: Int) {
        if (appCount <= 0) return
        _uiState.update { it.copy(
            focusZone = DualHomeFocusZone.APP_BAR,
            appBarIndex = it.appBarIndex.coerceIn(0, appCount - 1)
        )}
    }

    fun focusCarousel() {
        _uiState.update { it.copy(focusZone = DualHomeFocusZone.CAROUSEL) }
    }

    fun selectNextApp(appCount: Int) {
        _uiState.update { it.copy(
            appBarIndex = (it.appBarIndex + 1).coerceAtMost(appCount - 1)
        )}
    }

    fun selectPreviousApp() {
        _uiState.update { it.copy(
            appBarIndex = (it.appBarIndex - 1).coerceAtLeast(0)
        )}
    }

    fun toggleFavorite() {
        val game = _uiState.value.selectedGame ?: return
        viewModelScope.launch {
            gameDao.updateFavorite(game.id, !game.isFavorite)
            loadGamesForCurrentSection()
        }
    }

    fun getGameDetailIntent(gameId: Long): Pair<Intent, android.os.Bundle?> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("argosy://game/$gameId")
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val options = displayAffinityHelper.getActivityOptions(forEmulator = true)
        return intent to options
    }

    fun getLaunchIntent(gameId: Long): Pair<Intent, android.os.Bundle?> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("argosy://play/$gameId")
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val options = displayAffinityHelper.getActivityOptions(forEmulator = true)
        return intent to options
    }

    private fun GameEntity.toUi(): DualHomeGameUi {
        val firstScreenshot = screenshotPaths?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() }
        val effectiveBackground = backgroundPath ?: firstScreenshot ?: coverPath
        return DualHomeGameUi(
            id = id,
            title = title,
            coverPath = coverPath,
            platformName = platformSlug,
            platformSlug = platformSlug,
            playTimeMinutes = playTimeMinutes,
            lastPlayedAt = lastPlayed?.toEpochMilli(),
            status = status,
            communityRating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty,
            isPlayable = localPath != null || source == GameSource.STEAM || source == GameSource.ANDROID_APP,
            isFavorite = isFavorite,
            backgroundPath = effectiveBackground,
            description = description,
            developer = developer,
            releaseYear = releaseYear,
            titleId = null
        )
    }
}
